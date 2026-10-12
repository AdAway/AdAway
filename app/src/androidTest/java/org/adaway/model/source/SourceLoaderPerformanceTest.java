package org.adaway.model.source;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.util.Constants.HOSTS_FILENAME;
import static org.adaway.util.Constants.PREFS_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.root.RootModel;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.Hostnames;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connected performance gate for the hot update path: parse, raw bulk insert,
 * runtime sync, and root hosts file generation on a file-backed WAL database.
 */
@RunWith(AndroidJUnit4.class)
public class SourceLoaderPerformanceTest {
    private static final String TAG = "SourceLoaderPerfTest";
    private static final String DB_NAME = "source-loader-performance-test.db";
    private static final String ARG_SCALE_LINES = "adawayPerfLines";
    private static final String ARG_SCALE_PARSE_BUDGET_MS = "adawayPerfParseBudgetMs";
    private static final String ARG_SCALE_SYNC_BUDGET_MS = "adawayPerfSyncBudgetMs";
    private static final String ARG_SCALE_ROOT_WRITE_BUDGET_MS = "adawayPerfRootWriteBudgetMs";
    private static final String ARG_ROOT_WRITE_ROWS = "adawayRootWriteRows";
    private static final String ARG_ROOT_WRITE_IPV4_BUDGET_MS = "adawayRootWriteIpv4BudgetMs";
    private static final String ARG_ROOT_WRITE_IPV6_BUDGET_MS = "adawayRootWriteIpv6BudgetMs";
    private static final String ARG_ALLOW_REBUILD_BLOCKED_ROWS =
            "adawayAllowRebuildBlockedRows";
    private static final String ARG_ALLOW_REBUILD_EXACT_RULES =
            "adawayAllowRebuildExactRules";
    private static final String ARG_ALLOW_REBUILD_SUFFIX_RULES =
            "adawayAllowRebuildSuffixRules";
    private static final String ARG_ALLOW_REBUILD_REDIRECT_ROWS =
            "adawayAllowRebuildRedirectRows";
    private static final String ARG_ALLOW_REBUILD_SYNC_BUDGET_MS =
            "adawayAllowRebuildSyncBudgetMs";
    private static final String ARG_ALLOW_REBUILD_ROOT_WRITE_BUDGET_MS =
            "adawayAllowRebuildRootWriteBudgetMs";
    private static final String ARG_ALLOW_REBUILD_SEED_ROOT_STAGE =
            "adawayAllowRebuildSeedRootStage";
    private static final String ARG_ALLOW_REBUILD_SEED_RUNTIME_ROWS =
            "adawayAllowRebuildSeedRuntimeRows";
    private static final String ARG_ALLOW_STATS_BLOCKED_ROWS =
            "adawayAllowStatsBlockedRows";
    private static final String ARG_ALLOW_STATS_EXACT_RULES =
            "adawayAllowStatsExactRules";
    private static final String ARG_ALLOW_STATS_SUFFIX_RULES =
            "adawayAllowStatsSuffixRules";
    private static final String ARG_ALLOW_STATS_REFRESH_BUDGET_MS =
            "adawayAllowStatsRefreshBudgetMs";
    private static final String ARG_ALLOW_STATS_SEED_ROWS =
            "adawayAllowStatsSeedRows";
    private static final int SOURCE_ID = 2;
    private static final int GENERATION = 2;
    private static final int ALLOW_HEAVY_SEED_CHUNK_SIZE = 100_000;
    private static final String ROOT_STAGE_SOURCE_GENERATION_INDEX_NAME =
            "index_root_host_entries_stage_source_generation";
    private static final String ROOT_STAGE_SOURCE_GENERATION_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `" + ROOT_STAGE_SOURCE_GENERATION_INDEX_NAME +
                    "` ON `root_host_entries_stage` (`source_id`, `generation`)";
    private static final String ROOT_STAGE_GENERATION_SOURCE_INDEX_NAME =
            "index_root_host_entries_stage_generation_source";
    private static final String ROOT_STAGE_GENERATION_SOURCE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `" + ROOT_STAGE_GENERATION_SOURCE_INDEX_NAME +
                    "` ON `root_host_entries_stage` (`generation`, `source_id`)";
    private static final String ROOT_STAGE_REVERSE_HOST_INDEX_NAME =
            "index_root_host_entries_stage_reverse_host";
    private static final String ROOT_STAGE_REVERSE_HOST_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `" + ROOT_STAGE_REVERSE_HOST_INDEX_NAME +
                    "` ON `root_host_entries_stage` " +
                    "(`reverse_host`, `type`, `source_id`, `generation`)";

    private static final int EXACT_HOST_RULES = 5_000;
    private static final int ABP_SUFFIX_RULES = 2_000;
    private static final int DNSMASQ_SUFFIX_RULES = 1_000;
    private static final int SURGE_SUFFIX_RULES = 500;
    private static final int REDIRECT_RULES = 500;
    private static final int DUPLICATE_HOST_RULES = 500;
    private static final int SKIPPED_RULES = 500;
    private static final int TOTAL_LINES = EXACT_HOST_RULES
            + ABP_SUFFIX_RULES
            + DNSMASQ_SUFFIX_RULES
            + SURGE_SUFFIX_RULES
            + REDIRECT_RULES
            + DUPLICATE_HOST_RULES
            + SKIPPED_RULES;
    private static final int INSERTED_ROWS = TOTAL_LINES - SKIPPED_RULES;
    private static final int RUNTIME_ROWS = EXACT_HOST_RULES
            + ABP_SUFFIX_RULES
            + DNSMASQ_SUFFIX_RULES
            + SURGE_SUFFIX_RULES
            + REDIRECT_RULES;

    private static final long PARSE_INSERT_BUDGET_MS = 45_000L;
    private static final long SYNC_BUDGET_MS = 45_000L;
    private static final long ROOT_WRITE_BUDGET_MS = 10_000L;
    private static final int MAX_PROGRESS_EVENTS = 10;

