package org.adaway.vpn.dns;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import timber.log.Timber;

/**
 * This class represents the running DNS queries queue.<br>
 * This queue is time and space bound.
 *
 * @author Bruce BUJON
 */
public class DnsQueryQueue implements Iterable<DnsQuery> {
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
     * Add query to queue.
     *
     * @param query The query to add.
     */
    public void add(DnsQuery query) {
        // Apply time constraint by removing timed out queries
        clearTimedOutQueries();
        // Apply space constraint by removing older packet if queue is full
        if (this.queries.size() > DNS_MAXIMUM_WAITING) {
            DnsQuery oldestQuery = this.queries.remove();
            Timber.d("Dropping query due to space constraints: %s.", query.socket);
            oldestQuery.socket.close();
        }
        // Add query to queue
        this.queries.add(query);
    }

    private void clearTimedOutQueries() {
        long now = System.currentTimeMillis() / 1000;
        while (!this.queries.isEmpty() && (now - queries.element().time) > DNS_TIMEOUT_SEC) {
            DnsQuery wosPacket = queries.remove();
            Timber.d("Timeout on socket %s.", wosPacket.socket);
            wosPacket.socket.close();
        }
    }

    @NonNull
    public Iterator<DnsQuery> iterator() {
        return queries.iterator();
    }

    /**
     * Get the number of pending DNS queries.
     *
     * @return The number of pending DNS queries.
     */
    public int size() {
        return this.queries.size();
    }
}
