package org.adaway.vpn.dns;

import android.system.StructPollfd;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import timber.log.Timber;

/**
 * This class represents the running DNS queries queue.<br>
 * This queue is time and space bound.
 *
 * @author Bruce BUJON
 */
public class DnsQueryQueue implements AutoCloseable {
    /**
     * The maximum number of responses to wait for.
     */
    private static final int DNS_MAXIMUM_WAITING = 1024;
    /**
     * The maximum time to wait for the response (in seconds).
     */
    private static final long DNS_TIMEOUT_SEC = 10;
    /**
     * The packet queue (older packets first, in the queue head).
     */
    private final Queue<PendingDnsQuery> queries;
    private final LongSupplier currentTimeSeconds;

    /**
     * Constructor.
     */
    public DnsQueryQueue() {
        this(new LinkedList<>(), () -> System.nanoTime() / 1_000_000_000L);
    }

    DnsQueryQueue(Queue<PendingDnsQuery> queries, LongSupplier currentTimeSeconds) {
        this.queries = queries;
        this.currentTimeSeconds = currentTimeSeconds;
    }

    /**
     * Add DNS query to the queue.
     *
     * @param socket   The socket used to query DNS server.
     * @param callback The callback to call with the query response data.
     */
    public void addQuery(DatagramSocket socket, Consumer<byte[]> callback) throws IOException {
        addQuery(new DnsQuery(socket, callback, this.currentTimeSeconds.getAsLong()));
    }

    void addQuery(PendingDnsQuery query) {
        // Apply time constraint by removing timed out queries
        clearTimedOutQueries();
        // Apply space constraint by removing older packet if queue is full
        ensureFreeSpace();
        // Add query to the queue
        this.queries.add(query);
    }

    private void ensureFreeSpace() {
        while (this.queries.size() >= DNS_MAXIMUM_WAITING) {
            PendingDnsQuery oldestQuery = this.queries.remove();
            Timber.d("Dropping query due to space constraints: %s.", oldestQuery);
            oldestQuery.close();
        }
    }

    private void clearTimedOutQueries() {
        long oldestAllowedTime = this.currentTimeSeconds.getAsLong() - DNS_TIMEOUT_SEC;
        while (!this.queries.isEmpty()
                && this.queries.element().isOlderThan(oldestAllowedTime)) {
            PendingDnsQuery timedOutQuery = this.queries.remove();
            Timber.d("Query %s timed out.", timedOutQuery);
            timedOutQuery.close();
        }
    }

    /**
     * Get the number of pending DNS queries.
     *
     * @return The number of pending DNS queries.
     */
    public int size() {
        return this.queries.size();
    }

    @Override
    public void close() {
        PendingDnsQuery query;
        while ((query = this.queries.poll()) != null) {
            query.close();
        }
    }

    /**
     * Get the query pollfds.
     *
     * @return The query pollfds.
     */
    public StructPollfd[] getQueryFds() {
        clearTimedOutQueries();
        return this.queries.stream()
                .map(PendingDnsQuery::getPollfd)
                .toArray(StructPollfd[]::new);
    }

    /**
     * Handle any responded query.
     */
    public void handleResponses() {
        clearTimedOutQueries();
        Iterator<PendingDnsQuery> iterator = this.queries.iterator();
        while (iterator.hasNext()) {
            PendingDnsQuery query = iterator.next();
            if (query.isAnswered()) {
                iterator.remove();
                query.handleResponse();
            }
        }
    }
}
