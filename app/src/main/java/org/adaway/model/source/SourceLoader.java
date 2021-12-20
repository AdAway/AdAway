package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.BOGUS_IPV4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPV4;
import static org.adaway.util.Constants.LOCALHOST_IPV6;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * This class is an {@link HostsSource} loader.<br>
 * It parses a source and loads it to database.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceLoader {
    private static final String TAG = "SourceLoader";
    private static final String END_OF_QUEUE_MARKER = "#EndOfQueueMarker";
    private static final int INSERT_BATCH_SIZE = 100;
    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+).*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);

    private final HostsSource source;

    SourceLoader(HostsSource hostsSource) {
        this.source = hostsSource;
    }

    void parse(BufferedReader reader, HostListItemDao hostListItemDao) {
        // Clear current hosts
        hostListItemDao.clearSourceHosts(this.source.getId());
        // Create batch
        int parserCount = 3;
        LinkedBlockingQueue<String> hostsLineQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<HostListItem> hostsListItemQueue = new LinkedBlockingQueue<>();
        SourceReader sourceReader = new SourceReader(reader, hostsLineQueue, parserCount);
        ItemInserter inserter = new ItemInserter(hostsListItemQueue, hostListItemDao, parserCount);
        ExecutorService executorService = Executors.newFixedThreadPool(
                parserCount + 2,
                r -> new Thread(r, TAG)
        );
        executorService.execute(sourceReader);
        for (int i = 0; i < parserCount; i++) {
            executorService.execute(new HostListItemParser(this.source, hostsLineQueue, hostsListItemQueue));
        }
        Future<Integer> inserterFuture = executorService.submit(inserter);
        try {
            Integer inserted = inserterFuture.get();
            Timber.i("%s host list items inserted.", inserted);
        } catch (ExecutionException e) {
            Timber.w(e, "Failed to parse hosts sources.");
        } catch (InterruptedException e) {
            Timber.w(e, "Interrupted while parsing sources.");
            Thread.currentThread().interrupt();
        }
        executorService.shutdown();
    }

    private static class SourceReader implements Runnable {
        private final BufferedReader reader;
        private final BlockingQueue<String> queue;
        private final int parserCount;

        private SourceReader(BufferedReader reader, BlockingQueue<String> queue, int parserCount) {
            this.reader = reader;
            this.queue = queue;
            this.parserCount = parserCount;
        }

        @Override
        public void run() {
            try {
                this.reader.lines().forEach(this.queue::add);
            } catch (Throwable t) {
                Timber.w(t, "Failed to read hosts source.");
            } finally {
                // Send end of queue marker to parsers
                for (int i = 0; i < this.parserCount; i++) {
                    this.queue.add(END_OF_QUEUE_MARKER);
                }
            }
        }
    }

    private static class HostListItemParser implements Runnable {
        private final HostsSource source;
        private final BlockingQueue<String> lineQueue;
        private final BlockingQueue<HostListItem> itemQueue;

        private HostListItemParser(HostsSource source, BlockingQueue<String> lineQueue, BlockingQueue<HostListItem> itemQueue) {
            this.source = source;
            this.lineQueue = lineQueue;
            this.itemQueue = itemQueue;
        }

        @Override
        public void run() {
            boolean allowedList = this.source.isAllowEnabled();
            boolean endOfSource = false;
            while (!endOfSource) {
                try {
                    String line = this.lineQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (line == END_OF_QUEUE_MARKER) {
                        endOfSource = true;
                        // Send end of queue marker to inserter
                        HostListItem endItem = new HostListItem();
                        endItem.setHost(line);
                        this.itemQueue.add(endItem);
                    } else {
                        HostListItem item = allowedList ? parseAllowListItem(line) : parseHostListItem(line);
                        if (item != null && isRedirectionValid(item) && isHostValid(item)) {
                            this.itemQueue.add(item);
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while parsing hosts list item.");
                    endOfSource = true;
                    Thread.currentThread().interrupt();
                }
            }
        }

        private HostListItem parseHostListItem(String line) {
            Matcher matcher = HOSTS_PARSER_PATTERN.matcher(line);
            if (!matcher.matches()) {
                Timber.d("Does not match: %s.", line);
                return null;
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
            } else if (this.source.isRedirectEnabled()) {
                type = REDIRECTED;
            } else {
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
            // Extract hostname
            int indexOf = line.indexOf('#');
            if (indexOf == 1) {
                line = line.substring(0, indexOf);
            }
            line = line.trim();
            // Create item
            HostListItem item = new HostListItem();
            item.setType(ALLOWED);
            item.setHost(line);
            item.setEnabled(true);
            item.setSourceId(this.source.getId());
            return item;
        }

        private boolean isRedirectionValid(HostListItem item) {
            return item.getType() != REDIRECTED || RegexUtils.isValidIP(item.getRedirection());
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
        private final int parserCount;

        private ItemInserter(BlockingQueue<HostListItem> itemQueue, HostListItemDao hostListItemDao, int parserCount) {
            this.hostListItemQueue = itemQueue;
            this.hostListItemDao = hostListItemDao;
            this.parserCount = parserCount;
        }

        @Override
        public Integer call() {
            int inserted = 0;
            int workerStopped = 0;
            HostListItem[] batch = new HostListItem[INSERT_BATCH_SIZE];
            int cacheSize = 0;
            boolean queueEmptied = false;
            while (!queueEmptied) {
                try {
                    HostListItem item = this.hostListItemQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (item.getHost() == END_OF_QUEUE_MARKER) {
                        workerStopped++;
                        if (workerStopped >= this.parserCount) {
                            queueEmptied = true;
                        }
                    } else {
                        batch[cacheSize++] = item;
                        if (cacheSize >= batch.length) {
                            this.hostListItemDao.insert(batch);
                            cacheSize = 0;
                            inserted += cacheSize;
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while inserted hosts list item.");
                    queueEmptied = true;
                    Thread.currentThread().interrupt();
                }
            }
            // Flush current batch
            HostListItem[] remaining = new HostListItem[cacheSize];
            System.arraycopy(batch, 0, remaining, 0, remaining.length);
            this.hostListItemDao.insert(remaining);
            inserted += cacheSize;
            // Return number of inserted items
            return inserted;
        }
    }
}
