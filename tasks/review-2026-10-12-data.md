# Data Pipeline Review - 2026-10-12

## Scope and Ownership

Finite review of `model/source`, `db`, `model/git`, and associated tests on
`codex/october-release-review`. Read `CLAUDE.md` and `tasks/lessons.md`.
Initial scope excluded UI; parent subsequently authorized SourceEditActivity save/delete
integration and additive editor regression tests. No VPN, catalog lists, release files,
canonical trackers, schema, or migration edits.
No commits, pushes, or Gradle invocations. Other owners' shared-workspace edits were preserved.

## Findings and Fixes

| ID | Priority | Evidence and change | Regression coverage |
| --- | --- | --- | --- |
| DATA-01 | P1 | `SourceLoader.parse` cleaned staging before stopping workers and only used `shutdown()`. Inserter interruption could flush/commit partial data. It now interrupts and joins workers before cleanup, propagates parser failures, and rolls back interrupted inserts. `SourceModel` cancels its HTTP calls, joins outer pools before dropping scratch tables, cleans unconsumed temporary downloads, and treats coordinator interruption as cancellation. Stop also exits a paused file-source loop. | `SourceLoaderDatabaseSemanticsTest.interruptedParseJoinsWorkersBeforeReturningAndRollsBack`; existing HTTP stop test and performance read-failure test. |
| DATA-02 | P1 | A source comment equal to `#endofqueuemarker` was treated as an internal termination message. Enough copies silently truncated an import or stranded workers. Reader/parser termination now uses sentinel identity, not source-text equality. | `SourceLoaderDatabaseSemanticsTest.sourceTextCannotImpersonateEndMarkers`. |
| DATA-03 | P1 | `||example.com^/advert` and `example.com^banner` were truncated into whole-domain blocks. Only a complete DNS-representable ABP domain rule is now accepted; plain-domain input rejects caret expressions. | `SourceLoaderParserPatternsTest.abpSeparatorFollowedByPathIsNotAWholeDomainBlock`. |
| DATA-04 | P1 | Disabling a source during its download could be undone at the rule level: parser rows were always enabled using the old source snapshot. Raw imports now read current selection inside the SQLite writer transaction and preserve it for inserted/staged rows. | `SourceModelHttpConditionalTest.disableDuringDownloadDoesNotImportEnabledRowsFromStaleSnapshot`. |
| DATA-05 | P1 | Full-update FILE imports caught only `IOException`, although loader failures are `IllegalStateException`; errors escaped without the normal carry-forward/error-recording path. Targeted failures also omitted source error metadata. Additionally, HTTP 200 HTML/error pages with zero supported rules could replace working data. Runtime parse failures now enter the normal failure path, targeted errors are recorded, and all-unrecognized nonempty imports fail instead of publishing empty data. | `SourceModelGenerationFailureTest.fullUpdate_fileParserFailureIsRecordedAndKeepsActiveGeneration`; `SourceModelHttpConditionalTest.successfulHttpErrorPageDoesNotReplaceWorkingFilters`. |
| DATA-06 | P2 | Successful 200 responses without ETag retained an old ETag; weak ETags were modified; expired/error metadata still permitted HTTP-cache reuse; unsolicited 304s were accepted without validators; targeted 304s renewed the weekly full-fetch clock. Changed responses now replace/clear ETag, opaque weak tags are preserved, expired/error requests force network validation, invalid 304s fail, and 304s preserve the last body-fetch date. | HTTP tests `changedResponseWithoutEtagClearsPreviousValidator`, `weakEtagIsStoredAndSentUnchanged`, `expiredMetadataForcesNetworkInsteadOfReusingHttpCache`, `unsolicited304FailsAndPreservesPreviousRows`, `notModifiedDoesNotRenewTheLastBodyFetchDate`. |
| DATA-07 | P2 | Downloads had no byte cap and `readLine()` could allocate an arbitrarily large string before the bounded queue helped. Downloads now cap declared and actual body bytes at 512 MiB; chunked character reading caps each line at 64 Ki characters and handles initial UTF-8 BOM and CR/LF. Oversized inputs fail safely. Existing generated/read-failure performance fixtures were adapted to the character-reader contract. | HTTP `oversizedResponseFailsBeforeReadingAndPreservesPreviousRows`; loader `oversizedLineFailsWithoutPublishingAPartialImport`, `utf8BomAndMixedLineEndingsPreserveFirstAndLastRules`. |
| DATA-08 | P2 | Gist owner-only URLs passed the length guard then indexed a missing segment; Gist lookalike authorities matched a prefix without `/`. GitHub commit checks ignored the selected ref; modern GitLab `/-/raw/` and subgroup URLs were misparsed, and query paths were concatenated unescaped. Fixed checked rejection/authority prefix and structured API URL construction with ref/path preservation. | New `GitHostsSourceUrlTest` (7 tests). |
| DATA-09 | P2 | Parallel first downloads could independently initialize multiple HTTP clients/caches against the same cache directory. Client creation is now synchronized. | `SourceModelGenerationFailureTest.parallelDownloadsShareOneHttpClientAndCache`. |
| DATA-10 | P1 | Source-edit transactions can race updates holding an old URL/format snapshot. Added `runSourceMutation(Supplier<T>)`, sharing the update operation's atomic gate; busy work throws `UPDATE_IN_PROGRESS`. Removed unreachable false/success branches around the gate. Release clears pool ownership before reopening the gate. SourceEdit save/delete now hold the gate through transaction and runtime sync, reject busy actions with the existing localized error resource, and enqueue only after release. | `SourceModelGenerationFailureTest.mutationGateRejectsOverlappingUpdatesAndAlwaysReleases`; editor tests `busyUpdateRejectsSaveAndDeleteThenDeleteClearsRuntime`, `urlEditClearsOldRuntimeBeforeReplacementDownloadCompletes`. |
| DATA-11 | P1 | `HostsSourcesImmediateUpdateWorker.doWork()` returned success when retrieval returned false, losing the replacement one-time download requested by a new subscription. It now returns retry on false, as it already does for busy/error exceptions, and applies only after successful retrieval. | Actual worker `doWork()` tests in `SourceUpdateServiceWorkManagerTest`: `immediateWorkerRetriesWhenRetrievalDoesNotComplete`, `immediateWorkerRetriesWhenAnotherUpdateOwnsTheGate`. |
| DATA-12 | P1 | Parent's connected run exposed a second exception during recovery: the injected insert constraint was caught during parsing, then escaped during `copySourceGenerationReplacingTarget` before failure metadata and generation cleanup. Both failed-source and unchanged-source carry-forward loops now catch SQLite failures, mark the generation unsafe, and use the existing abort path. Active generation remains untouched. | Existing failing `fullUpdate_fileParserFailureIsRecordedAndKeepsActiveGeneration` preserved; added `fullUpdate_carryForwardWriteFailureAbortsEvenWithSuccessfulSource`. Both require rerun after the fix. |

