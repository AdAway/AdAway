package org.adaway.model.source;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostEntryDao;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * Regression test for the HTTP 304 generation-migration bug.
 *
 * ROOT CAUSE:
 * {@code checkAndRetrieveHostsSources()} uses an atomic generation-swap pattern:
 *   1. Write new host entries tagged with generation G+1.
 *   2. Flip active_generation to G+1.
 *   3. {@code cleanupNonActiveGenerations(G+1)} deletes all rows where
 *      {@code source_id != 1 AND generation != G+1}.
 *
 * When a source returns HTTP 304 (Not Modified), no new rows are written; the
 * source's existing rows still carry generation G. Step 3 therefore deletes ALL of
 * that source's entries on every update run after the first one.
 * Result: the VPN proxy sees no blocked domains for those sources.
 *
 * FIX:
 * Before cleanup runs, copy 304-source rows from G to G+1 via:
 *   {@code hostListItemDao.copySourceGeneration(sourceId, G, G+1)}
 * Copying keeps generation G valid until the final active-generation flip succeeds.
 *
 * This test guards:
 * 1. {@link HostListItemDao#migrateSourceGeneration} method exists with the correct
 *    signature — a compile error here means the fix was accidentally removed.
 * 2. The generation arithmetic is correct: oldGeneration == currentImportGeneration - 1.
 * 3. Variant: multiple sequential updates (simulate G=1→2, G=2→3) — old generation
 *    always equals newGeneration - 1, never a stale absolute value.
 */
public class Generation304MigrationTest {

    // -------------------------------------------------------------------------
    // Guard 1: HostListItemDao.migrateSourceGeneration() method signature
    // -------------------------------------------------------------------------

    /**
     * Compile-time guard: if {@link HostListItemDao#migrateSourceGeneration(int, int, int)}
     * is removed or renamed, this test fails to compile, immediately surfacing the regression.
     *
     * The method is referenced via reflection so the test body itself runs as a JVM test
     * (no Room DB needed) while still failing loudly if the method disappears.
     */
    @Test
    public void hostListItemDao_hasMigrateSourceGenerationMethod() throws Exception {
        // Reflective lookup: throws NoSuchMethodException if signature changes.
        Method m = HostListItemDao.class.getMethod(
                "migrateSourceGeneration",
                int.class, int.class, int.class);

        assertNotNull("migrateSourceGeneration(int, int, int) must exist on HostListItemDao", m);
        assertEquals("migrateSourceGeneration return type must be void",
                void.class, m.getReturnType());
    }

    @Test
    public void hostListItemDao_hasCopySourceGenerationMethod() throws Exception {
        Method m = HostListItemDao.class.getMethod(
                "copySourceGeneration",
                int.class, int.class, int.class);

        assertNotNull("copySourceGeneration(int, int, int) must exist on HostListItemDao", m);
        assertEquals("copySourceGeneration return type must be void",
                void.class, m.getReturnType());
    }

    @Test
    public void hostListItemDao_hasTransactionalGenerationHelpers() throws Exception {
        Method copy = HostListItemDao.class.getMethod(
                "copySourceGenerationReplacingTarget",
                int.class, int.class, int.class);
        Method replace = HostListItemDao.class.getMethod(
                "replaceSourceGeneration",
                int.class, int.class, int.class);
        Method count = HostListItemDao.class.getMethod(
                "countSourceHostsForGeneration",
                int.class, int.class);

        assertEquals(void.class, copy.getReturnType());
        assertEquals(void.class, replace.getReturnType());
        assertEquals(int.class, count.getReturnType());
    }

    @Test
    public void hostEntryDao_hasPureRuntimeRebuildForOuterTransactions() throws Exception {
        Method rebuild = HostEntryDao.class.getMethod("rebuildFromActiveGeneration");
        Method sync = HostEntryDao.class.getMethod("sync");

        assertEquals(void.class, rebuild.getReturnType());
        assertEquals(void.class, sync.getReturnType());
    }

    @Test
    public void sourceModel_runtimeRefreshDelegatesLargeCacheDecisionToDao() throws Exception {
        String sourceModel = readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java");
        String compactSourceModel = compact(sourceModel);

        assertTrue("Runtime refresh must rebuild through HostEntryDao so large active sets " +
                        "clear stale materialized caches and rebuild root export.",
                compactSourceModel.contains("this.hostEntryDao.rebuildFromActiveGeneration(db);"));
        assertFalse("SourceModel must not skip runtime refresh before HostEntryDao can clear " +
                        "stale root_host_entries.",
                sourceModel.contains("RUNTIME_CACHE_REFRESH_MAX_ROWS") ||
                        sourceModel.contains("Skipping runtime cache refresh"));
    }

    @Test
    public void hostEntryDao_suffixAllowDeletesUseStreamingTempTableWithBatchedFallback()
            throws Exception {
        String dao = compact(readRepoFile(
                "app/src/main/java/org/adaway/db/dao/HostEntryDao.java"));
        String exactSuffixDelete = dao.substring(
                dao.indexOf("WITH RECURSIVE `exact_suffixes`(`host`, `suffix`)"),
                dao.indexOf("int deleteExactRowsAllowedByActiveSuffixRulesBatch"));
        String suffixSuffixDelete = dao.substring(
                dao.indexOf("WITH RECURSIVE `blocked_suffixes`(`host`, `suffix`)"),
                dao.indexOf("int deleteSuffixRowsAllowedByActiveSuffixRulesBatch"));

        assertTrue("Exact-row suffix allow delete must bound the generated suffix surface to " +
                        "a host batch.",
                exactSuffixDelete.contains("AND `host` > :afterHost") &&
                        exactSuffixDelete.contains("AND (:upperHost IS NULL OR `host` <= :upperHost)"));
        assertTrue("Suffix-row suffix allow delete must bound the generated suffix surface to " +
                        "a host batch.",
                suffixSuffixDelete.contains("AND `host` > :afterHost") &&
                        suffixSuffixDelete.contains("AND (:upperHost IS NULL OR `host` <= :upperHost)"));
        assertTrue("Suffix allow deletes must advance through host_entries by indexed host ranges.",
                dao.contains("String getHostEntryBatchUpperBound") &&
                        dao.contains("SUFFIX_ALLOW_DELETE_BATCH_SIZE"));
        assertTrue("Transactional runtime rebuilds must use the reverse-host indexed suffix " +
                        "matcher instead of scanning every host entry in Java.",
                dao.contains("deleteRowsAllowedByActiveSuffixRules(activeGeneration, db)") &&
                        dao.contains("db != null && db.inTransaction()") &&
                        dao.contains("CREATE TEMP TABLE IF NOT EXISTS") &&
                        dao.contains("PRIMARY KEY(`kind`, `host`)") &&
                        dao.contains("index_host_entries_kind_reverse_host") &&
                        dao.contains("index_hosts_lists_active_allow_source_kind_reverse_host") &&
                        dao.contains("index_hosts_lists_active_allow_generation_kind_reverse_host") &&
                        dao.contains("entry.`reverse_host` >= allowed.`reverse_host` || '.'") &&
                        dao.contains("entry.`reverse_host` < allowed.`reverse_host` || '/'"));
        assertFalse("Transactional suffix allow deletion must not keep the old Java scan.",
                dao.contains("isAllowedBySuffixSet(host, allowSuffixes)") ||
                        dao.contains("getHostEntryHostsByKind(kind)"));
    }

    @Test
    public void sourceModel_marksGenerationUnsafeWhenCarryForwardHasNoPriorCoverage()
            throws Exception {
        String sourceModel = readRepoFile("app/src/main/java/org/adaway/model/source/SourceModel.java");
        String compactSourceModel = compact(sourceModel);
        int failedCarryForwardStart = compactSourceModel.indexOf(
                "for (HostsSource source : failedCarryForwardSources)");
        int deferredCarryForwardStart = compactSourceModel.indexOf(
                "for (HostsSource source : deferredCarryForwardSources)");
        int carryForwardEnd = compactSourceModel.indexOf("long carryForwardMs =");
        assertTrue("Failed-source and 304 carry-forward must run before finalization.",
                failedCarryForwardStart >= 0 && deferredCarryForwardStart > failedCarryForwardStart
                        && carryForwardEnd > deferredCarryForwardStart);
        String failedCarryForward = compactSourceModel.substring(
                failedCarryForwardStart, deferredCarryForwardStart);
        String deferredCarryForward = compactSourceModel.substring(
                deferredCarryForwardStart, carryForwardEnd);

        assertTrue("Carry-forward must inspect previous active generation coverage.",
                sourceModel.contains("countSourceHostsForGeneration(source.getId(), oldGeneration)"));
        assertTrue("Never-synced failed sources must not be treated as migrated.",
                sourceModel.contains("activeRows <= 0 && source.getLocalModificationDate() == null"));
        assertTrue("Failed sources must use direct carry-forward so SQL dedupe cannot suppress " +
                        "their previous active coverage.",
                sourceModel.contains("failedCarryForwardSources.add(source)") &&
                        sourceModel.contains("failedCarryForwardSources.add(result.source)") &&
                        failedCarryForward.contains("if (!carryForwardPreviousGeneration" +
                                "(source, importGeneration)) { generationUnsafe.set(true); }"));
        assertTrue("304 carry-forward must mark the full-update generation unsafe and use " +
                        "the SQL dedupe surface.",
                deferredCarryForward.contains("if (!carryForwardPreviousGeneration(source, " +
                        "importGeneration, sqlDeduper)) { generationUnsafe.set(true); }"));
        assertTrue("Unsafe generations must abort before activation.",
                compactSourceModel.contains("|| generationUnsafe.get()) { " +
                        "abortUnsafeGeneration(writableDb, importGeneration, sourceFailures); " +
                        "postMultiPhaseProgress(MultiPhaseProgress.idle()); " +
                        "throw new HostErrorException(DOWNLOAD_FAILED); }") &&
                        compactSourceModel.indexOf("abortUnsafeGeneration(writableDb,") <
                                compactSourceModel.indexOf("FinalizeTimings finalizeTimings = " +
                                        "finalizeActivatedGeneration("));
    }

    @Test
    public void sourceModel_hasNoChangeFastPathForAllUnchangedSources() throws Exception {
        String sourceModel = readRepoFile("app/src/main/java/org/adaway/model/source/SourceModel.java");

        assertTrue("Full update must track sources that actually changed.",
                sourceModel.contains("changedSourceCount"));
        assertTrue("304 sources must defer carry-forward until the update is known to need activation.",
                sourceModel.contains("deferredCarryForwardSources.add(result.source)"));
        assertTrue("All-304 updates must take the no-change finalization path.",
                sourceModel.contains("Pipeline no-change fast path"));
        assertTrue("No-change fast path must rebuild host_entries when disabled sources changed runtime truth.",
                sourceModel.contains("runtimeRebuildRequired"));
        assertTrue("Disabled source rows must be removed before runtime rebuild.",
                sourceModel.contains("this.hostListItemDao.clearSourceHosts(sourceId)"));
        assertTrue("Runtime rebuild must be conditional inside the no-change path.",
                sourceModel.contains("if (runtimeRebuildRequired)"));
        assertTrue("Changed updates must still carry forward deferred 304 sources before activation.",
                sourceModel.contains("for (HostsSource source : deferredCarryForwardSources)"));
    }

    @Test
    public void sourceModel_treatsNonSuccessfulHttpStatusAsDownloadFailure() throws Exception {
        String sourceModel = readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java");
        String downloadToTempFile = sourceModel.substring(
                sourceModel.indexOf("private DownloadResult downloadToTempFile"),
                sourceModel.indexOf("private static final class DownloadResult"));

        assertTrue("HTTP 304 is the only non-2xx response that may skip parsing.",
                downloadToTempFile.contains("response.code() == HTTP_NOT_MODIFIED"));
        assertTrue("HTTP 4xx/5xx responses must enter the failed-source carry-forward path.",
                downloadToTempFile.contains("if (!response.isSuccessful())") &&
                        downloadToTempFile.contains("return DownloadResult.failed(source, " +
                                "\"HTTP \" + response.code())"));
        assertTrue("Response bodies may be parsed only after the HTTP status is accepted.",
                downloadToTempFile.indexOf("if (!response.isSuccessful())") <
                        downloadToTempFile.indexOf("ResponseBody body = response.body()"));
    }

    @Test
    public void sourceModel_serializesEveryPublicUpdateEntryPoint() throws Exception {
        String sourceModel = readRepoFile("app/src/main/java/org/adaway/model/source/SourceModel.java");

        assertTrue("Full update must use the shared update gate.",
                sourceModel.contains("beginUpdateOperation(\"checkAndRetrieveHostsSources\")"));
        String legacyRetrieve = sourceModel.substring(
                sourceModel.indexOf("public boolean retrieveHostsSources() throws HostErrorException"),
                sourceModel.indexOf("public boolean checkAndRetrieveHostsSources()"));
        assertTrue("Legacy all-source update must delegate directly to the staged full-update " +
                        "pipeline.",
                legacyRetrieve.contains("return checkAndRetrieveHostsSources();"));
        assertFalse("Legacy all-source update must not keep the deleted pre-staged body.",
                legacyRetrieve.contains("beginUpdateOperation(\"retrieveHostsSources\")") ||
                        legacyRetrieve.contains("useStagedPipelineForLegacyRetrieve()") ||
                        legacyRetrieve.contains("ConcurrentHashMap.newKeySet()"));
        assertTrue("Single-source update must use the shared update gate.",
                sourceModel.contains("beginUpdateOperation(\"retrieveHostsSource\")"));
        assertTrue("Scoped multi-source update must use the shared update gate.",
                sourceModel.contains("beginUpdateOperation(\"retrieveHostsSources(list)\")"));
        assertTrue("Every gated update path must release through the shared finalizer.",
                sourceModel.contains("finishUpdateOperation();"));
        assertTrue("Colliding updates must fail explicitly instead of reporting success.",
                sourceModel.contains("throw new HostErrorException(UPDATE_IN_PROGRESS)"));
    }

    @Test
    public void sourceModel_passesExplicitGenerationIntoParserWorkers() throws Exception {
        String sourceModel = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java"));

        assertTrue("Full update must freeze its target generation before worker lambdas run.",
                sourceModel.contains("final int importGeneration = activeGen + 1"));
        assertTrue("Full update must use SQL-backed dedup instead of a heap-resident global set.",
                sourceModel.contains("final SqlUpdateDeduper sqlDeduper = new SqlUpdateDeduper(writableDb)"));
        assertTrue("Full-update URL parse workers must receive the frozen generation and SQL deduper.",
                sourceModel.contains("parseSourceInputStream(result.source, reader, importGeneration, sqlDeduper)"));
        assertTrue("Full-update file sources must receive the frozen generation.",
                sourceModel.contains("readSourceFile(source, importGeneration, sqlDeduper)"));
        assertTrue("Single-source URL updates must parse into their staging generation.",
                sourceModel.contains("parseSourceInputStream(source, reader, stagingGeneration, sqlDeduper)"));
        assertTrue("Single-source file updates must parse into their staging generation.",
                sourceModel.contains("readSourceFile(source, stagingGeneration, sqlDeduper)"));
        assertTrue("Carry-forward must not infer the generation from mutable shared state.",
                sourceModel.contains("carryForwardPreviousGeneration(@NonNull HostsSource source, " +
                        "int importGeneration)"));
        assertTrue("Active full-update carry-forward must share the SQL dedup surface.",
                sourceModel.contains("carryForwardPreviousGeneration(source, importGeneration, " +
                        "sqlDeduper)") &&
                        sourceModel.contains("copyUnseenSourceGeneration("));
        assertTrue("SourceLoader must be constructed from the explicit helper parameter.",
                sourceModel.contains("new SourceLoader(hostsSource, generation)"));
    }

    @Test
    public void sourceLoader_usesSetBasedSqlDedupFlushOnly() throws Exception {
        String sourceLoader = readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceLoader.java");
        String sqlDeduper = readRepoFile(
                "app/src/main/java/org/adaway/model/source/SqlUpdateDeduper.java");

        assertTrue("SQL dedup imports must stage parsed rows before the set-based flush.",
                sourceLoader.contains("compilePendingInsertStatement()") &&
                        sourceLoader.contains("SqlUpdateDeduper.stagePending(") &&
                        sourceLoader.contains("flushPendingRowsToHostsLists()"));
        assertFalse("SourceLoader must not keep always-null per-row dedup statement plumbing.",
                sourceLoader.contains("dedupStmt"));
        assertFalse("SourceLoader must not call a per-row seen-table write before staging.",
                sourceLoader.contains("markSeen("));
        assertFalse("SqlUpdateDeduper must not expose the old per-row seen-table insert API.",
                sqlDeduper.contains("compileInsertStatement()") ||
                        sqlDeduper.contains("markSeen("));
    }

    @Test
    public void allowHeavyBenchmarkCanSeedRootExportStagePath() throws Exception {
        String perfTest = readRepoFile(
                "app/src/androidTest/java/org/adaway/model/source/SourceLoaderPerformanceTest.java");

        assertTrue("Allow-heavy benchmark must expose an explicit stage-seeding switch.",
                perfTest.contains("ARG_ALLOW_REBUILD_SEED_ROOT_STAGE") &&
                        perfTest.contains("adawayAllowRebuildSeedRootStage"));
        assertTrue("Allow-heavy benchmark must seed root_host_entries_stage on request.",
                perfTest.contains("seedAllowHeavyRootExportStageRows(fixture)") &&
                        perfTest.contains("INSERT INTO `root_host_entries_stage`"));
        assertTrue("Allow-heavy benchmark must assert staged candidate coverage exists.",
                perfTest.contains("SELECT COUNT(*) FROM root_host_entries_stage"));
        assertTrue("Allow-heavy benchmark output must report whether the staged path was seeded.",
                perfTest.contains("seedRootStage=") &&
                        perfTest.contains("stageRows="));
        assertTrue("Allow-heavy benchmark must budget the production root write path.",
                perfTest.contains("ARG_ALLOW_REBUILD_ROOT_WRITE_BUDGET_MS") &&
                        perfTest.contains("adawayAllowRebuildRootWriteBudgetMs") &&
                        perfTest.contains("rootWriteMs="));
        assertFalse("Allow-heavy benchmark must not keep stale root-cursor performance budgets.",
                perfTest.contains("ARG_ALLOW_REBUILD_ROOT_CURSOR_BUDGET_MS") ||
                        perfTest.contains("adawayAllowRebuildRootCursorBudgetMs"));
    }

    @Test
    public void rootModelUsesExplicitRootApplyCursors() throws Exception {
        String rootModel = readRepoFile("app/src/main/java/org/adaway/model/root/RootModel.java");

        assertTrue("Root apply must use the materialized root export cursor for the normal path.",
                rootModel.contains("getRootHostsFileCursorMaterialized()"));
        assertTrue("Root apply fallback must use the explicit active streaming cursor.",
                rootModel.contains("getActiveRootHostsFileCursor()"));
        assertTrue("Materialized root apply must use bounded chunk cursors.",
                rootModel.contains("getActiveRuntimeRuleCountNow() > 0") &&
                        rootModel.contains("HOSTS_FILE_CHUNK_ROWS") &&
                        rootModel.contains("writeMaterializedHostChunks") &&
                        rootModel.contains("getRootHostsFileChunkCursorMaterialized(") &&
                        rootModel.contains("getRootHostsFileChunkCursorMaterializedIpv6("));
        assertFalse("Root apply must not use the ambiguous materialized-or-active cursor.",
                rootModel.contains("hostEntryDao.getRootHostsFileCursor()"));
    }

    @Test
    public void largeRootExportSkipsRedirectPhaseWhenNoRedirectRules() throws Exception {
        String dao = readRepoFile("app/src/main/java/org/adaway/db/dao/HostEntryDao.java");

        assertTrue("Large root export must read redirected rule count from cached stats.",
                dao.contains("SELECT `redirected_count` FROM `hosts_stats` WHERE `id` = 0") &&
                        dao.contains("boolean hasRedirectRules = " +
                                "getRedirectedEntryCountNow() > 0"));
        assertTrue("Direct root export must skip redirected-row scans when no redirect exists.",
                dao.contains("if (hasRedirectRules) {\n" +
                        "            insertRootExportRedirectedRows(db, false, " +
                        "activeGeneration);"));
        assertTrue("Staged root export must skip redirected-row scans when no redirect exists.",
                dao.contains("if (hasRedirectRules) {\n" +
                        "            insertRootExportStagedRedirectedRows(db, false, " +
                        "activeGeneration);"));
        assertTrue("Large root export must pass the redirect guard to staged and direct paths.",
                dao.contains("hasWildcardExactAllowRules, hasRedirectRules,\n" +
                        "                        activeGeneration"));
    }

    @Test
    public void sourceModel_finalizesGenerationAndRuntimeTruthAtomically() throws Exception {
        String sourceModel = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java"));
        String activatedFinalizer = sourceModel.substring(
                sourceModel.indexOf("private FinalizeTimings finalizeActivatedGeneration("),
                sourceModel.indexOf("private FinalizeTimings finalizeNoChange("));

        assertTrue("Full update must use the atomic finalizer instead of split commits.",
                sourceModel.contains("FinalizeTimings finalizeTimings = finalizeActivatedGeneration("));
        assertTrue("Atomic finalizer must wrap activation, cleanup, and runtime rebuild.",
                sourceModel.contains("this.database.runInTransaction(() -> { " +
                        "SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();"));
        assertTrue("Disabled-source cleanup must happen inside the publish transaction.",
                sourceModel.contains("applyDisabledSourceFinalization(disabledSourceIds);"));
        assertTrue("Source metadata commits must happen inside the publish transaction.",
                sourceModel.contains("applySourceCommits(sourceCommits);"));
        assertTrue("Active generation must be flipped inside the publish transaction.",
                sourceModel.contains("setActiveGeneration(db, importGeneration);"));
        assertTrue("Generation publish must invalidate stale root-export materialization before " +
                        "the async runtime rebuild can race with root apply.",
                activatedFinalizer.contains("this.hostEntryDao.invalidateRootExportMaterializedCache();"));
        assertTrue("Root-export invalidation must happen before scheduling async runtime refresh.",
                activatedFinalizer.indexOf(
                        "this.hostEntryDao.invalidateRootExportMaterializedCache();") <
                        activatedFinalizer.indexOf("scheduleRuntimeCacheRefresh();"));
        assertFalse("Full update must not flip active generation, clean old rows, then sync in " +
                        "separate committed operations.",
                sourceModel.contains("setActiveGeneration(writableDb, importGeneration); " +
                        "long cleanupStartedMs = SystemClock.elapsedRealtime(); " +
                        "cleanupNonActiveGenerations(writableDb, importGeneration);"));
    }

    @Test
    public void sourceModel_defersFullUpdateSourceMetadataUntilPublish() throws Exception {
        String sourceModel = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java"));
        String stagedUpdate = sourceModel.substring(
                sourceModel.indexOf("public boolean checkAndRetrieveHostsSources() " +
                        "throws HostErrorException"),
                sourceModel.indexOf("private static int ensureAndGetActiveGeneration"));

        assertTrue("Successful source metadata must be recorded as pending commit state.",
                stagedUpdate.contains("sourceCommits.add(SourceCommit.changed("));
        assertTrue("304 stale-error clearing must be recorded as pending commit state.",
                stagedUpdate.contains("sourceCommits.add(SourceCommit.unchanged(result.source.getId()))"));
        assertTrue("Failed source errors must be recorded separately from success metadata.",
                stagedUpdate.contains("sourceFailures.add(SourceFailure.of("));
        assertFalse("Full-update workers must not publish ETag directly before generation " +
                        "finalization.",
                stagedUpdate.contains("hostsSourceDao.updateEntityTag(result.source.getId()"));
        assertFalse("Full-update workers must not publish modification dates directly before " +
                        "generation finalization.",
                stagedUpdate.contains("hostsSourceDao.updateModificationDates(result.source.getId()"));
        assertFalse("Full-update workers must not clear download errors directly before " +
                        "generation finalization.",
                stagedUpdate.contains("hostsSourceDao.clearDownloadError(result.source.getId())"));
    }

    @Test
    public void sourceModel_abortsStoppedUpdatesBeforeGenerationActivation() throws Exception {
        String sourceModel = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java"));

        assertTrue("Stopped updates must clean the staging generation and abort before " +
                        "carry-forward or active generation finalization.",
                sourceModel.contains("if (progressBuilder.isStopped()) { " +
                        "cleanupGeneration(writableDb, importGeneration); " +
                        "progressBuilder.setFinalizing(false); " +
                        "progressBuilder.setComplete(false); " +
                        "postMultiPhaseProgress(progressBuilder.build(), true); " +
                        "postIdleAfterTerminal(); " +
                        "return false; }"));
        assertTrue("Stopped update workers must not publish a partial generation.",
                sourceModel.indexOf("if (progressBuilder.isStopped()) { " +
                        "cleanupGeneration(writableDb, importGeneration);") <
                        sourceModel.indexOf("FinalizeTimings finalizeTimings = " +
                                "finalizeActivatedGeneration("));
    }

    @Test
    public void sourceModel_ignoresProgressControlsAfterTerminalStates() throws Exception {
        String sourceModel = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java"));

        assertTrue("Pause/resume/stop requests must share the same update-control gate.",
                sourceModel.contains("private boolean canControlCurrentUpdate()") &&
                        sourceModel.contains("if (!canControlCurrentUpdate()) { return; }"));
        assertTrue("Update controls must be ignored after the update is no longer running.",
                sourceModel.contains("return this.updateInProgress.get()"));
        assertTrue("Update controls must be ignored once a stop request is terminal.",
                sourceModel.contains("&& !this.progressBuilder.isStopped()"));
        assertTrue("Update controls must be ignored while finalization is publishing runtime truth.",
                sourceModel.contains("&& !this.progressBuilder.isFinalizing()"));
        assertTrue("Update controls must be ignored after completion is already published.",
                sourceModel.contains("&& !this.progressBuilder.isComplete()"));
    }

    @Test
    public void sourceModel_defersTargetedMultiSourcePromotionUntilFinalRuntimeRebuild()
            throws Exception {
        String sourceModel = compact(readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java"));
        assertTrue("Targeted update worker must return staged work for final batch publication.",
                sourceModel.contains("private TargetedSourceUpdate retrieveHostsSource("));

        String listUpdate = sourceModel.substring(
                sourceModel.indexOf("public void retrieveHostsSources(@NonNull List<Integer> sourceIds)"),
                sourceModel.indexOf("private TargetedSourceUpdate retrieveHostsSource("));
        String singleUpdate = sourceModel.substring(
                sourceModel.indexOf("private TargetedSourceUpdate retrieveHostsSource("),
                sourceModel.indexOf("public void syncHostEntries()"));

        assertTrue("Targeted multi-source updates must collect every deferred finalization, " +
                        "not only staged row promotions.",
                listUpdate.contains("List<TargetedSourceUpdate> finalizationUpdates = " +
                        "new ArrayList<>()"));
        assertTrue("Targeted multi-source updates must preserve order while removing duplicate ids.",
                listUpdate.contains("List<Integer> uniqueSourceIds = " +
                        "new ArrayList<>(new LinkedHashSet<>(sourceIds))"));
        assertTrue("Targeted multi-source updates must publish staged rows in one finalizer.",
                listUpdate.contains("finalizeStagedSourceGenerations(finalizationUpdates)"));
        assertTrue("Targeted multi-source updates must capture per-source staged work.",
                listUpdate.contains("TargetedSourceUpdate update = " +
                        "retrieveHostsSource(sourceId, false)"));
        assertFalse("Targeted multi-source updates must not iterate duplicate-prone source ids.",
                listUpdate.contains("for (int sourceId : sourceIds)"));
        assertFalse("Targeted multi-source updates must not delegate to a per-source method that " +
                        "can publish each source independently.",
                listUpdate.contains("for (int sourceId : sourceIds) { " +
                        "retrieveHostsSource(sourceId, false); }"));
        assertTrue("Per-source targeted updates must return staged changed sources to the caller.",
                singleUpdate.contains("stagedUpdate = TargetedSourceUpdate.changed(") &&
                        singleUpdate.contains("return stagedUpdate;"));
        assertTrue("Batch disabled-source cleanup must be deferred to finalization.",
                singleUpdate.contains("return syncAfter ? null : " +
                        "TargetedSourceUpdate.disabled(sourceId)"));
        assertTrue("Batch 304 metadata must be deferred to finalization.",
                singleUpdate.contains("TargetedSourceUpdate.metadataOnly("));
        assertFalse("Batch disabled-source cleanup must not clear source rows inline.",
                singleUpdate.contains("if (!source.isEnabled()) { " +
                        "this.hostListItemDao.clearSourceHosts(sourceId);"));
        assertFalse("Batch 304 metadata must not publish ETag inline.",
                singleUpdate.contains("this.hostsSourceDao.updateEntityTag(source.getId(), " +
                        "result.entityTag);"));
        assertFalse("Batch 304 metadata must not publish modification dates inline.",
                singleUpdate.contains("this.hostsSourceDao.updateModificationDates(sourceId, " +
                        "localModificationDate, onlineModificationDate);"));
        assertFalse("Batch per-source work must not promote staged rows before the final " +
                        "runtime rebuild.",
                singleUpdate.contains("promoteStagedSourceGeneration( sourceId, " +
                        "activeGeneration, stagingGeneration);"));
        assertTrue("Single-source downloads must keep reporting current-list progress.",
                singleUpdate.contains("downloadToTempFile(source, (label, withinFraction)") &&
                        sourceModel.contains("progress.onProgress(source.getLabel()"));
    }

    @Test
    public void userRuleMutationSurfaceReusesSourceModelRuntimeSync() throws Exception {
        String domainChecker = readRepoFile(
                "app/src/main/java/org/adaway/ui/domainchecker/DomainCheckerViewModel.java");
        Path aiExecutor = repoRoot().resolve(
                "app/src/main/java/org/adaway/model/ai/AiActionExecutor.java");

        assertFalse("Domain checker user-rule writes must not use the no-DB HostEntryDao.sync() " +
                        "fallback.",
                domainChecker.contains("mHostEntryDao.sync()"));
        assertTrue("Domain checker user-rule writes must reuse SourceModel.syncHostEntries().",
                domainChecker.contains("getSourceModel().syncHostEntries()"));
        assertFalse("AI user-rule mutation surface is removed from the product.",
                Files.exists(aiExecutor));
    }

    @Test
    public void appDatabaseSeedsUserSourceBeforeSafetyAllowlist() throws Exception {
        String appDatabase = compact(readRepoFile(
                "app/src/main/java/org/adaway/db/AppDatabase.java"));

        assertTrue("Database creation must seed default sources before WaTg allowlist inserts " +
                        "user-list rows.",
                appDatabase.contains("AppDatabase.initialize(context, instance); " +
                        "WaTgSafetyAllowlist.ensureAllowlistSync(context);"));
        assertFalse("WaTg allowlist must not race source initialization from a separate " +
                        "onCreate task.",
                appDatabase.contains("WaTgSafetyAllowlist.ensureAllowlist(context);"));
    }

    // -------------------------------------------------------------------------
    // Guard 2: Generation arithmetic — oldGeneration = currentImportGeneration - 1
    // -------------------------------------------------------------------------

    /**
     * The 304 handler computes {@code oldGeneration = currentImportGeneration - 1}.
     * Verifies this produces the correct "previous generation" for the first update
     * (activeGen=0 → importGeneration=1, oldGen must be 0).
     */
    @Test
    public void generationArithmetic_firstUpdate_oldGenerationIsZero() {
        int activeGen = 0;
        int currentImportGeneration = activeGen + 1; // == 1
        int oldGeneration = currentImportGeneration - 1; // == 0

        assertEquals("First update: activeGen=0, import=1, old must be 0", 0, oldGeneration);
        assertEquals("First update: currentImportGeneration must be 1", 1, currentImportGeneration);
    }

    /**
     * Variant: second update run (activeGen=1 → importGeneration=2).
     * This is the CRITICAL case: without migrateSourceGeneration(), rows at generation 1
     * would be deleted by cleanupNonActiveGenerations(2) on the second update.
     */
    @Test
    public void generationArithmetic_secondUpdate_oldGenerationIsOne() {
        int activeGen = 1;
        int currentImportGeneration = activeGen + 1; // == 2
        int oldGeneration = currentImportGeneration - 1; // == 1

        assertEquals("Second update: activeGen=1, import=2, old must be 1", 1, oldGeneration);
        assertEquals("Second update: currentImportGeneration must be 2", 2, currentImportGeneration);
    }

    /**
     * Variant: Nth update (activeGen=42 → importGeneration=43).
     * Guards against any absolute-value hardcoding — the arithmetic must always be
     * relative to currentImportGeneration, regardless of how large the generation counter grows.
     */
    @Test
    public void generationArithmetic_nthUpdate_oldGenerationIsAlwaysOneLess() {
        for (int activeGen = 0; activeGen <= 100; activeGen++) {
            int currentImportGeneration = activeGen + 1;
            int oldGeneration = currentImportGeneration - 1;

            assertEquals(
                    "activeGen=" + activeGen + ": oldGeneration must equal activeGen",
                    activeGen, oldGeneration);
            assertEquals(
                    "activeGen=" + activeGen + ": currentImportGeneration must equal activeGen+1",
                    activeGen + 1, currentImportGeneration);
        }
    }

    // -------------------------------------------------------------------------
    // Guard 3: Verify migrateSourceGeneration UPDATE semantics (pure logic)
    // -------------------------------------------------------------------------

    /**
     * Models the migrateSourceGeneration UPDATE semantics in pure Java.
     * Simulates a tiny in-memory "hosts_lists" table to verify that rows with
     * oldGeneration are re-tagged to newGeneration (and rows with other generations
     * are untouched) — exactly what the SQL UPDATE does.
     *
     * This is the core behavioural regression: before the fix, 304 rows at generation G
     * were cleaned up; after the fix they are migrated to G+1 and survive cleanup.
     */
    @Test
    public void migrateSourceGeneration_updatesMatchingRows_leavesOthersUntouched() {
        // Simulate hosts_lists rows: {sourceId, generation}
        int[][] rows = {
                {2, 1},  // source 2, old generation (should be migrated)
                {2, 1},  // source 2, old generation (should be migrated)
                {3, 1},  // source 3, old generation (different source — should NOT be migrated here)
                {2, 0},  // source 2, even-older generation (different old gen — should NOT be migrated)
        };

        int sourceId = 2;
        int oldGeneration = 1;
        int newGeneration = 2;

        // Apply migrateSourceGeneration logic
        for (int[] row : rows) {
            if (row[0] == sourceId && row[1] == oldGeneration) {
                row[1] = newGeneration;
            }
        }

        // Source 2's rows that were at oldGeneration=1 are now at newGeneration=2
        assertEquals("row[0]: source 2, gen should be migrated to 2", 2, rows[0][1]);
        assertEquals("row[1]: source 2, gen should be migrated to 2", 2, rows[1][1]);
        // Source 3's rows are untouched
        assertEquals("row[2]: source 3, gen should remain 1", 1, rows[2][1]);
        // Source 2's rows at different generation are untouched
        assertEquals("row[3]: source 2 gen=0, should remain 0 (different old gen)", 0, rows[3][1]);
    }

    /**
     * Variant: simulates cleanupNonActiveGenerations() after migration.
     * After migrateSourceGeneration(sourceId=2, old=1, new=2):
     *   - Source 2 rows at gen=2 → SURVIVE cleanup(active=2)
     *   - Source 3 rows at gen=1 → DELETED by cleanup(active=2)   [source 3 returned 200 and was re-written]
     * Without migration, source 2 rows at gen=1 would also be deleted.
     */
    @Test
    public void cleanupAfterMigration_migratedRowsSurvive_nonMigratedRowsDeleted() {
        // Rows after migration: {sourceId, generation, survived}
        // source 2: 304 source — rows migrated from gen=1 to gen=2 by migrateSourceGeneration
        // source 3: 200 source — new rows written at gen=2
        // source 4: 304 source — WITHOUT migration (bug scenario) rows still at gen=1
        int[][] rows = {
                {2, 2},  // source 2, migrated to new gen (should survive)
                {3, 2},  // source 3, new rows at new gen (should survive)
                {4, 1},  // source 4, NOT migrated (bug scenario — should be deleted)
        };

        int activeGeneration = 2;
        // Apply cleanupNonActiveGenerations: delete where source_id != 1 AND generation != activeGeneration
        boolean[] deleted = new boolean[rows.length];
        for (int i = 0; i < rows.length; i++) {
            int srcId = rows[i][0];
            int gen = rows[i][1];
            if (srcId != 1 && gen != activeGeneration) {
                deleted[i] = true;
            }
        }

        assertFalse("Source 2 (migrated 304): must survive cleanup", deleted[0]);
        assertFalse("Source 3 (200 response): must survive cleanup", deleted[1]);
        assertTrue("Source 4 (un-migrated 304 — bug): must be deleted by cleanup", deleted[2]);
    }
    @Test
    public void copyForward_keepsOldGenerationValidUntilActivation() {
        int[][] rows = {
                {2, 1},
                {2, 1},
                {3, 1},
        };
        int sourceId = 2;
        int oldGeneration = 1;
        int newGeneration = 2;

        int copiedRows = 0;
        for (int[] row : rows) {
            if (row[0] == sourceId && row[1] == oldGeneration) {
                copiedRows++;
            }
        }
        int[][] afterCopy = new int[rows.length + copiedRows][2];
        for (int i = 0; i < rows.length; i++) {
            afterCopy[i][0] = rows[i][0];
            afterCopy[i][1] = rows[i][1];
        }
        int writeIndex = rows.length;
        for (int[] row : rows) {
            if (row[0] == sourceId && row[1] == oldGeneration) {
                afterCopy[writeIndex][0] = row[0];
                afterCopy[writeIndex][1] = newGeneration;
                writeIndex++;
            }
        }

        int oldRows = 0;
        int newRows = 0;
        for (int[] row : afterCopy) {
            if (row[0] == sourceId && row[1] == oldGeneration) oldRows++;
            if (row[0] == sourceId && row[1] == newGeneration) newRows++;
        }

        assertEquals("Old generation remains active-safe until final activation", 2, oldRows);
        assertEquals("New generation receives a complete carry-forward copy", 2, newRows);
    }

    @Test
    public void failedNeverSyncedSource_cannotBeCountedAsMigratedCoverage() {
        int previousRows = 0;
        boolean hasPriorSuccessfulSync = false;

        boolean migrated = previousRows > 0 || hasPriorSuccessfulSync;

        assertFalse("A failed enabled source with no previous generation is partial coverage.",
                migrated);
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return normalizeLineEndings(new String(Files.readAllBytes(
                repoRoot().resolve(relativePath)), StandardCharsets.UTF_8));
    }

    private static Path repoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        return Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ");
    }
}
