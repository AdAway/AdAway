package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.junit.Assert.assertEquals;

import org.adaway.model.source.FilterListCatalog;
import org.junit.Test;

import java.time.ZonedDateTime;

public class HostsSourceFreshnessTest extends DbTest {
    private static final ZonedDateTime DOWNLOADED_AT =
            ZonedDateTime.parse("2026-10-14T00:00:00Z");

    @Test
    public void freshDefaultSubscriptionsNeedDownload() throws InterruptedException {
        this.hostsSourceDao.deleteAll();
        for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getDefaults()) {
            this.hostsSourceDao.insert(entry.toHostsSource());
        }

        assertEquals(4, this.hostsSourceDao.getEnabled().size());
        assertFreshness(0, 4);
    }

    @Test
    public void onlineDateWithoutLocalDownloadStillNeedsUpdate() throws InterruptedException {
        this.hostsSourceDao.updateOnlineModificationDate(EXTERNAL_SOURCE_ID, DOWNLOADED_AT);

        assertFreshness(0, 1);
    }

    @Test
    public void failedFirstDownloadStillNeedsUpdate() throws InterruptedException {
        this.hostsSourceDao.updateDownloadError(EXTERNAL_SOURCE_ID, "Network unavailable");

        assertFreshness(0, 1);
    }

    @Test
    public void downloadedEmptySourceIsUpToDate() throws InterruptedException {
        this.hostsSourceDao.updateModificationDates(
                EXTERNAL_SOURCE_ID, DOWNLOADED_AT, DOWNLOADED_AT);

        assertEquals(0, getSourceFromId(EXTERNAL_SOURCE_ID).getSize());
        assertFreshness(1, 0);
    }

    @Test
    public void downloadedSourcesKeepTheirDateBasedClassification() throws InterruptedException {
        this.hostsSourceDao.updateModificationDates(
                EXTERNAL_SOURCE_ID, DOWNLOADED_AT, DOWNLOADED_AT);
        insertSource(3, "https://freshness-older.test/hosts.txt");
        this.hostsSourceDao.updateModificationDates(
                3, DOWNLOADED_AT, DOWNLOADED_AT.minusDays(1));
        insertSource(4, "https://freshness-newer.test/hosts.txt");
        this.hostsSourceDao.updateModificationDates(
                4, DOWNLOADED_AT, DOWNLOADED_AT.plusDays(1));

        assertFreshness(2, 1);
    }

    @Test
    public void disabledSourcesAreExcludedRegardlessOfDownloadState()
            throws InterruptedException {
        this.hostsSourceDao.setSourceEnabled(EXTERNAL_SOURCE_ID, false);
        assertFreshness(0, 0);

        this.hostsSourceDao.updateOnlineModificationDate(EXTERNAL_SOURCE_ID, DOWNLOADED_AT);
        assertFreshness(0, 0);

        this.hostsSourceDao.updateModificationDates(
                EXTERNAL_SOURCE_ID, DOWNLOADED_AT, DOWNLOADED_AT.plusDays(1));
        assertFreshness(0, 0);

        this.hostsSourceDao.updateModificationDates(
                EXTERNAL_SOURCE_ID, DOWNLOADED_AT, DOWNLOADED_AT);
        assertFreshness(0, 0);
    }

    @Test
    public void enablingNeverDownloadedSourceMakesItNeedUpdate() throws InterruptedException {
        this.hostsSourceDao.applySourceSelection(EXTERNAL_SOURCE_ID, false);
        assertFreshness(0, 0);

        this.hostsSourceDao.applySourceSelection(EXTERNAL_SOURCE_ID, true);
        assertFreshness(0, 1);
    }

    @Test
    public void userRulesNeverContributeToSubscriptionFreshness() throws InterruptedException {
        this.hostsSourceDao.deleteAll();
        assertFreshness(0, 0);

        this.hostsSourceDao.updateModificationDates(USER_SOURCE_ID, DOWNLOADED_AT, DOWNLOADED_AT);
        assertFreshness(0, 0);

        this.hostsSourceDao.updateOnlineModificationDate(
                USER_SOURCE_ID, DOWNLOADED_AT.plusDays(1));
        assertFreshness(0, 0);
    }

    @Test
    public void clearingDownloadedPropertiesMakesSourceNeedUpdate() throws InterruptedException {
        this.hostsSourceDao.updateModificationDates(
                EXTERNAL_SOURCE_ID, DOWNLOADED_AT, DOWNLOADED_AT);
        assertFreshness(1, 0);

        this.hostsSourceDao.clearProperties(EXTERNAL_SOURCE_ID);
        assertFreshness(0, 1);
    }

    private void assertFreshness(int upToDate, int outdated) throws InterruptedException {
        assertEquals("Up-to-date subscriptions", upToDate,
                getOrAwaitValue(this.hostsSourceDao.countUpToDate()).intValue());
        assertEquals("Subscriptions needing an update", outdated,
                getOrAwaitValue(this.hostsSourceDao.countOutdated()).intValue());
    }
}
