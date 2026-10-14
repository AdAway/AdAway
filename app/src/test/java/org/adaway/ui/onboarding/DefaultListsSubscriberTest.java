package org.adaway.ui.onboarding;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;

import org.adaway.db.dao.HostsSourceDao;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Unit tests for DefaultListsSubscriber.
 *
 * Exercises the real preparation and scheduling logic with an in-memory DAO double.
 * No Android database, activity, or network is needed.
 */
public class DefaultListsSubscriberTest {

    /**
     * Converting the default catalog entries via toHostsSource() yields
     * HostsSource objects with non-null labels and valid URLs.
     */
    @Test
    public void defaultEntries_convertToHostsSource_haveValidLabelsAndUrls() {
        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
        assertFalse("Catalog should have at least one default entry", defaults.isEmpty());

        for (FilterListCatalog.CatalogEntry entry : defaults) {
            HostsSource source = entry.toHostsSource();
            assertNotNull("toHostsSource() should not return null", source);
            assertNotNull("Label must not be null", source.getLabel());
            assertFalse("Label must not be empty", source.getLabel().isEmpty());
            assertNotNull("URL must not be null", source.getUrl());
            assertFalse("URL must not be empty", source.getUrl().isEmpty());
            assertTrue("Source should be enabled by default", source.isEnabled());
        }
    }

    /**
     * The converted list for bulk-insert contains the same count as getDefaults().
     */
    @Test
    public void defaultEntries_bulkConversion_matchesDefaultsCount() {
        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
        List<HostsSource> sources = new ArrayList<>();
        for (FilterListCatalog.CatalogEntry entry : defaults) {
            sources.add(entry.toHostsSource());
        }
        assertEquals("Converted list size must equal defaults list size",
                defaults.size(), sources.size());
    }

    @Test
    public void productionSeededDefaultsNeedDownloadWithoutAnotherInsertion() {
        SourceStore store = productionSeededStore();
        List<HostsSource> originalSources = new ArrayList<>(store.sources);
        for (HostsSource source : store.sources) {
            assertTrue(source.isEnabled());
            assertNull(source.getLocalModificationDate());
            assertNull(source.getOnlineModificationDate());
            assertEquals(0, source.getSize());
        }

        assertTrue(store.prepare());

        assertEquals(0, store.insertCalls);
        assertEquals(0, store.allowlistCalls);
        assertEquals(originalSources, store.sources);
        for (int index = 0; index < originalSources.size(); index++) {
            assertSame(originalSources.get(index), store.sources.get(index));
        }
    }

    @Test
    public void emptyDatabaseInsertsDefaultsAndAllowlistsBeforeDownload() {
        SourceStore store = new SourceStore();

        assertTrue(store.prepare());

        assertEquals(FilterListCatalog.getDefaults().size(), store.sources.size());
        assertEquals(1, store.insertCalls);
        assertEquals(1, store.allowlistCalls);
        assertTrue("Preparing again must still report pending downloads.", store.prepare());
        assertEquals(1, store.insertCalls);
        assertEquals(1, store.allowlistCalls);
    }

    @Test
    public void existingCustomSubscriptionAndDisabledDefaultArePreserved() {
        SourceStore store = new SourceStore();
        HostsSource custom = new HostsSource();
        custom.setId(42);
        custom.setLabel("My selected list");
        custom.setUrl("https://example.test/hosts");
        custom.setAllowEnabled(true);
        custom.setRedirectEnabled(true);
        HostsSource disabled = FilterListCatalog.getDefaults().get(0).toHostsSource();
        disabled.setId(43);
        disabled.setEnabled(false);
        store.sources.add(custom);
        store.sources.add(disabled);

        assertTrue(store.prepare());

        assertEquals(0, store.insertCalls);
        assertEquals(0, store.allowlistCalls);
        assertEquals(2, store.sources.size());
        assertSame(custom, store.sources.get(0));
        assertEquals(42, custom.getId());
        assertEquals("My selected list", custom.getLabel());
        assertEquals("https://example.test/hosts", custom.getUrl());
        assertTrue(custom.isEnabled());
        assertTrue(custom.isAllowEnabled());
        assertTrue(custom.isRedirectEnabled());
        assertSame(disabled, store.sources.get(1));
        assertFalse(disabled.isEnabled());

        custom.setLocalModificationDate(ZonedDateTime.now());
        assertFalse("A disabled, never-downloaded source must not trigger retrieval.",
                store.prepare());
    }

    @Test
    public void allDisabledDefaultsAreNotReenabledOrDownloaded() {
        SourceStore store = productionSeededStore();
        store.sources.forEach(source -> source.setEnabled(false));

        assertFalse(store.prepare());

        assertEquals(0, store.insertCalls);
        assertTrue(store.sources.stream().noneMatch(HostsSource::isEnabled));
    }

    @Test
    public void successfulEmptyDownloadsAndUserSourceDoNotTriggerInitialization() {
        SourceStore store = productionSeededStore();
        store.sources.forEach(source -> source.setLocalModificationDate(ZonedDateTime.now()));
        HostsSource userSource = new HostsSource();
        userSource.setId(HostsSource.USER_SOURCE_ID);
        userSource.setUrl(HostsSource.USER_SOURCE_URL);
        store.sources.add(userSource);

        assertFalse("Zero rules is not the same as never downloaded.", store.prepare());
        assertEquals(0, store.insertCalls);
    }