## Parent Integration

Completed under the parent's later authorization: the application's shared SourceModel wraps
SourceEdit save/delete transactions and immediate runtime synchronization. URL/format edits and
deletes invalidate root export state and rebuild runtime state before reporting success or
starting a replacement download. Busy mutations change no rows and show
`error_update_in_progress_message`. The worker is enqueued only after gate release.

Preserved the parent's stable-identity/selection/metadata rename behavior, duplicate checks,
and clearing old rows/validators on URL or parsing-option changes. Existing five editor test
bodies were left intact; two tests were added. No DAO API changes were required.

## Integration Follow-Up and Final Freeze

- SourceEdit save/delete integration is complete, including busy-error handling and immediate
  runtime synchronization before replacement downloads. The two additive editor regressions
  remain in `SourceEditAddEditInstrumentedTest`; the parent's original five tests are unchanged.
- The connected-test carry-forward exception was fixed as DATA-12. Android reruns remain owned
  by the parent; this report does not claim that pending rerun passed.
- The subsequent JVM failure at `Generation304MigrationTest` line 168 was an exact source-text
  assertion that assumed carry-forward had no try/catch wrapper. Updated only that test method
  to inspect each loop's coverage guard, preserving direct failed-source carry-forward and
  deduplicated 304 carry-forward checks. Strengthened the unsafe-generation assertion to require
  cleanup and `DOWNLOAD_FAILED` before activation.
- Direct JDK/JUnitCore rerun of `Generation304MigrationTest`: **29 tests passed**. No Gradle.
  This final follow-up changes only that test and this report; production files are frozen.

## Verification

- Direct JDK 21 compilation with `--release 17 -proc:none` against existing app classes and local
  dependencies succeeded for the modified production/test Java files. This is not a replacement
  for Room annotation processing, Android packaging, or instrumentation.
- Direct JUnitCore run passed **125 tests** across `GitHostsSourceUrlTest`,
  `SourceLoaderParserPatternsTest`, `SourceLoaderTest`, `Generation304MigrationTest`, and
  `SourceModelCarryForwardConcurrencyTest`.
- Scoped `git diff --check` passed. An unrelated canonical tracker trailing blank line was not edited.
- Android regression tests are added but not executed by this owner. Gradle is reserved for the
  parent's consolidated run.
