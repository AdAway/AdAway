package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SiteCompatibilityAllowlistTest {
    @Test
    public void requiredDomainsProtectYnetFirstPartyBrowsingHosts() {
        List<String> domains = SiteCompatibilityAllowlist.REQUIRED_DOMAINS;

        assertTrue(domains.contains("ynet.co.il"));
        assertTrue(domains.contains("www.ynet.co.il"));
        assertTrue(domains.contains("images1.ynet.co.il"));
        assertTrue(domains.contains("ynet-pic1.yit.co.il"));
        assertTrue(domains.contains("vod-hls.ynethd.com"));
        assertTrue(domains.contains("vod-progressive.ynethd.com"));
    }

    @Test
    public void requiredDomainsStayNarrowAndDoNotAllowKnownTrackingHosts() {
        List<String> domains = SiteCompatibilityAllowlist.REQUIRED_DOMAINS;

        assertFalse(domains.contains("*.ynet.co.il"));
        assertFalse(domains.contains("stats.ynet.co.il"));
        assertFalse(domains.contains("p.ynet.co.il"));
        assertFalse(domains.contains("totalmedia2.ynet.co.il"));
        assertFalse(domains.contains("ynetbanneradmin.yit.co.il"));
        assertFalse(domains.contains("ynetads-10fd1.firebaseapp.com"));
    }

    @Test
    public void requiredDomainsHaveNoDuplicates() {
        List<String> domains = SiteCompatibilityAllowlist.REQUIRED_DOMAINS;
        Set<String> unique = new HashSet<>(domains);

        assertEquals(unique.size(), domains.size());
    }

    @Test
    public void buildItemCreatesEnabledUserAllowEntry() {
        HostListItem item = SiteCompatibilityAllowlist.buildItem("www.ynet.co.il");

        assertEquals("www.ynet.co.il", item.getHost());
        assertEquals(ListType.ALLOWED, item.getType());
        assertEquals(RuleKind.EXACT, item.getKind());
        assertEquals(HostsSource.USER_SOURCE_ID, item.getSourceId());
        assertTrue(item.isEnabled());
        assertEquals(0, item.getGeneration());
    }

    @Test
    public void ensureAllowlistCreatesUserSourceBeforeUserRows() throws Exception {
        String allowlist = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SiteCompatibilityAllowlist.java"));

        assertTrue(allowlist.contains("ensureUserSource(appContext,database);"
                + "HostListItemDao"));
        assertTrue(allowlist.contains("database.hostsSourceDao().insert(userSource);"));
        assertTrue(allowlist.indexOf("ensureUserSource(appContext,database);")
                < allowlist.indexOf("dao.insert(buildItem(domain));"));
    }

    @Test
    public void userSourceCreatedWithUserListCapabilities() throws Exception {
        String allowlist = readRepoFile(
                "app/src/main/java/org/adaway/model/source/SiteCompatibilityAllowlist.java");

        assertTrue(allowlist.contains("setId(HostsSource.USER_SOURCE_ID)"));
        assertTrue(allowlist.contains("setUrl(HostsSource.USER_SOURCE_URL)"));
        assertTrue(allowlist.contains("setAllowEnabled(true)"));
        assertTrue(allowlist.contains("setRedirectEnabled(true)"));
    }

    @Test
    public void databaseCreationSeedsSiteCompatibilityAllowlist() throws Exception {
        String appDatabase = compact(readRepoFile(
                "app/src/main/java/org/adaway/db/AppDatabase.java"));

        assertTrue(appDatabase.contains("WaTgSafetyAllowlist.ensureAllowlistSync(context);"
                + "SiteCompatibilityAllowlist.ensureAllowlistSync(context);"));
    }

    @Test
    public void appStartupBackfillsExistingDatabasesAndRefreshesRuntime() throws Exception {
        String application = compact(readRepoFile(
                "app/src/main/java/org/adaway/AdAwayApplication.java"));

        assertTrue(application.contains("if(SiteCompatibilityAllowlist."
                + "ensureAllowlistSync(this)){this.sourceModel.syncHostEntries();}"));
    }

    @Test
    public void defaultSubscriptionSeedsSiteCompatibilityBeforeReturning() throws Exception {
        String subscriber = compact(readRepoFile(
                "app/src/main/java/org/adaway/ui/onboarding/DefaultListsSubscriber.java"));

        assertTrue(subscriber.contains("WaTgSafetyAllowlist.ensureAllowlistSync(applicationContext);"
                + "SiteCompatibilityAllowlist.ensureAllowlistSync(applicationContext);"));
        assertTrue(subscriber.contains("hostsSourceDao.insertAll(toInsert);"));
        assertTrue(subscriber.indexOf("hostsSourceDao.insertAll(toInsert);")
                < subscriber.indexOf("ensureAllowlists.run();"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("..", relativePath);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }
}
