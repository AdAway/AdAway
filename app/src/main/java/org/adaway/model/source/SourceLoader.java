package org.adaway.model.source;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.Log;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.BOGUS_IPv4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPv4;
import static org.adaway.util.Constants.LOCALHOST_IPv6;

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

    private final int sourceId;
    private final boolean parseRedirectedHosts;

    SourceLoader(HostsSource hostsSource) {
        this.sourceId = hostsSource.getId();
        this.parseRedirectedHosts = hostsSource.isRedirectEnabled();
    }

    void parse(Reader reader, HostListItemDao hostListItemDao) {
        // Clear current hosts
        hostListItemDao.clearSourceHosts(this.sourceId);
        // Create batch
        int parserCount = 3;
        LinkedBlockingQueue<String> hostsLineQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<HostListItem> hostsListItemQueue = new LinkedBlockingQueue<>();
        SourceReader sourceReader = new SourceReader(reader, hostsLineQueue, parserCount);
        ItemInserter inserter = new ItemInserter(hostsListItemQueue, hostListItemDao, parserCount);
        ExecutorService executorService = Executors.newFixedThreadPool(
                parserCount + 2,
                r -> new Thread(r, "SourceLoader")
        );
        executorService.execute(sourceReader);
        for (int i = 0; i < parserCount; i++) {
            executorService.execute(new HostListItemParser(hostsLineQueue, hostsListItemQueue));
        }
        Future<Integer> inserterFuture = executorService.submit(inserter);
        try {
            Integer inserted = inserterFuture.get();
            Log.i(TAG, inserted + " host list items inserted.");
        } catch (ExecutionException e) {
            Log.w(TAG, "Failed to parse hosts sources.", e);
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while parsing sources.", e);
            Thread.currentThread().interrupt();
        }
        executorService.shutdown();
    }

    private HostListItem parseHostListItem(String line) {
        Matcher matcher = HOSTS_PARSER_PATTERN.matcher(line);
        if (!matcher.matches()) {
            Log.d(TAG, "Does not match: " + line);
            return null;
        }
        // Check IP address validity or while list entry (if allowed)
        String ip = matcher.group(1);
        String hostname = matcher.group(2);
        // Skip localhost name
        if (LOCALHOST_HOSTNAME.equals(hostname)) {
            return null;
        }
        // check if ip is 127.0.0.1 or 0.0.0.0
        ListType type;
        if (LOCALHOST_IPv4.equals(ip)
                || BOGUS_IPv4.equals(ip)
                || LOCALHOST_IPv6.equals(ip)) {
            type = BLOCKED;
        } else if (this.parseRedirectedHosts) {
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
        item.setSourceId(this.sourceId);
        return item;
    }

    private boolean isRedirectionValid(HostListItem item) {
        return item.getType() != REDIRECTED || RegexUtils.isValidIP(item.getRedirection());
    }

    private boolean isHostValid(HostListItem item) {
        return RegexUtils.isValidWildcardHostname(item.getHost());
    }

    private static class SourceReader implements Runnable {
        private final Reader reader;
        private final BlockingQueue<String> queue;
        private final int parserCount;

        private SourceReader(Reader reader, BlockingQueue<String> queue, int parserCount) {
            this.reader = reader;
            this.queue = queue;
            this.parserCount = parserCount;
        }

        @Override
        public void run() {
            new BufferedReader(this.reader)
                    .lines()
                    .forEach(this.queue::add);
            // Send end of queue marker to parsers
            for (int i = 0; i < this.parserCount; i++) {
                this.queue.add(END_OF_QUEUE_MARKER);
            }
        }
    }

    private class HostListItemParser implements Runnable {
        private final BlockingQueue<String> hostsLineQueue;
        private final BlockingQueue<HostListItem> hostListItemQueue;

        private HostListItemParser(BlockingQueue<String> lineQueue, BlockingQueue<HostListItem> itemQueue) {
            this.hostsLineQueue = lineQueue;
            this.hostListItemQueue = itemQueue;
        }

        @Override
        public void run() {
            boolean endOfSource = false;
            while (!endOfSource) {
                try {
                    String line = this.hostsLineQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (line == END_OF_QUEUE_MARKER) {
                        endOfSource = true;
                        // Send end of queue marker to inserter
                        HostListItem endItem = new HostListItem();
                        endItem.setHost(line);
                        this.hostListItemQueue.add(endItem);
                    } else {
                        HostListItem item = parseHostListItem(line);
                        if (item != null && isRedirectionValid(item) && isHostValid(item)) {
                            this.hostListItemQueue.add(item);
                        }
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while parsing hosts list item.", e);
                    endOfSource = true;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static class ItemInserter implements Callable<Integer> {
        private final BlockingQueue<HostListItem> hostListItemQueue;
        private final HostListItemDao hostListItemDao;
        private final int parserCount;

        private ItemInserter(BlockingQueue<HostListItem> ItemQueue, HostListItemDao hostListItemDao, int parserCount) {
            this.hostListItemQueue = ItemQueue;
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
                    Log.w(TAG, "Interrupted while inserted hosts list item.", e);
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
