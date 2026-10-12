package org.adaway.vpn.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.system.StructPollfd;

import org.junit.Test;

import java.util.LinkedList;
import java.util.Queue;

public class DnsQueryQueueTest {
    @Test
    public void capacityEvictsOldestBeforeAdding1025thQuery() {
        Queue<PendingDnsQuery> queries = new LinkedList<>();
        FakePendingDnsQuery oldest = new FakePendingDnsQuery(25L);
        queries.add(oldest);
        for (int i = 1; i < 1024; i++) {
            queries.add(new FakePendingDnsQuery(25L));
        }
        DnsQueryQueue queue = new DnsQueryQueue(queries, () -> 30L);
        queue.addQuery(new FakePendingDnsQuery(30L));
        assertEquals(1024, queue.size());
        assertTrue(oldest.closed);
    }

    @Test
    public void closeReleasesPendingQueriesWithoutDeliveringOldReplies() {
        Queue<PendingDnsQuery> queries = new LinkedList<>();
        FakePendingDnsQuery first = new FakePendingDnsQuery(25L);
        FakePendingDnsQuery second = new FakePendingDnsQuery(25L);
        queries.add(first);
        queries.add(second);
        DnsQueryQueue queue = new DnsQueryQueue(queries, () -> 30L);
        queue.close();
        queue.close();
        assertEquals(0, queue.size());
        assertTrue(first.closed);
        assertTrue(second.closed);
        assertFalse(first.responseHandled);
        assertFalse(second.responseHandled);
    }

    @Test
    public void getQueryFdsDropsTimedOutQueriesBeforePolling() {
        FakePendingDnsQuery staleQuery = new FakePendingDnsQuery(10L);
        Queue<PendingDnsQuery> queries = new LinkedList<>();
        queries.add(staleQuery);

        DnsQueryQueue queue = new DnsQueryQueue(queries, () -> 30L);

        assertEquals(0, queue.getQueryFds().length);
        assertEquals(0, queue.size());
        assertTrue(staleQuery.closed);
        assertEquals(0, staleQuery.pollFdReads);
    }

    @Test
    public void handleResponsesDropsTimedOutQueriesWhileIdle() {
        FakePendingDnsQuery staleQuery = new FakePendingDnsQuery(10L);
        Queue<PendingDnsQuery> queries = new LinkedList<>();
        queries.add(staleQuery);

        DnsQueryQueue queue = new DnsQueryQueue(queries, () -> 30L);

        queue.handleResponses();

        assertEquals(0, queue.size());
        assertTrue(staleQuery.closed);
        assertFalse(staleQuery.responseHandled);
    }

    @Test
    public void getQueryFdsKeepsFreshQueries() {
        FakePendingDnsQuery freshQuery = new FakePendingDnsQuery(25L);
        Queue<PendingDnsQuery> queries = new LinkedList<>();
        queries.add(freshQuery);

        DnsQueryQueue queue = new DnsQueryQueue(queries, () -> 30L);

        assertEquals(1, queue.getQueryFds().length);
        assertEquals(1, queue.size());
        assertFalse(freshQuery.closed);
        assertEquals(1, freshQuery.pollFdReads);
    }

    private static final class FakePendingDnsQuery extends PendingDnsQuery {
        private final long createdAtSeconds;
        private boolean closed;
        private boolean responseHandled;
        private int pollFdReads;

        private FakePendingDnsQuery(long createdAtSeconds) {
            this.createdAtSeconds = createdAtSeconds;
        }

        @Override
        boolean isOlderThan(long timestamp) {
            return this.createdAtSeconds < timestamp;
        }

        @Override
        StructPollfd getPollfd() {
            this.pollFdReads++;
            return null;
        }

        @Override
        boolean isAnswered() {
            return true;
        }

        @Override
        void handleResponse() {
            this.responseHandled = true;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}
