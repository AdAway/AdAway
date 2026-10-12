# App Surface Review - 2026-10-12

Scope: `ui/home`, `ui/lists`, `ui/log`, `ui/prefs`, `ui/domainchecker`,
`ui/onboarding`, `ui/adware`, `helper`, `broadcast`, `tile`, `model/backup`;
parent additionally assigned `ui/adblocking` during this pass.
Excluded: URL SourceEdit, hosts sources, discover/filterlists, shared strings,
version metadata, canonical task trackers, commits/pushes, Gradle execution.

## Bounded Pass

- [x] Read CLAUDE.md and lessons; inspect assigned production surfaces and related tests.
- [x] Fix bounded confirmed bugs and add targeted regression tests.
- [x] Run available non-Gradle checks; report Android integration gates to parent.
- [x] Record fixes, coverage, and unresolved/manual limits.

## Confirmed Bugs Fixed

All findings below are source-confirmed. JVM execution and Android execution are
distinguished in the verification section; compiled instrumentation is not a passing
device test.

| ID | Evidence / User Impact | Completed Fix | Regression Proof |
| --- | --- | --- | --- |
| APP-01 (P2) | `BackupImporter` inserted sources and earlier lists before parsing later lists; over-limit arrays were silently truncated, and `readLine()` accumulated unlimited input before limits ran. `BackupFormat` accepted arbitrary host text and missing redirect targets. A failed/crafted restore could partly change protection or exhaust memory. | Validate all arrays/rows first, reject over-limit input, bound reader to 16 Mi characters, require valid hosts/redirect targets, and commit all mutations in one Room transaction. Preserve valid legacy wildcard allows. | `BackupValidationTest` (5 JVM cases); `BackupImportAtomicityTest.malformedLaterListLeavesSourcesAndExistingRulesUnchanged`, `databaseFailureRollsBackEarlierSourceAndRuleWrites`, `overLimitDocumentIsRejectedInsteadOfPartiallyImported`. |
| APP-02 (P2) | Import used `getHostId(host)` without rule kind. Exporting an exact allow and a suffix block for the same hostname then restoring collapsed them into one row. | Match imported user rules by host AND kind, replacing that identity only and retaining other kinds/source rows. Repeated restore remains idempotent. | `BackupImportAtomicityTest.exactAndSuffixRulesSurviveRepeatedRestoreWithoutCollapsing`. |
| APP-03 (P2) | Import/export wrapped possibly-null provider streams immediately; revoked grants throw `SecurityException` outside the IOException handler. Export opened the destination before serialization and used provider mode `w`, which need not truncate. | Null streams and denied grants become IO failures; async failures reach the existing failure toast. Use application context, explicit UTF-8 and `wt`; serialize before opening the destination. | Compiled and source-inspected; existing `PrefsBackupRestoreSafInstrumentedTest.backupExporterAndImporterRoundTripThroughProviderUri` is a parent execution gate. No newly executed provider-failure test. |
| APP-04 (P2) | `observeForever(this::updateTile)` and `removeObserver(this::updateTile)` create distinct observers; repeated listening leaked observers, also when the ad-block model changed. | Keep one observer and the exact observed LiveData; remove on stop/destroy and before rebinding; tolerate null state. | `AdBlockingTileServiceInstrumentedTest.listeningStopsObservingOriginalModelEvenAfterModelChanges`. |
| APP-05 (P2) | Pager constructor-created fragment fields differ from Android-restored fragments. Add after rotation called an unattached fragment with null activity/model. Paging submissions also used the fragment rather than view lifecycle. | Resolve attached fragments from FragmentManager, scope paging to view lifecycle, and finish action mode/release activity when destroying the view. | `ListsAddUserRulesInstrumentedTest.addDialogsUseRestoredFragmentsAfterActivityRecreation`. |
| APP-06 (P2) | `LogViewModel.addListItem()` always inserted; the schema has no unique host constraint. Choosing Block after Allow left the old allow rule active. | Transactionally replace user EXACT rows for the selected host while preserving suffix/source rules and refreshing user source counts. | `LogRuntimeTruthTest.changingExactUserRuleReplacesAllowAndPreservesSuffixRule` verifies stored identities and effective runtime type. |
| APP-07 (P2) | Onboarding kept chosen method and finish-after-VPN-consent only in fields. Recreation lost the selection/completion intent. Automatic detection could update a destroyed activity or overwrite a chosen method. | Save/restore both fields, render restored selection, avoid rerunning initial detection after recreation, and guard stale auto-detection callback. | `OnboardingFirstRunInstrumentedTest.recreationRetainsSelectionAndPendingVpnCompletion`. |
| APP-08 (P2) | `ApplyConfigurationSnackbar.apply()` called `endLoading()` directly on diskIO, where failure Snackbar/ImageView creation and shared UI state changes occurred. Runtime failures left the installing state stuck. | Dispatch completion through mainThread for success/checked/runtime failure, remove blocking sleep, prevent overlapping applies, and skip completion views after detach. | `ApplyConfigurationSnackbarThreadTest.successfulApplyBuildsCompletionFeedbackOnMainThread`, `hostFailureBuildsErrorFeedbackOnMainThread`, `unexpectedDatabaseFailureEndsLoadingOnMainThread`. Tests use real app executors, real Snackbar views and the Android main Looper with controlled model outcomes. |