    @Test
    public void partiallyDownloadedSourcesStillNeedInitialDownload() {
        SourceStore store = productionSeededStore();
        store.sources.forEach(source -> source.setLocalModificationDate(ZonedDateTime.now()));
        HostsSource missing = store.sources.get(0);
        missing.setLocalModificationDate(null);
        missing.setOnlineModificationDate(ZonedDateTime.now());

        assertTrue("An online check alone does not mean a source was downloaded.", store.prepare());
        assertEquals(0, store.insertCalls);
    }

    @Test
    public void retainedInitializerQueuesOnlyOneDownloadAcrossRecreation() {
        SourceStore store = productionSeededStore();
        ViewModelStore retainedStore = new ViewModelStore();
        Queue<Runnable> disk = new ArrayDeque<>();
        Queue<Runnable> main = new ArrayDeque<>();
        AtomicInteger updates = new AtomicInteger();

        initializer(retainedStore).initialize(disk::add, main::add, store::prepare,
                updates::incrementAndGet);
        initializer(retainedStore).initialize(disk::add, main::add, store::prepare,
                updates::incrementAndGet);
        assertEquals(1, disk.size());
        assertTrue(main.isEmpty());
        disk.remove().run();

        initializer(retainedStore).initialize(disk::add, main::add, store::prepare,
                updates::incrementAndGet);
        assertTrue(disk.isEmpty());
        assertEquals(1, main.size());
        main.remove().run();

        initializer(retainedStore).initialize(disk::add, main::add, store::prepare,
                updates::incrementAndGet);
        assertTrue(disk.isEmpty());
        assertTrue(main.isEmpty());
        assertEquals(1, updates.get());
        assertEquals(0, store.insertCalls);
        retainedStore.clear();
    }

    @Test
    public void offlineAttemptDoesNotMarkSourcesDownloadedOrPreventLaterRetry() {
        SourceStore store = productionSeededStore();
        AtomicInteger attempts = new AtomicInteger();
        Runnable offlineUpdate = () -> {
            attempts.incrementAndGet();
            store.sources.forEach(source -> source.setLastDownloadError("Offline"));
        };
        InitialSourcesViewModel firstLaunch = new InitialSourcesViewModel();
        firstLaunch.initialize(Runnable::run, Runnable::run, store::prepare, offlineUpdate);
        firstLaunch.initialize(Runnable::run, Runnable::run, store::prepare, offlineUpdate);

        assertEquals("Recreation must not turn a failure into an automatic retry loop.",
                1, attempts.get());
        assertTrue("Failure must leave initial downloads eligible for retry.", store.prepare());
        store.sources.forEach(source -> assertNull(source.getLocalModificationDate()));

        InitialSourcesViewModel laterLaunch = new InitialSourcesViewModel();
        laterLaunch.initialize(Runnable::run, Runnable::run, store::prepare, () -> {
            attempts.incrementAndGet();
            store.sources.forEach(source -> {
                source.setLocalModificationDate(ZonedDateTime.now());
                source.setLastDownloadError(null);
            });
        });
        new InitialSourcesViewModel().initialize(Runnable::run, Runnable::run,
                store::prepare, attempts::incrementAndGet);

        assertEquals(2, attempts.get());
        assertFalse(store.prepare());
        assertEquals(0, store.insertCalls);
    }

    private static InitialSourcesViewModel initializer(ViewModelStore store) {
        return new ViewModelProvider(store, new ViewModelProvider.NewInstanceFactory())
                .get(InitialSourcesViewModel.class);
    }

    private static SourceStore productionSeededStore() {
        SourceStore store = new SourceStore();
        // Same conversion used by AppDatabase.initialize, not an empty Room test database.
        int id = HostsSource.USER_SOURCE_ID + 1;
        for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getDefaults()) {
            HostsSource source = entry.toHostsSource();
            source.setId(id++);
            store.sources.add(source);
        }
        assertFalse(store.sources.isEmpty());
        return store;
    }

    private static final class SourceStore {
        final List<HostsSource> sources = new ArrayList<>();
        int insertCalls;
        int allowlistCalls;
        final HostsSourceDao dao = (HostsSourceDao) Proxy.newProxyInstance(
                HostsSourceDao.class.getClassLoader(), new Class<?>[]{HostsSourceDao.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getAll":
                        case "getEnabled":
                            return sources.stream()
                                    .filter(source -> source.getId() != HostsSource.USER_SOURCE_ID)
                                    .filter(source -> !method.getName().equals("getEnabled")
                                            || source.isEnabled())
                                    .collect(Collectors.toList());
                        case "insertAll":
                            insertCalls++;
                            for (Object source : (List<?>) args[0]) {
                                sources.add((HostsSource) source);
                            }
                            return null;
                        default:
                            throw new AssertionError("Unexpected DAO operation: " + method.getName());
                    }
                });

        boolean prepare() {
            return DefaultListsSubscriber.prepareInitialSources(dao, () -> {
                assertFalse("Sources must exist before initializing allowlists.", sources.isEmpty());
                allowlistCalls++;
            });
        }
    }
}
