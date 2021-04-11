package org.adaway.ui.log;

import org.junit.Test;

import java.util.Comparator;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.ui.log.LogEntrySort.ALPHABETICAL;
import static org.adaway.ui.log.LogEntrySort.TOP_LEVEL_DOMAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogEntrySortTest {
    @Test
    public void testTopLevelDomainComparator() {
        Comparator<LogEntry> comparator = TOP_LEVEL_DOMAIN.comparator();
        LogEntry entry1 = new LogEntry("google.com", null);
        LogEntry entry1Copy = new LogEntry("google.com", ALLOWED);
        LogEntry entry2 = new LogEntry("beta.google.com", null);
        LogEntry entry3 = new LogEntry("www.google.com", null);

        // Check equality
        assertEquals(0, comparator.compare(entry1, entry1));
        assertEquals(0, comparator.compare(entry2, entry2));
        assertEquals(0, comparator.compare(entry3, entry3));

        // Check transitivity
        assertTrue( comparator.compare(entry1, entry2) < 0);
        assertTrue( comparator.compare(entry2, entry3) < 0);
        assertTrue( comparator.compare(entry1, entry3) < 0);

        // Check sign opposite
        assertTrue( comparator.compare(entry2, entry1) > 0);
        assertTrue( comparator.compare(entry3, entry2) > 0);
        assertTrue( comparator.compare(entry3, entry1) > 0);

        // Check equals elements have the same order
        assertEquals(0, comparator.compare(entry1, entry1Copy));
        assertTrue(comparator.compare(entry1, entry2) * comparator.compare(entry1Copy, entry2) > 0);
    }

    @Test
    public void testAlphabeticalComparator() {
        Comparator<LogEntry> comparator = ALPHABETICAL.comparator();
        LogEntry entry1 = new LogEntry("google.com", null);
        LogEntry entry1Copy = new LogEntry("google.com", ALLOWED);
        LogEntry entry2 = new LogEntry("beta.google.com", null);
        LogEntry entry3 = new LogEntry("www.google.com", null);

        // Check equality
        assertEquals(0, comparator.compare(entry1, entry1));
        assertEquals(0, comparator.compare(entry2, entry2));
        assertEquals(0, comparator.compare(entry3, entry3));

        // Check transitivity
        assertTrue( comparator.compare(entry2, entry1) < 0);
        assertTrue( comparator.compare(entry1, entry3) < 0);
        assertTrue( comparator.compare(entry2, entry3) < 0);

        // Check sign opposite
        assertTrue( comparator.compare(entry1, entry2) > 0);
        assertTrue( comparator.compare(entry3, entry1) > 0);
        assertTrue( comparator.compare(entry3, entry2) > 0);

        // Check equals elements have the same order
        assertEquals(0, comparator.compare(entry1, entry1Copy));
        assertTrue(comparator.compare(entry1, entry2) * comparator.compare(entry1Copy, entry2) > 0);
    }
}