- During the parent's 286-test connected run, inspected the emitted per-test logcat for the
  file-parser failure and confirmed the carry-forward stack. Final production/editor/test Java
  compile passed after DATA-12; the running device suite predates that fix.

Exact unit-test targets for the parent:

```text
org.adaway.model.git.GitHostsSourceUrlTest
org.adaway.model.git.GitHostsSourceTest
org.adaway.model.source.SourceLoaderParserPatternsTest
org.adaway.model.source.SourceLoaderTest
org.adaway.model.source.Generation304MigrationTest
org.adaway.model.source.SourceModelCarryForwardConcurrencyTest
```

Exact Android test targets, including existing integration guards:

```text
org.adaway.model.source.SourceLoaderDatabaseSemanticsTest
org.adaway.model.source.SourceModelHttpConditionalTest
org.adaway.model.source.SourceModelGenerationFailureTest
org.adaway.model.source.SourceModelRuntimeTruthMutationTest
org.adaway.model.source.SourceUpdateServiceWorkManagerTest
org.adaway.model.source.SourceLoaderPerformanceTest#parseReadFailure_rollsBackTargetGenerationAndKeepsActiveRows
org.adaway.model.source.SourceLoaderPerformanceTest#parseInsertSyncAndRootApply_mixedRules10k_staysWithinBudget
org.adaway.db.MigrationTest
org.adaway.db.SourceDbTest
org.adaway.ui.source.SourceEditAddEditInstrumentedTest
```

## Review Limits

- Inspected migration registration through schema 32, generation copy/promotion, source-selection
  transactions, delete cascades, source statistics, and active/root runtime-query paths. No new
  migration defect was established; no schema change was made. Existing migration/runtime tests
  remain the consolidation gate, not claimed executed evidence.
- Cancellation waits for worker termination before cleanup. A permanently blocked third-party
  content-provider read is not given an OS-level kill/timeout by these changes.
- Limits are per URL body and per line, not an aggregate disk budget across every selected source.
  Legitimate empty/comment-only files remain valid; all-unrecognized nonempty input is rejected.
- No live Git provider, production filter endpoint, large-scale performance benchmark, device
  storage-exhaustion experiment, or platform/root/VPN test was run by this owner.
- Unescaped slashes inside Git ref names remain ambiguous in raw URL paths; tests cover ordinary
  refs, percent-decoded paths, explicit GitHub refs, and modern/legacy GitLab routes.
- Mutations bypassing the new shared gate still need caller-side coordination. SourceEdit wiring
  is included; other owners' UI/runtime changes remain outside this pass.

## DATA-13: Offline Local-Only Updates Incorrectly Require Network

- Severity: P1. The parent's full-suite `HomeUpdateActionsInstrumentedTest`
  `checkForUpdateButtonRunsLocalSourcePipelineBeforeApply` failure is corroborated by
  `/tmp/adaway-review-home-action-logcat.log` at 23:59:13: `netInfo=null` causes
  `NO_CONNECTION` before the local `content://org.adaway.test.hosts/success.txt` source is read.
- Fix: move the full-update connectivity check after source categorization inside the existing
  mutation gate and cleanup `try/finally`. Only enabled URL sources require connectivity.
  Local-only/user-only updates and disabled URL cleanup retain their existing offline pipeline;
  enabled URLs still fail with `NO_CONNECTION` before importing files or activating a generation.
- Added `SourceModelOfflineFileTest` with six deterministic Android regressions: local-file
  import and runtime resolution; disabled URL plus local-file import; user-only rules; disabled
  URL-only cleanup preserving user rules; enabled URL rejection preserving active rows/generation,
  restoring import pragmas, leaving no deduper table, and releasing the gate for a local retry;
  and rejection of an overlapping update before checking connectivity or source selection.
  A `ContextWrapper` returns null for `CONNECTIVITY_SERVICE`; no global Wi-Fi or emulator changes.
  Teardown drains the serial disk executor before closing the in-memory database.
- Verification: existing parent failure/log inspected; scoped whitespace check performed.
  New tests are not executed by this owner: no Gradle or emulator commands, per ownership split.
  Parent target: `org.adaway.model.source.SourceModelOfflineFileTest`, followed by the original
  `HomeUpdateActionsInstrumentedTest` regression.

Parent integration evidence: all six offline regressions failed against the old installed
app, then passed with the fix, together with both Home update-action tests (8/8).
Fresh five-million-line pipeline proof also passed; exact counts, timings, and the
non-privileged root-export boundary are recorded in the main review ledger.