    private Context context;
    private AppDatabase db;
    private HostsSource source;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        FilterSetStore.setGlobalEnabled(this.context, false);
        this.context.deleteDatabase(DB_NAME);
        this.db = Room.databaseBuilder(this.context, AppDatabase.class, DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .allowMainThreadQueries()
                .build();
        AppDatabase.optimizeCreatedDatabaseStorage(this.db.getOpenHelper().getWritableDatabase());
        this.hostListItemDao = this.db.hostsListItemDao();
        this.hostEntryDao = this.db.hostEntryDao();

        this.hostsSourceDao = this.db.hostsSourceDao();
        insertHostsMeta();
        insertSource(this.hostsSourceDao, USER_SOURCE_ID, HostsSource.USER_SOURCE_URL, false);
        insertSource(this.hostsSourceDao, SOURCE_ID, "https://perf.example.test/hosts.txt", true);
        this.source = this.hostsSourceDao.getAll()
                .stream()
                .filter(hostsSource -> hostsSource.getId() == SOURCE_ID)
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    @After
    public void tearDown() throws Exception {
        if (this.db != null) {
            this.db.close();
        }
        if (this.context != null) {
            this.context.deleteDatabase(DB_NAME);
        }
    }

    @Test
    public void parseInsertSyncAndRootApply_mixedRules10k_staysWithinBudget() throws Exception {
        SourceLoader loader = new SourceLoader(this.source, GENERATION);
        AtomicLong insertedByCallback = new AtomicLong();
        AtomicInteger progressEvents = new AtomicInteger();

        long parseStartMs = SystemClock.elapsedRealtime();
        int skipped = loader.parse(
                new BufferedReader(new StringReader(buildMixedRulesFixture())),
                this.hostListItemDao,
                this.db.getOpenHelper().getWritableDatabase(),
                inserted -> {
                    insertedByCallback.addAndGet(inserted);
                    progressEvents.incrementAndGet();
                },
                null);
        long parseMs = SystemClock.elapsedRealtime() - parseStartMs;

        assertEquals(SKIPPED_RULES, skipped);
        assertEquals(INSERTED_ROWS, insertedByCallback.get());
        assertEquals(INSERTED_ROWS, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        assertEquals(INSERTED_ROWS, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries_stage " +
                        "WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        assertTrue("SourceLoader progress events exceeded " + MAX_PROGRESS_EVENTS + ": "
                + progressEvents.get(), progressEvents.get() <= MAX_PROGRESS_EVENTS);
        assertTrue("SourceLoader parse+insert exceeded " + PARSE_INSERT_BUDGET_MS
                + "ms: " + parseMs + "ms", parseMs < PARSE_INSERT_BUDGET_MS);

        setActiveGeneration(GENERATION);
        long syncStartMs = SystemClock.elapsedRealtime();
        rebuildRuntimeEntries();
        long syncMs = SystemClock.elapsedRealtime() - syncStartMs;

        assertEquals(RUNTIME_ROWS, this.hostEntryDao.getAll().size());
        assertTrue("HostEntryDao.sync after 10k-line import exceeded " + SYNC_BUDGET_MS
                + "ms: " + syncMs + "ms", syncMs < SYNC_BUDGET_MS);

        long rootRows = materializedRootRowCount();
        RootWriteResult rootWrite = createRootHostsFileWithTestDatabase(false);

        System.out.println("SourceLoaderPerformanceTest lines=" + TOTAL_LINES
                + " inserted=" + INSERTED_ROWS
                + " runtimeRows=" + RUNTIME_ROWS
                + " progressEvents=" + progressEvents.get()
                + " parseMs=" + parseMs
                + " syncMs=" + syncMs
                + " rootRows=" + rootRows
                + " rootWriteMs=" + rootWrite.elapsedMs
                + " rootWriteBytes=" + rootWrite.bytes);
        assertEquals(RUNTIME_ROWS, rootRows);
        assertRootWriteBytes(rootWrite, rootRows);
        assertTrue("Root hosts file write after 10k-line import exceeded "
                + ROOT_WRITE_BUDGET_MS + "ms: " + rootWrite.elapsedMs + "ms",
                rootWrite.elapsedMs < ROOT_WRITE_BUDGET_MS);
    }

    @Test
    public void parseInsertSyncAndRootApply_requestedScale_recordsBenchmark() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        int totalLines = getPositiveIntArgument(arguments, ARG_SCALE_LINES, 0);
        assumeTrue("Set instrumentation arg " + ARG_SCALE_LINES
                + " to run the large filter pipeline benchmark", totalLines > 0);

        ScaleFixture fixture = ScaleFixture.forTotalLines(totalLines);
        SourceLoader loader = new SourceLoader(this.source, GENERATION);
        AtomicLong insertedByCallback = new AtomicLong();
        AtomicInteger progressEvents = new AtomicInteger();

        long parseStartMs = SystemClock.elapsedRealtime();
        int skipped = loader.parse(
                new GeneratedRulesReader(fixture),
                this.hostListItemDao,
                this.db.getOpenHelper().getWritableDatabase(),
                inserted -> {
                    insertedByCallback.addAndGet(inserted);
                    progressEvents.incrementAndGet();
                },
                null);
        long parseMs = SystemClock.elapsedRealtime() - parseStartMs;

        assertEquals(fixture.skippedRules, skipped);
        assertEquals(fixture.insertedRows, insertedByCallback.get());
        assertEquals(fixture.insertedRows, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        assertEquals(fixture.insertedRows, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries_stage " +
                        "WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        this.hostsSourceDao.updateSizeForGeneration(SOURCE_ID, GENERATION);

        setActiveGeneration(GENERATION);
        long syncStartMs = SystemClock.elapsedRealtime();
        rebuildRuntimeEntries();
        long syncMs = SystemClock.elapsedRealtime() - syncStartMs;

        assertEquals(expectedMaterializedRuntimeRows(fixture.runtimeRows),
                scalarLong("SELECT COUNT(*) FROM host_entries"));

        long rootRows = materializedRootRowCount();
        RootWriteResult rootWrite = createRootHostsFileWithTestDatabase(false);

        System.out.println("SourceLoaderScaleBenchmark lines=" + fixture.totalLines
                + " inserted=" + fixture.insertedRows
                + " runtimeRows=" + fixture.runtimeRows
                + " progressEvents=" + progressEvents.get()
                + " parseMs=" + parseMs
                + " syncMs=" + syncMs
                + " rootRows=" + rootRows
                + " rootWriteMs=" + rootWrite.elapsedMs
                + " rootWriteBytes=" + rootWrite.bytes);
        assertEquals(fixture.runtimeRows, rootRows);
        assertRootWriteBytes(rootWrite, rootRows);

        assertWithinOptionalBudget(arguments, ARG_SCALE_PARSE_BUDGET_MS, parseMs);
        assertWithinOptionalBudget(arguments, ARG_SCALE_SYNC_BUDGET_MS, syncMs);
        assertWithinOptionalBudget(arguments, ARG_SCALE_ROOT_WRITE_BUDGET_MS,
                rootWrite.elapsedMs);
    }

    @Test
    public void sqlUpdateDedupAndCarryForward_requestedScale_recordsBenchmark() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        int totalLines = getPositiveIntArgument(arguments, ARG_SCALE_LINES, 0);
        assumeTrue("Set instrumentation arg " + ARG_SCALE_LINES
                + " to run the large SQL dedup/carry-forward benchmark", totalLines > 0);
        assumeTrue(ARG_SCALE_LINES + " must be at least 100 for overlap coverage",
                totalLines >= 100);

        int carryForwardSourceId = 5;
        int oldGeneration = GENERATION - 1;
        HostsSourceDao hostsSourceDao = this.db.hostsSourceDao();
        insertSource(hostsSourceDao, carryForwardSourceId,
                "https://perf.example.test/sql-carry-forward.txt", true);

        ScaleFixture fixture = ScaleFixture.forTotalLines(totalLines);
        SourceLoader loader = new SourceLoader(this.source, GENERATION);
        AtomicLong insertedByCallback = new AtomicLong();
        AtomicInteger progressEvents = new AtomicInteger();
        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(this.db.getOpenHelper().getWritableDatabase());

        long parseMs;
        long importedCountMs;
        long carryForwardMs;
        long dedupCountMs;
        long runtimeCountMs;
        int copied;
        try {
            long parseStartMs = SystemClock.elapsedRealtime();
            int skipped = loader.parse(
                    new GeneratedRulesReader(fixture),
                    this.hostListItemDao,
                    this.db.getOpenHelper().getWritableDatabase(),
                    inserted -> {
                        insertedByCallback.addAndGet(inserted);
                        progressEvents.incrementAndGet();
                    },
                    deduper);
            parseMs = SystemClock.elapsedRealtime() - parseStartMs;
            logScalePhase("parse", parseMs);

            assertEquals(fixture.skippedRules, skipped);
            assertEquals(fixture.insertedRows, insertedByCallback.get());
            long importedCountStartMs = SystemClock.elapsedRealtime();
            assertEquals(fixture.runtimeRows, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                    SOURCE_ID, GENERATION));
            importedCountMs = SystemClock.elapsedRealtime() - importedCountStartMs;
            logScalePhase("imported-count", importedCountMs);

            seedCarryForwardRows(carryForwardSourceId, oldGeneration, fixture);
            long carryForwardStartMs = SystemClock.elapsedRealtime();
            copied = deduper.copyUnseenSourceGeneration(
                    carryForwardSourceId, oldGeneration, GENERATION);
            carryForwardMs = SystemClock.elapsedRealtime() - carryForwardStartMs;
            logScalePhase("carry-forward", carryForwardMs);

            assertEquals(fixture.carryForwardCopiedRows(), copied);
            long dedupCountStartMs = SystemClock.elapsedRealtime();
            assertEquals((long) fixture.runtimeRows + fixture.carryForwardCopiedRows(),
                    deduper.count());
            dedupCountMs = SystemClock.elapsedRealtime() - dedupCountStartMs;
            logScalePhase("dedup-count", dedupCountMs);
        } finally {
            deduper.drop();
        }
        assertScratchTableAbsent();
        this.hostsSourceDao.updateSizeForGeneration(SOURCE_ID, GENERATION);
        this.hostsSourceDao.updateSizeForGeneration(carryForwardSourceId, GENERATION);

        setActiveGeneration(GENERATION);
        long syncStartMs = SystemClock.elapsedRealtime();
        rebuildRuntimeEntries();
        long syncMs = SystemClock.elapsedRealtime() - syncStartMs;
        logScalePhase("runtime-rebuild", syncMs);

        long expectedRuntimeRows = (long) fixture.runtimeRows + fixture.carryForwardRuntimeRows();
        long runtimeCountStartMs = SystemClock.elapsedRealtime();
        assertEquals(expectedMaterializedRuntimeRows(expectedRuntimeRows),
                scalarLong("SELECT COUNT(*) FROM host_entries"));
        runtimeCountMs = SystemClock.elapsedRealtime() - runtimeCountStartMs;
        logScalePhase("runtime-count", runtimeCountMs);

        long rootRows = materializedRootRowCount();
        RootWriteResult rootWrite = createRootHostsFileWithTestDatabase(false);
        logScalePhase("root-write", rootWrite.elapsedMs);

        System.out.println("SourceLoaderSqlDedupCarryForwardScaleBenchmark lines="
                + fixture.totalLines
                + " inserted=" + fixture.runtimeRows
                + " copied=" + copied
                + " membershipRows=" + ((long) fixture.runtimeRows + copied)
                + " runtimeRows=" + expectedRuntimeRows
                + " progressEvents=" + progressEvents.get()
                + " parseMs=" + parseMs
                + " importedCountMs=" + importedCountMs
                + " carryForwardMs=" + carryForwardMs
                + " dedupCountMs=" + dedupCountMs
                + " syncMs=" + syncMs
                + " runtimeCountMs=" + runtimeCountMs
                + " rootRows=" + rootRows
                + " rootWriteMs=" + rootWrite.elapsedMs
                + " rootWriteBytes=" + rootWrite.bytes);
        assertEquals(expectedRuntimeRows, rootRows);
        assertRootWriteBytes(rootWrite, rootRows);

        assertWithinOptionalBudget(arguments, ARG_SCALE_PARSE_BUDGET_MS, parseMs);
        assertWithinOptionalBudget(arguments, ARG_SCALE_SYNC_BUDGET_MS, syncMs);
        assertWithinOptionalBudget(arguments, ARG_SCALE_ROOT_WRITE_BUDGET_MS,
                rootWrite.elapsedMs);
    }

    @Test
    public void rootModelCreateHostsFile_requestedRows_recordsWriteBenchmark() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        int rootRows = getPositiveIntArgument(arguments, ARG_ROOT_WRITE_ROWS, 0);
        assumeTrue("Set instrumentation arg " + ARG_ROOT_WRITE_ROWS
                + " to run the root hosts file write benchmark", rootRows > 0);

        long seedStartMs = SystemClock.elapsedRealtime();
        seedRootExportRows(rootRows);
        long seedMs = SystemClock.elapsedRealtime() - seedStartMs;

        try {
            RootWriteResult ipv4 = createRootHostsFileWithTestDatabase(false);

            RootWriteResult ipv6 = createRootHostsFileWithTestDatabase(true);

            System.out.println("RootModelHostsFileWriteBenchmark rows=" + rootRows
                    + " seedMs=" + seedMs
                    + " ipv4Ms=" + ipv4.elapsedMs
                    + " ipv4Bytes=" + ipv4.bytes
                    + " ipv6Ms=" + ipv6.elapsedMs
                    + " ipv6Bytes=" + ipv6.bytes);

            assertTrue("Generated IPv4 hosts file should not be empty", ipv4.bytes > rootRows);
            assertTrue("IPv6-enabled hosts file should be larger than IPv4-only output",
                    ipv6.bytes > ipv4.bytes);
            assertWithinOptionalBudget(arguments, ARG_ROOT_WRITE_IPV4_BUDGET_MS,
                    ipv4.elapsedMs);
            assertWithinOptionalBudget(arguments, ARG_ROOT_WRITE_IPV6_BUDGET_MS,
                    ipv6.elapsedMs);
        } finally {
            this.context.deleteFile(HOSTS_FILENAME);
        }
    }