## Inspected Coverage

- Home: navigation/restoration and onboarding handoff; ViewModel actions; count/progress,
  error and leak-status view bindings. Static bounded pass, no home code changes.
- Lists: pager restoration, search/filter state, add/edit validators, toggles/actions,
  adapters and paging lifecycle. Fix limited to restored-fragment/view ownership.
- DNS log: model loading/sort/recording, rule actions, redirect validator and adapter.
- Preferences: main/theme/language/telemetry/method navigation; root redirection/webserver,
  VPN exclusions/restart, update scheduling/notification preferences, SAF backup launchers.
- Domain checker: normalization, runtime lookup and add/remove actions, result rendering
  and loading state. No changes made in this lane.
- Onboarding: method selection, consent result handling, default subscription handoff.
- Adware: package matching/scan executor, result binding and uninstall intent.
- Helpers: notification channels/intents, theme application and preference access patterns.
- Broadcast/tile: command parsing, boot/update paths and manifest receiver/service permission
  boundaries (read-only manifest inspection), observer and toggle behavior.
- Backup/apply feedback: complete owned files reviewed and patched as above.

## Verification

- PASS: direct JDK 21 `javac -proc:none` compilation of every patched production class
  and every new/modified test, using Android 36 SDK, cached AndroidX/library jars,
  generated R classes and existing project dependency classes. Output only under
  `/tmp/adaway-app-review.xFS8T3`, not shared Gradle outputs. Final snackbar change
  was recompiled afterward. This is a targeted compile check, not a clean app build.
- PASS: standalone JUnit 4.13.2 execution, **18 tests**, no failures:
  `BackupValidationTest` (5), `ApplyConfigurationSnackbarContractTest` (1),
  `OnboardingFirstRunContractTest` (1), `ListsSearchBarTest` (10),
  `LogSortActionContractTest` (1). JSON runtime: repository-pinned 20260522 jar.
- PASS: scoped `git diff --check` for this lane. Whole-worktree check reported an
  unrelated trailing blank line in parent-owned `tasks/todo.md`; left untouched.
- NOT RUN here: Gradle, Android instrumentation, emulator/device installation,
  release packaging/signing. Parent owns those consolidated gates.

## Parent Gates And Material Limits

- Run the new/modified Android methods listed in APP-01/02/04/05/06/07/08 above;
  also rerun existing SAF round-trip and ordinary Lists/onboarding/log tests.
- Snackbar thread tests are compiled but NOT executed. They assert worker/main
  Thread identity and call the real completion UI, not just source-text matching.
- Backup merge policy remains additive for sources; existing source settings are
  not overwritten. Imported source redirect enablement remains forcibly disabled.
- Oversized backups now fail clearly (existing import-failed feedback) instead of
  claiming a partial success: 200 sources, 50,000 total rules, 16 Mi characters.
  A valid export exceeding these limits is not restorable in this bounded format.
- Restore does not recover persisted grants for content-URI sources. It retains the
  existing requirement to apply/update afterward; no automatic remote downloads,
  root-host installation or VPN activation was added by this lane.
- Provider revocation/null-stream branches and provider-specific truncation still
  need device/provider execution. Serialization-before-open cannot guarantee atomic
  export if a provider fails midway through writing.
- Unmodified surface coverage is static, not a bug-free claim. Home detach/reattach,
  adware uninstall/rescan, domain-checker rapid actions/error paths, settings on OEM
  devices, real boot/package replacement, and real root/VPN consent remain outside
  this finite executed proof set. No broad refactors or shared-resource changes.

## Consolidation Follow-Up

- Parent's `/tmp/adaway-review-checks.log` exposed eight missed one-argument calls
  in the existing `BackupFormatSecurityTest`. The earlier targeted compile checked
  the new tests but did not cover this existing caller; that was a verification gap.
- Updated all eight calls and the Javadoc to the typed parser: redirect validation
  uses `REDIRECTED`; ordinary/suffix/missing-kind rules use `BLOCKED`. Added type
  assertions to accepted cases. No type-guessing compatibility overload was added,
  so missing redirect targets remain rejected for actual redirected-list entries.
- PASS: direct JDK 21 compilation of all `model/backup` production and JVM test
  sources. PASS: standalone JUnit execution of `BackupFormatSecurityTest` (8) and
  `BackupValidationTest` (5), **13 tests**, no failures. No Gradle run by this lane.
