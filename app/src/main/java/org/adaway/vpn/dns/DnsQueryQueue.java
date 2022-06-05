package org.adaway.vpn.dns;

import android.system.StructPollfd;

import java.net.DatagramSocket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * This class represents the running DNS queries queue.<br>
 * This queue is time and space bound.
 *
 * @author Bruce BUJON
 */
public class DnsQueryQueue {
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
    private final Queue<DnsQuery> queries;

    /**
     * Constructor.
     */
    public DnsQueryQueue() {
        this.queries = new LinkedList<>();
    }

    /**
     * Add DNS query to the queue.
     *
     * @param socket   The socket used to query DNS server.
     * @param callback The callback to call with the query response data.
     */
    public void addQuery(DatagramSocket socket, Consumer<byte[]> callback) {
        // Apply time constraint by removing timed out queries
        clearTimedOutQueries();
        // Apply space constraint by removing older packet if queue is full
        ensureFreeSpace();
        // Add query to the queue
        DnsQuery query = new DnsQuery(socket, callback);
        this.queries.add(query);
    }

    private void ensureFreeSpace() {
        if (this.queries.size() > DNS_MAXIMUM_WAITING) {
            DnsQuery oldestQuery = this.queries.remove();
            Timber.d("Dropping query due to space constraints: %s.", oldestQuery);
            oldestQuery.close();
        }
    }

    private void clearTimedOutQueries() {
        long now = System.currentTimeMillis() / 1000;
        while (!this.queries.isEmpty() && this.queries.element().isOlderThan(now - DNS_TIMEOUT_SEC)) {
            DnsQuery timedOutQuery = this.queries.remove();
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

    /**
     * Get the query pollfds.
     *
     * @return The query pollfds.
     */
    public StructPollfd[] getQueryFds() {
        return this.queries.stream()
                .map(DnsQuery::getPollfd)
                .toArray(StructPollfd[]::new);
    }

    /**
     * Handle any responded query.
     */
    public void handleResponses() {
        Iterator<DnsQuery> iterator = this.queries.iterator();
        while (iterator.hasNext()) {
            DnsQuery query = iterator.next();
            if (query.isAnswered()) {
                iterator.remove();
                query.handleResponse();
            }
        }
    }
}