    @Test
    public void rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        int blockedRows = getPositiveIntArgument(arguments, ARG_ALLOW_REBUILD_BLOCKED_ROWS, 1_000);
        assumeTrue("Set instrumentation arg " + ARG_ALLOW_REBUILD_BLOCKED_ROWS
                + "=0 to skip the allow-heavy runtime rebuild benchmark", blockedRows > 0);

        int exactAllowRules = getPositiveIntArgument(arguments, ARG_ALLOW_REBUILD_EXACT_RULES,
                Math.max(1, blockedRows / 100));
        int suffixAllowRules = getPositiveIntArgument(arguments, ARG_ALLOW_REBUILD_SUFFIX_RULES,
                Math.max(1, blockedRows / 200));
        int redirectRows = getPositiveIntArgument(arguments, ARG_ALLOW_REBUILD_REDIRECT_ROWS, 0);
        AllowHeavyFixture fixture = AllowHeavyFixture.create(
                blockedRows, exactAllowRules, suffixAllowRules, redirectRows);
        boolean seedRootStage = getBooleanArgument(arguments, ARG_ALLOW_REBUILD_SEED_ROOT_STAGE,
                false);
        boolean seedRuntimeRows = getBooleanArgument(arguments,
                ARG_ALLOW_REBUILD_SEED_RUNTIME_ROWS, true);
        assumeTrue("Metadata-only runtime seed requires " + ARG_ALLOW_REBUILD_SEED_ROOT_STAGE +
                "=true", seedRuntimeRows || seedRootStage);
        long seedStartMs = SystemClock.elapsedRealtime();
        if (seedRuntimeRows) {
            seedAllowHeavyRuntimeRows(fixture);
        } else {
            seedAllowHeavyRuntimeMetadata(fixture);
        }
        long stageRows = 0L;
        if (seedRootStage) {
            seedAllowHeavyRootExportStageRows(fixture);
            stageRows = scalarLong("SELECT COUNT(*) FROM root_host_entries_stage " +
                    "WHERE source_id = ? AND generation = ?", SOURCE_ID, GENERATION);
            assertEquals(fixture.stagedRootRows(), stageRows);
        }
        long seedMs = SystemClock.elapsedRealtime() - seedStartMs;
        long checkpointStartMs = SystemClock.elapsedRealtime();
        checkpointWal();
        long checkpointMs = SystemClock.elapsedRealtime() - checkpointStartMs;
        System.out.println("HostEntryAllowHeavySeedBenchmark blockedRows="
                + fixture.blockedRows
                + " exactAllowRules=" + fixture.exactAllowRules
                + " suffixAllowRules=" + fixture.suffixAllowRules
                + " redirectRows=" + fixture.redirectRows
                + " seedRuntimeRows=" + seedRuntimeRows
                + " seedRootStage=" + seedRootStage
                + " stageRows=" + stageRows
                + " seedMs=" + seedMs
                + " checkpointMs=" + checkpointMs);
        setActiveGeneration(GENERATION);

        long syncStartMs = SystemClock.elapsedRealtime();
        rebuildRuntimeEntries();
        long syncMs = SystemClock.elapsedRealtime() - syncStartMs;

        long expectedActiveRows = (long) fixture.blockedRows + fixture.redirectRows
                + fixture.exactAllowRules + fixture.suffixAllowRules;
        boolean materializedRuntimeCacheExpected =
                expectedActiveRows <= HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS;
        long runtimeRows = scalarLong("SELECT COUNT(*) FROM host_entries");
        assertEquals(materializedRuntimeCacheExpected ? fixture.expectedRuntimeRows() : 0,
                runtimeRows);

        long rootRows = materializedRootRowCount();
        RootWriteResult rootWrite = createRootHostsFileWithTestDatabase(false);
        assertEquals(fixture.expectedRootRows(), rootRows);
        assertRootWriteBytes(rootWrite, rootRows);
        assertEquals(expectedActiveRows, this.hostEntryDao.getActiveRuntimeRuleCountNow());

        System.out.println("HostEntryAllowHeavyRebuildBenchmark blockedRows="
                + fixture.blockedRows
                + " exactBlockedRows=" + fixture.exactBlockedRows
                + " suffixBlockedRows=" + fixture.suffixBlockedRows
                + " exactAllowRules=" + fixture.exactAllowRules
                + " suffixAllowRules=" + fixture.suffixAllowRules
                + " redirectRows=" + fixture.redirectRows
                + " exactAllowMatches=" + fixture.exactAllowMatches
                + " suffixAllowExactMatches=" + fixture.suffixAllowExactMatches
                + " suffixAllowSuffixMatches=" + fixture.suffixAllowSuffixMatches
                + " runtimeRows=" + runtimeRows
                + " rootRows=" + rootRows
                + " materializedRuntimeCache=" + materializedRuntimeCacheExpected
                + " seedRuntimeRows=" + seedRuntimeRows
                + " seedRootStage=" + seedRootStage
                + " stageRows=" + stageRows
                + " syncMs=" + syncMs
                + " rootRows=" + rootRows
                + " rootWriteMs=" + rootWrite.elapsedMs
                + " rootWriteBytes=" + rootWrite.bytes);