- Rechecked Java callers across main/test/androidTest including ignored files;
  no one-argument `hostFromJson` call remains. Root `Files.*` errors were untouched
  and remain owned by the runtime agent.
- Verification lesson: when changing a package-local signature, compile all
  existing callers/tests, not only newly added or directly patched tests.

## Connected Recreation Timeout Follow-Up

- Parent's connected run failed
  `ListsAddUserRulesInstrumentedTest.addDialogsUseRestoredFragmentsAfterActivityRecreation`
  at 23:27:31 with the 90-second JUnit timeout. The saved per-test log shows the
  first activity destroyed at 23:26:01.848 and its replacement resumed at
  23:26:01.951. Blocked and allowed Add actions then completed; the timeout stack
  points to the SECOND `scenario.recreate()` (old test line 122).
- The stack stops in `ActivityScenario.recreate():708 -> waitForIdleSync()`.
  Inspection of the cached AndroidX test-core 1.7.0 bytecode confirms this is an
  unbounded preliminary idle wait, before requesting recreation. There is no
  second relaunch in the trace, and rendering continues throughout the wait.
  This proves a test synchronization failure, not a failure to restore the second
  activity; the exact source of sustained rendering is not established.
- Test-only patch: call the real `Activity.recreate()` on the main thread and
  wait at most 10 seconds for a different ListsActivity to reach RESUMED via the
  lifecycle monitor. Always unregister the callback. Retain restored-fragment
  readiness, all three Add dialogs, and persisted-rule assertions. Finish the
  activity before scenario cleanup can wait for idleness. No production changes
  or timeout increase.
- Connected rerun is PENDING. No Gradle invocation, APK installation, device
  setting change, or interruption of the active consolidated suite was performed.
  Only existing logs (including read-only logcat) and cached dependencies were
  inspected. The original failing connected execution remains the runtime proof
  until the parent reruns this exact method with the rebuilt test APK.
- PASS: the patched test compiles with JDK 21 against the Android SDK 36 Java
  API image, current compiled app/test dependencies, and cached AndroidX jars.
  Output is isolated in `/tmp/adaway-lists-recreation.bOlRfk`; no shared build
  output was changed. This is compilation only, not a passing connected test.
- Verification lesson: distinguish recreation's preliminary global-idle wait
  from actual lifecycle restoration; use bounded lifecycle synchronization when
  animations keep the UI active, and keep the user-visible assertions intact.

## PR 10 CI Cleanup Follow-Up (2026-10-13)

- Scope: only `SourcesToggleApplyInstrumentedTest.java` and this appended record.
  Reviewed commit `5a72c982d8`; no production change or SourceLoader change.
- Downloaded evidence: `app/build/reports/ci-connected-review/ci-artifacts/connected-logcat.txt`
  and `app/build/reports/ci-connected-review/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 14-_app-.xml`.
  XML lines 604-620 record the 60.311-second failure of
  `togglingSourceOffPersistsRowsAndApplyRunsProtectionUpdate`, with
  `ActivityScenario.close():421 -> moveToState():654 -> waitForIdleSync()` at
  original test line 118. The compiled method's line table puts line 118 on the
  normal close path AFTER the final assertion; exceptional close maps to line 98.
- Root cause: teardown waits for global main-thread idleness before requesting
  activity destruction, while the still-resumed UI continues rendering. Logcat
  records source synchronization completing at 21:36:13.985 and continuous app
  frame output until the 21:37:12.919 timeout. The runner then directly finishes
  the same HomeActivity at 21:37:12.977; it reaches PAUSED at 21:37:13.011 and
  DESTROYED at 21:37:13.963. This supports idle-starved cleanup rather than failed
  source/apply assertions or a blocked main thread. The precise animating view or
  observer is not identified by these artifacts, and is not claimed as a new
  production defect.
- Minimal test fix: retain the captured HomeActivity, call `finish()` directly
  through `runOnMainSync` in `finally`, and poll `isDestroyed()` on the main
  thread with the existing 10-second deadline before `scenario.close()`. A failed
  destruction wait raises a specific assertion without entering the idle-gated
  close. All source-toggle, disabled-row, snackbar-action, apply-order/count and
  final source-state assertions remain; the 60-second test timeout is unchanged.
- Validation is reserved for the parent: no Gradle, emulator/device commands,
  builds, or test execution were run for this follow-up. The CI failure is the
  pre-fix runtime evidence; the patched connected method still needs a rerun.
- PASS: scoped `git diff --check`; reviewed the diff to confirm all behavioral
  assertions and the original timeout are preserved.
- Parent validation: both CI-repaired classes passed (seven tests), followed by
  the full 297-test API 34 run with prepared VPN consent: 291 passed, six intentional
  skips, zero failures, 177.963 seconds. Protected CI remains open.
