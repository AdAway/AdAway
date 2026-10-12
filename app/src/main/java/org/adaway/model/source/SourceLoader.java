package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.adaway.util.Constants.BOGUS_IPV4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPV4;
import static org.adaway.util.Constants.LOCALHOST_IPV6;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.os.SystemClock;

import timber.log.Timber;

/**
 * This class is an {@link HostsSource} loader.<br>
 * It parses a source and loads it to database.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceLoader {
    private static final String TAG = "SourceLoader";
    // Identity-only sentinel: source text must never be able to stop a parser worker.
    private static final String END_OF_QUEUE_MARKER = new String("#endofqueuemarker");
    static final int MAX_LINE_LENGTH = 64 * 1024;
    // Batch size tradeoff:
    // - Smaller => smoother UI updates but slower DB throughput
    // - Larger  => much faster parsing/DB insert for large filter sets
    //
    // 2000 is tuned for throughput on large filter sets. This increases per-batch latency, but can
    // significantly reduce overall parse time when SQLite/Room is the bottleneck.
    private static final int INSERT_BATCH_SIZE = 2000;
    // Queue capacity - balances throughput vs memory usage.
    // 20,000 allows reader to stay ahead of parsers without blocking.
    private static final int QUEUE_CAPACITY = 20000;
    // Parser thread count - adaptive based on hardware (from SourceModel)
    private static final int PARSER_COUNT = SourceModel.PARSER_THREADS_PER_SOURCE;
    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+).*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);
    static final Pattern ADBLOCK_DOUBLE_PIPE = Pattern.compile("^\\|\\|([^\\^/$|\\s]+)\\^?$");
    static final Pattern URL_HOST = Pattern.compile("^\\|?https?://([^/\\^$]+).*$");
    private static final Pattern DNSMASQ_ADDRESS =
            Pattern.compile("^address=/([^/\\s#]+)/([^\\s]*)\\s*(?:#.*)?$");
    private static final Pattern DNSMASQ_LOCAL =
            Pattern.compile("^local=/([^/\\s#]+)/?\\s*(?:#.*)?$");
    private static final Pattern DNSMASQ_SERVER = Pattern.compile("^server=/([^/]+)/.*$");
    // Unbound DNS: local-zone: "example.com" always_refuse
    static final Pattern UNBOUND_LOCAL_ZONE =
            Pattern.compile("^\\s*local-zone:\\s*\"([^\"]+)\"\\s+([A-Za-z_]+).*$");
    // Unbound DNS: local-data: "example.com A 0.0.0.0"
    static final Pattern UNBOUND_LOCAL_DATA =
            Pattern.compile("^\\s*local-data:\\s*\"([^\\s\"]+)\\s+([A-Za-z]+)\\s+([^\\s\"]+)\".*$");
    // BIND RPZ: example.com CNAME .  (optionally: example.com 60 IN CNAME .)
    static final Pattern RPZ_CNAME_DOT = Pattern.compile("^([a-zA-Z0-9][a-zA-Z0-9._-]{0,252})\\s+(?:\\d+\\s+)?(?:IN\\s+)?CNAME\\s+\\..*$");
    // Surge/Quantumult/Clash host rules. Action-bearing rules are accepted only
    // when the action is a block action such as REJECT; DIRECT/proxy actions are skipped.
    static final Pattern SURGE_DOMAIN_RULE = Pattern.compile(
            "^DOMAIN(?:-FULL)?,([a-zA-Z0-9][a-zA-Z0-9._-]{1,252})"
                    + "(?:,([^#\\s]+))?\\s*(?:#.*)?$");
    static final Pattern SURGE_DOMAIN_SUFFIX_RULE = Pattern.compile(
            "^DOMAIN-SUFFIX,([a-zA-Z0-9][a-zA-Z0-9._-]{1,252})"
                    + "(?:,([^#\\s]+))?\\s*(?:#.*)?$");
    // BIND zone statement: zone "example.com" { type master; ... };
    static final Pattern BIND_ZONE_STMT = Pattern.compile("^\\s*zone\\s+\"([^\"]+)\"\\s*\\{.*$");
    private final HostsSource source;
    private final int generation;

    SourceLoader(HostsSource hostsSource) {
        this.source = hostsSource;
        this.generation = 0;
    }

    SourceLoader(HostsSource hostsSource, int generation) {
        this.source = hostsSource;
        this.generation = generation;
    }

    static final class ExtractedRule {
        @NonNull
        final String host;
        @NonNull
        final RuleKind kind;

        private ExtractedRule(@NonNull String host, @NonNull RuleKind kind) {
            this.host = host;
            this.kind = kind;
        }
    }

    int parse(BufferedReader reader, HostListItemDao hostListItemDao) {
        return parse(reader, hostListItemDao, null, null, null);
    }

    /**
     * Parse hosts source and insert into database.
     * @param reader The source reader.
     * @param hostListItemDao The DAO for inserting items.
     * @param onBatchInserted Callback called after each batch insert with the count of inserted items.
     * @return The number of skipped (malformed or unrecognized) lines.
     */
    int parse(BufferedReader reader, HostListItemDao hostListItemDao, @Nullable LongConsumer onBatchInserted) {
        return parse(reader, hostListItemDao, null, onBatchInserted, null);
    }

    /**
     * Parse hosts source and insert into database, optionally using raw SQLite for high-throughput bulk inserts.
     * When {@code db} is provided, inserts are performed using a compiled statement + explicit transactions,
     * which is significantly faster than Room @Insert for large batches.
     * @return The number of skipped (malformed or unrecognized) lines.
     */
    int parse(BufferedReader reader,
               HostListItemDao hostListItemDao,
               @Nullable SupportSQLiteDatabase db,
               @Nullable LongConsumer onBatchInserted,
               @Nullable SqlUpdateDeduper sqlDeduper) {
        // Clear any previous partial import for THIS generation only (atomic updates keep old generations intact).
        hostListItemDao.clearSourceHostsForGeneration(this.source.getId(), this.generation);
        if (db != null) {
            clearRootExportStage(db, this.source.getId(), this.generation);
        }
        // Create queues with bounded capacity to prevent OOM during parallel parsing
        LinkedBlockingQueue<String> hostsLineQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        LinkedBlockingQueue<HostListItem> hostsListItemQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        AtomicReference<Throwable> sourceReadFailure = new AtomicReference<>();
        SourceReader sourceReader = new SourceReader(reader, hostsLineQueue, PARSER_COUNT, sourceReadFailure);
        ItemInserter inserter = new ItemInserter(hostsListItemQueue, hostListItemDao, db,
                this.source.getId(), this.generation, PARSER_COUNT, onBatchInserted,
                sourceReadFailure, sqlDeduper);

        // Shared counter for malformed lines across all parser threads
        AtomicInteger skippedLines = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(
                PARSER_COUNT + 2,
                r -> {
                    Thread t = new Thread(r, TAG);
                    // Parser threads are CPU-intensive - slightly lower priority to avoid starving I/O
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
        );
        executorService.execute(sourceReader);
        for (int i = 0; i < PARSER_COUNT; i++) {
            executorService.execute(new HostListItemParser(this.source, hostsLineQueue, hostsListItemQueue,
                    skippedLines, sourceReadFailure));
        }
        Future<Integer> inserterFuture = executorService.submit(inserter);
        int skipped = 0;
        boolean succeeded = false;
        boolean interrupted = false;
        try {
            Integer inserted = inserterFuture.get();
            skipped = skippedLines.get();
            if (inserted == 0 && skipped > 0) {
                throw new IllegalStateException("Hosts source contains no supported rules");
            }
            if (skipped > 0) {
                Timber.w("Source %s: %d lines skipped (malformed or unrecognized format)", this.source.getLabel(), skipped);
            }
            Timber.i("%s: %d host list items inserted, %d lines skipped.", this.source.getLabel(), inserted, skipped);
            succeeded = true;
        } catch (ExecutionException e) {
            Timber.w(e, "Failed to parse hosts sources.");
            throw new IllegalStateException("Failed to parse hosts source.", e);
        } catch (InterruptedException e) {
            Timber.w(e, "Interrupted while parsing sources.");
            interrupted = true;
            throw new IllegalStateException("Interrupted while parsing hosts source.", e);
        } finally {
            executorService.shutdownNow();
            // Cleanup must follow the last possible writer, including on cancellation.
            while (!executorService.isTerminated()) {
                try {
                    executorService.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            try {
                if (!succeeded) {
                    hostListItemDao.clearSourceHostsForGeneration(this.source.getId(), this.generation);
                    if (db != null) {
                        clearRootExportStage(db, this.source.getId(), this.generation);
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return skipped;
    }

    private static void clearRootExportStage(
            @NonNull SupportSQLiteDatabase db, int sourceId, int generation) {
        SupportSQLiteStatement statement = db.compileStatement(
                "DELETE FROM root_host_entries_stage WHERE source_id = ? AND generation = ?");
        statement.bindLong(1, sourceId);
        statement.bindLong(2, generation);
        statement.executeUpdateDelete();
    }

    /**
     * Extract a hostname from non-hosts syntaxes (ABP/uBO/AdGuard style lists, domain lists, dnsmasq, etc.).
     * ABP rules with $options or cosmetic/element-hiding rules (##, #@#, #?#, #$#) are skipped —
     * they cannot be represented as DNS-level domain blocks without causing false positives.
     */
    @Nullable
    static String extractHostnameFromNonHostsSyntax(String rawLine) {
        ExtractedRule rule = extractRuleFromNonHostsSyntax(rawLine);
        return rule == null ? null : rule.host;
    }

    @Nullable
    static RuleKind extractRuleKindFromNonHostsSyntax(String rawLine) {
        ExtractedRule rule = extractRuleFromNonHostsSyntax(rawLine);
        return rule == null ? null : rule.kind;
    }

    @Nullable
    static ExtractedRule extractRuleFromNonHostsSyntax(String rawLine) {
        if (rawLine == null) return null;
        String line = rawLine.trim();
        if (line.isEmpty()) return null;

        // Skip ABP/uBO cosmetic and scriptlet rules BEFORE any stripping.
        // "domain##selector", "domain#@#selector" etc. — the ## is the separator, not a comment.
        // These must be caught on the raw line before the '#' stripping below removes the ##.
        if (line.contains("##") || line.contains("#@#") || line.contains("#$#") || line.contains("#?#")) {
            return null;
        }

        // Skip section headers like [AdBlock Plus 2.0]
        if (line.startsWith("[")) {
            return null;
        }

        // Skip exceptions
        if (line.startsWith("@@")) {
            return null;
        }

        // dnsmasq: address=/example.com/0.0.0.0 or address=/example.com/#
        Matcher dnsmasq = DNSMASQ_ADDRESS.matcher(line);
        if (dnsmasq.matches()) {
            return isDnsmasqNullAddress(dnsmasq.group(2)) ? suffixRule(dnsmasq.group(1)) : null;
        }

        // dnsmasq: local=/example.com/ or local=/example.com
        Matcher dnsmasqLocal = DNSMASQ_LOCAL.matcher(line);
        if (dnsmasqLocal.matches()) {
            return suffixRule(dnsmasqLocal.group(1));
        }

        // dnsmasq: server=/example.com/8.8.8.8 is upstream routing, not blocking.
        Matcher dnsmasqServer = DNSMASQ_SERVER.matcher(line);
        if (dnsmasqServer.matches()) {
            return null;
        }

        // Drop inline comments for common syntaxes (standalone # only, not ## which was handled above)
        int hash = line.indexOf('#');
        if (hash > 0) {
            line = line.substring(0, hash).trim();
        }
        if (line.isEmpty()) return null;

        // Unbound: local-zone: "example.com" always_refuse
        Matcher unboundZone = UNBOUND_LOCAL_ZONE.matcher(line);
        if (unboundZone.matches()) {
            return isUnboundBlockZoneType(unboundZone.group(2))
                    ? exactRule(unboundZone.group(1)) : null;
        }

        // Unbound: local-data: "example.com A 0.0.0.0"
        Matcher unboundData = UNBOUND_LOCAL_DATA.matcher(line);
        if (unboundData.matches()) {
            return isUnboundBlockDataTarget(unboundData.group(2), unboundData.group(3))
                    ? exactRule(unboundData.group(1)) : null;
        }

        // BIND RPZ: example.com CNAME .  (optionally with TTL/IN class)
        Matcher rpz = RPZ_CNAME_DOT.matcher(line);
        if (rpz.matches()) {
            return exactRule(rpz.group(1));
        }

        Matcher surgeSuffix = SURGE_DOMAIN_SUFFIX_RULE.matcher(line);
        if (surgeSuffix.matches()) {
            return isSurgeBlockAction(surgeSuffix.group(2)) ? suffixRule(surgeSuffix.group(1)) : null;
        }

        // Surge/Quantumult/Clash exact-host rules.
        Matcher surge = SURGE_DOMAIN_RULE.matcher(line);
        if (surge.matches()) {
            return isSurgeBlockAction(surge.group(2)) ? exactRule(surge.group(1)) : null;
        }

        // Generic BIND zones are ambiguous; only explicit RPZ CNAME . rules are block-safe.
        Matcher bindZone = BIND_ZONE_STMT.matcher(line);
        if (bindZone.matches()) {
            return null;
        }

        // Plain domain line: example.com
        // Guard: skip lines starting with '|' — those are ABP-style rules handled below,
        // and sanitizeHostname() would otherwise strip the leading '||' and return a hostname
        // even for content-filter rules like ||google.com^$third-party.
        if (!line.startsWith("|")) {
            if (line.indexOf('/') >= 0 || line.indexOf('$') >= 0 || line.indexOf('^') >= 0) {
                return null;
            }
            String plain = sanitizeHostname(line);
            if (plain != null) {
                return new ExtractedRule(plain, EXACT);
            }
        }

        // uBO/ABP style: ||example.com^ maps to a DNS suffix rule. Skip rules with
        // $options or path components because DNS cannot represent their browser context.
        // Ad networks are covered by OISD/hosts-format entries instead.
        Matcher dbl = ADBLOCK_DOUBLE_PIPE.matcher(line);
        if (dbl.matches()) {
            return suffixRule(dbl.group(1));
        }

        // URL style: |https://example.com/path
        Matcher url = URL_HOST.matcher(line);
        if (url.matches()) {
            return null;
        }

        return null;
    }

    @Nullable
    private static ExtractedRule exactRule(String rawHost) {
        String host = sanitizeHostname(rawHost);
        return host == null ? null : new ExtractedRule(host, EXACT);
    }

    @Nullable
    private static ExtractedRule suffixRule(String rawHost) {
        String host = sanitizeHostname(rawHost);
        return host == null ? null : new ExtractedRule(host, SUFFIX);
    }

    private static boolean isDnsmasqNullAddress(@NonNull String target) {
        return target.equals("0.0.0.0") || target.equals("::") || target.equals("#");
    }

    private static boolean isSurgeBlockAction(@Nullable String rawAction) {
        if (rawAction == null || rawAction.isEmpty()) {
            return true;
        }
        String firstAction = rawAction.split(",", 2)[0].trim().toUpperCase(Locale.ROOT);
        return firstAction.equals("REJECT") || firstAction.equals("REJECT-DROP");
    }

    private static boolean isUnboundBlockZoneType(@NonNull String rawType) {
        String type = rawType.toLowerCase(Locale.ROOT);
        return type.equals("always_refuse")
                || type.equals("always_nxdomain")
                || type.equals("always_nodata")
                || type.equals("always_deny")
                || type.equals("always_null");
    }

    private static boolean isUnboundBlockDataTarget(@NonNull String rawRecordType,
            @NonNull String rawTarget) {
        String recordType = rawRecordType.toUpperCase(Locale.ROOT);
        String target = rawTarget.toLowerCase(Locale.ROOT);
        if (recordType.equals("A")) {
            return target.equals(BOGUS_IPV4) || target.equals(LOCALHOST_IPV4);
        }
        if (recordType.equals("AAAA")) {
            return target.equals("::") || target.equals(LOCALHOST_IPV6);
        }
        return false;
    }

    @Nullable
    static String sanitizeHostname(@Nullable String raw) {
        if (raw == null) return null;
        String h = raw.trim();
        if (h.isEmpty()) return null;

        // Remove leading separators used in some syntaxes
        while (!h.isEmpty() && (h.charAt(0) == '.' || h.charAt(0) == '|')) {
            h = h.substring(1);
        }
        if (h.length() > 1 && h.charAt(h.length() - 1) == '.') {
            h = h.substring(0, h.length() - 1);
        }

        // Truncate at common ABP/uBO delimiters
        int cut = h.length();
        int caret = h.indexOf('^');
        if (caret >= 0) cut = Math.min(cut, caret);
        int dollar = h.indexOf('$');
        if (dollar >= 0) cut = Math.min(cut, dollar);
        int slash = h.indexOf('/');
        if (slash >= 0) cut = Math.min(cut, slash);
        if (cut != h.length()) {
            h = h.substring(0, cut);
        }

        h = h.trim();
        if (h.isEmpty()) return null;

        // Reject patterns/wildcards/regex-like tokens
        if (h.indexOf('*') != -1 || h.indexOf('?') != -1 || h.indexOf('%') != -1) return null;
        if (h.indexOf(' ') != -1 || h.indexOf('\t') != -1) return null;

        // Reject IP addresses here (we only accept hostnames via this path)
        if (RegexUtils.isValidIP(h)) return null;

        // Basic hostname validation
        return RegexUtils.isValidHostname(h) ? h : null;
    }

    private static class SourceReader implements Runnable {
        private final BufferedReader reader;
        private final BlockingQueue<String> queue;
        private final int parserCount;
        private final AtomicReference<Throwable> failure;

        private SourceReader(BufferedReader reader, BlockingQueue<String> queue, int parserCount,
                AtomicReference<Throwable> failure) {
            this.reader = reader;
            this.queue = queue;
            this.parserCount = parserCount;
            this.failure = failure;
        }

        @Override
        public void run() {
            try {
                char[] buffer = new char[8192];
                StringBuilder line = new StringBuilder();
                boolean firstCharacter = true;
                int count;
                while ((count = this.reader.read(buffer)) != -1) {
                    for (int i = 0; i < count; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException();
                        }
                        char character = buffer[i];
                        if (firstCharacter && character == '\ufeff') {
                            firstCharacter = false;
                            continue;
                        }
                        firstCharacter = false;
                        if (character == '\n' || character == '\r') {
                            if (line.length() > 0) {
                                this.queue.put(line.toString());
                                line.setLength(0);
                            }
                        } else {
                            if (line.length() >= MAX_LINE_LENGTH) {
                                throw new IOException("Hosts source line exceeds " + MAX_LINE_LENGTH
                                        + " characters");
                            }
                            line.append(character);
                        }
                    }
                }
                if (line.length() > 0) {
                    this.queue.put(line.toString());
                }
            } catch (Throwable t) {
                this.failure.compareAndSet(null, t);
                Timber.w(t, "Failed to read hosts source.");
            } finally {
                // Send end of queue marker to parsers
                for (int i = 0; i < this.parserCount; i++) {
                    try {
                        this.queue.put(END_OF_QUEUE_MARKER);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private static class HostListItemParser implements Runnable {
        private final HostsSource source;
        private final BlockingQueue<String> lineQueue;
        private final BlockingQueue<HostListItem> itemQueue;
        private final AtomicInteger skippedLines;
        private final AtomicReference<Throwable> failure;

        private HostListItemParser(HostsSource source, BlockingQueue<String> lineQueue,
                                   BlockingQueue<HostListItem> itemQueue,
                                   AtomicInteger skippedLines, AtomicReference<Throwable> failure) {
            this.source = source;
            this.lineQueue = lineQueue;
            this.itemQueue = itemQueue;
            this.skippedLines = skippedLines;
            this.failure = failure;
        }

        @Override
        public void run() {
            boolean allowedList = this.source.isAllowEnabled();
            boolean endOfSource = false;
            while (!endOfSource) {
                try {
                    String line = this.lineQueue.take();
                    // Check end of queue marker
                    if (END_OF_QUEUE_MARKER == line) {
                        endOfSource = true;
                        // Send end of queue marker to inserter
                        HostListItem endItem = new HostListItem();
                        endItem.setHost(line);
                        this.itemQueue.put(endItem);  // put() blocks if full, add() throws!
                    } // Skip comments and empty lines (no logging - too slow for millions of lines)
                    else if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') {
                        // skip
                    } else {
                        HostListItem item = allowedList ? parseAllowListItem(line) : parseHostListItem(line);
                        if (item == null || !isRedirectionValid(item) || !isHostValid(item)) {
                            // Track failed parse attempts
                            this.skippedLines.incrementAndGet();
                        } else {
                            this.itemQueue.put(item);
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while parsing hosts list item.");
                    endOfSource = true;
                    Thread.currentThread().interrupt();
                    this.failure.compareAndSet(null, e);
                } catch (Throwable t) {
                    this.failure.compareAndSet(null, t);
                    endOfSource = true;
                }
            }
        }

        private HostListItem parseHostListItem(String line) {
            Matcher matcher = HOSTS_PARSER_PATTERN.matcher(line);
            if (!matcher.matches()) {
                // Best-effort: accept common "filter syntax" formats by extracting a hostname.
                ExtractedRule extracted = extractRuleFromNonHostsSyntax(line);
                if (extracted == null) {
                    return null;  // Skip non-matching lines silently for performance
                }
                // Treat extracted domain as blocked (0.0.0.0 domain)
                HostListItem item = new HostListItem();
                item.setType(BLOCKED);
                item.setHost(extracted.host);
                item.setKind(extracted.kind);
                item.setEnabled(true);
                item.setSourceId(this.source.getId());
                return item;
            }
            // Check IP address validity or while list entry (if allowed)
            String ip = matcher.group(1);
            String hostname = matcher.group(2);
            assert hostname != null;
            // Skip localhost name
            if (LOCALHOST_HOSTNAME.equals(hostname)) {
                return null;
            }
            // check if ip is 127.0.0.1 or 0.0.0.0
            ListType type;
            if (LOCALHOST_IPV4.equals(ip)
                    || BOGUS_IPV4.equals(ip)
                    || LOCALHOST_IPV6.equals(ip)) {
                type = BLOCKED;
            } else if (!RegexUtils.isValidIP(ip)) {
                ExtractedRule extracted = extractRuleFromNonHostsSyntax(line);
                if (extracted != null) {
                    HostListItem item = new HostListItem();
                    item.setType(BLOCKED);
                    item.setHost(extracted.host);
                    item.setKind(extracted.kind);
                    item.setEnabled(true);
                    item.setSourceId(this.source.getId());
                    return item;
                }
                return null;
            } else if (this.source.isRedirectEnabled()) {
                type = REDIRECTED;
            } else {
                // Not a standard hosts entry (IP is not 127.0.0.1 / 0.0.0.0 / ::1 and redirect
                // is disabled). This can happen for lines that HOSTS_PARSER_PATTERN matches
                // structurally but which belong to another format — e.g. RPZ:
                //   "ads.example.com CNAME . ; comment" → ip=ads.example.com, hostname=CNAME
                // Fall through to non-hosts syntax extraction to recover these lines.
                ExtractedRule extracted = extractRuleFromNonHostsSyntax(line);
                if (extracted != null) {
                    HostListItem item = new HostListItem();
                    item.setType(BLOCKED);
                    item.setHost(extracted.host);
                    item.setKind(extracted.kind);
                    item.setEnabled(true);
                    item.setSourceId(this.source.getId());
                    return item;
                }
                return null;
            }
            HostListItem item = new HostListItem();
            item.setType(type);
            item.setHost(hostname);
            item.setEnabled(true);
            if (type == REDIRECTED) {
                item.setRedirection(ip);
            }
            item.setSourceId(this.source.getId());
            return item;
        }

        private HostListItem parseAllowListItem(String line) {
            // Try multi-format extraction first (ABP, dnsmasq, URL, plain domain).
            ExtractedRule extracted = extractRuleFromNonHostsSyntax(line);
            String host = extracted == null ? null : extracted.host;
            RuleKind kind = extracted == null ? EXACT : extracted.kind;
            if (host == null) {
                // Fall back: allow wildcard entries (*.example.com) which are valid for
                // allow-lists but rejected by sanitizeHostname inside the extractor above.
                String trimmed = line.trim();
                int hash = trimmed.indexOf('#');
                if (hash > 0) trimmed = trimmed.substring(0, hash).trim();
                if (!trimmed.isEmpty() && RegexUtils.isValidWildcardHostname(trimmed)) {
                    host = trimmed;
                }
            }
            if (host == null) {
                return null;
            }
            // Create item
            HostListItem item = new HostListItem();
            item.setType(ALLOWED);
            item.setHost(host);
            item.setKind(kind);
            item.setEnabled(true);
            item.setSourceId(this.source.getId());
            return item;
        }

        private boolean isRedirectionValid(HostListItem item) {
            if (item.getType() != REDIRECTED) return true;
            String ip = item.getRedirection();
            // Reject private/reserved IPs to prevent DNS redirect attacks routing traffic
            // to router admin pages, internal services, or loopback (ATK-01).
            if (!RegexUtils.isValidIP(ip) || RegexUtils.isPrivateOrReservedIp(ip)) {
                return false;
            }
            return true;
        }

        private boolean isHostValid(HostListItem item) {
            String hostname = item.getHost();
            if (item.getType() == BLOCKED) {
                if (hostname.indexOf('?') != -1 || hostname.indexOf('*') != -1) {
                    return false;
                }
                return RegexUtils.isValidHostname(hostname);
            }
            return RegexUtils.isValidWildcardHostname(hostname);
        }
    }

    private static class ItemInserter implements Callable<Integer> {
        private final BlockingQueue<HostListItem> hostListItemQueue;
        private final HostListItemDao hostListItemDao;
        @Nullable
        private final SupportSQLiteDatabase db;
        private final int sourceId;
        private final int generation;
        private final int parserCount;
        @Nullable
        private final LongConsumer onBatchInserted;
        private final AtomicReference<Throwable> sourceReadFailure;
        @Nullable
        private final SqlUpdateDeduper sqlDeduper;

        private ItemInserter(BlockingQueue<HostListItem> itemQueue,
                             HostListItemDao hostListItemDao,
                             @Nullable SupportSQLiteDatabase db,
                             int sourceId,
                             int generation,
                             int parserCount, @Nullable LongConsumer onBatchInserted,
                             AtomicReference<Throwable> sourceReadFailure,
                             @Nullable SqlUpdateDeduper sqlDeduper) {
            this.hostListItemQueue = itemQueue;
            this.hostListItemDao = hostListItemDao;
            this.db = db;
            this.sourceId = sourceId;
            this.generation = generation;
            this.parserCount = parserCount;
            this.onBatchInserted = onBatchInserted;
            this.sourceReadFailure = sourceReadFailure;
            this.sqlDeduper = sqlDeduper;
        }

        @Override
        public Integer call() {
            int inserted = 0;
            int workerStopped = 0;
            HostListItem[] batch = new HostListItem[INSERT_BATCH_SIZE];
            int cacheSize = 0;
            boolean queueEmptied = false;

            // Lightweight DB perf logging: rows/sec + batch insert latency.
            // Rate-limited to once every ~2s to avoid log spam.
            final boolean perfLog = !Timber.forest().isEmpty();
            final long startMs = SystemClock.elapsedRealtime();
            long lastLogMs = startMs;
            int insertedSinceLastLog = 0;
            long totalDbInsertMs = 0L;

            // Optional fast path: compiled statement for bulk insert.
            final SupportSQLiteStatement insertStmt;
            final SupportSQLiteStatement pendingStmt;
            if (db != null) {
                insertStmt = db.compileStatement(
                        "INSERT INTO hosts_lists (host, reverse_host, type, kind, enabled, " +
                                "redirection, source_id, generation) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                );
                if (this.sqlDeduper != null) {
                    pendingStmt = this.sqlDeduper.compilePendingInsertStatement();
                } else {
                    pendingStmt = null;
                }
                db.beginTransaction();
            } else {
                insertStmt = null;
                pendingStmt = null;
            }

            try {
                // Source selection may have changed while the download was in flight.
                // Read under the writer transaction so a concurrent toggle runs wholly before
                // or after this import, never between this read and the inserted rows.
                boolean sourceEnabled = true;
                if (db != null) {
                    try (android.database.Cursor cursor = db.query(
                            "SELECT enabled FROM hosts_sources WHERE id = ?",
                            new Object[]{this.sourceId})) {
                        sourceEnabled = cursor.moveToFirst() && cursor.getInt(0) != 0;
                    }
                }
                while (!queueEmptied) {
                    try {
                        Throwable workerFailure = this.sourceReadFailure.get();
                        if (workerFailure != null) {
                            throw new IllegalStateException("Hosts source worker failed.", workerFailure);
                        }
                        HostListItem item = this.hostListItemQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (item == null) {
                            continue;
                        }
                        // Check end of queue marker
                        if (END_OF_QUEUE_MARKER.equals(item.getHost())) {
                            workerStopped++;
                            if (workerStopped >= this.parserCount) {
                                queueEmptied = true;
                            }
                        } else {
                            batch[cacheSize++] = item;
                            if (cacheSize >= batch.length) {
                                long t0 = SystemClock.elapsedRealtimeNanos();
                                if (db != null && insertStmt != null) {
                                    BulkInsertResult result = bulkInsert(
                                            insertStmt, pendingStmt,
                                            batch, cacheSize, this.generation, sourceEnabled);
                                    long batchDbMs = result.dbMs;
                                    inserted += result.inserted;
                                    if (perfLog) {
                                        totalDbInsertMs += batchDbMs;
                                        insertedSinceLastLog += result.inserted;
                                        long nowMs = SystemClock.elapsedRealtime();
                                        if (nowMs - lastLogMs >= 2000L) {
                                            long windowMs = Math.max(1L, nowMs - lastLogMs);
                                            double rowsPerSec = (insertedSinceLastLog * 1000.0) / windowMs;
                                            double batchRowsPerSec = batchDbMs > 0
                                                    ? (result.inserted * 1000.0) / batchDbMs : 0.0;
                                            Timber.i("DB insert perf: win=%.0f rows/s, batch=%.0f rows/s (batchSize=%d, lastBatchMs=%d), totalInserted=%d, elapsed=%ds",
                                                    rowsPerSec, batchRowsPerSec, cacheSize, batchDbMs, inserted, (nowMs - startMs) / 1000);
                                            insertedSinceLastLog = 0;
                                            lastLogMs = nowMs;
                                        }
                                    }
                                    if (result.inserted > 0 && this.onBatchInserted != null) {
                                        this.onBatchInserted.accept(result.inserted);
                                    }
                                    cacheSize = 0;
                                    continue;
                                } else {
                                    // Ensure generation is set for Room insert
                                    for (int i = 0; i < cacheSize; i++) {
                                        if (batch[i] != null) batch[i].setGeneration(this.generation);
                                    }
                                    this.hostListItemDao.insert(batch);
                                }
                                long batchDbMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L;
                                inserted += cacheSize;
                                if (perfLog) {
                                    totalDbInsertMs += batchDbMs;
                                    insertedSinceLastLog += cacheSize;
                                    long nowMs = SystemClock.elapsedRealtime();
                                    if (nowMs - lastLogMs >= 2000L) {
                                        long windowMs = Math.max(1L, nowMs - lastLogMs);
                                        double rowsPerSec = (insertedSinceLastLog * 1000.0) / windowMs;
                                        double batchRowsPerSec = batchDbMs > 0 ? (cacheSize * 1000.0) / batchDbMs : 0.0;
                                        Timber.i("DB insert perf: win=%.0f rows/s, batch=%.0f rows/s (batchSize=%d, lastBatchMs=%d), totalInserted=%d, elapsed=%ds",
                                                rowsPerSec, batchRowsPerSec, cacheSize, batchDbMs, inserted, (nowMs - startMs) / 1000);
                                        insertedSinceLastLog = 0;
                                        lastLogMs = nowMs;
                                    }
                                }
                                // Notify callback of batch insert for live UI updates
                                if (this.onBatchInserted != null) {
                                    this.onBatchInserted.accept(cacheSize);
                                }
                                cacheSize = 0;
                            }
                        }
                    } catch (InterruptedException e) {
                        Timber.w(e, "Interrupted while inserted hosts list item.");
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Hosts source insert interrupted.", e);
                    }
                }
                // Flush current batch
                HostListItem[] remaining = new HostListItem[cacheSize];
                System.arraycopy(batch, 0, remaining, 0, remaining.length);
                int finalInserted = 0;
                if (cacheSize > 0) {
                    long t0 = SystemClock.elapsedRealtimeNanos();
                    finalInserted = cacheSize;
                    if (db != null && insertStmt != null) {
                        BulkInsertResult result = bulkInsert(
                                insertStmt, pendingStmt, remaining,
                                cacheSize, this.generation, sourceEnabled);
                        totalDbInsertMs += result.dbMs;
                        finalInserted = result.inserted;
                    } else {
                        for (int i = 0; i < cacheSize; i++) {
                            if (remaining[i] != null) remaining[i].setGeneration(this.generation);
                        }
                        this.hostListItemDao.insert(remaining);
                        long batchDbMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L;
                        totalDbInsertMs += batchDbMs;
                    }
                }
                Throwable failure = this.sourceReadFailure.get();
                if (failure != null) {
                    throw new IllegalStateException("Failed to read hosts source.", failure);
                }
                int acceptedRows = inserted + finalInserted;
                if (db != null && this.sqlDeduper != null) {
                    long flushStartedNs = SystemClock.elapsedRealtimeNanos();
                    int flushed = this.sqlDeduper.flushPendingRowsToHostsLists();
                    long flushMs = (SystemClock.elapsedRealtimeNanos() - flushStartedNs) / 1_000_000L;
                    totalDbInsertMs += flushMs;
                    if (perfLog) {
                        Timber.i("DB staged import flush: rows=%d accepted=%d flushMs=%d",
                                flushed, acceptedRows, flushMs);
                    }
                    inserted = flushed;
                } else {
                    if (db != null) {
                        long stageStartedNs = SystemClock.elapsedRealtimeNanos();
                        int staged = stageRootExportCandidates(
                                db, this.sourceId, this.generation);
                        long stageMs =
                                (SystemClock.elapsedRealtimeNanos() - stageStartedNs)
                                        / 1_000_000L;
                        totalDbInsertMs += stageMs;
                        if (perfLog) {
                            Timber.i("DB root-export stage flush: rows=%d accepted=%d " +
                                    "flushMs=%d", staged, acceptedRows, stageMs);
                        }
                    }
                    inserted = acceptedRows;
                }
                if (db != null) {
                    db.setTransactionSuccessful();
                }
                // Notify callback of final batch
                if (finalInserted > 0 && this.onBatchInserted != null) {
                    this.onBatchInserted.accept(finalInserted);
                }
                if (perfLog) {
                    long nowMs = SystemClock.elapsedRealtime();
                    long elapsedMs = Math.max(1L, nowMs - startMs);
                    double rowsPerSec = (inserted * 1000.0) / elapsedMs;
                    double dbOnlyRowsPerSec = totalDbInsertMs > 0 ? (inserted * 1000.0) / totalDbInsertMs : 0.0;
                    Timber.i("DB insert perf done: overall=%.0f rows/s, dbOnly=%.0f rows/s (totalInserted=%d, dbInsertMs=%d, elapsed=%ds)",
                            rowsPerSec, dbOnlyRowsPerSec, inserted, totalDbInsertMs, elapsedMs / 1000);
                }
            } finally {
                if (db != null) {
                    db.endTransaction();
                }
            }
            // Return number of inserted items
            return inserted;
        }

        private static BulkInsertResult bulkInsert(
                @NonNull SupportSQLiteStatement stmt,
                @Nullable SupportSQLiteStatement pendingStmt,
                @NonNull HostListItem[] items,
                int count,
                int generation,
                boolean sourceEnabled) {
            long t0 = SystemClock.elapsedRealtimeNanos();
            int inserted = 0;
            for (int i = 0; i < count; i++) {
                HostListItem it = items[i];
                if (it == null) continue;
                it.setEnabled(it.isEnabled() && sourceEnabled);
                if (pendingStmt != null) {
                    SqlUpdateDeduper.stagePending(pendingStmt, it, generation);
                    inserted++;
                    continue;
                }
                stmt.clearBindings();
                stmt.bindString(1, it.getHost());
                stmt.bindString(2, it.getReverseHost());
                ListType type = it.getType();
                stmt.bindLong(3, type != null ? type.getValue() : 0);
                RuleKind kind = it.getKind();
                stmt.bindLong(4, kind != null ? kind.getValue() : EXACT.getValue());
                stmt.bindLong(5, it.isEnabled() ? 1L : 0L);
                String redirection = it.getRedirection();
                if (redirection != null) {
                    stmt.bindString(6, redirection);
                } else {
                    stmt.bindNull(6);
                }
                stmt.bindLong(7, it.getSourceId());
                stmt.bindLong(8, generation);
                stmt.executeInsert();
                inserted++;
            }
            return new BulkInsertResult(
                    (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L,
                    inserted);
        }

        private static int stageRootExportCandidates(
                @NonNull SupportSQLiteDatabase db,
                int sourceId,
                int generation) {
            SupportSQLiteStatement statement = db.compileStatement(
                    "INSERT INTO root_host_entries_stage " +
                            "(host, reverse_host, type, redirection, source_id, generation) " +
                            "SELECT host, reverse_host, type, redirection, source_id, " +
                            "generation FROM hosts_lists WHERE source_id = ? " +
                            "AND generation = ? AND enabled = 1 AND type IN (0, 2)");
            statement.bindLong(1, sourceId);
            statement.bindLong(2, generation);
            return statement.executeUpdateDelete();
        }

        private static final class BulkInsertResult {
            final long dbMs;
            final int inserted;

            BulkInsertResult(long dbMs, int inserted) {
                this.dbMs = dbMs;
                this.inserted = inserted;
            }
        }
    }
}