        assertWithinOptionalBudget(arguments, ARG_ALLOW_REBUILD_SYNC_BUDGET_MS, syncMs);
        assertWithinOptionalBudget(arguments, ARG_ALLOW_REBUILD_ROOT_WRITE_BUDGET_MS,
                rootWrite.elapsedMs);
    }

    @Test
    public void refreshRuntimeStats_allowHeavyRequestedRows_recordsBenchmark() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        int blockedRows = getPositiveIntArgument(arguments, ARG_ALLOW_STATS_BLOCKED_ROWS, 1_000);
        assumeTrue("Set instrumentation arg " + ARG_ALLOW_STATS_BLOCKED_ROWS
                + "=0 to skip the allow-heavy runtime stats benchmark", blockedRows > 0);

        int exactAllowRules = getPositiveIntArgument(arguments, ARG_ALLOW_STATS_EXACT_RULES,
                Math.max(1, blockedRows / 100));
        int suffixAllowRules = getPositiveIntArgument(arguments, ARG_ALLOW_STATS_SUFFIX_RULES,
                Math.max(1, blockedRows / 200));
        AllowHeavyFixture fixture = AllowHeavyFixture.create(
                blockedRows, exactAllowRules, suffixAllowRules, 0);
        boolean seedRows = getBooleanArgument(arguments, ARG_ALLOW_STATS_SEED_ROWS, true);
        long seedStartMs = SystemClock.elapsedRealtime();
        if (seedRows) {
            seedAllowHeavyRuntimeRows(fixture);
        } else {
            seedAllowHeavyRuntimeMetadata(fixture);
        }
        long seedMs = SystemClock.elapsedRealtime() - seedStartMs;
        long checkpointMs = 0L;
        if (seedRows) {
            long checkpointStartMs = SystemClock.elapsedRealtime();
            checkpointWal();
            checkpointMs = SystemClock.elapsedRealtime() - checkpointStartMs;
        }
        setActiveGeneration(GENERATION);

        long statsStartMs = SystemClock.elapsedRealtime();
        this.hostEntryDao.refreshStatsFromActiveGeneration();
        long statsMs = SystemClock.elapsedRealtime() - statsStartMs;

        long expectedActiveRows = (long) fixture.blockedRows + fixture.redirectRows
                + fixture.exactAllowRules + fixture.suffixAllowRules;
        assertEquals(fixture.blockedRows, this.hostEntryDao.getBlockedEntryCountNow());
        assertEquals(fixture.exactBlockedRows,
                this.hostEntryDao.getBlockedExactEntryCountNow());
        assertEquals(expectedActiveRows, this.hostEntryDao.getActiveRuntimeRuleCountNow());
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM host_entries"));
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM root_host_entries"));

        System.out.println("HostEntryAllowHeavyStatsRefreshBenchmark blockedRows="
                + fixture.blockedRows
                + " exactBlockedRows=" + fixture.exactBlockedRows
                + " suffixBlockedRows=" + fixture.suffixBlockedRows
                + " exactAllowRules=" + fixture.exactAllowRules
                + " suffixAllowRules=" + fixture.suffixAllowRules
                + " activeRows=" + expectedActiveRows
                + " seedRows=" + seedRows
                + " seedMs=" + seedMs
                + " checkpointMs=" + checkpointMs
                + " statsMs=" + statsMs);

        assertWithinOptionalBudget(arguments, ARG_ALLOW_STATS_REFRESH_BUDGET_MS, statsMs);
    }

    @Test
    public void parseReadFailure_rollsBackTargetGenerationAndKeepsActiveRows() {
        int activeGeneration = GENERATION - 1;
        insertRawHostListItem(SOURCE_ID, activeGeneration, "active.example.test");
        setActiveGeneration(activeGeneration);

        SourceLoader loader = new SourceLoader(this.source, GENERATION);

        try {
            loader.parse(failingAfterFirstLineReader(), this.hostListItemDao,
                    this.db.getOpenHelper().getWritableDatabase(), null, null);
            fail("Expected parse failure for a reader that throws mid-stream");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Failed to parse hosts source"));
        }

        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                SOURCE_ID, activeGeneration));
    }

    @Test
    public void sqlUpdateDeduper_preservesSourceOwnershipAndKeepsRedirectTargets() {
        int secondSourceId = 3;
        HostsSourceDao hostsSourceDao = this.db.hostsSourceDao();
        insertSource(hostsSourceDao, secondSourceId, "https://perf.example.test/other.txt", true);
        HostsSource secondSource = hostsSourceDao.getAll()
                .stream()
                .filter(hostsSource -> hostsSource.getId() == secondSourceId)
                .findFirst()
                .orElseThrow(AssertionError::new);

        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(this.db.getOpenHelper().getWritableDatabase());
        try {
            SourceLoader firstLoader = new SourceLoader(this.source, GENERATION);
            SourceLoader secondLoader = new SourceLoader(secondSource, GENERATION);

            firstLoader.parse(
                    new BufferedReader(new StringReader(
                            "0.0.0.0 ads.example\n1.1.1.1 redirect.example\n")),
                    this.hostListItemDao,
                    this.db.getOpenHelper().getWritableDatabase(),
                    null,
                    deduper);
            assertEquals(0L, deduper.pendingCount());
            secondLoader.parse(
                    new BufferedReader(new StringReader(
                            "0.0.0.0 ads.example\n2.2.2.2 redirect.example\n")),
                    this.hostListItemDao,
                    this.db.getOpenHelper().getWritableDatabase(),
                    null,
                    deduper);
            assertEquals(0L, deduper.pendingCount());

            assertEquals(4L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE generation = ?", GENERATION));
            assertEquals(2L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND generation = ?",
                    "ads.example", GENERATION));
            assertEquals(2L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND generation = ?",
                    "redirect.example", GENERATION));
            assertEquals(4L, deduper.count());

            setActiveGeneration(GENERATION);
            rebuildRuntimeEntries();
            assertEquals(1L, scalarLong(
                    "SELECT COUNT(*) FROM host_entries WHERE host = ?", "ads.example"));

            hostsSourceDao.setSourceItemsEnabled(SOURCE_ID, false);
            rebuildRuntimeEntries();
            assertEquals(1L, scalarLong(
                    "SELECT COUNT(*) FROM host_entries WHERE host = ?", "ads.example"));
        } finally {
            deduper.drop();
        }
        assertScratchTableAbsent();
    }

    @Test
    public void sqlUpdateDeduper_largeRootExportPrefersRedirectOverBlockedDuplicate() {
        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(this.db.getOpenHelper().getWritableDatabase());
        try {
            SourceLoader loader = new SourceLoader(this.source, GENERATION);
            loader.parse(
                    new BufferedReader(new StringReader(
                            "0.0.0.0 redirect-shadow.example\n" +
                                    "1.1.1.1 redirect-shadow.example\n" +
                                    "0.0.0.0 duplicate-root.example\n" +
                                    "||duplicate-root.example^\n")),
                    this.hostListItemDao,
                    this.db.getOpenHelper().getWritableDatabase(),
                    null,
                    deduper);
        } finally {
            deduper.drop();
        }
        assertScratchTableAbsent();

        this.hostsSourceDao.updateRuleStats(
                SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                3, 2, 0, 1);
        setActiveGeneration(GENERATION);

        rebuildRuntimeEntries();

        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM host_entries"));
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM root_host_entries"));
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries " +
                        "WHERE host = ? AND type = 2 AND redirection = ?",
                "redirect-shadow.example", "1.1.1.1"));
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries WHERE host = ? AND type = 0",
                "redirect-shadow.example"));
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries WHERE host = ? AND type = 0",
                "duplicate-root.example"));
    }

    @Test
    public void sqlUpdateDeduper_carryForwardSkipsAlreadySeenRowsAndMarksCopiedRows() {
        int carryForwardSourceId = 4;
        int oldGeneration = GENERATION - 1;
        HostsSourceDao hostsSourceDao = this.db.hostsSourceDao();
        insertSource(hostsSourceDao, carryForwardSourceId,
                "https://perf.example.test/carry-forward.txt", true);

        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(this.db.getOpenHelper().getWritableDatabase());
        try {
            SourceLoader changedLoader = new SourceLoader(this.source, GENERATION);
            changedLoader.parse(
                    new BufferedReader(new StringReader(
                            "0.0.0.0 ads.example\n" +
                                    "0.0.0.0 null-redirection.example\n" +
                                    "1.1.1.1 redirect.example\n")),
                    this.hostListItemDao,
                    this.db.getOpenHelper().getWritableDatabase(),
                    null,
                    deduper);
            assertEquals(0L, deduper.pendingCount());

            insertRawHostListItem(carryForwardSourceId, oldGeneration, "ads.example");
            insertRawHostListItem(carryForwardSourceId, oldGeneration, "unique.example");
            insertRawHostListItemWithRedirection(
                    carryForwardSourceId, oldGeneration, "null-redirection.example", "");
            insertRawRedirectHostListItem(
                    carryForwardSourceId, oldGeneration, "redirect.example", "1.1.1.1");
            insertRawRedirectHostListItem(
                    carryForwardSourceId, oldGeneration, "redirect.example", "2.2.2.2");

            int copied = deduper.copyUnseenSourceGeneration(
                    carryForwardSourceId, oldGeneration, GENERATION);

            assertEquals(5, copied);
            assertEquals(0L, deduper.pendingCount());
            assertEquals(8L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE generation = ?", GENERATION));
            assertEquals(2L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND generation = ?",
                    "ads.example", GENERATION));
            assertEquals(1L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND generation = ?",
                    "unique.example", GENERATION));
            assertEquals(2L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND generation = ?",
                    "null-redirection.example", GENERATION));
            assertEquals(3L, scalarLong(
                    "SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND generation = ?",
                    "redirect.example", GENERATION));
            assertEquals(8L, deduper.count());
        } finally {
            deduper.drop();
        }
        assertScratchTableAbsent();
    }

    private void insertHostsMeta() {
        SupportSQLiteDatabase database = this.db.getOpenHelper().getWritableDatabase();
        database.execSQL("INSERT OR IGNORE INTO hosts_meta " +
                "(id, active_generation) VALUES (0, 0)");
        database.execSQL("INSERT OR IGNORE INTO hosts_stats " +
                "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, " +
                "active_rule_count, root_export_materialized) VALUES (0, 0, 0, 0, 0, 0, 0)");
    }

    private static void insertSource(HostsSourceDao hostsSourceDao, int id, String url,
            boolean redirectEnabled) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(true);
        source.setRedirectEnabled(redirectEnabled);
        hostsSourceDao.insert(source);
    }

    private void setActiveGeneration(int generation) {
        this.db.getOpenHelper().getWritableDatabase()
                .execSQL("INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, "
                        + generation + ")");
    }

    private void insertRawHostListItem(int sourceId, int generation, String host) {
        this.db.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO hosts_lists (host, reverse_host, type, kind, enabled, " +
                        "source_id, generation) VALUES (?, ?, 0, 0, 1, ?, ?)",
                new Object[]{host, Hostnames.reverseLabels(host), sourceId, generation});
    }

    private void insertRawHostListItemWithRedirection(
            int sourceId, int generation, String host, String redirection) {
        this.db.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO hosts_lists (host, reverse_host, type, kind, enabled, " +
                        "redirection, source_id, generation) VALUES (?, ?, 0, 0, 1, ?, ?, ?)",
                new Object[]{host, Hostnames.reverseLabels(host), redirection, sourceId,
                        generation});
    }

    private void insertRawRedirectHostListItem(
            int sourceId, int generation, String host, String redirection) {
        this.db.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO hosts_lists (host, reverse_host, type, kind, enabled, " +
                        "redirection, source_id, generation) VALUES (?, ?, 2, 0, 1, ?, ?, ?)",
                new Object[]{host, Hostnames.reverseLabels(host), redirection, sourceId,
                        generation});
    }

    private void seedCarryForwardRows(int sourceId, int generation, ScaleFixture fixture) {
        insertRawHostListItem(sourceId, generation, "ads0.example.com");
        insertRawRedirectHostListItem(
                sourceId, generation, "redirect0.example.dev", "8.8.8.1");
        insertRawHostListItem(sourceId, generation, "carry-forward-a.example.com");
        insertRawHostListItem(sourceId, generation, "carry-forward-b.example.com");
        insertRawRedirectHostListItem(
                sourceId, generation, "carry-forward-redirect.example.com", "9.9.9.9");
        assertTrue(fixture.runtimeRows >= 2);
    }

    private void seedRootExportRows(int rows) {
        this.db.runInTransaction(() -> {
            this.db.getOpenHelper().getWritableDatabase()
                    .execSQL("DELETE FROM root_host_entries");
            SupportSQLiteStatement insert = this.db.getOpenHelper().getWritableDatabase()
                    .compileStatement("INSERT INTO root_host_entries " +
                            "(host, kind, type, redirection) VALUES (?, 0, ?, ?)");
            for (int i = 0; i < rows; i++) {
                insert.clearBindings();
                insert.bindString(1, "writer" + i + ".example.test");
                if (i % 100 == 0) {
                    insert.bindLong(2, 2);
                    insert.bindString(3, "8.8.8.8");
                } else {
                    insert.bindLong(2, 0);
                    insert.bindNull(3);
                }
                insert.executeInsert();
            }
            int redirectedRows = (rows + 99) / 100;
            this.db.getOpenHelper().getWritableDatabase()
                    .execSQL("UPDATE hosts_stats SET blocked_count = ?, blocked_exact_count = ?, " +
                                    "redirected_count = ?, active_rule_count = ? WHERE id = 0",
                            new Object[]{rows - redirectedRows, rows - redirectedRows,
                                    redirectedRows, rows});
            this.hostEntryDao.setRootExportMaterialized(true);
        });
    }

    private void seedAllowHeavyRuntimeRows(AllowHeavyFixture fixture) {
        long startedMs = SystemClock.elapsedRealtime();
        this.db.runInTransaction(() -> {
            SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
            createAllowHeavySeedDigits(writableDb);
            long phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRows(writableDb, fixture.exactBlockedRows,
                    exactBlockedHostExpression(fixture), exactBlockedReverseHostExpression(fixture),
                    0, 0, SOURCE_ID, GENERATION);
            printAllowHeavySeedPhase("exact-blocked", fixture.exactBlockedRows, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRows(writableDb, fixture.suffixBlockedRows,
                    suffixBlockedHostExpression(fixture),
                    suffixBlockedReverseHostExpression(fixture), 0, 1, SOURCE_ID, GENERATION);
            printAllowHeavySeedPhase("suffix-blocked", fixture.suffixBlockedRows, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRedirectRows(writableDb, fixture.redirectRows,
                    redirectHostExpression(), redirectReverseHostExpression(), SOURCE_ID,
                    GENERATION);
            printAllowHeavySeedPhase("redirect", fixture.redirectRows, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRows(writableDb, fixture.exactAllowRules,
                    "'exact-allow' || __i__ || '.allowperf.example.test'",
                    "'test.example.allowperf.exact-allow' || __i__",
                    1, 0, USER_SOURCE_ID, 0);
            printAllowHeavySeedPhase("exact-allow", fixture.exactAllowRules, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRows(writableDb, fixture.suffixAllowRules,
                    "'suffix-allow' || __i__ || '.allowperf.example.test'",
                    "'test.example.allowperf.suffix-allow' || __i__",
                    1, 1, USER_SOURCE_ID, 0);
            printAllowHeavySeedPhase("suffix-allow", fixture.suffixAllowRules, phaseStartMs);
        });
        printAllowHeavySeedPhase("total", fixture.blockedRows + fixture.redirectRows
                + fixture.exactAllowRules
                + fixture.suffixAllowRules, startedMs);
        int userRules = fixture.exactAllowRules + fixture.suffixAllowRules;
        this.hostsSourceDao.updateRuleStats(
                SOURCE_ID,
                fixture.blockedRows + fixture.redirectRows,
                fixture.blockedRows + fixture.redirectRows,
                fixture.blockedRows,
                fixture.exactBlockedRows,
                0,
                fixture.redirectRows);
        this.hostsSourceDao.updateRuleStats(
                USER_SOURCE_ID,
                userRules,
                userRules,
                0,
                0,
                userRules,
                0);
    }

    private void seedAllowHeavyRootExportStageRows(AllowHeavyFixture fixture) {
        long startedMs = SystemClock.elapsedRealtime();
        this.db.runInTransaction(() -> {
            SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
            createAllowHeavySeedDigits(writableDb);
            writableDb.execSQL("DELETE FROM root_host_entries_stage WHERE source_id = " +
                    SOURCE_ID + " AND generation = " + GENERATION);
            long phaseStartMs = SystemClock.elapsedRealtime();
            dropAllowHeavyRootStageIndexes(writableDb);
            printAllowHeavySeedPhase("root-stage-drop-indexes", 0, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRootStageRows(writableDb, fixture.exactBlockedRows,
                    exactBlockedHostExpression(fixture),
                    exactBlockedReverseHostExpression(fixture), 0, SOURCE_ID, GENERATION);
            printAllowHeavySeedPhase("root-stage-exact-blocked",
                    fixture.exactBlockedRows, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRootStageRows(writableDb, fixture.suffixBlockedRows,
                    suffixBlockedHostExpression(fixture),
                    suffixBlockedReverseHostExpression(fixture), 0, SOURCE_ID, GENERATION);
            printAllowHeavySeedPhase("root-stage-suffix-blocked",
                    fixture.suffixBlockedRows, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            insertAllowHeavyRootStageRedirectRows(writableDb, fixture.redirectRows,
                    redirectHostExpression(), redirectReverseHostExpression(), SOURCE_ID,
                    GENERATION);
            printAllowHeavySeedPhase("root-stage-redirect",
                    fixture.redirectRows, phaseStartMs);
            phaseStartMs = SystemClock.elapsedRealtime();
            createAllowHeavyRootStageIndexes(writableDb);
            printAllowHeavySeedPhase("root-stage-create-indexes", 0, phaseStartMs);
        });
        printAllowHeavySeedPhase("root-stage-total", fixture.stagedRootRows(), startedMs);
    }

    private static void dropAllowHeavyRootStageIndexes(SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `" + ROOT_STAGE_SOURCE_GENERATION_INDEX_NAME + "`");
        db.execSQL("DROP INDEX IF EXISTS `" + ROOT_STAGE_GENERATION_SOURCE_INDEX_NAME + "`");
        db.execSQL("DROP INDEX IF EXISTS `" + ROOT_STAGE_REVERSE_HOST_INDEX_NAME + "`");
    }

    private static void createAllowHeavyRootStageIndexes(SupportSQLiteDatabase db) {
        db.execSQL(ROOT_STAGE_SOURCE_GENERATION_INDEX_SQL);
        db.execSQL(ROOT_STAGE_GENERATION_SOURCE_INDEX_SQL);
        db.execSQL(ROOT_STAGE_REVERSE_HOST_INDEX_SQL);
    }

    private static void printAllowHeavySeedPhase(String phase, long rows, long phaseStartMs) {
        System.out.println("HostEntryAllowHeavySeedPhase phase=" + phase
                + " rows=" + rows
                + " ms=" + (SystemClock.elapsedRealtime() - phaseStartMs));
    }

    private void seedAllowHeavyRuntimeMetadata(AllowHeavyFixture fixture) {
        int userRules = fixture.exactAllowRules + fixture.suffixAllowRules;
        this.hostsSourceDao.updateRuleStats(
                SOURCE_ID,
                fixture.blockedRows + fixture.redirectRows,
                fixture.blockedRows + fixture.redirectRows,
                fixture.blockedRows,
                fixture.exactBlockedRows,
                0,
                fixture.redirectRows);
        this.hostsSourceDao.updateRuleStats(
                USER_SOURCE_ID,
                userRules,
                userRules,
                0,
                0,
                userRules,
                0);
    }

    private static void createAllowHeavySeedDigits(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `allow_perf_digits` " +
                "(`n` INTEGER PRIMARY KEY) WITHOUT ROWID");
        db.execSQL("DELETE FROM `allow_perf_digits`");
        db.execSQL("INSERT INTO `allow_perf_digits` (`n`) VALUES " +
                "(0), (1), (2), (3), (4), (5), (6), (7), (8), (9)");
    }

    private static void insertAllowHeavyRows(SupportSQLiteDatabase db, int rowCount,
            String hostExpression, String reverseHostExpression, int type, int kind,
            int sourceId, int generation) {
        for (int offset = 0; offset < rowCount; offset += ALLOW_HEAVY_SEED_CHUNK_SIZE) {
            int count = Math.min(ALLOW_HEAVY_SEED_CHUNK_SIZE, rowCount - offset);
            String i = "(" + offset + " + numbers.`n`)";
            String host = hostExpression.replace("__i__", i);
            String reverseHost = reverseHostExpression.replace("__i__", i);
            db.execSQL("WITH `numbers`(`n`) AS (" +
                    "SELECT ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` " +
                    "FROM `allow_perf_digits` AS ones " +
                    "CROSS JOIN `allow_perf_digits` AS tens " +
                    "CROSS JOIN `allow_perf_digits` AS hundreds " +
                    "CROSS JOIN `allow_perf_digits` AS thousands " +
                    "CROSS JOIN `allow_perf_digits` AS ten_thousands " +
                    "WHERE ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` < " + count + ") " +
                    "INSERT INTO `hosts_lists` " +
                    "(`host`, `reverse_host`, `type`, `kind`, `enabled`, `source_id`, " +
                    "`generation`) " +
                    "SELECT " + host + ", " + reverseHost + ", " + type + ", " +
                    kind + ", 1, " +
                    sourceId + ", " + generation + " FROM `numbers`");
        }
    }

    private static void insertAllowHeavyRedirectRows(SupportSQLiteDatabase db, int rowCount,
            String hostExpression, String reverseHostExpression, int sourceId, int generation) {
        for (int offset = 0; offset < rowCount; offset += ALLOW_HEAVY_SEED_CHUNK_SIZE) {
            int count = Math.min(ALLOW_HEAVY_SEED_CHUNK_SIZE, rowCount - offset);
            String i = "(" + offset + " + numbers.`n`)";
            String host = hostExpression.replace("__i__", i);
            String reverseHost = reverseHostExpression.replace("__i__", i);
            db.execSQL("WITH `numbers`(`n`) AS (" +
                    "SELECT ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` " +
                    "FROM `allow_perf_digits` AS ones " +
                    "CROSS JOIN `allow_perf_digits` AS tens " +
                    "CROSS JOIN `allow_perf_digits` AS hundreds " +
                    "CROSS JOIN `allow_perf_digits` AS thousands " +
                    "CROSS JOIN `allow_perf_digits` AS ten_thousands " +
                    "WHERE ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` < " + count + ") " +
                    "INSERT INTO `hosts_lists` " +
                    "(`host`, `reverse_host`, `type`, `kind`, `enabled`, `redirection`, " +
                    "`source_id`, `generation`) SELECT " + host + ", " + reverseHost +
                    ", 2, 0, 1, '8.8.8.8', " + sourceId + ", " + generation +
                    " FROM `numbers`");
        }
    }

    private static void insertAllowHeavyRootStageRows(SupportSQLiteDatabase db, int rowCount,
            String hostExpression, String reverseHostExpression, int type, int sourceId,
            int generation) {
        for (int offset = 0; offset < rowCount; offset += ALLOW_HEAVY_SEED_CHUNK_SIZE) {
            int count = Math.min(ALLOW_HEAVY_SEED_CHUNK_SIZE, rowCount - offset);
            String i = "(" + offset + " + numbers.`n`)";
            String host = hostExpression.replace("__i__", i);
            String reverseHost = reverseHostExpression.replace("__i__", i);
            db.execSQL("WITH `numbers`(`n`) AS (" +
                    "SELECT ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` " +
                    "FROM `allow_perf_digits` AS ones " +
                    "CROSS JOIN `allow_perf_digits` AS tens " +
                    "CROSS JOIN `allow_perf_digits` AS hundreds " +
                    "CROSS JOIN `allow_perf_digits` AS thousands " +
                    "CROSS JOIN `allow_perf_digits` AS ten_thousands " +
                    "WHERE ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` < " + count + ") " +
                    "INSERT INTO `root_host_entries_stage` " +
                    "(`host`, `reverse_host`, `type`, `redirection`, `source_id`, " +
                    "`generation`) " +
                    "SELECT " + host + ", " + reverseHost + ", " + type + ", NULL, " +
                    sourceId + ", " + generation + " FROM `numbers`");
        }
    }

    private static void insertAllowHeavyRootStageRedirectRows(SupportSQLiteDatabase db,
            int rowCount, String hostExpression, String reverseHostExpression, int sourceId,
            int generation) {
        for (int offset = 0; offset < rowCount; offset += ALLOW_HEAVY_SEED_CHUNK_SIZE) {
            int count = Math.min(ALLOW_HEAVY_SEED_CHUNK_SIZE, rowCount - offset);
            String i = "(" + offset + " + numbers.`n`)";
            String host = hostExpression.replace("__i__", i);
            String reverseHost = reverseHostExpression.replace("__i__", i);
            db.execSQL("WITH `numbers`(`n`) AS (" +
                    "SELECT ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` " +
                    "FROM `allow_perf_digits` AS ones " +
                    "CROSS JOIN `allow_perf_digits` AS tens " +
                    "CROSS JOIN `allow_perf_digits` AS hundreds " +
                    "CROSS JOIN `allow_perf_digits` AS thousands " +
                    "CROSS JOIN `allow_perf_digits` AS ten_thousands " +
                    "WHERE ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` < " + count + ") " +
                    "INSERT INTO `root_host_entries_stage` " +
                    "(`host`, `reverse_host`, `type`, `redirection`, `source_id`, " +
                    "`generation`) SELECT " + host + ", " + reverseHost +
                    ", 2, '8.8.8.8', " + sourceId + ", " + generation +
                    " FROM `numbers`");
        }
    }

    private static String redirectHostExpression() {
        return "'redirect' || __i__ || '.allowperf.example.test'";
    }

    private static String redirectReverseHostExpression() {
        return "'test.example.allowperf.redirect' || __i__";
    }

    private static String exactBlockedHostExpression(AllowHeavyFixture fixture) {
        return "CASE WHEN __i__ < " + fixture.exactAllowMatches + " THEN " +
                "'exact-allow' || __i__ || '.allowperf.example.test' " +
                "WHEN (__i__ - " + fixture.exactAllowMatches + ") < " +
                fixture.suffixAllowExactMatches + " THEN " +
                "'child.suffix-allow' || (__i__ - " + fixture.exactAllowMatches +
                ") || '.allowperf.example.test' " +
                "ELSE 'exact-blocked' || __i__ || '.allowperf.example.test' END";
    }

    private static String exactBlockedReverseHostExpression(AllowHeavyFixture fixture) {
        return "CASE WHEN __i__ < " + fixture.exactAllowMatches + " THEN " +
                "'test.example.allowperf.exact-allow' || __i__ " +
                "WHEN (__i__ - " + fixture.exactAllowMatches + ") < " +
                fixture.suffixAllowExactMatches + " THEN " +
                "'test.example.allowperf.suffix-allow' || (__i__ - " +
                fixture.exactAllowMatches + ") || '.child' " +
                "ELSE 'test.example.allowperf.exact-blocked' || __i__ END";
    }

    private static String suffixBlockedHostExpression(AllowHeavyFixture fixture) {
        return "CASE WHEN __i__ < " + fixture.suffixAllowSuffixMatches + " THEN " +
                "'suffix-allow' || __i__ || '.allowperf.example.test' " +
                "ELSE 'suffix-blocked' || __i__ || '.allowperf.example.test' END";
    }

    private static String suffixBlockedReverseHostExpression(AllowHeavyFixture fixture) {
        return "CASE WHEN __i__ < " + fixture.suffixAllowSuffixMatches + " THEN " +
                "'test.example.allowperf.suffix-allow' || __i__ " +
                "ELSE 'test.example.allowperf.suffix-blocked' || __i__ END";
    }

    private long createRootHostsFile(boolean enableIpv6) throws Exception {
        setEnableIpv6(enableIpv6);
        RootModel rootModel = new RootModel(this.context);
        Method method = RootModel.class.getDeclaredMethod("createNewHostsFile");
        method.setAccessible(true);

        long startMs = SystemClock.elapsedRealtime();
        method.invoke(rootModel);
        return SystemClock.elapsedRealtime() - startMs;
    }

    private RootWriteResult createRootHostsFileWithTestDatabase(boolean enableIpv6)
            throws Exception {
        AppDatabase previousDatabase = swapAppDatabaseSingleton(this.db);
        try {
            long elapsedMs = createRootHostsFile(enableIpv6);
            return new RootWriteResult(elapsedMs, getGeneratedHostsFile().length());
        } finally {
            swapAppDatabaseSingleton(previousDatabase);
            this.context.deleteFile(HOSTS_FILENAME);
        }
    }

    private void setEnableIpv6(boolean enabled) {
        this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(this.context.getString(R.string.pref_enable_ipv6_key), enabled)
                .commit();
    }

    private File getGeneratedHostsFile() {
        return new File(this.context.getFilesDir(), HOSTS_FILENAME);
    }

    private static AppDatabase swapAppDatabaseSingleton(AppDatabase database) throws Exception {
        Field field = AppDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        AppDatabase previous = (AppDatabase) field.get(null);
        field.set(null, database);
        return previous;
    }

    private long scalarLong(String sql, Object... args) {
        try (Cursor cursor = this.db.getOpenHelper().getReadableDatabase()
                .query(new SimpleSQLiteQuery(sql, args))) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private void checkpointWal() {
        try (Cursor cursor = this.db.getOpenHelper().getWritableDatabase()
                .query("PRAGMA wal_checkpoint(TRUNCATE)")) {
            while (cursor.moveToNext()) {
                // Drain the pragma result so SQLite completes the checkpoint before timing rebuild.
            }
        }
    }

    private void assertScratchTableAbsent() {
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                        "AND name = 'update_seen_hosts'"));
    }

    private static int getPositiveIntArgument(Bundle arguments, String name, int defaultValue) {
        String value = arguments.getString(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return parsed;
    }

    private static boolean getBooleanArgument(Bundle arguments, String name, boolean defaultValue) {
        String value = arguments.getString(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return !"0".equals(value) && !"false".equalsIgnoreCase(value);
    }

    private static void assertWithinOptionalBudget(Bundle arguments, String name, long actualMs) {
        int budgetMs = getPositiveIntArgument(arguments, name, 0);
        if (budgetMs > 0) {
            assertTrue(name + " exceeded " + budgetMs + "ms: " + actualMs + "ms",
                    actualMs < budgetMs);
        }
    }

    private static void logScalePhase(String phase, long elapsedMs) {
        String message = "SourceLoaderSqlDedupCarryForwardScaleBenchmark phase="
                + phase + " ms=" + elapsedMs;
        Log.i(TAG, message);
        System.out.println(message);
    }

    private long materializedRootRowCount() {
        assertTrue("Root export should be materialized before timing root file generation",
                this.hostEntryDao.hasMaterializedRootExportRows());
        return this.hostEntryDao.getMaterializedRootExportEntryCountNow();
    }

    private static void assertRootWriteBytes(RootWriteResult rootWrite, long rootRows) {
        assertTrue("Generated root hosts file should not be empty: rows=" + rootRows
                + " bytes=" + rootWrite.bytes, rootWrite.bytes > rootRows);
    }

    private void rebuildRuntimeEntries() {
        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        applyBalancedRuntimeRebuildPragmas(writableDb);
        try {
            this.hostEntryDao.rebuildFromActiveGeneration(writableDb);
        } finally {
            restoreRuntimeRebuildPragmas(writableDb);
        }
    }

    private static void applyBalancedRuntimeRebuildPragmas(SupportSQLiteDatabase db) {
        db.execSQL("PRAGMA synchronous=NORMAL");
    }

    private static void restoreRuntimeRebuildPragmas(SupportSQLiteDatabase db) {
        db.execSQL("PRAGMA synchronous=FULL");
    }

    private static long expectedMaterializedRuntimeRows(long activeRows) {
        return activeRows <= HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS ? activeRows : 0L;
    }

    private static final class RootWriteResult {
        final long elapsedMs;
        final long bytes;

        RootWriteResult(long elapsedMs, long bytes) {
            this.elapsedMs = elapsedMs;
            this.bytes = bytes;
        }
    }

    private static BufferedReader failingAfterFirstLineReader() {
        return new BufferedReader(new StringReader("0.0.0.0 partial.example.test\n")) {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                int count = super.read(buffer, offset, length);
                if (count >= 0) {
                    return count;
                }
                throw new IOException("injected read failure");
            }
        };
    }

    private static String buildMixedRulesFixture() {
        StringBuilder builder = new StringBuilder(380_000);
        for (int i = 0; i < EXACT_HOST_RULES; i++) {
            builder.append("0.0.0.0 ads").append(i).append(".example.com\n");
        }
        for (int i = 0; i < ABP_SUFFIX_RULES; i++) {
            builder.append("||tracker").append(i).append(".example.net^\n");
        }
        for (int i = 0; i < DNSMASQ_SUFFIX_RULES; i++) {
            builder.append("address=/dns").append(i).append(".example.org/0.0.0.0\n");
        }
        for (int i = 0; i < SURGE_SUFFIX_RULES; i++) {
            builder.append("DOMAIN-SUFFIX,surge").append(i).append(".example.io,REJECT\n");
        }
        for (int i = 0; i < REDIRECT_RULES; i++) {
            builder.append("8.8.").append(8 + (i / 250) % 100).append('.')
                    .append(1 + (i % 250)).append(" redirect").append(i)
                    .append(".example.dev\n");
        }
        for (int i = 0; i < DUPLICATE_HOST_RULES; i++) {
            builder.append("0.0.0.0 ads").append(i % 1_000).append(".example.com\n");
        }
        for (int i = 0; i < SKIPPED_RULES; i++) {
            builder.append("@@||allowed").append(i).append(".example.com^\n");
        }
        return builder.toString();
    }

    private static final class ScaleFixture {
        final int totalLines;
        final int exactHostRules;
        final int abpSuffixRules;
        final int dnsmasqSuffixRules;
        final int surgeSuffixRules;
        final int redirectRules;
        final int duplicateHostRules;
        final int skippedRules;
        final int insertedRows;
        final int runtimeRows;

        private ScaleFixture(int totalLines, int exactHostRules, int abpSuffixRules,
                int dnsmasqSuffixRules, int surgeSuffixRules, int redirectRules,
                int duplicateHostRules, int skippedRules) {
            this.totalLines = totalLines;
            this.exactHostRules = exactHostRules;
            this.abpSuffixRules = abpSuffixRules;
            this.dnsmasqSuffixRules = dnsmasqSuffixRules;
            this.surgeSuffixRules = surgeSuffixRules;
            this.redirectRules = redirectRules;
            this.duplicateHostRules = duplicateHostRules;
            this.skippedRules = skippedRules;
            this.insertedRows = totalLines - skippedRules;
            this.runtimeRows = exactHostRules + abpSuffixRules + dnsmasqSuffixRules
                    + surgeSuffixRules + redirectRules;
        }

        static ScaleFixture forTotalLines(int totalLines) {
            int exact = totalLines / 2;
            int abp = totalLines / 5;
            int dnsmasq = totalLines / 10;
            int surge = totalLines / 20;
            int redirect = totalLines / 20;
            int duplicates = totalLines / 20;
            int skipped = totalLines - exact - abp - dnsmasq - surge - redirect - duplicates;
            return new ScaleFixture(totalLines, exact, abp, dnsmasq, surge, redirect,
                    duplicates, skipped);
        }

        int carryForwardCopiedRows() {
            return 5;
        }

        int carryForwardRuntimeRows() {
            return 3;
        }
    }

    private static final class AllowHeavyFixture {
        final int blockedRows;
        final int exactBlockedRows;
        final int suffixBlockedRows;
        final int exactAllowRules;
        final int suffixAllowRules;
        final int redirectRows;
        final int exactAllowMatches;
        final int suffixAllowExactMatches;
        final int suffixAllowSuffixMatches;

        private AllowHeavyFixture(int blockedRows, int exactBlockedRows, int suffixBlockedRows,
                int exactAllowRules, int suffixAllowRules, int redirectRows,
                int exactAllowMatches, int suffixAllowExactMatches,
                int suffixAllowSuffixMatches) {
            this.blockedRows = blockedRows;
            this.exactBlockedRows = exactBlockedRows;
            this.suffixBlockedRows = suffixBlockedRows;
            this.exactAllowRules = exactAllowRules;
            this.suffixAllowRules = suffixAllowRules;
            this.redirectRows = redirectRows;
            this.exactAllowMatches = exactAllowMatches;
            this.suffixAllowExactMatches = suffixAllowExactMatches;
            this.suffixAllowSuffixMatches = suffixAllowSuffixMatches;
        }

        static AllowHeavyFixture create(int blockedRows, int exactAllowRules,
                int suffixAllowRules, int redirectRows) {
            int exactBlockedRows = blockedRows / 2;
            int suffixBlockedRows = blockedRows - exactBlockedRows;
            int exactAllowMatches = Math.min(exactAllowRules, exactBlockedRows);
            int exactRowsLeft = exactBlockedRows - exactAllowMatches;
            int suffixAllowExactMatches = Math.min(suffixAllowRules, exactRowsLeft);
            int suffixAllowSuffixMatches = Math.min(suffixAllowRules, suffixBlockedRows);
            return new AllowHeavyFixture(blockedRows, exactBlockedRows, suffixBlockedRows,
                    exactAllowRules, suffixAllowRules, redirectRows, exactAllowMatches,
                    suffixAllowExactMatches, suffixAllowSuffixMatches);
        }

        long expectedRuntimeRows() {
            return (long) blockedRows + redirectRows - exactAllowMatches
                    - suffixAllowExactMatches - suffixAllowSuffixMatches;
        }

        long expectedRootRows() {
            return expectedRuntimeRows();
        }

        long stagedRootRows() {
            return (long) blockedRows + redirectRows;
        }

        String exactBlockedHost(int index) {
            if (index < exactAllowMatches) {
                return exactAllowHost(index);
            }
            int suffixIndex = index - exactAllowMatches;
            if (suffixIndex < suffixAllowExactMatches) {
                return "child." + suffixAllowHost(suffixIndex);
            }
            return "exact-blocked" + index + ".allowperf.example.test";
        }

        String suffixBlockedHost(int index) {
            if (index < suffixAllowSuffixMatches) {
                return suffixAllowHost(index);
            }
            return "suffix-blocked" + index + ".allowperf.example.test";
        }

        String exactAllowHost(int index) {
            return "exact-allow" + index + ".allowperf.example.test";
        }

        String suffixAllowHost(int index) {
            return "suffix-allow" + index + ".allowperf.example.test";
        }
    }

    private static final class GeneratedRulesReader extends BufferedReader {
        private final ScaleFixture fixture;
        private int index;
        private String pendingLine;
        private int pendingOffset;

        GeneratedRulesReader(ScaleFixture fixture) {
            super(new StringReader(""));
            this.fixture = fixture;
        }

        @Override
        public int read(char[] buffer, int offset, int length) {
            int count = 0;
            while (count < length) {
                if (this.pendingLine == null) {
                    String line = nextLine();
                    if (line == null) break;
                    this.pendingLine = line + "\n";
                    this.pendingOffset = 0;
                }
                int copied = Math.min(length - count,
                        this.pendingLine.length() - this.pendingOffset);
                this.pendingLine.getChars(this.pendingOffset, this.pendingOffset + copied,
                        buffer, offset + count);
                this.pendingOffset += copied;
                count += copied;
                if (this.pendingOffset == this.pendingLine.length()) this.pendingLine = null;
            }
            return count == 0 && length > 0 ? -1 : count;
        }

        private String nextLine() {
            if (this.index >= this.fixture.totalLines) {
                return null;
            }

            int current = this.index++;
            int offset = current;
            if (offset < this.fixture.exactHostRules) {
                return "0.0.0.0 ads" + offset + ".example.com";
            }
            offset -= this.fixture.exactHostRules;
            if (offset < this.fixture.abpSuffixRules) {
                return "||tracker" + offset + ".example.net^";
            }
            offset -= this.fixture.abpSuffixRules;
            if (offset < this.fixture.dnsmasqSuffixRules) {
                return "address=/dns" + offset + ".example.org/0.0.0.0";
            }
            offset -= this.fixture.dnsmasqSuffixRules;
            if (offset < this.fixture.surgeSuffixRules) {
                return "DOMAIN-SUFFIX,surge" + offset + ".example.io,REJECT";
            }
            offset -= this.fixture.surgeSuffixRules;
            if (offset < this.fixture.redirectRules) {
                return "8.8." + (8 + (offset / 250) % 100) + '.'
                        + (1 + (offset % 250)) + " redirect" + offset
                        + ".example.dev";
            }
            offset -= this.fixture.redirectRules;
            if (offset < this.fixture.duplicateHostRules) {
                return "0.0.0.0 ads" + (offset % Math.max(1, this.fixture.exactHostRules))
                        + ".example.com";
            }
            offset -= this.fixture.duplicateHostRules;
            return "@@||allowed" + offset + ".example.com^";
        }
    }
}
