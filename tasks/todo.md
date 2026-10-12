# v13.4.5 - Agentic Loop + Domain Checker Actions (Superseded)

## Design Decision Log

### Decision 1: Agentic Loop Model
- **Chosen**: Agentic loop (auto-execute CHECK_DOMAIN, single user-approval for write actions)
- **Alternatives**: Inline follow-up, simple reset
- **Rationale**: Best UX - "fix WhatsApp" resolves end-to-end; user approves only write ops

### Decision 2: Max LLM Turns
- **Chosen**: 2 (initial -> check results -> final plan)
- **Rationale**: Prevents runaway loops; CHECK_DOMAIN is the only read-only action

### Decision 3: Tool Result Security
- **Chosen**: Inject only AiActionExecutor result strings (hostname + fixed status word)
- **Rationale**: hostname already validated by RegexUtils.isValidHostname(); status is code-controlled

### Decision 4: Domain Checker Actions
- **Chosen**: Add contextual action buttons below result in existing fragment
- **Constraint**: No overlapping components on home screen
- **Actions**: Allow (if blocked), Block (if not blocked), Remove from allowlist (if allowed)

## Implementation Checklist
- [x] Superseded by the 2026-06-15 AI feature cut. The default product no longer ships
  `FilterListSuggester`, `AiSuggestBottomSheet`, AI settings, or AI user-rule mutation surfaces.
- [x] Domain Checker actions were handled as a separate non-AI runtime-truth path.
- [x] Follow-up guards now keep AI production/UI sources, default docs, and AI-only network trust
  overrides out of the shipped app.

---

# Market-Leading Quality Plan

## Plan - 2026-06-29 FilterLists Selected Action Responsiveness
- [x] Reproduce the reported symptom path in code by tracing checkbox state to selected bulk
  button enablement.
- [x] Fix selected bulk actions so visible cached rows remain actionable during a background
  directory refresh.
- [x] Add a static regression guard for the cached-row refresh state.
- [x] Verify with focused JVM/resource checks and the connected selected-bulk UI test.

## Review - 2026-06-29 FilterLists Selected Action Responsiveness
- Root cause: `refreshBulkActionsState()` treated any `directoryLoading` refresh as busy, while
  row checkboxes stayed enabled. With cached rows visible during a background directory refresh,
  checking one or more rows could leave `Subscribe selected` and `Unsubscribe selected` disabled.
- Changed the busy gate to block selected actions only when the directory is loading and no rows
  are available yet, or when a bulk operation is actually running.
- Added `DiscoverPresetSubscriptionTest` coverage so a background directory refresh cannot disable
  selected-row commands after visible cached rows exist.
- Verification passed with JDK 21: `git diff --check`, focused
  `DiscoverPresetSubscriptionTest` plus `UserStoryStatusTrackerTest`, `:app:processDebugResources`,
  and connected `FilterListsVisibleBulkActionsInstrumentedTest` on `adaway-api34-16g`.

## Plan - 2026-06-29 Branch and CI/CD Protection
- [x] Inspect current repo admin permission, default branch, PR check contexts, branch protection,
  and Actions repository settings.
- [x] Protect `master` with required release-gate CI contexts, strict up-to-date checks, admin
  enforcement, conversation resolution, and blocked force-push/delete.
- [x] Harden GitHub Actions defaults to read-only tokens and immutable action references.
- [x] Patch workflow definitions so required checks always report and CI jobs request only needed
  token permissions.
- [x] Verify workflow syntax/hygiene and record current evidence without claiming PR #7 is green.

## Review - 2026-06-29 Branch and CI/CD Protection
- GitHub reports `stevesolun/AdAway` default branch `master` and this operator has `ADMIN`
  permission.
- `master` branch protection is now enabled with strict required status checks:
  `Analyze (cpp)`, `Analyze (java)`, `CodeQL`, `Connected Android tests`, `Development build`,
  and `Validate locales`.
- Protection also enforces admins, requires conversation resolution, and blocks force pushes and
  branch deletion.
- Repository Actions settings now use read-only default workflow tokens, disallow Actions tokens
  from approving pull requests, and require SHA-pinned action references.
- Workflow hardening in this slice: Android CI declares `contents: read`, CodeQL and locale
  validation cancel stale duplicate runs, locale validation always emits the required check and
  fails with a diff instead of auto-committing to PR branches, and the issue checker explicitly
  requests `issues: write`.
- Verification passed: `git diff --check`, YAML parse over all `.github/workflows/*.yml`, source-file
  execution of `AndroidLocaleChecker.java` with JDK 21, no floating `uses: ...@v*`/`@main`/`@master`
  workflow action refs, and live GitHub API reads for branch protection plus Actions permissions.
- Current PR #7 is intentionally not declared green by this slice; before these changes,
  `Connected Android tests` was red and the protected branch would block merging until the fresh
  head passes required checks.

## Plan - 2026-06-29 Connected UX Touch Target Gate
- [x] Watch the fresh protected-branch CI run after the workflow-hardening push.
- [x] Inspect the failed connected Android job and identify whether it came from CI hardening or
  product UX coverage.
- [x] Fix the Discover filter-list switch touch target regression caught by `UxDeviceMatrixTest`.
- [x] Add a focused static guard so the switch controls cannot shrink below the connected-test
  threshold again.
- [x] Run focused local verification before pushing the fix.

## Review - 2026-06-29 Connected UX Touch Target Gate
- Fresh PR #7 head `a7880320` passed `Validate locales`, `Analyze (cpp)`, `Analyze (java)`,
  `CodeQL`, and `Development build`, then failed `Connected Android tests`.
- The connected failure was `UxDeviceMatrixTest` on the Discover screen:
  `filterlistsCompatibleOnlySwitch` measured `519x105`, below the test's 48dp touch-target floor
  on the CI emulator.
- The root cause was `fragment_discover_filterlists.xml` explicitly setting both filter-list
  `MaterialSwitch` controls to `android:minHeight="40dp"`.
- Fixed both filter-list switches to `48dp` and extended `DiscoverPresetSubscriptionTest` to guard
  `filterlistsCompatibleOnlySwitch` and `filterlistsShowSubscribedSwitch`.
- Local verification passed with JDK 21: `git diff --check`, focused
  `DiscoverPresetSubscriptionTest` plus `UserStoryStatusTrackerTest`, and
  `:app:processDebugResources`.

## Plan - 2026-06-28 Durable CI Evidence Wording
- [x] Re-check the latest PR #7 state after the previous evidence refresh.
- [x] Replace self-staling `latest pushed head` wording with durable CI-evidence wording.
- [x] Keep exact source-under-test evidence for the UX packet/readiness preflight at `1a25953d`.
- [x] Run focused tracker/release guard verification and hygiene.

## Review - 2026-06-28 Durable CI Evidence Wording
- PR #7 was green at branch tip `0e5bc25a` before this wording cleanup: Analyze cpp, Analyze java,
  CodeQL, Development build, and Connected Android tests all passed.
- The release-gate handoff now records the `63eaa749` check set as reusable CI evidence rather
  than a live-head invariant. Release decisions must still re-run `gh pr checks 7` at the branch tip.
- This avoids a docs-only evidence refresh loop while preserving the canonical `REL-005` boundary:
  final readiness still needs real release artifact, physical smoke, checked UX signoff, and
  release-grade license-boundary reports.
- Focused guard bundle passed with explicit OpenJDK 21 `JAVA_HOME`: `ReleaseReadinessScriptTest`,
  `UxMatrixScriptTest`, and `UserStoryStatusTrackerTest`.

## Plan - 2026-06-28 REL-005 Current PR CI Ledger Refresh
- [x] Re-check pushed PR #7 CI after the docs-only convergence reconciliation.
- [x] Preserve the `REL-004`/`REL-005` source-under-test distinction: UX packet and readiness
  preflight remain tied to `1a25953d`, while a captured PR CI head is green at `63eaa749`.
- [x] Update the canonical `REL-005` retest ledger and guard test so captured CI evidence is not
  presented as a live-head invariant.
- [x] Run focused tracker/release guard verification and hygiene.

## Review - 2026-06-28 REL-005 Current PR CI Ledger Refresh
- Captured PR #7 pushed head `63eaa749e84a2872d42e82b34e61e61e0d84f789` was green: Analyze cpp
  `1m13s`, Analyze java `4m3s`, CodeQL `5s`, Development build `6m9s`, and Connected Android
  tests `9m16s`.
- This is an evidence-led docs refresh only. It does not change the app source-under-test for the
  refreshed UX matrix packet or readiness preflight, which remain recorded at
  `1a25953daf58d51b56d796e641e34e6cf34bfc65`.
- Focused guard bundle passed with explicit OpenJDK 21 `JAVA_HOME`: `ReleaseReadinessScriptTest`,
  `UxMatrixScriptTest`, and `UserStoryStatusTrackerTest`.

## Plan - 2026-06-28 CTO Convergence Todo Reconciliation
- [x] Re-ground PR #7 head, CI, clean worktree state, and canonical tracker rows.
- [x] Dispatch independent expert lanes for stale todo reconciliation, tracker/open-gate audit, and
  CI/release evidence audit.
- [x] Reconcile stale historical unchecked checklist items only where later benchmark, connected-test,
  or PR evidence supersedes them.
- [x] Keep the true external P0 release board open and avoid marking the overall goal complete.
- [x] Rerun the focused tracker/release guard tests and hygiene after this docs cleanup.

## Review - 2026-06-28 CTO Convergence Todo Reconciliation
- Native tmux/OMX team orchestration was unavailable in this shell (`tmux` missing, no active
  `$TMUX`, and no `omx` command), so the CTO split used Codex native expert subagents plus local
  parallel audits.
- Tracker expert: `tasks/user-story-status.tsv` has `98` stories and exactly `8` partial rows:
  `RUNTIME-007`, `UPDATE-002`, `UPDATE-004`, `REL-001`, `REL-002`, `REL-003`, `REL-004`, and
  `REL-005`. All eight need external hardware, signed/tagged artifacts, legal/provenance review,
  physical-device smoke, human UX signoff, or final release workflow inputs.
- Todo reconciliation expert: old June 13 root-export/materialization checklist items are
  superseded by the stage-backed import/export work and fresh `RUNTIME-010` 5M full
  parse/import/sync/root-write proof; old connected-test and PR-push checklist items are
  superseded by later full connected suite and PR CI evidence.
- CI/release expert: PR #7 head `6239897c9b369141fe29b36a968ad82beb129e8a` is green for
  Development build, Connected Android tests, Analyze cpp, Analyze java, and CodeQL. No local
  code-level release gate remains actionable.
- Historical `REL-004`/`REL-005` notes that cite the earlier `e762f2b4` packet are archival only;
  the current canonical source-under-test packet is `1a25953d` with review packet SHA-256
  `d481b9ab3152760fb917474704131b15c44ae45c4aec615581714e5d9e29eae4`.
- Focused guard suite passed with explicit OpenJDK 21 `JAVA_HOME`:
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; tests were
  `ReleaseReadinessScriptTest`, `UxMatrixScriptTest`, and `UserStoryStatusTrackerTest`.

## Plan - 2026-06-28 REL-004/REL-005 Source-Under-Test Refresh
- [x] Confirm PR #7 is green and the only remaining partial tracker rows are external P0 gates.
- [x] Restart the API 34 emulator and regenerate the UX matrix packet against source commit
  `1a25953daf58d51b56d796e641e34e6cf34bfc65`.
- [x] Run UX signoff and final readiness preflights, expecting fail-closed results until human
  UX signoff, release artifacts, physical smoke, and release-grade license-boundary reports exist.
- [x] Update canonical evidence without marking `REL-004`, `REL-005`, or the overall goal done.

## Review - 2026-06-28 REL-004/REL-005 Source-Under-Test Refresh
- PR #7 was green before this slice at `1a25953d`: Analyze cpp, Analyze java, CodeQL,
  Development build, and Connected Android tests all passed.
- `scripts/run-ux-matrix.ps1` passed all five variants on `adaway-api34-16g` and pulled
  `40` screenshots to `app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d`.
- The refreshed review packet records source commit
  `1a25953daf58d51b56d796e641e34e6cf34bfc65` and SHA-256
  `d481b9ab3152760fb917474704131b15c44ae45c4aec615581714e5d9e29eae4`.
- `verify-ux-signoff.ps1` failed as expected with `Status: failed`, `Checked items: 0`,
  `Unchecked items: 45`, and `Issues: 1`; source commit and review packet source commit matched.
- Spot-checked `font-1.6-rtl` Home/Discover/Sources and `font-1.6` More; no obvious clipping,
  hidden bottom navigation, or unreachable primary action was found. This is still not human
  release signoff.
- `verify-release-readiness.ps1` failed closed with `Issues: 7`: missing release artifact report,
  missing physical smoke report, failed UX signoff, and debug/preflight license-boundary evidence
  that is not acceptable for final release readiness.

## Plan - 2026-06-28 REL-001 Current-Head License Boundary Refresh
- [x] Re-ground the active PR worktree and confirm PR #7 is green at
  `c03030f89ec2337bb9213949cec2e2c3db69f309`.
- [x] Re-run GitTracked, WorkingTree, and GitTracked strict source-archive license-boundary
  reports at the current PR head.
- [x] Rebuild the debug APK/SBOM and re-run strict artifact-boundary checking against the current
  PR head.
- [x] Update canonical REL-001 evidence without closing legal/provenance or signed-release gates.

## Review - 2026-06-28 REL-001 Current-Head License Boundary Refresh
- PR #7 was green before the slice at `c03030f8`: Analyze cpp, Analyze java, CodeQL,
  Connected Android tests, and Development build all passed.
- `scripts/check-license-boundary.ps1 -SourceMode GitTracked` passed with source commit
  `c03030f89ec2337bb9213949cec2e2c3db69f309`, `2492` source entries, and `Issues: 0`.
- `scripts/check-license-boundary.ps1 -SourceMode WorkingTree` passed with source commit
  `c03030f89ec2337bb9213949cec2e2c3db69f309`, `2170` source entries, and `Issues: 0`.
- `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive` passed with
  `2492` source entries, `2199` source archive entries, and `Issues: 0`.
- `:app:assembleDebug :app:cyclonedxBom` passed, then strict artifact-boundary checking passed
  for `app-debug.apk` and `bom.json`: `1119` APK entries, `265` APK resources, `116` SBOM
  components, and `Issues: 0`.
- Debug artifact hashes at this head: APK
  `cc587365535bae924e7a12cd0f3c35b58fb6595320243c6f37b37580b1e26771`, SBOM
  `a75b7111dd87229a6c93541dd51190fb3f0ef1d7e785a290777269c7aa2706d1`, report
  `21027e77758de52edfde32651dfee2a1532c40c46ef1276a1617db2bf7044038`.
- `REL-001` remains partial: MIT/provenance legal clearance, signed release APK/SBOM artifact
  boundary reports, release attestation, and directRelease install/update smoke still require the
  real release inputs.

## CTO Expert Findings - 2026-06-28
- Release-gate expert: the remaining P0 board is external-input gated, not locally code-gated:
  `RUNTIME-007`, `UPDATE-002`, `UPDATE-004`, `REL-001`, `REL-002`, `REL-003`, `REL-004`, and
  `REL-005` still require rooted writable hardware, signed/tagged artifacts, physical-device
  smoke, human UX signoff, or legal/provenance signoff.
- Runtime/performance expert: the old 4M/5M import issue was the full parse/import path missing
  `root_host_entries_stage` population, which forced slow direct root export. Current evidence
  shows fresh 5M parse/import/sync/root-write proof is already recorded under `RUNTIME-010`; no
  further code fix is indicated there.
- Android connected-test expert: package-replaced WorkManager repair, Quick Settings installed
  service contract, DNS packet-to-visible-log bridge, prepared VPN lifecycle, notification
  harnesses, induced Paging failure proof, and local backup transport smoke are already locally
  closed or correctly bounded by manual/OEM consent. No production patch is recommended for those
  named items.
- Tracker/CI expert: the stale local issue was `REL-001` rollup wording and hashes, not the
  license-boundary scripts. This slice refreshes the current-head evidence and keeps final release
  readiness blocked until real release inputs exist.

## Plan - 2026-06-28 CTO Evidence Guardrail Slice
- [x] Resolve the REL-001 "current head" wording caught by the release subagent by recording the
  exact source-baseline commit under test.
- [x] Add a tracker guardrail proving benchmark evidence paths referenced by the spreadsheet exist.
- [x] Add a catalog URL validity sweep so future static catalog edits cannot reintroduce malformed
  or non-HTTPS list URLs.

## Review - 2026-06-28 CTO Evidence Guardrail Slice
- Corrected REL-001 evidence wording from "current head" to source-baseline commit
  `72b64e6f4c848a4667adcab115d432e9dad32376`. The final release gate still requires
  legal/provenance signoff and release artifact APK/SBOM boundary reports from a real release
  attempt.
- Added `UserStoryStatusTrackerTest` coverage for repo-relative `tasks/benchmarks/...` evidence
  paths and the REL-001 exitcode artifacts.
- Added `FilterListCatalogPresetTest` coverage for parseable HTTPS URLs across the catalog,
  defaults, balanced preset, and aggressive preset.

## Plan - 2026-06-28 REL-001 Source-Baseline License Boundary Refresh
- [x] Confirm no signed release APK, SBOM, physical device, or rooted writable target is locally
  available.
- [x] Re-run GitTracked, WorkingTree, and GitTracked strict source-archive license-boundary reports
  at the source-baseline commit before recording evidence.
- [x] Record fresh source-side evidence while keeping legal/provenance and artifact APK/SBOM gates
  open.

## Review - 2026-06-28 REL-001 Source-Baseline License Boundary Refresh
- `scripts/check-license-boundary.ps1 -SourceMode GitTracked` passed with `2474` source entries,
  `Issues: 0`, and source commit `72b64e6f4c848a4667adcab115d432e9dad32376`.
- `scripts/check-license-boundary.ps1 -SourceMode WorkingTree` passed with `2170` source entries
  and `Issues: 0`.
- `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive` passed with
  `2474` source entries, `2180` source archive entries, and `Issues: 0`.
- All reports still say `MIT release status: blocked until GPL-derived material is cleared` and
  have `APK: not-provided`, `SBOM: not-provided`, and `Strict artifacts: false`.
- `REL-001` remains partial: source-baseline checks are fresh, but legal/provenance signoff and
  release artifact APK/SBOM boundary checks still require external inputs.

## Plan - 2026-06-28 REL-004 Fresh UX Matrix Packet
- [x] Re-ground available devices and confirm no physical release device/artifact gate is locally
  closeable.
- [x] Start the local `adaway-api34-16g` emulator and run a fresh UX matrix into a dated output
  directory.
- [x] Hash/count the generated packet and run signoff preflight to prove the human-review boundary.
- [x] Update `REL-004` without marking it covered.

## Review - 2026-06-28 REL-004 Fresh UX Matrix Packet
- `scripts/run-ux-matrix.ps1` passed all five variants on `adaway-api34-16g`: baseline,
  `font-1.3`, `font-1.6`, `font-1.3-rtl`, and `font-1.6-rtl`.
- The run generated `40` screenshots and
  `app/build/reports/ux-matrix-2026-06-28-rel004/ux-matrix-review.md`.
- Review packet SHA-256:
  `a28fa2dc5740c603ae37fc358746a31773cb9d8384927871948de6abf311db19`.
- `verify-ux-signoff.ps1` preflight failed as expected with `Status: failed`, `Checked items: 0`,
  `Unchecked items: 45`, and `Review packet still has unchecked items.`
- Spot-checked worst-case `font-1.6-rtl` Discover/Home/Sources and `font-1.6` More screenshots;
  no obvious clipping, hidden bottom navigation, or unreachable primary action was found. This is
  still not human release signoff.

## Plan - 2026-06-28 Release Gate Handoff Tightening
- [x] Re-ground PR #7 CI and the remaining open P0 release board after `RUNTIME-009` closed.
- [x] Inspect release artifact, physical-smoke, UX signoff, and final readiness workflows/scripts.
- [x] Run the focused script/tracker JVM verifier batch.
- [x] Update canonical tracker rows for `REL-002`, `REL-003`, `REL-004`, and `REL-005` without
  closing external proof gates.

## Review - 2026-06-28 Release Gate Handoff Tightening
- PR #7 was green at `4dd9abc8` before this handoff slice: Analyze cpp/java, CodeQL,
  Development build, and Connected Android tests all passed.
- `pwsh 7.6.3` is available locally, so the script unit tests exercised the real PowerShell
  release gates instead of skipping.
- Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.scripts.ReleaseReadinessScriptTest --tests
  org.adaway.scripts.UxMatrixScriptTest --tests
  org.adaway.tasks.UserStoryStatusTrackerTest --dependency-verification=strict --stacktrace`.
- Added `tasks/benchmarks/2026-06-28-release-gate-handoff-evidence.md` with exact owner inputs for
  physical smoke, UX signoff, and final readiness workflow dispatch.
- Updated `REL-002`, `REL-003`, `REL-004`, and `REL-005` from vague `Not started`/`Not retested`
  wording to concrete verifier-contract evidence and exact external proof still required. These
  rows intentionally remain `Partially covered`; they are not closed until real release artifacts,
  physical-device smoke, human UX signoff, and legal/provenance reports exist.

## Plan - 2026-06-28 RUNTIME-009 Prepared VPN Lifecycle Proof
- [x] Re-ground `RUNTIME-009` from the tracker, existing lifecycle test, and VPN consent evidence.
- [x] Prepare the API 34 emulator into the required state: AdAway VPN consent granted and no active
  VPN tunnel.
- [x] Run the existing full `VpnLifecycleInstrumentedTest` start/stop/resume method without a
  consent skip.
- [x] If full lifecycle passes, update `RUNTIME-009` with connected prepared-device evidence while
  keeping physical-device release smoke separate.
- [x] Run focused JVM/tracker hygiene and connected lifecycle hygiene before pushing.

## Review - 2026-06-28 RUNTIME-009 Prepared VPN Lifecycle Proof
- Prepared `adaway-api34-16g` by manually installing the debug and androidTest APKs, driving the
  Android VPN consent dialog for the current install, verifying `vpn_management` owner UID `10196`
  and `VPN CONNECTED` on `tun0`, then force-stopping AdAway to leave consent granted with no active
  tunnel.
- The first full lifecycle run did not skip and reached stop/tunnel/heartbeat assertions, but
  failed because `VpnService` persists `STOPPED` before the asynchronous `"VPN service stopped."`
  log reached the test's `RecordingTree`.
- Fixed `VpnLifecycleInstrumentedTest` to wait for lifecycle log messages before asserting them.
- Direct instrumentation passed the full prepared-device lifecycle method:
  `adb shell am instrument -w -r -e class
  'org.adaway.vpn.VpnLifecycleInstrumentedTest#startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists'
  org.adaway.test/androidx.test.runner.AndroidJUnitRunner`, result `OK (1 test)`.
- Focused hygiene passed before commit:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.tasks.UserStoryStatusTrackerTest --dependency-verification=strict --stacktrace`
  and `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.VpnLifecycleInstrumentedTest
  --dependency-verification=strict --stacktrace` with 2 connected tests on `adaway-api34-16g`.
- Recorded the boundary in
  `tasks/benchmarks/2026-06-28-runtime009-prepared-vpn-lifecycle-evidence.md`: Gradle connected
  runs can reinstall the app and invalidate the prepared consent UID, so this is a connected
  prepared-device proof; physical-device VPN release smoke remains under `REL-003`.

## Plan - 2026-06-28 CTO Convergence Slice
- [x] Re-ground PR #7 status, CI, and remaining P0/P1 rows before editing.
- [x] Split expert lanes for Android system contracts, runtime/performance/root/VPN gates, and
  release/CI guardrails.
- [x] Promote only locally proven P1 system contracts while preserving external release-smoke
  caveats.
- [x] Add a tracker guardrail test so CI prevents accidental overclaiming of platform/OEM
  boundaries.
- [x] Run focused JVM and connected verification, then commit and push only if the slice is green.

## Review - 2026-06-28 CTO Convergence Slice
- Current PR #7 head `f48f2de7` was green before the slice: Analyze cpp, Analyze java, CodeQL,
  Connected Android tests, and Development build all passed.
- Kept true external P0 release gates open: rooted writable hosts apply, signed directRelease
  install/update smoke, legal/provenance review, tagged release artifact proof, physical-device
  release smoke, human UX review, and final real-artifact readiness.
- Confirmed `RUNTIME-010` is already closed by the fresh 5M full parse/import/sync/root-write
  benchmark. The old 4M/5M issue was the full parse/import path failing to populate
  `root_host_entries_stage`, which forced slow direct root export; the stage-backed path now has
  fresh 5M evidence in `tasks/benchmarks/2026-06-27-runtime010-5m-full-parse-evidence.md`.
- Promoted `SYS-001` from partial to covered connected tile contract because the app-owned service
  metadata, bind permission, action resolution, null tile guard, and conditional shell tile path
  are device-proven; OEM/SystemUI dispatch remains manual release smoke.
- Promoted `SYS-004` from partial to covered connected receiver contract because the receiver-level
  `MY_PACKAGE_REPLACED` path repairs hosts update, app update, and filter-set WorkManager
  schedules from persisted preferences; real signed app-upgrade broadcast delivery remains release
  smoke.
- Focused verification passed on 2026-06-28: `UserStoryStatusTrackerTest` JVM guardrail passed,
  and connected `AdBlockingTileServiceInstrumentedTest` plus
  `UpdateReceiverPackageReplaceInstrumentedTest` passed on `adaway-api34-16g` with the expected
  Quick Settings SystemUI dispatch skip.

## Plan - 2026-06-14 Goal Continuation 60
- [x] Answer the delay question with current benchmark evidence instead of hand-waving.
- [x] Reduce the remaining root-export blocked-row copy cost without changing allow/redirect
  semantics.
- [x] Re-run compile, focused connected DB/migration tests, and 600k/1M performance gates.
- [x] Record measured results and the next bottleneck in this task log.

## Review - 2026-06-14 Goal Continuation 60
- Kept the direct root-export improvement that merges exact/suffix blocked-row export into one
  source scan and one user scan, copies `NULL` for blocked-row redirection, and keeps the v26
  append-style `root_host_entries` table. This preserves allow/redirect semantics and leaves
  focused correctness green.
- Restored the branch to the correctness-verified direct materialized root-export path after
  measuring hybrid/active-cursor experiments. Those experiments were not kept because they either
  missed the 1M sync/root-cursor gates or introduced unresolved state semantics around partial
  `root_host_entries`.
- Compile and focused DB/migration verification passed after rollback:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`, then focused connected tests for
  `migration25To26_rebuildsRootExportForAppendWrites`,
  `testLargeRuntimeSkipStillMaterializesRootExportRows`,
  `testRootExportKeepsMaterializedStateWhenValidOutputIsEmpty`, and
  `testRootHostsCursorReadsActiveTruthAndMatchesListApi`.
- Final clean 600k direct-export benchmark passed:
  `HostEntryDao.root-export-direct perf: indexDropMs=2 clearMs=0 blockedMs=443
  indexCreateMs=1538 allowMs=77 redirectShadowMs=1 redirectedMs=9 dedupeMs=589 finishMs=0
  totalMs=2659`; final line `runtimeRows=0 rootRows=588000
  materializedRuntimeCache=false syncMs=6717 rootCursorMs=4434`.
- Best measured 1M direct-export result improved but remains red:
  before this pass, direct export was `syncMs=251443 rootCursorMs=12212` with
  `blockedMs=220608`; after the kept merge/null/range change the best 1M direct result was
  `syncMs=183617 rootCursorMs=10890` with `blockedMs=168997`. That is a real improvement but not
  the 120s gate.
- Discarded experiments:
  table scan source copy lost at 600k/1M (`syncMs=42309` at 600k and `200773` at 1M);
  large SQLite cache/mmap tuning was either unsupported by Android `execSQL` or slower
  (`189617` at 1M); active cursor made sync cheap but missed the root cursor gate
  (`rootCursorMs=194971` to `208757` at 1M); hybrid exact-materialized/suffix-active got closest
  but still missed, with best observed `syncMs=126953 rootCursorMs=166153` and later
  `syncMs=130890 rootCursorMs=108608`.
- Current conclusion: correctness is green and 600k is fast. The 1M/5M market-leading target needs
  a deeper storage design, likely import-time root-export construction with explicit partial-cache
  state or a separate root-export store, not another small query hint.

## Plan - 2026-06-13 Goal Continuation 59
- [x] Reconcile the compacted status with the actual worktree and latest task log.
- [x] Convert large root export away from stale active-cursor fallback and back to a valid
  materialized root export while still skipping large `host_entries` runtime cache rebuilds.
- [x] Add schema/migration support for append-style `root_host_entries` writes and preserve root
  cursor/list correctness.
- [x] Run compile, focused connected DB/migration tests, 100k, 600k, and 1M allow-heavy benchmarks.
- [x] Superseded by the stage-backed import/export architecture and fresh `RUNTIME-010` 5M full
  parse/import/sync/root-write proof.

## Review - 2026-06-13 Goal Continuation 59
- Answered the delay question with evidence: the long wall time is the Android emulator seeding and
  timing 600k/1M SQLite datasets, not a stuck Gradle process. The 1M seed alone took `372846ms`.
- Added Room schema v26 for an append-friendly `root_host_entries` layout with an `id` rowid
  primary key plus `index_root_host_entries_host` and `index_root_host_entries_reverse_host`.
- Added `MIGRATION_25_26` to rebuild existing v25 `root_host_entries` rows into the append layout
  while preserving host, reverse host, type, kind, and redirection values.
- Changed the large-cache branch in `HostEntryDao.rebuildFromActiveGeneration(...)` so it still
  skips `host_entries` above `500000` active rows, but now rebuilds a valid materialized
  `root_host_entries` export instead of falling back to the slow active Java cursor.
- Reworked direct root export to append active blocked rows, build root export indexes once, apply
  exact/suffix allow deletes, delete rows shadowed by redirects, insert redirects, and run a final
  host-level dedupe phase.
- Compile and focused correctness passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  and focused connected tests for `migration25To26_rebuildsRootExportForAppendWrites`,
  `testLargeRuntimeSkipStillMaterializesRootExportRows`,
  `testRootExportKeepsMaterializedStateWhenValidOutputIsEmpty`, and
  `testRootHostsCursorReadsActiveTruthAndMatchesListApi`.
- Strengthened `testLargeRuntimeSkipStillMaterializesRootExportRows` after the first focused run so
  the large direct-export path now covers duplicate exact/suffix rows and redirect shadowing; the
  same focused connected test set passed again after that assertion expansion.
- 100k benchmark passed on the small materialized runtime path:
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=98000 rootRows=98000
  materializedRuntimeCache=true syncMs=2444 rootCursorMs=373`.
- First 600k append attempt failed narrowly at the 120s sync gate:
  `syncMs=122575 rootCursorMs=5380`; phase evidence showed the grouped/correlated blocked insert
  was the problem: `blockedMs=110145`.
- Removed hot-path grouped/correlated duplicate checks and moved dedupe to one final set-based
  phase. The repeat 600k benchmark passed:
  `HostEntryDao.root-export-direct perf: indexDropMs=77 clearMs=15 blockedMs=76029
  indexCreateMs=3094 allowMs=170 redirectShadowMs=1 redirectedMs=1 dedupeMs=756 finishMs=1
  totalMs=80144`;
  final line `runtimeRows=0 rootRows=588000 materializedRuntimeCache=false syncMs=83997
  rootCursorMs=4601`.
- The 1M gate remains red:
  `HostEntryDao.root-export-direct perf: indexDropMs=42 clearMs=19 blockedMs=220608
  indexCreateMs=6703 allowMs=3773 redirectShadowMs=13 redirectedMs=37 dedupeMs=8716 finishMs=11
  totalMs=239922`;
  final line `runtimeRows=0 rootRows=980000 materializedRuntimeCache=false syncMs=251443
  rootCursorMs=12212`.
- Broader connected DB/migration verification passed after the final test strengthening:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest,org.adaway.db.MigrationTest
  --dependency-verification=strict --stacktrace`; 41 tests, 0 failures.
- `git diff --check` passed with only existing Windows LF-to-CRLF warnings. Generated Room schema
  `app/schemas/org.adaway.db.AppDatabase/26.json` now records `root_host_entries.id` as the primary
  key plus `index_root_host_entries_host` and `index_root_host_entries_reverse_host`.
- Current conclusion: correctness is green and 600k root export is materially better than the
  active-cursor path, but 1M/5M cannot be claimed market-leading yet. The remaining bottleneck is
  the bulk active blocked-row copy into root export storage; the next architecture likely needs
  root output built during import, a different persistent export representation, or a production
  root writer that can stream active truth without Java cursor filtering or SQLite re-copying.

## Plan - 2026-06-13 Goal Continuation 57
- [x] Re-read current root-export implementation and latest benchmark evidence.
- [x] Add the next measured root-export optimization slice using persisted reversed labels on
  `root_host_entries`.
- [x] Run focused correctness tests and current-code 100k benchmark evidence.
- [x] Superseded by the final stage-seeded 1M root-export benchmark evidence.
- [x] Consume all returned expert-agent findings without closing unfinished agents.
- [x] Update this task log with evidence and remaining market-leading gaps.

## Review - 2026-06-13 Goal Continuation 57
- Added Room schema v24 for `root_host_entries.reverse_host`, plus
  `index_root_host_entries_reverse_host` and `MIGRATION_23_24` backfill coverage.
- Reworked the large-cache root-export writer to insert active exact, suffix, and redirected rows
  directly into `root_host_entries`, then run allow-rule deletion against the persisted reversed
  labels. This removes the abandoned candidate-staging table from the active path.
- Focused verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration23To24_addsRootExportReverseHostIndex,org.adaway.db.SourceDbTest#testRootExportKeepsMaterializedStateWhenValidOutputIsEmpty,org.adaway.db.SourceDbTest#testLargeRuntimeSkipStillMaterializesRootExportRows
  --dependency-verification=strict --stacktrace`;
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  --dependency-verification=strict --stacktrace`;
  and `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest,org.adaway.db.MigrationTest
  --dependency-verification=strict --stacktrace` with 39 connected tests and 0 failures.
- Current-code 100k allow-heavy benchmark passed:
  `HostEntryAllowHeavySeedBenchmark blockedRows=100000 exactAllowRules=1000
  suffixAllowRules=500 seedMs=14469 checkpointMs=45`;
  `HostEntryDao.sync perf: allowRules=true clearMs=0 importBlockedMs=156
  suffixIndexCreateMs=217 allowMs=80 importRedirectedMs=25 indexDropMs=8 indexCreateMs=150
  rootExportMs=360 statsMs=54 totalMs=1050 wildcardExactAllowRules=false`;
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=98000 rootRows=98000
  materializedRuntimeCache=true syncMs=1977 rootCursorMs=509`.
- Added benchmark seed-phase logging so long connected runs show whether they are seeding rows,
  committing the transaction, or exercising the root-export path. This explained the earlier
  "stalled before seed" runs: at large scale, the seed transaction can spend minutes in SQLite
  insert and commit work before the benchmark prints its old single seed line.
- Direct-root 600k benchmark passed after adding progress logging:
  `HostEntryAllowHeavySeedBenchmark blockedRows=600000 exactAllowRules=6000
  suffixAllowRules=3000 seedMs=203456 checkpointMs=53`;
  `HostEntryDao.root-export-direct perf: indexDropMs=41 blockedMs=42058 allowMs=885
  redirectedMs=1 indexCreateMs=0 statsMs=0 totalMs=42985`;
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=0 rootRows=588000
  materializedRuntimeCache=false syncMs=45647 rootCursorMs=5063`.
- Direct-root 1M benchmark failed the `120000ms` sync budget:
  `seedMs=411974`, root export `totalMs=282000` with `blockedMs=273101`, and final
  `syncMs=312065 rootCursorMs=13537 rootRows=980000`. The bottleneck is the bulk active-row
  insert into `root_host_entries`, not suffix allow deletion.
- Added Room schema v25 plus `MIGRATION_24_25` to rebuild `root_host_entries` as
  `WITHOUT ROWID`, and centralized creation-time storage optimization in
  `AppDatabase.optimizeCreatedDatabaseStorage(...)` so production and direct connected-test
  builders use the same table layout.
- Focused verification for v25 passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration24To25_rebuildsRootExportWithoutRowid,org.adaway.db.SourceDbTest#testLargeRuntimeSkipStillMaterializesRootExportRows,org.adaway.db.SourceDbTest#testRootExportKeepsMaterializedStateWhenValidOutputIsEmpty
  --dependency-verification=strict --stacktrace`.
- Aligned v25 1M benchmark still failed the `120000ms` sync budget:
  `HostEntryAllowHeavySeedBenchmark blockedRows=1000000 exactAllowRules=10000
  suffixAllowRules=5000 seedMs=464342 checkpointMs=60`;
  `HostEntryDao.root-export-direct perf: indexDropMs=21 blockedMs=237653 allowMs=5150
  redirectedMs=30 indexCreateMs=0 statsMs=20 totalMs=242874`;
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=0 rootRows=980000
  materializedRuntimeCache=false syncMs=258930 rootCursorMs=16982`.
- All outstanding expert agents were allowed to finish and were not closed early. Their findings
  add these remaining P0/P1 gaps: release CI still needs signed update-manifest publication, PR CI
  needs Android/security/license gates, MIT remains legally blocked by GPL-derived code/assets,
  FilterLists.com subscriptions need durable provenance/compatibility metadata and recoverable
  per-list import outcomes, and the long-term runtime architecture should consider direct active
  truth/resolver paths instead of rebuilding multi-million-row caches.
- Remaining gap: the overall market-leading goal is still open. Correctness is green for this
  slice and 600k root export is green, but 1M root-export scale is still red and needs an
  algorithmic export change rather than another storage-only tweak.

## Plan - 2026-06-13 Goal Continuation 56
- [x] Add red connected tests for root-export cache state: valid empty materialized exports must
  not fall back to active raw queries.
- [x] Add a schema-backed root-export materialized flag with migration/schema coverage.
- [x] Direct-materialize `root_host_entries` from active runtime truth when the large runtime cache
  skips `host_entries`.
- [x] Update 1M allow-heavy benchmark expectations so root cursor/apply is measured on the large
  cache-skip path.
- [x] Superseded by later focused verification, connected DB/migration coverage, and final 1M
  benchmark evidence.

## Review - 2026-06-13 Goal Continuation 56
- Added connected coverage proving a valid empty `root_host_entries` export stays materialized and
  does not fall back to active raw queries, plus large-runtime-skip coverage proving
  `host_entries` can be skipped while `root_host_entries` is still rebuilt.
- Added `hosts_stats.root_export_materialized`, Room schema v23, `MIGRATION_22_23`, and migration
  coverage so an empty-but-valid root export is distinguishable from an invalid cache.
- Removed the duplicate large-cache early return from `SourceModel`; runtime refresh now delegates
  the large/small materialization decision to `HostEntryDao.rebuildFromActiveGeneration(db)`.
- Replaced the first direct active CTE root-export writer after 1M evidence showed it did not
  complete inside the 120s target after a `seedMs=341835` setup.
- Rebuilt the large-skip root export through indexed active rows and a temporary unkeyed staging
  table. Correctness is green, but the 1M performance gate is not green yet.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  --dependency-verification=strict --stacktrace`;
  and `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest,org.adaway.db.MigrationTest
  --dependency-verification=strict --stacktrace` with 38 connected tests and 0 failures.
- 100k allow-heavy benchmark passed before the later large-skip writer revisions:
  `seedMs=7991`, `syncMs=1338`, `rootCursorMs=398`, `runtimeRows=98000`,
  `rootRows=98000`.
- Best current large-skip evidence is the 600k unkeyed staging run:
  `seedMs=150064`, root direct `tableMs=1 blockedMs=70115 allowMs=1816 copyMs=1637
  redirectedMs=11 cleanupMs=26 totalMs=73606`, final `syncMs=74908`,
  `rootCursorMs=4627`, `rootRows=588000`.
- Negative performance evidence: the direct CTE path failed to finish usefully at 1M; the keyed
  split writer failed the 1M 120s gate at `syncMs=153018`; CTAS staging regressed 600k to
  `syncMs=174347`; and the final 1M unkeyed attempt was stopped after more than 10 minutes without
  a seed metric from the emulator/instrumentation runner.
- Remaining gap: the 1M allow-heavy large-skip gate is still open; more root-export architecture
  work is needed before this continuation can be called performance-complete.

## Plan - 2026-06-13 Goal Continuation 55
- [x] Add a red connected test proving suffix allow deletion uses a label-reversed indexed key
  instead of broad Java/runtime-row scans.
- [x] Add `reverse_host` storage, indexes, migration, and Room schema updates for
  `hosts_lists` and `host_entries`.
- [x] Replace transaction-time suffix allow Java scans with indexed SQL range deletes.
- [x] Preserve runtime/root/domain-checker semantics for exact, suffix, wildcard, active
  generation, and user rules.
- [x] Run focused connected tests, compile, scale benchmark, and hygiene checks.

## Review - 2026-06-13 Goal Continuation 55
- Added `Hostnames.reverseLabels(...)` and persisted `reverse_host` on `hosts_lists` and
  `host_entries`, including Room schema v22, migration backfill, source-generation copying, and
  SQL update-deduper staging/flush support.
- Replaced the suffix-allow runtime deletion Java scan with a temp-table SQL path that joins
  `host_entries` through `index_host_entries_kind_reverse_host` and matches both exact reversed
  suffix keys and bounded child ranges.
- Added active-generation covering indexes for source import and reverse-key allow matching.
  `SourceDbTest#testBlockedRuntimeImportStreamsExistingKindHostIndex` now guards that exact and
  suffix source imports use `index_hosts_lists_active_generation_kind_host` without temp b-trees.
- Kept old no-reverse `host_entries` migrations `WITHOUT ROWID`, but made the v22 reverse-host
  runtime table rowid-backed because the 1M insert experiments showed reverse-host
  `WITHOUT ROWID` was a bottleneck.
- Added a large-cache boundary in `HostEntryDao.rebuildFromActiveGeneration(...)`: when active
  rule metadata exceeds `500000` rows, `host_entries` and `root_host_entries` are cleared and the
  app uses direct active-truth runtime/root queries instead of materializing a multi-million-row
  runtime cache.
- Verification caught a real regression: the fast stats path trusted zeroed source metadata in
  direct DB tests, leaving `hosts_stats` at zero after materialized rebuilds. Fixed by separating
  raw active-row stats from final materialized runtime stats and falling back to raw active rows
  only when metadata says zero but active rows exist.
- Focused verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest,org.adaway.db.MigrationTest
  --dependency-verification=strict --stacktrace`.
- 100k allow-heavy runtime benchmark passed after the stats fix:
  `HostEntryAllowHeavySeedBenchmark blockedRows=100000 exactAllowRules=1000
  suffixAllowRules=500 seedMs=7613 checkpointMs=48`;
  `HostEntryDao.sync perf: allowRules=true clearMs=0 importBlockedMs=105
  suffixIndexCreateMs=139 allowMs=38 importRedirectedMs=26 indexDropMs=7 indexCreateMs=85
  rootExportMs=113 statsMs=32 totalMs=545 wildcardExactAllowRules=false`;
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=98000 rootRows=98000
  materializedRuntimeCache=true syncMs=1139 rootCursorMs=305`.
- 1M allow-heavy runtime benchmark passed after the stats fix:
  `HostEntryAllowHeavySeedBenchmark blockedRows=1000000 exactAllowRules=10000
  suffixAllowRules=5000 seedMs=297707 checkpointMs=58`;
  `HostEntryDao.sync skipped materialized runtime cache: activeRuleRows=1015000
  maxRows=500000 totalMs=164`;
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=0 rootRows=0
  materializedRuntimeCache=false syncMs=176 rootCursorMs=0`.
- Broad Gradle verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`;
  and `git diff --check` with only Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: 5M post-slice runtime/root apply is not rerun in this continuation;
  direct huge root-host export still needs a dedicated materialized/root-apply path if root mode
  must handle more than `500000` rows without falling back to active queries; empty
  `root_host_entries` still cannot distinguish "valid empty export" from "not materialized";
  FilterLists health/provenance UI is not finished; and MIT remains legally blocked until
  GPL-derived code/assets are permission-cleared, removed, or rewritten.

## Plan - 2026-06-13 Goal Continuation 54
- [x] Inspect APK self-install permission and store-boundary behavior.
- [x] Add Android 8+ install-package permission handling for direct APK self-update.
- [x] Block APK self-update for non-AdAway-signed stores.
- [x] Add release docs and security tests for the self-update boundary.
- [x] Run focused compile/test/manifest hygiene.

## Review - 2026-06-13 Goal Continuation 54
- Added `android.permission.REQUEST_INSTALL_PACKAGES` for the direct APK updater and documented
  that the path is only for AdAway-signed direct APK distribution.
- Updated `ApkDownloadReceiver` to check `canRequestPackageInstalls()` on Android 8+ and route
  users to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` before launching `ACTION_INSTALL_PACKAGE`.
- Updated `UpdateModel` so manifest checks and APK downloads are refused unless `getStore()` is
  `ADAWAY`; F-Droid and unknown-store installs rely on their store updater instead.
- Extended `SecurityHardeningTest` to guard the permission, runtime install-source check,
  installer-settings route, and AdAway-store self-update boundary.
- Focused verification passed:
  `:app:compileDebugJavaWithJavac :app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`.
- Final debug build/lint verification passed:
  `:app:assembleDebug :app:lintDebug --dependency-verification=strict --stacktrace`.
- License-boundary verification passed:
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`.
- Scoped diff hygiene passed with only expected Windows LF-to-CRLF warnings.

## Plan - 2026-06-13 Goal Continuation 52
- [x] Spawn independent expert reviewers for FilterLists quality, suffix performance, and
  release/MIT boundaries without blocking local implementation.
- [x] Implement a bounded FilterLists provenance slice: persist list id, syntax metadata,
  compatibility label/score, and selected URL on subscribed sources.
- [x] Fix reviewer-identified single-subscribe provenance loss and empty negative URL cache handling.
- [x] Add Room v21 migration coverage for new FilterLists source metadata.
- [x] Add unit and connected tests for FilterLists subscription provenance.
- [x] Run focused compile/unit/connected verification and schema hygiene.
- [x] Consume returned expert-agent findings into the backlog/review notes.

## Review - 2026-06-13 Goal Continuation 52
- FilterLists provenance reviewer found that bulk subscription now persisted metadata, but direct
  single-list subscribe and add-source prefill still lost it; it also found worker negative-cache
  empty URLs could bypass direct-subscribe refetch/null handling.
- Added `FilterListsSourceMetadata` and wired bulk subscribe, direct subscribe, and add-source
  prefill/editor preservation through the same metadata path.
- Added additive Room v21 fields on `hosts_sources`: `filter_list_id`,
  `filter_list_syntax_ids`, `filter_list_compatibility`,
  `filter_list_compatibility_score`, and `filter_list_selected_url`.
- Normalized empty cached FilterLists URLs to `null` before subscription logic so negative cache
  entries cannot become empty source URLs.
- Release/security reviewer confirmed the app-side self-update verifier exists, but release still
  lacks signed manifest publication; APK self-install needs a non-Play permission/flavor decision;
  release cleanup weakens durable provenance; MIT remains blocked by live GPL-derived code.
- Performance reviewer confirmed the remaining 5M allow-heavy runtime rebuild gap needs an
  indexable label-reversed suffix key on `hosts_lists`/`host_entries`, not more batching.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  :app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --tests org.adaway.model.source.FilterListsSourceMetadataTest
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration20To21_addsFilterListsProvenanceColumns,org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest#subscribeAllRecorder_writesCompatibleSourcesAndReturnsExactCounts
  --dependency-verification=strict --stacktrace`;
  `:app:assembleDebug :app:lintDebug --dependency-verification=strict --stacktrace`;
  and scoped `git diff --check` with CRLF warnings only.

## Plan - 2026-06-13 Goal Continuation 53
- [x] Inspect current update manifest contract, release workflow, and release/security tests.
- [x] Add a bounded signed-update-manifest generation/publication path.
- [x] Add tests/docs covering manifest artifact publication and verification commands.
- [x] Run focused release/security verification.

## Review - 2026-06-13 Goal Continuation 53
- Added `scripts/generate-update-manifest.sh` to build the signed manifest envelope required by
  `Manifest.java`: compact payload JSON, APK SHA-256, signer certificate SHA-256, HTTPS APK URL,
  channel/store, 14-day maximum expiry, RSA/SHA-256 signature, optional public-key verification,
  and `manifest.json.sha256`.
- Updated `.github/workflows/fork-release-apk.yml` to derive version/cert outputs from the
  verified APK, require `UPDATE_MANIFEST_PRIVATE_KEY_BASE64`, generate `manifest.json`, upload
  manifest/checksum as release assets, and include them in provenance attestation.
- Removed automatic older-release deletion from the tagged release workflow so release APK/SBOM/
  manifest/checksum artifacts remain available for durable provenance.
- Updated `RELEASING.md` and `README.md` with the private manifest signing secret, manifest
  artifacts, local generation command, checksum checks, and `gh attestation verify` commands.
- Extended `SecurityHardeningTest` to guard manifest generation/upload/attestation, signing
  script behavior, manifest checksum generation, retained releases, and release verification docs.
- Verification passed:
  Git Bash smoke test for `scripts/generate-update-manifest.sh` with a temporary RSA key and
  dummy APK; `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest --tests
  org.adaway.model.update.ManifestTest --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`; and scoped
  `git diff --check` with CRLF warnings only.

## Plan - 2026-06-13 Goal Continuation 51
- [x] Let the 5M stats benchmark finish and record the real failure instead of stopping early.
- [x] Consume the remaining read-only expert agents for runtime truth, FilterLists UX, and
  release/provenance gaps.
- [x] Replace post-hoc active-row stats scans with metadata-backed per-source counters.
- [x] Add Room v20 migration/schema coverage for source stats metadata.
- [x] Add focused connected tests for migration, metadata-backed active stats, and 5M logical
  stats-refresh performance.
- [x] Run final focused unit/compile/diff hygiene for this slice.

## Review - 2026-06-13 Goal Continuation 51
- The 5M row-backed stats benchmark was allowed to finish. It failed the 300s budget with:
  `HostEntryAllowHeavyStatsRefreshBenchmark blockedRows=5000000 exactBlockedRows=2500000
  suffixBlockedRows=2500000 exactAllowRules=50000 suffixAllowRules=25000 activeRows=5075000
  seedMs=1342015 checkpointMs=138 statsMs=697442`.
- Conclusion: a better SQL scan is still the wrong architecture for dashboard/runtime counters at
  market-leading scale. `hosts_stats` now refreshes from per-source metadata in `hosts_sources`,
  not from active `hosts_lists` rows.
- Added source metadata columns in Room schema v20: `active_rule_count`, `blocked_count`,
  `blocked_exact_count`, `allowed_count`, and `redirected_count`.
- Added `MIGRATION_19_20` to add/backfill source metadata and repopulate `hosts_stats` from
  metadata. The old v20 schema generated by the failed covering-index experiment was removed and
  regenerated with the metadata schema.
- Updated source finalization and manual list edit paths so source stats are refreshed when
  source generations are committed or individual rows are added, edited, disabled, moved, or
  removed.
- Updated `HostEntryDao.refreshStatsFromActiveGeneration()` to refresh user-source metadata
  directly and then aggregate `hosts_stats` from enabled `hosts_sources`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration18To19_createsRuntimeStatsTable,org.adaway.db.MigrationTest#migration19To20_backfillsSourceStatsMetadata,org.adaway.db.SourceDbTest#testBlockedStatsReadActiveTruthWithoutMaterializedRuntimeRows,org.adaway.model.source.SourceLoaderPerformanceTest#refreshRuntimeStats_allowHeavyRequestedRows_recordsBenchmark
  --dependency-verification=strict --stacktrace`.
- 5M logical metadata-backed stats gate passed:
  `HostEntryAllowHeavyStatsRefreshBenchmark blockedRows=5000000 exactBlockedRows=2500000
  suffixBlockedRows=2500000 exactAllowRules=0 suffixAllowRules=0 activeRows=5000000
  seedRows=false seedMs=3 checkpointMs=0 statsMs=12`.
- Final hygiene passed:
  `:app:testDebugUnitTest --dependency-verification=strict --stacktrace`,
  `:app:assembleDebug :app:lintDebug --dependency-verification=strict --stacktrace`, and
  `git diff --check` with line-ending warnings only.
- Remaining expert-agent gaps consumed into backlog:
  runtime architecture should continue moving toward direct active-truth reads; FilterLists imports
  still need durable provenance/compatibility/outcome metadata; release CI still needs signed
  update-manifest generation/publication; MIT remains blocked by GPL-derived app/VPN code and
  assets.

## Plan - 2026-06-13 Goal Continuation 50
- [x] Audit current release/provenance, Home terminal-state, and allow-heavy benchmark gaps from
  the task ledger.
- [x] Spawn read-only expert reviewers for release provenance, Home operation-state UX, and
  allow-heavy performance gates.
- [x] Patch release provenance/documentation issues that were concrete and low-risk.
- [x] Patch Home terminal-state controls/counters/accessibility regressions found by review.
- [x] Run the explicit 1M allow-heavy benchmark with budgets.
- [x] Attempt the explicit 5M allow-heavy benchmark, investigate the failure, and replace the
  native-crashing suffix-allow SQL with a bounded batched implementation.
- [x] Run focused, broad Gradle, license-boundary, and diff-hygiene verification.

## Review - 2026-06-13 Goal Continuation 50
- Spawned and consumed three read-only expert reviewers:
  - Release/provenance reviewer found missing `artifact-metadata: write`, release checksum files
    using CI-internal paths instead of asset basenames, local docs that did not reproduce
    artifact-aware boundary gates, and untracked dependency-verification files that still need to
    be staged with the release/provenance patch.
  - Home UX/state reviewer found Home still uses legacy `MultiPhaseProgress`, complete terminal
    progress could leave pause/stop controls enabled, stopped progress could keep the blocked
    counter frozen, and terminal announcements could be missed by accessibility services.
  - Performance reviewer confirmed the allow-heavy gate is only meaningful with explicit
    `adawayAllowRebuild*` arguments and budgets, and that the DAO SQL shape was the remaining
    risk surface for 5M suffix-allow scale.
- Updated `.github/workflows/fork-release-apk.yml` to grant `artifact-metadata: write` for
  attestation storage records and to write APK/SBOM `.sha256` files using release asset basenames
  rather than CI-internal `app/build/...` paths.
- Updated `RELEASING.md` so local release verification uses the PowerShell boundary checker as the
  primary path, includes basename checksum generation, and includes the artifact-aware
  `-ApkPath`, `-SbomPath`, and `-StrictArtifacts` gate. The Bash wrapper remains documented only
  as a Unix convenience when PowerShell is available.
- Extended `SecurityHardeningTest` to guard artifact metadata permission, basename checksums, and
  the stronger local release-boundary documentation.
- Patched Home update terminal behavior: pause/resume/stop requests are ignored after stop,
  finalizing, complete, or non-running states; Home disables pause/stop during `COMPLETE`; stopped
  terminal progress resets import counter guards and refreshes counters; complete/stopped text is
  announced explicitly for accessibility.
- Added source-level tests for terminal update controls/counter restoration and `SourceModel`
  terminal control guards.
- Ran the explicit 1M allow-heavy benchmark after the release/Home patches. It passed with:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=1000000 exactBlockedRows=500000
  suffixBlockedRows=500000 exactAllowRules=1000 suffixAllowRules=1000 runtimeRows=997000
  rootRows=997000 syncMs=23656 rootCursorMs=6691`.
- Ran the explicit 5M allow-heavy benchmark with `exact/suffix=5000/5000` and budgets
  `sync=300000ms`, `rootCursor=120000ms`. The first attempt failed by native SQLite crash:
  `Fatal signal 6 (SIGABRT)` in `libsqlite.so` while executing
  `HostEntryDao_Impl.deleteExactRowsAllowedByActiveSuffixRules`; stack showed
  `sqlite3MemMalloc`, `pcache1Alloc`, and `SQLiteConnection.executeForChangedRowCount`.
- Replaced the memory-heavy all-rows suffix-allow recursive CTE with bounded host-range batches
  (`SUFFIX_ALLOW_DELETE_BATCH_SIZE = 50000`) for both exact and suffix runtime rows. Added a
  source-level guard and connected regression proving nested suffix allow rules still remove exact
  and suffix blocked rows.
- Re-ran 1M after the batched suffix-allow rewrite. It passed under the same budgets with:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=1000000 exactBlockedRows=500000
  suffixBlockedRows=500000 exactAllowRules=1000 suffixAllowRules=1000 runtimeRows=997000
  rootRows=997000 syncMs=24714 rootCursorMs=6371`.
- Re-ran 5M after the batched suffix-allow rewrite. It no longer crashed, but still failed the
  300s sync budget: `adawayAllowRebuildSyncBudgetMs exceeded 300000ms: 737801ms`.
  This remains a hard performance gap; the next fix likely needs an indexed suffix-match design
  such as reversed-host materialization or another suffix lookup structure, not just batching.
- Focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testSuffixAllowRulesRemoveNestedExactAndSuffixRuntimeRows
  --dependency-verification=strict --stacktrace`.
- Focused release/Home verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`.
- Broad Gradle verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene verification passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`;
  and `git diff --check` with only existing LF-to-CRLF warnings.
- Remaining full-goal gaps: 5M allow-heavy suffix-allow rebuild still misses the market-leading
  performance bar; Home still should consume `FilterOperationState` as the single operation truth;
  dependency-verification files are still untracked and must be staged with release/provenance
  changes; release signing/SBOM/artifact gates still need real release-secret execution; and MIT
  remains blocked until GPL-derived app/VPN code and assets are removed, rewritten, or
  permission-cleared.

## Plan - 2026-06-12 Goal Continuation 49
- [x] Keep the progress/cancellation/release reviewers open until they return, then close them
  after their findings are consumed.
- [x] Make filter update terminal lifecycle explicit: finalizing, complete, stopped, then delayed
  idle.
- [x] Update Home progress copy, controls, counters, and live-region announcements around
  finalizing/stopped states.
- [x] Make FilterLists bulk-cancel state durable and avoid reporting cancellation before the
  worker has stopped and flushed queued rows.
- [x] Run focused JVM, connected Android, broad Gradle, license-boundary, and diff-hygiene
  verification.

## Review - 2026-06-12 Goal Continuation 49
- Spawned and consumed three focused reviewers:
  - Progress lifecycle reviewer found stopped updates could remain visually active forever,
    complete was not a reliable terminal-after-finalize state, and the stopped lifecycle source
    test was stale.
  - Discover cancellation reviewer found cancelled status could disappear after WorkManager
    reported `CANCELLED`, the UI announced cancellation before worker shutdown, and cancelled
    summaries could count queued rows not flushed to storage.
  - Release/licensing reviewer confirmed the current patch still is not a full release-provenance
    change and MIT remains blocked by GPL-derived packaged app/VPN code.
- Added `FilterOperationState.Phase.FINALIZE` and `STOPPED`, plus explicit complete/stopped
  mapping from `MultiPhaseProgress`.
- Added terminal progress publication in `SourceModel`: full updates now post `FINALIZE` while
  activation/runtime rebuild happens, post explicit `COMPLETE` after successful finalization, post
  explicit `STOPPED` after stop cleanup, and then return to idle with a token-guarded delayed idle
  transition.
- Updated Home progress UX so finalizing, complete, and stopped have distinct copy; pause/stop
  controls are disabled during terminal states; stopped updates reattach counter observers; and
  status/progress text uses polite accessibility live regions.
- Made Discover FilterLists bulk cancellation durable with a persisted stopping flag, disabled the
  cancel button while stop is in flight, and taught terminal WorkManager `CANCELLED` output to show
  a cancelled summary even when the worker has no result counts.
- Updated `FilterListsSubscribeAllWorker` cancellation to poll completion futures instead of
  blocking on `take()`, flush queued recorder rows before returning cancelled output, cancel the
  notification, and share the shutdown path through `finishCancelled`.
- Added/updated focused source-level tests covering finalizing/stopped/complete progress mapping,
  stopped update lifecycle source guards, durable Discover cancellation state, and worker
  poll-and-flush cancellation behavior.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.FilterOperationStateTest --tests
  org.adaway.model.source.Generation304MigrationTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest --dependency-verification=strict
  --stacktrace`.
- Connected regression verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.model.source.SourceModelHttpConditionalTest,org.adaway.db.SourceDbTest#testRuntimeSyncUsesSetBasedRulesAtScale
  --dependency-verification=strict --stacktrace`. The run finished 10 tests with 0 failures.
- Broad Gradle verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene verification passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`;
  and `git diff --check` with only existing LF-to-CRLF warnings.
- Remaining full-goal gaps: MIT is still not legally available for the distributed app until
  GPL-derived app/VPN code and assets are removed, rewritten, or permission-cleared; release
  provenance still needs an atomic staged patch for CI/docs/SBOM/dependency-verification decisions;
  the shell license checker needs either executable bit alignment or documented `bash` invocation;
  artifact metadata permission needs a release-policy decision; the 1M/5M allow-heavy benchmarks
  still need reruns after the final SQL patch; and Home should eventually consume
  `FilterOperationState` directly instead of legacy `MultiPhaseProgress`.

## Plan - 2026-06-12 Goal Continuation 48
- [x] Spawn focused reviewers for allow-heavy SQL performance, progress/counter UX semantics, and
  release/MIT provenance blockers.
- [x] Add an allow-heavy runtime rebuild benchmark that runs as a small default connected gate and
  scales by instrumentation arguments for 100k/1M rows.
- [x] Optimize active allow-rule rebuilds with v16 allow-probe indexes, indexed exact/suffix allow
  deletes, transaction-backed index deferral, and cheaper root suffix materialization.
- [x] Fix Home progress copy so accepted parser rows are not displayed as final blocked-host truth.
- [x] Run focused connected, broad Gradle, license-boundary, and diff-hygiene verification.

## Review - 2026-06-12 Goal Continuation 48
- Spawned and consumed three read-only expert reviewers:
  - SQL/performance confirmed allow-heavy rebuild was still effectively
    `host_entries x active_allow_rules`, missing active allow-rule covering indexes.
  - UX/product confirmed Home presented parser accepted rows as final blocked-host protection truth.
  - Release/licensing confirmed MIT remains blocked by packaged GPL-derived app/VPN code and that
    the staged release-boundary patch is not enough by itself for an atomic release provenance
    change.
- Added `rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark` to
  `SourceLoaderPerformanceTest`. It now runs a 1,000-row allow-heavy fixture by default and accepts
  `adawayAllowRebuildBlockedRows`, `adawayAllowRebuildExactRules`,
  `adawayAllowRebuildSuffixRules`, `adawayAllowRebuildSyncBudgetMs`, and
  `adawayAllowRebuildRootCursorBudgetMs` for requested scale/budget runs.
- Baseline evidence before the allow-heavy SQL patch:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=100000 exactAllowRules=100
  suffixAllowRules=100 runtimeRows=99700 rootRows=99700 syncMs=160683 rootCursorMs=798`.
- Added Room schema version 16 with two active allow-rule probe indexes:
  `index_hosts_lists_active_allow_source_kind_host` on
  `(type, enabled, kind, source_id, host)` and
  `index_hosts_lists_active_allow_generation_source_kind_host` on
  `(type, enabled, kind, generation, source_id, host)`.
- Split allow filtering so literal exact allow rules use indexed equality deletes, wildcard exact
  allow rules retain the conservative LIKE path, suffix allow rules use generated suffix candidates
  joined by equality, and transaction-backed rebuilds drop/recreate the root export covering index
  instead of maintaining it during large imports.
- Simplified the non-wildcard root suffix materialization path: suffix allow effects are already
  applied to `host_entries`, so root export only needs to suppress suffix-base rows with exact
  literal allow rules.
- Final 100k allow-heavy verification after the indexed rebuild/root simplification passed:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=100000 exactAllowRules=100
  suffixAllowRules=100 runtimeRows=99700 rootRows=99700 syncMs=5662 rootCursorMs=996`.
  The DAO phase log for the same run showed `importBlockedMs=648`, `allowMs=3390`,
  `indexCreateMs=386`, and `rootExportMs=416`.
- Exploratory 1M allow-heavy run before the final index-deferral/root-materialization tweak passed
  but exposed the live-index insert bottleneck:
  `blockedRows=1000000 exactAllowRules=1000 suffixAllowRules=1000 runtimeRows=997000
  rootRows=997000 syncMs=222885 rootCursorMs=14087`, with
  `importBlockedMs=116975`, `allowMs=40256`, and `rootExportMs=29523`.
- Fixed Home progress semantics: `HomeFragment` no longer writes `progress.parsedHostCount` into
  the Blocked card, and the progress copy now says `accepted rules` instead of `blocked`. The final
  Blocked counter remains driven by the runtime DB observer after rebuild.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration15To16_addsActiveAllowProbeIndexes,org.adaway.db.SourceDbTest#testAllowRulesOverrideSuffixBlocksAtRuntime,org.adaway.db.SourceDbTest#testRuntimeSyncUsesSetBasedRulesAtScale,org.adaway.model.source.SourceLoaderPerformanceTest#rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark -Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildBlockedRows=100000 -Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildExactRules=100 -Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSuffixRules=100 --dependency-verification=strict --stacktrace`.
- Broader connected runtime/migration verification passed after the final SQL tweak:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest,org.adaway.db.SourceDbTest,org.adaway.db.UserListTest,org.adaway.model.source.SourceLoaderPerformanceTest -Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildBlockedRows=1000 --dependency-verification=strict --stacktrace`.
  The run finished 33 tests with 3 optional scale tests skipped and 0 failures.
- Broad Gradle verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene verification passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`;
  and `git diff --check` with only existing LF-to-CRLF warnings.
- Remaining full-goal gaps: rerun 1M allow-heavy after the final index-deferral/root export tweak
  and add a 5M allow-heavy gate; complete the progress finalizing/cancelled-state UX work; make the
  release provenance patch atomic by staging CI/docs/dependency-verification/resource-reference
  changes together; and keep the app GPLv3+ until packaged GPL-derived code/assets are removed,
  rewritten, or permission-cleared for a real MIT edition.

## Plan - 2026-06-12 Goal Continuation 47
- [x] Resolve the git-tracked release-boundary blocker without staging unrelated work.
- [x] Spawn read-only expert explorers for the next UX, performance, and release-note gaps.
- [x] Replace unscanned auto-generated GitHub release notes with checked release-boundary copy.
- [x] Add tests guarding the release-note boundary.
- [x] Run focused and broad verification, then record remaining full-goal gaps.

## Review - 2026-06-12 Goal Continuation 47
- Staged only the release-boundary source files needed to make the git-tracked source inventory
  honest: `.gitattributes`, `scripts/check-license-boundary.ps1`,
  `scripts/check-license-boundary.sh`, and the seven forbidden tracked deletions under
  `app/src/main/assets` and `tcpdump/jni/libpcap/SUNOS4`.
- `scripts/check-license-boundary.ps1 -SourceMode GitTracked` and
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive` now pass.
- Disabled unscanned generated GitHub release notes in `fork-release-apk.yml` and replaced them
  with fixed GPL boundary wording plus source-archive clarification.
- Removed the manual `delete_tags` input from cleanup releases and hard-coded `delete_tags: false`
  so release tags remain addressable for source archive provenance.
- Updated `RELEASING.md` to say generated release notes are disabled and to include the strict
  source boundary check in the local release checklist.
- Implemented the UX reviewer's P1 patch: the FilterLists bulk action is no longer a destructive
  switch. It is now explicit add/remove buttons, with status in a polite live region and centralized
  enablement based on loading/running state, visible compatible list count, and visible
  subscription state.
- Implemented the performance reviewer's low-risk scratch-table patch: `update_pending_hosts` is
  now `PRIMARY KEY(source_id,type,kind,host,redirection_is_null,redirection_value) WITHOUT ROWID`
  with `INSERT OR IGNORE`, so flush no longer performs the two pending-table `GROUP BY` passes.
- Focused verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  :app:testDebugUnitTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --tests org.adaway.security.SecurityHardeningTest :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_preservesSourceOwnershipAndKeepsRedirectTargets,org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_carryForwardSkipsAlreadySeenRowsAndMarksCopiedRows --dependency-verification=strict --stacktrace`.
- Broad Gradle verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License/hygiene verification passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`;
  and `git diff --check` with only existing LF-to-CRLF warnings.
- Remaining full-goal gaps: the app is still GPLv3+ until GPL-derived code/assets are removed,
  rewritten, or permission-cleared; 1M/5M carry-forward and allow-heavy runtime rebuild benchmarks
  still need honest large-scale coverage; and progress copy still needs separation between
  accepted/staged rows and final blocked-host counts.

## Plan - 2026-06-12 Goal Continuation 46
- [x] Spawn read-only expert explorers for the next independent gaps: FilterLists UX/scope,
  set-based import performance risk, and release/MIT boundary hardening.
- [x] Fix FilterLists bulk subscription so "Subscribe to all" applies to the visible filtered
  scope instead of silently subscribing the entire directory under search/tag/language filters.
- [x] Make subscribe-all switch state, confirmation counts, and worker progress reflect the same
  scope.
- [x] Add focused tests for scoped worker filtering and fragment wiring.
- [x] Integrate explorer findings that are actionable without conflicting with this slice.
- [x] Run targeted unit/connected tests plus the broad Gradle/license/diff gates and record
  evidence.

## Review - 2026-06-12 Goal Continuation 46
- Spawned and consumed three read-only expert explorers for FilterLists UX/scope,
  set-based import performance, and release/security/licensing.
- Fixed the FilterLists.com bulk action scope bug: filtered search/tag/language/DNS-safe views now
  pass exact visible list IDs to `FilterListsSubscribeAllWorker`, confirmation counts use
  `filtered`, switch state resolves against `filtered`, and unsubscribe-all removes only visible
  scoped sources when filters are active.
- Added filtered-scope copy for subscribe/unsubscribe confirmations and blocked pointless
  subscribe-all runs when the current view has zero DNS-safe lists.
- Added worker filtering helpers and tests proving search/tag/language/compatibility scope and
  exact visible-ID scope. Connected Worker coverage now proves hidden rows are not resolved or
  subscribed.
- Fixed the performance explorer's P1 correctness finding: SQL update dedup now preserves
  per-source `hosts_lists` membership by including `source_id` in the seen key and pending flush
  grouping. Final runtime dedup still happens in `host_entries`.
- Added connected regression coverage proving that disabling the first source does not remove a
  host still provided by a second enabled source.
- Rewrote carry-forward copy to use one grouped CTE over the old source generation instead of a
  correlated `MIN(id)` subquery per row.
- Extended the license-boundary scanner to include `CHANGELOG.md` and `RELEASING.md`, with
  `SecurityHardeningTest` fixture coverage for MIT claims in those release-facing files.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict
  --stacktrace`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest,org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_preservesSourceOwnershipAndKeepsRedirectTargets,org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_carryForwardSkipsAlreadySeenRowsAndMarksCopiedRows --dependency-verification=strict --stacktrace`.
- Broad Gradle verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene checks: `scripts/check-license-boundary.ps1` passed;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive` passed; full `git diff --check`
  passed with only existing LF-to-CRLF warnings.
- Release blocker remains: `scripts/check-license-boundary.ps1 -SourceMode GitTracked` and
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive` still fail
  until the already-deleted tracked forbidden entries are staged/committed:
  `app/src/main/assets/icon.svg`, `app/src/main/assets/localhost-2410.crt`,
  `app/src/main/assets/localhost-2410.key`, `app/src/main/assets/test.html`, and
  `tcpdump/jni/libpcap/SUNOS4/nit_if.o.*`.

## P0 Filtering Correctness
- [x] Add regression tests proving runtime `host_entries` ignores stale/future generations.
- [x] Make `HostEntryDao.sync()` import only user rows plus active-generation source rows.
- [x] Add regression tests proving a failed source update preserves prior active coverage or aborts activation.
- [x] Fix full-generation activation so failed enabled sources are carried forward from the prior generation.
- [x] Make domain checker and AI diagnostics consume final runtime truth.

## Rule Semantics And FilterLists
- [x] Add parser tests for unsafe dnsmasq/BIND flattening.
- [x] Stop treating upstream-routing dnsmasq rules and ambiguous BIND zones as blocked domains.
- [x] Stop flattening suffix and browser-anchor rules into exact-host storage until rule-kind semantics exist.
- [x] Add rule-kind storage and VPN/runtime suffix matching for safe suffix-capable rules.
- [x] Materialize suffix-rule base domains for root hosts output without claiming subdomain coverage.
- [x] Make suffix allow rules remove exact and suffix materialized rows they cover.
- [x] Preserve rule kind when creating user overrides and when exporting/importing backups.
- [x] Add a documented capability matrix for exact, suffix, wildcard, allow, redirect, exception, skipped, and browser-only rules.
- [x] Gate bulk FilterLists subscription by compatibility and selected URL quality.

## Performance, UX, Security, Licensing
- [x] Add phase timing and progress throttling for the update pipeline.
- [x] Serialize source imports so SQLite has one hosts-list writer during bulk updates.
- [x] Replace destructive bulk switch behavior with confirmation/progress/cancel UX.
- [x] Add leak-status UI for Private DNS, DoH, bypass, and excluded apps.
- [x] Harden self-update with signed manifests and APK hash/certificate checks.
- [x] Bind APK update signature preflight to the currently installed app signer.
- [x] Reconcile already-terminal APK downloads immediately after receiver registration.
- [x] Unregister APK download receivers after terminal update outcomes.
- [x] Prevent update progress failure states from crashing the update screen.
- [x] Add release APK package/version/signature/checksum identity gate before upload.
- [x] Enforce real signing material for release builds instead of debug-sign fallback.
- [x] Standardize user-facing copy and release artifact names on AdAway.
- [x] Add MIT relicensing track before any license claim change.
- [x] Inventory GPL-derived code/assets before any MIT relicensing claim.
- [x] Disable dormant webserver controls when the native executable is absent.
- [x] Remove dormant webserver static localhost certificate/key assets and user-CA localhost trust.
- [x] Hard-disable dormant webserver activation pending a full replacement/re-audit.

## Verification
- [x] Verify JDK 21, Android SDK, and NDK 27.2.12479018.
- [x] Run targeted JVM regression tests for changed code.
- [x] Compile Android instrumentation tests for changed DB tests.
- [x] Superseded by later full connected instrumentation suites and PR CI connected-test evidence.
- [x] Run full unit/build/lint gates once the Android SDK environment is available.
- [x] Run repo-level Gradle test task.

## Review - 2026-06-11
- Implemented the first P0 correctness slice: runtime sync now reads user rules plus
  active-generation source rows, failed full updates carry forward prior active rows, and
  single-source updates stage before promotion.
- Routed Domain Checker and AI domain checks through final runtime `host_entries` truth.
- Added parser guardrails and tests for unsafe dnsmasq upstream routing and ambiguous BIND
  zone flattening.
- Added `RuleKind` storage and v12 Room migration so suffix/browser-anchor rules are stored as
  suffix rules instead of flattened exact hosts.
- Added label-boundary runtime suffix matching, allow precedence, root exact-only output, and
  method-aware Home/Domain Checker/AI diagnostics so root mode does not claim unenforceable
  suffix protection.
- Added a conservative FilterLists.com compatibility gate so bulk subscription imports only
  DNS-safe hosts/domain syntaxes and rejects non-raw or binary URLs.
- Replaced switch-style FilterLists bulk actions with confirmation, cancel, scoped removal,
  row progress IDs, and a durable final summary.
- Disabled the dormant webserver path when `libwebserver_exec.so` is not bundled and made
  fresh installs default that preference off.
- Expanded MIT relicensing notes into a concrete GPL/native/assets blocker inventory.
- Added shared `FilterOperationState` and throttled SourceModel multi-phase progress emissions
  to reduce main-thread update churn during large imports.
- Kept downloads parallel while serializing source imports, so `hosts_lists` has one active
  SQLite writer instead of competing per-source insert transactions.
- Enforced release signing properties for release Gradle tasks; unsigned/debug-signed release
  artifacts now fail at configuration time.
- Hardened app self-updates with signed manifest envelopes, required APK SHA-256, required
  signing-certificate SHA-256, HTTPS download URLs, expiry checks, and fail-closed installer
  handoff.
- Made APK download receiver cleanup terminal and idempotent so failed or completed downloads
  do not leave stale registered receivers behind.
- Updated release CI to require signing/update trust material and to publish `AdAway_*`
  artifacts instead of the old fork branding.
- Standardized visible app resources and README copy on `AdAway`.
- Added a Home leak-resistance card for Private DNS, browser DoH exposure, stopped VPN,
  strict-mode guidance, and VPN excluded app/system bypass state.
- Added capability and MIT relicensing docs without changing the current GPLv3+ license.
- Verification passed for repo-level `gradlew test`, unit tests, androidTest Java compilation,
  debug build, and lint; connected instrumentation was not run because no Android device or
  emulator was attached.
- Re-ran focused parser/FilterLists compatibility tests and the full JVM/debug/lint gate after
  the suffix/browser-anchor guardrail; release builds fail closed without signing material and
  update-manifest trust material.
- Re-ran focused parser tests and Android test Java compilation after rule-kind suffix support;
  Room schema `12.json` is generated.
- Re-ran update manifest/integrity tests and debug Java compilation after receiver cleanup.
- Final broad gate passed after all suffix/runtime/update-receiver changes:
  `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestJavaWithJavac`, `:app:assembleDebug`,
  `:app:lintDebug`, and repo-level `test`.
- Final hygiene passed: `git diff --check` had only CRLF warnings, visible branding scan found
  no `AdsAway` hits, no Android device/emulator was attached, and release builds fail closed
  without signing material or update-manifest trust material.
- Waited for all three focused expert reviewers to finish before closing them, then integrated
  the webserver, update-security, and suffix-runtime findings.
- Removed shipped localhost certificate/key material, removed localhost user-CA trust from
  network security config, retired certificate export/install UI, hard-disabled webserver
  availability, and removed boot/root auto-start paths for the dormant server.
- Added `SecurityHardeningTest` guards for packaged credential material, private-key strings,
  localhost user-CA trust, and boot/root webserver startup source regressions.
- Bound downloaded APK signer verification to the currently installed app signer in addition
  to the signed manifest claim, reconciled already-terminal downloads after receiver
  registration, made update-progress null states non-crashing, and added strict signed-manifest
  URL validation.
- Added a release workflow artifact identity gate that verifies package name, tag-matching
  version name, expected release certificate SHA-256, and writes a checksum before upload.
- Fixed suffix runtime/root semantics: root hosts output now receives exact rows plus only the
  suffix base domain, suffix allow rules delete exact descendants and suffix rows they cover,
  user overrides preserve `RuleKind`, and backup JSON round-trips optional `kind`.
- Updated the capability matrix to disclose VPN suffix support versus root hosts base-domain
  materialization.
- Verification after reviewer integration passed: focused JVM tests for security/backup/update
  hardening; broad Gradle gate `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`, `:app:assembleDebug`, `:app:lintDebug`, and
  repo-level `test`; `git diff --check` reported only CRLF warnings; main-source scans found
  no packaged private key, localhost cert, or user-CA trust; `assembleRelease` still fails
  closed without signing material.
- Connected instrumentation remains not run: configured SDK `adb` is available, but no devices
  or emulators were attached.

## Review - 2026-06-11 Continuation
- Waited for the second expert swarm to finish and closed all four reviewers only after their
  findings were integrated.
- Added a v11-to-v12 Room migration instrumentation test covering `kind` defaults, suffix-rule
  insertion, existing runtime rows, and the new rule-kind indexes.
- Fixed global import deduplication so allow, redirect, block, suffix, and redirected-target
  rules are keyed by semantic rule identity instead of only `kind:host`.
- Switched root hosts output to a cursor-backed stream so large runtime tables are written row
  by row instead of materializing all `HostEntry` rows into heap.
- Disabled bundled root tcpdump capture until the old native tcpdump/libpcap path is replaced
  or fully re-audited, and added regression coverage for the disabled production path.
- Removed raw DNS query names from `Timber` DNS proxy logs and capped synchronized VPN log
  retention.
- Changed normal CI and CodeQL builds to debug artifacts so the production release path can
  remain fail-closed on missing signing and update-trust material.
- Updated README release notes for production signing, update-manifest trust material, release
  artifact identity checks, and debug-only CI artifacts.
- Made Home content scrollable with bottom action clearance so the new status stack is safer on
  small screens and high font scales.
- Verification passed after this continuation: focused security/parser tests, debug Java
  compilation, Android test Java compilation, broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`, `:app:assembleDebug`, `:app:lintDebug`, and
  repo-level `test`.
- Release verification remains fail-closed as intended: `:app:assembleRelease` stops without
  signing properties. Connected instrumentation remains blocked because `adb devices` reports
  no attached device or emulator.

## Review - 2026-06-11 Goal Continuation
- Spawned and waited for three focused reviewers covering release supply chain, update
  performance/correctness, and UX/accessibility; closed them only after their findings were
  integrated.
- Changed source-specific scheduled updates so they update only due source IDs. Global
  schedules remain the only path that updates all enabled sources.
- Added a batch source-update path that defers `host_entries` rebuild until all scheduled due
  sources are refreshed, avoiding one full runtime sync per source.
- Throttled FilterLists bulk-import notifications through the same progress gate used for
  WorkManager progress, with focused unit coverage for first/every-25/final emissions.
- Made bulk source-size accounting generation-aware and carried failed file sources forward
  into the next generation so cleanup does not erase prior active coverage.
- Added Android DB coverage for generation-specific source size calculation.
- Improved filter-list accessibility: per-source update is now a 48dp touch target with a
  source-specific content description, category switches expose state descriptions, and empty
  Custom category holders clear stale recycled switch state.
- Added Gradle wrapper checksum pinning and committed Gradle dependency verification metadata.
  Verified the official Gradle 8.9 checksum before adding it.
- Verification passed: focused worker tests, debug Java compile, Android test Java compile,
  strict dependency-verification debug compile, and the broad normal gate
  `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestJavaWithJavac`, `:app:assembleDebug`,
  `:app:lintDebug`, and repo-level `test`.
- Release remains fail-closed without signing properties. Connected instrumentation remains
  unrun because `adb devices` reports no attached device or emulator. `git diff --check`
  reports only CRLF normalization warnings.

## Review - 2026-06-11 Goal Continuation 2
- Waited for all three active reviewers to complete before closing them. Integrated the UX,
  release-supply-chain, and runtime-sync findings that were actionable in this slice.
- Replaced immediate destructive deletes with confirmation prompts for source deletion,
  single FilterLists unsubscribe, and user-authored list-rule deletion. Confirmed source
  deletion also clears its per-URL schedule keys before removing the source.
- Reworked `HostEntryDao.sync()` so allow-rule deletion and redirect import run as fixed
  set-based SQL statements instead of materializing active allow/redirect rows in Java.
- Added scale-oriented Android DB coverage for runtime sync: thousands of blocked rows,
  exact and suffix allow overrides, active/stale generation filtering, duplicate redirect
  priority, and elapsed-time logging without a brittle CI threshold.
- Stopped packaging the disabled bundled tcpdump native module by removing the app dependency,
  removing `:tcpdump` from normal Gradle settings, and removing the stale release workflow
  tcpdump lint exclusion. Added a security regression guard for this packaging boundary.
- Pinned all GitHub Actions workflow `uses:` refs to immutable commit SHAs, added Gradle
  wrapper validation to CI/release workflows, added weekly Dependabot updates for Actions and
  Gradle, and added release provenance attestation for APK/checksum artifacts.
- Verification passed:
  `:app:compileDebugJavaWithJavac`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  focused `SecurityHardeningTest`,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  and repo-level `test`.
- Release remains fail-closed as intended: `:app:assembleRelease --stacktrace` stops at
  `app/build.gradle:85` without signing properties. Workflow scan reports
  `NO_MUTABLE_USES_REFS`. Debug APK inspection shows only Sentry native libraries, with no
  tcpdump/libpcap payload. `git diff --check` reports only CRLF normalization warnings.
- Connected instrumentation remains unrun because the configured Android SDK has `adb`, but
  `adb devices` reports no attached device or emulator.
- Remaining release gap from the supply-chain reviewer: SBOM generation, release upload, and
  SBOM attestation are still not implemented.

## Review - 2026-06-11 Goal Continuation 3
- Spawned and waited for four focused reviewers covering release supply chain, MIT/license
  readiness, filter/runtime semantics, and QA/scoring gaps. Closed all reviewers only after
  their final findings returned.
- Replaced the first-pass hand-rolled SBOM writer with the official CycloneDX Gradle plugin
  `org.cyclonedx.bom` 3.2.4, pinned through Gradle dependency verification metadata.
- Updated the release workflow to generate the SBOM under strict dependency verification,
  validate JSON syntax, write an SBOM SHA-256 file, include the SBOM and checksum in
  provenance subjects, create a dedicated SBOM attestation, and upload both SBOM artifacts
  with the release.
- Added hardening tests that reject mutable workflow refs, require SBOM generation/upload/
  attestation/checksum wiring, reject lenient SBOM dependency resolution, and require the
  Android CI workflow to run `:app:lintDebug`.
- Added `:app:lintDebug` as an explicit Android CI gate.
- Reduced lint warnings on changed UI surfaces by moving FilterLists compatibility copy into
  string resources, adding RTL-symmetric padding, and replacing Home scheduled-progress text
  concatenation with a string resource placeholder.
- Fixed mixed-case imported host misses by lower-casing hosts at entity write boundaries and
  in runtime SQL import/allow comparisons, including legacy-row-safe `LOWER(...)` queries.
- Fixed root hosts export conflict where a suffix block could emit the same base host as an
  exact redirect; exact rows now win in root export, matching VPN resolution priority.
- Added Android DB coverage for mixed-case runtime resolution and exact-redirect versus suffix
  block root export behavior.
- Verification passed:
  `--write-verification-metadata sha256 :app:generateSbom`,
  strict `:app:generateSbom`,
  focused `SecurityHardeningTest`,
  `:app:compileDebugJavaWithJavac`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  and repo-level `test`.
- Final evidence: generated SBOM is CycloneDX 1.6 for `AdAway` with 112 components;
  workflow scan reports `NO_MUTABLE_USES_REFS`; lint reports `0 errors, 402 warnings`;
  `git diff --check` reports only CRLF normalization warnings.
- Release remains fail-closed as intended: `:app:assembleRelease --stacktrace` stops at
  `app/build.gradle:86` without signing properties. Connected instrumentation remains unrun
  because `adb devices` reports no attached device or emulator.
- Remaining major gaps from reviewers: connected/emulator CI for `androidTest`, real
  performance budgets/macrobenchmarking, behavioral WorkManager tests for scheduled updates,
  dnsmasq suffix semantics, Domain Checker write validation, and MIT edition blockers
  around GPL-derived code/assets and stale third-party notices.

## Review - 2026-06-11 Goal Continuation 4
- Waited for all three focused reviewers to complete before closing them. Integrated the
  parser, Domain Checker, and connected-Android-test findings that were actionable in this
  slice.
- Fixed dnsmasq rule semantics: block-safe `address=/domain/0.0.0.0`,
  `address=/domain/::`, `address=/domain/#`, and `local=/domain/` now import as suffix
  rules; upstream routing and public redirect targets such as
  `server=/domain/8.8.8.8` and `address=/domain/8.8.8.8` remain unsupported/skipped.
- Added action-aware Surge/Clash/Quantumult parsing: `DOMAIN`/`DOMAIN-FULL` `REJECT`
  rules import as exact rules, `DOMAIN-SUFFIX` `REJECT` rules import as suffix rules,
  and non-block actions such as `DIRECT` remain skipped. FilterLists syntax `29` remains
  not bulk-safe.
- Hardened Domain Checker normalization so rule writes reject IPs, IPv6 literals,
  `localhost`, single-label hosts, malformed domains, wildcard input, and unsafe URL-ish
  payloads, while preserving valid URL stripping, trailing-root-dot handling, and IDNA
  conversion to ASCII.
- Corrected public capability wording: README no longer claims "any URL, any format", and
  the rule capability matrix now documents dnsmasq suffix behavior and Surge/Clash block
  action boundaries.
- Added a connected Android CI job using only already-pinned workflow actions. It installs
  the Android emulator and API 34 Google APIs x86_64 image, boots a headless AVD, and runs
  `:app:connectedDebugAndroidTest --stacktrace`.
- Installed the local Android emulator package and API 34 Google APIs x86_64 system image
  under `C:\Users\solun\AppData\Local\Android\Sdk` so connected tests can run locally.
- First local connected run exposed real androidTest gaps: Room migration schemas were not
  packaged as androidTest assets and the in-memory DB fixture did not seed `hosts_meta`.
  Fixed both by adding `app/schemas` to androidTest assets and seeding the metadata row in
  `DbTest`.
- Verification passed:
  focused parser/compatibility/Domain Checker tests,
  focused `SecurityHardeningTest`,
  local emulator `:app:connectedDebugAndroidTest`,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  and repo-level `test`.
- Final evidence: connected Android tests report `20` tests, `0` failures, `0` errors;
  installed SDK packages include `emulator` 36.6.11, `platform-tools` 37.0.0,
  `platforms;android-34`, `build-tools;34.0.0`, `ndk;27.2.12479018`, and
  `system-images;android-34;google_apis;x86_64`; workflow scan reports
  `NO_MUTABLE_USES_REFS`; lint reports `401` issues; `git diff --check` reports only CRLF
  normalization warnings.
- Remaining major gaps: real performance budgets/macrobenchmarking, behavioral
  WorkManager tests for scheduled updates, remaining lint backlog, and MIT edition blockers
  around GPL-derived code/assets and stale third-party notices.

## Review - 2026-06-11 Goal Continuation 5
- Spawned and waited for three focused reviewers covering performance budgets,
  WorkManager scheduling/progress QA, and license/lint cleanup. Closed all reviewers only
  after their findings returned.
- Fixed the remaining APK update scheduler stale-work risk: `ApkUpdateService.syncPreferences`
  now uses `ExistingPeriodicWorkPolicy.UPDATE`, matching host-source and filter-set update
  scheduling so changed settings replace existing periodic work instead of preserving stale
  requests.
- Added connected WorkManager behavior tests for host-source updates, APK update checks, and
  filter-set updates. The tests now seed stale unique periodic work and verify constraints or
  periodic intervals change after production scheduling calls, so they distinguish `UPDATE`
  from `KEEP` instead of only proving that one active work exists.
- Fixed scheduled filter-set progress semantics. Scoped scheduled updates are currently
  executed as one batched retrieval, so progress now consistently reports a single batch
  (`0/1` while running, `1/1` when done) instead of presenting `0/N` and then jumping to
  `1/1`. Replaced the old source-text worker test with direct progress data assertions.
- Added low-flake connected performance budgets to the existing in-memory DB scale test:
  median-of-three `HostEntryDao.sync()` must stay below 5000 ms, and median root hosts cursor
  export must stay below 2000 ms for the current scale fixture.
- Added a README license-status note: MIT relicensing remains a future track only, and the
  distributed app remains GPLv3+ until GPL-derived app/VPN code, assets, and third-party
  notice boundaries are cleared and verified.
- Reduced lint debt by clearing the narrow `ContentDescription` warning class: decorative
  filter category and add-options icons now use null content descriptions, while the source
  update indicator keeps its runtime accessibility description and suppresses only the static
  XML warning.
- Verification passed:
  focused `FilterSetUpdateWorkerTest`,
  focused `SecurityHardeningTest`,
  `:app:compileDebugJavaWithJavac`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  focused connected WorkManager/DB scale tests,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  repo-level `test`,
  and full local emulator `:app:connectedDebugAndroidTest`.
- Final evidence: full connected Android tests report `30` tests, `0` failures, `0` errors;
  `HostEntryDao.sync` scale median is 2689 ms under the 5000 ms budget; root hosts cursor
  scale median is 441 ms under the 2000 ms budget; lint reports `392` issues and `0`
  `ContentDescription` issues; workflow scan reports `NO_MUTABLE_USES_REFS`;
  `git diff --check` reports only CRLF normalization warnings.
- Remaining major gaps: broader macrobenchmark/performance coverage beyond the DB/runtime
  scale budgets, the remaining lint backlog, deeper UI screenshot/accessibility matrix, and
  MIT edition blockers around GPL-derived code/assets and full third-party notice
  classification.

## Review - 2026-06-11 Goal Continuation 6
- Kept all three running reviewers open until their final results returned, per the updated
  project lesson. Integrated their findings instead of closing or summarizing around them.
- MIT/license reviewer confirmed the current legal baseline is still correct: README and
  `docs/mit-relicensing-plan.md` must keep the distributed app GPLv3+ because shipped
  app/VPN code and brand assets remain GPL-derived. Follow-up notice work should classify
  tcpdump as source-only/excluded from `:app`, webserver/Mongoose/OpenSSL as dormant, and
  shipped mipmap/drawable icon assets as current MIT-edition blockers.
- UI/lint reviewer identified Domain Checker hardcoded text as the narrowest visible win.
  Replaced Domain Checker title, input hint, action button, source label, status text, source
  rows, and checked-domain text with string resources; reduced the result card corner radius
  from 20dp to 8dp for consistency with the utility UI direction.
- Cleared the `DefaultLocale` warnings in AI-related provider/category parsing by using
  `Locale.ROOT` in `AiActionExecutor`, `AiAgentResponse`, `FilterListSuggester`,
  `LlmProvider`, and `PrefsAiFragment`.
- Performance reviewer recommended a connected import-throughput gate before adding a
  macrobenchmark module. Added `SourceLoaderPerformanceTest`, a file-backed WAL connected
  test that drives `SourceLoader.parse(...)`, raw SQLite bulk inserts, `HostEntryDao.sync()`,
  and root cursor export on a mixed 10k-line fixture with hosts, ABP suffix, dnsmasq suffix,
  Surge suffix, redirects, duplicates, and skipped rules.
- The first 100k and 10k connected attempts exposed a real production deadlock: parser
  completion markers were stored through `HostListItem.setHost()`, which lowercases strings,
  but `ItemInserter` checked the marker with reference equality. The inserter then treated
  marker rows as normal rows and waited forever. Fixed the sentinel to lowercase and compare
  by value in both parser and inserter paths.
- Verification passed:
  focused `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`,
  targeted connected `SourceLoaderPerformanceTest`,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  repo-level `test`,
  and full local emulator `:app:connectedDebugAndroidTest`.
- Final evidence: full connected Android tests report `31` tests, `0` failures, `0` errors;
  the connected SourceLoader gate reports `lines=10000`, `inserted=9500`,
  `runtimeRows=9000`, `progressEvents=5`, `parseMs=9968`, `syncMs=448`, and
  `rootCursorMs=131`; lint reports `372` issues, with Domain Checker down to only its
  existing `Overdraw` warning; `git diff --check` reports only CRLF normalization warnings.
- Remaining major gaps: optimize import throughput beyond the current 10k connected budget,
  add a separate non-CI/manual 100k/1M/5M benchmark track or macrobenchmark module, reduce
  the remaining lint backlog, update stale README/help copy noted by the UI reviewer, and
  complete third-party notice/MIT-edition blocker classification.

## Review - 2026-06-11 Goal Continuation 7
- Kept the final performance reviewer running until its result arrived. Integrated its
  recommendation to keep the source-wide raw SQLite transaction in `SourceLoader.ItemInserter`
  and added the missing failure guard it flagged.
- Hardened `SourceLoader` partial-read behavior: `SourceReader` now records read failures,
  `ItemInserter` checks that failure before committing the source-wide raw SQLite transaction,
  and the outer parse path clears the target staging generation before throwing. A truncated
  or failing read can no longer commit partial rows for the new generation.
- Added connected regression coverage in `SourceLoaderPerformanceTest`: a `BufferedReader`
  that throws after one line must make `parse()` throw, leave the target generation empty,
  and preserve the previous active generation rows.
- Kept the measured source-wide transaction path. The full connected run now reports the
  10k mixed-rule SourceLoader gate at `parseMs=4180`, `syncMs=183`, `rootCursorMs=127`,
  `inserted=9500`, `runtimeRows=9000`, and `progressEvents=5`.
- Removed stale public/help instructions that told users to enable the dormant local
  webserver. README now describes current Gradle modules accurately; default English help
  points users to Domain Checker, DNS Log, and targeted Allowed rules instead of the old
  blank-response workaround.
- Expanded the legal/docs cleanup from the expert reviews: `THIRD_PARTY_LICENSES.md` now
  distinguishes current app/runtime dependencies, source-only build/test materials, branded
  assets, tcpdump source-only status, and dormant webserver/OpenSSL notices; the MIT plan
  now treats this as blocker inventory, not a relicensing switch.
- Verification passed:
  focused compile `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`,
  targeted connected `SourceLoaderPerformanceTest`,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  repo-level `test`,
  and full local emulator `:app:connectedDebugAndroidTest`.
- Final evidence: full connected Android tests report `32` tests, `0` failures, `0` errors;
  targeted SourceLoader connected tests report `2` tests, `0` failures, `0` errors;
  lint reports `372` issues; workflow scan reports `NO_MUTABLE_USES_REFS`; `git diff --check`
  reports only CRLF normalization warnings.
- Remaining major gaps: the app is still not objectively at a 100/100 score while lint has
  372 findings and no screenshot/accessibility/dynamic-type/RTL matrix has been completed;
  performance coverage still needs non-CI/manual 100k, 1M, and 5M rule benchmarks or a
  macrobenchmark track; MIT remains blocked until GPL-derived code/assets and third-party
  notices are legally cleared.

## Review - 2026-06-11 Goal Continuation 8
- Reran the expert swarm slice instead of closing unfinished work early. Integrated completed
  findings from the lint/quality, UI/accessibility, performance-scale, and MIT/legal-release
  reviewers.
- Lint/quality slice: removed the remaining targeted `HardcodedText` and `SetTextI18n`
  findings in the changed Discover, AI suggestion, Home counter, and Lists search surfaces.
  Added missing `many` plural quantities for Catalan, Mexican Spanish, and Brazilian
  Portuguese source-count strings. Final lint counters are `352` total findings,
  `0` `MissingQuantity`, `0` `HardcodedText`, and `0` `SetTextI18n`.
- Performance/schema slice: removed the redundant `host_entries(host)` index because the
  primary key and existing `host_entries(kind, host)` index already cover the exact runtime
  lookups. Added database version 13, migration 12->13, schema export, and connected
  migration coverage proving the redundant index is dropped, the lookup index remains, the
  row survives migration, and exact lookup query planning does not use the removed index.
- Legal/release slice: aligned README and RELEASING release artifact naming with the current
  workflow, documented release SBOM/checksum/provenance expectations, and expanded
  `THIRD_PARTY_LICENSES.md` source-only/build resource inventory. The distributed app remains
  GPLv3+; an MIT edition remains blocked on GPL-derived code/assets and legal clearance.
- UI/accessibility reviewer findings carried forward: Home needs dynamic-type clipping checks,
  Discover and My Lists need stronger empty/loading/error/retry states, destructive Domain
  Checker and AI key-clear actions need confirmation, and update failure summaries need to
  remain visible instead of collapsing into a generic result.
- Verification passed:
  focused compile `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`,
  targeted connected `MigrationTest`,
  broad `:app:testDebugUnitTest`,
  `:app:compileDebugAndroidTestJavaWithJavac`,
  `:app:assembleDebug`,
  `:app:lintDebug`,
  repo-level `test`,
  and full local emulator `:app:connectedDebugAndroidTest`.
- Final evidence: full connected Android tests report `33` tests, `0` failures, `0` errors;
  broad Gradle gate completed successfully in 58 seconds; full connected run completed
  successfully in 59 seconds; workflow scan reports `NO_MUTABLE_USES_REFS`; `git diff --check`
  reports only CRLF normalization warnings.
- Remaining major gaps: lint still has `352` findings, mostly `UnusedResources`; no full
  screenshot/accessibility/dynamic-type/RTL matrix has been completed; performance still
  needs manual or macrobenchmark 100k, 1M, and 5M rule update runs; MIT remains a separate
  edition track until GPL-derived material is cleared.

## Review - 2026-06-11 Goal Continuation 9
- Spawned two focused reviewers and waited for their completed findings before closing them:
  one reviewed `NotifyDataSetChanged`/RecyclerView update performance, and one reviewed the
  next UI/accessibility patch slice.
- Replaced all remaining `notifyDataSetChanged()` usage in the inspected app UI/model source
  with more specific update mechanisms. Discover catalog and FilterLists structural filtering
  now use `DiffUtil`; selection/progress/subscription changes use row or range updates; the
  grouped source list now diffs category/source rows; the redundant `SimpleAdapter` refresh in
  Adware was removed.
- Fixed a DiffUtil correctness edge in `CategorizedSourcesAdapter` by making
  `FilterListItem.SourceItem` capture immutable UI snapshot fields. This prevents optimistic
  source mutations from changing the old diff item before the diff runs.
- Applied the UI/accessibility reviewer slice for active source rows: categorized source
  labels and status text now allow two lines, per-source download errors are visible in the
  active categorized list, and the error text is truncated before display to keep the row
  bounded.
- Cleaned up remaining visible FilterLists Java literals touched in this slice: all-languages,
  manual-review, no-direct-URL, resolve/update-failed, background-running, and syntax fallback
  messages now use string resources.
- Verification passed:
  focused `:app:compileDebugJavaWithJavac :app:lintDebug`,
  focused unit tests `HostsSourceTest` and `DownloadErrorTrackingTest`,
  broad `:app:testDebugUnitTest`,
  `:app:assembleDebug`,
  and `:app:lintDebug`.
- Final evidence: lint reports `336` findings, down from `352`; `NotifyDataSetChanged`,
  `HardcodedText`, `SetTextI18n`, and `MissingQuantity` are all `0`; `UnusedResources` remains
  `259` and `Overdraw` remains `16`. `rg "notifyDataSetChanged\\(" app/src/main/java/org/adaway/ui
  app/src/main/java/org/adaway/model` returns no matches. `git diff --check` reports only CRLF
  normalization warnings.
- Remaining major gaps: full screenshot/accessibility/dynamic-type/RTL verification is still
  not complete; lint still has `336` findings; large 100k/1M/5M update benchmarks remain;
  FilterLists/My Lists need persistent empty/error/retry states; MIT remains blocked on
  GPL-derived material and legal clearance.
- Follow-up overdraw slice: removed redundant Home `ui_bg` backgrounds from `fragment_home`
  and `home_content`, leaving the home activity/navigation root background authoritative.
  Verification passed with `:app:compileDebugJavaWithJavac :app:lintDebug`. Lint now reports
  `334` total findings and `14` `Overdraw` findings. Broader root/dialog background overdraw
  items were intentionally left for a visual/screenshot pass because several may be
  standalone screen roots.

## Review - 2026-06-12 Goal Continuation 10
- Continued from current worktree evidence: lint started this slice at `334` findings with
  `PrivateResource=12`, `Overdraw=14`, and `UnusedResources=259`.
- Added a TDD-covered FilterLists inline state resolver. The first focused test run failed
  because `FilterListsUiState` did not exist; after adding the resolver, the focused
  `FilterListsUiStateTest` passed.
- Wired persistent FilterLists.com empty/error/retry UI into the Discover FilterLists screen:
  load failures with no rows now show an inline retry panel, an empty directory shows a
  durable empty state, and active filters with zero matches show an inline no-matches state.
  Snackbar feedback remains as a transient supplement instead of the only failure surface.
- Spawned and integrated a read-only PrivateResource reviewer. Replaced the private fragment
  animator shadowing with app-owned `adaway_fragment_*` animator resources and an app-owned
  interpolator, then updated `PrefsActivity` to reference those names. This removes the
  private override and private interpolator references instead of suppressing them.
- Verification passed:
  red/green focused `FilterListsUiStateTest`,
  focused `:app:compileDebugJavaWithJavac`,
  focused `:app:lintDebug`,
  broad `:app:testDebugUnitTest`,
  `:app:assembleDebug`,
  and `:app:lintDebug`.
- Final evidence: lint now reports `322` findings, `PrivateResource=0`,
  `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`, and `MissingQuantity=0`.
  `rg "R\\.animator\\.fragment_(open|close)_(enter|exit)|@anim/fragment_fast_out_extra_slow_in"
  app/src/main` reports no old private animation references. `git diff --check` reports only
  CRLF normalization warnings.
- Remaining major gaps: `UnusedResources=259`, `Overdraw=14`, dependency/version lint
  findings, no full screenshot/accessibility/dynamic-type/RTL matrix, no 100k/1M/5M update
  benchmark run, My Lists still needs durable empty/loading/error state, and MIT remains
  blocked until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 11
- Answered the timing/status concern by continuing the current implementation slice instead
  of stopping at explanation. The remaining Ampere resource-lint agent was checked twice and
  is still running; it has not been closed.
- Added a TDD-covered My Lists inline state resolver. The first focused run failed because
  `ListsUiState` did not exist; after adding the resolver, `ListsUiStateTest` passed.
- Wired durable My Lists empty/error/retry states into `hosts_lists_fragment` and
  `AbstractListFragment`: refresh load errors show a retry panel, empty lists show a no-rules
  panel, and active searches with no rows show a no-matches panel. The list remains visible
  during loading and whenever rows exist.
- Verification passed:
  focused red/green `ListsUiStateTest`,
  focused `:app:compileDebugJavaWithJavac :app:lintDebug`,
  broad `:app:testDebugUnitTest`,
  `:app:assembleDebug`,
  and `:app:lintDebug`.
- Final evidence: lint remains at `322` findings with `PrivateResource=0`,
  `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`, `MissingQuantity=0`,
  `UnusedResources=259`, `Overdraw=14`, and `GradleDependency=9`. `git diff --check`
  reports only CRLF normalization warnings.
- Remaining major gaps: the still-running Ampere lint reviewer may return actionable
  `UnusedResources` cleanup, `Overdraw=14` remains, dependency/version lint findings remain,
  no full screenshot/accessibility/dynamic-type/RTL matrix has been completed, no 100k/1M/5M
  update benchmark run has been completed, and MIT remains blocked until GPL-derived material
  and assets are cleared.

## Review - 2026-06-12 Goal Continuation 12
- Integrated the completed Ampere resource-lint report after it finished; the agent was then
  closed. No unfinished agent was closed early.
- Verified the safest resource-deletion group by grepping references and matching current
  `UnusedResources` lint entries before deletion. Removed unused glass/progress drawables,
  stale `lists_activity`, `drawer_list_item`, and `reboot_dialog` layouts, and the unused
  `button_spacing` / `dialog_spacing` dimens.
- Verified and removed two closed stale resource groups after separate lint/assemble gates:
  the legacy Discover activity/dialog shell and the legacy Home bottom-app-bar/drawer shell.
- Verification passed:
  first cleanup `:app:lintDebug :app:assembleDebug`,
  Discover-shell cleanup `:app:lintDebug :app:assembleDebug`,
  Home-shell cleanup `:app:lintDebug :app:assembleDebug`,
  and final broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- Final evidence: lint now reports `299` findings, `UnusedResources=240`, `Overdraw=11`,
  `PrivateResource=0`, `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`,
  `MissingQuantity=0`, and `GradleDependency=9`. `git diff --check` reports only CRLF
  normalization warnings.
- Remaining major gaps: `UnusedResources=240`, `Overdraw=11`, dependency/version lint
  findings, no full screenshot/accessibility/dynamic-type/RTL matrix, no 100k/1M/5M update
  benchmark run, and MIT remains blocked until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 13
- Spawned three read-only reviewers for independent remaining quality gaps: next
  `UnusedResources` candidates, dependency lint, and overdraw. Integrated and closed only the
  completed dependency reviewer; the resource and overdraw reviewers are still running.
- Removed another verified closed stale resource group: old `WelcomeActivity`/support layouts
  and their private-only drawables. The current manifest uses `OnboardingActivity`, and grep
  showed those resources were only referenced inside the deleted stale layouts.
- Removed four additional stale layout shells after tracing current replacements:
  `help_activity`, `help_fragment`, `hosts_content_fragment`, and `hosts_sources_dialog`.
  Localized raw help HTML was intentionally kept for a dedicated review because it is broad
  localized user-facing content.
- Upgraded the AndroidX Test stack in `gradle/libs.versions.toml`:
  `androidx.test.ext:junit` `1.2.1 -> 1.3.0`, and shared
  `androidxTestCore` `1.6.1 -> 1.7.0` for `androidx.test:core` and
  `androidx.test:runner`. The first compile failed on dependency verification for new
  artifacts; reran Gradle with `--write-verification-metadata sha256` and verified the gate
  passed with refreshed `gradle/verification-metadata.xml`.
- Verification passed:
  welcome/support cleanup `:app:lintDebug :app:assembleDebug`,
  stale layout-shell cleanup `:app:lintDebug :app:assembleDebug`,
  dependency metadata refresh `:app:compileDebugAndroidTestJavaWithJavac :app:lintDebug`,
  forced fresh lint model `:app:lintDebug --rerun-tasks`,
  and final broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- Final evidence: lint now reports `274` findings, `UnusedResources=226`, `Overdraw=9`,
  `GradleDependency=0`, `AndroidGradlePluginVersion=3`, `PrivateResource=0`,
  `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`, and `MissingQuantity=0`.
  `git diff --check` reports only CRLF normalization warnings.
- Remaining major gaps: resource and overdraw reviewers still need to finish, `UnusedResources`
  remains at `226`, `Overdraw=9`, AGP major-version lint remains, no full
  screenshot/accessibility/dynamic-type/RTL matrix, no 100k/1M/5M update benchmark run, and
  MIT remains blocked until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 14
- Integrated the completed overdraw reviewer after it finished, then closed that completed
  agent. The resource reviewer is still running and was left open.
- Removed only the three safe redundant fragment root backgrounds identified by the reviewer:
  `fragment_discover`, `fragment_domain_checker`, and `fragment_more`. The shared
  `activity_home_nav` shell still owns the `@color/ui_bg` background, so these removals avoid
  duplicate painting without exposing the launch-screen window background.
- Left the remaining overdraw warnings untouched because they need screenshot or theme review:
  `activity_home_nav`, `activity_onboarding`, `bottom_sheet_ai_suggest`,
  `fragment_discover_catalog`, `schedules_activity`, and `update_actity`.
- Verification passed:
  focused `:app:lintDebug :app:assembleDebug`,
  final broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`,
  and `git diff --check`.
- Final evidence: lint now reports `271` findings, `UnusedResources=226`, `Overdraw=6`,
  `GradleDependency=0`, `AndroidGradlePluginVersion=3`, `PrivateResource=0`,
  `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`, and `MissingQuantity=0`.
  `git diff --check` reports only CRLF normalization warnings.
- Remaining major gaps: resource reviewer still needs to finish, `UnusedResources=226`,
  `Overdraw=6`, AGP major-version lint remains, no full
  screenshot/accessibility/dynamic-type/RTL matrix, no 100k/1M/5M update benchmark run, and
  MIT remains blocked until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 15
- Integrated the completed resource reviewer after it finished, then closed that completed
  agent. No unfinished agent was closed early.
- Removed only exact dead resource keys from the reviewer report: deleted localized
  `strings_support.xml` files, removed old home drawer/action-bar strings from localized
  `strings_home.xml` files, removed `filter_catalog_title`, removed localized
  `reboot_never_reboot`, and removed three unused color tokens.
- Guarded the cleanup with exact live-reference searches so similarly named live resources
  such as `update_update_button` and `update_support_title` were not touched.
- Verification passed:
  exact `rg` live-reference check for removed keys,
  exact `rg` resource-name check for removed keys,
  `:app:lintDebug :app:assembleDebug`,
  `:app:testDebugUnitTest`,
  and `git diff --check`.
- Final evidence: lint now reports `260` findings, `UnusedResources=215`, `Overdraw=6`,
  `GradleDependency=0`, `AndroidGradlePluginVersion=3`, `PrivateResource=0`,
  `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`, and `MissingQuantity=0`.
  `git diff --check` reports only CRLF normalization warnings.
- Remaining major gaps: `UnusedResources=215`, `Overdraw=6`, AGP major-version lint remains,
  no full screenshot/accessibility/dynamic-type/RTL matrix, no 100k/1M/5M update benchmark
  run, and MIT remains blocked until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 16
- Integrated Pauli's completed drawer-resource review after verifying the current tree. Removed
  old `drawer_open`, `drawer_close`, and `drawer_items` resources from localized values files,
  deleting locale files that only contained the dead drawer array. This also removed the
  `InconsistentArrays` warning caused by stale drawer menu translations.
- Cleared small deterministic lint groups: Hebrew unreachable `many` plural quantities,
  two localized spelling warnings, obsolete min-SDK guards in API-key storage and VPN startup,
  plural-candidate catalog strings, and two empty fragment-host layouts.
- Integrated Kuhn's completed overdraw review with the safer theme-first approach. Added plain
  content/window-background themes, switched Home/Onboarding content inflation away from the
  launch-screen drawable, gave Schedules a surface window background, and then removed redundant
  root backgrounds from activity and fragment layouts. The AI bottom sheet now uses its Material
  container surface instead of repainting an inner full-sheet background.
- Verification passed:
  exact drawer reference checks,
  focused `:app:compileDebugJavaWithJavac :app:lintDebug` after non-resource cleanup,
  focused `:app:compileDebugJavaWithJavac :app:lintDebug` after drawer cleanup,
  focused `:app:compileDebugJavaWithJavac :app:lintDebug` after overdraw cleanup,
  final broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`,
  and `git diff --check`.
- Final evidence: lint now reports `229` findings, `UnusedResources=212`, `Overdraw=0`,
  `GradleDependency=0`, `AndroidGradlePluginVersion=3`, `PrivateResource=0`,
  `NotifyDataSetChanged=0`, `HardcodedText=0`, `SetTextI18n=0`, `MissingQuantity=0`,
  `InconsistentArrays=0`, `UnusedQuantity=0`, `Typos=0`, `ObsoleteSdkInt=0`,
  `PluralsCandidate=0`, and `MergeRootFrame=0`. `git diff --check` reports only CRLF
  normalization warnings.
- Remaining major gaps: `UnusedResources=212`, `VectorPath=5`, AGP major-version lint remains,
  query visibility/foreground-service/autofill/backup/label/inflate lint remain, no full
  screenshot/accessibility/dynamic-type/RTL matrix, no 100k/1M/5M update benchmark run, and MIT
  remains blocked until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 17
- Completed the AGP/Gradle quality pass: upgraded Android Gradle Plugin to `9.2.1`,
  Gradle wrapper to `9.5.1`, installed Android SDK Platform 36, moved `compileSdk` to 36
  while leaving `targetSdk` at 34, and refreshed dependency verification metadata.
- Cleared all remaining non-resource lint groups by adding Android 12+ data extraction and
  pre-Android-12 full-backup exclusion rules, fixing the Discover search autofill state,
  removing the redundant onboarding label, guarding foreground-service type constants by API
  level, documenting intentional package-visibility enumeration with targeted suppressions,
  and giving the add-source bottom sheet inflater a parent.
- Updated dependency warnings: Guava `33.6.0-android`, OkHttp `5.4.0`, dnsjava `3.6.5`,
  org.json `20260522`, and Sentry BOM `8.43.2`. The Sentry 8 API change was handled by
  passing `SentryLogLevel.ERROR` and mirroring the enum in the local sentry stub.
- Removed the final `UnusedResources` set reported by lint: 203 dead strings, 3 dead plurals,
  1 dead array, obsolete help raw files across locales, and unused mipmap icon-round/
  icon-foreground variants. The broad resource prune was validated by resource linking and
  debug assembly after removal.
- Added CycloneDX SBOM generation and verified dependency metadata for the SBOM path. The SBOM
  is generated at `app/build/reports/sbom/adaway.cdx.json` and was 280,178 bytes in the
  verified run.
- Verification passed:
  `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`,
  `:sentrystub:assemble`,
  `:app:generateSbom --rerun-tasks`,
  and `git diff --check`.
- Final evidence: `lint_issues=0`; `UnusedResources=0`; `Overdraw=0`;
  `GradleDependency=0`; `AndroidGradlePluginVersion=0`; `NewerVersionAvailable=0`;
  `Aligned16KB=0`; `QueryPermissionsNeeded=0`; `InlinedApi=0`; `Autofill=0`;
  `RedundantLabel=0`; `InflateParams=0`; `DataExtractionRules=0`. `git diff --check`
  reports only CRLF normalization warnings.
- Remaining major gaps: CycloneDX still warns that it resolves `releaseRuntimeClasspath`
  during configuration, no full screenshot/accessibility/dynamic-type/RTL matrix has been run,
  no 100k/1M/5M update benchmark run has been captured in this continuation, release assemble
  remains intentionally gated on real signing and update-manifest keys, and MIT remains blocked
  until GPL-derived material and assets are cleared.

## Review - 2026-06-12 Goal Continuation 18
- Left unfinished expert work open until the long-running 5M connected benchmark completed. No
  unfinished agent was closed early.
- Added and verified the connected scale benchmark in `SourceLoaderPerformanceTest`. Baseline
  5M run was allowed to finish and took `1h 17s` wall time with
  `parseMs=1102906`, `syncMs=196518`, and `rootCursorMs=2235494`. After the runtime sync/root
  export optimization, the 5M run passed in `33m 39s` with `parseMs=887334`,
  `syncMs=168551`, and `rootCursorMs=876350`, a roughly 56% wall-time reduction and roughly
  61% root-cursor reduction. The patched 1M run passed in `4m 48s` with
  `syncMs=5992` and `rootCursorMs=29251`.
- Optimized runtime host-entry sync/export by skipping allow-rule delete passes when no active
  allow rules exist, using `INSERT OR IGNORE` against the runtime primary key instead of a
  temp `DISTINCT` set, logging sync phase timings, and streaming root hosts without a production
  `ORDER BY` unless allow-rule subtraction is needed.
- Hardened release and SBOM behavior: release/SBOM tasks now fail closed without signing
  material and update-manifest trust material. Verified unsigned `:app:generateSbom` fails as
  intended, then verified an ephemeral signed
  `:app:assembleRelease :app:generateSbom --dependency-verification=strict` succeeds. Verified
  APK package `org.adaway`, version `13.5.0`, versionCode `130500`, compileSdk `36`, APK SHA-256
  `EC33828AFB436B10602BB8F78C14D56BB68E04D4DD4FAB976D27A61A99FFF69B`, and SBOM SHA-256
  `2AB0E72C1D3119B20B85CA2D995E85EC08A34E1D81076B25DE1CE0812C0F3894`.
- Added the connected UX matrix test and runner. Fixed Home, Discover, and Sources touch-target
  and label regressions, including source/category switch content descriptions, RTL-safe Home
  version alignment, and large-font Discover chip sizing. Updated the UX script to install and
  run instrumentation directly so screenshots can be pulled before Gradle cleanup, and to parse
  instrumentation output because `am instrument` can exit zero on assertion failures.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`,
  targeted connected `UxDeviceMatrixTest`,
  `scripts/run-ux-matrix.ps1` for baseline, font scale `1.3`, and RTL font scale `1.3`,
  final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac`,
  lint XML issue count `0`, and `git diff --check`.
- Final artifacts: UX matrix pulled 18 screenshots under `app/build/reports/ux-matrix`, six
  screens for each of baseline, large font, and RTL large font. `git diff --check` reports only
  CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked
  until GPL-derived code/assets are cleared or permission is obtained, CycloneDX still emits its
  configuration-time resolution warning, R8 still warns about dnsjava service-provider classes,
  and broader manual product/security review is still needed before claiming release quality.

## Review - 2026-06-12 Goal Continuation 19
- Integrated the second expert swarm findings across licensing, native/security, and filter
  correctness. No unfinished expert was closed early.
- Added a CI license-boundary guard in `scripts/check-license-boundary.ps1` and wired it into
  `.github/workflows/android-ci.yml`. The guard blocks premature MIT release claims while GPL
  blockers remain, and keeps the GPL/MIT boundary documented until a real relicensing pass is
  complete. Verified locally with `.\scripts\check-license-boundary.ps1`.
- Fixed the VPN runtime truth cache gap found by the filter reviewer. Domain checker and AI rule
  mutations now invalidate the application-owned VPN rules cache after `host_entries` sync, and
  `VpnModelCacheInvalidationTest` proves a cached suffix block is replaced by a later exact user
  allow only after invalidation.
- Hardened update manifests against replay by requiring `expiresAt` and rejecting validity windows
  longer than 14 days. Added unit coverage for missing and excessive expiry.
- Narrowed release/SBOM signing gates so dependency audits on `releaseRuntimeClasspath` are no
  longer blocked by signing secrets, while actual release and SBOM tasks still fail closed without
  signing and update-manifest trust material.
- Moved repository declarations into `settings.gradle` with
  `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, kept Google/Maven Central centralized, and restricted
  JitPack to `com.github.topjohnwu.libsu`.
- Verification passed:
  focused `ManifestTest`, `SecurityHardeningTest`, and `SourceLoaderParserPatternsTest`;
  targeted connected `VpnModelCacheInvalidationTest`;
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`;
  `:app:dependencies --configuration releaseRuntimeClasspath --dependency-verification=strict`;
  unsigned `:app:assembleRelease` and `:app:generateSbom` fail closed with the expected signing
  material error; final
  `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac`;
  lint XML issue count `0`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived code/assets and third-party asset provenance; R8 still warns about dnsjava
  service-provider classes; CycloneDX still emits its configuration-time resolution warning;
  dependency verification is still hash-only rather than signature/trusted-key backed; domain
  checker root-vs-VPN integration coverage is still thin; failed-source all-failed activation
  needs another hardening pass; FilterLists worker coverage remains thin; and broad ProGuard/R8
  suppressions still need ownership tightening.

## Review - 2026-06-12 Goal Continuation 20
- Spawned a third expert swarm for failed-source generation correctness, dnsjava/R8 release
  warnings, and adversarial QA/security review. All spawned agents completed; none were closed
  while unfinished.
- Fixed all-failed full-update activation. `SourceModel` now tracks genuinely successful sources
  separately from UI "downloaded/processed" progress, aborts all-failed generations before
  activation, removes staging rows, and treats source-less download future exceptions as unsafe
  instead of activating without guaranteed carry-forward.
- Added `SourceModelGenerationFailureTest`, an in-memory Room connected regression proving an
  all-failed full update throws `DOWNLOAD_FAILED`, preserves the old active generation, preserves
  runtime truth, and removes staging rows.
- Centralized VPN cache invalidation at `SourceModel.syncHostEntries()` and extended
  `VpnModelCacheInvalidationTest` so the application-level source sync path refreshes live VPN
  rule truth without requiring a VPN restart.
- Fixed the dnsjava release R8 warning. The release build now strips dnsjava's Java 18 desktop
  resolver SPI descriptor from `mergeReleaseJavaResource` output before R8/signing, excludes the
  descriptor from packaged resources, and keeps exact R8 suppressions limited to that desktop SPI.
- Tightened release/SBOM fail-closed behavior. Unsigned aggregate `:app:assemble --dry-run`,
  `:app:build --dry-run`, and `:app:bundle --dry-run` now fail with the release signing/trust
  material error, while `:app:dependencies --configuration releaseRuntimeClasspath --dry-run`
  still succeeds for dependency audit work.
- Fixed stale cached update manifests. `Manifest` now stores `expiresAt`, can be revalidated
  after parse time, `UpdateModel.checkForUpdate()` clears stale cached manifest state on failed
  checks, and `UpdateModel.update()` refuses expired cached manifests before enqueueing downloads.
- Verification passed:
  focused `ManifestTest` and `SecurityHardeningTest`;
  targeted connected `SourceModelGenerationFailureTest` and `VpnModelCacheInvalidationTest`
  (3 tests);
  aggregate unsigned release dry-run gates for `assemble`, `build`, and `bundle`;
  dependency dry-run for `releaseRuntimeClasspath`;
  final signed `:app:assembleRelease --rerun-tasks --warning-mode all` with the dnsjava strip
  task executed and no `InetAddressResolverProvider`/R8 warning matches;
  `jar tf` checks confirmed no dnsjava desktop resolver descriptor in merged release Java
  resources or the release APK; release APK SHA-256
  `4173C6885446680F85E24133EE65E6875168EF8B8B36528B60F1CFBFCD731BDA`;
  final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac`;
  lint XML issue count `0`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived code/assets and third-party asset provenance; CycloneDX still emits its
  configuration-time resolution warning; dependency verification is still hash-only rather than
  signature/trusted-key backed; domain checker root-vs-VPN integration coverage is still thin;
  FilterLists worker coverage remains thin; broad ProGuard suppressions still need ownership
  tightening; and `scripts/check-license-boundary.ps1` should be expanded to catch more MIT
  wording variants and report all matches before failing.

## Review - 2026-06-12 Goal Continuation 21
- Waited for all three focused reviewers to finish before closing them. No unfinished expert was
  closed. The reviewers covered FilterLists subscribe-all coverage, domain-checker runtime truth,
  and license-boundary behavior.
- Fixed domain-checker root/VPN runtime truth mismatch. Root mode now resolves one hostname using
  root-hosts export semantics: exact runtime row first, then a same-host suffix row materialized
  as a root hosts-file entry, without traversing parent suffixes. VPN mode still uses suffix
  traversal. `DomainCheckerRuntimeTruthTest` now proves a suffix rule blocks the base host in
  root mode, does not block the child host in root mode, and blocks both in VPN mode.
- Strengthened the MIT/GPL boundary guard. `scripts/check-license-boundary.ps1` now scans
  top-level `Resources`, catches broader release-claim phrasing such as released/distributed/
  available under MIT and MIT terms wording, and keeps detailed stderr reporting. Mirrored the
  claim paths and regex intent into `scripts/check-license-boundary.sh` to avoid stale tooling.
- Added behavior-level license coverage. `SecurityHardeningTest` now creates a temporary fixture
  repo, runs the real PowerShell boundary script, and asserts that docs, metadata, and Resources
  MIT claims fail while both `MIT claims:` and `GPL blockers:` are reported before exit.
- Refactored FilterLists subscribe-all candidate handling into a tested helper. Cached and
  network-resolved candidates now share the same compatibility, URL-quality, duplicate, and source
  construction path. Resolver failures now increment the no-url skipped count instead of dropping
  out of the final summary. `FilterListsSubscribeAllWorkerTest` covers mixed classification and
  preexisting duplicate behavior without WorkManager/network fakes.
- Verification passed:
  `.\scripts\check-license-boundary.ps1`;
  focused `SecurityHardeningTest`;
  focused `FilterListsSubscribeAllWorkerTest`;
  targeted connected `DomainCheckerRuntimeTruthTest`;
  final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac`;
  lint XML issue count `0`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived code/assets and third-party asset provenance; CycloneDX still emits its
  configuration-time resolution warning; dependency verification is still hash-only rather than
  signature/trusted-key backed; broad ProGuard suppressions still need ownership tightening; the
  FilterLists subscribe-all worker still needs end-to-end WorkManager/Room coverage; and broader
  manual product/security release review is still required before claiming market-leading quality.

## Review - 2026-06-12 Goal Continuation 22
- Waited for the release-chain reviewer to finish before closing it, then closed all completed
  reviewers only after consuming their findings. No unfinished expert was closed.
- Added Room-backed connected coverage for FilterLists subscribe-all persistence. The new
  `FilterListsSubscribeAllWorkerRoomTest` proves compatible sources are inserted enabled, rescue
  flags stay disabled, existing URLs are counted as already subscribed, unsupported syntaxes are
  skipped, and null URLs are counted as no-url skips.
- Refactored `FilterListsSubscribeAllWorker` around a package-private `SubscribeAllRecorder` so
  `doWork()` still owns network, preferences, notifications, progress, and update enqueueing,
  while Room writes and final output counters share one tested path.
- Fixed the CycloneDX Gradle configuration-time resolution warning. `cyclonedxDirectBom` now
  clears the task input dependency file collection and disables stale up-to-date reuse, leaving
  release-runtime dependency resolution in the task action. Verified `:app:generateSbom` with
  `--warning-mode fail`.
- Upgraded dependency verification from hash-only to signature-aware metadata with trusted keys
  and explicit SHA-256 fallback reasons, then added a security guard for the signed verification
  mode. Remaining release-chain gap: no checked-in Gradle verification keyring yet, so dependency
  verification is stronger but not fully hermetic.
- Verification passed:
  red/green Android test compile for the new recorder seam;
  targeted connected `FilterListsSubscribeAllWorkerRoomTest`;
  focused `FilterListsSubscribeAllWorkerTest`;
  focused `SecurityHardeningTest.atk34_gradleSbomTaskEmitsCycloneDxForRuntimeClasspath`;
  `:app:generateSbom --warning-mode fail` with dummy release trust properties;
  final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail`;
  `scripts/check-license-boundary.ps1`;
  lint XML issue count `0`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived code/assets and third-party asset provenance; dependency verification still needs a
  checked-in keyring/keyserver policy for hermetic release reproducibility; broad ProGuard
  suppressions still need ownership tightening; the FilterLists worker still lacks a full
  WorkManager `doWork()` fakeable end-to-end test; and broader manual product/security release
  review is still required before claiming market-leading quality.

## Review - 2026-06-12 Goal Continuation 23
- Spawned focused reviewers for ProGuard ownership, WorkManager `doWork()` fakeability, and
  MIT/release boundary gaps. All spawned reviewers completed and were closed only after their
  findings were consumed.
- Hardened dependency verification reproducibility by exporting and checking in Gradle's
  dependency verification public keyring files:
  `gradle/verification-keyring.gpg` and `gradle/verification-keyring.keys`. Added
  `SecurityHardeningTest` coverage that requires the binary keyring, armored key dump, and real
  PGP public-key material. Verified strict `releaseRuntimeClasspath` dependency resolution after
  export.
- Wired the license-boundary guard into the tagged release workflow before the signed APK build.
  `fork-release-apk.yml` now runs `scripts/check-license-boundary.ps1`, and the release workflow
  security test proves the guard is present before `:app:assembleRelease`.
- Tightened app-owned ProGuard/R8 rules. Removed the blanket ContentProvider keep, blanket
  `com.google.**` and `io.sentry.**` suppressions, duplicated OkHttp optional-platform
  suppressions, and the broad `java.net.spi.**` suppression. Kept only the exact dnsjava Java
  desktop resolver SPI classes and the OkHttp public-suffix keepnames rule. The security test now
  rejects the broad rules and requires the exact dnsjava classes.
- Verified release R8 with a temporary signed release build. `:app:assembleRelease --rerun-tasks`
  completed under strict dependency verification after the ProGuard cleanup. Log and artifact
  scans found no `Missing class`, `R8`, `InetAddressResolverProvider`, broad optional-platform, or
  removed dnsjava SPI descriptor matches. Release APK SHA-256:
  `29DCE986426E0AF27B5F5EDC0AEC2D43DDE139E4DA167EFBC2E726F35C94506C`.
- Verification passed:
  red/green `SecurityHardeningTest.atk34_dependencyVerificationChecksInPublicKeyring`;
  red/green `SecurityHardeningTest.atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom`;
  red/green `SecurityHardeningTest.atk34_releaseBuildStripsDnsjavaDesktopResolverSpi`;
  `:app:dependencies --configuration releaseRuntimeClasspath --dependency-verification=strict`;
  `:app:generateSbom --warning-mode fail --dependency-verification=strict`;
  release `:app:assembleRelease --rerun-tasks --dependency-verification=strict`;
  final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  `scripts/check-license-boundary.ps1`;
  lint XML issue count `0`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived code/assets and third-party asset provenance; dependency verification still has
  ignored keys from unavailable keyservers and no explicit keyserver-disable policy; the
  FilterLists worker still lacks the full fakeable WorkManager `doWork()` test seam identified by
  the reviewer; release/source artifact license BOM proof is still incomplete; and broader manual
  product/security release review is still required before claiming market-leading quality.

## Review - 2026-06-12 Goal Continuation 24
- Answered the slow-run concern by checking completed Android test reports instead of rerunning
  unnecessarily. The full `FilterListsSubscribeAllWorkerDoWorkTest` instrumentation class had
  already completed on `adaway-api34` with 2 tests, 0 failures, and 0 errors.
- Added WorkManager-level `doWork()` coverage for FilterLists subscribe-all. The new
  `FilterListsSubscribeAllWorkerDoWorkTest` runs the real worker with fakeable dependencies and
  proves compatible new lists are subscribed, existing URLs are counted as already subscribed,
  unsupported syntaxes are skipped without details fetches, no-download-URL lists are counted, the
  update service is enqueued once, and directory list fetch failures return `Retry` without writes.
- Added a narrow fakeable seam to `FilterListsSubscribeAllWorker`: production still uses the real
  Room recorder, FilterLists API, shared preferences, and `SourceUpdateService`, while tests can
  supply a `DirectoryClient`, recorder, cache prefs, and update enqueue hook without network or
  WorkManager scheduler side effects.
- Consumed and closed both focused sidecar reviewers only after they finished. The artifact/license
  reviewer found the current license guard is a text-policy tripwire, not yet a full source/APK/SBOM
  artifact BOM gate. The dependency reviewer found the safe immediate hardening was to disable
  Gradle PGP keyserver lookups while leaving ignored keys intact.
- Hardened dependency verification policy by adding `<key-servers enabled="false"/>` to
  `gradle/verification-metadata.xml`. `SecurityHardeningTest` now asserts keyservers are disabled
  and no ad-hoc keyserver URLs are pinned.
- Verification passed:
  red `:app:compileDebugAndroidTestJavaWithJavac` before the worker test seam;
  green `:app:compileDebugAndroidTestJavaWithJavac`;
  connected `FilterListsSubscribeAllWorkerDoWorkTest`;
  connected `FilterListsSubscribeAllWorkerRoomTest`;
  focused JVM `FilterListsSubscribeAllWorkerTest`;
  strict Android test compile;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  strict `SecurityHardeningTest`;
  strict signed `:app:assembleRelease :app:generateSbom` with a disposable local keystore and dummy
  update manifest public key;
  `scripts/check-license-boundary.ps1`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived code/assets and third-party asset provenance; release/source artifact BOM proof still
  needs source-entry, APK zip/resource, and SBOM notice checks; dependency verification still has
  ignored keys that should only be removed after separate metadata/keyring regeneration and strict
  release proof; and broader manual product/security release review is still required before
  claiming market-leading quality.

## Review - 2026-06-12 Goal Continuation 25
- Spawned two focused read-only reviewers for the artifact license/BOM guard and adversarial QA.
  Both completed and were closed only after their findings were consumed. Their main findings were
  that filesystem-only scans miss tracked source archive blockers, dependency MIT notices must not
  be treated as product MIT relicensing, APK resource names require `aapt`, the Bash guard should
  not duplicate policy, and the release workflow needs a post-artifact gate before attestation.
- Replaced the license boundary guard with a structured PowerShell implementation. It now supports
  `-SourceMode WorkingTree|GitTracked`, optional `-ApkPath`, optional `-SbomPath`, and
  `-StrictArtifacts`; scans source entries for forbidden app assets; inspects APK zip entries for
  localhost/test/icon/native packet-capture/webserver/TLS blockers; uses `aapt dump resources` for
  packaged branded/provenance-sensitive resources; parses CycloneDX SBOM components; and fails
  closed when strict artifact mode omits or cannot read the APK/SBOM.
- Replaced `scripts/check-license-boundary.sh` with a thin PowerShell wrapper so Windows/Linux CI
  policy cannot drift between duplicated implementations.
- Wired the tagged release workflow to run a second strict artifact boundary check after APK
  selection and identity verification, using `${{ steps.apk.outputs.path }}` and
  `${{ steps.sbom.outputs.path }}`, before provenance/SBOM attestation and upload.
- Extended `THIRD_PARTY_LICENSES.md` runtime notices for generated-SBOM coverage by adding Okio and
  JNA rows alongside the existing AndroidX, dnsjava, Guava, libsu, Material, OkHttp, Pcap4J,
  Sentry/sentrystub, SLF4J, and Timber notices.
- Added real-script fixture coverage in `SecurityHardeningTest`: MIT/GPL claim reporting,
  forbidden source asset failure, APK zip blocker plus missing JNA SBOM notice failure, legitimate
  MIT dependency notice pass-through, and release workflow ordering for the new post-artifact gate.
- Verification passed:
  `scripts/check-license-boundary.ps1`;
  artifact mode `scripts/check-license-boundary.ps1 -ApkPath <current release APK> -SbomPath app/build/reports/sbom/adaway.cdx.json -StrictArtifacts`;
  uncached strict `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict --rerun-tasks`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings. The Bash wrapper could
  not be executed in this Windows environment because `bash.exe` is the WSL launcher and no WSL
  distribution is installed.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived app/VPN code, packaged brand assets, and third-party asset provenance; a strict
  `-SourceMode GitTracked` release check will only pass once tracked source deletions are actually
  part of the branch state; dependency verification still has ignored keys that need a separate
  metadata/keyring cleanup; and broader manual product/security release review is still required
  before claiming market-leading quality.

## Review - 2026-06-12 Goal Continuation 26
- Spawned focused read-only reviewers for packaged asset replacement risk and UX/product impact.
  Both completed and were closed only after their findings were consumed. They confirmed that the
  core logo/adaptive icon layers could be safely replaced under stable resource names, while
  PayPal/GitHub mark cleanup should use generic renamed resources rather than same-name trademark
  replacements.
- Replaced the packaged old bird/logo visual system with original project-created geometric DNS
  shield vectors in `logo.xml`, `icon_foreground_red.xml`, `icon_foreground_white.xml`, and
  `icon_monochrome.xml`. The stable resource names keep About, notifications, QS tile, launcher,
  onboarding, Home, and FAB references intact.
- Removed the remaining density PNG launcher fallbacks (`mipmap-*/icon.png`) after proving resource
  compilation succeeds with the adaptive icon XML on the app's minSdk 26 baseline.
- Removed packaged PayPal/GitHub mark drawables and replaced their UI references with generic
  project-created `ic_support_24dp` and `ic_code_host_24dp` vectors. No stale
  `@drawable/paypal` or `@drawable/ic_github_24dp` references remain.
- Updated `THIRD_PARTY_LICENSES.md` and `docs/mit-relicensing-plan.md` to record that old packaged
  launcher/app logo/adaptive icon artwork and packaged PayPal/GitHub mark drawables have been
  replaced. The broader GPL app/VPN code remains the controlling MIT blocker.
- Tightened `scripts/check-license-boundary.ps1` so packaged PayPal/GitHub resource names are now
  forbidden regressions instead of merely documented notice items. `SecurityHardeningTest` now
  asserts that this regression guard remains present.
- Verification passed:
  `:app:mergeDebugResources :app:processDebugResources --dependency-verification=strict`;
  strict signed `:app:assembleRelease :app:generateSbom` with a disposable local keystore and dummy
  update manifest public key;
  artifact mode `scripts/check-license-boundary.ps1 -ApkPath app/build/outputs/apk/release/app-release.apk -SbomPath app/build/reports/sbom/adaway.cdx.json -StrictArtifacts`;
  `aapt dump resources` showing new `ic_support_24dp`, `ic_code_host_24dp`, and DNS shield
  resources while old `paypal`/`ic_github_24dp` names are absent;
  uncached strict `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict --rerun-tasks`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  `scripts/check-license-boundary.ps1`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived app/VPN code and remaining source-only/historical asset provenance; a strict
  `-SourceMode GitTracked` release check will only pass once tracked source deletions are actually
  part of the branch state; dependency verification still has ignored keys that need a separate
  metadata/keyring cleanup; screenshots/accessibility review for the new icon system is still
  needed; and broader manual product/security release review is still required before claiming
  market-leading quality.

## Review - 2026-06-12 Goal Continuation 27
- Spawned focused read-only reviewers for source-distribution policy and adversarial QA. Both
  completed and were closed only after their findings were consumed. Reviewers confirmed that the
  deleted reusable app assets and old SunOS libpcap objects should be committed as deletions, and
  that `Resources/`, `tcpdump/`, and `webserver/` can be excluded from generated app-release
  source archives while remaining available in normal git checkouts for auditability.
- Removed obsolete binary libpcap object files from the working source tree:
  `tcpdump/jni/libpcap/SUNOS4/nit_if.o.sparc`,
  `tcpdump/jni/libpcap/SUNOS4/nit_if.o.sun3`, and
  `tcpdump/jni/libpcap/SUNOS4/nit_if.o.sun4c.4.0.3c`.
- Added `.gitattributes` export-ignore rules for historical `Resources/`, dormant `tcpdump/` and
  `webserver/`, and the removed app asset paths (`icon.svg`, reusable localhost cert/key, and
  `test.html`) so generated release source archives are scoped to the Android app release.
- Updated `THIRD_PARTY_LICENSES.md` and `docs/mit-relicensing-plan.md` to document the release
  source-archive scoping and removal of the old SunOS object files without overstating this as MIT
  relicensing.
- Tightened `scripts/check-license-boundary.ps1` so the SunOS object-file guard catches actual
  filenames like `nit_if.o.sparc`, not only names ending exactly in `.o`.
- Strengthened the release workflow pre-build license check to run
  `scripts/check-license-boundary.ps1 -SourceMode GitTracked`, so committed source blockers are
  caught before the APK/SBOM build as well as in the later artifact-aware gate.
- Added `SecurityHardeningTest` coverage for `.gitattributes` export-ignore rules, explicit absence
  of the deleted app asset files and SunOS object files, and a real
  `git archive --worktree-attributes` ZIP inspection that rejects `Resources/`, `tcpdump/`,
  `webserver/`, deleted app assets, and `SUNOS4/nit_if.o*` entries.
- Verification passed:
  `git check-attr export-ignore` for `Resources`, `tcpdump`, `webserver`, nested tcpdump object
  files, and removed app assets;
  generated `git archive --worktree-attributes` ZIP inspection with zero forbidden entries;
  `scripts/check-license-boundary.ps1`;
  uncached strict `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict --rerun-tasks`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  and `git diff --check`.
- Diagnostic expected-red check: `scripts/check-license-boundary.ps1 -SourceMode GitTracked`
  currently fails in this dirty worktree on the seven deleted-but-not-yet-committed blockers
  (`app/src/main/assets/icon.svg`, `localhost-2410.crt`, `localhost-2410.key`, `test.html`, and
  the three SunOS object files). This is the intended release behavior; it will pass only after
  those deletions and `.gitattributes` are part of the committed branch state.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived app/VPN code and any remaining source/provenance items outside the app artifact;
  dependency verification still has ignored keys that need a separate metadata/keyring cleanup;
  screenshots/accessibility review for the new icon system is still needed; and broader manual
  product/security release review is still required before claiming market-leading quality.

## Review - 2026-06-12 Goal Continuation 28
- Spawned and consumed three read-only reviewers before closing them:
  dependency/release-chain, UI/accessibility QA, and devil-advocate release/security. The reviewers
  confirmed the next high-risk gaps: untracked dependency-verification files, stale ignored-key
  entries for keyring-present subkeys, CI jobs without explicit strict dependency verification,
  source-boundary proof still needing committed/index state, and UI/accessibility issues around
  unlabeled switches, onboarding scalability, RTL polish, and 32dp update controls.
- Hardened dependency verification by removing stale ignored-key entries for
  `32EE5355A6BC6E42` and `E88979FB9B30ACF2` after verifying both are present as exported keyring
  subkeys in `gradle/verification-keyring.keys`.
- Added scoped trusted-key coverage for the reviewed full signing fingerprints:
  `0F06FF86BEEAF4E71866EE5232EE5355A6BC6E42` for Android build-tool, data binding,
  annotation, and AndroidX instrumentation-test artifacts; and
  `A5F483CD733A4EBAEA378B2AE88979FB9B30ACF2` for the resolved AndroidX runtime families
  (`annotation`, `appcompat`, `concurrent`, `constraintlayout`, `core`, `fragment`, `paging`,
  `profileinstaller`, `startup`, and `transition`).
- Strengthened `SecurityHardeningTest` so dependency verification now asserts disabled
  keyservers, checked-in keyrings, bounded unresolved fallback counts, absence of the two stale
  ignored-key declarations, and the reviewed trust scopes for the two Google/AndroidX signing
  subkeys.
- Made normal CI and CodeQL Gradle calls explicit about dependency verification by adding
  `--dependency-verification=strict` to unit tests, lint, debug assemble, Sonar, connected
  androidTest, and the CodeQL autobuild replacement. Release APK/SBOM workflow strict verification
  remains in place.
- Verification passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict --rerun-tasks`;
  `:app:dependencies --configuration releaseRuntimeClasspath --dependency-verification=strict`;
  signed `:app:generateSbom --warning-mode fail --dependency-verification=strict` with a disposable
  local keystore and dummy update-manifest public-key property;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  `scripts/check-license-boundary.ps1`; and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Current dependency-verification state is materially better but not complete: `gradle/verification-metadata.xml`
  still has 31 unresolved `<ignored-key>` entries and 345 `reason="A key couldn't be downloaded"`
  checksum fallbacks. Those remain intentionally bounded by tests until a controlled
  `--write-verification-metadata pgp,sha256 --export-keys` regeneration and manual signer review
  can remove them safely.
- Remaining release blockers called out by reviewers: the verification metadata/keyring files are
  still untracked until added to branch state; `scripts/check-license-boundary.ps1 -SourceMode GitTracked`
  will remain red until `.gitattributes` and the source deletions are staged/committed; MIT remains
  blocked by GPL-derived shipped app/VPN code; VPN `allowBypass()` and encrypted-DNS coverage still
  weaken market-leading leak-resistance claims; artifact/license BOM coverage is useful but not a
  complete legal review; and the next UI slice should start with accessibility labels/state,
  onboarding scrollability, 48dp update controls, RTL-safe layout/drawables, and a broader UX matrix.

## Review - 2026-06-12 Goal Continuation 29
- Continued the UI/accessibility slice from the reviewer findings: FilterLists and catalog row
  switches now expose per-row accessible names/state, catalog cards expose checked state, onboarding
  method cards are checkable and announce selected/not-selected state, and onboarding is scrollable
  for smaller or larger-font devices.
- Replaced destructive/progress-obscuring UI patterns in the Home surface: update pause/resume and
  stop controls are now 48dp, long-running Home progress no longer stacks indefinite snackbars over
  status cards, and the main protection FAB is a bounded Material FAB that hides while update
  progress surfaces are active.
- Cleaned obvious RTL hazards: removed `layout_marginLeft`/`layout_marginRight` and
  `rotation="-90"` residues in the scanned UI paths, converted More and add-options end chevrons to
  an auto-mirrored drawable, and left the down-arrow expand indicator as the only remaining
  `ic_expand_more_24` layout use.
- Fixed two device-only null-manifest crashes found by the UX matrix: Home version text now handles
  a missing update manifest, and UpdateActivity has an explicit unavailable state instead of
  dereferencing a null manifest.
- Hardened `scripts/run-ux-matrix.ps1`: debug and androidTest APKs are built with strict dependency
  verification, and instrumentation output now fails on crash/stack/failure markers instead of
  relying on process exit code alone.
- Verification passed:
  focused `:app:mergeDebugResources :app:compileDebugJavaWithJavac --dependency-verification=strict`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict`;
  connected `scripts/run-ux-matrix.ps1 -OutputDir app/build/reports/ux-matrix` on `emulator-5554`
  for baseline, 1.3 font scale, and 1.3 font scale RTL variants, producing 18 screenshots;
  `scripts/check-license-boundary.ps1`;
  RTL residue scan for left/right margins and `rotation="-90"`;
  and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived shipped app/VPN code and legal/provenance clearance; `-SourceMode GitTracked` remains
  red until `.gitattributes` and source deletions are staged/committed; dependency verification
  still has bounded unresolved ignored-key/checksum fallback entries; and leak-resistance still
  needs deeper VPN bypass/encrypted-DNS enforcement review before any market-leading claim.

## Review - 2026-06-12 Goal Continuation 30
- Waited for the remaining VPN leak-resistance reviewer instead of closing it early. The final
  reviewer output confirmed the app-managed VPN bypass toggle is now default-off, and identified
  the next high-risk truthfulness issues: Home could trust premature/stale VPN state, Private DNS
  unreadable state looked clean, and the encrypted-DNS copy overclaimed static common-DoH routes.
- Hardened VPN running truth: `VpnService.startVpn()` now persists `STARTING`, while persisted
  `RUNNING` is written only through the worker status path after `VpnBuilder.establish()` succeeds.
  `VpnServiceControls.isTunnelEstablished()` now requires post-establish `RUNNING` plus a VPN
  network, and on Android 12+ rejects VPN networks owned by another app.
- Made Home leak status more honest: unreadable Private DNS is a risk/unknown state, a running VPN
  no longer makes encrypted DNS look fully safe, and the Home copy now says common DoH providers are
  routed while other encrypted DNS may bypass filtering.
- Strengthened the release/license boundary guard with a `-StrictSourceArchive` mode that creates a
  `git archive --worktree-attributes` source ZIP and fails if release archives contain historical
  `Resources/`, dormant `tcpdump/` or `webserver/`, removed app assets, or old SunOS libpcap object
  entries.
- Wired `-StrictSourceArchive` into both tagged-release license-boundary checks, before APK build
  and again before release attestation/upload alongside the APK and SBOM artifact checks.
- Added focused regression coverage: `LeakStatusTest` now locks Private DNS unknown and
  encrypted-DNS coverage-limit risks; `SecurityHardeningTest` now checks post-establish VPN truth,
  foreign-VPN owner rejection, truthful leak copy, release workflow `-StrictSourceArchive` use,
  strict source-archive script support, and a real fixture git repo proving a dormant `webserver/`
  archive entry fails the boundary script.
- The license/MIT reviewer confirmed MIT is still not a direct switch: current GPL-derived app code
  and source provenance remain blockers. The dependency-verification reviewer confirmed the current
  ignored-key/keyring state is bounded by tests and should not be blindly pruned without controlled
  metadata regeneration.
- Verification passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  focused `:app:testDebugUnitTest --tests org.adaway.ui.home.LeakStatusTest --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  and `git diff --check`.
- `git diff --check` reports only Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is still not complete; MIT remains blocked
  by GPL-derived material and legal/provenance clearance; `-SourceMode GitTracked` remains expected
  red until deletions and `.gitattributes` are staged/committed; dependency verification still has
  bounded unresolved ignored-key/checksum fallback entries; and full encrypted-DNS enforcement still
  needs deeper policy work beyond static common-provider routes.

## Review - 2026-06-12 Goal Continuation 31
- Spawned and waited for three focused reviewers:
  filter/runtime correctness, performance/update pipeline, and UI/product QA. The delayed reviewer
  was allowed to finish before closeout, per the user correction captured in `tasks/lessons.md`.
- Hardened full-update generation activation: failed enabled sources now only migrate from the
  previous active generation when prior active coverage actually exists, preventing a mixed
  success-plus-never-synced-failure run from activating an incomplete generation.
- Added a no-change fast path for all-unchanged URL updates: 304-only runs now clean the unused
  import generation and skip carry-forward copies, generation activation, `host_entries` sync, and
  final cleanup work.
- Fixed AI domain diagnostics to use the same runtime truth as Domain Checker: root-mode checks now
  resolve through `HostEntryDao.resolveRootEntry()` instead of exact-only lookup, while VPN behavior
  remains suffix-aware.
- Fixed the Sources screen FAB overlap by reserving bottom and end list padding, then reran the UX
  screenshot matrix to confirm rows are no longer hidden by the floating action button.
- Verification passed:
  focused `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  connected `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest,org.adaway.model.source.SourceModelGenerationFailureTest --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  resource/Java gate `:app:mergeDebugResources :app:compileDebugJavaWithJavac --dependency-verification=strict`;
  `scripts/run-ux-matrix.ps1 -OutputDir app/build/reports/ux-matrix-continuation31b`;
  and `git diff --check`.
- `git diff --check` reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; `-SourceMode GitTracked` is expected red
  until deletions and `.gitattributes` are staged/committed; Discover presets still add sources
  without activating update/apply and still use hard-coded snackbar text; My Lists/Sources is not
  yet first-class navigation; `retrieveHostsSources()` and `retrieveHostsSource()` bypass the
  full-update concurrency gate, and the single-source path still mutates shared
  `currentImportGeneration`; global dedup memory use is not yet 5M-rule quality; and stronger
  connected tests are still needed for mixed success/failure full updates, public single-source
  failure, and a real all-304 no-change update.

## Review - 2026-06-12 Goal Continuation 32
- Spawned and waited for three focused reviewers:
  update concurrency/generation, Discover preset UX/product flow, and update-pipeline
  performance/scalability. All three were closed only after completion.
- Hardened update concurrency: every public update entry point is now behind the shared
  `updateInProgress` gate, colliding updates throw the new `UPDATE_IN_PROGRESS` host error instead
  of silently returning success, and the gate finalizer is shared so workers do not apply or mark
  schedules as run after a no-op collision.
- Reduced generation race exposure: full-update file/URL parse workers, single-source URL/file
  parses, and carry-forward calls now receive the intended import/staging generation explicitly
  instead of relying on the mutable `currentImportGeneration` field during worker execution.
- Fixed stale runtime truth on disabled-source no-change updates: disabled source rows are cleared,
  zero-enabled-source updates rebuild `host_entries`, and all-304 no-change fast-path runs rebuild
  runtime entries when disabled-source changes affected the runtime table.
- Fixed Discover quick preset activation: preset chips now insert missing sources, re-enable
  existing disabled preset sources, treat never-fetched/failed existing preset sources as needing
  update, enqueue `SourceUpdateService.enqueueUpdateNow()`, and show localized pluralized snackbar
  copy instead of raw English strings.
- Added regression coverage:
  `Generation304MigrationTest` now guards update-entry serialization, explicit generation passing,
  update-in-progress failure, no-change fast-path runtime rebuild, and disabled-source cleanup;
  `SourceModelGenerationFailureTest` now covers zero-enabled-source runtime sync; and
  `DiscoverPresetSubscriptionTest` guards preset update enqueue, disabled-source re-enable, and
  resource-backed snackbar copy.
- Verification passed:
  focused `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  resource/Java `:app:mergeDebugResources :app:compileDebugJavaWithJavac --dependency-verification=strict --stacktrace`;
  connected `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest --dependency-verification=strict --stacktrace`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; `-SourceMode GitTracked` is expected red
  until deletions and `.gitattributes` are staged/committed; the legacy no-arg
  `retrieveHostsSources()` path still deserves deletion/delegation to remove the old in-place
  update implementation; generation activation plus `host_entries` rebuild is still not a single
  database finalizer transaction; global Java-heap dedup remains the main 1M-5M-rule scalability
  blocker and should move to SQL-backed import dedup; DiscoverCatalogFragment still has older
  selection-only preset behavior and hard-coded count formatting; My Lists/Sources is not yet
  first-class navigation; and stronger connected tests are still needed for mixed success/failure
  full updates, public single-source failure, and a real all-304 no-change update.

## Review - 2026-06-12 Goal Continuation 33
- Waited for and consumed the three focused sidecar reviewers that finished in this continuation:
  SQL dedup performance, Discover catalog UX, and generation/runtime finalizer atomicity. Closed
  only those completed reviewers after their findings were applied to the local plan.
- Retired the public no-arg `retrieveHostsSources()` runtime behavior by delegating it to the
  staged full-update path through `checkAndRetrieveHostsSources()`. The old body still exists behind
  an always-true delegation guard and remains cleanup debt, but public calls no longer run the
  legacy in-place update implementation.
- Fixed the remaining Discover catalog snackbar count formatting: added
  `filter_added_success_count` plurals, changed `DiscoverCatalogFragment` to use the plural
  resource, and added a source guard in `DiscoverPresetSubscriptionTest`.
- Implemented SQL-backed update dedup for the parser/update pipeline. Parser workers now hand
  parsed rows to the inserter, and the inserter uses an update-scoped SQL unique table keyed by
  `(type, kind, host, redirection)` before writing `hosts_lists`, removing the large Java
  `ConcurrentHashMap` pressure from the active full/single-source update paths.
- Device verification found that SQLite TEMP tables are connection-local under Android/Room and
  failed when `count()` used a different connection. The dedup table is now a normal internal table
  cleared at the start of each update run so parser/database connections share one uniqueness
  surface.
- Added connected coverage in `SourceLoaderPerformanceTest` proving cross-source dedup keeps one
  exact duplicate, preserves two redirect targets for the same host with different redirections,
  and reports the expected dedup count.
- Verification passed:
  focused `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  instrumentation compile `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  connected
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_deduplicatesAcrossSourcesAndKeepsRedirectTargets --dependency-verification=strict --stacktrace`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; `-SourceMode GitTracked` is expected red
  until deletions and `.gitattributes` are staged/committed; the old no-arg update body should be
  physically deleted after the delegation guard has soaked; generation activation plus
  `host_entries` rebuild is still not a single database finalizer transaction; SQL dedup still
  needs 100k/1M/5M benchmark evidence and rollback/carry-forward tests; DiscoverCatalogFragment
  still has older selection-only preset behavior; My Lists/Sources is not yet first-class
  navigation; and stronger connected tests are still needed for mixed success/failure full updates,
  public single-source failure, and a real all-304 no-change update.

## Review - 2026-06-12 Goal Continuation 34
- Spawned three focused reviewers for generation finalization, SQL dedup/carry-forward risk, and
  Discover UX. All three were allowed to finish and were closed only after their findings were
  consumed.
- Fixed the full-update P1 runtime-truth split: `HostEntryDao` now exposes
  `rebuildFromActiveGeneration()` as a pure DB rebuild primitive, while standalone
  `sync()` remains transactional. Full-update generation activation now runs
  `setActiveGeneration`, disabled-source cleanup, source metadata/error commits,
  inactive-generation cleanup, and `host_entries` rebuild in one Room transaction before VPN cache
  invalidation.
- Moved full-update freshness metadata out of parser/download workers. ETag, modification dates,
  generation-specific size, stale-error clearing, and download errors are now collected as pending
  source finalization records and applied only in the publish/abort transaction.
- Fixed stopped-update safety: if the user stops during the URL pipeline, the staging generation is
  cleaned and the method returns before no-change handling, carry-forward, or active generation
  finalization can publish a partial generation.
- Added/updated regression coverage: `Generation304MigrationTest` now guards pure runtime rebuild,
  atomic generation/runtime finalization, deferred source metadata publication, and stopped-update
  abort-before-activation; `SourceModelGenerationFailureTest` now includes a connected Android
  trigger test proving a runtime rebuild failure rolls back active generation, staged cleanup, and
  pending source metadata (ETag, dates, and size).
- Verification passed:
  focused `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  connected
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest --dependency-verification=strict --stacktrace`;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; `-SourceMode GitTracked` remains expected
  red until deletions and `.gitattributes` are staged/committed; multi-source targeted updates
  still promote individual sources before the final runtime sync; carry-forward still bypasses SQL
  dedup and can reintroduce duplicates between changed and 304/failed sources; carry-forward still
  treats metadata-with-zero-active-rows as safe; SQL dedup still needs rollback/carry-forward tests
  and 100k/1M/5M benchmark evidence; Discover FilterLists still has a misleading
  `Subscribe to all` switch partial-state bug and subscribed rows still open add-source flow instead
  of the existing source; My Lists/Sources is not yet first-class navigation; and stronger connected
  tests are still needed for mixed success/failure full updates, public single-source failure, and a
  real all-304 no-change update.

## Review - 2026-06-12 Goal Continuation 35
- Waited for the two continuation reviewers to finish before closing them. The SQL carry-forward
  reviewer confirmed the active P1s: failed-source carry-forward was happening before successful
  parses could mark SQL dedup state, and null redirections were being collapsed into empty-string
  identity. The Discover reviewer confirmed the next product P1s: partial `Subscribe to all`
  state appears checked, and subscribed FilterLists rows still open add-source flow.
- Fixed SQL carry-forward dedup identity. `update_seen_hosts` now uses
  `(type, kind, host, redirection_is_null, redirection_value)` so `NULL` and empty-string redirects
  do not collapse, and it is dropped at construction and in full-update `finally` to avoid stale
  scratch-table schema/data across update runs.
- Changed full-update failure handling so FILE, download, and parse failures defer carry-forward
  until all successful parses finish. The final deferred carry-forward loop uses the same
  `SqlUpdateDeduper`, so copied rows cannot preempt fresh rows from later successful sources.
- Reworked `copyUnseenSourceGeneration()` to copy one canonical exact old-generation row instead
  of aggregating `enabled`/`redirection`, preserving the copied row's real values while skipping
  rows already marked by successful parses.
- Updated connected coverage in `SourceLoaderPerformanceTest` for carry-forward dedup, redirect
  identity, and null-vs-empty redirection identity. Updated `Generation304MigrationTest` source
  guards to require deferred failed-source carry-forward through the SQL dedup surface.
- Verification passed:
  initial red focused guard
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --rerun-tasks --stacktrace`
  failed at `Generation304MigrationTest.java:113` before the source guard was updated;
  green focused
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  connected
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest --dependency-verification=strict --stacktrace`
  completed on `adaway-api34(AVD) - 14` with 5/5 completed, 1 requested-scale benchmark skipped,
  0 failed;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; Discover FilterLists still needs the
  partial/all/none subscription-state fix and subscribed-row click fix; SQL dedup still needs
  100k/1M/5M benchmark evidence and abort/stop cleanup stress coverage; multi-source targeted
  updates still promote individual sources before final runtime sync; My Lists/Sources is not yet
  first-class navigation; and `-SourceMode GitTracked` remains expected red until large deletes and
  `.gitattributes` are staged/committed.

## Review - 2026-06-12 Goal Continuation 36
- Fixed the Discover FilterLists P1 partial-state bug. Added `FilterListsSubscriptionState` with
  explicit `NONE`, `PARTIAL`, and `ALL` states; unsupported rows are ignored for master-state
  calculation, while compatible rows with missing/empty cached URLs are not counted as subscribed.
  The `Subscribe to all` switch is checked only for `ALL`, so partial subscriptions no longer
  present as a destructive remove-all state.
- Fixed subscribed-row navigation. Rows that are already subscribed now resolve the existing
  `hosts_sources` row by URL and open `SourceEditActivity.SOURCE_ID`; they no longer launch the
  add-source intent. If the cached URL is stale and the source is gone, the row clears local
  subscribed state and refreshes.
- Persisted resolved list URLs from single-row add/update flows into the same
  `filterlists_cache` mapping used by the bulk worker, so row state and master switch state survive
  fragment recreation outside the subscribe-all worker path.
- Added focused coverage:
  `FilterListsSubscriptionStateTest` covers none, partial, all, unsupported-row ignoring, and
  missing/empty cached URL behavior; `DiscoverPresetSubscriptionTest` source guards ensure the
  master switch uses `FilterListsSubscriptionState.ALL` and subscribed rows route to existing
  source edit instead of unconditional `onPick()`.
- Verification passed:
  initial red focused run
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.FilterListsSubscriptionStateTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --dependency-verification=strict --rerun-tasks --stacktrace`
  failed at compile with missing `FilterListsSubscriptionState`;
  green focused run of the same command;
  broad `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; SQL dedup still needs 100k/1M/5M benchmark
  evidence and abort/stop cleanup stress coverage; multi-source targeted updates still promote
  individual sources before final runtime sync; My Lists/Sources is not yet first-class navigation;
  stronger connected tests are still needed for mixed success/failure full updates, public
  single-source failure, and a real all-304 no-change update; and `-SourceMode GitTracked` remains
  expected red until large deletes and `.gitattributes` are staged/committed.

## Plan - 2026-06-12 Goal Continuation 37
- [x] Keep reviewers running in parallel for independent remaining domains: targeted-update
  finalization, SQL cleanup/benchmark risk, and My Lists/Sources navigation.
- [x] Fix targeted multi-source updates so changed sources stage independently and publish through
  one final transaction plus one runtime rebuild, not per-source active-generation promotion.
- [x] Add focused red/green guards proving the list update path no longer calls
  `promoteStagedSourceGeneration()` inside each per-source worker before final sync.
- [x] Run focused JVM/connected tests for `SourceModel`, then the broad Gradle, license, and diff
  gates sequentially.
- [x] Consume and close all reviewers only after findings are captured, then append the review
  evidence and remaining blockers.

## Review - 2026-06-12 Goal Continuation 37
- Waited for all three continuation reviewers to finish and closed them after consuming their
  findings. The targeted-update reviewer identified the P1 duplicate-id hazard in
  `retrieveHostsSources(List<Integer>)`: duplicate selected ids could promote the first staged
  update, clear it on the second duplicate, and rebuild runtime rows without the selected source.
  The same reviewer also flagged remaining P2s: disabled selected sources and URL 304 metadata
  still publish immediately in batch mode. The SQL reviewer confirmed benchmark/cleanup evidence
  gaps for SQL dedup and carry-forward at 100k/1M/5M scale. The navigation reviewer confirmed My
  Lists/Sources is still not first-class navigation and warned that a naive tab embed would lose
  `HostsSourcesFragment` menu actions.
- Fixed targeted multi-source changed-source publication. The public list update path now
  de-duplicates ids with insertion order preserved, collects per-source `TargetedSourceUpdate`
  records, and finalizes changed staged sources in one database transaction followed by one runtime
  rebuild. Per-source work in batch mode now parses into staging and returns the staged update
  instead of promoting each changed source independently.
- Added staged-update cleanup for failed targeted batches. If a batch throws after staging changed
  rows, the list update path clears those staging generations before rethrowing; runtime-only dirty
  work is still synced when needed. Targeted `SqlUpdateDeduper` scratch state is dropped in the
  per-source `finally` path.
- Added focused source-guard coverage in `Generation304MigrationTest` requiring the
  `TargetedSourceUpdate` worker return type, duplicate-id de-dupe, staged update collection,
  final batch publication through `finalizeStagedSourceGenerations()`, and no duplicate-prone
  `for (int sourceId : sourceIds)` loop.
- Verification passed:
  initial red focused guard
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest.sourceModel_defersTargetedMultiSourcePromotionUntilFinalRuntimeRebuild --dependency-verification=strict --rerun-tasks --stacktrace`
  failed before implementation;
  green focused guard with the same command;
  full JVM
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  connected
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest --dependency-verification=strict --stacktrace`
  passed on rerun after an emulator/network transient returned `NO_CONNECTION` on the first run;
  broad
  `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` exits 0 and reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; disabled selected sources and URL 304
  metadata still publish immediately in targeted batch mode; SQL dedup/carry-forward still need
  requested-scale 100k/1M/5M benchmark evidence and scratch cleanup assertions; My Lists/Sources is
  not yet a first-class bottom-nav destination; stronger connected tests are still needed for mixed
  success/failure full updates, public single-source failure, and a real all-304 no-change update;
  and `-SourceMode GitTracked` remains expected red until large deletes and `.gitattributes` are
  staged/committed.

## Plan - 2026-06-12 Goal Continuation 38
- [x] Fix the remaining targeted batch P2s from the reviewer: disabled selected sources and URL
  304 metadata must not publish inline during a multi-source batch.
- [x] Add red/green guards requiring targeted list updates to collect all deferred finalization
  work, not just staged row promotions.
- [x] Add connected rollback/no-rebuild coverage for targeted disabled cleanup and metadata-only
  finalization.
- [x] Run focused JVM, focused connected, broad Gradle, license, and diff gates.
- [x] Close completed sidecar explorers and capture their follow-up plans for SQL evidence and
  first-class Sources navigation.

## Review - 2026-06-12 Goal Continuation 38
- Fixed targeted batch finalization so deferred work now covers staged promotions, disabled-source
  cleanup, and metadata-only commits. `retrieveHostsSources(List<Integer>)` now collects
  `finalizationUpdates`, and `finalizeStagedSourceGenerations()` applies disabled cleanup and
  source metadata in the same finalization transaction that publishes changed rows.
- Disabled selected sources no longer clear `hosts_lists` rows or source properties inline in
  batch mode. Batch mode returns `TargetedSourceUpdate.disabled(sourceId)` and publishes cleanup
  through finalization; single-source update behavior still cleans and syncs immediately.
- URL 304 targeted batch updates no longer update ETag, modification dates, or size inline. They
  return `TargetedSourceUpdate.metadataOnly(...)`; metadata-only finalization applies the commit
  without forcing a runtime rebuild when no runtime rows changed.
- Added `SourceCommit.unchanged(...)` for 304 metadata and `TargetedSourceUpdate` variants for
  disabled and metadata-only work. Cleanup now ignores non-staged finalization records, so rollback
  cleanup does not delete unrelated active rows.
- Expanded `Generation304MigrationTest` to require deferred disabled cleanup and 304 metadata in
  targeted batches and to reject inline mutation in the batch worker. Expanded the connected
  `SourceModelGenerationFailureTest` with rollback coverage for disabled cleanup and no-runtime-
  rebuild coverage for metadata-only finalization. That connected test file is currently untracked
  in this worktree but present and executed successfully.
- Captured follow-up explorer output: SQL evidence should add a connected
  `SourceLoaderPerformanceTest` benchmark using real `SqlUpdateDeduper` plus scratch-table cleanup
  assertions; Sources navigation should use a wrapper `HostsSourcesTabFragment` with its own
  toolbar to preserve `hosts_sources_menu` actions instead of embedding `HostsSourcesFragment`
  directly.
- Verification passed:
  initial red focused guard
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest.sourceModel_defersTargetedMultiSourcePromotionUntilFinalRuntimeRebuild --dependency-verification=strict --rerun-tasks --stacktrace`
  failed at `Generation304MigrationTest.java:278`;
  green focused guard with the same command;
  full JVM
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --rerun-tasks --stacktrace`;
  connected
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest --dependency-verification=strict --stacktrace`
  ran 6 tests on `adaway-api34(AVD) - 14`;
  broad
  `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` exits 0 and reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; SQL dedup/carry-forward still need
  requested-scale 100k/1M/5M benchmark evidence and scratch cleanup assertions; My Lists/Sources is
  not yet a first-class bottom-nav destination; stronger connected tests are still needed for mixed
  success/failure full updates, public single-source failure, and a real all-304 no-change update;
  and `-SourceMode GitTracked` remains expected red until large deletes and `.gitattributes` are
  staged/committed.

## Plan - 2026-06-12 Goal Continuation 39
- [x] Add connected SQL dedup/carry-forward benchmark coverage that uses a real
  `SqlUpdateDeduper`, gated by `adawayPerfLines` for requested 100k/1M/5M runs.
- [x] Add small connected assertions that `update_seen_hosts` is absent after deduper use and
  failure paths.
- [x] Run a red focused connected check where practical, then green focused connected
  `SourceLoaderPerformanceTest`/`SourceModelGenerationFailureTest`.
- [x] Run the broad Gradle, license, and diff gates.
- [x] Append evidence, benchmark command guidance, and remaining blockers.

## Review - 2026-06-12 Goal Continuation 39
- Added connected SQL dedup/carry-forward benchmark coverage in
  `SourceLoaderPerformanceTest`. The new
  `sqlUpdateDedupAndCarryForward_requestedScale_recordsBenchmark` uses a real
  `SqlUpdateDeduper`, parses generated large-scale rules, seeds a second source's prior
  generation with overlapping plus unique rows, times `copyUnseenSourceGeneration()`, and prints
  parse/carry-forward/sync/root-cursor timings. It is gated by `adawayPerfLines`, so default
  connected runs skip it while requested 100k/1M/5M performance runs can execute it directly.
- Added connected cleanup assertions for the update-scoped scratch table. Direct deduper tests now
  call `deduper.drop()` in `finally` and assert `update_seen_hosts` is absent. The SourceModel
  all-failed full-update connected test also asserts the scratch table is absent after abort.
- Red/green evidence:
  initial connected red
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest --dependency-verification=strict --stacktrace`
  failed in both direct deduper tests with `expected:<0> but was:<1>` for
  `update_seen_hosts`; green focused run of the same command passed with two requested-scale
  benchmarks skipped by default.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest --dependency-verification=strict --stacktrace`;
  and the unskipped 100k SQL benchmark
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDedupAndCarryForward_requestedScale_recordsBenchmark -Pandroid.testInstrumentationRunnerArguments.adawayPerfLines=100000 --dependency-verification=strict --stacktrace`.
- 100k SQL benchmark metric:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=100000 inserted=90000 copied=3 dedupRows=90003 progressEvents=46 parseMs=35938 carryForwardMs=1 syncMs=367 rootCursorMs=439`.
- Broad verification passed:
  `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` exits 0 and reports only expected Windows LF-to-CRLF normalization warnings.
  Both connected test files are currently untracked in this worktree, but present and executed by
  Gradle.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; SQL dedup/carry-forward still needs the
  heavier 1M and 5M requested-scale benchmark evidence; My Lists/Sources is not yet a first-class
  bottom-nav destination; stronger connected tests are still needed for mixed success/failure full
  updates, public single-source failure, and a real all-304 no-change update; and
  `-SourceMode GitTracked` remains expected red until large deletes and `.gitattributes` are
  staged/committed.

## Plan - 2026-06-12 Goal Continuation 40
- [x] Add a red JVM contract test for first-class Sources navigation, wrapper toolbar ownership,
  and shared `HostsSourcesFragment` menu handling.
- [x] Implement a `Sources` bottom-nav tab using a wrapper fragment with its own toolbar and a
  child `HostsSourcesFragment`, preserving legacy `HostsSourcesActivity` behavior.
- [x] Route Home and More source-management entry points to the new tab instead of launching the
  legacy activity from the main shell.
- [x] Add a connected HomeActivity smoke test if local APIs make it practical without destabilizing
  onboarding state.
- [x] Run focused JVM/connected checks, broad Gradle, license, and diff gates, then record results.

## Review - 2026-06-12 Goal Continuation 40
- Added first-class Sources navigation. `bottom_nav_menu.xml` now includes `nav_sources` with a
  `Sources` label, and `HomeActivity` routes it to a new `HostsSourcesTabFragment`.
- Preserved existing source-management commands while embedding in the no-action-bar Home shell.
  `HostsSourcesTabFragment` owns a `MaterialToolbar`, inflates `hosts_sources_menu`, hosts the
  existing `HostsSourcesFragment` as a child, and delegates toolbar menu clicks to the child.
  `HostsSourcesFragment` keeps its legacy options-menu behavior for `HostsSourcesActivity` through
  the shared `handleMenuItem(...)` method.
- Routed Home and More source-management entry points to `R.id.nav_sources` instead of launching
  `HostsSourcesActivity` from the main shell. The legacy activity remains available for external
  or back-stack compatibility.
- Added `HomeNavigationSourcesContractTest` covering the bottom-nav item, wrapper layout/toolbar,
  shared menu handling, and Home/More navigation routes. Added
  `HomeNavigationSourcesInstrumentedTest`, which launches `HomeActivity`, selects Sources, and
  asserts the selected tab, wrapper fragment, child `HostsSourcesFragment`, toolbar, and
  `action_hosts_update_all` menu item exist.
- Red/green evidence:
  initial red
  `:app:testDebugUnitTest --tests org.adaway.ui.home.HomeNavigationSourcesContractTest --dependency-verification=strict --rerun-tasks --stacktrace`
  failed on missing `nav_sources`, missing wrapper file, and old Home/More activity launches;
  after implementation the same contract passed. A follow-up contract run briefly failed because
  the test asserted an overly-specific `return handleMenuItem(item);` string; the contract was
  corrected to assert delegation while preserving the safer `super.onOptionsItemSelected` fallback.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeNavigationSourcesInstrumentedTest --dependency-verification=strict --stacktrace`
  ran 1 test on `adaway-api34(AVD) - 14`.
- Broad verification passed:
  `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac --warning-mode fail --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`.
- `git diff --check` exits 0 and reports only expected Windows LF-to-CRLF normalization warnings.
  Git diff stats are noisy on some touched files because of line-ending normalization in this
  worktree.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; SQL dedup/carry-forward still needs the
  heavier 1M and 5M requested-scale benchmark evidence; stronger connected tests are still needed
  for mixed success/failure full updates, public single-source failure, and a real all-304
  no-change update; and `-SourceMode GitTracked` remains expected red until large deletes and
  `.gitattributes` are staged/committed.

## Plan - 2026-06-12 Goal Continuation 41
- [x] Run the requested 1M SQL dedup/carry-forward connected benchmark and capture the metric.
- [x] Wait for and consume the SourceModel edge-test reviewers before implementing follow-up
  coverage.
- [x] Add connected coverage for mixed full-update file success/failure, public single-source
  failure preservation, and real HTTPS 304 no-change behavior.
- [x] Add androidTest-only provider/HTTPS test infrastructure without touching production code.
- [x] Run focused connected tests, strict broad Gradle gates, and diff hygiene.

## Review - 2026-06-12 Goal Continuation 41
- Ran the 1M requested-scale connected SQL benchmark:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDedupAndCarryForward_requestedScale_recordsBenchmark -Pandroid.testInstrumentationRunnerArguments.adawayPerfLines=1000000 --dependency-verification=strict --stacktrace`.
- 1M SQL benchmark metric:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3 dedupRows=900003 progressEvents=451 parseMs=394855 carryForwardMs=12 syncMs=29513 rootCursorMs=17913`.
- Ran the 5M requested-scale connected SQL benchmark:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDedupAndCarryForward_requestedScale_recordsBenchmark -Pandroid.testInstrumentationRunnerArguments.adawayPerfLines=5000000 --dependency-verification=strict --stacktrace`.
- The host command timed out after 60 minutes, but Gradle/instrumentation kept running. Monitoring
  showed `org.adaway` still CPU-active, with a ~5GB benchmark DB and no crash. The instrumentation
  later completed and the Android XML result recorded 1 test, 0 failures, 0 errors, time
  `8031.965s`.
- 5M SQL benchmark metric:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=5000000 inserted=4500000 copied=3 dedupRows=4500003 progressEvents=2250 parseMs=2114157 carryForwardMs=77 syncMs=421967 rootCursorMs=4043095`.
- 5M finding: the dedup/carry-forward path itself remains cheap (`carryForwardMs=77`), but full
  runtime rebuild and root-output cursor work are not market-leading at this scale. `syncMs` is
  ~7.0 minutes and `rootCursorMs` is ~67.4 minutes on the emulator, so future performance work
  should target root-output streaming/query shape and large-table runtime rebuild cost rather than
  carry-forward SQL.
- Added connected full-update coverage in `SourceModelGenerationFailureTest` for a mixed FILE
  update where one content source fails and another succeeds. The test proves the failed source's
  previous active row is carried into the new active generation, the successful source publishes
  fresh rows, old successful rows are removed, runtime `host_entries` reflects final truth, source
  metadata/error state is correct, and `update_seen_hosts` is absent.
- Added connected public API coverage proving `retrieveHostsSource(sourceId)` reports
  `DOWNLOAD_FAILED` while preserving the prior active generation, active `hosts_lists` row, runtime
  block decision, and scratch-table cleanup.
- Added androidTest-only `TestHostsContentProvider` plus `AndroidManifest.xml` provider
  registration so FILE-source tests use Android's real `ContentResolver` path.
- Added `SourceModelHttpConditionalTest`, backed by HTTPS `MockWebServer`, `okhttp-tls`, and a
  test-injected trusted OkHttp client. It enqueues two 304 responses, verifies conditional request
  headers, installs a `host_entries` delete trigger to prove the all-304 path does not rebuild
  runtime rows, and asserts active generation/rows/source sizes remain unchanged.
- Pinned the new androidTest TLS dependency through Gradle dependency verification metadata with
  `--write-verification-metadata sha256`; strict verification then passed.
- Red/green evidence:
  initial mixed FILE connected test failed with `DOWNLOAD_FAILED` before the test provider existed;
  after adding the provider, the same focused test passed. The provider compile failed once on
  Android framework signatures (`openFile` exception type and final `requireContext()`), then
  passed after tightening the test provider contract.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest#fullUpdate_mixedFileSuccessAndFailure_activatesCompleteGeneration --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest#retrieveHostsSource_publicFailure_preservesActiveRowsAndReportsDownloadFailed --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelHttpConditionalTest --dependency-verification=strict --stacktrace`;
  and full connected `SourceModelGenerationFailureTest` ran 8 tests on `adaway-api34(AVD) - 14`.
- Broad verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug :app:lintDebug test --dependency-verification=strict --stacktrace`.
- `git diff --check` exits 0 and reports only expected Windows LF-to-CRLF normalization warnings.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; the 5M benchmark proves runtime/root-output
  hot paths still need another optimization pass; release signing/trust verification remains
  fail-closed unless real release properties are supplied; and `-SourceMode GitTracked` remains
  expected red until large deletes, untracked tests/docs/scripts, dependency-verification files,
  and `.gitattributes` are staged/committed.

## Plan - 2026-06-12 Goal Continuation 42
- [x] Revert the experimental root cursor `NOT IN` query rewrite after it regressed the 1M
  requested-scale benchmark.
- [x] Add and verify a covering `host_entries(kind, host, type, redirection)` root-export index
  with a schema migration.
- [x] Run migration and DB semantics tests after the covering-index change.
- [x] Re-run the 1M requested-scale SQL benchmark and compare against the clean baseline.
- [x] Implement the next runtime sync/root-output optimization pass if the covering index is not
  enough for market-leading performance.
- [x] Run focused performance/semantics verification and final diff hygiene.

## Review - 2026-06-12 Goal Continuation 42
- The local root cursor query-shape experiment that replaced the suffix `NOT EXISTS` anti-join
  with `NOT IN` was reverted. It preserved `SourceDbTest` semantics but regressed the 1M
  requested-scale benchmark from `rootCursorMs=17913` to `rootCursorMs=28934`, so it is not a
  candidate for this branch.
- Added schema version 14 with `MIGRATION_13_14`, rebuilding
  `index_host_entries_kind_host` as a covering index over `kind`, `host`, `type`, and
  `redirection`.
- Added migration coverage asserting the index columns and SQLite query plan use
  `COVERING INDEX index_host_entries_kind_host`.
- Verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest --dependency-verification=strict --stacktrace`
  ran 3 tests on `adaway-api34(AVD) - 14`.
- DB semantics verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest --dependency-verification=strict --stacktrace`
  ran 13 tests on `adaway-api34(AVD) - 14`.
- Covering-index 1M requested-scale benchmark passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDedupAndCarryForward_requestedScale_recordsBenchmark -Pandroid.testInstrumentationRunnerArguments.adawayPerfLines=1000000 --dependency-verification=strict --stacktrace`.
- Covering-index 1M metric:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3 dedupRows=900003 progressEvents=451 parseMs=310950 carryForwardMs=13 syncMs=31814 rootCursorMs=12956`.
- Finding: the covering index is a correct partial win for root export
  (`17913ms -> 12956ms`, about 28% faster on the 1M benchmark) but it is not sufficient by
  itself. Runtime sync was slightly worse (`29513ms -> 31814ms`), and the 5M benchmark still
  requires a deeper runtime rebuild/root-output optimization.
- Split runtime rebuild imports into user and active-generation source statements so sync no
  longer relies on the index-hostile `source_id = USER OR generation = active` predicate for the
  bulk blocked/redirected imports. Redirected rows are still applied source-first and user-second
  so user redirects preserve their previous conflict priority.
- Split-query 1M metric:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3 dedupRows=900003 progressEvents=451 parseMs=342106 carryForwardMs=23 syncMs=33767 rootCursorMs=11809`.
- Finding: splitting the import predicates alone did not fix sync; runtime rebuild remained slower
  than the clean baseline because the large cost is secondary-index maintenance while bulk-loading
  `host_entries`.
- Added a production-path `rebuildFromActiveGeneration(SupportSQLiteDatabase)` overload that can
  temporarily drop and recreate the secondary root-export index inside the surrounding SQLite
  transaction when there are no active allow rules. Plain DAO `sync()` remains available for
  small/direct callers and semantics tests.
- Updated SourceModel publish/sync paths and the connected performance benchmark to use the
  transaction-DB rebuild path. An initial deferred-index benchmark accidentally still called
  `hostEntryDao.sync()` and therefore reported `indexCreateMs=0`; the benchmark was corrected
  before accepting the result.
- Added connected coverage proving `rebuildFromActiveGeneration(db)` recreates
  `index_host_entries_kind_host` as a covering index after starting with that index absent.
- Deferred-index production-path 1M metric:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3 dedupRows=900003 progressEvents=451 parseMs=357364 carryForwardMs=8 syncMs=17968 rootCursorMs=12920`.
- Finding: the production-path deferred-index rebuild is worth keeping. Against the clean 1M
  baseline it improves sync from `29513ms` to `17968ms` and root cursor from `17913ms` to
  `12920ms`; `HostEntryDao.sync perf` shows the index recreation cost explicitly
  (`indexCreateMs=1417`). The remaining 5M root-output target still likely needs materialized
  root-export rows or a larger root-output representation change.
- Focused verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest --dependency-verification=strict --stacktrace`
  ran 3 tests;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest --dependency-verification=strict --stacktrace`
  ran 14 tests;
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#parseInsertSyncAndRootCursor_mixedRules10k_staysWithinBudget --dependency-verification=strict --stacktrace`;
  and connected `SourceModelGenerationFailureTest,SourceModelHttpConditionalTest` ran 9 tests.
- Broad verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene checks passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`. Diff check still reports only expected Windows LF-to-CRLF warnings.

## Plan - 2026-06-13 Goal Continuation 58
- [x] Let the in-flight 1M threshold benchmark finish and capture final evidence.
- [x] Revert the disproven `MATERIALIZED_RUNTIME_CACHE_MAX_ROWS = 1_500_000L`
  experiment back to the measured direct-root cutoff.
- [x] Keep the useful measurement and storage changes: seed-phase benchmark logging, v25
  `root_host_entries` `WITHOUT ROWID`, and production/test database storage alignment.
- [x] Re-run compile and focused connected correctness gates after the rollback.
- [x] Superseded by the later stage-backed root export during source import, which avoids the old
  direct 1M root-row copy path.

## Review - 2026-06-13 Goal Continuation 58
- The 1M threshold experiment finished after about 15 minutes and failed as expected. Final metric:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=1000000 exactBlockedRows=500000
  suffixBlockedRows=500000 exactAllowRules=10000 suffixAllowRules=5000 runtimeRows=980000
  rootRows=980000 materializedRuntimeCache=true syncMs=405649 rootCursorMs=27998`.
- The sync phase evidence shows why this path is wrong: `HostEntryDao.sync perf:
  importBlockedMs=252095 suffixIndexCreateMs=9522 allowMs=2170 indexCreateMs=13793
  rootExportMs=17968 statsMs=4609 totalMs=300466`.
- Restored `HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS` to `500_000L`; materializing the
  full runtime cache above that size is slower than the direct-root path on the current emulator.
- Compile verification passed after the rollback:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused connected verification passed after the rollback:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testLargeRuntimeSkipStillMaterializesRootExportRows,org.adaway.db.SourceDbTest#testRootExportKeepsMaterializedStateWhenValidOutputIsEmpty,org.adaway.db.MigrationTest#migration24To25_rebuildsRootExportWithoutRowid
  --dependency-verification=strict --stacktrace`.
- The retained v25 aligned direct-root run improved but did not pass the 1M budget:
  `syncMs=258930 rootCursorMs=16982`, with `root-export-direct blockedMs=237653
  totalMs=242874`. This confirms the remaining release-blocking work is algorithmic: stop doing a
  million-row SQLite insert into `root_host_entries` during sync.
- A fresh explorer could not be spawned because the prior agent slots are still open and the thread
  limit is reached. Per the user's correction, do not close those agents just to free a slot unless
  they are confirmed no longer needed or the user explicitly allows cleanup.

## Plan - 2026-06-13 Goal Continuation 49
- [x] Add active-truth runtime stats coverage for Home blocked counts when materialized
  `host_entries` rows are empty or skipped.
- [x] Add a schema migration for a tiny stats table so Home does not need heavyweight
  runtime-table materialization just to show counts.
- [x] Wire stats refresh into runtime-cache refresh before deciding whether to skip large
  materialized cache rebuilds.
- [x] Verify the focused DB, migration, and direct-runtime-truth tests on the emulator.

## Review - 2026-06-13 Goal Continuation 49
- Added `hosts_stats` as schema version 19 with bounded active-truth counters:
  blocked rows, exact blocked rows, allowed rows, redirected rows, and total active runtime rules.
- Changed `HostEntryDao` blocked-count queries to read the tiny stats table instead of
  `host_entries`, so Home can stay accurate even when large runtime materialization is skipped.
- Updated runtime cache refresh to always refresh `hosts_stats` first, then skip full
  materialized runtime-table rebuilds above the large-row threshold.
- Added migration coverage proving `MIGRATION_18_19` creates and backfills `hosts_stats` from
  active generation rows plus user rows while excluding stale and future generations.
- Added DB coverage proving stats counts read active truth with empty materialized runtime rows.
- Red test/compile step failed as expected before production code on missing migration/API.
- Compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testBlockedStatsReadActiveTruthWithoutMaterializedRuntimeRows,org.adaway.db.SourceDbTest#testRuntimeResolutionReadsActiveTruthWithoutMaterializedRows,org.adaway.db.SourceDbTest#testRootHostsExportReadsActiveTruthWithoutMaterializedRows,org.adaway.db.MigrationTest#migration18To19_createsRuntimeStatsTable --dependency-verification=strict --stacktrace`.
- Hygiene passed:
  `git diff --check`. Diff check still reports only expected Windows LF-to-CRLF warnings.
- Remaining gaps: stats are raw active-rule counters, not fully effective post-allow,
  post-dedup counts; 5M publish/apply benchmarking still needs retargeting now that the hot path
  can skip materialized runtime cache rebuilds.

## Plan - 2026-06-13 Goal Continuation 47
- [x] Verify whether the focused connected tests were still running before starting more Gradle
  work.
- [x] Add a minimal direct active-generation resolver slice for VPN/domain/AI runtime truth.
- [x] Switch the DNS log screen from exact-only `host_entries` lookup to final runtime truth.
- [x] Add a direct active-generation root export reader so root output can be generated without
  waiting for `root_host_entries` materialization.
- [x] Add connected tests for runtime/root active truth with empty materialized tables.
- [x] Compile and run focused connected verification.
- [x] Consume the completed architecture, product/filter, and release/security agent findings into
  the next work queue.

## Review - 2026-06-13 Goal Continuation 47
- Confirmed the prior long run was the 5M allow-heavy benchmark, not a hung focused test:
  `HostEntryAllowHeavyRebuildBenchmark ... syncMs=877324 rootCursorMs=175956`, failing the
  `adawayAllowRebuildSyncBudgetMs=300000` gate. This proves the remaining hot path is eager
  materialization of large runtime/root tables, not Gradle startup.
- The focused connected verification that was pending after the direct resolver patch passed:
  5 `SourceDbTest` methods plus 2 `VpnModelCacheInvalidationTest` methods, 0 failures.
- Added direct active-generation runtime lookup in `HostEntryDao.resolveEntry(...)` and
  `resolveRootEntry(...)`: exact redirect first, active allow rules second, exact block third,
  VPN suffix block by label peeling, and root suffix block only for the exact root host.
- Added `testRuntimeResolutionReadsActiveTruthWithoutMaterializedRows`, proving VPN/domain/AI
  runtime resolution reads `hosts_lists + hosts_meta.active_generation` even when `host_entries`
  is empty.
- Changed `LogViewModel.updateLogs()` to call `getTypeForHost(...)` instead of exact-only
  `getTypeOfHost(...)`, so logged DNS requests now reflect suffix rules and active generation.
- Added `HostEntryDao.ROOT_EXPORT_ACTIVE_QUERY` and switched `getAllForRootHostsFile()` plus
  `getRootHostsFileCursor()` to stream active root truth from `hosts_lists` instead of
  `root_host_entries`.
- Added `testRootHostsExportReadsActiveTruthWithoutMaterializedRows`, proving root export can
  produce redirected and blocked base-host rows while ignoring stale/allowed rows before
  `root_host_entries` is populated.
- Updated the root export parity/plan test to validate the real active root export query and to
  assert it no longer depends on `root_host_entries` or `host_entries`.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testRuntimeResolutionReadsActiveTruthWithoutMaterializedRows,org.adaway.db.SourceDbTest#testRootHostsExportReadsActiveTruthWithoutMaterializedRows,org.adaway.db.SourceDbTest#testRootHostsCursorReadsActiveTruthAndMatchesListApi,org.adaway.db.SourceDbTest#testRuntimeRebuildMaterializesRootExportRows,org.adaway.db.SourceDbTest#testSuffixRuntimeResolutionUsesOnlyActiveGeneration,org.adaway.db.SourceDbTest#testRuntimeSyncUsesOnlyActiveGeneration,org.adaway.model.vpn.VpnModelCacheInvalidationTest --dependency-verification=strict --stacktrace`.
  The connected run finished 8 tests on API 34 with 0 failures.
- Architecture agent finding consumed: runtime truth should become `hosts_lists` filtered by
  `(source_id = USER OR generation = active_generation)`, with materialized tables treated as a
  compatibility/cache layer only. Next performance slice should stop `SourceModel` finalization
  from requiring full `host_entries`/`root_host_entries` rebuild before protection can apply, and
  replace Home counters with active-truth stats or a tiny maintained stats table.
- Product/filter agent P0 findings consumed: FilterLists.com subscriptions lose identity after
  import because `HostsSource` lacks origin/filterlists/syntax/tag/language/compatibility fields;
  bulk subscription failures are currently only counters, not a retryable per-list outcome ledger.
  Queue: add source metadata storage and `FilterImportRun`/`FilterImportItemOutcome` before
  claiming market-leading discovery.
- Product/filter P1/P2 findings consumed: presets need durable active profiles and diff previews;
  catalog entries need compatibility/risk/partial-extraction metadata; source rows need compact
  health/provenance/skipped-rule disclosures; Discover should default to DNS-safe recommended
  ordering rather than raw directory browsing.
- Release/security agent findings consumed: client-side signed updater checks exist, but release
  CI still needs to create/sign/verify/publish `manifest.json` and canonical APK alias; Android CI
  and CodeQL need PR/workflow-dispatch gates; MIT remains legally blocked for the distributed app;
  release artifacts should publish native/web dormant-asset absence proof; dependency verification
  fallback debt needs a documented audit and CI guard.
- Remaining major gap: the 5M gate still fails while `rebuildFromActiveGeneration(...)` performs a
  full eager materialization. The next code slice should make update/apply paths consume active
  truth directly and either skip large materialization or demote it to an async/cache refresh with
  bounded stats.

## Plan - 2026-06-13 Goal Continuation 48
- [x] Spawn focused read-only performance and QA agents for the next materialization-removal slice.
- [x] Identify every `SourceModel` finalization path that still treats runtime-table rebuild as
  publish-critical.
- [x] Flip stale finalizer tests from "host_entries rebuild failure rolls back publish" to
  "runtime cache failure cannot roll back active truth."
- [x] Verify those tests fail red against the synchronous-rebuild implementation.
- [x] Move runtime cache refresh out of update/single-source publish transactions.
- [x] Bound best-effort runtime cache refresh so 5M-scale updates do not spend minutes rebuilding a
  compatibility cache in the background.
- [x] Add a public `checkAndRetrieveHostsSources()` test that publishes changed active truth while
  materialized table writes are forbidden.
- [x] Re-run compile, focused connected tests, and nearby SourceModel connected tests.

## Review - 2026-06-13 Goal Continuation 48
- Agent consensus: `hosts_lists + hosts_meta.active_generation` must be runtime truth; `host_entries`
  and `root_host_entries` are now compatibility caches and must not be required for update/apply
  finalization.
- Red tests first: the updated `SourceModelGenerationFailureTest` finalizer tests failed as
  expected with `SQLiteConstraintException` from `HostEntryDao.rebuildFromActiveGeneration(...)`,
  proving the old implementation still coupled publish correctness to materialization.
- `SourceModel.finalizeActivatedGeneration(...)`, `finalizeNoChange(...)`,
  `finalizeStagedSourceGeneration(...)`, and `finalizeStagedSourceGenerations(...)` no longer call
  `HostEntryDao.rebuildFromActiveGeneration(db)` inside their publish transactions.
- Added `SourceModel.scheduleRuntimeCacheRefresh()` as a best-effort compatibility-cache refresh
  after active truth changes. Failures are logged and do not roll back active generation, source
  metadata, disabled-source cleanup, VPN invalidation, or root export correctness.
- Added a hard `RUNTIME_CACHE_REFRESH_MAX_ROWS` guard at 500k active rows. Above that, the app skips
  rebuilding `host_entries/root_host_entries` entirely instead of moving the 5M stall into a
  background disk churn problem.
- Added `HostEntryDao.countActiveRuntimeRuleRows()` to gate that cache refresh.
- Re-applied the stale log fix in the current worktree: `LogViewModel.updateLogs()` now calls
  `getTypeForHost(...)`, so DNS logs read active runtime truth instead of exact-only
  `host_entries`.
- Added `checkAndRetrieveHostsSources_changedSourcePublishesWithoutRuntimeMaterialization`: a real
  MockWebServer 200+304 update clears materialized tables, forbids inserts into them with triggers,
  publishes `active_generation == STAGING_GENERATION`, resolves changed and carried-forward hosts
  through active truth, drains the root cursor, and keeps both materialized tables empty.
- Focused verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  the three updated red/green finalizer tests;
  and a 10-test connected run covering changed-source publish without materialization, all-304,
  finalizer cache-failure behavior, direct runtime/root active truth, root cursor parity, and VPN
  cache invalidation.
- Broader nearby verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.model.source.SourceModelHttpConditionalTest --dependency-verification=strict --stacktrace`;
  10 tests, 0 failures.
- Remaining risks called out by agents: Home blocked counts still rely on materialized
  `host_entries`; a market-leading implementation needs a small active-truth stats table or other
  bounded count cache, not an unbounded live `COUNT(DISTINCT ...)`. Optional 5M benchmark coverage
  should be retargeted from explicit `rebuildRuntimeEntries()` to the real publish/apply path.

## Plan - 2026-06-13 Goal Continuation 46
- [x] Let the 5M benchmark finish instead of closing it early.
- [x] Test the host-ordered source import/index experiment against the real 5M gate.
- [x] Revert the experiment when it failed the 5M budget and restored the better covering
  kind/host runtime-import index.
- [x] Regenerate the Room schema after the revert.
- [x] Re-run focused compile and connected DB/migration guards.
- [x] Superseded by the active-truth runtime path, bounded cache refresh, and stage-backed root
  export proof at 5M scale.

## Review - 2026-06-13 Goal Continuation 46
- Ran the requested 5M benchmark to completion after the user explicitly asked not to close long
  benchmark work early.
- Host-ordered experiment result:
  `HostEntryAllowHeavySeedBenchmark blockedRows=5000000 exactAllowRules=5000
  suffixAllowRules=5000 seedMs=485601 checkpointMs=148`.
- Final failed 5M gate:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=5000000 exactBlockedRows=2500000
  suffixBlockedRows=2500000 exactAllowRules=5000 suffixAllowRules=5000 exactAllowMatches=5000
  suffixAllowExactMatches=5000 suffixAllowSuffixMatches=5000 runtimeRows=4985000
  rootRows=4985000 syncMs=877324 rootCursorMs=175956`.
- Phase evidence:
  `HostEntryDao.sync perf: allowRules=true clearMs=33 importBlockedMs=495367
  allowMs=148198 importRedirectedMs=41 indexDropMs=54 indexCreateMs=30693 rootExportMs=13935
  totalMs=688321 wildcardExactAllowRules=false`.
- Finding: feeding source rows from a covering `host,kind` index failed the real target and was
  reverted. The 5M bottleneck is not solved by source-side scan order; it is the cost of eagerly
  materializing millions of derived `host_entries` rows plus allow filtering and cursor draining.
- Restored the intended v18 schema: narrow `index_hosts_lists_host` plus covering
  `index_hosts_lists_kind_host(kind, host, type, enabled, generation, source_id, redirection)`.
- Verification after revert passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks
  --dependency-verification=strict --stacktrace`;
  and connected `MigrationTest` plus the focused `SourceDbTest` runtime-generation/query-plan
  guards passed 11/11.

## Plan - 2026-06-13 Goal Continuation 45
- [x] Confirm whether a Gradle or benchmark process is still running.
- [x] Separate the measured 5M bottleneck from build/tooling delay.
- [x] Remove expensive aggregate/order work from active-generation source imports.
- [x] Route user blocked-rule imports through the user/source index instead of the large
  kind/host scan.
- [x] Rebuild the kind/host hosts-list index as a covering runtime-import index.
- [x] Add migration and query-plan tests for the physical runtime-table/index changes.
- [x] Run focused compile, unit, connected DB, and migration verification.
- [x] Benchmark the 1M and 5M runtime-sync paths after each meaningful SQLite change.
- [x] Superseded by the active-truth/read-path design and later 5M benchmark proof.

## Review - 2026-06-13 Goal Continuation 45
- Confirmed there is no fresh Gradle or 5M benchmark still running. The only Java process found
  is an older long-lived process from June 12, so the current delay is not an active stuck command.
- The slow path is now isolated to SQLite write amplification in `host_entries` at 5M scale:
  millions of rows are materialized into the derived runtime table while uniqueness and indexes are
  enforced. WAL growth during the 5M run was steady, which means the database was writing rather
  than hanging.
- Changed blocked runtime import so user rows are imported through `importBlockedUser()` and source
  rows are imported through active-generation exact/suffix source queries. This avoids scanning the
  giant source rowset when the user source is empty or small.
- Removed `GROUP BY` and `ORDER BY` from active-generation source blocked imports and kept duplicate
  suppression in the destination table.
- Rebuilt `index_hosts_lists_kind_host` as a covering index on
  `(kind, host, type, enabled, generation, source_id, redirection)` so source imports can read from
  the index without random table lookups.
- Added migration coverage for the v17 `host_entries` `WITHOUT ROWID` rebuild and v18 covering
  hosts-list index rebuild. Room schema JSON was regenerated through version 18.
- Verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  and focused connected DB/migration tests for `MigrationTest` plus the runtime-sync generation and
  query-plan guards in `SourceDbTest`.
- Best 1M measured result after the covering index:
  `seedMs=54840 importBlockedMs=1122 allowMs=8555 indexCreateMs=1451 rootExportMs=1911
  totalMs=13053 syncMs=18111 rootCursorMs=4780`.
- 1M `importBlockedMs` improved from the earlier kind-split result of `8156ms` to `1122ms`, and
  1M `syncMs` improved from `30310ms` to `18111ms`.
- 5M still does not meet the proposed `syncMs < 300000` gate. The latest 5M run seeded in
  `715963ms`, then `host_entries` import kept writing past 300 seconds with WAL growth from about
  59M to 506M before the run was stopped. The next fix must reduce or redesign destination
  materialization, not just add source-side indexes.
- Reverted unproven SQLite cache/temp-store tuning after it failed to beat the covering-index
  result at 1M scale.

## Plan - 2026-06-12 Goal Continuation 45
- [x] Dispatch focused expert agents for the remaining market-leading gaps: 5M performance,
  UX/filter quality, and release/licensing/security.
- [x] Run the 5M materialized SQL dedup/carry-forward benchmark and compare it with the earlier
  5M clean baseline.
- [x] Capture `syncMs`, `rootCursorMs`, parser/import timing, and `HostEntryDao.sync perf` from
  logcat so the result is source-of-truth evidence rather than a narrow pass/fail.
- [x] Use the 5M result and agent findings to decide the next implementation slice.
- [x] Record verification, remaining gaps, and any follow-up gates in this task log.

## Review - 2026-06-12 Goal Continuation 45
- Dispatched and closed three read-only expert reviewers: performance/Android systems,
  UX/product/filter quality, and release/security/licensing.
- Performance review finding: after materialized root export, 5M wall-clock risk moved to
  parser/import and `hosts_lists` write amplification. The current pipeline still creates
  `HostListItem` objects, sends them through queues, marks SQL dedup row-by-row, and executes one
  `hosts_lists` insert per accepted row while maintaining multiple secondary indexes.
- Performance follow-up candidates: finer timing inside parse/import, fast-path common hosts
  parsing before regex fallback, staging-table import to reduce index churn, explicit allow-rule
  scale benchmarks, and 1M/5M production root-writer/apply evidence.
- Release/security review finding: update-manifest and APK integrity hardening are real, but MIT
  cannot be claimed while GPL-derived app/VPN code remains. `-SourceMode GitTracked` is expected
  red until tracked deleted asset/native blockers are staged or committed.
- Release/security follow-up candidates: updater host/path pinning, dependency verification debt
  cleanup, CI PR/CodeQL SDK alignment, and a release artifact dry run with SBOM/artifact boundary
  checks once real or disposable release trust material is provided.
- UX/filter review finding: “Subscribe all” appears under search/tag/language filters but
  subscribes the whole FilterLists directory, so a user can filter for a narrow set and still
  subscribe everything DNS-safe.
- UX/filter follow-up candidates: scope bulk subscription to current filtered results or make it
  explicitly global; restore/remove the confusing curated catalog path; add compatibility
  assessment using URL quality, preflight/content type, parse yield, skipped counts, and root/VPN
  disclosure; surface partial-apply truth in source rows.
- Live 5M benchmark progress evidence so far: at `17:05:33` emulator time it reached
  `1094000` inserted rows at `859s`; app memory remained bounded around `102MB` PSS and the
  benchmark database was about `2198886` 4KB pages at the latest poll.
- Live 5M benchmark later evidence: insert logging reached `4500000` accepted rows and reported
  `DB insert perf done: overall=1397 rows/s, dbOnly=1423 rows/s (totalInserted=4500000,
  dbInsertMs=3161679, elapsed=3221s)`. The parser then reported `4500000 host list items
  inserted, 250000 lines skipped`, after which SQLite logged a `source-loader-performance-test`
  WAL truncation from about `1190737712` bytes. The test is still running with the JUnit runner
  thread active; no final `SourceLoaderSqlDedupCarryForwardScaleBenchmark` line has been emitted.
- Live 5M runtime rebuild evidence: the same old-code run later emitted
  `HostEntryDao.sync perf: allowRules=false clearMs=5 importBlockedMs=273181 allowMs=0
  importRedirectedMs=18403 indexDropMs=31 indexCreateMs=14255 rootExportMs=7871 totalMs=313746`.
  That confirms the run has moved past runtime rebuild/materialization and into the final root
  cursor drain.
- Final old-code 5M materialized benchmark passed. Gradle wall time was `1h 35m 25s`; final line:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=5000000 inserted=4500000 copied=3
  dedupRows=4500003 progressEvents=2251 parseMs=3552783 carryForwardMs=31 syncMs=375649
  rootCursorMs=159957`. Compared with the earlier clean 5M baseline, root cursor improved from
  `4043095ms` to `159957ms`, but parse/import is now the release-blocking long pole and sync is
  still about `6.26` minutes at 5M.
- Interim performance diagnosis: the 5M long pole is no longer root cursor export. It is
  generation import/write amplification around SQL dedup plus `hosts_lists` insertion and
  secondary-index/WAL maintenance, followed by an under-instrumented post-import SQL tail.
- Added follow-up instrumentation in `SourceLoaderPerformanceTest` for the silent post-import
  phases: parse, imported count, carry-forward, dedup count, runtime rebuild, runtime count, and
  root cursor. Future large runs should emit `SourceLoaderPerfTest` logcat phase lines.
- Implemented the next import optimization slice pending verification: `SqlUpdateDeduper` now owns
  a cheap unindexed `update_pending_hosts` staging table. `SourceLoader` stages rows accepted by
  the update-wide seen table and flushes pending rows into indexed `hosts_lists` once per
  successful source, preserving cross-source first-wins dedup semantics while reducing per-row
  indexed-table work in the parser loop.
- Added `HostEntryDao.sync phase=...` logs around clear, blocked import, allow filtering,
  redirected import, index creation, and root export so future large runtime rebuilds expose their
  live phase instead of logging only at the end.
- Removed a production-only `sqlDeduper.count()` call from the full-update completion log. The
  app now uses the existing parsed-host progress counter instead of forcing a full scan of the
  multi-million-row seen table just to print diagnostics.
- Narrowed the production root hosts-file cursor to `host`, `type`, and `redirection`, which are
  the only columns `RootModel.writeHosts(...)` reads. The list API still exposes `kind` for DB
  semantics tests, and cursor parity tests now treat root export cursor rows as exact projections.
- Removed per-row SQLite `LOWER(host)` calls from active-generation source imports into
  `host_entries`; parser/entity writes already normalize source rows to lowercase. Defensive
  lowercasing remains on user-source imports, which are small and more likely to come from manual
  entry or legacy data.
- Optimized `HostListItem.setHost(...)` for the hot parser path by avoiding a new lowercase string
  allocation when the parsed host is already lowercase ASCII.
- Staged 1M benchmark passed after the first optimization pass:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3
  dedupRows=900003 progressEvents=451 parseMs=281247 importedCountMs=143 carryForwardMs=2
  dedupCountMs=35 syncMs=6725 runtimeCountMs=83 rootCursorMs=4415`.
- Finding from the 1M staged benchmark: staging improved parse/import from the prior materialized
  1M `parseMs=320117` to `281247` and narrowed root cursor from `5599` to `4415`, but DB insert
  is still dominated by the update-wide seen table (`dbInsertMs=271416`) rather than the
  `hosts_lists` flush (`flushMs=7570`).
- Added a follow-up low-risk SQLite optimization: `update_seen_hosts` is now created
  `WITHOUT ROWID` because it stores only a composite primary key and does not need a separate
  rowid table b-tree during millions of dedup inserts.
- `WITHOUT ROWID` focused tests passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_deduplicatesAcrossSourcesAndKeepsRedirectTargets,org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_carryForwardSkipsAlreadySeenRowsAndMarksCopiedRows --dependency-verification=strict --stacktrace`.
- `WITHOUT ROWID` 1M benchmark passed:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3
  dedupRows=900003 progressEvents=451 parseMs=271090 importedCountMs=132 carryForwardMs=1
  dedupCountMs=19 syncMs=6344 runtimeCountMs=80 rootCursorMs=4816`.
- Finding from the second 1M staged benchmark: `WITHOUT ROWID` improved `dbInsertMs` from
  `271416` to `260572` and `parseMs` from `281247` to `271090`, but the seen-table insert path is
  still the dominant remaining bottleneck.
- Reworked SQL dedup into a set-based flush: the parser now stages accepted rows into
  `update_pending_hosts` without per-row seen-table primary-key writes. On successful source
  completion, SQLite inserts the first pending row per key into `hosts_lists` if that key has not
  been seen in the update, then marks all pending keys in `update_seen_hosts` and clears pending.
  Progress callbacks now represent streamed accepted rows; DB assertions still validate unique
  runtime rows.
- Set-based dedup compile and focused connected tests passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_deduplicatesAcrossSourcesAndKeepsRedirectTargets,org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_carryForwardSkipsAlreadySeenRowsAndMarksCopiedRows,org.adaway.model.source.SourceLoaderPerformanceTest#parseReadFailure_rollsBackTargetGenerationAndKeepsActiveRows --dependency-verification=strict --stacktrace`.
- Set-based dedup 1M benchmark passed:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3
  dedupRows=900003 progressEvents=475 parseMs=163541 importedCountMs=167 carryForwardMs=3
  dedupCountMs=30 syncMs=6086 runtimeCountMs=88 rootCursorMs=4591`. This reduced 1M parse/import
  by about 40% versus the `WITHOUT ROWID` slice and about 49% versus the first materialized 1M
  baseline.
- Broad focused connected verification passed after the set-based dedup change:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest,org.adaway.db.SourceDbTest --dependency-verification=strict --stacktrace`.
  The run started 23 tests, skipped the 3 opt-in scale benchmarks, finished 26 tests, and had
  0 failures.
- Corrected the production completion log label from `unique hosts` to `accepted host rows`
  because progress callbacks now report streamed accepted rows during the faster staged import,
  while final DB/runtime assertions continue to validate unique rows.
- Broad Gradle verification passed after the final log-label fix:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License-boundary checks passed:
  `scripts/check-license-boundary.ps1` and
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`.
- Full `git diff --check` passed with only the existing Windows LF-to-CRLF warnings.
- Closed the completed root-output, SQLite/runtime rebuild, and QA/devil-advocate agents after
  consuming their findings into the implementation and this task log.
- Remaining major gaps: the overall market-leading goal is not complete; MIT remains blocked by
  GPL-derived material and legal/provenance clearance; 5M root-output performance is still far
  above the proposed release gate and likely needs materialized root-export rows or a larger export
  architecture change; and `-SourceMode GitTracked` remains expected red until large deletes,
  untracked tests/docs/scripts, dependency-verification files, and `.gitattributes` are
  staged/committed.

## Plan - 2026-06-12 Goal Continuation 43
- [x] Add red connected tests for a materialized root-export table, including migration, exact vs
  suffix root output, active allow-rule exclusion, and covering/simple cursor plan evidence.
- [x] Add `root_host_entries` schema/entity/migration and wire Room/AppDatabase v15.
- [x] Rebuild `root_host_entries` inside the same runtime rebuild transaction after
  `host_entries` is finalized.
- [x] Change root hosts List/Cursor APIs to read materialized root export rows instead of running
  suffix anti-joins during export.
- [x] Run focused connected tests and requested-scale performance evidence.
- [x] Consume agent findings, record review evidence, and run final hygiene gates.

## Review - 2026-06-12 Goal Continuation 43
- Added red connected coverage for the materialized root-export table. The first migration test
  failed as expected on missing `MIGRATION_14_15`, proving the test guarded the new schema path.
- Added Room schema version 15 with a `RootHostEntry` entity and `root_host_entries` table:
  `host`, `kind`, `type`, and `redirection`, keyed by `host`.
- Added `MIGRATION_14_15` to create and backfill `root_host_entries` from active runtime truth.
  Exact rows are copied first; suffix rows are projected to exact base-host rows with active
  suffix-allow suppression and `INSERT OR IGNORE` so exact/redirect rows win.
- Rebuilt `root_host_entries` inside `HostEntryDao.rebuildFromActiveGeneration(...)` after
  `host_entries` is finalized, in the same transaction used by `SourceModel` publish/sync paths.
- Changed root hosts file List/Cursor APIs to read `root_host_entries` directly, removing the
  suffix anti-join and `hosts_lists` lookups from the root export hot path.
- Added DB semantics coverage for suffix root projection, exact child allow behavior, suffix allow
  suppression, redirects, duplicate suppression, cursor/list parity, and query-plan evidence that
  export reads `root_host_entries` rather than the old `host_entries` anti-join shape.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration14To15_createsAndPopulatesMaterializedRootExport --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testRuntimeRebuildMaterializesRootExportRows --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest --dependency-verification=strict --stacktrace`;
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.model.source.SourceModelHttpConditionalTest --dependency-verification=strict --stacktrace`;
  and `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#parseInsertSyncAndRootCursor_mixedRules10k_staysWithinBudget --dependency-verification=strict --stacktrace`.
- Materialized 1M requested-scale benchmark passed:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=3 dedupRows=900003 progressEvents=451 parseMs=320117 carryForwardMs=4 syncMs=6786 rootCursorMs=5599`.
- Runtime rebuild timing now reports the materialization cost explicitly:
  `HostEntryDao.sync perf: allowRules=false clearMs=0 importBlockedMs=1852 allowMs=0 importRedirectedMs=146 indexDropMs=4 indexCreateMs=913 rootExportMs=1599 totalMs=4514`.
- Finding: the 1M runtime sync plus root cursor path improved from the clean baseline
  `29513ms + 17913ms = 47426ms` to `6786ms + 5599ms = 12385ms`, about a 74% reduction on the
  measured hot path. Against the deferred-index-only best result, the same combined path improved
  from `30888ms` to `12385ms`.
- Closed the completed SQLite/migration, QA/devil-advocate, and root-semantics agents after
  consuming their findings into the implementation and this task log.
- Broad verification passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene checks passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`. Diff check still reports only expected Windows LF-to-CRLF warnings.
- Remaining gaps: 5M materialized benchmarking has not been rerun; production root hosts-file
  writing with IPv6 on/off is not separately benchmarked; the overall market-leading goal is still
  larger than this single optimization; MIT remains blocked by GPL-derived material and
  legal/provenance clearance.

## Plan - 2026-06-12 Goal Continuation 44
- [x] Add an optional connected benchmark for production `RootModel` hosts-file generation.
- [x] Seed `root_host_entries` directly so the benchmark measures file writing rather than parsing
  or runtime-table rebuilding again.
- [x] Run the benchmark with IPv4-only and IPv6-enabled output.
- [x] Re-run broad Gradle/license/diff hygiene after the Android test-source change.

## Review - 2026-06-12 Goal Continuation 44
- Added `rootModelCreateHostsFile_requestedRows_recordsWriteBenchmark`, guarded by the optional
  instrumentation argument `adawayRootWriteRows`, so normal broad test runs skip the large writer
  benchmark.
- The benchmark swaps the test database into `AppDatabase`'s singleton only for the duration of
  the test, invokes the real `RootModel.createNewHostsFile()` path, and restores the previous
  singleton afterward.
- The benchmark seeds `root_host_entries` directly with blocked and redirected rows. That isolates
  production hosts-file writing from parser/runtime rebuild cost, which is covered by the separate
  SourceLoader and DB benchmarks.
- Focused connected benchmark passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#rootModelCreateHostsFile_requestedRows_recordsWriteBenchmark -Pandroid.testInstrumentationRunnerArguments.adawayRootWriteRows=100000 --dependency-verification=strict --stacktrace`.
- 100k root writer metric:
  `RootModelHostsFileWriteBenchmark rows=100000 ipv4Ms=2080 ipv4Bytes=3487214 ipv6Ms=1548 ipv6Bytes=6347216`.
- Finding: production file generation is not the current primary bottleneck at 100k materialized
  rows; it is measurable and now gated, but the larger remaining performance risk is still 5M
  parse/import scale and full-device apply behavior.
- Broad verification passed after the benchmark-source change:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug :app:lintDebug test --dependency-verification=strict --stacktrace`.
- License and hygiene checks passed:
  `scripts/check-license-boundary.ps1`;
  `scripts/check-license-boundary.ps1 -StrictSourceArchive`;
  and `git diff --check`. Diff check still reports only expected Windows LF-to-CRLF warnings.

## Plan - 2026-06-14 Goal Continuation 45
- [x] Preserve the active long-running 1M instrumentation run and extract its final evidence
  instead of closing or restarting it.
- [x] Finish the import-time root-export staging slice: stage root-export candidates during the
  set-based SQL dedup flush, carry staged candidates across source promotion/carry-forward paths,
  and materialize root export from staged candidates when coverage is complete.
- [x] Remove the disabled per-row root-export staging path from `SourceLoader` so direct parsing
  does not pay double-write cost.
- [x] Re-run compile and focused connected verification after cleanup.
- [x] Record benchmark results and remaining bottlenecks.

## Review - 2026-06-14 Goal Continuation 45
- Added `root_host_entries_stage` as schema version 27 with migration coverage and stage indexes
  for source/generation and root export ordering.
- `SqlUpdateDeduper` now inserts deduped blocked/redirect rows into the root-export stage during
  its set-based pending flush, keeping the parser loop off the hot root-export table.
- `HostEntryDao.rebuildFromActiveGeneration(...)` now uses staged candidates for large runtime
  rebuilds when every enabled non-user source has staged rows and no user blocked/redirect rows
  require fallback. The fallback path remains the active-generation materializer.
- `SourceModel` now clears, promotes, carries forward, and deletes stage rows with the matching
  generation/source lifecycle so stale or failed updates do not become runtime truth.
- Removed the disabled per-row root-export staging statement from `SourceLoader` after it proved
  too expensive in a direct 600k experiment.
- The 1M SQL-dedup benchmark finished and passed:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=1000000 inserted=900000 copied=5
  membershipRows=900005 runtimeRows=900003 progressEvents=475 parseMs=586873
  importedCountMs=36832 carryForwardMs=459 dedupCountMs=4131 syncMs=68121
  runtimeCountMs=77 rootCursorMs=5026`.
- Finding: the reason this verification takes so long is now explicit. On emulator, parse/import
  is the long pole at `586873ms`; runtime/root export is within the requested budget
  (`syncMs=68121`, `rootCursorMs=5026`) but still has a visible staged materialization tail
  (`root-export-stage totalMs=51115`, including `redirectShadowMs=30448`).
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Fresh focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.db.MigrationTest#migration26To27_createsRootExportStageTable,
  org.adaway.db.SourceDbTest#testLargeRuntimeSkipStillMaterializesRootExportRows,
  org.adaway.db.SourceDbTest#testRootHostsCursorReadsActiveTruthAndMatchesListApi,
  org.adaway.model.source.SourceModelHttpConditionalTest#checkAndRetrieveHostsSources_changedSourcePublishesWithoutRuntimeMaterialization,
  org.adaway.model.source.SourceLoaderPerformanceTest#parseInsertSyncAndRootCursor_mixedRules10k_staysWithinBudget
  --dependency-verification=strict --stacktrace`; Gradle reported 5 tests, 0 failures.
- Remaining gaps: this slice improves/gates the 1M runtime/root-export path but does not complete
  the overall market-leading scorecard. Parse/import and staged redirect-shadow deletion remain
  the next performance targets, and MIT remains blocked pending GPL-derived material clearance.

## Plan - 2026-06-14 Goal Continuation 46
- [x] Reuse the completed reviewer swarm without closing agents, because the agent limit is
  already reached and the user explicitly asked not to close running agents.
- [x] Validate whether the reported large-set stale root-export risk is still present in current
  code.
- [x] Replace the staged root-export redirect-shadow delete pass with precedence-aware dedupe.
- [x] Add focused staged-path correctness coverage for redirect-vs-block precedence.
- [x] Run compile, focused connected tests, and a scaled benchmark.

## Review - 2026-06-14 Goal Continuation 46
- Current code no longer has the old `SourceModel.scheduleRuntimeCacheRefresh()` >500k early
  return. Large refreshes call `HostEntryDao.rebuildFromActiveGeneration(...)`, which clears
  materialized runtime/root caches and rebuilds `root_host_entries`. The stale-root-export agent
  finding is recorded as resolved in current state, not reimplemented.
- Changed `HostEntryDao` root-export materialization to skip the separate redirect-shadow delete
  pass. Redirect rows are inserted normally and `dedupeRootExportRowsByPrecedence(...)` now keeps
  the best row per host, with redirects winning over blocked rows and earlier rows preserving
  existing priority within the same type.
- Added `sqlUpdateDeduper_largeRootExportPrefersRedirectOverBlockedDuplicate`, which forces the
  large staged root-export path with source metadata while using a tiny fixture. It proves a
  blocked+redirect host leaves only the redirect, duplicate exact/suffix blocks collapse to one
  row, and `host_entries` remains skipped for large active counts.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused connected correctness verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_largeRootExportPrefersRedirectOverBlockedDuplicate,
  org.adaway.db.SourceDbTest#testLargeRuntimeSkipStillMaterializesRootExportRows
  --dependency-verification=strict --stacktrace`; Gradle reported 2 tests, 0 failures.
- 600k requested-scale benchmark passed:
  `SourceLoaderSqlDedupCarryForwardScaleBenchmark lines=600000 inserted=540000 copied=5
  membershipRows=540005 runtimeRows=540003 progressEvents=285 parseMs=293369
  importedCountMs=6282 carryForwardMs=112 dedupCountMs=3188 syncMs=14605
  runtimeCountMs=52 rootCursorMs=3374`.
- Runtime phase evidence after the change:
  `HostEntryDao.root-export-stage perf: indexDropMs=10 clearMs=0 blockedMs=5966
  indexCreateMs=2017 allowMs=0 redirectShadowMs=0 redirectedMs=376 dedupeMs=1228
  finishMs=0 totalMs=9597 allowRules=false wildcardExactAllowRules=false`.
- Finding: the targeted redirect-shadow pass is gone (`redirectShadowMs=0`), but this is not a
  broad performance win claim. On this emulator run, parse/import remains the dominant cost and
  staged root-export still has measurable blocked insert plus precedence dedupe cost.
- Swarm follow-ups still open for the market-leading scorecard:
  FilterLists subscriptions need persistent provenance/compatibility metadata and per-item import
  outcomes; presets need durable active profile state and diff preview; release CI needs update
  manifest generation/publication proof and PR triggers; dependency verification needs an audit
  document for ignored keys/fallback groups; MIT remains blocked until GPL-derived app/VPN code
  and assets are removed, rewritten, or permission-cleared.

## Plan - 2026-06-14 Goal Continuation 47
- [x] Inspect existing FilterLists provenance work before adding new schema.
- [x] Extend the current source metadata model with durable directory name, tag ids, and language
  ids instead of duplicating provenance elsewhere.
- [x] Wire richer metadata through bulk subscribe, direct Discover subscribe, and the add-source
  confirmation flow.
- [x] Add migration, helper, worker, and string-guard tests for the richer provenance.
- [x] Run focused JVM and connected verification.

## Review - 2026-06-14 Goal Continuation 47
- Bumped Room schema to version 28 and added `hosts_sources.filter_list_name`,
  `filter_list_tag_ids`, and `filter_list_language_ids`. Schema `28.json` was generated and
  contains the new columns.
- `FilterListsSourceMetadata` now applies and copies directory name, tag ids, and language ids
  alongside existing FilterLists id/syntax/compatibility/selected URL metadata.
- `FilterListsSubscribeAllWorker` now preserves summary tag/language metadata for both cached
  and network-resolved bulk subscriptions.
- `DiscoverFilterListsFragment` now passes richer metadata into both direct insert and
  `SourceEditActivity` confirmation flows. `SourceEditActivity` accepts and preserves the new
  extras, while existing-source edits still copy provenance only when the URL is unchanged.
- Added `MigrationTest.migration27To28_addsFilterListsDirectoryProvenanceColumns`.
- Expanded `FilterListsSourceMetadataTest`, `FilterListsSubscribeAllWorkerTest`,
  `FilterListsSubscribeAllWorkerRoomTest`, `FilterListsSubscribeAllWorkerDoWorkTest`, and
  `DiscoverPresetSubscriptionTest` so the richer provenance is required by tests.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.FilterListsSourceMetadataTest
  --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --dependency-verification=strict --stacktrace`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.db.MigrationTest#migration27To28_addsFilterListsDirectoryProvenanceColumns,
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest#subscribeAllRecorder_writesCompatibleSourcesAndReturnsExactCounts,
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest#doWork_subscribesAllWithFakeDirectoryClientAndEnqueuesUpdateOnce
  --dependency-verification=strict --stacktrace`; Gradle reported 3 tests, 0 failures.
- Remaining product gaps: per-item import outcome ledger, visible source-row provenance chips,
  durable active presets/profile diffing, compatibility/risk disclosure, and smarter recommended
  defaults still need implementation before the filter UX can credibly be called market-leading.

## Plan - 2026-06-14 Goal Continuation 48
- [x] Inspect source-list row rendering after adding durable FilterLists provenance.
- [x] Surface compact provenance/health in the existing source status area without adding a new
  screen or wider row layout.
- [x] Snapshot provenance fields in `FilterListItem.SourceItem` so RecyclerView diffing updates
  rows when metadata or skipped counts change.
- [x] Add focused unit coverage for the rendered provenance summary.
- [x] Run focused verification and hygiene.

## Review - 2026-06-14 Goal Continuation 48
- `CategorizedSourcesAdapter` now appends a second source status line when useful metadata exists:
  `FilterLists.com`, the stored compatibility label, and skipped-rule count. Example:
  `FilterLists.com • DNS-safe • 12 skipped`.
- Non-directory sources with parse skips now show a compact health signal such as `3 skipped`
  instead of hiding skipped-rule loss from users.
- `FilterListItem.SourceItem` now snapshots `skippedCount`, FilterLists id/name/compatibility,
  tag ids, and language ids. Adapter diffing now compares these fields, so provenance/health
  changes rebind affected rows.
- Added `CategorizedSourcesAdapterTest` for the provenance summary, including directory
  compatibility plus skipped rows, skipped rows without directory provenance, and empty-summary
  behavior.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.CategorizedSourcesAdapterTest
  --tests org.adaway.model.source.FilterListsSourceMetadataTest
  --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --dependency-verification=strict --stacktrace`.
- Remaining UX gaps: source rows now expose provenance/health, but they are still plain text
  rather than visual chips; per-item import outcome details and retry actions are still open.

## Plan - 2026-06-14 Goal Continuation 49
- [x] Inspect bulk FilterLists worker output and Discover's completed summary path.
- [x] Add a capped last-run outcome ledger for bulk subscribe runs.
- [x] Persist the ledger in FilterLists cache preferences and expose a compact review preview in
  WorkManager output.
- [x] Show the review preview in Discover's completed/cancelled bulk status summary.
- [x] Add focused tests for output preview, durable ledger persistence, and cancellation ordering.
- [x] Run focused compile, JVM tests, connected tests, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 49
- `FilterListsSubscribeAllWorker` now records every bulk candidate outcome as a capped ledger:
  subscribed, already present, no usable URL, or unsupported syntax.
- The worker persists the latest ledger to `filterlists_cache` using
  `lastRunOutcomes`, `lastRunOutcomeCount`, `lastRunReviewCount`,
  `lastRunReviewPreview`, `lastRunCancelled`, and `lastRunFinishedAt`.
- WorkManager output now includes `reviewCount` and `reviewPreview`, keeping Data small while
  giving the UI concrete names for the first reviewable skipped items.
- `DiscoverFilterListsFragment` appends a review summary to the final bulk status when skipped
  items need attention, for example `Review: Unsupported: Regional unsupported`.
- `FilterListsSubscribeAllWorkerDoWorkTest` now verifies that the full worker path writes the
  durable ledger and output preview for subscribed, already, unsupported, and no-url lists.
- `FilterListsSubscribeAllWorkerRoomTest` now verifies review output on the recorder path, and
  `FilterListsSubscribeAllWorkerTest` now verifies cancellation flushes before persistence and
  notification cancellation.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --dependency-verification=strict --stacktrace`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest,
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest
  --dependency-verification=strict --stacktrace`; Gradle reported 4 tests, 0 failures.
- `git diff --check` passed with only existing CRLF conversion warnings.
- Remaining gap: the ledger is durable and visible in summary form, but there is still no full
  in-app detail screen or retry action for individual skipped FilterLists rows.

## Plan - 2026-06-14 Goal Continuation 50
- [x] Inspect the Discover FilterLists layout for a low-impact review-details action.
- [x] Add a hidden-by-default "Review details" action below the bulk status.
- [x] Wire the action to the persisted last-run outcome ledger.
- [x] Format the ledger into a capped dialog so large runs cannot render unbounded text.
- [x] Add focused tests for the review action wiring and formatter output.
- [x] Run compile, focused JVM tests, connected worker tests, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 50
- Added `filterlistsReviewLastRunButton` to the Discover FilterLists bulk controls. It only
  appears when the persisted last run has reviewable skipped items and hides while a bulk job is
  running.
- `DiscoverFilterListsFragment` now opens a `Last FilterLists subscription` dialog from the
  durable worker ledger, showing readable statuses for added, already present, no-url, and
  unsupported rows.
- The dialog renderer caps output at 80 rows and appends a remaining-count line when the persisted
  ledger is larger than the UI preview.
- Added `DiscoverPresetSubscriptionTest` guards for review action wiring, persisted ledger usage,
  capped rendering, and readable formatter output.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --dependency-verification=strict --stacktrace`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest,
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest
  --dependency-verification=strict --stacktrace`; Gradle reported 4 tests, 0 failures.
- `git diff --check` passed with only existing CRLF conversion warnings.
- Remaining gap: users can inspect skipped rows after a bulk run, but there is still no one-tap
  retry or manual-add shortcut from individual dialog rows.

## Plan - 2026-06-14 Goal Continuation 51
- [x] Attempt to dispatch sidecar expert reviewers for the remaining scorecard gaps without
  closing existing agents.
- [x] Implement a one-tap retry path for last-run FilterLists no-URL outcomes.
- [x] Clear persisted and in-memory negative URL cache entries before retrying so the worker
  refetches list details instead of repeating the previous skip.
- [x] Requeue the existing subscribe-all worker with an explicit list-id scope for retryable
  no-URL rows only.
- [x] Add focused tests for retry parser behavior and source/layout wiring.
- [x] Run compile, focused JVM tests, connected worker tests, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 51
- Tried to spawn a read-only performance reviewer, but the thread is still at the agent limit.
  Existing agents were not closed, honoring the user's explicit correction to let them finish.
- Added a `Retry no URL` action next to `Review details` in the Discover FilterLists last-run
  action row. The action appears only when the durable last-run ledger contains retryable
  `SKIPPED_NO_URL` entries with real FilterLists IDs.
- `DiscoverFilterListsFragment` now parses retryable no-URL IDs from the persisted ledger,
  removes `listUrl_<id>` negative-cache entries from `filterlists_cache`, clears the in-memory
  resolved URL cache, and requeues `FilterListsSubscribeAllWorker` with an explicit ID scope.
- Unsupported browser-rule rows are not retried by this action; they still require manual review
  because retrying them would only reproduce the compatibility skip.
- Added `DiscoverPresetSubscriptionTest` coverage for the retry action wiring and
  `parseRetryableNoUrlIds(...)`, including duplicate, zero, unsupported, and malformed ID cases.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --dependency-verification=strict --stacktrace`.
- Focused connected verification passed:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest,
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest
  --dependency-verification=strict --stacktrace`; Gradle reported 4 tests, 0 failures.
- `git diff --check` passed with only existing CRLF conversion warnings.
- Remaining gap: unsupported FilterLists rows still need a better manual-review/manual-add path,
  and the broader market-leading objective still has performance, release, and MIT blockers.

## Plan - 2026-06-14 Goal Continuation 52
- [x] Keep existing expert agents open while consuming the completed review findings.
- [x] Validate the returned P0 stale-root-export finding against current runtime-cache code.
- [x] Add a dependency-verification fallback audit for unresolved signing-key debt.
- [x] Extend `SecurityHardeningTest` so dependency-verification fallback counts cannot grow
  without updating the audit.
- [x] Run focused security tests, release runtime dependency verification, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 52
- Existing agents were not closed. The active thread was already at the agent limit, so no new
  sidecar reviewer could be spawned without violating the user's correction to let existing agents
  finish.
- Rechecked the reported stale-root-export concern against current `SourceModel` and
  `HostEntryDao` behavior. The current implementation rebuilds runtime rows from active
  generation truth; the returned finding appears stale against this worktree rather than a new P0.
- Added `docs/dependency-verification-audit.md` as the human review record for strict Gradle
  dependency-verification fallback debt. It records the current unresolved-key counts, reviewed
  dependency families, and rules against key-server lookup, hash-only downgrade, and broad key
  trust.
- Extended `SecurityHardeningTest.atk34_dependencyVerificationFallbacksAreAudited()` to require
  the audit file and to keep the ignored-key and key-download fallback counts synchronized with
  `gradle/verification-metadata.xml`.
- Fresh focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh release runtime dependency verification passed:
  `:app:dependencies --configuration releaseRuntimeClasspath --dependency-verification=strict
  --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Remaining gap: this closes one release/supply-chain review item, but the broader market-leading
  objective still has open performance scale gates, unsupported FilterLists manual-review UX, and
  MIT relicensing blockers.

## Plan - 2026-06-14 Goal Continuation 53
- [x] Try to dispatch an additional focused UX/QA sidecar reviewer without closing existing
  agents.
- [x] Inspect the current Discover FilterLists review, retry, row-click, and test coverage.
- [x] Replace unsupported-row dead-end behavior with an explicit manual-review dialog.
- [x] Add a last-run unsupported review action backed by the durable worker ledger.
- [x] Keep unsupported lists out of automatic subscription while offering an explicit manual-add
  route only after warning copy and URL resolution.
- [x] Run focused JVM/resource verification, debug/androidTest compile, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 53
- A new sidecar reviewer could not be spawned because the thread is still at the agent limit.
  Existing agents were not closed.
- `DiscoverFilterListsFragment` now opens unsupported rows in a manual-review dialog instead of
  ending at a snackbar. The dialog fetches current FilterLists details, explains why AdAway did
  not auto-subscribe the list, and only offers `Add manually` when a direct usable URL exists.
- The existing source-editor path was factored into `openSourceEditForFilterList(...)` so both
  compatible direct adds and explicit manual adds preserve FilterLists provenance extras.
- The durable last-run action row now includes `Review unsupported`; it parses
  `SKIPPED_UNSUPPORTED` IDs from the worker ledger, lets the user choose a loaded unsupported
  list, and then opens the same manual-review flow.
- The last-run action row now stacks actions vertically so `Review details`, `Retry no URL`, and
  `Review unsupported` do not clip on narrow screens.
- Added focused Discover tests for the unsupported action wiring, manual-review/manual-add path,
  and unsupported ledger parser.
- Fresh focused JVM/resource verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- There are no Discover-specific connected instrumentation tests in `app/src/androidTest`; the
  current device tests cover the FilterLists worker path rather than this dialog UI.
- Remaining gap: this closes the unsupported FilterLists manual-review UX gap, but broader
  performance scale gates, final release hardening, and MIT relicensing blockers remain open.

## Plan - 2026-06-14 Goal Continuation 54
- [x] Confirm the importer already uses staged SQL dedup and identify remaining dead per-row
  dedup symbols.
- [x] Remove unused `dedupStmt` plumbing from `SourceLoader`.
- [x] Remove the obsolete `SqlUpdateDeduper.compileInsertStatement()` and `markSeen(...)`
  per-row seen-table API.
- [x] Add a focused source-level regression guard for the set-based staged dedup path.
- [x] Run strict JVM, Java compile, connected SQL-dedup, and diff hygiene verification.

## Review - 2026-06-14 Goal Continuation 54
- The staged importer already passed `compilePendingInsertStatement()` into
  `bulkInsert(...)`; the old `dedupStmt` path was always null and therefore dead. Removing it
  makes the hot-path shape unambiguous for future performance work.
- `SqlUpdateDeduper` now exposes only the pending-row staging API plus the set-based
  `flushPendingRowsToHostsLists()` path for parsed rows. Carry-forward still marks copied
  generation rows through set-based SQL inside `copyUnseenSourceGeneration(...)`.
- `Generation304MigrationTest.sourceLoader_usesSetBasedSqlDedupFlushOnly()` now guards against
  reintroducing the old per-row seen-table statement/call path.
- Fresh focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh connected SQL-dedup verification passed on `emulator-5554`:
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=
  org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_preservesSourceOwnershipAndKeepsRedirectTargets,
  org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_carryForwardSkipsAlreadySeenRowsAndMarksCopiedRows,
  org.adaway.model.source.SourceLoaderPerformanceTest#sqlUpdateDeduper_largeRootExportPrefersRedirectOverBlockedDuplicate
  --dependency-verification=strict --stacktrace`; Gradle reported 3 tests and `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings, and manual
  trailing-whitespace checks passed.
- Remaining gap: this is a simplicity/performance-risk cleanup, not the final 5M-rule proof.
  Broader scale benchmarks, final release hardening, and MIT relicensing blockers remain open.

## Plan - 2026-06-14 Goal Continuation 55
- [x] Poll existing expert agents without closing them.
- [x] Triage completed expert findings against current worktree state.
- [x] Pick a bounded market-leading product gap that is not blocked on the 5M runtime migration.
- [x] Make FilterLists.com provenance affect My Lists category grouping, not just row subtitle
  text.
- [x] Add focused JVM regression coverage and run strict verification.

## Review - 2026-06-14 Goal Continuation 55
- Existing agents were left open. Completed expert findings were consumed from the running swarm
  without closing any agent.
- The FilterLists/product reviewer finding about lost directory identity was partially stale:
  this worktree already had durable `HostsSource` FilterLists metadata, worker persistence,
  source-editor extras, and source-row provenance text. The remaining concrete gap was grouping:
  `CategorizedSourcesAdapter` and `FilterListItem.SourceItem` still categorized by URL-only
  static catalog matching, so metadata-backed directory subscriptions could still land in
  `Custom`.
- Added a dedicated `FilterListCategory.FILTERLISTS` category labeled `FilterLists.com`, expanded
  by default in My Lists.
- Added `FilterListCatalog.getCategoryForSource(...)`, which keeps user sources under `My Lists`,
  prefers durable FilterLists metadata over URL matching, and otherwise falls back to the static
  catalog URL category.
- Updated `CategorizedSourcesAdapter` grouping and category bulk toggles, plus
  `FilterListItem.SourceItem`, to use the source-aware resolver.
- Added focused JVM coverage in `CategorizedSourcesAdapterTest` for FilterLists metadata category
  precedence, user-source precedence, and static catalog fallback.
- Fresh focused JVM/resource verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.CategorizedSourcesAdapterTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings. Manual
  trailing-whitespace scan still reports older whitespace in `FilterListCategory.java` and
  `FilterListItem.java`, but no added line is flagged by `git diff --check`.
- Remaining full-goal gaps: 5M allow-heavy runtime/root rebuild still needs a deeper architecture
  and proof; release manifest publication and artifact absence reports are still not fully
  executed with real secrets; presets still need durable active-profile state; MIT remains blocked
  by GPL-derived packaged app/VPN code and assets until rewritten, removed, permission-cleared, and
  legally reviewed.

## Plan - 2026-06-14 Goal Continuation 56
- [x] Poll the open expert swarm without closing any agents.
- [x] Ask the product/FilterLists expert for a read-only follow-up on preset/profile state.
- [x] Add durable active-profile storage on top of existing `FilterSetStore` shared preferences.
- [x] Persist quick-preset and catalog-preset memberships as named profile sets.
- [x] Reconcile manual source/catalog changes to a custom active profile.
- [x] Add a Discover profile-status line backed by a pure profile-state resolver.
- [x] Run focused preset/profile tests, resource generation, compile, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 56
- Existing agents were not closed. The product/FilterLists expert returned a follow-up confirming
  durable preset profile state as the next high-impact product gap; its P0 finding matched the
  local implementation target.
- `FilterSetStore` now has named built-in profiles (`safe`, `balanced`, `aggressive`, `custom`),
  active-profile persistence, preset-profile saving, custom-profile saving, and profile-name
  normalization without a Room migration.
- `DiscoverFragment` quick preset application now persists the full preset URL membership and
  active profile after adding or re-enabling sources. It also shows a compact profile status under
  the quick-start chips.
- Added `FilterProfileState` with `NONE`, `EXACT`, `EXTENDED`, and `PARTIAL` so Discover can show
  `Profile: Balanced Mode`, `Profile: Balanced Mode + custom`, or
  `Profile: Modified from Balanced Mode` based on current enabled URLs versus the saved profile
  baseline.
- `DiscoverCatalogFragment` now preserves preset identity when a preset chip selects missing rows:
  applying the selection saves the full preset URL set, including already-added rows. Manual
  select-all or row changes clear preset identity and save `custom`.
- `HostsSourcesFragment` now marks manual source toggles as custom and records an applied saved
  set as the active profile.
- Added focused JVM coverage in `FilterSetStoreTest`, `FilterProfileStateTest`, and
  `DiscoverPresetSubscriptionTest` for profile normalization, profile relationship states, quick
  preset persistence, catalog preset/custom reconciliation, source toggle reconciliation, and the
  visible Discover profile status.
- Fresh focused JVM/resource verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --tests org.adaway.ui.hosts.FilterSetStoreTest
  --tests org.adaway.ui.hosts.FilterProfileStateTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for touched files with only CRLF conversion warnings. Manual
  trailing-whitespace scan still reports older whitespace in `HostsSourcesFragment.java`, but the
  lines touched in this continuation are clean.
- Remaining full-goal gaps: 5M allow-heavy runtime/root rebuild still needs deeper architecture
  and proof; release manifest publication and artifact absence reports are still not fully
  executed with real secrets; FilterLists compatibility/risk disclosure can still be richer; MIT
  remains blocked by GPL-derived packaged app/VPN code and assets until rewritten, removed,
  permission-cleared, and legally reviewed.

## Plan - 2026-06-14 Goal Continuation 57
- [x] Refresh the release/security workflow state without closing existing expert agents.
- [x] Reconcile saved release-review findings against the current dirty worktree.
- [x] Pick the remaining bounded CI coverage gap instead of duplicating existing artifact
  hardening work.
- [x] Add pull-request and manual-dispatch triggers for Android CI and CodeQL security analysis.
- [x] Add a focused `SecurityHardeningTest` guard for the new workflow trigger contract.
- [x] Run strict focused security tests, Java compile gates, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 57
- Existing expert agents were left open. Local inspection showed the saved release note was partly
  stale: this worktree already has signed update manifest generation, SBOM/checksum release
  assets, attestation, strict release license-boundary checks, and artifact APK/SBOM boundary
  tests.
- The remaining concrete release-security gap was trigger coverage. `android-ci.yml` now runs on
  `pull_request` to `master` and `workflow_dispatch`, so license-boundary checks, unit tests,
  lint, debug assembly, and connected Android tests protect proposed changes and can be rerun on
  demand.
- `android-analysis.yml` now runs on `push`, `pull_request`, the existing weekly schedule, and
  `workflow_dispatch`, so CodeQL C/C++ and Java analysis is not limited to scheduled drift scans.
- `SecurityHardeningTest.atk35_securityWorkflowsRunOnPullRequestsAndManualDispatch()` now guards
  the CI and CodeQL trigger contract.
- Fresh focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: 5M allow-heavy runtime/root rebuild still needs deeper architecture
  and proof; release workflows still need a real tagged run with production secrets before claiming
  live manifest publication; FilterLists compatibility/risk disclosure can still be richer; MIT
  remains blocked by GPL-derived packaged app/VPN code and assets until rewritten, removed,
  permission-cleared, and legally reviewed.

## Plan - 2026-06-14 Goal Continuation 58
- [x] Reuse the existing expert swarm instead of closing agents or spawning beyond the thread
  limit.
- [x] Inspect the current FilterLists compatibility and Discover row state.
- [x] Add a pure compatibility disclosure model for row summaries, full capability text, and
  persisted syntax-id decoding.
- [x] Surface capability/skipped-semantics disclosure in Discover rows and unsupported-review
  dialogs.
- [x] Preserve the same compatibility disclosure after subscription in My Lists source rows.
- [x] Run focused compatibility/Discover/source-row tests, compile gates, and diff hygiene.

## Review - 2026-06-14 Goal Continuation 58
- The agent pool was already full, so existing product and performance reviewers were reused and
  left open. The product reviewer confirmed a follow-up gap after the first patch: Discover rows
  disclosed compatibility, but persisted source rows still collapsed back to raw
  `DNS-safe`/`Domain extraction only` metadata.
- `FilterListCompatibility` now exposes concise row summaries, full capability summaries, and
  safe decoding for persisted FilterLists syntax-id metadata. Unsupported browser-rule syntaxes
  now disclose that AdAway performs domain extraction only and skips exceptions, redirects,
  path/options rules, cosmetics, scriptlets, and unsafe-to-flatten rules.
- `DiscoverFilterListsFragment` now appends capability detail to every directory row description,
  includes it in accessibility copy, and shows the same capability detail in unsupported manual
  review before offering manual add.
- `filterlists_import_item.xml` now gives the description text three lines so the list description
  and capability disclosure have room without overlapping row controls.
- `FilterListItem.SourceItem` now carries existing FilterLists syntax IDs and compatibility score
  into `CategorizedSourcesAdapter`. My Lists provenance now renders durable summaries such as
  `FilterLists.com | DNS-safe: exact hosts and plain domains | 12 skipped`,
  `FilterLists.com | Manual review: browser semantics skipped`, or
  `FilterLists.com | Manual review: unknown syntax`.
- Added focused coverage in `FilterListCompatibilityTest`, `DiscoverPresetSubscriptionTest`, and
  `CategorizedSourcesAdapterTest` for safe/unsupported/unknown capability disclosure and
  persisted source-row summaries.
- Fresh focused JVM/resource verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.FilterListCompatibilityTest
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --tests org.adaway.ui.hosts.CategorizedSourcesAdapterTest
  --tests org.adaway.model.source.FilterListsSourceMetadataTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- The performance reviewer identified the next concrete 5M-proof slice: add a
  stage-seeded allow-heavy root export benchmark switch so the existing benchmark proves the
  `root_host_entries_stage` path instead of falling back to direct `hosts_lists` copying.
- Remaining full-goal gaps: 1M/5M root export performance still needs the stage-seeded benchmark
  and scale proof; release workflows still need a real tagged run with production secrets before
  claiming live manifest publication; MIT remains blocked by GPL-derived packaged app/VPN code and
  assets until rewritten, removed, permission-cleared, and legally reviewed.

## Plan - 2026-06-14 Goal Continuation 59
- [x] Continue the performance-review slice without closing the existing expert agents.
- [x] Add a red source-level guard proving the allow-heavy benchmark can seed
  `root_host_entries_stage`.
- [x] Add the benchmark switch and SQL seeding path for staged root export candidates.
- [x] Run a connected smoke benchmark with `adawayAllowRebuildSeedRootStage=true`.
- [x] Add a cheap instrumentation proof for the large-runtime staged export branch without
  inserting 500,001 rows.
- [x] Run focused unit, compile, connected instrumentation, and diff hygiene checks.

## Review - 2026-06-14 Goal Continuation 59
- The delay came from Android verification, not code churn: Gradle had to build/install test APKs,
  route PowerShell-safe instrumentation arguments, drive the API 34 emulator, and collect XML and
  logcat evidence.
- `SourceLoaderPerformanceTest` now accepts
  `adawayAllowRebuildSeedRootStage=true`, seeds `root_host_entries_stage` with the allow-heavy
  fixture, asserts the staged row count, and prints `seedRootStage=` plus `stageRows=` in both
  seed and rebuild benchmark lines.
- `Generation304MigrationTest.allowHeavyBenchmarkCanSeedRootExportStagePath()` guards the new
  benchmark contract. It failed red before the implementation and passed after the benchmark
  switch and seeding helper were added.
- The connected smoke benchmark passed on `adaway-api34(AVD) - 14` with `blockedRows=1000`; XML
  reported 1 test, 0 failures, and logcat showed `seedRootStage=true stageRows=1000`.
- Since the large-runtime branch only activates above
  `HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS` (`500_000`), `SourceDbTest` now includes
  `testLargeRuntimeSkipUsesCompleteRootExportStage()`. It inflates source stats above the
  threshold, seeds a tiny complete root export stage, calls
  `rebuildFromActiveGeneration(writableDb)` in a transaction, and proves the staged branch skips
  `host_entries`, materializes only staged root rows, honors user allow rules, keeps redirect
  precedence, and excludes an active `hosts_lists` fallback-only row.
- Fresh connected instrumentation verification passed:
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage"
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL` and XML
  reported 1 test, 0 failures, 0 errors, 0 skipped.
- Fresh focused JVM guard passed:
  `:app:testDebugUnitTest
  --tests org.adaway.model.source.Generation304MigrationTest.allowHeavyBenchmarkCanSeedRootExportStagePath
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh Android test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: 1M/5M connected benchmark runs still need scheduled scale proof and
  budget thresholds; release workflows still need a real tagged run with production secrets before
  claiming live manifest publication; MIT remains blocked by GPL-derived packaged app/VPN code and
  assets until rewritten, removed, permission-cleared, and legally reviewed.

## Plan - 2026-06-14 Goal Continuation 60
- [x] Reuse the existing expert agents for performance, QA/devil-advocate, and product/filter
  sidecar reviews without closing them.
- [x] Run fresh stage-seeded 100k and 501k allow-heavy connected benchmarks.
- [x] Run the 1M stage-seeded benchmark against the 120s sync gate and capture the failure.
- [x] Remove unnecessary redirected-row scans when no active redirected rules exist.
- [x] Strengthen root export stage completeness so partial stages fall back to active truth and
  disabled-source stage rows cannot leak.
- [x] Run focused JVM, compile, connected correctness, 1M performance, and diff-hygiene checks.

## Review - 2026-06-14 Goal Continuation 60
- The performance reviewer recommended running 100k, 501k, and 1M stage-seeded benchmarks one at
  a time, manually confirming `root-export-stage` at 501k/1M and recording stage row counts,
  `syncMs`, `rootCursorMs`, and phase timings.
- Fresh 100k stage-seeded benchmark passed as a below-threshold sanity run:
  `HostEntryAllowHeavyRebuildBenchmark ... runtimeRows=98000 rootRows=98000
  materializedRuntimeCache=true seedRootStage=true stageRows=100000 syncMs=2568
  rootCursorMs=1540`.
- Fresh 501k stage-seeded benchmark crossed the `500_000` runtime-cache cutoff and passed the
  explicit `sync=120000ms`, `rootCursor=30000ms` gates:
  `HostEntryDao.root-export-stage perf ... blockedMs=900 indexCreateMs=2568 allowMs=448
  redirectedMs=177 dedupeMs=3621 totalMs=7797`;
  final line `runtimeRows=0 rootRows=490980 materializedRuntimeCache=false
  seedRootStage=true stageRows=501000 syncMs=14505 rootCursorMs=4453`.
- The first 1M stage-seeded benchmark failed the 120s sync gate by a narrow but real margin:
  `adawayAllowRebuildSyncBudgetMs exceeded 120000ms: 122364ms`. Phase timing showed the bug:
  `redirectedMs=70519` despite the allow-heavy fixture having zero redirected rules.
- `HostEntryDao` now reads `hosts_stats.redirected_count` and passes `hasRedirectRules` into
  large direct/staged root-export materialization. When the count is zero, both direct and staged
  paths skip redirected-row insertion instead of scanning/grouping the full candidate table.
- QA found a correctness hole in the first staged proof: the old completeness check only required
  at least one staged row per enabled source and could accept a partial stage. `HostEntryDao` now
  compares enabled non-user staged rows against enabled source `blocked_count + redirected_count`;
  mismatches fall back to active `hosts_lists` truth.
- QA also flagged disabled-source leakage. Staged blocked/redirect inserts now filter non-user
  rows through enabled `hosts_sources`, so stale stage rows from disabled sources cannot export.
- `SourceDbTest` now covers the complete-stage path, incomplete-stage fallback to active truth,
  and disabled-source stage filtering. The complete-stage test still proves redirect precedence,
  user exact allow, user suffix allow, empty `host_entries`, materialized root output, and
  list/cursor parity.
- Final 1M stage-seeded benchmark passed on the final code with the same 120s/30s gates:
  `HostEntryDao.root-export-stage perf ... blockedMs=1664 indexCreateMs=8404 allowMs=892
  redirectedMs=0 dedupeMs=6849 totalMs=17853`;
  final line `runtimeRows=0 rootRows=980000 materializedRuntimeCache=false
  seedRootStage=true stageRows=1000000 syncMs=34006 rootCursorMs=13109`. XML reported 1 test,
  0 failures, 0 errors, 0 skipped.
- Fresh focused verification passed:
  `:app:testDebugUnitTest
  --tests org.adaway.model.source.Generation304MigrationTest.largeRootExportSkipsRedirectPhaseWhenNoRedirectRules
  --dependency-verification=strict --stacktrace`;
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  and connected
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage,org.adaway.db.SourceDbTest#testIncompleteRootExportStageFallsBackToActiveRules,org.adaway.db.SourceDbTest#testCompleteRootExportStageIgnoresDisabledSources"
  --dependency-verification=strict --stacktrace`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- The product/filter reviewer identified the next small market-quality slice: My Lists saved
  profile application needs an impact preview/confirmation because applying a set can silently
  disable currently enabled protection lists.
- Remaining full-goal gaps: 5M stage-seeded allow-heavy proof is still not rerun after this
  optimization; total artificial benchmark seed time is not budgeted; release workflows still
  need a real tagged run with production secrets before claiming live manifest publication; MIT
  remains blocked by GPL-derived packaged app/VPN code and assets until rewritten, removed,
  permission-cleared, and legally reviewed.

## Plan - 2026-06-14 Goal Continuation 61
- [x] Answer the user's delay question with concrete current blockers instead of hand-waving.
- [x] Reuse the existing expert agents and wait for their saved-profile apply findings without
  closing them.
- [x] Add a red profile-impact test for saved filter set application.
- [x] Add a saved-set apply preview that reports enabled, disabled, kept-enabled, missing, and
  downgrade-warning impact before any source mutation.
- [x] Route confirmed saved-set source/list enablement through a DAO transaction helper.
- [x] Run focused unit, Java compile, androidTest compile, and diff hygiene checks.

## Review - 2026-06-14 Goal Continuation 61
- Three reused agents independently flagged the same market-quality issue: choosing a saved My
  Lists profile could immediately disable currently enabled protection lists with no impact
  preview.
- `FilterProfileDiff` now computes the saved-profile apply impact as pure Java: lists to enable,
  lists to disable, currently enabled lists that stay enabled, missing saved URLs, and whether
  protection is weakened.
- `HostsSourcesFragment` now routes saved-set picker selections through
  `previewApplyFilterSet(...)` and `showApplyFilterSetConfirmation(...)`; only the positive
  confirmation path calls `applyFilterSet(...)`.
- The preview copy now discloses disabled list count, missing saved URLs, and an explicit warning
  when applying the profile will turn off currently enabled lists.
- `HostsSourceDao.applySourceSelection(...)` wraps `hosts_sources.enabled` and
  `hosts_lists.enabled` updates in a Room transaction, and confirmed saved-profile apply uses
  that helper.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: empty-profile apply still needs a more explicit "disable all lists"
  confirmation; stale `lastSources` should eventually be replaced by a fresh DAO snapshot before
  applying; pluralized strings and screenshot/accessibility coverage are still needed; 5M
  performance proof and release/live-signing proof remain open; MIT remains blocked by GPL-derived
  packaged app/VPN/native/assets until rewritten, removed, permission-cleared, and legally
  reviewed.

## Plan - 2026-06-14 Goal Continuation 62
- [x] Reuse existing QA, UX, and security agents for the saved-profile apply hardening follow-up.
- [x] Add red tests for empty-profile disable-all risk, missing-only profile state, missing set
  refusal, frozen preview URL apply, and profile-wide DAO transaction ownership.
- [x] Split missing/corrupt saved profile data from intentionally empty saved profiles.
- [x] Bind confirmation to the URL set that was previewed instead of re-reading profile URLs
  after the user confirms.
- [x] Move confirmed saved-profile source/list enablement into a fresh DAO read wrapped by a
  Room transaction.
- [x] Run focused unit, store unit, Java compile, androidTest compile, and diff hygiene checks.

## Review - 2026-06-14 Goal Continuation 62
- The agents confirmed the remaining risks in the saved-profile flow: empty and missing profile
  data were indistinguishable, the previewed URL set could differ from the confirmed apply URL
  set, `lastSources` was stale UI state, and profile apply was only per-source transactional.
- `FilterProfileDiff` now identifies empty profiles, missing-only profiles, and disable-all local
  impact while keeping the diff pure and JVM-testable.
- `FilterSetStore.hasSetUrls(...)` lets the UI distinguish a saved empty set from a stale/corrupt
  name whose `set_<name>` data is missing.
- `HostsSourcesFragment.previewApplyFilterSet(...)` now refuses missing profile data with
  `filter_set_missing`, sends intentionally empty profiles through a dedicated "Disable all filter
  lists?" confirmation, and passes the previewed `targetUrls` into the confirmed apply path.
- `HostsSourceDao.applySourceSelections(...)` performs a fresh `getAll()` inside a Room
  transaction and applies all source/list enablement changes together; the fragment now delegates
  the DB mutation to that method.
- Fresh TDD red evidence: the focused unit run failed before implementation because
  `FilterProfileDiff.isEmptyProfile()`, `disablesAllEnabledSources()`, and
  `isMissingOnlyProfile()` did not exist.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh store verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: active profile identity for arbitrary named sets needs a focused
  follow-up; no-op/missing-only copy still needs better UX semantics; pluralized strings and
  screenshot/accessibility coverage are still needed; 5M performance proof and release/live
  signing proof remain open; MIT remains blocked by GPL-derived packaged app/VPN/native/assets
  until rewritten, removed, permission-cleared, and legally reviewed.

## Plan - 2026-06-14 Goal Continuation 63
- [x] Reuse existing QA, UX, and security agents for saved-profile identity/copy review.
- [x] Add red tests for arbitrary saved-set active identity preservation and no-op/missing-only
  copy branching.
- [x] Preserve arbitrary saved set names as active profile identity while keeping preset names
  normalized.
- [x] Add a dedicated missing-only profile confirmation path.
- [x] Add close-only exact no-op handling and explicit "Set active" handling for identity-only
  applies.
- [x] Run focused unit, compile, and diff hygiene verification.

## Review - 2026-06-14 Goal Continuation 63
- QA found a real identity bug: `saveSet()` stored user-created names exactly, but
  `setActiveProfile()` lowercased them, so applying `Family Pack` could persist `family pack`
  and later fail to resolve the saved URLs.
- `FilterSetStore.normalizeActiveProfile(...)` now trims and preserves arbitrary saved set
  identity; `normalizePresetProfile(...)` still lowercases and accepts only safe, balanced, and
  aggressive preset keys.
- `HostsSourcesFragment.previewApplyFilterSet(...)` now routes missing-only profiles through a
  dedicated confirmation instead of generic apply copy.
- Exact no-op applies now branch on active saved-set identity: if the selected set is already
  active, the dialog has only `Close`; if the URLs match but another set is active, the dialog says
  it will make the selected set active and uses a `Set active` positive action.
- Fresh TDD red evidence: the focused unit run failed before implementation because
  `FilterSetStore.normalizeActiveProfile(...)` did not exist and the source guard could not find
  the no-op active-set branch.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; XML reported 8 tests, 0 failures, 0 errors.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: profile identity still ultimately needs opaque IDs instead of display
  names; duplicate/reserved profile names need a separate guard; pluralized strings,
  screenshot/accessibility coverage, 5M performance proof, release/live-signing proof, and MIT
  relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 64
- [x] Reuse existing QA/UX agents for saved-set name collision review without closing them.
- [x] Add red tests for canonical saved-set names, reserved system names, and case/whitespace
  duplicate detection.
- [x] Preserve cleaned display names while using canonical names only for validation.
- [x] Block user-saved names that collide with built-in profiles, `custom`, and
  `current selection`.
- [x] Block duplicate user-saved names that differ only by case or whitespace.
- [x] Run focused unit, compile, and diff hygiene verification.

## Review - 2026-06-14 Goal Continuation 64
- The UX reviewer confirmed the correct next rule: saved-set display names may keep user casing,
  but validation must compare a canonical key built from trim, whitespace collapse, and
  lowercase.
- `FilterSetStore.canonicalSetName(...)` now provides that canonical comparison key.
- `FilterSetStore.isReservedSetName(...)` rejects `custom`, `safe`, `balanced`, `aggressive`,
  their `* mode` variants, and `current selection` so user sets cannot spoof or overwrite system
  profile concepts.
- `FilterSetStore.hasCanonicalSetName(...)` detects duplicate display names across case and
  whitespace variants.
- `FilterSetStore.saveSet(...)` now stores names under the cleaned display name, not raw typed
  whitespace, keeping storage keys aligned with active identity.
- `HostsSourcesFragment.promptSaveFilterSet(...)` now refuses reserved and duplicate names before
  saving and shows targeted snackbars.
- Fresh TDD red evidence: the first focused run failed before implementation because
  `canonicalSetName(...)`, `isReservedSetName(...)`, and `hasCanonicalSetName(...)` did not exist;
  the tightened follow-up run failed until whitespace collapse and `Safe Mode`/`Balanced Mode`/
  `Aggressive Mode` reservations were implemented.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; XML reported 10 tests, 0 failures, 0 errors.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: profile identity still ultimately needs opaque IDs instead of display
  names; duplicate-name rejection should eventually become inline field errors instead of
  snackbar-after-dialog; pluralized strings, screenshot/accessibility coverage, 5M performance
  proof, release/live-signing proof, and MIT relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 65
- [x] Inspect current saved-set SharedPreferences key usage and schedule/update call sites.
- [x] Reuse existing QA/security/UX agents for opaque-ID and name-collision review without
  closing them.
- [x] Add red tests for canonical saved-set comparison, reserved built-in labels, and
  case/whitespace duplicate detection.
- [x] Implement the minimal compatibility step before full opaque IDs: clean display names,
  canonical comparison, reserved-name blocking, and duplicate-name blocking.
- [x] Run focused unit, compile, and diff hygiene verification.

## Review - 2026-06-14 Goal Continuation 65
- Current storage still uses display names as SharedPreferences keys, so a full opaque-ID
  migration remains open. This continuation reduced the immediate spoof/collision risk without
  breaking existing `names`, `set_<name>`, `schedule_<name>`, and active-profile callers.
- `FilterSetStore.normalizeActiveProfile(...)` now collapses internal whitespace as well as
  trimming, so the display key saved by `saveSet(...)` matches the active identity used elsewhere.
- `FilterSetStore.canonicalSetName(...)` gives validation a stable comparison key without
  changing the display name shown to users.
- `FilterSetStore.isReservedSetName(...)` now rejects built-in profile labels including `safe`,
  `balanced`, `aggressive`, `custom`, `current selection`, and the `Safe Mode`/`Balanced Mode`/
  `Aggressive Mode` variants.
- `FilterSetStore.hasCanonicalSetName(...)` blocks duplicates such as `Family Pack`,
  ` family   pack `, and `FAMILY PACK` before they can create confusing separate keys.
- `HostsSourcesFragment.promptSaveFilterSet(...)` now refuses reserved or duplicate names before
  calling `saveFilterSet(...)`, with targeted error messages.
- Fresh TDD red evidence: the focused run failed before implementation because
  `canonicalSetName(...)`, `isReservedSetName(...)`, and `hasCanonicalSetName(...)` did not exist;
  a follow-up red run failed until internal whitespace collapse and `* Mode` reserved names were
  implemented.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; XML reported 10 tests, 0 failures, 0 errors.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: true opaque profile IDs and migration still need a larger slice;
  inline field validation should replace snackbar-after-dialog; pluralized strings,
  screenshot/accessibility coverage, 5M performance proof, release/live-signing proof, and MIT
  relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 66
- [x] Inspect current saved-set save dialog and validation helpers.
- [x] Reuse existing QA/UX agents for inline validation review without closing them.
- [x] Add red tests for validation result classification and dialog behavior guards.
- [x] Implement inline validation that keeps the save dialog open on errors.
- [x] Run focused unit, compile, XML-result, and diff hygiene verification.

## Review - 2026-06-14 Goal Continuation 66
- QA and UX reviewers found the concrete flaw in the previous continuation: the save dialog used
  `AlertDialog.Builder.setPositiveButton(...)`, so reserved, duplicate, and empty-name failures
  could still auto-dismiss the dialog while showing feedback outside the field.
- `FilterSetStore.SetNameValidation` and `FilterSetStore.validateSetName(...)` now classify
  empty, reserved, duplicate, and valid names through one shared helper.
- `HostsSourcesFragment.promptSaveFilterSet(...)` now creates the dialog explicitly, overrides
  the positive button in `setOnShowListener(...)`, shows `TextInputLayout` inline errors, keeps the
  dialog open on invalid input, and only dismisses after a valid save.
- `filter_set_name_empty` was added alongside the reserved and duplicate name errors.
- Fresh TDD red evidence: the first focused run failed before implementation because
  `FilterSetStore.SetNameValidation` and `validateSetName(...)` did not exist.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`.
- Combined saved-profile verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; XML reported 11 tests, 0 failures, 0 errors.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle reported `BUILD SUCCESSFUL`.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: true opaque profile IDs and migration, TextWatcher or Robolectric
  dialog coverage, pluralized strings, screenshot/accessibility coverage, 5M performance proof,
  release/live-signing proof, and MIT relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 67
- [x] Audit saved filter profile storage and call sites for display-name key coupling.
- [x] Design the smallest compatible stable-ID migration path.
- [x] Add red tests for profile identity stability and migration wiring.
- [x] Implement profile-id storage for saved URLs, schedules, last-run timestamps, and active
  profile state while preserving legacy display-name reads.
- [x] Run focused unit, compile, XML-result, and diff hygiene verification.

## Review - 2026-06-14 Goal Continuation 67
- The storage audit confirmed saved filter profiles were still keyed directly by display name in
  URLs, schedules, last-run timestamps, and active profile state. That made visible text part of
  the data model.
- `FilterSetStore` now maintains stable profile ids with display-name metadata and canonical
  lookup metadata: `ids`, `display_<id>`, `canonical_<name>`, `set_id_<id>`,
  `schedule_id_<id>`, `last_run_id_<id>`, and `active_profile_id`.
- Built-in profiles get deterministic reserved ids (`profile_safe`, `profile_balanced`,
  `profile_aggressive`, `profile_custom`); user-saved profiles get opaque UUID-shaped ids that do
  not expose the display name in preference keys.
- Legacy display-name-keyed data is migrated lazily on read, including URL sets, schedule cadence,
  schedule time/day, last-run timestamp, and active profile id. Legacy keys are left readable for
  compatibility.
- Self-review found and fixed a migration efficiency issue before final verification: the metadata
  helper now only reports changes when ids, display metadata, or canonical metadata actually differ,
  avoiding a write-on-every-read migration path.
- Fresh TDD red evidence: the first focused run failed before implementation because
  `reservedProfileId(...)`, `newOpaqueProfileIdForTest(...)`, and
  `profileValueKeyForTest(...)` did not exist.
- Fresh focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.quickPresetPersistsDurableActiveProfile
  --dependency-verification=strict --stacktrace`.
- Combined saved-profile verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.quickPresetPersistsDurableActiveProfile
  --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; XML reported 13 tests, 0 failures, 0 errors.
- Fresh compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle exited 0.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: instrumentation coverage for actual SharedPreferences migration,
  rename/delete UI over stable ids, TextWatcher or Robolectric dialog coverage, pluralized strings,
  screenshot/accessibility coverage, 5M performance proof, release/live-signing proof, and MIT
  relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 68
- [x] Reuse existing QA, UX, and security agents for saved-profile migration and release-risk
  review.
- [x] Add real Android SharedPreferences instrumentation coverage for stable profile-id migration.
- [x] Fix reviewer-found P0 identity bugs in named saves and current-selection scheduling.
- [x] Harden legacy migration for one-pass active-profile IDs, current-selection legacy data,
  canonical duplicate legacy names, idempotency, and random new user-profile IDs.
- [x] Run focused unit, compile, emulator instrumentation, XML-result, and diff hygiene
  verification.

## Review - 2026-06-14 Goal Continuation 68
- The existing agent pool was full, so new agents could not be spawned; existing QA, UX, and
  security agents were reused without closing them.
- UX found two P0 profile identity bugs. First, saving a named set still called
  `saveCustomProfile(...)`, which overwrote and activated `Custom`. Second, scheduling
  `Current selection` persisted the visible placeholder label, which maps to `profile_custom`.
- `HostsSourcesFragment.saveFilterSet(...)` now saves named sets directly and makes the named set
  active without rewriting `Custom`.
- Scheduling current selection now creates a real non-reserved `Scheduled selection` saved set name,
  with suffixes when needed, before opening the schedule picker.
- `FilterSetStoreMigrationTest` now runs on Android against real SharedPreferences and covers:
  legacy display-name migration to stable id keys, new stable-key writes, deterministic reserved
  profile IDs, legacy `Current selection` not renaming `Custom`, and canonical duplicate legacy
  names remaining separately reachable.
- QA found that legacy active profile migration was two-pass and canonical duplicate legacy names
  could collapse through one `canonical_<name>` key. Security found that new user profile IDs were
  deterministic from display names despite being called opaque.
- `FilterSetStore` now writes `active_profile_id` during the first migration entry point, renames
  legacy `Current selection` to `Scheduled selection`, disambiguates canonical duplicate legacy
  names with `(legacy N)` suffixes, removes the legacy `names` index after migration so migration is
  idempotent, and uses random UUIDs for newly saved user profile IDs.
- Fresh TDD red evidence: the focused source guard failed before implementation at
  `DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile` because
  named saves still overwrote `Custom` and schedule-current-selection still persisted the
  placeholder label.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetStoreTest --tests
  org.adaway.ui.hosts.FilterProfileStateTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.quickPresetPersistsDurableActiveProfile
  --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceManualToggleAndSavedSetReconcileActiveProfile
  --dependency-verification=strict --stacktrace`; XML reported 13 tests, 0 failures, 0 errors.
- Compile verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`; Gradle exited 0.
- Emulator instrumentation passed on `adaway-api34(AVD) - 14`:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetStoreMigrationTest
  --dependency-verification=strict --stacktrace`; XML reported 5 tests, 0 failures, 0 errors.
- `git diff --check` passed for the touched files with only CRLF conversion warnings.
- Remaining full-goal gaps: delete/rename/manage saved-set UI over stable ids, scheduled-set status
  visibility, startup reconciliation when DB apply succeeds but active-profile preference write
  fails, update-manifest allowed-host policy, fork release certificate/store identity alignment,
  TextWatcher or Robolectric dialog coverage, pluralized strings, screenshot/accessibility
  coverage, 5M performance proof, release/live-signing proof, and MIT relicensing clearance.

## Plan - 2026-06-14 Goal Continuation 69
- [x] Reuse existing security, QA, and release-review context for update-manifest URL trust
  boundaries without closing the still-running agent pool.
- [x] Add red manifest parser tests for arbitrary HTTPS hosts and lookalike/subdomain hosts.
- [x] Enforce exact runtime APK URL host policy for signed update manifests.
- [x] Tighten GitHub release URLs to this fork's release-asset APK path and default HTTPS port.
- [x] Mirror the runtime URL policy in `scripts/generate-update-manifest.sh`.
- [x] Update release docs and security guard tests for fork release URL and attestation provenance.
- [x] Run focused unit, generator, XML-result, and diff-hygiene verification.

## Review - 2026-06-14 Goal Continuation 69
- The signed update manifest had a residual trust-boundary gap: a validly signed payload could
  point `apkUrl` at any HTTPS host. That made the manifest signer too powerful and weakened the
  release provenance model.
- Fresh TDD red evidence: the first focused run of
  `:app:testDebugUnitTest --tests org.adaway.model.update.ManifestTest
  --dependency-verification=strict --stacktrace` failed as expected because arbitrary HTTPS and
  lookalike host URLs were still accepted.
- `Manifest` now accepts only exact `app.adaway.org` and `github.com` hosts, rejects subdomains and
  lookalikes, rejects non-default HTTPS ports, disallows user info/fragments, and requires GitHub
  URLs to be under `/stevesolun/AdAway/releases/download/` with an `.apk` asset path.
- `ManifestTest` now covers accepted fork GitHub release APK URLs, case-insensitive
  `app.adaway.org`, missing `apkUrl` fallback, arbitrary host rejection, lookalike host rejection,
  `app.adaway.org` subdomain rejection, other GitHub repo rejection, non-release GitHub path
  rejection, non-APK release asset rejection, and non-default port rejection.
- `scripts/generate-update-manifest.sh` now validates the same URL host/path/port/user-info/fragment
  policy before signing the manifest, so release tooling cannot emit a payload the app rejects.
- `RELEASING.md` now uses the fork GitHub APK release path in the local manifest example, documents
  the signed-manifest URL allowlist, and verifies release attestations against `stevesolun/AdAway`
  rather than upstream.
- `SecurityHardeningTest` now asserts the generator allowlist, fork GitHub release URL generation,
  documented fork attestation verification commands, and absence of upstream attestation
  verification commands for fork release assets.
- Focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.update.ManifestTest --tests
  org.adaway.security.SecurityHardeningTest.atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom
  --tests org.adaway.security.SecurityHardeningTest.atk34_releaseCleanupAndDocsPreserveSourceProvenance
  --dependency-verification=strict --stacktrace`; XML reported 24 `ManifestTest` tests and 2
  selected `SecurityHardeningTest` tests, all with 0 failures and 0 errors.
- Generator verification passed through Git Bash: bad arbitrary host rejected, bad GitHub non-release
  path rejected, and
  `https://github.com/stevesolun/AdAway/releases/download/v1.0/AdAway_1.0.apk` accepted.
- `git diff --check` passed for the touched release/update-manifest files with only CRLF conversion
  warnings.
- Remaining full-goal gaps: fork release certificate/store identity alignment, release/live-signing
  proof, saved-set manage UI, startup profile reconciliation after DB-apply success but preference
  write failure, pluralized strings, screenshot/accessibility coverage, 5M performance proof, and
  MIT relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 70
- [x] Trace update-manifest distribution metadata from app request to release generator output.
- [x] Add red tests proving a signed manifest with the wrong channel or store is rejected.
- [x] Enforce requested channel/store metadata when `UpdateModel` parses a downloaded manifest.
- [x] Align release generator, GitHub workflow, and release docs on the runtime `adaway` store name.
- [x] Add security regression guards for workflow/docs/generator store drift.
- [x] Run focused unit, generator payload, XML-result, and diff-hygiene verification.

## Review - 2026-06-14 Goal Continuation 70
- The updater requested manifests with `store=adaway`, but the release generator and workflow emitted
  `store=github`, and runtime `Manifest` parsing carried `channel`/`store` as metadata without
  validating them. That made server or release-tooling distribution drift invisible.
- Fresh TDD red evidence: the focused run of
  `:app:testDebugUnitTest --tests org.adaway.model.update.ManifestTest
  --dependency-verification=strict --stacktrace` failed to compile because the
  distribution-aware `Manifest` constructor did not exist.
- `Manifest` now has a distribution-aware constructor that validates normalized `channel` and
  `store` against the caller's expected values when provided. Missing or mismatched expected
  distribution metadata now throws `JSONException`.
- `UpdateModel.downloadManifest()` now captures the requested channel and detected store once, sends
  those query parameters, and passes the same expected values into `Manifest` parsing.
- `scripts/generate-update-manifest.sh`, `.github/workflows/fork-release-apk.yml`, and
  `RELEASING.md` now use `store=adaway` for direct APK self-update manifests. GitHub remains only
  the release host, not the runtime store identity.
- `ManifestTest` now covers accepted `stable`/`adaway` distribution, unexpected channel rejection,
  unexpected store rejection, and missing expected distribution rejection.
- `SecurityHardeningTest` now guards the generator default store, workflow `--store adaway`, release
  docs `--store adaway`, absence of `--store github`, and `UpdateModel` distribution validation.
- Focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.update.ManifestTest --tests
  org.adaway.security.SecurityHardeningTest.atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom
  --tests org.adaway.security.SecurityHardeningTest.atk34_releaseCleanupAndDocsPreserveSourceProvenance
  --tests org.adaway.security.SecurityHardeningTest.atk34_apkSelfUpdateRequiresInstallPermissionAndAdAwayStoreBoundary
  --dependency-verification=strict --stacktrace`; XML reported 28 `ManifestTest` tests and 3
  selected `SecurityHardeningTest` tests, all with 0 failures and 0 errors.
- Generator payload verification passed through Git Bash: a generated default manifest payload
  contained `store=adaway` and `channel=stable`.
- `git diff --check` passed for the touched update/release files with only CRLF conversion warnings.
- Remaining full-goal gaps: release/live-signing proof with real repository secrets, saved-set manage
  UI, startup profile reconciliation after DB-apply success but preference write failure, pluralized
  strings, screenshot/accessibility coverage, 5M performance proof, and MIT relicensing clearance
  remain open.

## Plan - 2026-06-14 Goal Continuation 71
- [x] Trace saved-profile apply and status rendering from DB source selections to active-profile
  preferences.
- [x] Add red tests for startup/status reconciliation after a lost active-profile preference write.
- [x] Add exact-match-only reconciliation in `FilterSetStore`.
- [x] Use reconciliation before Discover renders active profile status.
- [x] Verify real Android `SharedPreferences` behavior with focused instrumentation.

## Review - 2026-06-14 Goal Continuation 71
- The saved-set apply path updated DB source selections and then wrote active-profile preferences
  with asynchronous `SharedPreferences.apply()`. If the DB write succeeded but the profile
  preference write was lost before restart, Discover could render a stale or custom profile even
  though current enabled sources exactly matched a saved set.
- Fresh TDD red evidence: the focused unit guard
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.quickPresetPersistsDurableActiveProfile
  --dependency-verification=strict --stacktrace` failed before implementation because Discover did
  not call `FilterSetStore.reconcileActiveProfile(...)`.
- `FilterSetStore.reconcileActiveProfile(context, enabledUrls)` now keeps the current active profile
  if it already exactly matches enabled source URLs, otherwise finds a deterministic exact saved-set
  match and records it as active. It leaves non-exact/extended/partial profiles unchanged, so real
  user customization remains visible.
- Exact-match resolution sorts saved profiles by canonical name with `custom` last, making repair
  deterministic when multiple saved sets contain the same URL set.
- `DiscoverFragment.updateProfileStatus()` now reads enabled source URLs first, reconciles exact
  saved-profile matches, then computes `FilterProfileState` for display.
- `FilterSetStoreMigrationTest` now covers exact saved-profile repair after a simulated lost
  preference write and preservation of extended preset customization.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.quickPresetPersistsDurableActiveProfile
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`; XML
  reported 1 selected `DiscoverPresetSubscriptionTest` test, 0 failures, 0 errors.
- Focused emulator instrumentation passed on `adaway-api34(AVD) - 14`:
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetStoreMigrationTest"
  --dependency-verification=strict --stacktrace`; XML reported 7 tests, 0 failures, 0 errors.
- `git diff --check` passed for the touched profile reconciliation files with only CRLF conversion
  warnings.
- Remaining full-goal gaps: release/live-signing proof with real repository secrets, saved-set manage
  UI, pluralized strings outside the already-fixed preset messages, screenshot/accessibility
  coverage, 5M performance proof, and MIT relicensing clearance remain open.

## Plan - 2026-06-14 Goal Continuation 72
- [x] Audit schedule/profile UI copy for hard-coded English strings and non-pluralized counts.
- [x] Add red source-level guard for localized schedule snackbars, weekday labels, and count plurals.
- [x] Move schedule confirmation copy and scheduled-count summaries to resources.
- [x] Replace hard-coded weekday labels with locale-aware `DayOfWeek` display names.
- [x] Run focused unit, compile, grep, and diff-hygiene verification.

## Review - 2026-06-14 Goal Continuation 72
- The filter-set schedule flow still hard-coded `Scheduled: ...` snackbars and weekday names, and
  the schedules screen concatenated `Scheduled sets:` / `Scheduled sources:` counts. That weakens
  localization and creates avoidable copy drift.
- Fresh TDD red evidence: the focused run of
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.scheduleUiUsesLocalizedStringsAndPlurals
  --dependency-verification=strict --stacktrace` failed before implementation because the schedule
  UI did not use resource-backed schedule copy.
- Added `filter_set_schedule_applied`, `schedule_filter_sets_count`, and `schedule_sources_count`
  resources in `strings.xml`.
- `HostsSourcesFragment` now formats schedule confirmation snackbars through
  `R.string.filter_set_schedule_applied` and builds weekday picker labels from
  `DayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())`.
- `SchedulesActivity` now uses plural resources for scheduled set/source summaries and shares the
  same locale-aware weekday label approach for picker labels and weekly schedule summaries.
- Focused verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.scheduleUiUsesLocalizedStringsAndPlurals
  :app:compileDebugJavaWithJavac --dependency-verification=strict --stacktrace`; XML reported 1
  selected test, 0 failures, 0 errors.
- Grep verification found no remaining targeted hard-coded `Scheduled:`, weekday literal, or
  scheduled-count concatenation strings in the touched schedule files.
- `git diff --check` passed for the touched schedule/localization files with only CRLF conversion
  warnings.
- Remaining full-goal gaps: release/live-signing proof with real repository secrets, saved-set manage
  UI, broader screenshot/accessibility coverage, 5M performance proof, and MIT relicensing clearance
  remain open.

## Plan - 2026-06-14 Goal Continuation 73
- [x] Audit saved filter-set UI and stable-id store APIs for rename/delete coverage.
- [x] Add red guards for a saved-set manager menu, rename/delete dialog flow, and store APIs.
- [x] Add stable-id `renameSet` and `deleteSet` operations that reject reserved presets.
- [x] Add a scoped saved-set management dialog from the Sources menu.
- [x] Verify persistence semantics with real Android `SharedPreferences` instrumentation.

## Review - 2026-06-14 Goal Continuation 73
- Users could save, apply, and schedule filter sets, but could not directly rename or delete saved
  user profiles. That left stale profiles and schedules with no ergonomic cleanup path.
- Fresh TDD red evidence: the focused run of
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceSavedSetManagerSupportsRenameAndDelete
  --dependency-verification=strict --stacktrace` failed before implementation because there was no
  manager menu/dialog/store API.
- Added `FilterSetStore.renameSet(...)` and `FilterSetStore.deleteSet(...)` for stable-id profiles.
  Rename preserves URLs, schedule, last-run metadata, stable id, and active identity. Delete removes
  stable-id metadata, schedule fields, last-run metadata, URL set, canonical mapping, and falls back
  to `custom` if the deleted profile was active.
- Reserved preset identities (`safe`, `balanced`, `aggressive`, `custom`) are rejected by the store
  APIs and filtered out of the management dialog.
- Added `Manage saved filter sets` to the Sources menu. It opens a saved-set list, then a
  rename/delete action dialog. Rename reuses inline validation for empty/reserved/duplicate names;
  delete requires explicit confirmation and removes associated schedules.
- Added resource-backed strings for the management menu, rename/delete dialogs, and success
  snackbars.
- Focused JVM verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.sourceSavedSetManagerSupportsRenameAndDelete
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`; XML
  reported 1 selected `DiscoverPresetSubscriptionTest` test, 0 failures, 0 errors.
- Focused emulator instrumentation passed on `adaway-api34(AVD) - 14`:
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetStoreMigrationTest"
  --dependency-verification=strict --stacktrace`; XML reported 9 tests, 0 failures, 0 errors.
- `git diff --check` passed for the touched saved-set manager files with only CRLF conversion
  warnings.
- Remaining full-goal gaps: release/live-signing proof with real repository secrets, broader
  screenshot/accessibility coverage, 5M performance proof, and MIT relicensing clearance remain
  open.

## Plan - 2026-06-15 Goal Continuation 74
- [x] Apply Ponytail as a development constraint without adding it as an app dependency.
- [x] Re-run the fresh 1M allow-heavy runtime/root benchmark.
- [x] Add red guards for optimized root-export redirect precedence and staged export completeness.
- [x] Patch only the narrow DAO SQL needed for semantic parity.
- [x] Re-run focused DAO, source-guard, diff, and patched 1M benchmark verification.

## Review - 2026-06-15 Goal Continuation 74
- Ponytail was used from `DietrichGebert/ponytail` as a development rule set, not a runtime
  dependency. The useful constraint for this slice was: do not add a new abstraction when existing
  SQLite ordering, indexes, and focused tests cover the problem.
- Fresh pre-patch 1M benchmark passed on `adaway-api34(AVD) - 14`:
  `blockedRows=1000000`, `exactAllowRules=10000`, `suffixAllowRules=5000`,
  `stageRows=1000000`, `runtimeRows=0`, `rootRows=980000`,
  `materializedRuntimeCache=false`, `syncMs=36998`, `rootCursorMs=11748`.
- A performance/devil-advocate review found two correctness risks in the optimized root-export
  path: redirect tie-breaking used `MIN(redirection)` instead of canonical source precedence, and
  staged export completeness only compared total rows rather than each enabled source's expected
  row count.
- Fresh TDD red evidence: the focused run of
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testLargeRuntimeSkipRedirectsUseSourcePriorityBeforeRedirectionValue,org.adaway.db.SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage,org.adaway.db.SourceDbTest#testCompleteRootExportStageRequiresPerSourceCounts"
  --dependency-verification=strict --stacktrace` failed 3/3 before the DAO patch.
- `HostEntryDao` now inserts optimized direct/staged redirect rows ordered by host, user priority,
  source id, and redirection value, then lets the existing root-export dedupe keep the first
  canonical row. This removes the incorrect `GROUP BY host` / `MIN(redirection)` shortcut.
- `HostEntryDao` now treats root-export stage data as complete only when the total staged count and
  each enabled source's staged count match `blocked_count + redirected_count`. If the distribution
  is wrong, the large-runtime path falls back to active `hosts_lists` truth.
- Added `SourceDbTest` coverage for direct optimized redirect precedence, staged optimized redirect
  precedence, and total-matching-but-per-source-wrong stage fallback.
- Focused emulator regression verification passed after the patch:
  the same 3 selected `SourceDbTest` methods, 0 failures, 0 errors.
- Broader emulator DAO verification passed:
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest"
  --dependency-verification=strict --stacktrace`; Gradle reported 31 tests, 0 failures.
- Source guard verification passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  --dependency-verification=strict --stacktrace`; XML reported 25 tests, 0 failures, 0 errors.
- Patched 1M benchmark passed on `adaway-api34(AVD) - 14`:
  `blockedRows=1000000`, `exactAllowRules=10000`, `suffixAllowRules=5000`,
  `stageRows=1000000`, `runtimeRows=0`, `rootRows=980000`,
  `materializedRuntimeCache=false`, `root-export-stage totalMs=8501`, `syncMs=10022`,
  `rootCursorMs=6551`.
- `git diff --check -- app/src/main/java/org/adaway/db/dao/HostEntryDao.java
  app/src/androidTest/java/org/adaway/db/SourceDbTest.java` passed with only CRLF conversion
  warnings.
- Remaining full-goal gaps: 5M performance proof, broader screenshot/accessibility coverage,
  release/live-signing proof with real repository secrets, and MIT relicensing clearance remain
  open.

## Plan - 2026-06-15 Goal Continuation 75
- [x] Attempt the 5M staged root-export benchmark on the existing `adaway-api34` AVD.
- [x] Diagnose the failed 5M attempt with concrete storage evidence.
- [x] Create a larger non-destructive test AVD for 5M proof.
- [x] Re-run the 5M staged root-export benchmark on the larger AVD.
- [x] Record the final 5M performance proof and remaining gaps.

## Review - 2026-06-15 Goal Continuation 75
- The first 5M run on the existing `adaway-api34` AVD failed due environment capacity, not app
  logic. The AVD had `disk.dataPartition.size=6G` and failed after seeding all 5,075,000 source
  rows with `SQLiteFullException: database or disk is full` while committing source stats before
  root-stage seeding. The test cleanup restored `/data` free space afterward.
- Created a separate `adaway-api34-16g` AVD using the installed Android 34 Google APIs x86_64
  system image and set `disk.dataPartition.size=16G`. The existing AVD config was left intact.
- The 16G AVD booted with about 15G free on `/data`, allowing the full 5M staged benchmark to run.
- 5M benchmark command:
  `:app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark"
  "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildBlockedRows=5000000"
  "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildExactRules=50000"
  "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSuffixRules=25000"
  "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSeedRootStage=true"
  "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSyncBudgetMs=300000"
  "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildRootCursorBudgetMs=300000"
  --dependency-verification=strict --stacktrace`.
- Successful 5M evidence on `adaway-api34-16g(AVD) - 14`: source seed phases were
  `exact-blocked rows=2500000 ms=558293`, `suffix-blocked rows=2500000 ms=329785`,
  `exact-allow rows=50000 ms=12636`, `suffix-allow rows=25000 ms=4479`, source total
  `rows=5075000 ms=1088438`, root-stage total `rows=5000000 ms=179645`, and seed benchmark
  `stageRows=5000000 seedMs=1281839 checkpointMs=1`.
- Final 5M rebuild evidence: `HostEntryDao.root-export-stage totalMs=133964`,
  skipped materialized runtime cache with `activeRuleRows=5075000`, and
  `HostEntryAllowHeavyRebuildBenchmark runtimeRows=0 rootRows=4900000
  materializedRuntimeCache=false seedRootStage=true stageRows=5000000 syncMs=176753
  rootCursorMs=197926`.
- The 5M run passed under the explicit 300,000 ms sync and root-cursor budgets:
  Gradle reported `BUILD SUCCESSFUL in 28m 45s`, 1 selected instrumentation test, 0 failures.
- Remaining full-goal gaps: broader screenshot/accessibility coverage, release/live-signing proof
  with real repository secrets, and MIT relicensing clearance remain open.

## Plan - 2026-06-15 Goal Continuation 76
- [x] Re-run the screenshot/accessibility UX matrix after the large correctness/performance work.
- [x] Fix any low-risk UI issue found by screenshot inspection.
- [x] Integrate the release/licensing reviewer findings once the subagent finished.
- [x] Verify the UX and release hardening changes with focused local checks.
- [x] Record the current standing and remaining gaps.

## Review - 2026-06-15 Goal Continuation 76
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir app\build\reports\ux-matrix-continuation76-fab`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed on `adaway-api34(AVD) - 14`: baseline, font scale 1.3, and
  font scale 1.3 plus RTL pseudo-locale each ran
  `org.adaway.ui.UxDeviceMatrixTest` with `OK (1 test)`.
- The UX matrix pulled 18 screenshots:
  `baseline`, `font-1.3`, and `font-1.3-rtl`, each with `home`, `discover`, `more`,
  `onboarding`, `sources`, and `update` screenshots at 1080x1920.
- Visual inspection found the old bottom-center pause/resume shield FAB overlapping Home content,
  including leak-resistance copy. `fragment_home.xml` now keeps the same pause/resume behavior but
  moves the FAB to the bottom end, above the bottom navigation, and uses a standard 56dp FAB size.
- The updated Home screenshot no longer has the shield overlaying leak-resistance copy. The remaining
  shield in the captured Home state is the intended progress marker inside the update progress bar.
- A release/licensing reviewer finished and found three narrow release risks: beta tags were still
  published as stable, signed tags were documented but not enforced, and GitHub mark blocking only
  matched `ic_github_24dp`.
- `fork-release-apk.yml` now classifies tags ending in `b` as `beta` channel and GitHub prereleases,
  while other tags remain `stable`. The signed update manifest and GitHub Release prerelease flag both
  consume this single classification.
- `fork-release-apk.yml` now fetches tag objects and fails closed with `git verify-tag` unless
  `RELEASE_TAG_PUBLIC_KEY_BASE64` is configured. `RELEASING.md` documents the new secret and the
  beta/stable tag behavior.
- `check-license-boundary.ps1` now blocks GitHub mark resource variants with
  `org\.adaway:drawable/ic_github(?:_|$)`, and `SecurityHardeningTest` includes a fake-`aapt`
  resource fixture proving `org.adaway:drawable/ic_github_octocat` fails the guard.
- Release/license verification passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive`.
- Focused security verification passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --dependency-verification=strict --stacktrace`; XML reported 42 tests, 0 failures, 0 errors.
- Diff hygiene passed for touched files:
  `git diff --check -- .github/workflows/fork-release-apk.yml RELEASING.md
  scripts/check-license-boundary.ps1
  app/src/test/java/org/adaway/security/SecurityHardeningTest.java
  app/src/main/res/layout/fragment_home.xml`, with only CRLF conversion warnings.
- Product simplification standing: Home is improved but still too dense. The next highest-ROI UX
  simplification is to make Discover impossible to mistake for an empty/broken screen by adding a
  durable loading/offline/empty/error state tied to its catalog state model, then re-run the UX matrix.
- Remaining full-goal gaps: live release proof with real GitHub secrets/signing material, real-device
  release APK smoke test, broader screenshot review beyond the key matrix, and MIT relicensing
  clearance remain open.

## Plan - 2026-06-15 Goal Continuation 77
- [x] Re-check the current ledger, worktree, and Ponytail guidance before making more changes.
- [x] Trace why Discover could appear blank during FilterLists directory loading.
- [x] Apply the smallest existing-state-model fix.
- [x] Verify with focused unit tests and the screenshot/accessibility matrix.
- [x] Record evidence and the next remaining gaps.

## Review - 2026-06-15 Goal Continuation 77
- Ponytail application: reused the existing `FilterListsUiState` and Discover state container instead
  of adding a new view model, new component, or loading framework.
- Root cause for the blank-looking Discover screenshot: `FilterListsUiState.resolve(...)` returned
  `EMPTY_HIDDEN` whenever `directoryLoading` was true, so first-run loading with no rows left the
  filter controls above a visually empty body.
- `FilterListsUiState` now returns `LOADING` only when the directory is still loading and there are
  no rows yet. Cached or visible rows remain visible during refresh.
- `DiscoverFilterListsFragment` now maps `LOADING` to inline copy:
  `Loading filter lists` and `Fetching the directory. Existing subscriptions keep working.`
- Focused state verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.FilterListsUiStateTest
  --dependency-verification=strict --stacktrace`.
- Broader Discover unit verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.*
  --dependency-verification=strict --stacktrace`; XML reported
  `DiscoverPresetSubscriptionTest` 20 tests, `FilterListsSubscriptionStateTest` 5 tests, and
  `FilterListsUiStateTest` 6 tests, all with 0 failures and 0 errors.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation77-discover-state`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed on `adaway-api34(AVD) - 14`: baseline, font scale 1.3, and
  font scale 1.3 plus RTL pseudo-locale each ran `org.adaway.ui.UxDeviceMatrixTest`
  with `OK (1 test)`.
- The matrix pulled 18 screenshots at 1080x1920. The baseline Discover screenshot now shows
  the loading title/message in the previous blank body area.
- Diff hygiene passed for the touched Discover files and ledger with only CRLF conversion warnings.
- Remaining full-goal gaps: live release proof with real GitHub secrets/signing material, real-device
  release APK smoke test, broader screenshot review beyond the key matrix, MIT relicensing clearance,
  and deeper Home simplification remain open.

## Plan - 2026-06-15 Goal Continuation 80
- [x] Inspect Discover loading/action state and current tests.
- [x] Add failing regression coverage for persistent Discover actions during loading.
- [x] Apply the smallest Discover layout/action-order fix.
- [x] Verify focused Discover tests and the screenshot/accessibility matrix.
- [x] Record evidence and remaining gaps.

## Review - 2026-06-15 Goal Continuation 80
- Ponytail application: no new Discover state, component, or action model. The existing quick-start
  strip and AI chip were reordered so useful actions stay in the first loading viewport.
- TDD red checks:
  `DiscoverPresetSubscriptionTest.discoverQuickActionsStayBetweenProfileAndBrowser` first failed
  when quick actions were ordered before the visible profile/header contract, then failed again when
  the AI chip still sat behind the preset chips.
- The green implementation places the quick-start strip before the browser/loading body and orders
  `Ask AI`, `Safe Mode`, `Balanced Mode`, then `Aggressive Mode`, preserving existing click handlers.
- Focused Discover verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.discoverQuickActionsStayBetweenProfileAndBrowser
  --dependency-verification=strict --stacktrace`.
- Broader Discover verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.*
  --dependency-verification=strict --stacktrace`.
- Diff hygiene passed for touched Discover files and the ledger:
  `git diff --check -- app/src/main/res/layout/fragment_discover.xml
  app/src/test/java/org/adaway/ui/discover/DiscoverPresetSubscriptionTest.java tasks/todo.md`,
  with only the known CRLF conversion warning for `fragment_discover.xml`.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation80-discover-ai-first`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed for baseline, font scale 1.3, and font scale 1.3 plus RTL pseudo-locale; each
  variant ran `org.adaway.ui.UxDeviceMatrixTest` with `OK (1 test)` and produced the expected six
  screenshots, 18 total.
- Visual spot-check: baseline Discover now shows `Ask AI`, `Safe Mode`, and `Balanced Mode` while
  the directory is loading. Font scale 1.3 plus RTL keeps `Ask AI` and `Safe Mode` visible without
  clipping.
- Remaining full-goal gaps: Home update progress is still visually heavy, full performance
  benchmarks remain to be re-run, live release proof with real secrets/signing material and
  real-device release APK smoke are still open, MIT relicensing still needs clearance, and broader
  screenshot review beyond the key matrix remains open.

## Plan - 2026-06-15 Goal Continuation 79
- [x] Inspect Home AI and existing Discover AI entry points.
- [x] Hide the duplicate Home AI prompt using the existing Discover AI path.
- [x] Add the smallest static regression check for the simplified Home contract.
- [x] Run focused Home verification and the screenshot/accessibility matrix.
- [x] Record evidence and remaining simplification gaps.

## Review - 2026-06-15 Goal Continuation 79
- Ponytail application: reused the existing Discover AI chip and bottom sheet instead of moving AI
  logic or adding another entry point. Home now keeps the AI prompt out of the primary status path by
  hiding `aiBoxCard`.
- Regression guard added in `HomeNavigationSourcesContractTest`: Home must keep `aiBoxCard` hidden,
  and Discover must retain the `chipDiscoverAskAi` / `AiSuggestBottomSheet` path.
- Focused Home verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.home.HomeNavigationSourcesContractTest
  --dependency-verification=strict --stacktrace`.
- Diff hygiene passed for touched Home files and the ledger:
  `git diff --check -- app/src/main/res/layout/home_content.xml
  app/src/test/java/org/adaway/ui/home/HomeNavigationSourcesContractTest.java tasks/todo.md`,
  with only the known CRLF conversion warning for `home_content.xml`.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation79-home-ai-hidden`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- The matrix produced the expected 18 screenshots: `baseline`, `font-1.3`, and `font-1.3-rtl`,
  each with `home`, `discover`, `more`, `onboarding`, `sources`, and `update`.
- Visual spot-check: baseline Home no longer shows the AI prompt; Home stays focused on status,
  update progress, sources, and leak resistance. Font scale 1.3 plus RTL still fits without clipping.
- Remaining simplification gaps: Home is still visually heavy during update progress, Discover should
  keep useful actions visible during loading/offline states, and the broader full-goal gaps remain:
  live release proof with real GitHub secrets/signing material, real-device release APK smoke test,
  broader screenshot review, MIT relicensing clearance, and remaining release/performance benchmarks.

## Plan - 2026-06-15 Goal Continuation 78
- [x] Inspect the remaining Discover loading screenshot clutter.
- [x] Apply the smallest existing-layout simplification.
- [x] Verify Discover unit coverage.
- [x] Re-run the screenshot/accessibility matrix.
- [x] Record evidence and remaining gaps.

## Review - 2026-06-15 Goal Continuation 78
- Ponytail application: no new component or state model. The existing bulk action row now has an id,
  and existing `refreshBulkActionsState()` hides that row when there are no visible rows and no bulk
  job is running.
- This removes disabled `Add visible lists` / `Remove visible` buttons from the first-load Discover
  empty body while preserving the cancel affordance during active bulk work.
- Regression guard added to `DiscoverPresetSubscriptionTest` so bulk actions remain explicit command
  buttons and hide when there are no visible actionable rows.
- Discover unit verification passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.*
  --dependency-verification=strict --stacktrace`; XML reported
  `DiscoverPresetSubscriptionTest` 20 tests, `FilterListsSubscriptionStateTest` 5 tests, and
  `FilterListsUiStateTest` 6 tests, all with 0 failures and 0 errors.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation78-discover-bulk-row`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed on `adaway-api34(AVD) - 14`: baseline, font scale 1.3, and
  font scale 1.3 plus RTL pseudo-locale each ran `org.adaway.ui.UxDeviceMatrixTest`
  with `OK (1 test)`.
- The matrix pulled 18 screenshots. Visual spot-check of the baseline Discover screenshot confirms
  the loading title/message remains visible and the disabled bulk buttons are gone.
- Diff hygiene passed for touched Discover files and ledger with only CRLF conversion warnings.
- Remaining full-goal gaps: live release proof with real GitHub secrets/signing material, real-device
  release APK smoke test, broader screenshot review beyond the key matrix, MIT relicensing clearance,
  and deeper Home simplification remain open.

## Plan - 2026-06-15 Goal Continuation 81
- [x] Inspect Home progress layout/state and existing tests.
- [x] Add failing regression tests for summary-first progress and bird branding.
- [x] Apply minimal Home progress simplification and restore bird logo assets.
- [x] Verify focused Home tests and screenshot/accessibility matrix.
- [x] Record evidence and remaining gaps.

## Review - 2026-06-15 Goal Continuation 81
- Ponytail application: no new progress component. Existing Home observer now hides noisy
  Download/Parse phase micro-rows in the primary Home path while preserving the overall progress
  summary and pause/stop controls. Restored the existing bird vectors instead of inventing new
  branding.
- Red checks: Home contract failed before implementation while Download/Parse phase rows were still
  visible, and the branding guard failed against the shield/block replacement assets.
- `HomeFragment` now hides `downloadPhaseLabel`, `downloadProgressBar`, `downloadPhasePercent`,
  `parsePhaseLabel`, `parseProgressBar`, and `parsePhasePercent` during active update progress,
  beside the existing hidden check row.
- `icon_foreground_red.xml`, `icon_foreground_white.xml`, `icon_monochrome.xml`, and `logo.xml`
  are restored to AdAway bird vectors.
- Branding guard added in `HomeNavigationSourcesContractTest`: logo assets must contain a bird path
  and must not contain the shield/block path.
- Focused Home contract passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest --dependency-verification=strict
  --stacktrace`.
- Broader Home package passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests org.adaway.ui.home.*
  --dependency-verification=strict --stacktrace`.
- Diff hygiene passed for touched Home progress, branding, test, and ledger files with only known
  CRLF conversion warnings.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation81-home-bird-progress-summary`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed baseline, font scale 1.3, and font scale 1.3 plus RTL pseudo-locale, producing
  18 screenshots under
  `app\build\reports\ux-matrix-continuation81-home-bird-progress-summary`.
- Visual spot-check: baseline Home shows the red bird in the header and progress marker. The update
  progress card is summary-first: overall percent and accepted-rule summary remain while Download
  and Parse rows are gone.
- Remaining UI cleanup target: font scale 1.3 plus RTL still shows the bird, but the blue floating
  action button can overlap the leak-resistance card. That is the next Home visual cleanup item.

## Plan - 2026-06-15 Goal Continuation 82
- [x] Let the existing expert agent finish and inspect its findings against current files.
- [x] Verify staged/direct redirect-priority and per-source stage completeness coverage in current
  `HostEntryDao` / `SourceDbTest`.
- [x] Add a red regression for a nearby uncovered root-export bug: non-materialized cursor duplicate
  rows when exact and suffix rules share a host.
- [x] Fix active suffix export queries without adding a large Java-side resolved-host set.
- [x] Run focused database instrumentation and static generation guard tests.
- [x] Record evidence, lessons, and remaining gaps.

## Review - 2026-06-15 Goal Continuation 82
- Existing agent result: Arendt finished and flagged redirect precedence/staged completeness risks.
  Current inspection showed the exact redirect-priority cases and per-source stage-count fallback are
  already covered by `SourceDbTest` and `getCompleteRootExportStageRows()`.
- New root-cause found while checking that area: `ActiveRootHostsCursor` intentionally does not
  track source exact rows in memory, but the active suffix SQL still returned suffix rows for hosts
  already owned by active exact blocked/redirect rules. Root export could emit duplicates in the
  non-materialized cursor path.
- Red check:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testRootHostsExportDoesNotDuplicateSuffixWhenExactExists'
  --dependency-verification=strict --stacktrace` failed with `expected:<2> but was:<4>`.
- Fix: added `ACTIVE_EXACT_RULE_NOT_EXISTS` in `HostEntryDao` and applied it to both active suffix
  root-export cursor queries. The user-suffix cursor now receives `activeGeneration`, keeping the
  SQL filter generation-aware.
- Ponytail application: no new cache, no giant Java dedupe set, no new export layer. Push the
  existing canonical precedence into the existing SQL query so million-row root export stays bounded.
- Green check for the new regression passed with the same command above.
- Broader database instrumentation passed:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest'
  --dependency-verification=strict --stacktrace`; result was 32/32 tests, 0 failed on
  `adaway-api34(AVD) - 14`.
- Static generation/DAO guard passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict
  --stacktrace`.
- Lesson recorded in `tasks/lessons.md`: protect identity-critical logo/launcher assets with
  explicit branding decisions and regression guards.
- Remaining gaps: the Home FAB overlap from the Continuation 81 RTL/font-scale screenshot remains
  the next UI cleanup target; full release/signing proof, real-device release smoke, MIT clearance,
  and larger performance benchmarks remain open.

## Plan - 2026-06-15 Goal Continuation 83
- [x] Inspect current Home wrapper/content layout, FAB binding, and prior RTL screenshot evidence.
- [x] Add a red Home contract for the leak-card overlap risk.
- [x] Remove the free-floating Home FAB overlay and dead binding path.
- [x] Run focused Home tests and the screenshot/accessibility matrix.
- [x] Record evidence and remaining gaps.

## Review - 2026-06-15 Goal Continuation 83
- Root cause: `fragment_home.xml` placed the bird FAB as a `bottom|end` CoordinatorLayout overlay.
  At font scale 1.3 plus RTL it sat directly over `leakStatusCardView`.
- Ponytail application: moving the FAB into the crowded header would likely create another
  collision. The simpler fix is to remove the overlay entirely and keep the bird as the header/logo
  identity signal.
- Red check:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest.homeProtectionFabDoesNotFloatOverStatusCards
  --dependency-verification=strict --stacktrace` failed before the layout/code cleanup.
- `fragment_home.xml` no longer contains the Home `FloatingActionButton` block.
- `HomeFragment` no longer calls `bindFab()`, no longer references `binding.fab`, and no longer
  carries the deleted FAB visibility/protection-toggle path.
- Focused Home contract passed with the same command above after the change.
- Broader Home package passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests org.adaway.ui.home.*
  --dependency-verification=strict --stacktrace`.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation83-home-no-fab-overlap`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed baseline, font scale 1.3, and font scale 1.3 plus RTL pseudo-locale, producing
  18 screenshots.
- Visual spot-check: baseline and font scale 1.3 RTL Home screenshots show the leak-resistance card
  without the bird FAB overlay. The AdAway bird remains in the header logo.
- Remaining UI cleanup: the credit line is close to the bottom navigation at large text/RTL and can
  look cramped. Broader open gaps remain release/signing proof, real-device release smoke, MIT
  clearance, and larger performance benchmarks.

## Plan - 2026-06-15 Goal Continuation 84
- [x] Inspect Home footer/bottom-nav crowding and attribution reachability.
- [x] Add a red Home contract for decorative credit line removal.
- [x] Remove Home `creditTextView` while keeping About/More attribution.
- [x] Run focused Home tests and screenshot/accessibility matrix.
- [x] Record evidence and remaining gaps.

## Review - 2026-06-15 Goal Continuation 84
- Root cause: decorative Home attribution was constrained near the bottom of
  `home_content.xml`, close to bottom navigation under large text/RTL. It did not need to compete
  with protection status or controls because the same attribution remains reachable from
  Preferences/About.
- Ponytail application: delete the redundant Home view instead of adding padding, spacers, or
  another layout workaround.
- Red check:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest.homeDoesNotCrowdBottomNavWithDecorativeCreditLine
  --dependency-verification=strict --stacktrace` failed before deletion at
  `HomeNavigationSourcesContractTest.java:179`.
- `home_content.xml` no longer contains `creditTextView` or `@string/credit_line_home`.
- `credit_line_home` remains present in `strings.xml` and remains referenced from
  `preferences_main.xml` under the About preference.
- Focused Home contract passed with the same command above after the deletion.
- Broader Home package passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests org.adaway.ui.home.*
  --dependency-verification=strict --stacktrace`.
- UX matrix command:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation84-home-credit-removed`
  with JDK 21 and `ANDROID_HOME=C:\Users\solun\AppData\Local\Android\Sdk`.
- UX matrix passed baseline, font scale 1.3, and font scale 1.3 plus RTL pseudo-locale, producing
  18 screenshots.
- Visual spot-check: baseline and font scale 1.3 RTL Home screenshots show the AdAway bird in the
  header and no credit line crowding bottom navigation.
- Arendt finished without being closed. New next P1 correctness target: staged/direct large root
  export redirect tie-breaking may diverge from canonical active-rule precedence; add a small
  staged/direct test with two external redirects for the same host where lower `source_id` has a
  lexicographically larger redirection before trusting the next 1M benchmark.
- Remaining full-goal gaps: resolve Arendt's root-export precedence finding, release/signing proof,
  real-device release smoke, MIT relicensing clearance, and larger performance benchmarks remain
  open.

## Plan - 2026-06-15 Goal Continuation 85
- [x] Inspect optimized root-export redirect inserts against canonical active precedence.
- [x] Add red instrumentation coverage for duplicate redirects where the same source has a
  lexicographically smaller canonical redirection inserted before a larger one.
- [x] Fix runtime redirect ordering with the smallest SQL change that keeps work in SQLite.
- [x] Run focused instrumentation and static DAO guards.
- [x] Record evidence and remaining gaps.

## Review - 2026-06-15 Goal Continuation 85
- Arendt's root-export warning was useful, but current code has two relevant guards already:
  direct large root export orders redirected candidates by source priority/source id/redirection,
  and staged root export refuses the staged shortcut when any user block/redirect candidate exists.
- Root cause found in the same precedence surface: normal runtime redirect import used
  `INSERT OR REPLACE` without a redirection tie-breaker. For duplicate redirects from the same
  source, the later row could replace the canonical lower redirection and make runtime/root output
  disagree with the active-rule CTE.
- Red check:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testRuntimeRedirectsUseCanonicalRedirectionWithinSource'
  --dependency-verification=strict --stacktrace` failed with
  `expected:<[1.1.1.1]> but was:<[9.9.9.9]>`.
- Fix: `HostEntryDao.importRedirected*()` now orders redirections descending because
  `INSERT OR REPLACE` keeps the last row, while `getActiveExactRedirectEntry()` orders redirections
  ascending because `LIMIT 1` keeps the first row.
- Ponytail application: no new resolver class, no Java-side duplicate map, no new temp table. One
  ordering clause per affected query.
- Focused regression passed with the same command above after the fix.
- Broader database instrumentation passed:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest'
  --dependency-verification=strict --stacktrace`; result was 33/33 tests, 0 failed on
  `adaway-api34(AVD) - 14`.
- Static generation/DAO guard passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict
  --stacktrace`.
- Remaining full-goal gaps: release/signing proof, real-device release smoke, MIT relicensing
  clearance, and larger performance benchmarks remain open.

## Plan - 2026-06-15 Goal Continuation 86
- [x] Inspect existing performance benchmark fixtures and pick the smallest meaningful large-scale
  post-redirect-fix gate.
- [x] Run the 1M stage-seeded allow-heavy runtime/root-export benchmark with explicit budgets.
- [x] Record benchmark metrics, remaining performance gaps, and environment limits.
- [x] Run hygiene checks and stop any emulator used.

## Review - 2026-06-15 Goal Continuation 86
- Ponytail application: no new benchmark harness. Reused the existing
  `SourceLoaderPerformanceTest#rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark`
  switch that already seeds the staged root-export path and exposes explicit budgets.
- Fresh post-redirect-fix 1M benchmark command:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildBlockedRows=1000000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildExactRules=10000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSuffixRules=10000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSeedRootStage=true'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSyncBudgetMs=120000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildRootCursorBudgetMs=30000'
  --dependency-verification=strict --stacktrace`.
- The benchmark passed on `adaway-api34(AVD) - 14`; Gradle reported 1 test, 0 failed.
- Seed metric: `HostEntryAllowHeavySeedBenchmark blockedRows=1000000 exactAllowRules=10000
  suffixAllowRules=10000 seedRootStage=true stageRows=1000000 seedMs=117403 checkpointMs=1`.
- Rebuild metric: `HostEntryAllowHeavyRebuildBenchmark blockedRows=1000000
  exactBlockedRows=500000 suffixBlockedRows=500000 exactAllowRules=10000 suffixAllowRules=10000
  exactAllowMatches=10000 suffixAllowExactMatches=10000 suffixAllowSuffixMatches=10000
  runtimeRows=0 rootRows=970000 materializedRuntimeCache=false seedRootStage=true
  stageRows=1000000 syncMs=12987 rootCursorMs=6296`.
- DAO phase metric: `HostEntryDao.root-export-stage totalMs=10140`, with redirected phase at
  `0ms` for this zero-redirect allow-heavy fixture.
- Remaining full-goal gaps: rerun or schedule fresh 5M proof after the latest DAO changes,
  release/signing proof with real secrets, real-device release smoke, and MIT relicensing clearance.

## Plan - 2026-06-15 Goal Continuation 87
- [x] Confirm the 16G AVD and prior 5M command are still usable.
- [x] Run the existing 5M stage-seeded allow-heavy runtime/root-export benchmark after the latest
  DAO redirect-ordering changes.
- [x] Record the full 5M metrics or the concrete environment/app failure.
- [x] Run hygiene checks and stop the emulator.

## Review - 2026-06-15 Goal Continuation 87
- Reused the existing 16G AVD and existing stage-seeded allow-heavy benchmark. No new harness or
  benchmark variant was added.
- Environment check: `adaway-api34-16g` booted with about `15G` free on `/data`.
- Fresh post-redirect-fix 5M benchmark command:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildBlockedRows=5000000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildExactRules=50000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSuffixRules=25000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSeedRootStage=true'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSyncBudgetMs=300000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildRootCursorBudgetMs=300000'
  --dependency-verification=strict --stacktrace`.
- The benchmark passed on `adaway-api34-16g(AVD) - 14`; Gradle reported 1 test, 0 failed, and
  `BUILD SUCCESSFUL in 17m 39s`.
- Source seed phases: `exact-blocked rows=2500000 ms=327192`,
  `suffix-blocked rows=2500000 ms=264403`, `exact-allow rows=50000 ms=9455`,
  `suffix-allow rows=25000 ms=2745`, total `rows=5075000 ms=710284`.
- Root-stage seed phases: exact `rows=2500000 ms=27181`, suffix
  `rows=2500000 ms=28291`, total `rows=5000000 ms=89686`.
- Seed metric: `HostEntryAllowHeavySeedBenchmark blockedRows=5000000
  exactAllowRules=50000 suffixAllowRules=25000 seedRootStage=true stageRows=5000000
  seedMs=806571 checkpointMs=1`.
- DAO phase metric: `HostEntryDao.root-export-stage totalMs=81045`, with blocked insert
  `19965ms`, index create `24084ms`, allow pruning `2796ms`, redirected phase `0ms`, and dedupe
  `34178ms`.
- Final rebuild metric: `HostEntryAllowHeavyRebuildBenchmark blockedRows=5000000
  exactBlockedRows=2500000 suffixBlockedRows=2500000 exactAllowRules=50000 suffixAllowRules=25000
  exactAllowMatches=50000 suffixAllowExactMatches=25000 suffixAllowSuffixMatches=25000
  runtimeRows=0 rootRows=4900000 materializedRuntimeCache=false seedRootStage=true
  stageRows=5000000 syncMs=102599 rootCursorMs=121921`.
- The fresh 5M run passed under the explicit `300000ms` sync and root-cursor budgets after the
  latest DAO redirect-ordering changes.
- Remaining full-goal gaps: release/signing proof with real secrets, real-device release smoke,
  broader screenshot/accessibility coverage, and MIT relicensing clearance.

## Plan - 2026-06-15 Goal Continuation 88
- [x] Inspect existing release signing and MIT/license-boundary guards.
- [x] Verify release builds fail closed without local signing/update trust material.
- [x] Verify the license-boundary script blocks premature MIT/artifact drift.
- [x] Restore and guard the AdAway bird launcher branding after the correction.
- [x] Record proof and remaining external release/MIT gaps.

## Review - 2026-06-15 Goal Continuation 88
- Current app status: the P0 runtime-generation, partial-update, duplicate redirect, and staged
  root-export performance work remains in place; fresh 1M and 5M stage-seeded benchmark evidence
  from continuations 86 and 87 is still the current performance proof.
- Simplicity/product stance: Home is the single protection/status surface, Discover is for finding
  lists, Sources/My Lists own subscriptions and schedules, and destructive bulk actions now need
  explicit confirmation/progress/final summaries instead of switch-style surprise state changes.
- Logo correction: restored the deleted bird launcher fallback assets from `HEAD`, kept the app
  logo vectors on the AdAway bird path, and added `android:roundIcon="@mipmap/icon_round"` so
  round launchers use the bird too.
- Added `HomeNavigationSourcesContractTest#launcherBrandingKeepsBirdIconFallbacks` so the manifest,
  adaptive launcher XML, and density fallback PNGs must remain wired and non-empty.
- Logo/resource verification passed:
  `.\gradlew.bat :app:processDebugResources --dependency-verification=strict`.
- Logo regression passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest --dependency-verification=strict`.
- Release fail-closed verification passed by failing intentionally without local trust material:
  `.\gradlew.bat :app:assembleRelease --dependency-verification=strict --stacktrace` and
  `.\gradlew.bat :app:generateSbom --dependency-verification=strict --stacktrace` both stop at
  `app/build.gradle:90` with `Release and release-SBOM builds require signingStoreLocation,
  signingStorePassword, signingKeyAlias, and signingKeyPassword.`
- License-boundary source checks passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive` and
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree`.
- Strict artifact guard failed closed as intended without explicit artifact paths:
  `Strict artifact mode requires -ApkPath` and `Strict artifact mode requires -SbomPath`.
- Arendt completed after the logo/release checks. Its redirect tie-breaker warning maps to the same
  precedence surface fixed and verified in continuation 85, then re-benchmarked in continuations
  86 and 87. Its still-valid follow-ups are to prove the measured DB-handle rebuild path is the
  same path used by user-visible huge updates, and to add stronger per-source staged completeness
  checks instead of relying only on total staged row count.
- Remaining full-goal gaps: real release proof still needs actual signing/update trust secrets in
  the release workflow, real-device release smoke, broader screenshot/accessibility coverage, and
  legal clearance/removal/rewrite of GPL-derived material before any MIT edition can ship.

## Plan - 2026-06-15 Goal Continuation 89
- [x] Inspect the current user-visible update/apply path against the 5M benchmarked rebuild path.
- [x] Let the sidecar path reviewer finish and incorporate its finding without closing it.
- [x] Reuse existing staged root-export completeness guards instead of adding duplicate machinery.
- [x] Fix the async runtime-refresh/root-apply race with the smallest cache invalidation boundary.
- [x] Run focused static, instrumentation, and license-boundary verification.

## Review - 2026-06-15 Goal Continuation 89
- Halley confirmed primary large update paths do not call the no-DB `HostEntryDao.sync()` fallback:
  Home, scheduled update, immediate update, and subscribe-all flows reach
  `SourceModel.checkAndRetrieveHostsSources()` and the DB-handle
  `HostEntryDao.rebuildFromActiveGeneration(db)` path. Remaining non-primary `sync()` callers are
  user-rule mutation surfaces such as domain checker/AI actions.
- The real gap was async timing: full update publish flips active generation and schedules runtime
  rebuild, while root apply can immediately read `HostEntryDao.getRootHostsFileCursor()`. If
  `root_export_materialized` was still true from the previous huge dataset, root apply could prefer
  stale `root_host_entries` until the async refresh completed.
- Ponytail application: did not add a new rebuild queue or make every full update synchronous.
  Added `HostEntryDao.invalidateRootExportMaterializedCache()` and call it inside generation/source
  publish transactions before `scheduleRuntimeCacheRefresh()`. That clears only
  `root_host_entries` plus the materialized flag, so immediate root apply falls back to active
  generation truth while the expensive runtime/root rebuild still runs asynchronously.
- Reused existing per-source staged completeness implementation. `getCompleteRootExportStageRows()`
  already checks total staged rows plus per-enabled-source counts, and `SourceDbTest` already covers
  incomplete, per-source mismatch, disabled-source, allow, and redirect precedence cases.
- Red/green guard: first ran
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest.sourceModel_finalizesGenerationAndRuntimeTruthAtomically
  --dependency-verification=strict`; it failed at `Generation304MigrationTest.java:326` before the
  fix, then passed after the cache invalidation change.
- Full static generation contract passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict`.
- Focused instrumentation passed on `adaway-api34(AVD) - 14`: 41 tests, 0 failed for
  `org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.db.SourceDbTest`.
- License-boundary check still passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree`.
- Hygiene: `git diff --check` passed for the touched slice, with only CRLF warnings. The emulator
  used for instrumentation was stopped after the run.
- Remaining full-goal gaps: non-primary `HostEntryDao.sync()` callers should get separate large
  dataset coverage, release proof still needs real signing/update secrets, real-device release
  smoke is still pending, and MIT relicensing remains blocked on legal/code-asset clearance.

## Plan - 2026-06-15 Goal Continuation 90
- [x] Inspect remaining non-primary `HostEntryDao.sync()` callers.
- [x] Add a red static guard proving user-rule mutation surfaces do not bypass `SourceModel`.
- [x] Route domain checker and AI rule mutations through the generation-aware runtime rebuild path.
- [x] Debug and fix the database creation race exposed by instrumentation.
- [x] Run focused unit, instrumentation, license-boundary, and hygiene verification.

## Review - 2026-06-15 Goal Continuation 90
- User-facing simplicity/product status: the current navigation model remains Home for protection
  and status, Discover for finding lists, Sources/My Lists for subscribed filters and schedules, and
  More for diagnostics/settings/about. The bird logo regression is already corrected and guarded by
  continuation 88.
- Runtime truth gap fixed: `DomainCheckerViewModel.syncRuntimeRules()` and
  `AiActionExecutor.applyDomain()` no longer call `HostEntryDao.sync()` directly. They now route
  user-rule mutations through `SourceModel.syncHostEntries()`, matching the active-generation
  rebuild and VPN/root cache invalidation path used by primary updates.
- Red/green guard: first ran
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest.userRuleMutationSurfacesReuseSourceModelRuntimeSync
  --dependency-verification=strict`; it failed at `Generation304MigrationTest.java:468` before the
  fix, then passed after rerouting the callers.
- Instrumentation exposed a production database creation race: `AppDatabase.onCreate()` could queue
  `WaTgSafetyAllowlist.ensureAllowlist(context)` before `AppDatabase.initialize(context, instance)`
  had inserted the user source row, causing a foreign-key failure when the allowlist inserted
  user-source rules.
- Race fix: `AppDatabase.onCreate()` now runs database initialization and
  `WaTgSafetyAllowlist.ensureAllowlistSync(context)` in the same disk-IO task, preserving the
  existing sync helper and deleting the ordering race instead of adding retries or exception
  swallowing.
- Added static guard
  `Generation304MigrationTest#appDatabaseSeedsUserSourceBeforeSafetyAllowlist` so the user source
  seed must remain before the safety allowlist insert.
- Full static generation contract passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict`.
- Focused instrumentation passed on `adaway-api34(AVD) - 14`: exit code `0`, `OK (4 tests)` for
  `org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest` and
  `org.adaway.model.vpn.VpnModelCacheInvalidationTest`.
- Production sync grep now finds only the DAO implementation/log strings, `HomeFragment` calling its
  view-model `sync()` method, and one explanatory comment in `WaTgSafetyAllowlist`; the direct
  `mHostEntryDao.sync()` / `entryDao.sync()` mutation callers are gone.
- License-boundary check still passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree`.
- Hygiene: `git diff --check` passed for the touched slice, with only CRLF warnings. The emulator
  used for instrumentation was stopped after the run.
- Remaining full-goal gaps: real release proof still needs signing/update trust secrets, real-device
  release smoke is still pending, broader screenshot/accessibility coverage remains open, and MIT
  relicensing remains blocked on legal/code-asset clearance.

## Plan - 2026-06-15 Goal Continuation 91
- [x] Re-anchor on current Ponytail guidance and the local task ledger.
- [x] Pick the smallest release-readiness slice that moves an open full-goal gap.
- [x] Add a red guard for executable real-device release smoke coverage.
- [x] Add the smoke script and release-doc hook.
- [x] Verify the release/security guard, script parser, debug-APK refusal, license boundary, and
  hygiene checks.

## Review - 2026-06-15 Goal Continuation 91
- Ponytail application: did not add a new CI service or pretend an emulator proves real-device
  release readiness. Added one local PowerShell gate that can be run against the signed release APK
  on attached physical hardware.
- Added `scripts/run-release-smoke.ps1`. It requires `-ApkPath`, inspects APK badging with `aapt`,
  rejects `application-debuggable`, verifies the package defaults to `org.adaway`, optionally checks
  the signer fingerprint with `-ExpectedCertSha256`, rejects emulator/qemu devices, installs the APK,
  launches it with `monkey`, and fails if `pidof org.adaway` is empty after launch.
- Documented the smoke command in `RELEASING.md` after the local artifact/license checks, including
  `-ExpectedCertSha256 "<release-certificate-sha256>"`.
- Red/green guard: first ran
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest.atk34_releaseSmokeRequiresReleaseApkOnRealDevice
  --dependency-verification=strict`; it failed with `NoSuchFileException` for
  `scripts\run-release-smoke.ps1`, then passed after adding the script and docs hook.
- Focused ATK-34 release/security unit group passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest.atk34* --dependency-verification=strict`.
- Script syntax check passed through PowerShell parser API.
- Debug-APK refusal path was exercised against `app\build\outputs\apk\debug\app-debug.apk`; the
  script failed before device operations with `Refusing to smoke-test a debuggable APK. Use the
  release APK.`
- License-boundary check still passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree`.
- Hygiene: `git diff --check` passed for the touched tracked files with only CRLF warnings, and
  `scripts\run-release-smoke.ps1` was separately checked for trailing whitespace because it is
  currently untracked.
- Remaining full-goal gaps: the actual release smoke still needs a signed release APK, expected
  certificate digest, and attached physical device; live release proof still needs repository
  signing/update secrets; broader screenshot/accessibility review and MIT relicensing clearance
  remain open.

## Plan - 2026-06-15 Goal Continuation 92 Ponytail Review Fixes
- [x] Add a red guard that the third-party/license inventory matches the restored AdAway bird
  branding and does not describe a different geometric shield asset.
- [x] Correct packaged logo/icon provenance text while keeping the bird assets in the app.
- [x] Move `REQUEST_INSTALL_PACKAGES` out of the base manifest and gate it to the direct APK
  update distribution path only.
- [x] Harden `scripts/run-ux-matrix.ps1` so instrumentation timeouts cleanly stop app/test
  processes and cannot wedge future matrix runs.
- [x] Replace the Domain Checker hardcoded `80dp`/focus workaround with the simplest reusable
  platform-inset path and keep the visual/top-scroll guard.
- [x] Remove duplicated UX-matrix idle waits where the Domain Checker scroll assertion runs.
- [x] Run focused unit/static checks, script parser checks, license-boundary checks, and a
  Ponytail re-review of the resulting diff.

## Review - 2026-06-17 Bird Branding License Provenance
- Strengthened the existing bird branding guard so `HomeNavigationSourcesContractTest` now also
  requires the restored density `icon_foreground.png` launcher fallbacks to exist and be non-empty.
- Updated `THIRD_PARTY_LICENSES.md` to explicitly inventory packaged density fallback launcher
  PNGs as AdAway bird assets. The existing guard still rejects stale geometric DNS shield wording.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest --dependency-verification=strict
  --stacktrace`, `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree`, and
  `git diff --check` with only existing CRLF conversion warnings.

## Plan - 2026-06-15 Goal Continuation 93 Devil Advocate Stabilization
- [x] Freeze broad feature/refactor work while crash and build state are proved.
- [x] Capture live crash evidence and classify it as AdAway app crash, native crash, ANR,
  harness failure, or external emulator/system noise.
- [x] Fix the UX matrix runner false-negative so `OK` instrumentation output exits cleanly,
  screenshots are pulled, and timeout cleanup remains bounded.
- [x] Patch concrete P0/P1 crash risks: detached Discover callbacks, SourceModel
  carry-forward concurrency, boot VPN permission launch flags, updater intent handling, and
  update progress cursor handling.
- [x] Disable/gate AI as a user-facing release surface by default, without deleting existing
  security-sensitive internals in the same stabilization slice.
- [x] Prove runtime truth through user-visible paths: update, immediate apply, domain checker
  mutation, user-rule mutation, scheduled update, and mixed 200/304/failure source updates.
- [x] Re-run scale/perf gates only after correctness is green: 100k, 1M, 5M, immediate root
  apply, VPN exact/suffix lookup, and no-ANR checks.
- [x] Apply Ponytail simplification only after behavior coverage: delete dead legacy update
  paths, replace brittle source-text tests, and defer native/Room/release refactors until their
  policies are explicit.
- [x] Split the broad worktree into reviewable slices: crash/stability, DB/runtime truth, UX,
  release security, licensing/MIT, and optional AI cleanup.
- [x] Superseded by the canonical external P0 release board below; do not use this historical
  checklist as an independent open gate.

## Review - 2026-06-15 Goal Continuation 93
- Final stabilization scope: kept broad refactor/release work frozen and focused on crash evidence,
  harness reliability, P0/P1 guards, AI default gating, runtime truth, and scale proof.
- Expert-agent work incorporated:
  - CTO/devil advocate kept the work anchored on crash evidence, dirty-worktree risk, AI default
    removal, and external release/legal blockers.
  - UX runner worker fixed `scripts/run-ux-matrix.ps1` so real `OK (n tests)` instrumentation output
    exits cleanly, refreshes process state, force-stops app/test packages on timeout, and can be
    unit-tested through `UxMatrixScriptTest`.
  - Discover detach worker hardened `DiscoverCatalogFragment` callbacks and added
    `DiscoverCatalogDetachSafetyTest`.
  - SourceModel worker made deferred carry-forward source tracking synchronized and added
    `SourceModelCarryForwardConcurrencyTest`.
- Local crash hardening added `CrashSurfaceHardeningTest` and production fixes for:
  `BootReceiver` `FLAG_ACTIVITY_NEW_TASK`, updater install/settings intent resolve/catch guards,
  null/missing DownloadManager cursor columns in `UpdateViewModel`, and the remaining
  `DiscoverFilterListsFragment` unsupported-review async dialog path.
- AI is no longer a default user-facing release surface: `BuildConfig.AI_FEATURE_ENABLED` defaults
  false, Home/Discover AI controls are hidden unless `-PadawayEnableAi=true`, the default AI
  preference entry is removed, and README user-facing AI feature/how-to/permission/troubleshooting
  docs were removed.
- Live crash check: installed and launched the debug app on `adaway-api34`; `pidof org.adaway`
  returned a process id and filtered logcat showed no AdAway `FATAL EXCEPTION`, native crash,
  `SQLiteFullException`, or AdAway ANR. The only observed ANR noise was external
  `com.google.android.gms.persistent` emulator/system output.
- UX matrix passed after the runner fix:
  `.\scripts\run-ux-matrix.ps1 -OutputDir app\build\reports\ux-matrix-continuation93-devil`
  exited `0` and pulled screenshot artifacts for all tested variants.
- Focused unit/static stabilization gates passed:
  `CrashSurfaceHardeningTest`, `AiSurfaceContractTest`, `UxMatrixScriptTest`,
  `DiscoverCatalogDetachSafetyTest`, and `SourceModelCarryForwardConcurrencyTest`.
- Build gates passed with JDK 21, local Android SDK, no daemon/build cache, and strict dependency
  verification: `:app:assembleDebug`, `:app:compileDebugAndroidTestJavaWithJavac`, and
  `:app:compileDebugJavaWithJavac -PadawayEnableAi=true`.
- Runtime connected gates passed:
  - 29 tests, 0 failed for `SourceModelGenerationFailureTest`,
    `SourceModelHttpConditionalTest`, `DomainCheckerRuntimeTruthTest`, and `MigrationTest`.
  - 7 tests, 0 failed for `SourceUpdateServiceWorkManagerTest`,
    `VpnModelCacheInvalidationTest`, and `DomainCheckerRuntimeTruthTest`, covering scheduled
    update work registration plus VPN/domain suffix truth.
- Scale gates passed:
  - 100k stage-seeded allow-heavy benchmark: `syncMs=1809`, `rootCursorMs=584`.
  - 1M stage-seeded allow-heavy benchmark: `syncMs=25986`, `rootCursorMs=8266`.
  - 5M stage-seeded allow-heavy benchmark on `adaway-api34-16g`: Gradle `BUILD SUCCESSFUL in
    38m 38s`, `syncMs=270805`, `rootCursorMs=284247`, both under the explicit `300000ms`
    budgets.
- Standalone evidence file added: `tasks/benchmarks/continuation93-evidence.md` records the
  crash capture, UX matrix, focused unit/build gates, connected runtime gates, 100k/1M/5M metrics,
  and hygiene checks so the final status does not depend only on prose in this ledger.
- Release/legal hygiene gates passed: `scripts\check-license-boundary.ps1 -SourceMode WorkingTree`,
  `git diff --check` (only CRLF warnings), and strict dependency verification on the executed
  Gradle gates.
- Items intentionally not checked:
  - Ponytail simplification cleanup was deferred because behavior coverage came first and deleting
    legacy paths or replacing brittle source-text guards is a separate review slice.
  - The broad dirty worktree has now been split into reviewable commits in continuation 94.
  - A true clean-checkout proof, signed release build, SBOM artifact publication, and physical
    device smoke remain blocked by missing release signing/update trust secrets and hardware.
  - MIT remains blocked until GPL-derived code/assets are removed, rewritten, permission-cleared,
    and legally reviewed.

## Plan - 2026-06-15 Goal Continuation 94 Commit Slicing
- [x] Unstage accidental index state and classify the dirty tree into reviewable slices.
- [x] Commit release/build/legal gates separately from runtime and UI work.
- [x] Commit DB/runtime truth and performance work as its own slice.
- [x] Commit UX/stability/default-AI-off changes as their own slice.
- [x] Commit localized/help-resource cleanup separately.
- [x] Commit dormant asset/native cleanup separately.
- [x] Commit Android UI instrumentation, rule capability docs, and durable task/evidence logs
  separately.
- [x] Superseded by later branch push, PR creation, and passing PR CI evidence.

## Review - 2026-06-15 Goal Continuation 94
- Created reviewable commits from the broad worktree instead of one opaque checkpoint:
  - `8d65b712 build: harden release and license gates`
  - `0bf8a93e fix: make filter runtime truth generation-aware`
  - `e2a34e54 feat: simplify filter management UX`
  - `835de3db chore: remove legacy help resources`
  - `8f38464c chore: remove dormant packaged assets`
- Added `.gitignore` coverage for `tasks/benchmarks/*.pid` so local benchmark process markers stay
  out of commits while benchmark logs and evidence remain durable.
- Remaining expected commit after this note: UI instrumentation tests, rule-capability docs, task
  ledger, lessons, and benchmark evidence.
- Still open after committing: run post-commit verification from the committed tree, then decide
  whether to push/create a PR or keep the branch local.

## Plan - 2026-06-15 Goal Continuation 95 Build Split Cleanup
- [x] Replace the fake updater permission placeholder with a real `directRelease`
  build-type manifest overlay.
- [x] Keep normal `debug` and `release` manifests free of
  `android.permission.REQUEST_INSTALL_PACKAGES`.
- [x] Keep runtime self-update disabled in normal builds and enabled only for the
  direct APK distribution build.
- [x] Update release workflow and release docs from property-based
  `assembleRelease -PadawayEnableDirectApkUpdater=true` to `assembleDirectRelease`.
- [x] Update focused ATK-34 guards for the build split and merged-manifest outputs.
- [x] Run focused ATK-34 unit tests, manifest-processing tasks, debug build, license boundary,
  and hygiene checks.
- [x] Commit the reviewable build-split slice separately.

## Review - 2026-06-15 Goal Continuation 95
- Removed the fake `requestInstallPackagesPermission` placeholder from the base manifest and
  removed the `adawayEnableDirectApkUpdater` property path from Gradle.
- Added a `directRelease` build type and `app/src/directRelease/AndroidManifest.xml` overlay.
  Normal `debug`/`release` builds keep `DIRECT_APK_UPDATES_ENABLED=false`; `directRelease`
  generates `DIRECT_APK_UPDATES_ENABLED=true` and is the only merged manifest that declares
  `android.permission.REQUEST_INSTALL_PACKAGES`.
- Updated the tagged release workflow and `RELEASING.md` to build
  `:app:assembleDirectRelease` and copy `app-directRelease.apk` from the direct-release output
  directory.
- Extended the ATK-34 guard to reject the old property/placeholder mechanism, assert the
  build-type split, and ensure the dnsjava release resource strip task covers both `release` and
  `directRelease`.
- Fresh verification passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest.atk34*
  :app:processDebugMainManifest :app:processReleaseMainManifest
  :app:processDirectReleaseMainManifest :app:generateReleaseBuildConfig
  :app:generateDirectReleaseBuildConfig :app:assembleDebug --rerun-tasks
  --dependency-verification=strict --stacktrace`.
- Generated artifact check: debug and normal release merged manifests had no
  `REQUEST_INSTALL_PACKAGES`, fake no-op permission, or placeholder; `directRelease` merged
  manifest contained `android.permission.REQUEST_INSTALL_PACKAGES`; generated BuildConfig was
  `false` for `release` and `true` for `directRelease`.
- Fail-closed release check: unsigned `.\gradlew.bat :app:assembleDirectRelease
  --dependency-verification=strict --stacktrace` stopped at the signing/trust-material gate:
  `Release and release-SBOM builds require signingStoreLocation, signingStorePassword,
  signingKeyAlias, and signingKeyPassword.`
- License and hygiene checks passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree` and `git diff --check`
  (only existing CRLF conversion warnings).

## Plan - 2026-06-16 Goal Continuation 99 Root Hosts Writer Path
- [x] Add a materialized root-export line cursor so the production root writer can read one
  preformatted line column instead of assembling every materialized row from three cursor columns.
- [x] Route `RootModel` through the line cursor only when `root_host_entries` is a valid
  materialized export, preserving the active-generation fallback for non-materialized runtime truth.
- [x] Add focused database coverage proving IPv4-only and IPv6-enabled root file lines match the
  current redirect/block semantics.
- [x] Run focused compile/test gates and the root-hosts-file benchmark for this slice.

## Review - 2026-06-16 Root Hosts Writer Path
- `HostEntryDao` now exposes materialized root-export line cursors for IPv4 and IPv6 root hosts
  files. The IPv6 cursor avoids the first sorted-UNION attempt that measured poorly at 1M rows.
- `RootModel` uses the line cursor only when `root_export_materialized` is true and keeps the
  active-generation cursor fallback for non-materialized runtime truth.
- `RootModel` now writes generated hosts files with a 1 MiB `BufferedWriter` buffer.
- `SourceDbTest.testRootHostsLineCursorMaterializesFileLines` covers blocked, redirected, suffix,
  allow-filtered, IPv4-only, and IPv6-enabled line output.
- Fixed the `SourceLoaderPerformanceTest` root-write fixture to create the same `hosts_stats`
  metadata row as the production database callback and to mark seeded `root_host_entries` as a
  valid materialized export.
- Verification passed:
  `.\gradlew.bat :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Connected verification passed on `adaway-api34(AVD)`:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testRootHostsLineCursorMaterializesFileLines'
  --dependency-verification=strict --stacktrace`.
- Budgeted 1M root writer benchmark passed on `adaway-api34(AVD)`:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#rootModelCreateHostsFile_requestedRows_recordsWriteBenchmark'
  '-Pandroid.testInstrumentationRunnerArguments.adawayRootWriteRows=1000000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayRootWriteIpv4BudgetMs=30000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayRootWriteIpv6BudgetMs=70000'
  --dependency-verification=strict --stacktrace`; measured
  `RootModelHostsFileWriteBenchmark rows=1000000 ipv4Ms=23826 ipv4Bytes=35869214
  ipv6Ms=51173 ipv6Bytes=65459216`.

## Plan - 2026-06-16 Root Performance Gate Cleanup
- [x] Audit `getRootHostsFileCursor()` production callers and classify whether root-cursor timing is
  still user-path performance evidence.
- [x] Replace large performance-gate cursor timing with production root hosts file write timing,
  keeping cursor/list checks as correctness-only coverage elsewhere.
- [x] Add or update source guards so the allow-heavy benchmark exposes root-write budgets rather
  than stale root-cursor budgets.
- [x] Run focused compile/unit/connected gates plus 1M and 5M root writer proof.
- [x] Push the branch and inspect CI status.

## Review - 2026-06-16 Root Performance Gate Cleanup
- Production caller audit found `getRootHostsFileCursor()` is no longer the normal materialized
  root apply hot path. `RootModel` uses materialized root export rows when
  `root_export_materialized` is true and only falls back to the active cursor when the materialized
  export is unavailable.
- `SourceLoaderPerformanceTest` now reports production root hosts file generation metrics:
  `rootWriteMs` and `rootWriteBytes`. The stale large-gate root-cursor budget arguments were
  replaced with `adawayPerfRootWriteBudgetMs` and `adawayAllowRebuildRootWriteBudgetMs`.
- `Generation304MigrationTest.allowHeavyBenchmarkCanSeedRootExportStagePath` now guards that the
  allow-heavy benchmark exposes the root-write budget and does not keep the old root-cursor budget.
- The initial 5M root-writer proof exposed a real writer bottleneck: the SQL line-concatenation
  cursor failed the 300s IPv4 budget at `ipv4Ms=321075` on `adaway-api34-16g`.
- Replaced the SQL line-concatenation writer with a single materialized row cursor and piece-wise
  writes for redirection, separator, and hostname. This avoids materializing every full hosts line
  inside SQLite and preserves the active cursor fallback.
- Verification passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict
  --stacktrace` and `.\gradlew.bat :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Connected semantics gate passed on `adaway-api34-16g(AVD)`:
  `SourceDbTest#testRootHostsMaterializedCursorBuildsFileLines`.
- Connected 10k update/root-apply gate passed:
  `SourceLoaderPerformanceTest lines=10000 inserted=9500 runtimeRows=9000 progressEvents=5
  parseMs=5324 syncMs=199 rootRows=9000 rootWriteMs=319 rootWriteBytes=276558`.
- Connected 1M staged allow-heavy update plus immediate root-write gate passed:
  `HostEntryAllowHeavyRebuildBenchmark blockedRows=1000000 ... syncMs=19175
  rootRows=997000 rootWriteMs=14556 rootWriteBytes=53126324`, under explicit
  `30000ms` sync and root-write budgets.
- Connected 5M root-writer proof passed on `adaway-api34-16g(AVD)`:
  `RootModelHostsFileWriteBenchmark rows=5000000 ipv4Ms=196724 ipv4Bytes=183789214
  ipv6Ms=232078 ipv6Bytes=336139216`, under explicit `300000ms` IPv4 and `600000ms`
  IPv6 budgets.
- PR #6 connected tests failed on pushed head `154e257d` in
  `VpnModelCacheInvalidationTest`: CI returned `ALLOWED` where the setup expected `BLOCKED`.
  Local reproduction used
  `SourceDbTest,VpnModelCacheInvalidationTest`; the fix makes the VPN cache test seed external
  source rows in the current active generation, restores the original ad-block method after the
  test, and stops asserting the stale raw-DAO-cache behavior that is not a user-facing contract.
- Post-fix connected verification passed locally:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest,org.adaway.model.vpn.VpnModelCacheInvalidationTest'
  --dependency-verification=strict --stacktrace --rerun-tasks` and
  `.\gradlew.bat :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace`;
  the full suite reported 110 tests, 3 skipped, 0 failed.
- Pushed `e77abc40` to PR #6 and inspected replacement checks. GitHub reported
  `Development build`, `Connected Android tests`, `CodeQL`, `Analyze (cpp)`, and
  `Analyze (java)` all passing on the pushed head.
- Final completion audit found `RootModel.writeActiveHosts(...)` still called the ambiguous
  materialized-or-active `getRootHostsFileCursor()` fallback directly. Replaced that production
  caller with explicit `getActiveRootHostsFileCursor()` and added a source-contract guard so root
  apply uses `getRootHostsFileCursorMaterialized()` for the normal path and the named active
  streaming cursor for the fallback.
- Re-running the 5M proof on JDK 21 initially exposed a current-state regression:
  `RootModelHostsFileWriteBenchmark rows=5000000 ipv4Ms=332097 ipv4Bytes=183789214
  ipv6Ms=297052 ipv6Bytes=336139216`, failing the explicit `300000ms` IPv4 budget.
  Replaced the hot `BufferedWriter`/`OutputStreamWriter` hosts-line path with a reusable buffered
  byte writer while preserving UTF-8 handling for headers/source labels.
- Post-writer verification passed on JDK 21:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `SourceDbTest#testRootHostsMaterializedCursorBuildsFileLines`; and the required 5M proof:
  `RootModelHostsFileWriteBenchmark rows=5000000 ipv4Ms=267232 ipv4Bytes=183789214
  ipv6Ms=236081 ipv6Bytes=336139216`, under explicit `300000ms` IPv4 and `600000ms`
  IPv6 budgets.

## Plan - 2026-06-16 Goal Continuation 101 Current-Head Runtime And Scale Proof
- [x] Reconfirm PR #6 is green after the CI/CD repair slice.
- [x] Confirm local JDK 21, Android SDK, NDK `27.2.12479018`, and an API 34 AVD are available.
- [x] Run the current-head connected runtime-truth/update gates against `43081eff+`.
- [x] Run focused unit coverage for the parser/security contracts touched by the CI/security slice.
- [x] Start the next scale/performance gate from existing `SourceLoaderPerformanceTest`
  benchmarks and record whether 100k/1M/5M are green or still architectural gaps.

## Review - 2026-06-16 Goal Continuation 101
- PR #6 checks were green before this continuation:
  `Development build`, `Connected Android tests`, `Analyze (cpp)`, `Analyze (java)`, and
  aggregate `CodeQL` all passed on `43081eff`.
- Local environment is usable: JDK 21 is active; `local.properties` points to
  `C:/Users/solun/AppData/Local/Android/Sdk`; platform-tools, emulator, cmdline-tools, and
  NDK `27.2.12479018` exist. `adb` is not on PATH, so local commands use the SDK tools by
  absolute path.
- The local `adaway-api34` AVD is attached as `emulator-5554` on API 34 with
  `sys.boot_completed=1`.
- Current-head connected runtime truth/update gates passed locally:
  `SourceModelGenerationFailureTest`, `SourceModelHttpConditionalTest`,
  `DomainCheckerRuntimeTruthTest`, and `MigrationTest` passed with 28 tests and 0 failures;
  `SourceUpdateServiceWorkManagerTest`, `VpnModelCacheInvalidationTest`, and
  `DomainCheckerRuntimeTruthTest` passed with 6 tests and 0 failures; and the Subscribe-All
  worker guards `FilterListsSubscribeAllWorkerDoWorkTest` and
  `FilterListsSubscribeAllWorkerRoomTest` passed with 6 tests and 0 failures.
- Focused unit/parser/security coverage passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --tests org.adaway.security.SecurityHardeningTest --dependency-verification=strict
  --stacktrace`.
- The 100k allow-heavy runtime/root benchmark passed on `adaway-api34`:
  `HostEntryAllowHeavySeedBenchmark blockedRows=100000 exactAllowRules=100
  suffixAllowRules=100 seedRootStage=false stageRows=0 seedMs=15274 checkpointMs=19`;
  final rebuild metric `runtimeRows=99700 rootRows=99700 materializedRuntimeCache=true
  syncMs=6190 rootCursorMs=1599`.
- The direct 1M allow-heavy benchmark is red at the root-cursor gate:
  `syncMs=49150 rootCursorMs=21229`, failing the explicit `10000ms` root cursor budget.
- The staged 1M allow-heavy benchmark is also red at the root-cursor gate on both AVDs. On
  `adaway-api34`, the run failed with `syncMs=22336 rootCursorMs=15495`. On
  `adaway-api34-16g`, the repeated run failed with `syncMs=25128 rootCursorMs=14901`; its DAO
  phase evidence was `HostEntryDao.root-export-stage totalMs=19478`, followed by
  `HostEntryDao.sync skipped materialized runtime cache and rebuilt root export:
  activeRuleRows=1002000 maxRows=500000 clearMs=2054 rootExportMs=19482 totalMs=21536`.
- Root-cursor investigation evidence:
  a device-local SQLite probe showed the current cursor query is a full `root_host_entries`
  table scan, while a forced covering `(host, type, redirection)` index is a covering scan.
  The in-app instrumentation probe on the same 1M fixture measured DAO scan `14279ms`, raw
  table scan `11371ms`, forced covering scan `9512ms`, and covering-index creation `3121ms`.
- WIP schema/cursor patch evidence:
  Room schema v29 with covering `index_root_host_entries_host(host, type, redirection)` compiles,
  and focused migration/runtime plan tests passed:
  `MigrationTest#migration28To29_rebuildsRootExportCursorIndex` plus
  `SourceDbTest#testRootHostsCursorReadsActiveTruthAndMatchesListApi`.
- Negative WIP performance evidence:
  covering index plus materialized split cursor produced one transient pass at
  `syncMs=16963 rootCursorMs=9939`, but repeated follow-up attempts were not stable: the
  extra type-first cursor index regressed to `syncMs=26821 rootCursorMs=18420`, the Java
  no-redirect split cursor failed at `syncMs=19647 rootCursorMs=11070`, and the SQLite
  no-redirect constant cursor failed at `syncMs=19489 rootCursorMs=13883`.
- Current conclusion: PR CI and runtime correctness are green, and the 100k gate is green. The
  current-head 1M gate is blocked by the materialized root export read path, not by generation
  activation or update correctness. Do not claim the performance phase complete, and do not
  merge the current WIP cursor patch as a fix, until the root apply/read architecture is changed
  enough to pass the 10s 1M cursor gate with margin.

## Plan - 2026-06-15 Goal Continuation 100 Connected CI Evidence
- [x] Re-check PR #6 after the CodeQL and dependency fixes.
- [x] Confirm `Development build`, `Analyze (cpp)`, `Analyze (java)`, and aggregate `CodeQL`
  are green on commit `cf27e604`.
- [x] Classify the remaining CI risk as the long-running `Connected Android tests` job, not an
  app compile, unit-test, lint, or CodeQL failure.
- [x] Harden the connected-test workflow with bounded job, emulator-boot, and instrumentation
  timeouts.
- [x] Add failure evidence capture for emulator logs, logcat, device state, and androidTest
  reports.
- [x] Commit the CI hardening patch and let the replacement connected-test lane prove itself.

## Review - 2026-06-15 Goal Continuation 100
- Live PR status after the earlier fixes: Android `Development build`, CodeQL C++, CodeQL Java,
  and the aggregate CodeQL security check passed. The only remaining non-green check was
  `Connected Android tests`, which stayed `in_progress` without downloadable logs.
- The connected-test job had no explicit job timeout and the Gradle instrumentation step had no
  step timeout. The GitHub logs endpoint does not expose logs while the job is in progress, so a
  stuck emulator/test run can leave the PR pending without evidence.
- Split the connected test shell into boot, logcat capture, bounded instrumentation, diagnostics,
  and artifact upload. This preserves the existing strict
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` gate while making
  hangs fail with evidence instead of waiting for the platform default timeout.
- The replacement run reached the `Boot emulator` step and stayed there long enough to expose one
  more unbounded call. `adb wait-for-device` is now wrapped with `timeout 300`, so emulator device
  detection is bounded independently of the outer boot-step timeout.
- The next replacement run proved the boot timeout fires at the expected five-minute boundary, then
  exposed another harness issue: diagnostics can hang when they run against a missing emulator.
  Every diagnostic `adb` call now has a short timeout so failure collection uploads evidence instead
  of becoming the new stuck step.
- The uploaded artifact then exposed the real boot failure: `emulator.log` reported
  `Unknown AVD name [adaway-api34]` and no `$HOME/.android/avd/adaway-api34.ini`. The boot step now
  exports `ANDROID_AVD_HOME=$HOME/.android/avd`, creates that directory before `avdmanager`, records
  `emulator -list-avds`, and fails before launch if `adaway-api34` is not visible.

## Plan - 2026-06-15 Goal Continuation 98 Fresh Regression Proof
- [x] Prove the committed tree is clean before running the phase.
- [x] Run a fresh committed-tree unit/build gate after the build-split, AI-removal, and
  Subscribe-All guard-replacement commits.
- [x] Start a local AVD when needed and rerun the broader connected runtime truth/update gates.
- [x] Rerun the UX matrix against the current committed app.
- [x] Run license and diff hygiene gates.
- [x] Record the exact evidence here and commit the evidence-only ledger update.

## Review - 2026-06-15 Goal Continuation 98
- Detached clean proof worktree was created at
  `C:\Users\solun\.config\superpowers\worktrees\AdAway\codex-market-leading-quality-clean-proof`
  from `fe1ad2c8`.
- Initial clean proof command failed in unit tests:
  `.\gradlew.bat --no-daemon --no-build-cache :app:testDebugUnitTest :app:assembleDebug
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Failure shape: 376 tests ran, 3 failed, 3 skipped. The three failures were
  `Generation304MigrationTest.largeRootExportSkipsRedirectPhaseWhenNoRedirectRules`,
  `SecurityHardeningTest.atk34_releaseBuildStripsDnsjavaDesktopResolverSpi`, and
  `DiscoverPresetSubscriptionTest.filterListsBulkReviewDetailsUsePersistedOutcomeLedger`.
- Root cause: the detached Windows clean worktree checked out source files with CRLF line endings,
  while those three contract tests searched exact `\n` source snippets. The tested production
  contracts were still present; the test readers were not line-ending-stable.
- Focused fix verification passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest.largeRootExportSkipsRedirectPhaseWhenNoRedirectRules
  --tests org.adaway.security.SecurityHardeningTest.atk34_releaseBuildStripsDnsjavaDesktopResolverSpi
  --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest.filterListsBulkReviewDetailsUsePersistedOutcomeLedger
  --dependency-verification=strict --rerun-tasks --stacktrace`.
- Clean proof worktree was advanced to `d7f5c729` after the line-ending-stable test fix.
- Fresh clean unit/build proof passed:
  `.\gradlew.bat --no-daemon --no-build-cache :app:testDebugUnitTest :app:assembleDebug
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  Gradle reported `BUILD SUCCESSFUL in 1m 55s`.
- Connected runtime gate 1 passed on `adaway-api34(AVD)`:
  `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.model.source.SourceModelHttpConditionalTest,org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest,org.adaway.db.MigrationTest'
  --dependency-verification=strict --stacktrace`; Gradle reported 28 tests, 0 failed.
- Connected runtime gate 2 passed on `adaway-api34(AVD)`:
  `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceUpdateServiceWorkManagerTest,org.adaway.model.vpn.VpnModelCacheInvalidationTest,org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest'
  --dependency-verification=strict --stacktrace`; Gradle reported 6 tests and
  `BUILD SUCCESSFUL`.
- Recent Subscribe-All connected guard tests passed on `adaway-api34(AVD)`:
  `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest,org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest'
  --dependency-verification=strict --stacktrace`; Gradle reported 6 tests and
  `BUILD SUCCESSFUL`.
- UX matrix passed from the clean proof tree:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation98-clean-proof`; the script exited 0 and pulled
  21 screenshot/artifact files from all three tested variants.
- License and hygiene checks passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree` and `git diff --check`
  (only existing CRLF conversion warnings).

## Plan - 2026-06-15 Goal Continuation 99 CI Dependency Resolution
- [x] Inspect PR #6 failing Android CI and CodeQL jobs.
- [x] Confirm the shared failure is Gradle resolving `com.github.topjohnwu.libsu:core:6.0.0`
  from JitPack, not a unit-test, lint, SDK, or CodeQL analysis failure.
- [x] Add a narrow local Maven mirror for the single JitPack-only `libsu` artifact while keeping
  strict dependency verification and the content-filtered JitPack fallback.
- [x] Add provenance documentation and a security regression guard for repository scope/order.
- [x] Rerun the Android CI build-side gates locally and record evidence.

## Review - 2026-06-15 Goal Continuation 99
- PR #6 failed in Android CI `Development build` and CodeQL `Analyze` before tests/analysis could
  run because `:app:dataBindingMergeDependencyArtifactsDebug` received HTTP 403 from JitPack for
  `https://jitpack.io/com/github/topjohnwu/libsu/core/6.0.0/core-6.0.0.pom`.
- Direct local `curl.exe -I -L` to the JitPack POM, AAR, and module returned HTTP 200, so the
  failure is runner/network-sensitive. Keeping CI dependent on that remote remains flaky.
- Added `third_party/maven/com/github/topjohnwu/libsu/core/6.0.0/` with the verified `core`
  AAR, POM, and Gradle module metadata. Hashes match `gradle/verification-metadata.xml`.
- Added the local Maven mirror before JitPack in `settings.gradle`, scoped to
  `com.github.topjohnwu.libsu`; JitPack stays as a content-filtered fallback for this group only.
- Added `third_party/maven/README.md`, updated `THIRD_PARTY_LICENSES.md`, pinned mirrored Maven
  artifacts as binary in `.gitattributes`, and extended `SecurityHardeningTest` so the local mirror
  remains centralized, byte-stable, and preferred before JitPack.
- Empty-cache/offline proof passed in a temporary Gradle project using only `third_party/maven`
  and `--dependency-verification=strict`: `resolved=core-6.0.0.aar:40221`,
  `BUILD SUCCESSFUL in 39s`.
- Android CI build-side verification passed locally with JDK 21:
  `.\gradlew.bat --no-daemon test --dependency-verification=strict --stacktrace`,
  `.\gradlew.bat --no-daemon :app:lintDebug --dependency-verification=strict --stacktrace`,
  and `.\gradlew.bat --no-daemon assembleDebug --dependency-verification=strict --stacktrace`.
- Focused security/license verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict`,
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1`,
  and `git diff --check` (only existing CRLF conversion warnings).
- After pushing `331c35c4`, Android CI `Development build` and CodeQL `Analyze (java)` passed.
  CodeQL `Analyze (cpp)` then failed at finalization with `CodeQL could not process any code
  written in C/C++` because the shared `assembleDebug` build does not compile the dormant native
  source tree.
- Updated CodeQL advanced setup so C++ uses a separate `build-mode: none` initializer. Java uses
  the previous initializer shape because CI proved `build-mode: manual` finalizes with no
  Java/Kotlin extracted; Android SDK setup and `./gradlew assembleDebug
  --dependency-verification=strict` now run only for the Java job.
- The later Java-only failure was caused by CodeQL seeing an up-to-date/cached `assembleDebug`
  build that did not execute Java compilation. The Java CodeQL build now adds `--rerun-tasks` so
  extraction observes real compile work on every run.
- Once both CodeQL language jobs passed, the aggregate GitHub Advanced Security check still failed
  on two high-severity CodeQL alerts in `SourceLoader` for backtracking-prone Surge/Clash option
  regexes. Replaced the nested comma-option capture with a single linear option capture and added a
  hostile comma-run regression test.

## Plan - 2026-06-15 Goal Continuation 97 Subscribe-All Guard Replacement
- [x] Replace the Subscribe-All cancellation source-text test with behavior coverage.
- [x] Extract the cancel-finalization sequence into a package-visible helper with production
  behavior unchanged.
- [x] Add connected coverage proving cancelled runs flush pending source inserts, persist the
  outcome ledger, cancel notification progress, and return cancelled output.
- [x] Add connected coverage proving a stopped worker returns a cancelled result promptly while a
  filter-list detail request is still blocked.
- [x] Keep the remaining SourceModel carry-forward concurrency sentinel for now because existing
  connected generation-failure tests cover carry-forward behavior, while the internal thread-safe
  collection guard is cheaper and less invasive than adding test-only hooks.
- [x] Run unit, compile, debug build, and hygiene gates for this slice.

## Review - 2026-06-15 Goal Continuation 97
- `FilterListsSubscribeAllWorker.finishCancelled(...)` now delegates to
  `finalizeCancelledRun(...)`, which performs the same shutdown, recorder flush, outcome
  persistence, notification cancellation, and cancelled output construction without requiring a
  source-text test.
- Removed `cancelPathPollsAndFlushesBeforeReportingCancelled` and its `readRepoFile` helper from
  `FilterListsSubscribeAllWorkerTest`.
- Added connected tests in `FilterListsSubscribeAllWorkerRoomTest` and
  `FilterListsSubscribeAllWorkerDoWorkTest` for cancelled-run persistence and stop-while-resolving
  behavior.
- Focused unit verification passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests
  org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest --dependency-verification=strict
  --rerun-tasks --stacktrace`.
- Compile/build verification passed:
  `.\gradlew.bat :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --rerun-tasks
  --stacktrace` and `.\gradlew.bat :app:assembleDebug --dependency-verification=strict
  --rerun-tasks --stacktrace`.
- Connected worker verification passed after starting the local `adaway-api34` AVD:
  `.\gradlew.bat :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsSubscribeAllWorkerDoWorkTest,org.adaway.ui.hosts.FilterListsSubscribeAllWorkerRoomTest'
  --dependency-verification=strict --stacktrace`; Gradle reported 6 tests on
  `adaway-api34(AVD)` and `BUILD SUCCESSFUL`.

## Plan - 2026-06-15 Goal Continuation 96 AI Feature Cut
- [x] Remove the dormant AI feature gate from Gradle instead of keeping a default-off
  product path.
- [x] Remove Home and Discover AI entry points so the default app has one simpler mental model:
  Home for protection/status and Discover for curated filter presets.
- [x] Remove AI production/UI/settings sources, layouts, strings, and AI-only unit tests.
- [x] Update runtime-truth guards so user-rule mutation coverage tracks the remaining product
  surfaces and asserts that the AI mutation surface is gone.
- [x] Replace the default-AI-off tests with removal contracts that fail if AI hooks or docs
  return.
- [x] Run focused unit tests and debug/androidTest compile gates for this slice.
- [x] Run final license/hygiene gates for this reviewable slice before commit.

## Review - 2026-06-15 Goal Continuation 96
- Removed `AI_FEATURE_ENABLED` and `adawayEnableAi` from `app/build.gradle`.
- Removed Home AI binding, Discover AI chip wiring, AI settings fragment, AI model/UI source
  packages, AI layouts/resources, and AI-only tests.
- Kept the simple product path explicit: Discover retains Safe, Balanced, and Aggressive preset
  chips as the primary filter-entry surface.
- Updated contract coverage in `AiSurfaceContractTest`,
  `HomeNavigationSourcesContractTest`, and `Generation304MigrationTest`; removed the AI-specific
  connected runtime-truth test because that product surface no longer exists.
- Production scan passed with no matches under `app/src/main` for the removed AI gate/classes,
  resources, preference IDs, or user-facing AI strings.
- Focused verification passed:
  `.\gradlew.bat :app:testDebugUnitTest --tests org.adaway.ui.home.AiSurfaceContractTest
  --tests org.adaway.ui.home.HomeNavigationSourcesContractTest
  --tests org.adaway.model.source.Generation304MigrationTest --dependency-verification=strict
  --rerun-tasks --stacktrace`.
- Compile verification passed:
  `.\gradlew.bat :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug
  --dependency-verification=strict --rerun-tasks --stacktrace`.
- License and hygiene checks passed:
  `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree` and `git diff --check`
  (only existing CRLF conversion warnings).

## Plan - 2026-06-17 Remaining Work Map And UX Text-Fit Matrix
- [x] Reconfirm current PR/worktree state and map remaining live rocks.
- [x] Select the next implementable slice from release/signing, real-device smoke,
  screenshot/accessibility, and MIT clearance.
- [x] Harden the UX matrix to catch visible ellipsized or vertically clipped text under the
  existing baseline, large-font, and RTL variants.
- [x] Run focused compile/unit verification and the device UX matrix.
- [x] Commit, push, and inspect CI for this slice.

## Review - 2026-06-17 Remaining Work Map And UX Text-Fit Matrix
- Current map: full release/signing proof still needs signing secrets; physical release smoke
  still needs a real device; MIT remains blocked by GPL-derived code/assets and legal clearance;
  screenshot/accessibility was the next locally actionable slice.
- Added a `TextView` fit audit to `UxDeviceMatrixTest` so the matrix now fails on visible
  ellipsized text or text layout height that exceeds the available view height.
- Focused compile/unit verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.scripts.UxMatrixScriptTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- UX matrix verification passed:
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-continuation-current -InstrumentationTimeoutSeconds 420`.
- The UX matrix produced 21 screenshots: seven screens each for `baseline`, `font-1.3`, and
  `font-1.3-rtl`.

## Plan - 2026-06-17 CI Runtime Hygiene
- [x] Inspect the current PR head, workflow files, and CI warning output.
- [x] Update SHA-pinned GitHub Actions to current Node 24-compatible refs while preserving pinning.
- [x] Replace the release cleanup action that still runs on Node 20 with an equivalent `gh release`
  shell step that keeps source tags.
- [x] Verify workflow YAML parses and every referenced action reports `node24` or `composite`.
- [x] Commit, push, and inspect PR CI for this slice.

## Review - 2026-06-17 CI Runtime Hygiene
- Updated Android CI, CodeQL, locale validation, release, and issue workflows away from
  Node 16/20 action runtimes. CodeQL now uses the v4 line instead of the deprecated v3 line.
- Kept all third-party actions pinned to immutable SHAs instead of floating tags.
- Replaced `dev-drprasad/delete-older-releases` with `gh release list` and `gh release delete`
  because its latest tag still declares `node20`; the replacement keeps tags by avoiding
  `--cleanup-tag`.
- First pushed CI run failed in `SecurityHardeningTest` because the old regression expected the
  removed `delete_tags: false` action input. Updated the test to assert the new invariant directly:
  cleanup uses `gh release delete` and does not pass `--cleanup-tag`.
- Verification passed:
  Python parsed every `.github/workflows/*.yml` file, `git diff --check` passed with only existing
  CRLF warnings, the action metadata verifier reported only `node24` or `composite` runtimes, and
  `.\gradlew.bat --no-daemon test --dependency-verification=strict --stacktrace` passed.
- The replacement Android CI run passed development build and CodeQL, then timed out during the
  connected-test lane while running `org.adaway.ui.UxDeviceMatrixTest`. The CI log reached
  `Tests 89/107 completed...` and then showed only continuous emulator frame stats without an
  instrumentation completion line.
- Replaced the UX matrix test's unbounded `waitForIdleSync()` calls with bounded main-thread drains
  around the existing fixed idle wait. This keeps the screenshot audit deterministic when an
  activity continues rendering and avoids letting instrumentation block forever waiting for a fully
  idle UI.
- Post-fix verification passed:
  `.\gradlew.bat --no-daemon :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`,
  focused connected `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.UxDeviceMatrixTest
  --dependency-verification=strict --stacktrace`, and
  `.\scripts\run-ux-matrix.ps1 -OutputDir app\build\reports\ux-matrix-ci-idle-fix
  -InstrumentationTimeoutSeconds 420`.
- The UX matrix runner exited 0 for `baseline`, `font-1.3`, and `font-1.3-rtl`, each with
  `OK (1 test)`, and pulled 21 screenshots total.
- The next pushed Android CI run on `73a8853b` confirmed the idle hang was gone: the connected
  suite finished instead of timing out. It then exposed two full-suite/order-dependent failures:
  `VpnModelCacheInvalidationTest.sourceModelSyncHostEntriesInvalidatesLiveVpnTruth` read stale
  or polluted baseline VPN truth as `ALLOWED`, and `UxDeviceMatrixTest` found the Sources
  `sourceStatus` row ellipsized as `Updated few minutes 2 skipped`.
- Hardened the VPN cache test by giving each test a unique `.invalid` host, explicitly
  invalidating the VPN cache after setup-only direct syncs, and sweeping any orphaned
  cache-invalidation user rows from prior interrupted runs. This keeps the test aimed at the
  SourceModel invalidation behavior instead of inherited singleton app database state.
- Let Sources status text wrap by removing the `sourceStatus` ellipsize/max-line cap from
  `filter_source_item.xml`; the row can grow instead of hiding provenance or skipped-rule state.
- Post-fix verification passed:
  `.\gradlew.bat --no-daemon :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`,
  focused connected `VpnModelCacheInvalidationTest,UxDeviceMatrixTest`,
  full connected `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  --dependency-verification=strict --stacktrace` with `110` tests, `0` failures, `3` skipped,
  and `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-ci-failure-fix -InstrumentationTimeoutSeconds 420`.

## Plan - 2026-06-17 Ponytail Legacy Pipeline Cleanup
- [x] Confirm the latest pushed bird-provenance slice is green in Android CI and CodeQL.
- [x] Delete the unreachable pre-staged `retrieveHostsSources()` implementation and the
  always-true delegation switch.
- [x] Remove the unused Java-side global de-dup parser path now that staged SQL de-dup is the
  live production path.
- [x] Keep single-source list-download progress working through `downloadToTempFile`.
- [x] Run focused unit/compile, connected SourceModel, license-boundary, and hygiene gates.
- [x] Commit, push, and inspect CI for this slice.

## Review - 2026-06-17 Ponytail Legacy Pipeline Cleanup
- Android CI run `27703347485` passed on `0f1df8a8`: development build, unit tests, lint,
  debug build, and connected Android tests were all green. CodeQL run `27703347888` also passed.
- Simplified `SourceModel.retrieveHostsSources()` to delegate directly to
  `checkAndRetrieveHostsSources()` and removed the old unreachable download/import body,
  `useStagedPipelineForLegacyRetrieve()`, the unused `downloadHostSource()` wrapper, the
  unused `DownloadCallable`, and stale sentinel/source id plumbing from `DownloadResult`.
- Simplified `SourceLoader` by deleting the no-longer-called Java global de-dup overloads and
  `buildDedupKey()`. The active staged SQL de-dup path remains the single production model.
- Restored real current-list progress reporting inside `downloadToTempFile` while copying a
  source response to its temporary staging file.
- Focused verification passed:
  `.\gradlew.bat --no-daemon :app:compileDebugJavaWithJavac :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest --tests
  org.adaway.model.source.SourceLoaderParserPatternsTest --dependency-verification=strict
  --stacktrace`.
- Android-test compile passed:
  `.\gradlew.bat --no-daemon :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Connected verification passed:
  `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.model.source.SourceModelHttpConditionalTest"
  --dependency-verification=strict --stacktrace`; Gradle reported 10 tests on
  `adaway-api34-16g(AVD) - 14`, 0 failed.
- Full local unit verification passed:
  `.\gradlew.bat --no-daemon test --dependency-verification=strict --stacktrace`.
- License and hygiene checks passed:
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree` and `git diff --check` with only existing CRLF conversion warnings.

## Plan - 2026-06-18 Domain Checker CI Observer Cleanup
- [x] Inspect replacement CI for `456e7b30`.
- [x] Pull the failing connected-test log and identify the exact failure.
- [x] Fix the failing test helper without changing production domain-checker behavior.
- [x] Verify the exact failing connected test locally.
- [x] Run focused compile/unit, hygiene, and license-boundary checks.
- [x] Commit, push, and inspect replacement CI.

## Review - 2026-06-18 Domain Checker CI Observer Cleanup
- CodeQL run `27725893043` passed on `456e7b30`.
- Android CI run `27725893032` passed the development build job, then failed in the connected
  Android tests job. The failing test was
  `DomainCheckerRuntimeTruthTest.domainCheckerUsesRootExactTruthAndVpnSuffixTruth`.
- CI failure evidence:
  `java.lang.IllegalStateException: Cannot invoke removeObserver on a background thread` at
  `DomainCheckerRuntimeTruthTest.check(DomainCheckerRuntimeTruthTest.java:119)`.
- Fixed the test helper by routing `checkResult.removeObserver(...)` through
  `InstrumentationRegistry.getInstrumentation().runOnMainSync(...)` whenever the callback or
  timeout cleanup is not already on the Android main thread.
- Focused connected verification passed:
  `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest#domainCheckerUsesRootExactTruthAndVpnSuffixTruth"
  --dependency-verification=strict --stacktrace`; Gradle reported 1 test on
  `adaway-api34-16g(AVD) - 14`.
- Focused compile/unit verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- License and hygiene checks passed:
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree` and `git diff --check` with only existing CRLF conversion warnings.

## Plan - 2026-06-18 Root Hosts Writer Scale Proof
- [x] Reproduce the 5M mixed root-writer failure with corrected benchmark metadata.
- [x] Reject the redirect-map and computed-line cursor experiments when they failed or regressed
  the proof gate.
- [x] Replace row-by-row materialized root export with bounded SQLite line chunks and direct
  chunk writes.
- [x] Preserve active-cursor fallback behavior and materialized root output semantics.
- [x] Prove the 1M and 5M mixed root-writer gates under explicit budgets.
- [x] Run focused compile/unit, connected root/database semantics, full unit, hygiene, and
  license-boundary checks.

## Review - 2026-06-18 Root Hosts Writer Scale Proof
- Corrected `rootModelCreateHostsFile_requestedRows_recordsWriteBenchmark` to print `seedMs`
  and seed `hosts_stats` consistently with its mixed blocked/redirected fixture. This exposed
  that stale metadata could hide redirect-capable root writer cost.
- Rejected the hash-map and streaming-merge redirect experiments as insufficient at 5M:
  `RootModelHostsFileWriteBenchmark rows=5000000 seedMs=835532 ipv4Ms=400626
  ipv6Ms=360311` failed the explicit `300000ms` budgets even though semantics stayed correct.
- Rejected the computed-line per-row cursor because it regressed 1M to
  `ipv4Ms=13905 ipv6Ms=24809` versus the faster streaming baseline.
- Implemented bounded materialized chunks: `HostEntryDao` now returns `GROUP_CONCAT` line
  chunks of `8192` root rows ordered by `root_host_entries.id`, and `RootModel` writes each
  chunk through the direct UTF-8 path. This avoids millions of Java cursor-row callbacks while
  keeping memory bounded.
- 1M proof passed:
  `RootModelHostsFileWriteBenchmark rows=1000000 seedMs=248727 ipv4Ms=2562
  ipv4Bytes=35869214 ipv6Ms=3494 ipv6Bytes=65459216`, under explicit `30000ms`
  IPv4 and IPv6 budgets.
- 5M proof passed:
  `RootModelHostsFileWriteBenchmark rows=5000000 seedMs=1068890 ipv4Ms=150041
  ipv4Bytes=183789214 ipv6Ms=147501 ipv6Bytes=336139216`, under explicit `300000ms`
  IPv4 and IPv6 budgets.
- Focused verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.model.source.Generation304MigrationTest :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Connected root/database semantics passed:
  `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.SourceDbTest#testRootHostsMaterializedCursorBuildsFileLines,org.adaway.db.SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage,org.adaway.db.SourceDbTest#testIncompleteRootExportStageFallsBackToActiveRules,org.adaway.db.SourceDbTest#testCompleteRootExportStageIgnoresDisabledSources"
  --dependency-verification=strict --stacktrace`; Gradle reported 4 tests on
  `adaway-api34-16g(AVD) - 14`.
- Full local unit verification passed:
  `.\gradlew.bat --no-daemon test --dependency-verification=strict --stacktrace`.
- License and hygiene checks passed:
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree` and `git diff --check` with only existing CRLF conversion warnings.

## Plan - 2026-06-18 Release Smoke Identity Gate
- [x] Confirm current release/SBOM gates fail closed without signing and update-manifest trust
  material.
- [x] Split locally provable APK identity verification from the physical-device install/launch
  smoke without weakening the real-device gate.
- [x] Fix concrete release-smoke certificate verification bugs found while exercising a signed APK.
- [x] Document the new identity-only command and keep the full smoke command documented.
- [x] Verify success and fail-closed behavior with a disposable signed direct-release APK.
- [x] Run focused security tests, release/SBOM checks, license-boundary, and hygiene gates.

## Review - 2026-06-18 Release Smoke Identity Gate
- Unsigned `.\gradlew.bat --no-daemon :app:generateSbom --dependency-verification=strict
  --stacktrace` still fails closed at configuration time with
  `Release and release-SBOM builds require signingStoreLocation, signingStorePassword,
  signingKeyAlias, and signingKeyPassword`.
- This Windows environment cannot currently run the Bash manifest script locally: `bash --version`
  invokes WSL and reports no installed distributions, and `openssl version` is not on PATH.
- Added `-VerifyOnly` to `scripts/run-release-smoke.ps1`. It now checks APK badging and optional
  signer identity, then exits before `adb` discovery, so artifact identity can be verified on CI
  or local machines without a connected device.
- Preserved the full physical-device smoke path: running the script without `-VerifyOnly` against
  the disposable signed APK reached device validation and failed on the attached emulator with
  `Release smoke must run on a real physical device, not an emulator`.
- Fixed two certificate-check bugs in the smoke script: colon-separated SHA-256 digests are now
  parsed after the literal `certificate SHA-256 digest:` label, and normalized digest comparisons
  are parenthesized so PowerShell compares the two function results instead of parser tokens.
- `RELEASING.md` now documents both commands: `-VerifyOnly` for release APK identity/signature
  verification and the existing full `run-release-smoke.ps1` command for install/launch on a real
  device.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`;
  disposable signed `.\gradlew.bat --no-daemon :app:assembleDirectRelease
  --dependency-verification=strict --stacktrace`; disposable signed
  `.\gradlew.bat --no-daemon :app:generateSbom --dependency-verification=strict --stacktrace`;
  `.\scripts\run-release-smoke.ps1 -ApkPath
  app\build\outputs\apk\directRelease\app-directRelease.apk -ExpectedCertSha256
  987c85a1c68a5cc68120ea5b4350610bc386035b1a4c21840a8349f6685f7166 -VerifyOnly`.
- Fail-closed script checks passed: `-VerifyOnly` rejects `app-debug.apk` as debuggable, and the
  disposable signed APK fails when `-ExpectedCertSha256` is all zeros.
- License and hygiene checks passed:
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree` and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: production release proof still needs real signing/update-manifest
  secrets, a working Bash/OpenSSL environment for local manifest signing or CI-only manifest proof,
  a physical-device release smoke, broader UX/accessibility proof, and MIT relicensing clearance.

## Plan - 2026-06-18 Cross-Platform Update Manifest Generator
- [x] Inspect the Bash manifest generator, release workflow, release docs, and security guards.
- [x] Replace shell-specific signing logic with one JDK-based generator used by both shell wrappers.
- [x] Preserve the GitHub Linux release workflow entrypoint while adding a Windows PowerShell
  entrypoint for local release proof.
- [x] Add behavior proof that generates a real RSA keypair, signs `manifest.json`, and verifies
  the signature over the embedded payload.
- [x] Prove the PowerShell wrapper succeeds locally and fails closed on unsafe APK URLs.
- [x] Run focused security tests, license-boundary, and hygiene checks.

## Review - 2026-06-18 Cross-Platform Update Manifest Generator
- Added `scripts/GenerateUpdateManifest.java` as the canonical update-manifest generator. It
  validates the same release URL boundary, hashes the APK, builds the exact embedded payload,
  signs with `SHA256withRSA`, optionally verifies with the SPKI public key, writes
  `manifest.json`, and emits `manifest.json.sha256`.
- Replaced `scripts/generate-update-manifest.sh` with a thin Linux wrapper around the Java
  generator, so `.github/workflows/fork-release-apk.yml` can keep invoking the same Bash
  entrypoint on GitHub-hosted Linux runners.
- Added `scripts/generate-update-manifest.ps1` as the Windows/local wrapper. Local manifest
  generation now depends on JDK 21, which the project already requires, instead of WSL, Bash, or
  OpenSSL.
- Updated `RELEASING.md` so the local PowerShell release checklist uses
  `.\scripts\generate-update-manifest.ps1`; it also documents that both wrappers delegate to the
  same JDK-based generator.
- Added `SecurityHardeningTest.atk34_updateManifestGeneratorSignsAndVerifiesLocally`, which
  generates a temporary RSA keypair, runs `java scripts/GenerateUpdateManifest.java`, parses the
  generated envelope, verifies the signature over the exact embedded payload, checks core payload
  fields, and confirms the checksum is written by manifest basename.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`;
  `.\scripts\generate-update-manifest.ps1` with a temporary RSA keypair and dummy APK generated
  `manifest.json` and `manifest.json.sha256`; the same wrapper rejected an `http://` APK URL with
  `apk-url must use HTTPS`.
- License and hygiene checks passed:
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree` and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: production release proof still needs real signing/update-manifest
  secrets, physical-device release smoke, broader UX/accessibility proof, and MIT relicensing
  clearance.

## Plan - 2026-06-18 Expanded Dynamic-Type UX Matrix
- [x] Inspect the current UX matrix runner, accessibility audit, and existing UI ledger.
- [x] Probe stronger dynamic-type coverage before making it part of the default runner.
- [x] Add default `font-1.6` and `font-1.6-rtl` UX matrix variants.
- [x] Fix the concrete Home stat-card label issue found in the 1.6 RTL screenshot.
- [x] Strengthen the connected UX audit so visible horizontal text overflow fails the matrix.
- [x] Verify focused unit/static guards, Android-test compile, expanded device matrix,
  license-boundary, and hygiene gates.

## Review - 2026-06-18 Expanded Dynamic-Type UX Matrix
- The existing runner covered baseline, `font-1.3`, and `font-1.3-rtl`. One-off probes with
  `font_scale=1.6` and `font_scale=1.6` plus `ar-XB` passed, so the stronger variants were added
  to `scripts/run-ux-matrix.ps1` as default coverage.
- Visual spot-check of the first 1.6 RTL Home screenshot exposed a polish issue that the old audit
  could not catch: the `Redirected` stat-card label wrapped as `Redirect` / `ed`.
- Updated the three Home stat-card labels to use the full card width, center gravity, one line, and
  autosizing from `10sp` to `12sp`, so the labels stay readable at 1.6 font scale without
  splitting short words.
- Strengthened `UxDeviceMatrixTest.auditTextFits(...)` to fail on visible horizontal text overflow
  using `getLineRight(...)`, `getLineLeft(...)`, and available text width, in addition to the
  existing ellipsis and vertical clipping checks.
- Added static guards in `UxMatrixScriptTest` for the five default variants and in
  `HomeNavigationSourcesContractTest` for Home stat-label autosizing plus the horizontal-overflow
  audit.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.scripts.UxMatrixScriptTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `.\scripts\run-ux-matrix.ps1 -OutputDir
  app\build\reports\ux-matrix-font16-expanded-final -InstrumentationTimeoutSeconds 420`.
- The expanded UX matrix produced 35 screenshots: seven each for `baseline`, `font-1.3`,
  `font-1.6`, `font-1.3-rtl`, and `font-1.6-rtl`. The final `font-1.6-rtl` Home screenshot keeps
  `Redirected` on one line.
- License and hygiene checks passed:
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree` and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: production release proof with real secrets, physical-device release
  smoke, broader manual UX review beyond the automated key-screen matrix, and MIT relicensing
  clearance.

## Plan - 2026-06-18 Scripted Post-Release Artifact Verification
- [x] Confirm the previous dynamic-type UX slice is green in CodeQL and Android CI.
- [x] Inspect the release workflow, local release docs, manifest generator, smoke script, and
  release security tests.
- [x] Add a canonical JDK-based verifier for downloaded release assets.
- [x] Add PowerShell and Bash wrappers so Windows and GitHub/Linux workflows can use the same
  verifier.
- [x] Replace the manual post-release checksum/attestation checklist with one scripted command in
  `RELEASING.md`.
- [x] Add security regression tests proving the verifier accepts a matching signed manifest and
  rejects a tampered APK.
- [x] Run focused security, wrapper help, license-boundary, and hygiene gates.

## Review - 2026-06-18 Scripted Post-Release Artifact Verification
- CodeQL run `27729910144` and Android CI run `27729910188` both passed on
  `0e2fa87e` before this slice started.
- Added `scripts/VerifyReleaseArtifacts.java` as the canonical post-release verifier. It checks
  APK, manifest, and SBOM SHA-256 files by release asset basename; verifies the signed update
  manifest with the configured RSA public key; compares manifest `apkSha256`, version, channel,
  store, APK URL, and signing-certificate digest; validates manifest expiry and allowed APK URL
  boundaries; checks basic CycloneDX SBOM shape; and optionally runs `gh attestation verify` for
  the APK, manifest, and SBOM.
- Added `scripts/verify-release-artifacts.ps1` and `scripts/verify-release-artifacts.sh` as thin
  wrappers around the same Java verifier.
- Updated `RELEASING.md` so post-publication verification is one repeatable artifact-set command
  on PowerShell or Bash instead of separate checksum and attestation snippets.
- Extended `SecurityHardeningTest` with release-verifier static guards and
  `atk34_releaseArtifactVerifierChecksManifestAndChecksums`, which generates a throwaway RSA
  keypair, signs a fixture manifest, verifies the full fixture artifact set, then tampers with the
  APK and proves the verifier fails on checksum drift.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`;
  `.\scripts\verify-release-artifacts.ps1 --help`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: live release proof still needs real repository signing/update secrets
  and a tagged workflow run; real-device release APK smoke is still pending; broader manual UX
  review beyond the key automated matrix remains open; and MIT relicensing still needs
  legal/provenance clearance.

## Plan - 2026-06-18 AI Surface Final Cleanup
- [x] Confirm latest release-verifier commit is green in CodeQL and Android CI.
- [x] Inspect remaining AI mentions in production code, default docs, tests, and stale task
  headers.
- [x] Remove stale AI-only network-security trust overrides from the packaged app.
- [x] Tighten AI-removal contract tests so AI provider network config and Discover AI chips cannot
  silently return.
- [x] Replace the stale top-level AI implementation checklist with a superseded decision note.
- [x] Run focused unit tests, debug packaging, license-boundary, and hygiene gates.

## Review - 2026-06-18 AI Surface Final Cleanup
- CodeQL run `27730425680` and Android CI run `27730425672` passed on `4053e5a3`.
- Removed the default app's AI-only `network_security_config.xml` and the manifest
  `android:networkSecurityConfig` hook. The default product no longer carries explicit trust
  overrides for old AI provider endpoints such as OpenAI, Anthropic, or Gemini.
- Updated `AdAwayApplication` comments so VPN runtime-cache invalidation references Domain
  Checker actions only.
- Strengthened `AiSurfaceContractTest` to fail if a dormant AI feature gate, AI network-security
  config, AI packages/layouts/settings, or default README AI copy returns.
- Fixed stale Discover quick-action test wording: the test now asserts no `chipDiscoverAskAi`
  is present instead of accidentally passing because the removed chip sorted before preset chips
  as index `-1`.
- Marked the old v13.4.5 AI implementation checklist as superseded by the AI feature cut and
  retained the Domain Checker action path as separate non-AI work.
- Verification passed:
  `.\gradlew.bat --no-daemon --rerun-tasks :app:testDebugUnitTest --tests
  org.adaway.ui.home.AiSurfaceContractTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:assembleDebug --dependency-verification=strict --stacktrace`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Expert sidecars identified two next unblocked slices: make `run-release-smoke.ps1` choose
  Android build tools by parsed version and behavior-test `-VerifyOnly` with a fake SDK; then make
  More > Backup & Restore deep-link directly to the backup/restore preference fragment instead of
  opening generic settings.
- Remaining full-goal gaps: live release proof with real secrets, physical-device release smoke,
  broader UX/manual screenshot review, and MIT legal/provenance clearance remain open.

## Plan - 2026-06-18 Release Smoke Build-Tools Selection
- [x] Consume release-readiness reviewer findings after the post-release verifier slice.
- [x] Fix `run-release-smoke.ps1` build-tool discovery so stale `9.0.0` directories cannot outrank
  current `36.0.0` build tools.
- [x] Add executable JVM coverage that runs `run-release-smoke.ps1 -VerifyOnly` against a fake
  Android SDK with competing build-tools versions.
- [x] Run focused security verification for the release-smoke script behavior.
- [x] Run license-boundary and hygiene gates before commit.

## Review - 2026-06-18 Release Smoke Build-Tools Selection
- Release-readiness sidecar found that `run-release-smoke.ps1` sorted Android build-tools
  directories lexicographically, so a stale `9.0.0` directory could be selected before `36.0.0`.
- Changed `Find-BuildTool` to sort by a parsed `System.Version` from the directory name, with the
  raw name only as a tie breaker.
- Added `SecurityHardeningTest.atk34_releaseSmokeVerifyOnlyUsesHighestParsedBuildToolsVersion`.
  The test creates a fake SDK with `build-tools/9.0.0/aapt` returning a wrong package and
  `build-tools/36.0.0/aapt` returning `org.adaway`, then runs the PowerShell smoke script in
  `-VerifyOnly` mode. It proves the script chooses the higher parsed build-tools version and exits
  before any `adb`/device work.
- Verification passed:
  `.\gradlew.bat --no-daemon --rerun-tasks :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`.
- Remaining release sidecar findings: post-release attestation verification currently covers APK,
  manifest, and SBOM but not their checksum sidecars; README release summary still lags the
  `directRelease` workflow and signed-tag secret table.

## Plan - 2026-06-18 Domain Checker CI State Isolation
- [x] Pull the failed connected Android CI log for `2e4eba16`.
- [x] Identify the exact full-suite failure and reproduce the focused test locally.
- [x] Patch the test fixture so direct runtime-truth rows are isolated from prior connected-test
  state.
- [x] Run the exact focused connected test.
- [x] Run the full connected Android test suite locally before pushing follow-up commits.

## Review - 2026-06-18 Domain Checker CI State Isolation
- Android CI run `27731153541` passed the development build but failed connected Android tests in
  `DomainCheckerRuntimeTruthTest.domainCheckerUsesRootExactTruthAndVpnSuffixTruth`.
- CI failure evidence: `Root hosts-file mode must report suffix rules for their materialized base
  host` at `DomainCheckerRuntimeTruthTest.java:80`.
- The focused connected test passed locally before the patch, which pointed at full-suite state
  leakage rather than the AI network cleanup itself.
- Hardened the test fixture by using a less collision-prone `.invalid` host, deleting leftover
  base/child host rows across sources during cleanup, refreshing the fixture source's active-rule
  stats after direct row insertion, invalidating materialized runtime caches during cleanup, and
  asserting that root-mode base suffix truth is materialized before exercising the UI-facing
  Domain Checker path.
- Focused connected verification passed:
  `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest#domainCheckerUsesRootExactTruthAndVpnSuffixTruth"
  --dependency-verification=strict --stacktrace`.
- Full connected verification passed:
  `.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  --dependency-verification=strict --stacktrace`; Gradle reported 110 tests on
  `adaway-api34-16g(AVD) - 14`, 0 failures, 3 skipped.

## Plan - 2026-06-18 More Backup Deep Link
- [x] Confirm latest branch and CI baseline are clean and green.
- [x] Inspect More and preferences navigation.
- [x] Add a Backup & Restore initial-fragment intent for `PrefsActivity`.
- [x] Route More > Backup & Restore through that deep link while keeping Preferences on main
  settings.
- [x] Add a JVM contract test for the navigation contract.
- [x] Run focused unit/compile, assemble/lint, license-boundary, and hygiene gates.

## Review - 2026-06-18 More Backup Deep Link
- CodeQL run `27731809590` and Android CI run `27731809584` passed on `4c947eda`
  before this slice started.
- `More > Backup & Restore` no longer opens generic settings. It now starts
  `PrefsActivity.createBackupRestoreIntent(...)`.
- `PrefsActivity` now accepts `EXTRA_INITIAL_FRAGMENT=backup_restore` and creates
  `PrefsBackupRestoreFragment` as the initial fragment for that entrypoint, while preserving
  `PrefsMainFragment` for normal preferences and keeping the nested Backup & Restore preference
  in `preferences_main.xml`.
- Added `HomeNavigationSourcesContractTest.moreBackupRestoreDeepLinksToBackupPreferences` so the
  More row, normal Preferences row, deep-link constants, initial-fragment selection, and nested
  settings entry are guarded together.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest :app:compileDebugJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest --rerun-tasks
  --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:assembleDebug :app:lintDebug
  --dependency-verification=strict --stacktrace`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: live release proof with real secrets, physical-device release smoke,
  broader manual UX review, MIT legal/provenance clearance, plus sidecar leftovers around
  add-source scheduling clarity, inset cleanup, README release docs, and checksum-sidecar
  attestation coverage.

## Plan - 2026-06-18 Release Attestation Sidecars
- [x] Confirm the latest branch and CI baseline are clean and green.
- [x] Ask sidecar reviewers to inspect the release verifier/workflow contract and release docs.
- [x] Make `--verify-attestations` verify APK, manifest, SBOM, and all three `.sha256`
  checksum sidecars.
- [x] Tighten verifier/workflow tests so the provenance block must include all six uploaded
  release assets.
- [x] Update README and `RELEASING.md` for the signed `directRelease` flow, signed-tag key,
  post-release verifier, physical-device smoke, and fork-vs-store release boundary.
- [x] Run focused security tests, debug assemble/lint, verifier wrapper help, license-boundary,
  and hygiene gates.

## Review - 2026-06-18 Release Attestation Sidecars
- CodeQL run `27732492691` and Android CI run `27732492678` passed on `c08ebaaf`
  before this slice started.
- `scripts/VerifyReleaseArtifacts.java` now verifies GitHub attestations for all six published
  assets when `--verify-attestations` is set: APK, APK checksum, signed manifest, manifest
  checksum, SBOM, and SBOM checksum.
- Added `GH_CLI_PATH` support so the verifier keeps using `gh` by default while tests can inject
  a deterministic fake GitHub CLI.
- Extended `SecurityHardeningTest.atk34_releaseArtifactVerifierChecksManifestAndChecksums` with a
  fake `gh` executable and asserted six attestation calls including all checksum sidecars.
- Tightened `atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom` to inspect the actual
  `Attest release artifacts` provenance block instead of accepting checksum tokens elsewhere in
  the workflow.
- Updated README release guidance from generic `assembleRelease`/unsigned tags to signed
  `assembleDirectRelease`, `git verify-tag`, `RELEASE_TAG_PUBLIC_KEY_BASE64`, checksum-sidecar
  attestations, and verifier/physical-device smoke expectations.
- Updated `RELEASING.md` to state fork tags publish the GitHub direct APK only; F-Droid/store
  releases remain separate store/build-pipeline work using the normal release variant.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:assembleDebug :app:lintDebug
  --dependency-verification=strict --stacktrace`;
  `.\scripts\verify-release-artifacts.ps1 --help`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: live release proof with real repository signing/update secrets,
  physical-device release APK smoke, broader manual UX review, MIT legal/provenance clearance,
  add-source scheduling clarity, and inset cleanup.

## Plan - 2026-06-18 Add Source Simplicity
- [x] Confirm latest branch and CI baseline are clean and green.
- [x] Inspect Add Source sheet, source editor, schedules entrypoints, and existing UX contract tests.
- [x] Ask a sidecar reviewer to challenge the Add Source simplification scope.
- [x] Remove schedule management from the Add Source sheet while keeping scheduling reachable from
  the Sources toolbar and Manage schedules screen.
- [x] Hide advanced format and redirected-host controls while creating a new source, while
  preserving URL/File choice and keeping full controls for existing-source edit.
- [x] Add contract tests for the simplified creation surface, editing-only scheduling action, and
  localized source schedule weekday picker.
- [x] Run focused unit/compile, assemble/lint, license-boundary, and hygiene gates.

## Review - 2026-06-18 Add Source Simplicity
- CodeQL run `27733178015` and Android CI run `27733178030` passed on `1cf01867`
  before this slice started.
- `hosts_add_options_sheet.xml` now contains only creation choices: Browse catalog and Add custom
  source. The previous `Manage schedules` row is removed from the add sheet.
- `HostsSourcesFragment.showAddSourceOptions()` no longer routes from the add sheet to
  `SchedulesActivity`; schedule management remains available through the Sources toolbar action.
- `SourceEditActivity` hides `auto_update_action` unless editing an existing source and guards the
  handler for non-editing mode.
- Add-source mode now collapses advanced `List format`, Block/Allow, redirected-host checkbox, and
  redirected-host warning controls. The URL/File type choice remains visible so file-source
  creation still works; existing-source edit keeps the advanced controls.
- `SourceEditActivity` now uses locale-aware weekday labels for source-level weekly schedules,
  matching the existing schedule UI approach.
- Extended `DiscoverPresetSubscriptionTest` with
  `addSourceFlowKeepsSchedulingOutOfCreationSurface` and strengthened
  `scheduleUiUsesLocalizedStringsAndPlurals` to cover the source editor.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest :app:compileDebugJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:assembleDebug :app:lintDebug
  --dependency-verification=strict --stacktrace`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: live release proof with real repository signing/update secrets,
  physical-device release APK smoke, broader manual UX review, MIT legal/provenance clearance,
  and inset cleanup.

## Plan - 2026-06-18 First-Class Sources UX Matrix
- [x] Confirm current branch, CI baseline, and remaining local UX gaps.
- [x] Update the UX matrix so it screenshots the embedded bottom-nav Sources tab, not only the
  legacy standalone sources activity.
- [x] Add Custom Rules coverage, FAB/list clearance, and RTL-safe Custom Rules anchoring.
- [x] Fix the global schedule first-launch semantics so passive UI tests and real users do not
  inherit an immediate full-source update.
- [x] Add static contract guards for first-class Sources, Custom Rules, WorkManager isolation,
  and schedule startup behavior.
- [x] Run focused unit and androidTest compile gates, then the UX matrix when a device is
  available.
- [x] Record verification evidence and remaining external release/MIT blockers.

## Review - 2026-06-18 First-Class Sources UX Matrix
- CodeQL run `27733860440` and Android CI run `27733860414` passed on `af5c410f`
  before this slice started.
- `UxDeviceMatrixTest` now captures `Sources` through the Home bottom-nav shell and captures
  reachable `Custom Rules` through `ListsActivity`; the legacy standalone Sources activity is no
  longer the primary Sources screenshot.
- Custom Rules now has bottom/end list padding, list clipping disabled, an accessibility label,
  and RTL-safe start/end constraints plus bottom/end FAB anchoring.
- The UX matrix now resets WorkManager around passive screenshots and the runner dismisses
  emulator launcher/system dialogs before instrumentation and after locale/font-scale changes.
- Root cause for the previous final-variant timeout was a real first-launch background update:
  global schedule defaults left `lastRun = 0`, and `FilterSetUpdateWorker` treated that as due
  now. Defaults and user schedule changes now seed `lastRun`; the worker repairs old zero
  `lastRun` globals without updating all sources immediately.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest --tests org.adaway.scripts.UxMatrixScriptTest
  :app:compileDebugAndroidTestJavaWithJavac :app:compileDebugJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetStoreMigrationTest"
  --dependency-verification=strict --stacktrace`; and
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ux-matrix.ps1
  -OutputDir app\build\reports\ux-matrix-first-class-sources`, which passed all five variants
  and pulled 8 screenshots per variant.
- Additional gates passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug
  --dependency-verification=strict --stacktrace`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Spot-checked generated `font-1.6-rtl` Sources and Custom Rules screenshots after the green
  matrix. Sources is usable through bottom navigation, and Custom Rules keeps the empty state,
  search field, FAB, and bottom navigation clear at large font.
- Remaining full-goal gaps: live release proof with real repository signing/update secrets,
  physical-device release APK smoke, broader manual UX review, MIT legal/provenance clearance,
  and larger scale/performance proof.

## Plan - 2026-06-18 Connected UX CI Recovery
- [x] Diagnose the pushed Android CI failure on `d88bde6b` from downloaded connected-test
  artifacts instead of guessing from the timeout.
- [x] Simplify the embedded Sources row layout so large-font/RTL screenshots keep source labels,
  status, host-count, and update action readable.
- [x] Fix the Home nav shell so tab content is constrained above the bottom navigation and the
  Sources toolbar title is visible in the embedded tab.
- [x] Isolate passive UI instrumentation tests from app/hosts/global schedule background update
  workers.
- [x] Move adware package scanning off the single DB executor so Domain Checker runtime-truth
  checks are not starved by device-wide package scans.
- [x] Re-run focused unit/compile, UX matrix, full connected Android tests, broad unit/lint,
  license-boundary, and diff hygiene gates.

## Review - 2026-06-18 Connected UX CI Recovery
- Android CI run `27736512922` failed in the connected Android tests job, not in build or CodeQL.
  The step timed out after reaching `UxDeviceMatrixTest` with 89/109 tests complete, 3 skipped,
  and 0 assertion failures. Local artifact inspection showed the test launched passive UI screens
  while background update work could still perform live source downloads.
- Source rows now use a flatter 8dp card treatment, a stable text column, and a below-row update
  action. The `font-1.6-rtl` matrix screenshot now shows readable Sources rows without ellipsized
  primary labels.
- `activity_home_nav.xml` now uses a constrained home nav shell, keeping tab content above the
  bottom navigation instead of relying on inset dodging. The embedded Sources toolbar uses the app
  title attributes, making the `Sources` title visible in screenshots.
- Added `InstrumentedTestState` for passive UI tests. `UxDeviceMatrixTest` and
  `HomeNavigationSourcesInstrumentedTest` now explicitly disable app update checks, host update
  checks, automatic host updates, and global filter-set schedules before launch, then cancel and
  prune WorkManager.
- Full connected tests then exposed a second real flake: `DomainCheckerRuntimeTruthTest` timed out
  while `AdwareLiveData` scanned installed packages on the single `diskIO` executor. `AppExecutors`
  now exposes `packageScanIO()`, and `AdwareLiveData` uses it so package scans cannot block
  user-facing DB lookups.
- Verification passed:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ux-matrix.ps1
  -OutputDir app\build\reports\ux-matrix-passive-ui-isolated`, which passed all five variants
  and pulled 8 screenshots per variant;
  `.\gradlew.bat "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest"
  --no-daemon :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace`;
  `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --dependency-verification=strict
  --stacktrace`, which finished 112 tests with 3 skipped and 0 failed;
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug
  --dependency-verification=strict --stacktrace`;
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1
  -SourceMode WorkingTree`; and `git diff --check` with only existing CRLF conversion warnings.
- Remaining full-goal gaps: live release proof with real repository signing/update secrets,
  physical-device release APK smoke, broader manual UX review beyond the screenshot matrix,
  MIT legal/provenance clearance, and larger 100k/1M/5M scale-performance proof.

## Plan - 2026-06-19 Stage-Backed 5M Root Export
- [x] Reproduce the remaining 5M allow-heavy root-export failure and keep the long run alive.
- [x] Replace the final-table copy bottleneck for safe large staged exports with a stage-backed
  root export plus persisted skip ids.
- [x] Keep the old materialized-table path for redirects, wildcard exact allow rules, and smaller
  staged exports that still need duplicate cleanup semantics.
- [x] Add v31 schema/migration coverage for stage-backed root export state.
- [x] Prove compile, focused connected correctness/migration tests, 1M gate, and 5M gate.

## Review - 2026-06-19 Stage-Backed 5M Root Export
- The prior 5M gate was red after the first staged-copy optimization:
  `syncMs=479902`, `rootWriteMs=53309`; root export still spent `439290ms`, mostly copying and
  deleting final `root_host_entries` rows.
- Added a schema-backed `root_export_stage_materialized` state and
  `root_export_skip_stage_ids` table. For complete large stages with no redirects and no wildcard
  exact allow rules, sync now builds the skip-id table and streams root output from
  `root_host_entries_stage` instead of duplicating millions of rows into `root_host_entries`.
- Kept the existing final-table materialization path for redirect/wildcard/small-stage cases.
  Focused connected tests for staged redirects, small duplicate cleanup, and migrations stayed
  green.
- Fixed the first stage-backed root writer attempt after 1M showed a bad query plan:
  `syncMs=17258` passed, but root write was `389441ms`. Forcing stage chunks to scan by row id
  brought the rerun to `syncMs=15185` and `rootWriteMs=4003`.
- Final 5M proof passed:
  `HostEntryDao.sync ... rootExportMs=118852 totalMs=150000`;
  `HostEntryAllowHeavyRebuildBenchmark ... rootRows=4900000 syncMs=150085
  rootWriteMs=185667 rootWriteBytes=265075324`.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused connected tests for
  `SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage`,
  `SourceDbTest#testCompleteRootExportStageDedupesBlockedDuplicatesWithoutRedirects`,
  `MigrationTest#migration29To30_dropsPersistentRootExportIndexes`, and
  `MigrationTest#migration30To31_addsStageBackedRootExportState`;
  1M allow-heavy gate with `syncMs=15185`, `rootWriteMs=4003`;
  and 5M allow-heavy gate with `syncMs=150085`, `rootWriteMs=185667`.
- `git diff --check` passed with only existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: this proves the 5M root export/update gate for the staged no-redirect
  path. Broader release proof, physical-device smoke, UX/manual review, MIT legal/provenance
  clearance, and any redirect-heavy 5M stress case are still separate phases.

## Plan - 2026-06-19 Stage-Backed Redirect Export
- [x] Add a red connected test that forces the large complete-stage branch with redirects and
  proves redirects should not force final-table materialization.
- [x] Make stage-backed root export handle redirects by persisting skip ids for redirect shadows
  and non-winning redirect rows.
- [x] Avoid O(n) scratch-table cleanup for unique redirect-heavy stages by detecting conflict
  hosts through the existing `reverse_host` index before building winner tables.
- [x] Extend the allow-heavy benchmark with redirect-row and metadata-only runtime seeding
  arguments so staged redirect sync/root-write can be measured directly.
- [x] Re-run focused redirect/stage DB regressions, unit tests, compile, and a 1M redirect-heavy
  staged benchmark.
- [x] Close the 5M redirect-heavy gate after making the benchmark seeder fast enough to reach
  sync timing within the command budget.

## Review - 2026-06-19 Stage-Backed Redirect Export
- Initial red test:
  `SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage` failed with
  `expected:<0> but was:<500005>` because redirects still forced rows into
  `root_host_entries`.
- `HostEntryDao` now permits stage-backed export with redirects when the complete stage is over
  the materialized-cache threshold and there are no wildcard exact allow rules. The stage cursor
  remains authoritative; `root_host_entries` stays empty for that path.
- Redirect correctness is handled through `root_export_skip_stage_ids`: blocked rows shadowed by
  winning redirects are skipped, non-winning redirects are skipped, and winners continue to stream
  from `root_host_entries_stage`.
- A first 1M redirect-heavy run exposed a real SQL shape problem:
  `syncMs=363355`, with `loserMs=152041` and `cleanupMs=155599`. The fix now first builds only
  `root_export_redirect_stage_conflict_hosts` via the existing `reverse_host` index and only
  creates candidate/winner scratch tables when conflicts exist.
- Green 1M redirect-heavy proof:
  `redirectRows=1000000`, `seedRuntimeRows=false`, `seedRootStage=true`,
  `syncMs=16222`, `rootWriteMs=10843`, `rootRows=1000001`, and
  `rootWriteBytes=45889263`.
- The first 5M redirect-heavy attempt after that still timed out because stage seeding alone hit
  `HostEntryAllowHeavySeedPhase phase=root-stage-total rows=5000001 ms=989427`. The benchmark
  harness now bulk-loads the stage table without maintaining stage indexes row-by-row, then
  recreates the same indexes before sync/root-write timing starts.
- A second 5M attempt showed the remaining product bottleneck: the reverse-host conflict scan was
  not covering, so SQLite had to read stage table rows for `type`, `source_id`, and `generation`.
  v32 replaces `index_root_host_entries_stage_reverse_host` with a covering
  `(reverse_host, type, source_id, generation)` index.
- Final 5M redirect-heavy proof passed:
  `HostEntryDao.root-export-stage redirect-skip conflicts=0 conflictMs=9524 totalMs=9548`;
  `HostEntryDao.sync ... rootExportMs=9731 totalMs=11322`;
  `HostEntryAllowHeavyRebuildBenchmark ... redirectRows=5000000 rootRows=5000001
  syncMs=11408 rootWriteMs=42908 rootWriteBytes=233889263`.
- Verification passed:
  focused connected tests for
  `SourceDbTest#testLargeRuntimeSkipStillMaterializesRootExportRows`,
  `SourceDbTest#testLargeRuntimeSkipRedirectsUseSourcePriorityBeforeRedirectionValue`,
  `SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage`, and
  `SourceDbTest#testCompleteRootExportStageDedupesBlockedDuplicatesWithoutRedirects`;
  `MigrationTest#migration31To32_makesRootStageReverseIndexCovering`;
  `:app:testDebugUnitTest`;
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`; and the 5M
  redirect-heavy staged benchmark above.
- Remaining full-goal gaps: live release proof with real signing/update secrets,
  physical-device release APK smoke, broader manual UX review, MIT legal/provenance clearance,
  and any release SBOM/provenance gates not yet run in this branch.

## Plan - 2026-06-19 Release Artifact Bundle Verification
- [x] Audit the current tagged release workflow, SBOM generation, signed manifest generator,
  artifact verifier, license-boundary guard, and release smoke script.
- [x] Add a red unit test requiring the release workflow to run the canonical artifact verifier
  before attestation and upload.
- [x] Wire `scripts/verify-release-artifacts.sh` into the tagged release workflow with APK,
  checksum sidecars, signed update manifest, SBOM, expected version, channel, store, APK URL,
  and signer certificate digest.
- [x] Re-run focused security tests plus local compile, license boundary, and diff hygiene.

## Review - 2026-06-19 Release Artifact Bundle Verification
- Existing release automation already failed closed on missing signing trust material, generated a
  CycloneDX SBOM, checksummed APK/SBOM/manifest artifacts, signed update manifests, checked
  license boundaries, and created GitHub provenance/SBOM attestations.
- The missing local release-proof link was end-to-end bundle verification before publishing:
  the workflow generated separate artifacts but did not run the repository's
  `VerifyReleaseArtifacts.java` contract over the final bundle before attestation/upload.
- Added `Verify release artifact bundle` to `.github/workflows/fork-release-apk.yml` after the
  strict artifact license-boundary check and before attestation. It verifies APK/SBOM/manifest
  checksum sidecars, manifest signature, expected version/channel/store, expected APK URL, and
  expected signing certificate digest.
- Verification passed:
  red/green targeted test
  `SecurityHardeningTest#atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom`;
  full `SecurityHardeningTest`;
  `:app:compileDebugJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check`
  with only existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: live release proof with real signing/update secrets,
  physical-device release APK smoke, broader manual UX review, MIT legal/provenance clearance,
  and any post-publish attestation verification that requires a real GitHub release.

## Plan - 2026-06-19 Release Attestation Verification
- [x] Add a red security test requiring the tagged release workflow to verify GitHub attestations
  after both attestation actions and before GitHub Release upload.
- [x] Add a red verifier regression for transient `gh attestation verify` lookup misses.
- [x] Wire a post-attestation workflow verifier step with explicit repository scope and
  `--verify-attestations`.
- [x] Add bounded attestation lookup retry to the canonical Java verifier.
- [x] Re-run focused security tests, compile, license boundary, and diff hygiene.
- [x] Commit the focused release-provenance slice.

## Review - 2026-06-19 Release Attestation Verification
- The prior workflow ran the canonical artifact verifier only before attestations existed. A
  release could therefore create attestations and upload a GitHub Release without proving that
  GitHub could verify the published provenance records for the APK, manifest, SBOM, and checksum
  sidecars.
- Added a post-attestation workflow step, `Verify release artifact attestations`, between the two
  attestation actions and GitHub Release upload. It reuses `scripts/verify-release-artifacts.sh`
  with the same APK/manifest/SBOM/checksum/version/channel/store/certificate checks plus explicit
  `--repo "${GITHUB_REPOSITORY}"` and `--verify-attestations`.
- Hardened `VerifyReleaseArtifacts.java` with a bounded retry for `gh attestation verify` so the
  release workflow tolerates transient attestation lookup propagation without weakening final
  failure behavior.
- Verification passed:
  red/green focused tests
  `SecurityHardeningTest#atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom` and
  `SecurityHardeningTest#atk34_releaseArtifactVerifierChecksManifestAndChecksums`;
  full `SecurityHardeningTest`;
  `:app:compileDebugJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: live tagged release proof with real repository signing/update secrets,
  physical-device release APK smoke, broader manual UX review, MIT legal/provenance clearance, and
  post-publish verification against a real created GitHub Release.

## Plan - 2026-06-19 Manual Post-Publish Release Verification
- [x] Add a red security test requiring a manual GitHub Actions workflow for post-publish artifact
  and attestation verification.
- [x] Add `.github/workflows/verify-release-artifacts.yml` to download a tagged release's APK,
  manifest, SBOM, and checksum sidecars, then run the canonical verifier with
  `--verify-attestations`.
- [x] Document the workflow in README/RELEASING so release operators have a CI-backed
  post-publish proof path.
- [x] Re-run focused security tests, workflow pinning coverage, compile, license boundary, and diff
  hygiene.
- [x] Commit the focused post-publish verification slice.

## Review - 2026-06-19 Manual Post-Publish Release Verification
- The previous release path had local/manual verifier instructions and in-workflow pre-upload
  checks, but no CI entry point to verify a created GitHub Release by downloading the published
  assets back from GitHub.
- Added manual workflow `.github/workflows/verify-release-artifacts.yml`. It accepts a release tag
  and expected release APK signing certificate SHA-256 digest, downloads the APK, signed update
  manifest, SBOM, and three checksum sidecars from the GitHub Release, derives stable/beta channel
  from the tag, and runs `scripts/verify-release-artifacts.sh` with `--verify-attestations` against
  `${GITHUB_REPOSITORY}`.
- The new workflow uses read-only `contents`/`attestations` permissions and pinned checkout/JDK
  setup actions, preserving the repo's immutable-action workflow rule.
- Verification passed:
  red/green focused test
  `SecurityHardeningTest#atk34_releaseCleanupAndDocsPreserveSourceProvenance`;
  full `SecurityHardeningTest`;
  `:app:compileDebugJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings. `actionlint` is not installed in this environment.
- Remaining full-goal gaps: the manual workflow still needs to be run against an actual tagged
  GitHub Release with real signing/update secrets, physical-device release APK smoke is still
  external, broader manual UX review remains open, and MIT legal/provenance clearance remains
  unavailable until GPL-derived material is cleared or permissioned.

## Plan - 2026-06-19 Runtime Bird Logo UX Guard
- [x] Re-audit current logo resources and existing static branding guards after the earlier logo
  confusion.
- [x] Add a red contract requiring the UX matrix to assert that the Home AdAway bird logo is
  visible before screenshot capture.
- [x] Add the runtime Home logo assertion to `UxDeviceMatrixTest`.
- [x] Fix the UX matrix WorkManager reset timeout exposed by the full device run.
- [x] Re-run focused unit/compile, full UX matrix, license boundary, and diff hygiene.
- [x] Commit the focused UX/logo proof slice.

## Review - 2026-06-19 Runtime Bird Logo UX Guard
- Current packaged branding is the AdAway bird: `logo.xml`, `icon_foreground_red.xml`,
  adaptive launcher icons, and density PNG fallbacks all point to or render the bird. The existing
  `HomeNavigationSourcesContractTest` already statically guards the bird vector paths, launcher
  foregrounds, PNG fallbacks, Home header use, and license inventory.
- Added missing runtime UX coverage: `UxDeviceMatrixTest` now asserts `logoImageView` is visible
  and renders at a meaningful size before capturing the Home screenshot. This means the screenshot
  matrix fails if the bird resource is present but hidden, collapsed, or not rendered in the actual
  Home shell.
- The first full UX matrix rerun exposed a verifier flake: baseline passed, then `font-1.3`
  failed in setup because `InstrumentedTestState.resetWorkManager()` used 5-second
  `cancelAllWork()`/`pruneWork()` waits. The reset helper now uses a named 30-second timeout, and
  the contract test guards that budget so repeated emulator variants do not fail on cleanup jitter.
- Verification passed:
  red/green unit contracts
  `HomeNavigationSourcesContractTest#uxMatrixAssertsHomeBirdLogoIsVisible` and
  `HomeNavigationSourcesContractTest#uxMatrixDoesNotInheritBackgroundWorkers`;
  `HomeNavigationSourcesContractTest`;
  `:app:compileDebugJavaWithJavac`;
  `:app:compileDebugAndroidTestJavaWithJavac`;
  full UX matrix via
  `scripts/run-ux-matrix.ps1 -OutputDir app/build/reports/ux-matrix-bird-logo-guard
  -InstrumentationTimeoutSeconds 360`, which passed baseline, font-1.3, font-1.6,
  font-1.3-rtl, and font-1.6-rtl and pulled 40 screenshots;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: physical-device release APK smoke and a real signed/tagged GitHub
  release run remain external; MIT legal/provenance clearance remains blocked on rights and
  GPL-derived material; broader manual UX review is improved by the matrix proof but not a formal
  design sign-off.

## Plan - 2026-06-19 Physical Release Smoke Workflow
- [x] Add a red security test requiring a manual physical-device release smoke workflow.
- [x] Add `.github/workflows/physical-release-smoke.yml` to download a tagged release APK and run
  the full `scripts/run-release-smoke.ps1` path on a self-hosted physical Android device runner.
- [x] Document the workflow and runner requirements in README/RELEASING.
- [x] Re-run focused security tests, full release/security tests, compile, license boundary, and
  diff hygiene.
- [x] Commit the focused physical release smoke workflow slice.

## Review - 2026-06-19 Physical Release Smoke Workflow
- The release smoke script already rejected debuggable APKs and emulators, installed the release
  APK, launched `org.adaway`, and verified that the process stayed alive, but that proof path was
  only documented as a local/manual command.
- Added a manual physical-device workflow entry point that requires a self-hosted runner labeled
  `android-device`, downloads `AdAway_<version>.apk` from the requested release tag, and invokes
  the full smoke script without `-VerifyOnly`. The workflow also checks for `gh` and `pwsh`
  before download/smoke execution so self-hosted runner setup failures surface immediately.
- Remaining full-goal gaps will still include actually running this workflow against a published
  release on a real physical device with production signing material; this patch makes that gate
  executable and reviewable, not already green in the external environment.
- Verification passed:
  red/green focused test
  `SecurityHardeningTest#atk34_releaseSmokeRequiresReleaseApkOnRealDevice`;
  full `SecurityHardeningTest`;
  `:app:compileDebugJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings. `actionlint` is not installed in this environment.

## Plan - 2026-06-19 Home Shared Operation State
- [x] Add a red Home contract requiring update progress to consume shared
  `FilterOperationState` instead of rendering from legacy `MultiPhaseProgress`.
- [x] Expose `FilterOperationState` from `HomeViewModel`.
- [x] Render the Home update progress container from `FilterOperationState` and keep terminal
  counter refresh/controls/accessibility behavior intact.
- [x] Re-run focused Home contracts, unit/compile gates, license boundary, and diff hygiene.
- [x] Commit the focused Home progress state slice.

## Review - 2026-06-19 Home Shared Operation State
- Current code still exposed `SourceModel.Progress` and `SourceModel.MultiPhaseProgress` through
  `HomeViewModel`, and `HomeFragment` observed both. That contradicted the plan's single
  `FilterOperationState` mental model for Home/Discover/Sources/workers.
- `HomeViewModel` now exposes `LiveData<FilterOperationState>` from `SourceModel`.
- `HomeFragment` now binds pause/resume, stop, summary progress text, the bird progress marker,
  scheduler task label, terminal accessibility announcement, and counter attach/detach behavior
  from `FilterOperationState`. Stopped and complete terminal states both reattach counters and
  refresh the visible counts.
- Verification passed:
  red/green focused test
  `HomeNavigationSourcesContractTest#homeUpdateProgressUsesSharedFilterOperationState`;
  full `HomeNavigationSourcesContractTest`;
  full `:app:testDebugUnitTest`;
  `:app:compileDebugAndroidTestJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.

## Plan - 2026-06-19 Notification Routing Cleanup
- [x] Add a red notification contract for separate hosts/app update notification IDs and correct
  VPN channel metadata.
- [x] Fix `NotificationHelper` so app-update notifications use `UPDATE_APP_NOTIFICATION_ID`.
- [x] Fix `NotificationHelper` so the VPN channel receives the VPN description.
- [x] Re-run focused notification contract, unit/compile gates, license boundary, and diff hygiene.
- [x] Commit the focused notification cleanup slice.

## Review - 2026-06-19 Notification Routing Cleanup
- Notification surface inspection found two concrete usability defects: app-update notifications
  reused the hosts-update notification ID, so one update notification could overwrite the other;
  and VPN channel creation assigned the VPN description to the update channel instead of the VPN
  channel.
- Added `NotificationHelperContractTest` to guard distinct hosts/app notification IDs and the
  correct VPN channel description target.
- `NotificationHelper` now routes hosts updates to `UPDATE_HOSTS_NOTIFICATION_ID`, app updates to
  `UPDATE_APP_NOTIFICATION_ID`, and assigns `notification_vpn_channel_description` to
  `vpnServiceChannel`.
- Verification passed:
  red/green focused `NotificationHelperContractTest`;
  full `:app:testDebugUnitTest`;
  `:app:compileDebugAndroidTestJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.

## Plan - 2026-06-19 VPN Idle Watchdog Stability
- [x] Add a red unit contract for VPN poll-timeout classification.
- [x] Treat an `Os.poll()` timeout with no pending DNS query and no queued device write as benign
  idle time instead of feeding the watchdog reconnect path.
- [x] Preserve watchdog handling when a timeout happens with pending DNS query work or pending
  device writes.
- [x] Re-run focused unit, full debug unit, Android-test compile, license-boundary, and diff
  hygiene gates.
- [x] Commit the focused VPN idle stability slice.

## Review - 2026-06-19 VPN Idle Watchdog Stability
- `VpnWorker.doOne(...)` already carried TODOs documenting the bug: a zero-event poll can be a
  valid idle result when no DNS query is outstanding and everything has already been written back to
  the device. The old code always called `vpnWatchDog.handleTimeout()`, which could turn an idle VPN
  into a reconnect loop.
- Added `VpnWorkerIdleTimeoutTest` to lock the intended split: idle timeouts are ignored, while
  pending DNS queries and pending device writes still belong to the watchdog path.
- `VpnWorker` now snapshots whether device writes were pending before polling and calls
  `vpnWatchDog.handleTimeout()` only when `isIdlePollTimeout(...)` is false.
- Verification passed:
  red focused `VpnWorkerIdleTimeoutTest` compile failure before the helper existed;
  green focused `VpnWorkerIdleTimeoutTest`;
  full `:app:testDebugUnitTest`;
  `:app:compileDebugAndroidTestJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining VPN follow-up: `DnsQueryQueue` only purges timed-out queries when adding a new query,
  so a deeper VPN runtime slice should audit stale upstream query cleanup and watchdog probe
  protection with connected or injectable behavior coverage before changing that path.

## Plan - 2026-06-19 DNS Query Queue Timeout Cleanup
- [x] Add a red JVM contract for dropping stale pending DNS queries before polling.
- [x] Add a red JVM contract for dropping stale pending DNS queries during idle response handling.
- [x] Add a small package-visible pending-query seam and injectable clock so queue timeout behavior
  is testable without Android socket descriptors.
- [x] Purge stale DNS queries before `getQueryFds()` and `handleResponses()`.
- [x] Re-run focused VPN/DNS tests, full debug unit tests, Android-test compile, license-boundary,
  and diff hygiene.
- [x] Commit the focused DNS queue timeout cleanup slice.

## Review - 2026-06-19 DNS Query Queue Timeout Cleanup
- `DnsQueryQueue` was documented as time and space bound, but timed-out queries were only purged
  when adding another upstream query. During idle VPN operation the worker repeatedly calls
  `getQueryFds()` and `handleResponses()`, so stale sockets could remain in the poll set until new
  DNS traffic arrived.
- Added `PendingDnsQuery` as a package-private abstraction and injected queue time into
  `DnsQueryQueue`. Production still creates real `DnsQuery` objects from `DatagramSocket`; the seam
  only lets unit tests exercise timeout behavior without Android `ParcelFileDescriptor` stubs.
- `getQueryFds()` now clears timed-out queries before exposing descriptors to `Os.poll()`, and
  `handleResponses()` does the same before scanning for answered queries.
- Verification passed:
  red focused `DnsQueryQueueTest` compile failure before the pending-query seam existed;
  green focused `DnsQueryQueueTest`;
  green focused `DnsQueryQueueTest` plus `VpnWorkerIdleTimeoutTest`;
  full `:app:testDebugUnitTest`;
  `:app:compileDebugAndroidTestJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining VPN follow-up: watchdog probe sockets still use a raw `DatagramSocket` in
  `VpnWatchdog`; a future connected/injectable test should confirm those probes are protected from
  VPN capture before changing that path.

## Plan - 2026-06-19 VPN Watchdog Probe Protection
- [x] Add a red watchdog contract requiring check-alive probe sockets to be protected before send.
- [x] Add a red watchdog contract that fails closed when socket protection fails.
- [x] Inject a small package-visible `SocketProtector` into `VpnWatchdog`.
- [x] Wire production `VpnWorker` to pass `VpnService.protect(...)` into the watchdog.
- [x] Re-run focused watchdog/VPN/DNS tests, full debug unit tests, Android-test compile,
  license-boundary, and diff hygiene.
- [x] Commit the focused watchdog probe protection slice.

## Review - 2026-06-19 VPN Watchdog Probe Protection
- Existing DNS forwarding sockets were protected with `vpnService.protect(...)`, but
  `VpnWatchdog.sendPacket()` opened a raw `DatagramSocket` for check-alive probes. On Android VPN
  this risks routing watchdog probes back through the VPN tunnel instead of the underlying network.
- Added `SocketProtector` and injected it into `VpnWatchdog`; the default constructor keeps the
  previous no-op behavior for package-local tests, while `VpnWorker` now passes
  `this.vpnService::protect`.
- `VpnWatchdog.sendPacket()` now protects the probe socket before `send(...)` and throws
  `VpnNetworkException` without sending if protection fails.
- Verification passed:
  red focused `VpnWatchdogTest` compile failure before `SocketProtector` existed;
  green focused `VpnWatchdogTest`;
  green focused `VpnWatchdogTest`, `VpnWorkerIdleTimeoutTest`, and `DnsQueryQueueTest`;
  full `:app:testDebugUnitTest`;
  `:app:compileDebugAndroidTestJavaWithJavac`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: physical-device release smoke, real tagged release verification with
  production secrets, broader manual UX sign-off, and MIT legal/provenance clearance remain
  external to this local patch.

## Plan - 2026-06-19 CI Migration 25-26 Index Repair
- [x] Push the accumulated local quality slices to PR #6 and inspect replacement CI.
- [x] Pull the failing connected Android test evidence.
- [x] Fix only the schema mismatch reported by CI.
- [x] Re-run focused and broad migration coverage plus unit/build and hygiene gates.
- [x] Commit and push the focused CI repair.

## Review - 2026-06-19 CI Migration 25-26 Index Repair
- Pushed 13 local commits to PR #6. The replacement PR head passed `Development build`,
  `Analyze (java)`, `Analyze (cpp)`, and aggregate `CodeQL`, then failed `Connected Android tests`.
- CI failure evidence from Android CI run `27809812609`, job `82297686929`:
  `MigrationTest#migration25To26_rebuildsRootExportForAppendWrites` failed because Room expected
  `root_host_entries` indexes `index_root_host_entries_host` and
  `index_root_host_entries_reverse_host`, but the migrated version-26 schema reported no indexes.
- Root cause: `MIGRATION_25_26` reused `optimizeRootHostEntriesStorage(database)`. That helper now
  matches the current version-32 storage shape and drops persistent root export indexes, but schema
  version 26 still requires those indexes until `MIGRATION_29_30` intentionally removes them.
- Fix: `MIGRATION_25_26` now recreates `ROOT_HOST_ENTRIES_HOST_INDEX_SQL` and
  `ROOT_HOST_ENTRIES_REVERSE_HOST_INDEX_SQL` after optimizing the table, preserving the historical
  version-26 schema while keeping later index-drop migrations intact.
- Verification passed:
  focused connected
  `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.db.MigrationTest#migration25To26_rebuildsRootExportForAppendWrites --dependency-verification=strict --stacktrace`;
  broad connected `MigrationTest` report with 21 tests, 0 failures, and 0 errors;
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.

## Plan - 2026-06-19 UX Matrix Sign-Off Packet
- [x] Turn the remaining broad manual UX review gap into a concrete review artifact.
- [x] Make `scripts/run-ux-matrix.ps1` validate the expected screenshot matrix after every run.
- [x] Emit a per-variant manual review checklist that points to every generated screenshot.
- [x] Add focused JVM coverage for manifest success and missing-screenshot failure behavior.
- [x] Run the real connected UX matrix and standard local gates.

## Review - 2026-06-19 UX Matrix Sign-Off Packet
- The automated UX matrix already covered baseline, large-font, and RTL variants, but the output
  was just screenshot folders. That left broader manual UX sign-off as an unstructured external
  step with no durable checklist or missing-screenshot proof.
- `scripts/run-ux-matrix.ps1` now has a single shared variant/screen matrix, loops through that
  matrix, and writes `ux-matrix-review.md` after successful instrumentation. The packet records
  baseline, 1.3, 1.6, 1.3 RTL, and 1.6 RTL variants; lists the eight expected screens for each;
  and includes manual review checks for text fit, touch targets, the AdAway bird, FAB/bottom-nav
  clearance, and RTL anchoring.
- The packet generation fails closed when an expected screenshot is missing, so the manual review
  cannot accidentally sign off an incomplete matrix.
- README now tells release reviewers to inspect the generated UX review packet.
- Verification passed:
  focused `:app:testDebugUnitTest --tests org.adaway.scripts.UxMatrixScriptTest --dependency-verification=strict --stacktrace`;
  direct `Write-UxMatrixReviewManifest` run against an existing real UX report;
  connected `scripts/run-ux-matrix.ps1 -OutputDir app/build/reports/ux-matrix-signoff-packet`
  with five variants, each reporting `OK (1 test)`, and 40 screenshots plus a review packet;
  full `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: this narrows manual UX review to a concrete packet, but human design
  sign-off is still not complete; physical-device release APK smoke, real tagged release
  verification with production secrets, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-19 Physical Release Smoke Report
- [x] Make the existing physical-device release smoke gate emit a durable report artifact.
- [x] Preserve the existing `-VerifyOnly` identity-check mode and default command behavior.
- [x] Upload the physical smoke report from the manual `physical-release-smoke.yml` workflow.
- [x] Add focused tests for workflow wiring, identity-only report output, and physical-mode report
  output without leaking the raw device serial.
- [x] Re-run focused release-smoke tests and standard local gates.

## Review - 2026-06-19 Physical Release Smoke Report
- The physical-device release smoke workflow already downloaded `AdAway_<version>.apk` and ran the
  full install/launch script on a self-hosted physical Android runner, but successful proof was only
  stdout. That made the remaining physical smoke gate harder to audit after a release run.
- `scripts/run-release-smoke.ps1` now accepts optional `-ReportPath`. Identity-only mode writes a
  report with APK identity status and explicitly records that physical install/launch was not run.
  Full physical mode writes a report with APK identity status, verified real-device status, a
  SHA-256 hash of the device serial instead of the raw serial, and the observed launch pid.
- `.github/workflows/physical-release-smoke.yml` passes `-ReportPath` and uploads
  `physical-release-smoke-report` using the already-pinned upload-artifact action.
- README and `RELEASING.md` now tell release operators to expect the smoke report artifact.
- Verification passed:
  manual fake-build-tools `run-release-smoke.ps1 -VerifyOnly -ReportPath ...` wrote the identity
  report and exited 0;
  manual fake-ADB physical smoke wrote the physical report, hashed `device-123`, recorded pid
  `4242`, and exited 0;
  focused `:app:testDebugUnitTest` with
  `SecurityHardeningTest.atk34_releaseSmokeRequiresReleaseApkOnRealDevice`,
  `SecurityHardeningTest.atk34_releaseSmokeVerifyOnlyUsesHighestParsedBuildToolsVersion`, and
  `SecurityHardeningTest.atk34_releaseSmokeReportRecordsPhysicalLaunchWithoutSerialLeak`
  under `--dependency-verification=strict --stacktrace`.
- Remaining full-goal gaps: the report artifact makes physical smoke auditable, but the real
  release APK still must be smoked on an actual physical device after a tagged production release;
  real tagged release verification with production secrets, human UX sign-off, and MIT
  legal/provenance clearance remain external gates.

## Plan - 2026-06-19 Release Artifact Verification Report
- [x] Add a failing contract for a durable `--report` output from the canonical release artifact
  verifier.
- [x] Make `scripts/VerifyReleaseArtifacts.java` write a non-secret verification report covering
  checksums, manifest signature/payload, expected release metadata, and attestation status.
- [x] Wire the manual post-publish `verify-release-artifacts.yml` workflow to upload the report.
- [x] Document the report in `RELEASING.md` and `README.md`.
- [x] Re-run focused release verification tests and the standard local gates, then commit and push.

## Review - 2026-06-19 Release Artifact Verification Report
- The manual post-publish release verifier already downloaded the six release assets and checked
  checksums, signed update-manifest semantics, expected release metadata, signer certificate digest,
  and GitHub attestations, but the successful proof was only stdout.
- Added optional `--report` support to `scripts/VerifyReleaseArtifacts.java`. The report is written
  only after requested checks pass, includes artifact basenames, expected/manifest metadata,
  checksum/signature/payload status, and attestation status, and does not include the manifest
  public key.
- `.github/workflows/verify-release-artifacts.yml` now passes
  `--report "$OUT_DIR/verification-report.md"` and uploads
  `release-artifact-verification-report` using the existing pinned upload-artifact action.
- README and `RELEASING.md` now tell release operators to expect the report artifact.
- Verification passed:
  focused release-verifier tests first failed for missing report support, missing workflow upload,
  and missing docs, then passed after the implementation;
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree`; and `git diff --check` with only
  existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: the report artifact makes post-publish release verification auditable,
  but the workflow still must be run against a real tagged release with production secrets; physical
  release APK smoke, human UX sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-19 License Boundary Report Artifacts
- [x] Add failing contracts for `check-license-boundary.ps1 -ReportPath` pass/fail report output.
- [x] Make the license-boundary script write a non-secret report for successful and failed checks.
- [x] Upload license-boundary reports from Android CI and tagged release workflows.
- [x] Document the report artifacts in `README.md` and `RELEASING.md`.
- [x] Re-run focused license-boundary tests and the standard local gates, then commit and push.

## Review - 2026-06-19 License Boundary Report Artifacts
- The license-boundary guard already blocked premature MIT release wording and artifact boundary
  drift, but successful or failed proof was only stdout/stderr. That made the remaining MIT/legal
  gate harder to audit after CI or release workflow runs.
- Added optional `-ReportPath` support to `scripts/check-license-boundary.ps1`. Passing reports
  record source mode, strict flags, APK/SBOM artifact names, inspected counts, blocked MIT release
  status, and zero issues. Failing reports are written before nonzero exit and include normalized
  issue details.
- Android CI now passes `-ReportPath app/build/reports/license-boundary/license-boundary-report.md`
  and uploads `license-boundary-report`. Tagged direct-APK releases now write source and artifact
  boundary reports under `release-boundary/` and upload `release-license-boundary-reports`.
- README and `RELEASING.md` now document the report artifacts.
- Verification passed:
  focused ATK-35 tests first failed for missing report support, missing workflow uploads, missing
  docs, and path-normalized failure details, then passed after the implementation;
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath app/build/reports/license-boundary/local-license-boundary-report.md`;
  inspected the generated local report with status `passed` and `Issues: 0`; and `git diff --check`
  with only existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: these reports make the GPL/MIT boundary auditable, but actual MIT
  relicensing still needs rights/provenance clearance outside this repo patch; physical-device
  release smoke, real tagged release verification with production secrets, and human UX sign-off
  remain external gates.

## Plan - 2026-06-19 UX Sign-Off Verification Report
- [x] Add failing contracts for a UX sign-off verifier that rejects unchecked review packets and
  writes a failure report.
- [x] Add a passing contract for completed review packets with reviewer identity and a durable
  sign-off report.
- [x] Implement `scripts/verify-ux-signoff.ps1` without changing the UX matrix runner semantics.
- [x] Document the UX sign-off verifier in `README.md`.
- [x] Re-run focused UX script tests and the standard local gates, then commit and push.

## Review - 2026-06-19 UX Sign-Off Verification Report
- The UX matrix already generated screenshots and `ux-matrix-review.md`, but human sign-off was
  not machine-auditable. A reviewer could leave unchecked items without a deterministic gate or
  durable sign-off report.
- Added `scripts/verify-ux-signoff.ps1`. It requires a reviewer, scans all checklist items in the
  review packet, fails while any item remains unchecked, writes `ux-signoff-report.md` on pass or
  failure, and leaves the existing UX matrix runner unchanged.
- README now documents the post-review verifier command with `-Reviewer` and
  `ux-signoff-report.md`.
- Verification passed:
  focused UX script tests first failed because the verifier script was missing and the README did
  not document it, then passed after implementation and docs;
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath app/build/reports/license-boundary/local-license-boundary-report.md`;
  and `git diff --check` with only existing Windows LF-to-CRLF warnings.
- Remaining full-goal gaps: this makes UX sign-off auditable, but the actual human review still
  has to be performed against real generated screenshots; physical-device release smoke, real
  tagged release verification with production secrets, and MIT rights/provenance clearance remain
  external gates.

## Plan - 2026-06-20 Release Readiness Proof Aggregator
- [x] Add failing contracts for a release-readiness verifier that rejects missing or non-passing
  release artifact, physical smoke, UX sign-off, and license-boundary reports.
- [x] Add a passing contract that accepts all four proof reports and writes one readiness report.
- [x] Implement `scripts/verify-release-readiness.ps1` as a local final gate over existing reports.
- [x] Document the release-readiness verifier in `README.md`.
- [x] Re-run focused readiness tests and the standard local gates, then commit and push.

## Review - 2026-06-20 Release Readiness Proof Aggregator
- The branch had separate proof reports for release artifacts, physical smoke, UX sign-off, and
  license-boundary checks, but no one-command final gate that proved all four reports were present
  and passing together.
- Added `scripts/verify-release-readiness.ps1`. It rejects missing, empty, or non-passing reports;
  requires physical-device smoke evidence instead of identity-only smoke; and writes
  `release-readiness-report.md` on pass or failure.
- Added focused script tests for the physical-smoke rejection path, the all-proofs-pass path, and
  README documentation of the release-readiness command.
- README now documents the final readiness aggregation command and all required report parameters.
- Verification passed:
  focused readiness tests first failed while the script and docs were missing, then passed after
  implementation; forced focused test rerun with `--rerun-tasks` passed;
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  first exceeded the local 5-minute command timeout without a test result, then passed when rerun
  with a longer timeout; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md`; `git diff --check` with
  only existing Windows LF-to-CRLF warnings; and a 100-column check over the new script and test.
- Remaining full-goal gaps: the readiness verifier is a local proof aggregator only. Real tagged
  release artifact verification, physical-device release smoke, human UX sign-off, and MIT
  rights/provenance clearance must still be performed before claiming release readiness.

## Plan - 2026-06-20 Release Proof Identity Consistency
- [x] Add failing readiness coverage proving physical smoke and release artifact reports cannot be
  mixed across different APK names, hashes, or signing certificates.
- [x] Add failing smoke-report coverage requiring `run-release-smoke.ps1` to record APK SHA-256 and
  signer identity when checked.
- [x] Extend the release smoke report with APK hash and normalized signer certificate fields.
- [x] Extend `verify-release-readiness.ps1` to compare release artifact and smoke APK identity.
- [x] Document the identity consistency expectation and rerun focused plus standard local gates.

## Review - 2026-06-20 Release Proof Identity Consistency
- The release-readiness verifier required four passing reports, but it did not prove the artifact
  verifier and physical smoke report described the same release APK. That could let stale or mixed
  reports pass the final local gate.
- Added release-readiness coverage for mismatched physical-smoke APK SHA-256, and updated passing
  fixtures to include APK name, APK SHA-256, and signer certificate identity.
- `run-release-smoke.ps1` now writes the tested APK SHA-256 and normalized signer certificate
  digest when signer verification is requested; verify-only reports explicitly say
  `Signer certificate SHA-256: not-checked`.
- `verify-release-readiness.ps1` now requires and compares the artifact verifier and physical
  smoke APK name, APK SHA-256, and signer certificate digest, and writes a
  `Release identity consistency` status line in the final report.
- README and `RELEASING.md` now document that final readiness requires matching same-APK identity
  evidence and that smoke reports include APK SHA-256 plus signer identity.
- Verification passed:
  focused tests first failed for missing smoke identity fields, missing readiness identity
  comparison, and missing docs; after implementation they passed; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a 100-column check passed over the
  modified scripts and readiness test.
- Remaining full-goal gaps: this prevents mixed release-proof reports, but real tagged release
  verification with production secrets, physical-device release smoke, human UX sign-off, and MIT
  legal/provenance clearance remain external gates.

## Plan - 2026-06-20 Release Artifact License Readiness
- [x] Add a failing readiness contract that rejects source-only license-boundary reports for final
  release readiness.
- [x] Require final readiness to use strict artifact license-boundary evidence with APK and SBOM
  artifact names.
- [x] Keep Android CI source-only license-boundary reports valid for CI, but not sufficient for
  final release readiness.
- [x] Document that `verify-release-readiness.ps1` expects the tagged release artifact boundary
  report, not the regular CI source report.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release Artifact License Readiness
- The final readiness verifier accepted any passing license-boundary report. That included the
  regular CI source-only report, which does not prove the actual release APK and SBOM artifact
  boundary.
- Added a focused regression test proving a source-only license-boundary report fails final
  readiness even when artifact verification, physical smoke, and UX sign-off reports pass.
- `verify-release-readiness.ps1` now requires release license-boundary evidence to report
  `Source mode: GitTracked`, `Strict source archive: true`, `Strict artifacts: true`, and concrete
  APK and SBOM artifact names.
- README and `RELEASING.md` now state that final readiness must use the tagged release artifact
  license-boundary report, not the regular CI source-only report.
- Verification passed:
  focused readiness tests first failed on the new source-only rejection and README expectation,
  then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a 100-column scan found only existing
  long README/RELEASING lines outside this slice. One first-pass 100-column helper command failed
  from PowerShell string interpolation syntax and was rerun successfully.
- Remaining full-goal gaps: this closes the source-only license-report loophole in final release
  readiness, but real tagged release verification with production secrets, physical-device release
  smoke, human UX sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 Release License Artifact Identity
- [x] Add a failing readiness contract that rejects a license-boundary artifact report for a
  different APK or SBOM than the release artifact verification report.
- [x] Require release artifact reports used for readiness to include the SBOM artifact name.
- [x] Compare release artifact and license-boundary APK/SBOM names in
  `verify-release-readiness.ps1`.
- [x] Document that final readiness requires the same APK/SBOM across release artifact and
  license-boundary reports.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release License Artifact Identity
- The final readiness verifier proved the release artifact and physical smoke reports described
  the same APK, and required strict artifact license-boundary evidence, but it did not prove the
  license-boundary report named the same APK/SBOM artifacts as the release artifact verifier.
- Added a focused regression test where release artifact, physical smoke, and UX reports pass, but
  the license-boundary report names `OtherAdAway.apk` and `other.cdx.json`. The test failed first,
  proving the old verifier accepted mixed artifact-license proof.
- `verify-release-readiness.ps1` now requires `- SBOM:` in the release artifact report and compares
  release artifact APK/SBOM names against the license-boundary APK/SBOM names.
- README and `RELEASING.md` now state that final readiness requires the same APK and SBOM artifact
  names from the release artifact verification report.
- Verification passed:
  focused readiness tests first failed on the new artifact mismatch and README expectation, then
  passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a 100-column scan found only existing
  long README/RELEASING lines outside this slice.
- Remaining full-goal gaps: this prevents mixed release artifact/license proof reports, but real
  tagged release verification with production secrets, physical-device release smoke, human UX
  sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 Release UX Sign-Off Provenance
- [x] Add a failing readiness contract that rejects anonymous or incomplete UX sign-off reports.
- [x] Require final readiness UX evidence to include reviewer identity, review packet, checked
  item count, unchecked item count, and zero issues.
- [x] Keep `verify-ux-signoff.ps1` as the source of the durable UX report and avoid changing the
  UX matrix runner semantics.
- [x] Document that final readiness requires a generated UX sign-off report, not hand-written
  pass markers.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release UX Sign-Off Provenance
- The final readiness verifier accepted a bare UX report containing only `Status: passed` and
  `Unchecked items: 0`. That could let hand-written pass markers satisfy the UX gate without a
  reviewer, reviewed packet, checked-item count, or zero-issues proof from `verify-ux-signoff.ps1`.
- Added a focused regression test where release artifact, physical smoke, and license-boundary
  reports pass, but the UX sign-off report is anonymous and incomplete. The test failed first,
  proving the old verifier accepted weak UX proof.
- `verify-release-readiness.ps1` now requires UX sign-off reports to include `Reviewer`,
  `Review packet`, `Checked items`, `Unchecked items: 0`, and `Issues: 0`, and rejects empty,
  `not-provided`, or non-positive checked-item counts.
- README and `RELEASING.md` now state that final readiness must use a report generated by
  `verify-ux-signoff.ps1`, not hand-written pass markers.
- Verification passed:
  focused readiness tests first failed on the new UX provenance and README expectations, then
  passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a 100-column scan found only existing
  long README/RELEASING lines outside this slice.
- Remaining full-goal gaps: this makes UX proof harder to fake, but the real human screenshot
  review still must be performed. Real tagged release verification with production secrets,
  physical-device release smoke, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 Release Artifact Proof Detail
- [x] Add a failing readiness contract that rejects sparse release artifact reports with only
  pass/status and attestation markers.
- [x] Require final readiness to see checksum verification, manifest signature verification,
  manifest payload verification, and expected signing certificate evidence.
- [x] Reject release artifact reports where the expected certificate was not provided or does not
  match the manifest signing certificate.
- [x] Document that final readiness requires the generated `verify-release-artifacts` report, not
  hand-written artifact pass markers.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release Artifact Proof Detail
- The final readiness verifier accepted a sparse release artifact report that had `Status: passed`,
  APK/SBOM names, APK SHA-256, manifest certificate, and attestation markers, but omitted the
  checksum, manifest signature, manifest payload, and expected-certificate proof emitted by
  `verify-release-artifacts`.
- Added a focused regression test where physical smoke, UX sign-off, and license-boundary reports
  pass while the release artifact report is sparse. The test failed first, proving the old verifier
  accepted weak release artifact proof.
- `verify-release-readiness.ps1` now requires `Checksum verification: passed`,
  `Manifest signature: passed`, `Manifest payload: passed`, and `Expected certificate SHA-256`.
  It rejects `not-provided` expected certificates and checks the expected certificate matches the
  manifest certificate.
- README and `RELEASING.md` now state that final readiness must use the generated
  `verify-release-artifacts` report, not hand-written artifact pass markers.
- Verification passed:
  focused readiness tests first failed on sparse artifact proof and README expectations, then
  passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a 100-column scan found only existing
  long README/RELEASING lines outside this slice.
- Remaining full-goal gaps: this makes release artifact proof harder to fake locally, but the
  real tagged release verification with production secrets still must be run. Physical-device
  release smoke, actual human UX screenshot sign-off, and MIT legal/provenance clearance remain
  external gates.

## Plan - 2026-06-20 Release Physical Smoke Provenance
- [x] Add a failing readiness contract that rejects sparse physical-smoke pass markers.
- [x] Require final readiness physical-smoke evidence to include package, signer certificate
  check, signer certificate digest, device serial hash, and launch pid.
- [x] Validate signer certificate check is true, device serial hash is SHA-256-shaped, and launch
  pid is a positive integer.
- [x] Document that final readiness requires the generated `run-release-smoke.ps1` physical-device
  report, not hand-written smoke markers.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release Physical Smoke Provenance
- The final readiness verifier accepted a sparse physical-smoke report with `Status: passed`,
  physical-device mode, APK hash, signer certificate digest, verified-real-device marker, and
  launch pid, but without package, signer-check flag, or device serial hash from
  `run-release-smoke.ps1`.
- Added a focused regression test where release artifact, UX sign-off, and license-boundary
  reports pass while the physical-smoke report is sparse. The test failed first, proving the old
  verifier accepted weak physical-device smoke proof.
- `verify-release-readiness.ps1` now requires `Package`, `Signer certificate check`,
  `Signer certificate SHA-256`, `Device serial SHA-256`, and `Launch pid observed`. It rejects
  signer checks other than true, non-SHA-256-shaped device serial hashes, and non-positive launch
  pids.
- README and `RELEASING.md` now state that final readiness must use the generated
  `run-release-smoke.ps1` physical-device report, not hand-written smoke markers.
- Verification passed:
  focused readiness tests first failed on sparse physical-smoke proof and README expectations,
  then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a 100-column scan found only existing
  long README/RELEASING lines outside this slice.
- Remaining full-goal gaps: this makes physical-smoke proof harder to fake locally, but the real
  physical-device release APK smoke still must be run. Real tagged release verification with
  production secrets, actual human UX screenshot sign-off, and MIT legal/provenance clearance
  remain external gates.

## Plan - 2026-06-20 Release Tag Provenance
- [x] Add a failing readiness contract that rejects release artifact and physical-smoke reports
  from different release tags.
- [x] Record release-tag provenance in generated artifact-verification and release-smoke reports.
- [x] Pass the release tag through the physical smoke workflow and document the requirement.
- [x] Require matching release tags in `verify-release-readiness.ps1`.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release Tag Provenance
- The final readiness verifier already compared APK name, APK SHA-256, signer certificate, and
  release APK/license artifact names, but it did not prove artifact verification and physical
  smoke came from the same release tag.
- Added a focused regression test where the release artifact report names `v13.5.0` and the
  physical smoke report names `v13.5.1`. The test failed first, proving the old verifier accepted
  mixed release-tag proof.
- `VerifyReleaseArtifacts.java` now records `Release tag` in its generated report by parsing the
  signed manifest APK URL. `run-release-smoke.ps1` records `Release tag` from an explicit
  `-ReleaseTag` argument or from standard `AdAway_<version>.apk` names.
- `.github/workflows/physical-release-smoke.yml` now passes the workflow dispatch tag into
  `run-release-smoke.ps1`.
- `verify-release-readiness.ps1` now requires release-tag fields in the release artifact and
  physical-smoke reports, validates their `v...` shape, and rejects mismatches.
- README and `RELEASING.md` now document same-release-tag readiness and physical-smoke report
  provenance.
- Verification passed:
  focused readiness/security unit tests first failed on the new tag-mismatch contract, then passed
  after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and a changed-hunk line-length scan found
  no new long code lines beyond pre-existing README/test/ledger lines.
- Remaining full-goal gaps: this closes another local stale-report loophole, but real tagged
  release verification with production secrets, physical-device release APK smoke, actual human UX
  screenshot sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 UX Sign-Off Packet Hash Provenance
- [x] Add failing contracts that require UX sign-off reports to carry a review-packet SHA-256.
- [x] Make `verify-ux-signoff.ps1` hash the exact packet it reviewed.
- [x] Require final readiness UX evidence to include a SHA-256-shaped review-packet hash.
- [x] Document that UX sign-off is tied to the exact review packet contents.
- [x] Re-run focused script tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 UX Sign-Off Packet Hash Provenance
- The UX sign-off report already required reviewer identity, packet name, checked count, zero
  unchecked items, and zero issues, but it did not bind the sign-off to the exact review-packet
  bytes.
- Added focused red tests requiring `verify-ux-signoff.ps1` to emit
  `Review packet SHA-256` and requiring `verify-release-readiness.ps1` to reject an otherwise
  complete UX sign-off report without that field.
- `verify-ux-signoff.ps1` now hashes the resolved review packet with SHA-256 and writes the
  lowercase digest into every generated sign-off report.
- `verify-release-readiness.ps1` now requires `Review packet SHA-256` and validates that it is a
  SHA-256-shaped value.
- README and `RELEASING.md` now state that final UX sign-off evidence includes the review-packet
  hash, not just a packet filename.
- Verification passed:
  focused UX/readiness script tests first failed on missing packet-hash provenance, then passed
  after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and changed-line length scanning found
  only pre-existing README long lines.
- Remaining full-goal gaps: this makes UX sign-off harder to reuse against a different packet, but
  the actual human screenshot review still must be performed. Real tagged release verification
  with production secrets, physical-device release APK smoke, and MIT legal/provenance clearance
  remain external gates.

## Plan - 2026-06-20 Release Readiness Evidence Summary
- [x] Add a failing readiness contract requiring the final readiness report to preserve release
  identity and proof-report hashes.
- [x] Make `verify-release-readiness.ps1` summarize release tag, APK, APK SHA-256, SBOM, and UX
  packet hash in its output report.
- [x] Make `verify-release-readiness.ps1` hash the four consumed proof reports so the final report
  is auditable.
- [x] Document the readiness evidence summary and proof-report hash fields.
- [x] Re-run focused readiness tests plus the standard local gates, then commit and push.

## Review - 2026-06-20 Release Readiness Evidence Summary
- The final readiness verifier validated the four proof reports, but the output
  `release-readiness-report.md` only kept pass/fail categories. It did not preserve the release
  identity or fingerprints of the proof reports it accepted.
- Added a focused red test requiring a passing readiness report to include release tag, APK,
  APK SHA-256, SBOM, UX review packet SHA-256, and SHA-256 hashes for the release artifact,
  physical smoke, UX sign-off, and license-boundary reports.
- `verify-release-readiness.ps1` now repeats the release identity from the artifact/UX reports
  and writes SHA-256 fingerprints for all four input proof reports in both passing and failing
  readiness reports.
- README and `RELEASING.md` now document the readiness evidence summary and proof-report hash
  fields.
- Verification passed:
  focused readiness tests first failed on missing readiness identity/hash output, then passed
  after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`
  gate passed; `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed; `git diff --check`
  passed with only existing Windows LF-to-CRLF warnings; and changed-line scanning found only
  pre-existing README long lines.
- Remaining full-goal gaps: this makes the final readiness report auditable, but the real tagged
  release verification with production secrets, physical-device release APK smoke, actual human UX
  screenshot sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 Release Readiness Workflow Aggregation
- [x] Add a failing workflow/docs contract for a manual final release-readiness workflow.
- [x] Implement the workflow to download durable proof artifacts by run ID.
- [x] Feed the generated UX sign-off report into the workflow without pretending human review is
  automated.
- [x] Run `verify-release-readiness.ps1` from the workflow and upload the final readiness report.
- [x] Re-run focused readiness workflow coverage plus the standard local gates, then commit and
  push.

## Review - 2026-06-20 Release Readiness Workflow Aggregation
- Gap found: the repository had a strong local `verify-release-readiness.ps1` gate, but no
  GitHub Actions workflow to aggregate the post-publish artifact verifier, physical-device smoke,
  release license-boundary evidence, and human UX sign-off into one durable final report.
- Added a focused red contract requiring `.github/workflows/verify-release-readiness.yml`, read-only
  permissions, proof artifact downloads, UX sign-off report ingestion, canonical readiness
  verification, final report upload, and README/RELEASING documentation.
- Added the manual `Verify release readiness` workflow. It accepts run IDs for
  `release-artifact-verification-report`, `physical-release-smoke-report`, and
  `release-license-boundary-reports`, decodes the base64 `ux-signoff-report.md`, runs
  `scripts/verify-release-readiness.ps1`, and uploads `release-readiness-report`.
- README and `RELEASING.md` now document the final workflow, including the
  `ux_signoff_report_base64` input and the final `release-readiness-report` artifact.
- Verification passed: the new focused workflow/docs contract failed first on the missing workflow
  and then passed after implementation; after cleanup the focused contract passed again; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed;
  `git diff --check` passed with only existing Windows LF-to-CRLF warnings; and changed-line
  length scanning passed.
- Remaining full-goal gaps: this makes the final release-readiness proof CI-backed, but the real
  production tagged release, real physical-device release smoke, actual human UX screenshot
  sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 UX Sign-Off Workflow Artifact
- [x] Add failing contracts for a durable UX sign-off workflow and readiness UX artifact
  consumption.
- [x] Implement a manual UX sign-off workflow that validates the checked review packet.
- [x] Switch final release readiness from raw UX report input to a UX sign-off run ID.
- [x] Update README and `RELEASING.md` for the UX sign-off artifact flow.
- [x] Re-run focused workflow coverage plus the standard local gates, then commit and push.

## Review - 2026-06-20 UX Sign-Off Workflow Artifact
- Gap found: `verify-release-readiness.yml` improved final aggregation, but it still accepted a
  raw base64 UX sign-off report. That made the UX proof less durable than the release artifact,
  physical smoke, and license-boundary reports.
- Added focused red contracts requiring a new `.github/workflows/verify-ux-signoff.yml` workflow
  and requiring final readiness to consume `ux-signoff-report` by run ID.
- Added the manual `Verify UX sign-off` workflow. It accepts a base64 checked
  `ux-matrix-review.md` plus reviewer identity, runs `scripts/verify-ux-signoff.ps1`, and uploads
  a durable `ux-signoff-report` artifact.
- Updated `verify-release-readiness.yml` to accept `ux_signoff_run_id`, download
  `ux-signoff-report`, and remove the raw `ux_signoff_report_base64` input.
- README and `RELEASING.md` now document the UX sign-off workflow and the artifact-based final
  readiness flow.
- Verification passed: the focused workflow contracts failed first on the old readiness UX input
  and missing UX sign-off workflow, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed;
  `git diff --check` passed with only existing Windows LF-to-CRLF warnings; and changed-line
  length scanning passed.
- Remaining full-goal gaps: this makes UX sign-off proof durable in CI, but the real production
  tagged release, real physical-device release smoke, actual human UX screenshot sign-off, and
  MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 UX Review Packet Hash Proof
- [x] Add failing readiness and workflow contracts for checked UX review packet hash verification.
- [x] Make `verify-release-readiness.ps1` compare the UX sign-off report hash with the checked
  review packet file hash when `-UxReviewPacket` is provided.
- [x] Make `verify-ux-signoff.yml` upload the checked review packet with the sign-off report.
- [x] Make `verify-release-readiness.yml` download and pass the checked review packet to final
  readiness.
- [x] Update README and `RELEASING.md` for packet-backed UX sign-off proof.
- [x] Re-run focused readiness/workflow coverage plus the standard local gates, then commit and
  push.

## Review - 2026-06-20 UX Review Packet Hash Proof
- Gap found: the UX sign-off report recorded `Review packet SHA-256`, but final readiness did not
  prove that value against the actual checked `ux-matrix-review.md` bytes.
- Added a focused red readiness test where the UX report names one packet hash while the supplied
  packet file hashes differently. The old verifier accepted it.
- Added workflow contracts requiring `verify-ux-signoff.yml` to upload the checked review packet
  and requiring `verify-release-readiness.yml` to pass `-UxReviewPacket`.
- `verify-release-readiness.ps1` now accepts `-UxReviewPacket`, computes its SHA-256, and rejects
  mismatch against the UX sign-off report. The generated readiness report also records
  `UX review packet file SHA-256`.
- README and `RELEASING.md` now document the `-UxReviewPacket` path and the same-review-packet
  hash requirement.
- Verification passed: focused readiness/workflow tests failed first on the missing packet-backed
  proof, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed;
  `git diff --check` passed with only existing Windows LF-to-CRLF warnings; and changed-line
  length scanning passed.
- Remaining full-goal gaps: this makes the UX proof chain auditable, but the real production
  tagged release, real physical-device release smoke, actual human UX screenshot sign-off, and
  MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 Release Source Commit Provenance
- [x] Add failing readiness and workflow contracts that reject proof reports from mixed source
  commits.
- [x] Record source commit provenance in generated release artifact, physical smoke, UX sign-off,
  and license-boundary reports.
- [x] Require `verify-release-readiness.ps1` to compare source commits across all proof reports.
- [x] Update README and `RELEASING.md` so release operators know final readiness is same-commit
  as well as same-tag/same-artifact.
- [x] Re-run focused readiness/workflow coverage plus the standard local gates, then commit and
  push.

## Review - 2026-06-20 Release Source Commit Provenance
- Gap found: final readiness could already reject mixed release tags, APK hashes, certificate
  hashes, license artifacts, UX packet hashes, and stale proof-report files, but it did not prove
  that the four proof reports were produced from the same source commit.
- Added a focused red readiness test where the release artifact, UX sign-off, and license-boundary
  reports used one source commit while physical smoke used another. The old verifier accepted the
  mixed proof set.
- `VerifyReleaseArtifacts.java`, `run-release-smoke.ps1`, `verify-ux-signoff.ps1`, and
  `check-license-boundary.ps1` now write `Source commit` using `GITHUB_SHA` first and local
  `git rev-parse HEAD` as a fallback. The PowerShell fallback uses `System.Diagnostics.Process`
  so non-git test fixtures return `not-provided` instead of aborting on native command errors.
- `verify-release-readiness.ps1` now requires a 40-hex source commit in release artifact,
  physical smoke, UX sign-off, and license-boundary reports; it rejects mismatches and records
  `Source commit` plus `Source commit consistency` in the final readiness report.
- README and `RELEASING.md` now document that final readiness requires same source commit as well
  as same release tag, APK, hashes, artifacts, and UX packet.
- Verification passed: focused readiness/UX/security tests failed first on the missing
  source-commit contract, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed;
  `scripts/check-license-boundary.ps1 -SourceMode WorkingTree -ReportPath
  app/build/reports/license-boundary/local-license-boundary-report.md` passed;
  `git diff --check` passed with only existing Windows LF-to-CRLF warnings; and changed-line
  length scanning passed.
- Remaining full-goal gaps: this closes another stale/mixed-proof loophole, but the real
  production tagged release, real physical-device release smoke, actual human UX screenshot
  sign-off, and MIT legal/provenance clearance remain external gates.

## Plan - 2026-06-20 UX Packet Source Commit Binding
- [x] Add failing UX script tests requiring generated review packets to carry a source commit and
  sign-off to reject stale packet commits.
- [x] Stamp `ux-matrix-review.md` with the source commit that generated it.
- [x] Make `verify-ux-signoff.ps1` require the packet source commit to match the current source
  commit and record both values in the sign-off report.
- [x] Update README and `RELEASING.md` so human reviewers know the checked packet is source-bound.
- [x] Re-run focused UX tests plus the standard local gates, then commit and push.

## Review - 2026-06-25 UX Packet Source Commit Binding
- Added focused red tests requiring generated UX review packets to carry `Source commit`,
  requiring stale packet/current source mismatches to fail sign-off, and requiring the report to
  include `Review packet source commit`.
- `run-ux-matrix.ps1` now stamps `ux-matrix-review.md` with `GITHUB_SHA` or local
  `git rev-parse HEAD`.
- `verify-ux-signoff.ps1` now records the packet source commit and rejects missing, invalid, or
  mismatched packet/current source commits before producing a passing sign-off report.
- README and `RELEASING.md` now document that the checked UX packet is source-bound.
- Verification passed: the focused `UxMatrixScriptTest` suite first failed on the missing
  source-commit contract, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed; license-boundary check passed;
  `git diff --check` passed with only existing LF-to-CRLF warnings; and changed-line scanning
  for edited code/docs passed.

## Plan - 2026-06-25 Canonical User Story Ledger
- [x] Pivot from the previous release-proof goal to the new objective: enumerate repo features as
  user stories, track status in one canonical spreadsheet, then test/fix/retest each behavior.
- [x] Dispatch read-only feature explorers for Home/onboarding, filter management, runtime/prefs,
  and update/release/security surfaces.
- [x] Create `tasks/user-story-status.tsv` as the canonical spreadsheet with stable story IDs,
  expected behavior, evidence paths, status, error IDs, fix status, and retest status.
- [x] Seed the ledger with current repo-derived stories and initial open risks from the explorers.
- [x] Validate the TSV shape so later tooling can iterate row-by-row.

## Review - 2026-06-25 Canonical User Story Ledger
- Created `tasks/user-story-status.tsv` with 98 current user-story rows and 15 tracking columns:
  story id, area, feature, user story, expected behavior, code/test evidence, status, test state,
  priority, risk notes, error id, error notes, fix status, and retest status.
- Integrated findings from all four read-only explorers. First grounded open rows include Home
  protection-state visibility, leak-status clean-state reachability, redirected-rule IP validation
  parity, Quick Settings tile null handling, inert startup update preference, root DNS log copy,
  launcher shortcut package drift, and adware matcher freshness.
- The new ledger is intentionally marked `Not tested`, `Partially covered`, `Needs attention`, or
  `Needs fix` rather than claiming completion. The next phase is to execute the stories in priority
  order, fill error fields with real test evidence, patch each confirmed logistical/UX error, and
  retest the affected rows.
- Verification passed: `tasks/user-story-status.tsv` parses as 98 stories with 15 columns.

## Plan - 2026-06-25 Story Fix Loop 1
- [x] Start from `tasks/user-story-status.tsv` and select grounded high-priority defects rather
  than introducing unrelated feature work.
- [x] Add red tests for `UPDATE-005` startup update preference wiring and `SYS-001` Quick Settings
  tile null handling.
- [x] Wire startup app-update checks through the existing HomeViewModel update path on fresh Home
  launches only.
- [x] Make Quick Settings tile updates tolerate a missing tile handle.
- [x] Update the canonical story ledger with fix and focused retest evidence.

## Review - 2026-06-25 Story Fix Loop 1
- Confirmed `UPDATE-005`: the app had `updateCheckAppStartup` resources and
  `PreferenceHelper.getUpdateCheckAppStartup()`, but no main-code caller. Added a focused source
  contract that failed first.
- `HomeActivity` now calls `HomeViewModel.checkForAppUpdate()` on fresh launch only when
  `PreferenceHelper.getUpdateCheckAppStartup(this)` is true. Restored activities do not rerun the
  startup check.
- Confirmed `SYS-001`: `AdBlockingTileService.updateTile()` dereferenced `getQsTile()` without a
  null guard. Added a focused crash-surface contract that failed first.
- `AdBlockingTileService.updateTile()` now returns when Android does not provide a tile handle.
- Focused retest passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.home.HomeNavigationSourcesContractTest --tests
  org.adaway.security.CrashSurfaceHardeningTest --dependency-verification=strict --stacktrace`.
- Remaining story-loop work: full device behavior is still open for both rows, and the rest of the
  P0/P1 story ledger still needs systematic test/fix/retest passes.

## Plan - 2026-06-25 Story Fix Loop 2
- [x] Confirm `HOME-010` with a failing unit test for a running VPN state with Private DNS off,
  no app bypass, and no excluded apps.
- [x] Make DoH leak classification distinguish covered common DoH routing from an active detected
  risk so the clean summary can render when no detectable leak risks remain.
- [x] Update `tasks/user-story-status.tsv` with the concrete fix and focused retest evidence.
- [x] Re-run focused leak-status tests plus the standard local gates, then commit and push.

## Review - 2026-06-25 Story Fix Loop 2
- Confirmed `HOME-010`: `LeakStatus.hasDohRisk()` counted DoH as an active risk even when the
  VPN was running and common DoH routes were covered, so the clean Home summary could not render.
  Added a focused unit test that failed first.
- `LeakStatus` now treats covered common DoH routing as a disclosed limitation, not an active
  detected risk. Root mode and stopped VPN mode still report DoH risk.
- Updated `tasks/user-story-status.tsv` so `HOME-010` records the clean-state fix while keeping the
  real device Private DNS/VPN bypass/excluded-app pass open.
- Verification passed: focused `LeakStatusTest` failed first on the unreachable clean-state
  behavior, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed; license-boundary check passed;
  `git diff --check` passed with only LF-to-CRLF warnings; TSV shape check passed as 98 stories
  with 15 columns; and edited Java plus Story Fix Loop 2 todo line-length scans passed.

## Plan - 2026-06-25 Story Fix Loop 3
- [x] Confirm `MORE-001` with a failing crash-surface contract for the More GitHub/Help external
  link.
- [x] Make the More GitHub/Help row resolve and catch external Activity launch failures instead
  of crashing the More tab.
- [x] Update `tasks/user-story-status.tsv` with the concrete fix and focused retest evidence.
- [x] Re-run focused crash-surface tests plus the standard local gates, then commit and push.

## Review - 2026-06-25 Story Fix Loop 3
- Confirmed `MORE-001`: the More GitHub/Help row directly launched an external `ACTION_VIEW`
  intent. A device or managed profile with no browser handler could throw instead of keeping the
  More tab usable. Added a focused crash-surface contract that failed first.
- `MoreFragment` now routes the GitHub/Help row through `openExternalUri`, checks
  `resolveActivity`, and catches `ActivityNotFoundException` plus `SecurityException`.
- Updated `tasks/user-story-status.tsv` so `MORE-001` records the external-link crash fix while
  keeping the broader More navigation/device pass open.
- Verification passed: focused `CrashSurfaceHardeningTest` failed first on the missing More link
  guard, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed; license-boundary check passed;
  `git diff --check` passed with only LF-to-CRLF warnings; TSV shape check passed as 98 stories
  with 15 columns; and edited Java plus Story Fix Loop 3 todo line-length scans passed.

## Plan - 2026-06-25 Story Fix Loop 4
- [x] Confirm `SYS-004` with a failing package-replace contract proving scheduled work is
  repaired after `ACTION_MY_PACKAGE_REPLACED`.
- [x] Expose the existing hosts-update and app-update preference sync helpers for receiver use.
- [x] Add filter-set schedule preference sync that restores defaults and enqueues or cancels work
  from persisted state.
- [x] Wire `UpdateReceiver` to repair hosts, app, and filter-set scheduled work after app update.
- [x] Update `tasks/user-story-status.tsv` with the concrete fix and focused retest evidence.
- [x] Re-run focused package-replace coverage plus the standard local gates, then commit and push.

## Review - 2026-06-25 Story Fix Loop 4
- Confirmed `SYS-004`: `UpdateReceiver` handled `ACTION_MY_PACKAGE_REPLACED` but only logged the
  new version. It did not repair scheduled hosts-update, app-update, or filter-set work after app
  replacement. Added a focused package-replace contract that failed first.
- `UpdateReceiver` now resyncs hosts, APK update, and filter-set scheduled work from persisted
  preferences when Android sends `ACTION_MY_PACKAGE_REPLACED`.
- `SourceUpdateService.syncPreferences()` and `ApkUpdateService.syncPreferences()` are public for
  receiver use. `FilterSetUpdateService.syncPreferences()` now restores filter-set schedule
  defaults, then enqueues or cancels work from persisted global schedule state.
- Updated `tasks/user-story-status.tsv` so `SYS-004` records the schedule-repair fix while keeping
  real installed-app replacement smoke open as a release/device gate.
- Verification passed: focused `SystemReceiverContractTest` failed first on the missing receiver
  repair call, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed; license-boundary check passed;
  `git diff --check` passed with only LF-to-CRLF warnings; TSV shape check passed as 98 stories
  with 15 columns; and the new test line-length scan passed.

## Plan - 2026-06-25 Story Fix Loop 5
- [x] Confirm `LIST-003` with a failing contract proving redirected user-rule add/edit validation
  rejects private or reserved redirect IPs like backup import does.
- [x] Add one shared `RegexUtils` redirect-target validator for valid public redirect IPs.
- [x] Wire redirected-list add/edit validation and backup import through the shared validator.
- [x] Update `tasks/user-story-status.tsv` with the concrete fix and focused retest evidence.
- [x] Re-run focused redirected-rule validation coverage plus the standard local gates, then
  commit and push.

## Review - 2026-06-25 Story Fix Loop 5
- Confirmed `LIST-003`: redirected user-rule add/edit validation only required
  `RegexUtils.isValidIP(ip)`, so loopback, RFC1918, link-local, and multicast redirect targets
  could be accepted by the UI even though backup import rejected those targets. Added a focused
  contract that failed first.
- Added `RegexUtils.isValidRedirectIp()` as the single policy for valid public redirect targets.
- `RedirectedHostsFragment` now uses that policy for both add and edit dialogs, and
  `BackupFormat` uses the same helper instead of duplicating the IP plus private/reserved checks.
- Updated `tasks/user-story-status.tsv` so `LIST-003` records the redirected-IP validation fix
  while keeping full UI add/edit device coverage open.
- Verification passed: focused `RedirectedHostsValidationContractTest` failed first on the weak UI
  policy, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed; license-boundary check passed;
  `git diff --check` passed with only LF-to-CRLF warnings; TSV shape check passed as 98 stories
  with 15 columns; and edited Java line-length scanning passed.

## Plan - 2026-06-25 Story Fix Loop 6
- [x] Confirm `HOME-003` with a failing Home contract proving the primary Home surface exposes
  active and inactive protection state instead of ignoring `isAdBlocked()`.
- [x] Add a concise primary protection status line to the Home hero.
- [x] Bind `notifyAdBlocked(boolean)` to explicit active/inactive strings and styling.
- [x] Update `tasks/user-story-status.tsv` with the concrete fix and focused retest evidence.
- [x] Re-run focused Home status coverage plus the standard local gates, then commit and push.

## Review - 2026-06-25 Story Fix Loop 6
- Confirmed `HOME-003`: Home observed `isAdBlocked()` but `notifyAdBlocked(boolean)` ignored the
  actual value and only repainted the header with the same background color. Added a focused Home
  contract that failed first.
- Added `protectionStatusTextView` to the Home hero, with explicit `Protection active` and
  `Protection off` states.
- `notifyAdBlocked(boolean)` now updates the primary status text and red/green state color from
  the applied-protection boolean.
- Updated `tasks/user-story-status.tsv` so `HOME-003` records the primary Home status fix while
  keeping full visual UX matrix coverage open for active, off, and VPN stopped device states.
- Verification passed: focused `homeHeroShowsExplicitProtectionState` failed first on the missing
  Home status, then passed after implementation; the full
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` gate passed; license-boundary check passed;
  `git diff --check` passed with only LF-to-CRLF warnings; TSV shape check passed as 98 stories
  with 15 columns; and changed-line length scanning passed.

## Plan - 2026-06-25 Story Fix Loop 7
- [x] Confirm `HOME-006` with a failing Home contract for the visible update action controls.
- [x] Make the Home check/update controls semantic buttons with clear labels and tooltips.
- [x] Keep the action wiring grounded: check triggers Home update; update/apply triggers sync.
- [x] Update `tasks/user-story-status.tsv` with the concrete HOME-006 evidence and remaining gap.
- [x] Run focused Home action coverage plus standard local gates, then commit and push if green.

## Review - 2026-06-25 Story Fix Loop 7
- Confirmed `HOME-006`: the Home source-card update actions were wired to the right ViewModel
  paths, but the primary user controls were plain `ImageView` icons with no tooltip semantics and
  vague `Update hosts` copy for an action that also applies protection.
- Added a focused Home contract that failed first on the missing semantic button/tooltip/copy
  requirements.
- Changed the Home check and update/apply controls to `ImageButton` widgets with selectable
  feedback, focusability, content descriptions, and tooltips.
- Updated the user-facing labels to `Check sources for updates` and
  `Update and apply protection`.
- Updated `tasks/user-story-status.tsv` while keeping the full connected update/apply user path
  open for device verification.
- Focused verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest.homeUpdateActionsAreClearSemanticButtons
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed-line length scanning passed.

## Plan - 2026-06-25 Story Fix Loop 8
- [x] Confirm `SRC-002` with a failing contract for source-toggle apply feedback.
- [x] Add distinct success copy for applied protection changes.
- [x] Make `ApplyConfigurationSnackbar` show success after a source-toggle apply completes.
- [x] Stop Sources update/apply success paths from reusing pending-configuration copy.
- [x] Update `tasks/user-story-status.tsv` with the concrete SRC-002 evidence and remaining gap.
- [x] Run focused source-apply feedback coverage plus standard local gates, then commit and
  push if green.

## Review - 2026-06-25 Story Fix Loop 8
- Confirmed `SRC-002`: source row toggles route through `ApplyConfigurationSnackbar`, but a
  successful apply could dismiss the installing snackbar with no distinct success confirmation.
  The Sources update/apply success path also reused pending-configuration copy.
- Added a focused source apply-feedback contract that failed first because
  `notification_configuration_applied` did not exist.
- Added `Protection changes applied.` as the explicit success state.
- `ApplyConfigurationSnackbar` now shows applied-success feedback after successful source-toggle
  apply, including the ignored self-update event path used by Sources.
- Sources update/apply success now uses applied-success copy instead of telling the user they still
  need to apply configuration.
- Updated `tasks/user-story-status.tsv` while keeping full connected source-toggle apply device
  verification open.
- Focused verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.adblocking.ApplyConfigurationSnackbarContractTest.
  applyFeedbackDistinguishesPendingInstallingSuccessAndFailure
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed-line length scanning passed.

## Plan - 2026-06-25 Story Fix Loop 9
- [x] Confirm `SRC-005` with a failing Sources update-all menu-path contract.
- [x] Make the update-all menu copy disclose that protection is applied after updating.
- [x] Show source-update-specific running feedback for the update-all path.
- [x] Keep the action wiring grounded: menu action calls update all, updates sources, then applies.
- [x] Update `tasks/user-story-status.tsv` with concrete SRC-005 evidence and remaining gap.
- [x] Run focused source update-all coverage plus standard local gates, then commit and
  push if green.

## Review - 2026-06-25 Story Fix Loop 9
- Confirmed `SRC-005`: the Sources overflow menu reached the correct all-sources update/apply
  code path, but the visible action said only `Update all` and the running snackbar used generic
  configuration-apply copy while downloading sources.
- Added a focused Sources action contract that failed first on the missing explicit update/apply
  copy and source-update running message.
- Changed the menu title to `Update all and apply protection`.
- Added `Updating sources and applying protection...` and use it only for the all-sources
  `runUpdateSources(null)` path.
- Kept the existing all-sources behavior grounded: menu action calls `updateAllSources()`,
  `updateAllSources()` calls `runUpdateSources(null)`, and the background path calls
  `checkAndRetrieveHostsSources()` before `adBlockModel.apply()`.
- Updated `tasks/user-story-status.tsv` while keeping full connected overflow-menu device
  verification open.
- Focused verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.hosts.HostsSourcesActionsContractTest.
  updateAllMenuPathUpdatesSourcesThenAppliesProtection
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed-line length scanning passed.

## Plan - 2026-06-25 Story Fix Loop 10
- [x] Confirm `HOME-001` and `HOME-002` with a failing launch-shell and bottom-nav
  contract before production edits.
- [x] Keep the launcher shell grounded: `HomeActivity` is the only launcher entry and starts on
  the Home tab unless a deliberate deep-navigation extra is present.
- [x] Make singleTop deep-navigation intents update the Activity intent before routing.
- [x] Keep all four bottom-nav destinations reachable through the same `showTab` switch.
- [x] Update `tasks/user-story-status.tsv` with concrete Home launch/navigation evidence and
  remaining connected-device gaps.
- [x] Run focused Home navigation coverage plus standard local gates, then commit and
  push if green.

## Review - 2026-06-25 Story Fix Loop 10
- Confirmed `HOME-001` and `HOME-002`: the launcher and four-tab bottom-nav shell were present,
  but `HomeActivity.onNewIntent()` routed singleTop Discover intents without storing the new
  Activity intent first.
- Added a focused Home launch-shell contract that failed first at
  `HomeNavigationSourcesContractTest.java:71` because `setIntent(intent)` was missing.
- Added the minimal lifecycle fix: `onNewIntent()` now calls `setIntent(intent)` before reading
  `EXTRA_NAV_DISCOVER` and routing to Discover.
- Kept existing launch behavior grounded: fresh launches still default to Home unless
  `EXTRA_NAV_DISCOVER` is present, and `showTab()` still routes Home, Discover, Sources,
  and More.
- Updated `tasks/user-story-status.tsv` for `HOME-001` and `HOME-002` while keeping full
  connected-device launch/navigation verification open.
- Focused red/green verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.home.HomeNavigationSourcesContractTest.
  homeLaunchShellStartsOnHomeAndKeepsLatestDeepIntent
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed-line length scanning passed.
- Local connected run was not attempted because `adb.exe devices` reported no attached devices;
  PR CI remains the connected-device gate for this slice.

## Plan - 2026-06-25 Story Fix Loop 11
- [x] Confirm `ONB-001` and `ONB-002` with a failing onboarding first-run contract before
  production edits.
- [x] Stop no-root auto-detect from launching the VPN permission prompt before user intent.
- [x] Keep VPN as the preselected no-root recommendation and request permission only when the
  user selects or starts VPN protection.
- [x] Keep onboarding completion grounded: save the selected method, launch Home with
  `EXTRA_ONBOARDING_COMPLETE`, and let Home subscribe default lists if empty.
- [x] Update `tasks/user-story-status.tsv` with concrete onboarding evidence and remaining
  connected install-smoke gaps.
- [x] Run focused onboarding coverage plus standard local gates, then commit and push if green.

## Review - 2026-06-25 Story Fix Loop 11
- Confirmed `ONB-001` and `ONB-002`: Home redirects unconfigured users to onboarding, and Home
  subscribes default lists after the onboarding-complete flag, but the no-root auto-detect path
  could call the VPN permission flow before explicit user intent.
- Added a focused onboarding first-run contract that failed first at
  `OnboardingFirstRunContractTest.java:26` because no-root auto-detect called `trySelectVpn()`.
- Split onboarding VPN state into passive preselection and explicit authorization:
  no-root auto-detect now calls `preselectVpn()`, while VPN card clicks and Start can request
  Android VPN consent.
- Added a pending-finish flag so accepting VPN consent from Start completes onboarding and launches
  Home with `EXTRA_ONBOARDING_COMPLETE`.
- Kept default-list behavior grounded: Home still calls
  `DefaultListsSubscriber.subscribeDefaultsIfEmpty` only after onboarding completion.
- Updated `tasks/user-story-status.tsv` for `ONB-001` and `ONB-002` while keeping fresh-install
  device smoke open.
- Focused red/green verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.onboarding.OnboardingFirstRunContractTest.
  noRootAutoDetectPreselectsVpnWithoutSurprisePermissionPrompt
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed-line length scanning passed.
- Local fresh-install smoke was not attempted because `adb.exe devices` reported no attached
  devices; PR CI remains the connected-device gate for this slice.

## Plan - 2026-06-25 Story Fix Loop 12
- [x] Confirm the next Discover proof-chain gap with a failing contract before production edits.
- [x] Preserve subscribed FilterLists row state from durable `hosts_sources` metadata when the
  transient FilterLists URL cache is cold or stale.
- [x] Make visible bulk remove use the same durable FilterLists ID to selected-URL mapping so
  subscribed rows can be removed after restart/cache miss.
- [x] Keep compatibility gating unchanged: unsupported rows remain manual-review only, and bulk
  subscribe still avoids unsafe browser-rule flattening.
- [x] Update `tasks/user-story-status.tsv` with concrete Discover evidence and remaining device
  gaps.
- [x] Run focused Discover/FilterLists tests plus standard local gates, then commit and push if
  green.

## Review - 2026-06-25 Story Fix Loop 12
- Confirmed `DISC-007` and `DISC-008`: subscribed FilterLists rows could depend on the transient
  FilterLists URL cache for row state and visible bulk removal, despite durable FilterLists
  provenance being stored on `hosts_sources`.
- Added a focused Discover contract that failed first at
  `DiscoverPresetSubscriptionTest.filterListsSubscribedRowsUseDurableSourceMetadataWhenUrlCacheMisses`
  because no durable FilterLists ID to source URL index existed.
- Added a durable in-memory index from `HostsSource.filterListId` to selected source URL, preferring
  `filterListSelectedUrl` and falling back to the source URL.
- Row state, single unsubscribe, and visible bulk remove now use durable source metadata before
  transient FilterLists URL prefs, so restart/cache-miss paths remain removable.
- Updated `tasks/user-story-status.tsv` for `DISC-007` and `DISC-008` while keeping full connected
  filtered/remove device verification open.
- Focused red/green verification passed:
  `:app:testDebugUnitTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest.
  filterListsSubscribedRowsUseDurableSourceMetadataWhenUrlCacheMisses
  --dependency-verification=strict --stacktrace`.
- Nearby Discover/FilterLists unit coverage passed:
  `DiscoverPresetSubscriptionTest`, `FilterListsSubscriptionStateTest`,
  `FilterListCompatibilityTest`, and `FilterListsSubscribeAllWorkerTest`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed source added-line length scanning
  passed.
- Local connected run was not attempted because the configured SDK `adb.exe devices` reported no
  attached devices; PR CI remains the connected-device gate for this slice.

## Plan - 2026-06-25 Story Fix Loop 13
- [x] Confirm `DISC-002` quick presets with connected evidence instead of another source-text
  assertion.
- [x] Drive the real Discover Safe preset chip through `HomeActivity` and verify preset sources are
  inserted or re-enabled in the app database.
- [x] Verify the active profile is persisted as Safe and an immediate hosts update work item is
  enqueued for the preset change.
- [x] Keep production code unchanged if the connected behavior already passes; fix only if the
  instrumented proof exposes a real behavior gap.
- [x] Update `tasks/user-story-status.tsv` with the connected quick-preset evidence and remaining
  device/manual gaps.
- [x] Run focused connected/test compile gates plus the standard local gates, then commit and push
  if green.

## Review - 2026-06-25 Story Fix Loop 13
- Confirmed `DISC-002` had source-text unit coverage but no connected test for the real Discover
  chip path.
- Added `DiscoverQuickPresetInstrumentedTest` to launch `HomeActivity`, navigate to Discover, tap
  the Safe preset chip, and verify preset sources, active Safe profile persistence, and immediate
  source-update work enqueue.
- Kept production code unchanged because this slice is proof-chain coverage unless CI/device
  execution exposes a real behavior failure.
- Updated `tasks/user-story-status.tsv` conservatively: android-test compile passed locally, while
  actual connected execution remains pending until PR CI runs because no local Android device is
  attached.
- Focused local compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed source added-line length scanning
  passed.
- Local connected execution was not attempted because the configured SDK `adb.exe devices` reported
  no attached devices; PR CI remains the connected-device gate for this new test.
- PR CI connected execution then passed on commit `c6cc86bc`: Connected Android tests, Development
  build, CodeQL, locale validation, and Java/C++ analysis were all green.

## Plan - 2026-06-25 Story Fix Loop 14
- [x] Confirm `DISC-006` compatibility gating at the integration boundary, not only in parser
  unit tests.
- [x] Add a scoped bulk-subscribe worker proof that unsupported browser-rule FilterLists entries
  are skipped before detail fetch, source insertion, or update enqueue side effects.
- [x] Keep production code unchanged if the behavior already holds; fix only a real behavior gap.
- [x] Update `tasks/user-story-status.tsv` with the connected/integration evidence and remaining
  manual Discover review gap.
- [x] Run focused worker/android-test compile gates plus the standard local gates, then commit and
  push if green.

## Review - 2026-06-25 Story Fix Loop 14
- Confirmed `DISC-006` still had parser/unit coverage but needed a worker/database boundary proof.
- Added `FilterListsSubscribeAllWorkerDoWorkTest.
  doWork_skipsUnsupportedScopedRowsWithoutFetchInsertOrUpdate` for a scoped unsupported
  browser-rule list that has a downloadable URL available in the fake directory.
- The test proves unsupported bulk candidates are not detail-fetched, not inserted into
  `hosts_sources`, and recorded for manual review.
- Found and fixed one real no-op side effect: a subscribe-all run enqueued immediate source update
  work even when it subscribed zero new sources.
- Updated the worker to call `enqueueUpdateNow` only when `recorder.getSubscribed() > 0`.
- Updated `tasks/user-story-status.tsv` for `DISC-006`; PR connected execution and full manual
  Discover visual review remain open.
- Focused android-test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Focused unit gate passed when run alone:
  `:app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterListsSubscribeAllWorkerTest --tests
  org.adaway.model.source.FilterListCompatibilityTest --dependency-verification=strict
  --stacktrace`.
- A parallel Gradle attempt produced transient missing-package compile errors while another Gradle
  process compiled the same variant; the same unit gate passed when rerun alone.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed source added-line length scanning
  passed.
- Local connected execution was not attempted because `adb devices` reported no attached devices;
  PR CI remains the connected-device gate for the new worker test.
- PR CI then passed on commit `32fde642`: Connected Android tests, Development build, CodeQL,
  locale validation, and Java/C++ analysis were all green.

## Plan - 2026-06-25 Story Fix Loop 15
- [x] Confirm `RUNTIME-003` with a real mixed network update: one changed HTTP source, one
  HTTP 304 source, and one failed HTTP source with previous active rows.
- [x] Prove the full-update pipeline activates only a complete generation containing changed rows
  plus carried-forward 304 and failed-source rows.
- [x] Verify failed-source metadata is recorded without losing active coverage or stale rows.
- [x] Keep production code unchanged if the behavior already holds; fix only a concrete runtime
  truth gap exposed by the test.
- [x] Update `tasks/user-story-status.tsv` with the integration evidence and remaining runtime
  gates.
- [x] Run focused android-test compile/full local gates, then commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 15
- Confirmed `RUNTIME-003` had direct file-success/failure coverage and direct `200 + 304`
  coverage, but no connected proof for a single network update mixing changed, unchanged, and
  failed enabled sources.
- Added `SourceModelHttpConditionalTest.
  checkAndRetrieveHostsSources_mixed200304AndFailurePreservesCoverage`.
- The test sets up three active HTTPS sources, returns `200` for one, `304` for one, and `500` for
  one, then verifies the new active generation contains the changed source row plus carried-forward
  rows for the unchanged and failed sources.
- The test also verifies stale changed-source rows are absent, failed-source metadata is recorded,
  successful source errors are cleared, source sizes remain consistent, and the scratch dedupe table
  is cleaned up.
- No production code changed in this slice because the focused proof compiles against the existing
  update pipeline; connected PR execution will decide whether a production patch is needed.
- Focused android-test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- License-boundary check passed; `git diff --check` passed with only LF-to-CRLF warnings; TSV
  shape check passed as 98 stories with 15 columns; and changed source added-line length scanning
  passed.
- Local connected execution was not attempted because `adb devices` reported no attached devices;
  PR CI remains the connected-device gate for the new mixed update test.
- PR connected execution failed on commit `88f69910` at
  `SourceModelHttpConditionalTest.
  checkAndRetrieveHostsSources_mixed200304AndFailurePreservesCoverage`: the failed source had zero
  rows in the activated staging generation after cleanup.
- Patched `SourceModel.carryForwardPreviousGeneration` so a full update falls back to a direct
  generation copy when SQL dedupe carry-forward produces no target rows despite prior active
  coverage.
- Focused post-fix gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Full post-fix local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Post-fix license-boundary check passed; `git diff --check` passed with only LF-to-CRLF
  warnings; TSV shape check passed as 98 stories with 15 columns; and changed source added-line
  length scanning passed.
- PR connected execution still failed on commit `ffeb48a0` at the same assertion, proving the
  SQL-dedupe fallback was not reached for the failed source.
- Split failed-source carry-forward from 304 carry-forward: failed sources now direct-copy previous
  active rows, while 304 sources continue through the SQL dedupe carry-forward path.
- Updated `Generation304MigrationTest` to guard the split so future refactors do not put failed
  sources back on the dedupe path.
- Focused post-split gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Full post-split local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Post-split license-boundary check passed; `git diff --check` passed with only LF-to-CRLF
  warnings; TSV shape check passed as 98 stories with 15 columns; and changed source added-line
  length scanning passed.
- PR connected execution still failed on commit `8b4a6502` at the same assertion. Root cause:
  `downloadToTempFile` accepted HTTP 500 responses as parseable success whenever a response body
  existed, so the failed source became a successful empty parse and bypassed failed-source
  carry-forward.
- Fixed HTTP status handling so HTTP 304 is the only non-2xx response that skips parsing and
  all HTTP 4xx/5xx responses enter the failed-source carry-forward path. Added
  `Generation304MigrationTest.sourceModel_treatsNonSuccessfulHttpStatusAsDownloadFailure`.
- Focused status-code gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.Generation304MigrationTest
  --dependency-verification=strict --stacktrace`.
- Full post-status-code local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on commit `b1d0b99c`: Connected Android tests, Development build, CodeQL,
  locale validation, Java analysis, and C++ analysis were all green.

## Plan - 2026-06-25 Story Fix Loop 16
- [x] Confirm `RUNTIME-004` with a connected mutation-matrix proof instead of another
  source-text assertion.
- [x] Drive `SourceModel.syncHostEntries()` through user allow insert/delete and source
  disable/enable mutations while active, stale, and future source-generation rows coexist.
- [x] Verify runtime resolution, materialized runtime rows, root export rows, and stats all use
  user rows plus active-generation enabled source rows only after each mutation.
- [x] Keep production code unchanged if the connected behavior already holds; fix only a concrete
  runtime truth gap exposed by the test.
- [x] Update `tasks/user-story-status.tsv` with the new evidence and remaining hardware/runtime
  gaps.
- [x] Run focused connected/test compile gates plus the standard local gates, then commit, push,
  and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 16
- Confirmed `RUNTIME-004` already had direct DAO active-generation and Domain Checker runtime
  truth coverage, but lacked a connected mutation-matrix proof through the public
  `SourceModel.syncHostEntries()` refresh path.
- Added `SourceModelRuntimeTruthMutationTest.
  syncHostEntries_keepsActiveTruthAcrossUserAndSourceMutations`.
- The test sets active generation `2`, seeds active, stale, and future source rows, then verifies
  runtime rows, root export rows, and stats after initial sync, user allow insert, user allow
  delete, source disable, and source re-enable.
- No production code changed before connected execution; the new behavior proof compiled against
  the existing runtime truth path.
- Focused android-test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Local connected execution was not attempted because
  `C:\Users\solun\AppData\Local\Android\Sdk\platform-tools\adb.exe devices` reported no attached
  devices; PR CI remains the connected-device gate for this test.
- PR CI passed on commit `52188300`: Connected Android tests, Development build, CodeQL,
  locale validation, Java analysis, and C++ analysis were all green.

## Plan - 2026-06-25 Story Fix Loop 17
- [x] Tighten `RUNTIME-005` with parser behavior tests for DNS root-dot normalization and
  unsafe Unbound local-zone types.
- [x] Prove the tests fail before changing production code.
- [x] Fix only the concrete parser gap exposed by the tests.
- [x] Run the focused parser gate, the standard JVM gate, and story-ledger hygiene checks.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 17
- Starting state: `RUNTIME-005` remained `Partially covered` with `Needs expanded parser
  matrix`, while PR #6 was green on head `8c523228`.
- The next parser proof targets DNS-list fidelity rather than broad refactoring: valid FQDN
  trailing dots should normalize before storage, and Unbound local-zone types that resolve
  normally or only log should not be flattened into blocking rules.
- Red parser gate failed before production changes:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --stacktrace` reported 62 tests, 3 failures for Unbound
  trailing-root-dot import, Unbound `inform` skip, and RPZ trailing-root-dot import.
- After the first fix, a second red parser gate failed with 64 tests, 2 failures for unanchored
  ABP path/options false positives: `example.com$third-party` and `example.com/path/ad.js`.
- Fixed `SourceLoader` to strip one DNS root dot before hostname validation, accept only
  block-safe Unbound `always_*` zone types, and reject unanchored path/options syntax before the
  plain-domain sanitizer can truncate it into a DNS block.
- Forced focused parser gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --rerun-tasks --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Remaining `RUNTIME-005` gaps stay open: parse-to-DB semantic proof, redirect-enabled source
  fallback behavior, Unbound `local-data` target safety, and broader dnsmasq formatting coverage.
- PR CI passed on commit `f0848cd0`: Connected Android tests, Development build, CodeQL,
  locale validation, Java analysis, and C++ analysis were all green.

## Plan - 2026-06-25 Story Fix Loop 18
- [x] Tighten `RUNTIME-005` with parser behavior tests for Unbound `local-data` target safety.
- [x] Prove non-null Unbound `local-data` records fail before changing production code.
- [x] Fix only the concrete `local-data` target gap exposed by the tests.
- [x] Run the focused parser gate and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 18
- Starting state: `RUNTIME-005` remained `Partially covered`; the row still listed Unbound
  `local-data` target safety as an open parser semantics gap.
- Added behavior tests for Unbound `local-data`: `A 0.0.0.0` and `AAAA ::` remain exact blocks,
  while public and private address answers are skipped instead of flattened into DNS blocks.
- Red parser gate failed before production changes:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --stacktrace` reported 68 tests, 2 failures for public and
  private Unbound `local-data` answers being extracted as blocks.
- Fixed `SourceLoader` so Unbound `local-data` captures record type and target, then imports only
  block-safe A/AAAA null-style targets while skipping redirect-style address answers.
- Focused parser gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `f613ec6a`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining `RUNTIME-005` gaps stay open: parse-to-DB semantic proof, redirect-enabled source
  fallback behavior, and broader dnsmasq formatting coverage.

## Plan - 2026-06-25 Story Fix Loop 19
- [x] Tighten `RUNTIME-005` with a connected parse-to-DB semantic proof for extracted
  `RuleKind`, skip behavior, generation, and root export staging.
- [x] Investigate redirect-enabled source fallback behavior for hosts-shaped DNS syntaxes.
- [x] Fix only the concrete redirect-enabled RPZ fallback bug.
- [x] Run the focused parser gate and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 19
- Starting state: `RUNTIME-005` remained `Partially covered`; the remaining row gaps were
  parse-to-DB semantic proof, redirect-enabled source fallback behavior, and broader dnsmasq
  formatting coverage.
- Added `SourceLoaderDatabaseSemanticsTest` to drive mixed exact, ABP suffix, dnsmasq suffix,
  dnsmasq local suffix, and Unbound local-data rules through `SourceLoader.parse(...)` with
  the raw SQLite/`SqlUpdateDeduper` fast path, then assert persisted `hosts_lists.kind`,
  generation, skipped unsafe rows, and `root_host_entries_stage` output.
- Explorer review found that `HostsSource.redirectEnabled` could misroute hosts-shaped DNS
  syntaxes such as `ads.example.com CNAME .` through the redirect branch and drop them after
  invalid redirect validation.
- Fixed `SourceLoader` so hosts-shaped lines with a non-IP first token fall back to
  block-safe non-hosts syntax extraction before redirect handling.
- Local connected execution was not attempted because
  `C:\Users\solun\AppData\Local\Android\Sdk\platform-tools\adb.exe devices` reported no attached
  devices; PR CI remains the connected-device gate for the new instrumentation test.
- A parallel Gradle probe produced one noisy `compileDebugJavaWithJavac` failure while a concurrent
  android-test compile succeeded; serial reruns are the authoritative local evidence.
- Focused parser gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR connected CI failed on `884217c9` because the test fixture reused a unique
  `hosts_sources.url`; `HostsSourceDao.insert(...)` ignored the second source and the parser
  correctly hit a `hosts_lists.source_id` foreign-key failure. The fixture now assigns a unique
  URL per source id.
- PR CI passed on `0bad5e05`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining `RUNTIME-005` gap stays open: broader dnsmasq formatting coverage.

## Plan - 2026-06-25 Story Fix Loop 20
- [x] Tighten `RUNTIME-005` with dnsmasq formatting and safety parser matrix tests.
- [x] Prove inline-comment `local=/.../` dnsmasq rules fail before changing production code.
- [x] Fix only the concrete dnsmasq local inline-comment parsing gap exposed by the tests.
- [x] Run the focused parser gate and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 20
- Starting state: `RUNTIME-005` remained `Partially covered`; its only remaining tracked gap was
  broader dnsmasq formatting coverage.
- Added dnsmasq parser tests for null-address inline comments, trailing-root-dot normalization,
  leading-dot normalization, `local=/domain` without a trailing slash, `local=/domain[/]`
  inline comments, private/loopback/empty-target redirect skips, and malformed multi-domain
  `address=` shapes.
- Red parser gate failed before production changes:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --stacktrace` reported 82 tests, 2 failures for
  `local=/example.com/ # comment` and `local=/example.com # comment`.
- Fixed `SourceLoader` so dnsmasq domain captures reject whitespace/comment-contaminated tokens
  and `local=/domain[/]` accepts trailing inline comments.
- Focused parser gate passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.SourceLoaderParserPatternsTest
  --dependency-verification=strict --stacktrace`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `5a3fb656`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- No remaining `RUNTIME-005` parser matrix gap is tracked locally.

## Plan - 2026-06-25 Story Fix Loop 21
- [x] Tighten `RUNTIME-006` by proving suffix rules block through the actual VPN DNS packet
  path, not only `VpnModel.getEntry`.
- [x] Seed a runtime suffix rule in connected test setup and feed `DnsPacketProxy` a synthetic
  A-query for a child domain.
- [x] Verify a suffix-blocked DNS request returns a local blocked response and does not forward
  upstream.
- [x] Run the focused connected test compile/test gate and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 21
- Starting state: `RUNTIME-006` is `Partially covered`; the canonical row still tracks
  `Needs VPN suffix lookup test`.
- Added `DnsPacketProxyRuntimeTruthTest`, which seeds a suffix-only blocked rule, feeds
  `DnsPacketProxy` a synthetic A-query for a child domain, and verifies the proxy writes a
  local blocked DNS response without forwarding upstream.
- No production defect was found; existing runtime suffix matching behavior passed through the
  actual VPN DNS packet path.
- Focused android-test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- First focused connected run was blocked by `No connected devices`; booted local AVD
  `adaway-api34` and reran the focused class successfully:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.dns.DnsPacketProxyRuntimeTruthTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `9eeb441f`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.

## Plan - 2026-06-25 Story Fix Loop 22
- [x] Tighten `RUNTIME-011` by proving DNS packet enforcement through `DnsPacketProxy`,
  not only runtime cache lookup.
- [x] Add packet-level connected coverage for exact blocked domains, allowed/default
  forwarding, and exact redirected domains.
- [x] Keep `RUNTIME-007` root hosts apply open until a rooted-device smoke proves the shell
  apply path; the local emulator exposes `su`, but standard `su -c` is not usable as-is.
- [x] Run the focused connected DNS proxy test and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 22
- Starting state: `RUNTIME-011` was `Partially covered`; the canonical row tracked
  `Needs packet-level integration test`.
- Extended `DnsPacketProxyRuntimeTruthTest` so packet-level connected coverage now verifies:
  suffix block, exact block, default allowed forwarding to the mapped upstream DNS server, and
  exact redirect synthesis with an A record.
- No production defect was found; existing DNS packet enforcement behavior matched runtime truth
  once exercised through the real `DnsPacketProxy` path.
- `RUNTIME-007` remains open. Local ADB sees `emulator-5554` and `/system/xbin/su`, but
  standard `su -c` fails with `invalid uid/gid '-c'`, so this environment is not accepted as a
  rooted hosts-file apply smoke.
- Focused android-test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Focused connected DNS proxy test passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.dns.DnsPacketProxyRuntimeTruthTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 4 tests on
  `adaway-api34`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `7da85aed`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.

## Plan - 2026-06-25 Story Fix Loop 23
- [x] Tighten `RUNTIME-008` by proving the `VpnWorker` packet-routing seam, not only
  `DnsPacketProxy` behavior.
- [x] Write a red unit test for tunnel-read-to-DNS-handler forwarding and queued tunnel writes.
- [x] Extract the smallest package-private packet processor from `VpnWorker` without changing
  public VPN behavior.
- [x] Run the focused worker test, focused DNS proxy connected test, and standard local Gradle
  gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence,
  preserving any remaining Android VPN consent/TUN-device smoke gap.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 23
- Starting state: `RUNTIME-008` was `Partially covered`; its canonical row still tracked
  `Needs connected VPN test`.
- Added a red unit test for the missing worker packet-routing seam. The first focused run failed
  at compile time because `VpnPacketProcessor` did not exist.
- Extracted `VpnPacketProcessor` from `VpnWorker` so tunnel reads forward the exact read bytes to
  the DNS handler and packet monitor, and queued proxy responses are drained back to the tunnel
  output stream in order.
- No public VPN behavior was intentionally changed; `VpnWorker` still owns lifecycle, socket
  forwarding, DNS query queueing, watchdog, and VPN interface establishment.
- Focused worker unit gate passed:
  `:app:testDebugUnitTest --tests org.adaway.vpn.worker.*
  --dependency-verification=strict --stacktrace`.
- Focused connected DNS proxy gate passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.dns.DnsPacketProxyRuntimeTruthTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 4 tests on
  `adaway-api34`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `de5ec702`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining gap: this still does not prove a real user-granted Android VPN interface and TUN fd;
  `RUNTIME-008` remains partially covered until that device smoke exists.

## Plan - 2026-06-25 Story Fix Loop 24
- [x] Tighten `RUNTIME-000` by proving runtime method dispatch for undefined, root, and VPN
  methods through `AdAwayApplication.getAdBlockModel()`.
- [x] Verify switching the stored method invalidates the cached app-level ad-block model.
- [x] Run the focused connected dispatch test and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 24
- Starting state: `RUNTIME-000` was `Not tested`; the canonical row tracked
  `Needs root and VPN mode coverage`.
- Added `AdBlockModelDispatchTest`, a connected instrumentation test that verifies
  `AdAwayApplication.getAdBlockModel()` dispatches `UNDEFINED`, `ROOT`, and `VPN` preferences
  to the corresponding model classes and rebuilds the cached app-level model after the stored
  method changes.
- No production defect was found; existing dispatch behavior matched the expected runtime method
  model.
- Focused connected dispatch test passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.adblocking.AdBlockModelDispatchTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Full local Gradle gate passed:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `c8d9fadb`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining runtime smokes are still tracked separately: `RUNTIME-007` for rooted hosts apply
  and `RUNTIME-008` for user-granted Android VPN/TUN behavior.

## Plan - 2026-06-25 Story Fix Loop 25
- [x] Tighten `SYS-002` by proving boot restore behavior instead of only source-text
  receiver wiring.
- [x] Add focused connected coverage for BOOT_COMPLETED dispatch, VPN-on-boot disabled,
  non-VPN method, VPN permission-required, and VPN permission-granted branches.
- [x] Fix the receiver so a missing VPN permission requests permission and does not also
  start the service in the same boot callback.
- [x] Run the focused connected boot-restore test and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-25 Story Fix Loop 25
- Starting state: `SYS-002` was the only P0 story still marked `Not tested`; its canonical row
  tracked `Needs integration pass`.
- Added `BootReceiverBehaviorTest`, a focused connected test that proves BOOT_COMPLETED dispatch,
  non-boot intent ignoring, VPN-on-boot disabled behavior, non-VPN method behavior, the
  permission-required branch, and the permission-granted branch with fake VPN platform hooks.
- The red run failed at compile time because the receiver did not expose a testable boot-restore
  controller seam.
- Extracted `BootRestoreController` from `BootReceiver`. The production behavior change is that
  a missing VPN permission now starts the permission activity with `FLAG_ACTIVITY_NEW_TASK` and
  returns without also starting the VPN service in the same boot callback.
- Updated the stale crash-surface source contract so it follows the new controller and asserts the
  permission request returns before service start.
- Focused connected boot-restore gate passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.broadcast.BootReceiverBehaviorTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 5 tests on
  `adaway-api34`.
- A combined standard gate run under the default shell JDK timed out and left Gradle Java
  processes alive; those processes were inspected and stopped before rerunning.
- Standard local gates passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `:app:testDebugUnitTest --dependency-verification=strict --stacktrace` and
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- PR CI passed on `2b0c89cf`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining boundary: this proves receiver behavior directly, not a physical device reboot smoke;
  physical reboot remains part of release smoke coverage.

## Plan - 2026-06-26 Story Fix Loop 26
- [x] Tighten `LIST-001` with a functional connected test for the blocked, allowed, and
  redirected Your Lists tabs.
- [x] Seed one dedicated test row per list type in the production app database and verify each
  requested tab selects the matching bottom-navigation item.
- [x] Verify each tab renders only its own rule row, including redirected-host subtext.
- [x] Patch production only if the functional test exposes a real tab/data routing defect.
- [x] Run the focused connected list-tab test and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and watch PR CI.

## Review - 2026-06-26 Story Fix Loop 26
- Starting state: `LIST-001` was `Partially covered`; its canonical row tracked
  `Needs functional tab test`.
- Added `ListsTabsInstrumentedTest`, a focused connected test that seeds one dedicated blocked,
  allowed, and redirected row into the production app database, launches `ListsActivity` with each
  tab extra, and verifies the selected bottom-navigation item, fragment class, visible host row,
  and redirected IP subtext.
- The focused test passed on the first run, so no production tab/data-routing defect was found and
  no production code was changed for this slice.
- Focused connected list-tab gate passed by XML evidence:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsTabsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` produced
  `tests="1" failures="0" errors="0"` for `ListsTabsInstrumentedTest` on `adaway-api34`.
- Standard local gate passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- PR CI passed on `10519abb`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining related flows are tracked separately: `LIST-003` add user rule, `LIST-004` edit/delete,
  `LIST-005` toggle enabled state, and `LIST-006` downloaded-rule override.

## Plan - 2026-06-26 Story Fix Loop 27
- [x] Debug the branch-tip connected Android CI failure on `ccf571d2` before guessing at a fix.
- [x] Pull CI logs/artifacts and classify the failure as test fixture state leakage, production
  crash, native crash, ANR, or emulator noise.
- [x] Make `ListsTabsInstrumentedTest` deterministic under the full connected suite without
  changing production list behavior.
- [x] Rerun the focused connected list-tab test and standard local Gradle gate.
- [x] Rerun the full local connected Android suite that failed in CI.
- [x] Push the fix and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 27
- The failed PR check was `Connected Android tests` on `ccf571d2`. The log showed one failure:
  `ListsTabsInstrumentedTest` timed out waiting for `000-list-blocked-ui.invalid` in tab 0; no
  app crash, native crash, or ANR was present in the failure evidence.
- Root cause: the first version seeded the production app database and waited for the row to be
  attached as a visible `TextView`. That passed focused, but full-suite state can leave earlier
  sorting rows in the shared app DB, so the seeded row may be present but off screen.
- Fixed the connected test to own a private WAL Room database through the existing
  `AppDatabase` singleton seam, seed `hosts_meta`/`hosts_stats`, assert each seeded row is visible
  to the runtime list query, and restore the previous singleton in teardown.
- Focused connected list-tab gate passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsTabsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 135
  tests on `adaway-api34` with 3 skipped and 0 failed.
- PR CI recheck on `b3923958` moved past the list-tab failure and exposed a separate Discover
  `UxDeviceMatrixTest` ellipsized-text failure, handled in Story Fix Loop 28.

## Plan - 2026-06-26 Story Fix Loop 28
- [x] Debug the branch-tip connected Android CI failure on `b3923958` before changing Discover UI.
- [x] Confirm whether the failure is a production crash, native crash, ANR, emulator noise, or a
  deterministic UX/accessibility issue.
- [x] Patch the smallest production layout surface that caused the failure.
- [x] Update the stale unit contract after CI's clean run exposed the old three-line cap assertion.
- [x] Rerun the focused UX matrix connected test.
- [x] Rerun the standard local Gradle gate and full local connected Android suite.
- [x] Superseded by Story Fix Loop 30 push and passing PR CI.

## Review - 2026-06-26 Story Fix Loop 28
- The failed PR check was `Connected Android tests` on `b3923958`. The prior list-tab failure was
  gone; the new failure was `UxDeviceMatrixTest` reporting ellipsized `filterlistsItemDesc` text on
  the Discover screen.
- Root cause: FilterLists descriptions now include capability summaries, but
  `filterlists_import_item.xml` still capped the description to three lines with `ellipsize=end`.
  Long third-party descriptions could therefore be clipped in the directory UI.
- Fixed the FilterLists row layout to let description and capability text wrap fully instead of
  truncating.
- The first pushed fix exposed a clean-run unit-test failure:
  `DiscoverPresetSubscriptionTest.filterListsRowsExposeCapabilityDisclosure` still asserted the
  old `android:maxLines="3"` contract. Forced local rerun reproduced it, and the contract now
  asserts that `filterlistsItemDesc` has neither `android:maxLines` nor `android:ellipsize`.
- CI-equivalent unit gate passed after the contract update:
  `test --dependency-verification=strict --stacktrace --rerun-tasks`.
- Focused UX matrix gate passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.UxDeviceMatrixTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 135
  tests on `adaway-api34` with 3 skipped and 0 failed.
- PR CI recheck is pending until this fix is pushed.

## Plan - 2026-06-26 Story Fix Loop 29
- [x] Re-ground `DISC-005` from the canonical story spreadsheet and current Discover
  FilterLists implementation.
- [x] Add a connected user-path proof for directory search, tag filtering, language filtering,
  DNS-safe compatibility filtering, and no-match state.
- [x] Seed deterministic cached FilterLists directory data so the test does not depend on the
  live FilterLists.com API.
- [x] Run the focused connected `DISC-005` test and patch production only if it exposes a real
  behavior defect.
- [x] Run the standard local Gradle gate and full connected suite.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 29
- Starting state: `DISC-005` was `Partially covered`; its canonical row tracked
  `Needs UX flow test`.
- Added `DiscoverFilterListsFiltersInstrumentedTest`, a connected test that launches Discover,
  uses cached FilterLists directory data, then drives the real search field, Regional tag chip,
  English language spinner, DNS-safe-only switch, and no-match search state.
- The focused connected test passed on the first run, so no production code was changed for this
  slice.
- Focused connected `DISC-005` gate passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.discover.DiscoverFilterListsFiltersInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 136
  tests on `adaway-api34` with 3 skipped and 0 failed.
- PR CI passed on `29324001`: Connected Android tests, Development build, CodeQL Java/C++
  analysis, and locale validation all reported success.
- Remaining boundary: this proves the control behavior and visible-row scope on device; broader
  Discover visual coverage across device sizes remains part of the UX matrix/release sweep.

## Plan - 2026-06-26 Story Fix Loop 30
- [x] Re-ground `DISC-009` from the canonical story spreadsheet and current Discover/worker code.
- [x] Confirm existing coverage boundary: worker cancellation internals are covered, but the
  Discover retry/progress/cancel/review affordance path lacks a device-level proof.
- [x] Add a connected user-path proof that seeds a last-run ledger, starts retry from Discover,
  observes a running bulk job, cancels it, and verifies durable stopping/cancelled UI feedback.
- [x] Patch production only if the focused proof exposes a real UX/logistical defect.
- [x] Run the focused connected `DISC-009` test, the standard local Gradle gate, and full connected
  suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 30
- Starting state: `DISC-009` was `Partially covered`; its canonical row tracked
  `Needs long-run cancel test`.
- Added `FilterListsBulkUiInstrumentedTest`, a connected test that launches the real Home shell,
  navigates to Discover, seeds cached FilterLists last-run review data, verifies review/retry/
  unsupported actions, starts a retry through the UI, observes a running worker, cancels it, and
  verifies the cancelled status plus no source insertion or update enqueue.
- The first two red runs exposed over-specific test expectations: the progress label can
  legitimately skip from `Preparing` to numeric progress, and the explicit `Stopping` text is a
  transient click-handler state before WorkManager reports `CANCELLED`.
- The full connected suite then exposed a real race: after cancellation, a detail request could
  resolve before the worker observed `isStopped()`, allowing the source insert and follow-up update
  enqueue to happen anyway.
- Fixed the worker by re-checking `isStopped()` after cached URL resolution and after `future.get()`
  returns a resolved detail, before any cache write, source record, or update enqueue side effect.
- Focused connected `DISC-009` gate passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsBulkUiInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 137
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Commit `6a14637e` was pushed and PR #6 CI passed: Development build, Connected Android tests,
  locale validation, CodeQL Java/C++ analysis, and CodeQL status all reported success.

## Plan - 2026-06-26 Story Fix Loop 31
- [x] Re-ground `DISC-010` from the canonical story spreadsheet and current Discover
  FilterLists implementation.
- [x] Confirm existing coverage boundary: source-text contracts cover unsupported-review wiring,
  but no connected test drives the actual unsupported row/review/manual-add user path.
- [x] Add a connected user-path proof that uses deterministic FilterLists API responses, opens an
  unsupported row review, verifies compatibility disclosure, and confirms manual add opens the
  source editor with the resolved URL.
- [x] Patch production only if the focused proof exposes a real UX/logistical defect.
- [x] Run the focused connected `DISC-010` test, standard local Gradle gate, and full connected
  suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 31
- Starting state: `DISC-010` was `Partially covered`; its canonical row tracked
  `Needs manual dialog test`.
- Added `DiscoverUnsupportedReviewInstrumentedTest`, a connected test that launches the real Home
  shell, navigates to Discover, loads deterministic unsupported FilterLists data, opens the
  unsupported review dialog, verifies the DNS capability disclosure and skipped browser-rule
  semantics, then taps `Add manually` and asserts `SourceEditActivity` receives the resolved label,
  URL, FilterLists ID, name, and selected URL extras.
- The first focused test passed, but the full connected suite exposed a test isolation problem:
  earlier Discover directory work could leave the fragment showing the live directory instead of
  the test-owned row. The test now reseeds the cached directory after Discover is active and
  invokes the existing retry/load action before applying the unique search filter.
- The same full-suite loop also exposed a real `DISC-009` cancellation race: `cancelUniqueWork()`
  can lag behind a resolved detail result, allowing source insertion and follow-up update enqueue
  after the user taps cancel. `DiscoverFilterListsFragment` now writes a synchronous cancel marker
  before calling WorkManager, and `FilterListsSubscribeAllWorker` checks that marker anywhere it
  previously relied only on `isStopped()`.
- Focused affected connected gate passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.discover.DiscoverUnsupportedReviewInstrumentedTest,org.adaway.ui.hosts.FilterListsBulkUiInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Final focused `DISC-010` connected retest passed:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.discover.DiscoverUnsupportedReviewInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 138
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Remaining boundary: the manual-add path is proven for an unsupported row with a usable resolved
  URL; no-URL unsupported review and broader visual matrix coverage remain separate stories.
- Commit `b4e756ca` was pushed and PR #6 CI passed: Development build, Connected Android tests,
  locale validation, CodeQL Java/C++ analysis, and CodeQL status all reported success.

## Plan - 2026-06-26 Story Fix Loop 32
- [x] Re-ground `DISC-007` and `DISC-008` from the canonical story spreadsheet and current
  Discover bulk-action implementation.
- [x] Confirm existing coverage boundary: worker and source-text contracts cover scope and durable
  metadata, but no connected test drives visible bulk subscribe followed by visible bulk remove.
- [x] Add a connected user-path proof that filters the directory, confirms the visible subscribe
  dialog count, subscribes only the compatible visible row, and leaves hidden/unsupported rows
  untouched.
- [x] Extend the same proof to remove only the visible FilterLists source while preserving a
  hidden subscribed FilterLists source.
- [x] Patch production only if the focused proof exposes a real UX/logistical defect.
- [x] Run the focused connected bulk visible-actions test, standard local Gradle gate, and full
  connected suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 32
- Starting state: `DISC-007` and `DISC-008` were `Partially covered`; their canonical rows
  tracked missing device proof for filtered visible subscribe and visible remove.
- Added `FilterListsVisibleBulkActionsInstrumentedTest`, a connected test that launches the real
  Home shell, navigates to Discover, loads deterministic cached FilterLists data, filters to a
  visible scope containing one DNS-safe list and one unsupported browser-rule list, and confirms
  the bulk subscribe dialog counts only the DNS-safe row.
- The test then taps `Subscribe`, verifies only the compatible visible source is inserted with
  durable FilterLists ID/name/selected-URL metadata, verifies the unsupported visible row is not
  inserted, verifies the hidden subscribed source is still present, and confirms the worker only
  resolved the visible DNS-safe list before enqueueing one update.
- The same test taps `Remove visible`, confirms the destructive dialog, and proves only the
  visible source is removed while the hidden subscribed FilterLists source remains.
- The first focused run failed in the test harness, not production behavior: the accessibility click
  helper matched the dialog title containing `Subscribe` before the actual button. The helper now
  uses exact text for dialog action buttons.
- No production code was changed in this loop; the connected proof verified existing behavior.
- Focused connected visible bulk-actions gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsVisibleBulkActionsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 139
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Committed and pushed as `bdb318d8`; PR CI passed all required checks on the pushed head.
- Remaining boundary: this proves filtered visible subscribe/remove; all-sources destructive remove
  remains covered by source contracts and should get its own device proof if prioritized.

## Plan - 2026-06-26 Story Fix Loop 33
- [x] Re-ground `NAV-001` from the canonical story spreadsheet and current Home navigation code.
- [x] Add a connected user-path proof that launches `HomeActivity` with
  `HomeActivity.EXTRA_NAV_DISCOVER=true` and asserts the selected bottom tab and fragment are
  Discover without a manual post-launch navigation call.
- [x] Add a connected Home no-source CTA proof that waits for the visible Browse Filter Lists CTA,
  taps it, and asserts the same Discover destination.
- [x] Patch production only if the focused proof exposes a real navigation defect.
- [x] Run the focused connected direct-entry test, standard local Gradle gate, and full connected
  suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 33
- Starting state: `NAV-001` is `Partially covered`; source-text contracts and UX matrix coverage
  exist, but the launch-intent path has no direct connected assertion.
- Added `HomeDiscoverDeepEntryInstrumentedTest`, a connected test that launches the real
  `HomeActivity` shell with `HomeActivity.EXTRA_NAV_DISCOVER=true` and asserts `nav_discover`,
  the `DiscoverFragment`, and visible Discover content without calling `navigateTo()` after launch.
- Added a second connected proof for the Home no-source CTA: the test clears external sources from
  the launched app context on disk IO, waits for the visible `discoverCta`, taps it, and asserts the
  same Discover destination.
- The first focused run exposed a test harness issue, not production behavior: directly invoking
  `HomeActivity.onNewIntent()` changed `Activity.getIntent()`, causing `ActivityScenario.close()`
  to ignore the destroy lifecycle because the activity intent no longer matched the launch intent.
- The second focused run exposed a setup issue: deleting sources before the activity launched did
  not create the no-source UI state because the target app context seeded default sources at
  startup. The final test now clears sources inside the launched activity context.
- No production code was changed in this loop; the connected proofs verified existing behavior.
- Focused connected deep-entry gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeDiscoverDeepEntryInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 141
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Committed and pushed as `2bfd8052`; PR CI passed all required checks on the pushed head.
- Remaining boundary: FilterLists subscription progress text is still source-level covered as a
  Discover navigation entry point; this slice proves the launch extra and Home no-source CTA.

## Plan - 2026-06-26 Story Fix Loop 34
- [x] Re-ground `HOME-004` and `HOME-005` from the canonical story spreadsheet and current Home
  counter bindings.
- [x] Add a connected Home user-path proof that seeds known blocked, allowed, redirected,
  up-to-date, and outdated source counts inside the launched app context.
- [x] Assert the visible Home counter text matches the seeded runtime/source truth.
- [x] Patch production only if the focused proof exposes a real counter or freshness defect.
- [x] Run the focused connected Home counters test, standard local Gradle gate, and full connected
  suite if the test mutates shared app data.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 34
- Starting state: `HOME-004` is `Partially covered` with no visual/data device proof, and
  `HOME-005` is `Needs connected data test`.
- Added `HomeCountersInstrumentedTest`, a connected test that launches the real Home shell, seeds
  known `hosts_lists`, `hosts_sources`, active generation, and runtime `host_entries` state through
  the launched app context, then asserts visible blocked, allowed, redirected, up-to-date, and
  outdated Home counters.
- The seeded proof drives production `hostEntryDao.sync()` before asserting the UI, so the blocked
  count follows the same runtime truth path that root-mode Home uses.
- No production code was changed in this loop; the connected proof verified existing post-sync
  counter and freshness behavior.
- Focused connected Home counters gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeCountersInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 142
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Committed and pushed as `2753f2e2`; PR CI passed all required checks on the pushed head.
- Remaining boundary: `HOME-004` still needs a focused active-operation proof for the counter-freeze
  behavior during an in-progress update.

## Plan - 2026-06-26 Story Fix Loop 35
- [x] Re-ground `HOME-004` from the canonical story spreadsheet and current Home counter binding.
- [x] Add a connected active-update proof that drives the real Home UI through an injected shared
  `FilterOperationState` while database counters change underneath it.
- [x] Assert visible blocked, allowed, and redirected counters freeze during active progress and
  refresh after terminal completion.
- [x] Patch production only if the focused proof exposes a real counter-freeze defect.
- [x] Run the focused connected Home counter-freeze test, standard local Gradle gate, and full
  connected suite if the test mutates shared app data.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 35
- Starting state: `HOME-004` remained `Partially covered`; the post-sync counter path was proven,
  but active update progress still lacked a device-level counter-freeze proof.
- Added an active-update path to `HomeCountersInstrumentedTest`: it launches the real Home shell,
  seeds visible blocked/allowed/redirected counts, injects the shared `FilterOperationState` that
  `SourceModel` publishes in production, mutates the database to new counts while progress is
  active, asserts the old counters stay visible during the active operation, then publishes
  terminal `COMPLETE` and asserts the new counters appear.
- The first focused connected run failed usefully:
  `homeCountersFreezeDuringActiveUpdateAndRefreshAfterCompletion` kept showing blocked count `3`
  instead of refreshing to `5` after terminal completion.
- Root cause: after terminal `COMPLETE`, `HomeFragment` reset and reattached counter observers,
  then the generic progress guard immediately re-read stale blocked-count LiveData and restored
  `initialBlockedCount`, causing the later correct Room value to be ignored.
- Fixed `HomeFragment` so terminal states do not re-prime the active-import blocked-count guard,
  and so the terminal/one-shot counter refresh reads and renders the blocked count alongside
  allowed and redirected counts.
- The first pushed PR CI run exposed a test-isolation gap: the connected test did not reliably
  reach the initial seeded blocked count on GitHub's full-suite order. The test now resets the
  shared `FilterOperationState` to idle in setup and teardown while keeping fixture seeding after
  Home launch so app database bootstrap can complete normally.
- Focused connected Home counters gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeCountersInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 143
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Committed and pushed as `2785d447`; PR CI passed all required checks on the pushed head.
- Remaining boundary: `HOME-004` is now covered by connected UI flow; broader Home progress visual
  matrix coverage remains tracked under `HOME-007`.

## Plan - 2026-06-26 Story Fix Loop 36
- [x] Re-ground `HOME-007` from the canonical story spreadsheet and current Home progress binding.
- [x] Add a connected Home progress proof that drives the real UI through shared
  `FilterOperationState` values for active, finalizing, complete, and stopped states.
- [x] Assert visible summary text, progress container visibility, and pause/stop control
  enablement match each operation state.
- [x] Patch production only if the focused proof exposes a real progress UX defect.
- [x] Run the focused connected Home progress test, standard local Gradle gate, and full connected
  suite if the test mutates shared app state.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 36
- Starting state: `HOME-007` was `Partially covered`; `FilterOperationState` unit tests and UX
  screenshots existed, but no connected test drove Home through visible progress-state transitions.
- Added `HomeProgressInstrumentedTest`, a connected test that launches the real Home shell and
  injects shared `FilterOperationState` values through the same `SourceModel` LiveData used in
  production.
- The test verifies idle hides the progress container; active download shows `34.0% Complete`,
  progress bar value `34`, hidden phase-detail rows, and enabled pause/stop controls; active parse
  shows accepted-rule count copy; finalizing shows finalizing copy and disables controls; stopped
  shows `Update stopped`; complete shows `Protection updated`; and idle hides progress again.
- No production code was changed in this loop; the connected proof verified existing progress UI
  behavior.
- Focused connected Home progress gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeProgressInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 141
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Committed and pushed as `7672b3d4`; PR CI passed all required checks on the pushed head.
- Remaining boundary: `HOME-007` is now covered by connected UI flow; human visual release
  sign-off remains tracked under `REL-004`.

## Plan - 2026-06-26 Story Fix Loop 37
- [x] Re-ground `HOME-008` from the canonical story spreadsheet and the real
  Home progress-control path.
- [x] Add a focused connected proof that clicks the real Home pause/resume button while
  `SourceModel` is in an active controllable update state.
- [x] Assert the click goes through `HomeFragment -> HomeViewModel -> SourceModel` by observing
  shared `FilterOperationState.paused` changes and the visible pause/resume affordance.
- [x] Patch production only if the focused proof exposes a real pause/resume defect.
- [x] Run the focused connected Home pause/resume test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 37
- Starting state: `HOME-008` was `Needs connected behavior coverage`; source-level contracts
  existed, but no connected test clicked the real Home pause/resume affordance and observed the
  shared `SourceModel` operation state.
- Added `HomeUpdateControlsInstrumentedTest`, a connected test that launches the real Home shell,
  primes `SourceModel` into an active controllable source update state, clicks the real
  `pauseResumeButton`, and waits for shared `FilterOperationState.paused` plus the visible
  content description to flip from `Pause update` to `Resume update` and back.
- The first candidate proof passed only because the test set a fake scheduler task name. The
  expert review found the production defect: ordinary Home updates never set `schedulerTaskName`,
  and `HomeFragment` hid the whole scheduler/control row when that name was empty.
- Tightened the connected proof to require the pause/resume button to be actually shown and
  enabled without a scheduler task name. The focused connected run then failed as expected:
  `View 2131296817 did not become shown and enabled`.
- Fixed `HomeFragment` so the progress control row is visible for every active source update and
  only the scheduler label depends on `schedulerTaskName`.
- Focused connected Home update-controls gate passed after the fix with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateControlsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` produced XML
  summary `tests="142" failures="0" errors="0" skipped="3"` on `adaway-api34`.
- Committed and pushed as `9000bdce`; PR CI passed all required checks on the pushed head.
- Remaining boundary: ordinary Home pause/resume controls are fixed and device-proven, but
  cooperative pause inside one long download or parser loop remains open as a worker-level
  semantics gap.

## Plan - 2026-06-26 Story Fix Loop 38
- [x] Re-ground `HOME-009` from the canonical story spreadsheet and current stop/cancel findings.
- [x] Add a failing model-level connected proof that a stopped full source update reports
  cancellation/non-success and preserves active-generation runtime truth.
- [x] Patch `SourceModel` stop semantics so stopped updates are not indistinguishable from
  successful updates.
- [x] Patch apply callers to skip `AdBlockModel.apply()` when the source update reports
  cancellation.
- [x] Verify focused stop/cancel tests, standard local Gradle gate, and full connected suite if
  singleton/database state is exercised.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 38
- Starting state: `HOME-009` was `Needs connected behavior coverage`; the risk was concrete:
  a stopped update could be reported like a successful update, and apply callers would still run
  `AdBlockModel.apply()` after cancellation.
- Added a connected model proof in `SourceModelHttpConditionalTest` that starts a full source
  update, stops it during a delayed download, and asserts the update result is `false`, the active
  generation remains unchanged, active runtime rows still resolve, staging rows are cleaned up,
  and the partially downloaded replacement host is not active.
- The first focused run failed as expected: the stopped update returned `null` instead of
  `Boolean.FALSE`.
- Fixed `SourceModel` so `retrieveHostsSources()` and `checkAndRetrieveHostsSources()` return a
  boolean completion contract: normal/no-change paths return `true`, stopped updates clean staging
  state and return `false`, and interrupted pause waits convert to stopped state instead of
  bypassing cleanup.
- Guarded source-update apply callers in Home, scheduled/immediate source workers, filter-set
  workers, source-list update UI, and the apply snackbar so they only call `AdBlockModel.apply()`
  after a completed source update.
- Added a connected Home control proof that clicks the real stop button while `SourceModel` is in
  an active controllable update state and waits for terminal `STOPPED` state plus disabled
  pause/stop controls.
- Focused connected model stop/cancel gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelHttpConditionalTest#checkAndRetrieveHostsSources_stopDuringDownloadReturnsCancelledAndKeepsRuntimeTruth
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace`.
- Focused connected Home update-controls gate passed with the same JDK:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateControlsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- The first full connected run exposed a transient `HomeCountersInstrumentedTest` teardown latch
  timeout on the shared DB cleanup path. The class passed when rerun focused, and a fresh full
  connected rerun passed without changing that fixture.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 147
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Remaining boundary: stopped full updates are now reported and handled as cancellation, but a
  deeper worker-level parse-cancellation cleanup audit remains open for a later source-pipeline
  hardening slice.

## Plan - 2026-06-26 Story Fix Loop 39
- [x] Re-ground `HOME-006` from the canonical story spreadsheet and the real Home action path.
- [x] Add a focused connected proof that clicks the visible Home update/apply action and drives a
  deterministic local file source through the shared `SourceModel` pipeline.
- [x] Assert the user path updates runtime truth and reaches the apply boundary without requiring
  root or VPN consent.
- [x] Patch production only if the focused proof exposes a real Home action defect.
- [x] Run the focused connected Home action test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 39
- Starting state: `HOME-006` was `Partially covered`; the visible Home action path had source
  contract coverage and worker coverage, but no connected proof that the user could tap the real
  Home check/update or update/apply controls and drive runtime truth before apply.
- Added `HomeUpdateActionsInstrumentedTest`, a connected test that launches the real
  `HomeActivity`, clicks `checkForUpdateImageView` and `updateImageView`, uses a deterministic
  local `content://org.adaway.test.hosts/success.txt` source, injects a recording root-mode
  `AdBlockModel`, and asserts `fresh-success.example` resolves as `BLOCKED` before the apply
  boundary is accepted.
- No production code changed for `HOME-006`; the slice device-proves the existing Home action
  behavior.
- First focused attempts failed before meaningful assertion because the Room database initializer
  reinserted default HTTPS sources after fixture cleanup, which sent the source pipeline to real
  network lists like `hosts.oisd.nl`. The test fixture now forces the writable DB open and drains
  the app disk executor before cleanup and seeding.
- Focused connected Home action gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateActionsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- The first full connected suite after adding the new test exposed the existing
  `HomeCountersInstrumentedTest` teardown latch timeout on shared `AppExecutors.diskIO()` cleanup.
  The counter fixture now uses a private single-use executor for fixture DB work.
- Affected connected classes passed together with the same JDK:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateActionsInstrumentedTest,org.adaway.ui.home.HomeCountersInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 4 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 149
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Remaining boundary: `HOME-006` is device-covered for the deterministic local source path; the
  broader visual matrix remains under `REL-004`, and real network/update-source behavior remains
  under `RUNTIME-001` and `RUNTIME-010`.

## Plan - 2026-06-26 Story Fix Loop 40
- [x] Re-ground `HOME-010` from the canonical story spreadsheet and the real Home leak-status
  rendering path.
- [x] Add a focused connected proof that launches Home with VPN mode selected, Private DNS set on
  the device, app-managed VPN bypass enabled, user-app exclusions configured, and system-app
  exclusions configured.
- [x] Assert the visible Home leak card summarizes every risk and exposes the expected Private DNS
  and VPN settings actions.
- [x] Patch production only if the connected proof exposes a real leak-status rendering defect.
- [x] Run the focused connected leak-status test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 40
- Starting state: `HOME-010` was `Partially covered`; leak-state semantics had unit coverage, but
  the real Home card had no connected proof that Android Private DNS globals and VPN
  bypass/exclusion preferences render as user-visible warnings with settings actions.
- Added `HomeLeakStatusInstrumentedTest`, a connected test that stores the emulator's original
  `private_dns_mode` and `private_dns_specifier`, sets Private DNS to `hostname` /
  `dns.example`, selects VPN mode while stopped, enables app-managed bypass, seeds two excluded
  user apps, and marks all system apps excluded.
- The test launches real `HomeActivity` and asserts the leak card shows `4 risks need attention`,
  VPN selected but not running, the Private DNS provider, Browser DoH bypass risk, app-managed VPN
  bypass, two excluded user apps, all system apps excluded, the strict-mode hint, and visible
  Private DNS / VPN settings actions.
- No production code changed for `HOME-010`; the slice device-proves existing Home leak-status
  rendering for the high-risk settings state.
- Focused connected Home leak-status gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeLeakStatusInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `:app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Full local connected gate passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 150
  tests on `adaway-api34` with 3 skipped and 0 failed.
- Post-test device checks confirmed `private_dns_mode` and `private_dns_specifier` were restored
  to `null` after the focused and full connected runs.
- Remaining boundary: the warning UI is device-covered for seeded Android Private DNS and VPN
  bypass/exclusion states; actual Always-on VPN and block-without-VPN OS toggle walkthroughs stay
  under release-smoke/manual settings coverage.

## Plan - 2026-06-26 Story Fix Loop 41
- [x] Re-ground `SRC-002` from the canonical story spreadsheet and the real Sources toggle path.
- [x] Add a focused connected proof that launches Home, navigates to Sources, drives the real
  source-row switch checked-change listener, and observes the pending apply snackbar.
- [x] Assert the toggle persists to both `hosts_sources` and downloaded `hosts_lists` rows.
- [x] Click the snackbar Apply action and assert the ad-blocking apply boundary is reached without
  network-dependent fixture data.
- [x] Patch production only if the connected proof exposes a real Sources toggle/apply defect.
- [x] Run the focused connected Sources toggle test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 41
- Starting state: `SRC-002` was `Partially covered`; apply-feedback copy had unit coverage, but
  the real Sources screen did not have connected evidence for changing a source, persisting row
  state, showing pending apply, and reaching the apply boundary.
- Added `SourcesToggleApplyInstrumentedTest`, a connected test that launches `HomeActivity`,
  navigates to Sources, seeds one deterministic local source with one active host row, verifies the
  displayed source switch, drives the switch checked-change listener off, and asserts both
  `hosts_sources` and `hosts_lists` reflect the disabled state.
- The test injects a local-sync `SourceModel` and recording `AdBlockModel` so the proof stays on
  the real Home/Sources UI and Room path without depending on external filter-list network I/O.
- During red/green work, Espresso and `ActivityScenario.onActivity()` snackbar polling repeatedly
  hung on Android global-idle waits. The final test uses bounded UI-tree checks on the main thread
  for snackbar text/action and lets database/apply assertions prove behavior. A low-level
  `SwitchMaterial.performClick()` run was observed once as passed in logcat but was not stable
  enough for the verifier, so this slice records a checked-change listener proof rather than a
  tap-dispatch proof.
- No production code changed for `SRC-002`; the slice adds connected coverage for existing
  source-toggle persistence and pending apply behavior.
- Focused connected Sources toggle/apply gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.SourcesToggleApplyInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Remaining boundary: real tap dispatch on the source switch and real network update-all stay under
  `SRC-005` / runtime update stories; `ApplyConfigurationSnackbarContractTest` continues to cover
  the success and failure copy contract.

## Plan - 2026-06-26 Story Fix Loop 42
- [x] Re-ground `SRC-005` from the canonical story spreadsheet and the real Sources toolbar path.
- [x] Add a focused connected proof that launches Home, navigates to Sources, triggers the toolbar
  `Update all and apply protection` menu action, and observes the source-specific running copy.
- [x] Keep the update deterministic with a local recording `SourceModel` so the test proves the UI
  action reaches the all-sources branch without external network I/O.
- [x] Assert the ad-blocking apply boundary is reached only after runtime truth is synced.
- [x] Patch production only if the connected proof exposes a real Sources update-all defect.
- [x] Run the focused connected Sources update-all test, the affected Home counters connected
  test, the full connected Android suite, and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 42
- Starting state: `SRC-005` was `Partially covered`; source-level tests proved the menu id,
  explicit copy, and handler branch, but the real Home/Sources toolbar action had no connected
  proof for update-all dispatch, running feedback, runtime sync, and apply ordering.
- Added `SourcesUpdateAllInstrumentedTest`, a connected test that launches `HomeActivity`,
  navigates to Sources, finds `hosts_sources_toolbar`, triggers `action_hosts_update_all`, and
  observes the source-specific `Updating sources and applying protection...` running snackbar.
- The test injects a recording `SourceModel` that blocks the deterministic update until the
  running snackbar is visible, then syncs `host_entries` from a seeded active source row. The
  recording `AdBlockModel` asserts `apply()` is reached only after runtime truth resolves the
  seeded host as blocked.
- No production code changed for `SRC-005`; the slice adds connected coverage for existing
  toolbar update-all behavior.
- CI failure follow-up: PR Connected Android tests failed on the previous pushed commit in
  `HomeCountersInstrumentedTest.homeCountersFreezeDuringActiveUpdateAndRefreshAfterCompletion`
  because the test waited for the blocked counter to show `3`. The downloaded CI log showed a
  `HostsSourcesImmediateUpdateWorker` still running during the Home fixture setup and mutating
  the same Room tables after `cancelAllWork()`.
- Patched `InstrumentedTestState.resetWorkManager()` to drain known WorkManager jobs after
  cancellation before passive UI tests seed shared database tables. This keeps the fix in
  androidTest isolation code and prevents cross-test update workers from racing Home/Sources
  fixtures.
- Red/green notes: the first run failed at compile because `HostErrorException` requires
  `HostError`, not a string. The next run proved the behavior but timed out in
  `ActivityScenario.close()` waiting for Android global idle after the update/apply UI. The final
  proof avoids the idle-prone close path and passes with bounded behavior assertions.
- Focused connected Sources update-all gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.SourcesUpdateAllInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Focused Home counters connected gate passed with the same JDK:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeCountersInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Full connected Android suite passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 152
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- PR CI recheck passed after push: Connected Android tests, Development build, Validate locales,
  Analyze (java), Analyze (cpp), and CodeQL.
- Remaining boundary: the real toolbar update-all dispatch and apply ordering are device-proven
  for a deterministic local source; real network download/parse/update-all scale remains under
  `RUNTIME-001` and `RUNTIME-010`.

## Plan - 2026-06-26 Story Fix Loop 43
- [x] Re-ground `SRC-003` and `SRC-004` from the canonical story spreadsheet and current
  `SourceEditActivity` / Sources FAB code.
- [x] Add a connected proof that opens the real Sources add sheet, launches custom source add,
  rejects an invalid URL without inserting a source, saves a valid custom HTTPS source, then
  reopens the visible source row for editing.
- [x] Assert edit save replaces the source URL/label/format metadata deterministically in Room.
- [x] Patch production only if the connected proof exposes a real add/edit validation or save
  defect.
- [x] Run the focused connected source add/edit test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 43
- Starting state: `SRC-003` and `SRC-004` were `Not tested`; the code showed the real add path is
  `HostsSourcesFragment` FAB -> add-options bottom sheet -> `SourceEditActivity`, while edit opens
  `SourceEditActivity` from the displayed source card.
- Added `SourceEditAddEditInstrumentedTest`, a connected test that launches `HomeActivity`,
  navigates to Sources, opens the real Sources FAB add-options sheet, chooses Custom source,
  rejects an invalid HTTP URL without inserting a row, saves a valid HTTPS source, reopens the
  visible source card, and saves edited label/URL/allow-format metadata.
- The first red runs found harness gaps in the test: the add-options bottom sheet lives in a dialog
  window, and AppCompat toolbar actions were not reachable through `invokeMenuActionSync`. The
  final test drives those visible actions through accessibility and synchronizes on Room state.
- The connected proof exposed a real production lifecycle defect: `SourceEditActivity` saved the
  source on `diskIO()` and then called `finish()` from that background executor. The apply path now
  posts `finish()` back to `AppExecutors.mainThread()`, matching the existing delete-source path.
- Red/green note: after the production fix, the data assertions passed but `ActivityScenario.close()`
  hung on Android global-idle cleanup. The final test follows the existing Sources update-all
  connected-test pattern and lets teardown finish resumed activities instead of using the
  idle-prone close path.
- Focused connected Sources add/edit gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.source.SourceEditAddEditInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 153
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Remaining boundary: custom HTTPS URL add and visible source-row edit are device-proven; file
  picker source selection and FilterLists manual-add metadata stay under their separate Discover
  and file-source stories.

## Plan - 2026-06-26 Story Fix Loop 44
- [x] Re-ground `SRC-006` and `SRC-007` from the canonical story spreadsheet, Sources toolbar
  menu, `FilterSetStore`, and `FilterProfileDiff` code.
- [x] Add a focused connected proof that seeds deterministic sources, opens the real Save filter
  set dialog, validates empty-name rejection, saves the current enabled URLs under a user name,
  then changes the current selection.
- [x] Drive the real Apply filter set dialog and preview confirmation, then assert both
  `hosts_sources` and downloaded `hosts_lists` rows match the saved profile.
- [x] Patch production only if the proof exposes a real save/apply dialog, preview, or persistence
  defect.
- [x] Run the focused connected filter-set save/apply test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 44
- Starting state: `SRC-006` and `SRC-007` were `Partially covered`; storage and diff semantics
  had unit tests, but the real Sources toolbar dialogs had no connected proof that a saved set
  captures current enabled sources and that applying it changes persisted source/list-row state.
- Added `FilterSetSaveApplyInstrumentedTest`, a connected test that launches `HomeActivity`,
  navigates to Sources, seeds three deterministic sources, opens the real Save filter set dialog,
  verifies empty-name validation, saves the two enabled source URLs as `Travel Pack`, then changes
  the current selection to a third source.
- The same test drives the real Apply filter set dialog, selects `Travel Pack`, asserts the
  preview title, enable/disable/keep counts, and disable warning, then confirms Apply and verifies
  `hosts_sources`, downloaded `hosts_lists`, and the active profile all match the saved set.
- Red/green note: the first focused run failed at the second toolbar action because
  `ActivityScenario.onActivity()` waited for Android global idle after dialog interaction. The
  final proof uses a bounded lifecycle lookup plus `runOnMainSync()` for toolbar menu actions,
  matching the harness pattern already used in other Sources tests.
- No production code changed for `SRC-006` or `SRC-007`; this slice device-proves existing
  save/apply behavior for deterministic local sources.
- Focused connected filter-set save/apply gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetSaveApplyInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 154
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Remaining boundary: named profile save/apply is device-proven for local seeded source rows; saved
  set rename/delete and scheduling remain tracked by their own Sources/schedule stories.

## Plan - 2026-06-26 Story Fix Loop 45
- [x] Triage the red PR CI connected-test failure from head `bbefaf0b` with downloaded job
  artifacts, not guesswork.
- [x] Fix the test reset hole that lets an already-running singleton `SourceModel` update keep
  parsing while passive UI tests assert seeded Home counter state.
- [x] Keep the patch test-only unless evidence shows a production Home counter defect.
- [x] Re-run the focused Home counter connected test, then the standard local gate and connected
  suite if the focused gate is green.
- [x] Update this review section with exact CI/local evidence, then commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 45
- Starting state: PR #6 failed only the `Connected Android tests` job after Loop 44. Build,
  locale validation, CodeQL, Java analysis, and C++ analysis all passed.
- CI artifact evidence showed
  `HomeCountersInstrumentedTest.homeCountersFreezeDuringActiveUpdateAndRefreshAfterCompletion`
  failed waiting for `blockedHostCounterTextView` to show `3`.
- Logcat showed the Home counter test started while a real source update from earlier UI coverage
  was still parsing default sources, including `StevenBlack Unified`. The failure is therefore a
  passive UI test isolation/reset gap, not evidence that the new filter-set save/apply flow broke
  Home counter math.
- Patched `InstrumentedTestState.resetForPassiveRootUi(...)` to stop the singleton `SourceModel`,
  shut down its current download/parse pools, wait for `updateInProgress` to clear, force idle
  progress, and drain the `SourceModel` main handler before and after WorkManager cancellation.
- Kept the fix test-only. Production Home counter logic was not changed for this loop; the only
  Home test change is a sharper failure message that reports the last observed counter text.
- During full-suite verification, the original CI failure moved: `SourcesUpdateAllInstrumentedTest`
  passed, then the run exposed another idle-prone harness path in `SourceEditAddEditInstrumentedTest`.
  The Sources and source-edit UI tests now use bounded lifecycle/main-thread polling instead of
  unbounded `ActivityScenario.onActivity()` / `waitForIdleSync()` cleanup paths after UI actions.
- Focused connected gates passed with `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `HomeCountersInstrumentedTest`, `SourcesUpdateAllInstrumentedTest`, and
  `SourceEditAddEditInstrumentedTest` ran together as 4 tests on `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 154
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `0cc23f10 test: harden connected ui isolation` was pushed to PR #6. Rechecked PR CI
  passed on head `0cc23f10`: Connected Android tests, Development build, Validate locales,
  Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 46
- [x] Re-ground `PREF-005` and adjacent `PREF-007` from the canonical story spreadsheet,
  Preferences main navigation, `PrefsVpnFragment`, and `VpnBuilder`.
- [x] Add a connected proof that active VPN users can open VPN settings from Preferences,
  see explicit app-managed bypass risk copy, toggle bypass, and change system-app exclusion.
- [x] Patch production if the proof exposes a real unreachable-settings or persistence defect.
- [x] Run the focused connected VPN settings test, the standard local Gradle gate, and enough
  connected coverage to protect the touched preference navigation.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit `cef94783 fix: make active vpn settings reachable` and push PR #6.
- [x] Recheck PR CI after the remote connected Android job finishes.

## Review - 2026-06-26 Story Fix Loop 46
- Starting state: `PREF-005` was `Partially covered`; security source tests proved
  `VpnBuilder.allowBypass()` is guarded and off by default, but there was no connected proof that
  users could reach the VPN bypass setting or see the leak-risk copy. `PREF-007` was `Not tested`
  for system-app exclusion behavior.
- Added `PrefsVpnSettingsInstrumentedTest`, which launches real `PrefsActivity`, uses active VPN
  mode, opens the VPN settings row, asserts the app-managed bypass warning copy, toggles the
  bypass switch, and changes system-app exclusion to `all`.
- The first red connected runs exposed a real production navigation defect: `PrefsMainFragment`
  installed click listeners on the root/VPN method rows that always consumed the XML fragment
  navigation. Active VPN users saw an "already using this mode" toast instead of the VPN settings
  screen.
- Fixed `PrefsMainFragment` so the active method row opens its configuration fragment while
  inactive method rows still launch onboarding to switch modes.
- Focused connected VPN settings gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsVpnSettingsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 155
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Remaining boundary: `PREF-005` now has connected UI/persistence coverage. `PREF-007` has
  connected settings persistence coverage, but direct proof that `VpnBuilder` calls
  `addDisallowedApplication(...)` for the selected system-app policy remains open.
- Commit `cef94783 fix: make active vpn settings reachable` was pushed to PR #6. At the first
  recheck, Development build, Validate locales, Analyze (java), Analyze (cpp), and CodeQL had
  passed; the remote Connected Android tests job was still running.
- Final PR CI recheck for head `cef94783` passed: Connected Android tests, Development build,
  Validate locales, Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 47
- [x] Re-ground `PREF-002` from the canonical story spreadsheet, Preferences method rows,
  onboarding, and current connected preference coverage.
- [x] Add a connected user-path proof that tapping the inactive method row opens onboarding
  instead of the active method's settings screen, and that the stored method does not change before
  onboarding completion.
- [x] Patch production only if the proof exposes a real switch-flow or navigation defect.
- [x] Run the focused connected switch-flow test and the standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit `48e9864f test: cover preference method switching` and push PR #6.
- [x] Triage the failed remote Connected Android tests job on `48e9864f`.
- [x] Patch the exposed `PrefsRootFragment` detached-listener crash and verify locally.
- [x] Commit, push, and recheck PR CI for the lifecycle fix.

## Review - 2026-06-26 Story Fix Loop 47
- Starting state: `PREF-002` was `Partially covered`; source-text/security tests covered pieces
  of method persistence, but there was no connected proof that Preferences route active method rows
  to configuration and inactive method rows to onboarding after the Loop 46 active-row fix.
- A read-only explorer confirmed the code-level contract: both method rows stay enabled, summaries
  reflect the stored method, active rows call `openConfiguration(...)`, inactive rows call generic
  `OnboardingActivity`, and `OnboardingActivity.finishOnboarding(...)` is the point where
  `PreferenceHelper.setAbBlockMethod(...)` changes the stored method.
- Added `PrefsAdBlockMethodSwitchInstrumentedTest` with a four-route matrix:
  root-active/root-row opens Root settings, root-active/VPN-row opens onboarding, VPN-active/VPN-row
  opens VPN settings, and VPN-active/root-row opens onboarding.
- The connected matrix also asserts that inactive-row onboarding does not immediately change the
  stored method. This matches current product behavior: tapping the inactive row reopens the generic
  protection-method chooser rather than preselecting the tapped target.
- No production patch was needed in this loop; the Loop 46 `PrefsMainFragment` behavior already
  satisfies the route contract.
- Focused connected gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsAdBlockMethodSwitchInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 4 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Commit `48e9864f test: cover preference method switching` was pushed to PR #6.
  Development build, Validate locales, Analyze (java), Analyze (cpp), and CodeQL passed, but
  remote Connected Android tests failed in `PrefsVpnSettingsInstrumentedTest` because a detached
  `PrefsRootFragment` preference listener called `requireContext()` after a full-suite preference
  reset.
- Patched `PrefsRootFragment.onSharedPreferenceChanged(...)` to ignore callbacks after detach
  instead of crashing on `requireContext()`.
- Focused CI-failure retest passed locally with
  `PrefsAdBlockMethodSwitchInstrumentedTest,PrefsVpnSettingsInstrumentedTest`.

## Plan - 2026-06-26 Story Fix Loop 48
- [x] Re-ground `LIST-003` from the canonical story spreadsheet, list fragments,
  dialog layouts, `ListsActivity`, `ListsViewModel`, and current DB tests.
- [x] Add a connected user-path proof that the real Lists FAB/dialog flow stores blocked,
  allowed, and redirected user rules in `hosts_lists` with the right type, source, enabled state,
  and redirect IP.
- [x] Patch production only if the proof exposes a real add-flow, validation, persistence, or UX
  defect.
- [x] Run the focused connected add-rule test and standard local Gradle gate.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 48
- Starting state: `LIST-003` was `Partially covered`; DB/runtime tests covered direct user-rule
  inserts, and source-text tests covered redirected IP policy, but no connected test drove the real
  Lists FAB, tab routing, dialog validation path, and ViewModel persistence.
- A read-only explorer confirmed the expected behavior: `ListsActivity` routes the FAB to the
  current tab, blocked add accepts valid multiline hostnames, allowed add accepts wildcard
  hostnames, redirected add requires hostname plus public redirect IP, and `ListsViewModel` stores
  enabled `USER_SOURCE_ID` rows before surfacing the apply snackbar.
- Added `ListsAddUserRulesInstrumentedTest`, using a private Room database swapped into the app
  singleton. The test launches real `ListsActivity`, drives blocked/allowed/redirected FAB dialogs,
  fills the real text fields through accessibility, clicks Add, and asserts the resulting
  `hosts_lists` rows by type/source/enabled/redirection.
- The first red runs exposed a test harness idle problem, not an add-flow defect:
  `ActivityScenario.close()` and later `ActivityScenario.onActivity()` waited forever after the
  add flow. The test now avoids global-idle waits for post-add actions by using the lifecycle
  monitor plus `runOnMainSync`, and teardown finishes resumed activities directly.
- No `LIST-003` production patch was needed. The only production patch in this work batch is the
  `PrefsRootFragment` detached-listener guard from the remote CI failure.
- Focused connected add-rule gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsAddUserRulesInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed locally with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 160
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `c8160146 fix: guard prefs lifecycle and cover list adds` was pushed to PR #6.
- Final PR CI recheck for head `c8160146` passed: Connected Android tests, Development build,
  Validate locales, Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 49
- [x] Re-ground `ONB-001` and `ONB-002` from the canonical story spreadsheet,
  `HomeActivity`, `OnboardingActivity`, `DefaultListsSubscriber`, current source-contract tests,
  and the default source database path.
- [x] Add connected user-path proof for first-run Home redirect, deterministic chooser state,
  no-root VPN auto-preselect without completing onboarding, and onboarding-complete default source
  insertion.
- [x] Patch production only if the proof exposes a real onboarding, DB, or UX defect.
- [x] Run the focused connected onboarding test.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Run the standard local Gradle gate.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 49
- Starting state: `ONB-001` and `ONB-002` were `Partially covered`; unit/source-contract tests
  covered intent wiring and passive VPN preselection, but there was no connected proof that a fresh
  Home launch routes to onboarding or that onboarding-complete Home launch writes default sources
  into the DB.
- A read-only explorer confirmed the expected contract: Home redirects only while the selected
  method is `UNDEFINED`; onboarding auto-detect preselects VPN without calling Android VPN consent;
  completion sends `HomeActivity.EXTRA_ONBOARDING_COMPLETE`; and Home calls
  `DefaultListsSubscriber.subscribeDefaultsIfEmpty(...)` on disk IO before optionally starting the
  update/apply pipeline.
- Added `OnboardingFirstRunInstrumentedTest`, which swaps in a private Room DB with the production
  user-source invariant, launches real `HomeActivity` and `OnboardingActivity`, and asserts:
  first-run redirect to onboarding, skip-auto-detect chooser starts with Start disabled, unrooted
  auto-detect preselects VPN without changing the stored method, and onboarding-complete Home
  launch inserts all default catalog sources plus the WA/TG safety allowlist.
- The first red run exposed a test DB fixture mismatch, not a production defect: without the
  production user source at `id=1`, the first auto-generated default source took id 1 and was hidden
  by `HostsSourceDao.getAll()`, which intentionally excludes the user source. The test now seeds
  the user-source row before proving external defaults are empty.
- No production patch was needed in this loop.
- Focused connected onboarding gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.onboarding.OnboardingFirstRunInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 4 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed locally with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 164
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `28f65a62 test: cover onboarding first run` was pushed to PR #6.
- Final PR CI recheck for head `28f65a62` passed: Connected Android tests, Development build,
  Validate locales, Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 50
- [x] Re-ground `HOME-001`, `HOME-002`, and `HOME-003` from the canonical story spreadsheet,
  `HomeActivity`, `HomeFragment`, Home layouts/resources, and current Home connected/unit tests.
- [x] Add connected user-path proof for passive Home launch defaulting to the Home tab, all four
  bottom navigation destinations, singleTop Discover deep-entry freshness, and visible
  active/off protection state rendering.
- [x] Patch production only if the proof exposes a real Home shell, navigation, or status-rendering
  defect.
- [x] Run the focused connected Home launch/navigation/state test.
- [x] Run the standard local Gradle gate and broader connected gate if isolation risk is present.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 50
- Starting state: `HOME-001`, `HOME-002`, and `HOME-003` were `Partially covered`;
  source-contract tests and UX screenshots covered the shell textually/visually, but there was no
  connected proof that passive launch selects the real Home tab, that users can traverse all four
  bottom-nav destinations, or that the Home hero reacts to live active/off protection state.
- A read-only explorer confirmed the expected contract: configured passive launch defaults to
  `HomeFragment`, first install redirects to onboarding instead, Discover deep intents must update
  `Activity.getIntent()`, bottom nav routes Home/Discover/Sources/More inside one shell, and
  `HomeFragment` renders `Protection off`/`Protection active` from `AdBlockModel.isApplied()`.
- Added `HomeLaunchNavigationStateInstrumentedTest`, which injects a deterministic
  `AdBlockModel`, launches real `HomeActivity`, asserts passive launch on `HomeFragment`, verifies
  all four real `BottomNavigationView` destinations, calls `onNewIntent(...)` for the singleTop
  Discover path, and toggles the model state through off/active/off while asserting visible status
  text.
- The first red run exposed a connected-test harness cleanup issue, not a production defect:
  assertions passed, but `ActivityScenario.close()` timed out waiting for a resumed `HomeActivity`
  to become destroyed. The test now uses explicit lifecycle-monitor cleanup, matching the stable
  list/preferences connected tests.
- No production patch was needed in this loop.
- Focused connected Home shell gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeLaunchNavigationStateInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 3 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed locally with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 167
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `e2f2479e test: cover home shell state` was pushed to PR #6.
- Final PR CI recheck for head `e2f2479e` passed: Connected Android tests, Development build,
  Validate locales, Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 51
- [x] Re-ground `RUNTIME-001` from the canonical story spreadsheet, `SourceModel`,
  `HomeViewModel`, `HostEntryDao`, the test content provider, and the existing Home update action
  connected test.
- [x] Strengthen connected user-path proof for Home update/apply actions so the test asserts
  source parsing, generation activation, source metadata/stat updates, host_entries runtime counts,
  and apply-boundary ordering.
- [x] Patch production only if the proof exposes a real update, runtime truth, or apply-ordering
  defect.
- [x] Run the focused connected Home update action test.
- [x] Run the standard local Gradle gate and the full connected suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 51
- Starting state: `RUNTIME-001` was `Partially covered`; generation-failure tests covered failed
  source preservation and active-generation safety, and Home update action tests proved apply saw
  one resolved host, but the canonical story had no connected proof of the full user path:
  download, parse, insert, generation activation, runtime cache materialization, and apply ordering.
- A read-only explorer confirmed the production path: `HomeViewModel.update()` and `sync()` call
  `SourceModel.checkAndRetrieveHostsSources()` on the network executor, and only call
  `AdBlockModel.apply()` when the source pipeline returns success.
- Strengthened `HomeUpdateActionsInstrumentedTest` rather than adding a synthetic model-only test.
  The existing deterministic content provider now proves two parsed hosts, active generation > 0,
  cleared download error, source freshness timestamps, source size/stat counters, active
  `hosts_lists` rows, `host_entries` runtime counts, and both hosts resolving as blocked before the
  recording `AdBlockModel.apply()` reports success.
- No production patch was needed in this loop. The first focused command failed before compilation
  because PowerShell/Gradle parsed the unquoted instrumentation property as a task name; the quoted
  command reached the device and passed.
- Focused connected Home update gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateActionsInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 2 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed locally with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 167
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `b229d24c test: prove runtime update apply pipeline` was pushed to PR #6.
- Final PR CI recheck for head `b229d24c` passed: Connected Android tests, Development build,
  Validate locales, Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 52
- [x] Re-ground `RUNTIME-002` from the canonical story spreadsheet,
  `SourceModelHttpConditionalTest`, `Generation304MigrationTest`, and the conditional download
  path in `SourceModel`.
- [x] Strengthen connected mixed-network proof so the 200/304/500 run asserts conditional
  `If-None-Match` and `If-Modified-Since` request headers for every source.
- [x] Patch production only if the proof exposes a real conditional GET, 304 carry-forward, or
  failed-source preservation defect.
- [x] Run the focused connected conditional GET test class.
- [x] Run the standard local Gradle gate and the full connected suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 52
- Starting state: `RUNTIME-002` was `Covered by tests`, but its `test_state` still said
  `Needs mixed network test`. Local inspection showed `SourceModelHttpConditionalTest` already had
  a connected mixed 200/304/HTTP 500 preservation test, while the remaining proof gap was that the
  mixed run did not assert conditional request headers for every source.
- Strengthened `SourceModelHttpConditionalTest` with a path-indexed MockWebServer request drain.
  The changed+304 test now proves two conditional GET requests, and the mixed 200/304/500 test
  proves three conditional GET requests while keeping the active-generation, carry-forward,
  failure metadata, and runtime resolution assertions.
- No production patch was needed in this loop. The first focused run failed for a test-scope
  reason: the three-source assertion was initially placed in the two-source changed+304 test.
  After correcting that scope, the focused connected class passed.
- Focused connected conditional gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelHttpConditionalTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 4 tests on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed locally with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 167
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `fb8bad9a test: cover mixed conditional source updates` was pushed to PR #6.
- Final PR CI recheck for head `fb8bad9a` passed: Connected Android tests, Development build,
  Validate locales, Analyze (java), Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 53
- [x] Re-ground `RUNTIME-012` from the canonical story spreadsheet, `DnsServerMapper`,
  `LeakStatus`, Home leak strings, `SecurityHardeningTest`, and `HomeLeakStatusInstrumentedTest`.
- [x] Add behavior-level DoH route-plan proof for common IPv4 and IPv6 provider routes, while
  preserving copy that discloses finite coverage instead of promising universal encrypted-DNS
  blocking.
- [x] Patch production only if the proof exposes a real route-planning, leak-status, or
  false-guarantee defect.
- [x] Run focused route/security JVM tests and the connected Home leak-status test.
- [x] Run the standard local Gradle gate and the full connected suite.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 53
- Starting state: `RUNTIME-012` was `Partially covered`; `SecurityHardeningTest` only scanned
  source text for DoH provider constants, `LeakStatusTest` covered risk classification, and
  `HomeLeakStatusInstrumentedTest` covered the visible stopped-VPN/Private-DNS/bypass risk state.
  There was no behavior test proving the route plan that production VPN configuration applies.
- Added `DnsServerMapperDohRoutesTest` test-first. The first focused run failed at compilation
  because `DnsServerMapper.DohRoute` and `commonDohBlockRoutes(boolean)` did not exist.
- Added a package-visible route-plan seam in `DnsServerMapper` and rewired production
  `addDohBlockRoutes(...)` to iterate that plan. The route test now proves eight IPv4 `/32` common
  DoH provider routes and eight IPv6 `/128` common provider routes when IPv6 is configured.
- Updated the stale security source guard to assert production applies
  `commonDohBlockRoutes(includeIpv6)` and that IPv6 DoH routes are still planned as `/128` host
  routes.
- This does not claim universal encrypted-DNS interception. The product copy still says common DoH
  providers are routed and other encrypted DNS may bypass filtering, and real Android VPN tunnel
  interception remains tracked by `RUNTIME-008`.
- Focused JVM gate passed with
  `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`:
  `:app:testDebugUnitTest --tests org.adaway.vpn.dns.DnsServerMapperDohRoutesTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`.
- Focused connected Home leak-status gate passed with the same JDK:
  `-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeLeakStatusInstrumentedTest
  :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` ran 1 test on
  `adaway-api34`.
- Standard local gate passed with the same JDK:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed locally with the same JDK:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 167
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Commit `f0d6a7cd test: prove common DoH route coverage` was pushed to PR #6, and final PR CI
  recheck passed: Connected Android tests, Development build, Validate locales, Analyze (java),
  Analyze (cpp), and CodeQL.

## Plan - 2026-06-26 Story Fix Loop 54
- [x] Re-ground `RUNTIME-010` from `tasks/user-story-status.tsv`, prior benchmark artifacts,
  `SourceLoaderPerformanceTest`, `SourceLoader`, and `HostEntryDao`.
- [x] Run current-head 100k and 1M full parse/sync/root-write benchmarks before editing.
- [x] Use the slow 1M result as root-cause evidence and add a red stage-coverage regression test.
- [x] Patch the import/runtime path so large parsed updates can use the stage-backed root export
  without duplicate hosts-file rows.
- [x] Rerun focused correctness and current-head 100k/1M scale gates.
- [x] Update `tasks/user-story-status.tsv` and this review section with exact evidence.
- [x] Run standard local Gradle gates, commit, push, and recheck PR CI.

## Review - 2026-06-26 Story Fix Loop 54
- Starting state: `RUNTIME-010` still said `Known gap` and `Needs benchmark rerun`, even though
  older ledger artifacts contained 5M stage/root-writer evidence. I treated the current worktree
  as authoritative and reran current-head full parse/sync/root-write benchmarks.
- Baseline current-head 100k full pipeline passed before edits:
  `SourceLoaderScaleBenchmark lines=100000 inserted=95000 runtimeRows=90000
  progressEvents=48 parseMs=58123 syncMs=1519 rootRows=90000 rootWriteMs=495
  rootWriteBytes=2857114`.
- Baseline current-head 1M full pipeline passed but exposed the real bottleneck:
  `SourceLoaderScaleBenchmark lines=1000000 inserted=950000 runtimeRows=900000
  progressEvents=475 parseMs=535607 syncMs=450369 rootRows=900000 rootWriteMs=1870
  rootWriteBytes=29476174`. Phase logs showed `HostEntryDao.root-export-direct` spent
  `redirectedMs=374490`, because full parse/import had never populated
  `root_host_entries_stage` and therefore could not use the fast stage-backed root-export path.
- Added the regression test first by asserting parsed imports populate `root_host_entries_stage`.
  The focused 10k connected test failed red with `expected:<9500> but was:<0>`.
- Patched `SourceLoader` to copy root-export candidates into `root_host_entries_stage` set-wise
  from `hosts_lists` at the end of the raw bulk import transaction. This keeps the stage copy
  atomic with the import and avoids a per-row double-write in the hot insert loop.
- The first staging proof exposed a correctness bug in the stage-backed path: duplicate blocked
  candidates could leak as duplicate root hosts-file rows. Added stage-backed duplicate skipping
  in `HostEntryDao` and extended `SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage`
  to include a duplicate blocked row while preserving redirect priority, allow filtering, and
  cursor/list parity.
- Focused connected 10k proof passed after the patch:
  `SourceLoaderPerformanceTest lines=10000 inserted=9500 runtimeRows=9000 progressEvents=5
  parseMs=11316 syncMs=699 rootRows=9000 rootWriteMs=101 rootWriteBytes=276558`.
- Focused stage-backed DAO proof passed:
  `SourceDbTest#testLargeRuntimeSkipUsesCompleteRootExportStage` on `adaway-api34`.
- Current-head 100k full pipeline passed after the patch:
  `SourceLoaderScaleBenchmark lines=100000 inserted=95000 runtimeRows=90000
  progressEvents=48 parseMs=49670 syncMs=1484 rootRows=90000 rootWriteMs=282
  rootWriteBytes=2857114`.
- Current-head 1M full pipeline passed with explicit `adawayPerfSyncBudgetMs=120000` and
  `adawayPerfRootWriteBudgetMs=30000`: stage flush was `rows=950000 flushMs=49477`,
  stage-backed root export was `totalMs=13049`, duplicate skip removed `50000` duplicate rows,
  and final output was `SourceLoaderScaleBenchmark lines=1000000 inserted=950000
  runtimeRows=900000 progressEvents=475 parseMs=570278 syncMs=13361 rootRows=900000
  rootWriteMs=2164 rootWriteBytes=29476174`.
- Result: 1M sync improved from `450369ms` to `13361ms`, and total selected test time dropped
  from `1094.876s` during the first staging attempt to `691.918s` with set-wise staging.
  The full 5M parse/import path remains open; prior 5M stage/root evidence is not enough to close
  `RUNTIME-010` as fully covered.
- Standard local gate passed:
  `test --dependency-verification=strict --stacktrace`.
- Full connected Android suite passed:
  `:app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace` finished 167
  tests on `adaway-api34`, with 3 skipped and 0 failed.
- Diff hygiene passed with only existing CRLF conversion warnings:
  `git diff --check`.

## Plan - 2026-06-27 RUNTIME-010 Fresh 5M Full Parse Proof
- [x] Recheck PR #6 head/CI before spending emulator time on the scale run.
- [x] Re-ground `RUNTIME-010` from `tasks/user-story-status.tsv`, the latest review block, and
  `SourceLoaderPerformanceTest`.
- [x] Run the fresh 5M full parse/import/sync/root-write connected benchmark on current head.
- [x] Record the 5M result in this ledger and `tasks/user-story-status.tsv` without marking the
  overall market-leading goal complete.
- [x] Prepare only the verified evidence update for commit/push.

## Review - 2026-06-27 RUNTIME-010 Fresh 5M Full Parse Proof
- PR #6 head remained `b132bf71 perf: stage root export during source import`. The installed
  `gh` CLI could not run `gh pr checks 6` without GitHub auth, so I rechecked public GitHub
  check-runs for the head SHA: Connected Android tests, CodeQL, Development build, Validate
  locales, Analyze (java), and Analyze (cpp) were all `completed/success`.
- Provisioned this Mac-side proof environment with Temurin JDK 21, Android SDK platform tools,
  API 34 and API 36 platforms, build-tools 36.0.0, NDK `27.2.12479018`, and an
  `adaway-api34-16g` API 34 Google APIs ARM64 AVD with 15G free on `/data`.
- Fresh 5M full parse/import/sync/root-write command:
  `./gradlew --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#parseInsertSyncAndRootApply_requestedScale_recordsBenchmark'
  '-Pandroid.testInstrumentationRunnerArguments.adawayPerfLines=5000000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayPerfSyncBudgetMs=300000'
  '-Pandroid.testInstrumentationRunnerArguments.adawayPerfRootWriteBudgetMs=300000'
  --dependency-verification=strict --stacktrace`.
- Result: Gradle `BUILD SUCCESSFUL in 4m 56s`; instrumented test log reported `OK (1 test)`.
  Raw stdout/stderr were preserved under
  `tasks/benchmarks/2026-06-27-runtime010-5m-full-parse-gradle.out.log` and
  `tasks/benchmarks/2026-06-27-runtime010-5m-full-parse-gradle.err.log`.
- Final benchmark metric:
  `SourceLoaderScaleBenchmark lines=5000000 inserted=4750000 runtimeRows=4500000
  progressEvents=2375 parseMs=124749 syncMs=8739 rootRows=4500000 rootWriteMs=2476
  rootWriteBytes=150601774`.
- Root-export phase evidence: `HostEntryDao.root-export-stage perf: ... totalMs=6644
  allowRules=false wildcardExactAllowRules=false stageBacked=true`; duplicate skip removed
  `250000` rows before the final root write.
- RUNTIME-010 now has fresh connected evidence for 100k, 1M, and 5M full parse/import/sync/root
  write. The overall market-leading release goal remains open for non-performance P0 gates,
  including release artifacts, physical/root/VPN smoke, UX sign-off, and legal/provenance review.

## Plan - 2026-06-27 RUNTIME-008 Fresh VPN Consent/TUN Proof
- [x] Re-ground `RUNTIME-008` from `tasks/user-story-status.tsv`, existing VPN worker/proxy
  tests, onboarding, Home status, and leak-status rendering.
- [x] Manually drive the first-run API 34 VPN consent path on a fresh debug install.
- [x] Fix only verified first-run defects exposed by that smoke path.
- [x] Run focused JVM contracts for onboarding and Home status/leak refresh.
- [x] Re-run fresh uninstall/reinstall VPN consent smoke and preserve raw evidence.
- [x] Update the canonical story ledger without marking the overall release goal complete.

## Review - 2026-06-27 RUNTIME-008 Fresh VPN Consent/TUN Proof
- Starting state: `RUNTIME-008` still had worker/proxy packet coverage but no real Android
  user-consent/TUN proof. The Mac API 34 emulator was available, so I used it for a fresh
  first-run non-root VPN smoke.
- The first manual pass exposed a real product defect: after Android VPN consent was accepted,
  `OnboardingActivity.finishOnboarding(VPN)` saved the method and launched Home, but did not
  apply the shared VPN model. Home could show VPN selected but not running after the user tapped
  `START PROTECTING`.
- Patched onboarding to call the active `AdBlockModel.apply()` after saving VPN mode, so the
  VPN start goes through the same model that Home observes. If VPN start fails, onboarding now
  stays visible and shows the existing VPN enable error instead of navigating to a misleading
  Home state.
- The first patch iteration started the raw service but left Home's `isAdBlocked()` observer
  stale; the final patch intentionally uses `AdBlockModel.apply()` and adds a bounded Home
  leak-status resample when VPN protection transitions active.
- Focused JVM contracts passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.onboarding.OnboardingFirstRunContractTest
  --tests org.adaway.ui.home.HomeNavigationSourcesContractTest --dependency-verification=strict
  --stacktrace`.
- Fresh emulator smoke used `adb uninstall org.adaway`, `:app:installDebug`, launcher start,
  tap `START PROTECTING`, accept the Android VPN `Connection request`, then collect UI dumps,
  `dumpsys vpn_management`, `dumpsys connectivity`, and logcat.
- Final evidence was preserved under
  `tasks/benchmarks/2026-06-27-runtime008-vpn-consent-tun-evidence.md` plus the
  `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-*` raw artifacts.
- Final proof highlights: consent XML contains `Connection request`, `AdAway wants`, and `OK`;
  Home XML contains `Protection active`; leak detail says `Protection method: VPN running`;
  `dumpsys vpn_management` reports active package `org.adaway`; `dumpsys connectivity` reports
  `NetworkAgentInfo ... VPN CONNECTED` with `InterfaceName: tun0`, `sessionId=AdAway`, and
  `OwnerUid: 10195`; logcat shows `VpnServiceControls.start()`, `Processing START`,
  `VPN established`, and status `RUNNING`.
- RUNTIME-008 is now covered by connected smoke for the emulator-backed Android consent/TUN
  path. Physical-device release smoke remains tracked under `REL-003`, rooted hosts apply
  remains `RUNTIME-007`, and the overall market-leading release goal remains open.

## Plan - 2026-06-27 REL-001 Local Source Boundary Reports
- [x] Install the missing Mac PowerShell prerequisite required by the release scripts.
- [x] Run GitTracked and WorkingTree license-boundary reports on current head.
- [x] Run GitTracked strict source-archive license-boundary report on the local evidence head.
- [x] Run focused `SecurityHardeningTest` coverage for the license-boundary guard.
- [x] Run the focused release/update script contract bundle for release gate coverage.
- [x] Record REL-001 source-scan evidence without closing the legal/provenance gate.

## Review - 2026-06-27 REL-001 Local Source Boundary Reports
- PR #6 CI recheck later passed on pushed head `6cb4d35b`: Analyze (cpp), Analyze (java),
  CodeQL, Development build, Validate locales, and Connected Android tests all passed. The
  connected job finished `167` tests with `3` skipped and `0` failed.
- Installed PowerShell 7.6.3 via Homebrew on this Mac so `scripts/check-license-boundary.ps1`
  can run locally.
- Generated source-boundary reports for current head `6cb4d35b5178beb15625e8a4d6c8be6f7e5c9ef2`:
  `SourceMode GitTracked` inspected `2416` entries and `SourceMode WorkingTree` inspected `2169`
  entries. Both reports passed with `Issues: 0`.
- Generated a strict source-archive report on local evidence head
  `fe5f66da46c149829d6d3883d6e9d532c06b71e2`: `SourceMode GitTracked
  -StrictSourceArchive` inspected `2425` source entries and `2115` source archive entries, with
  `Issues: 0`.
- Both reports still state `MIT release status: blocked until GPL-derived material is cleared`.
  This is evidence that the source guard is clean; it is not legal/provenance clearance and does
  not satisfy final artifact license readiness.
- Focused guard regression coverage passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --dependency-verification=strict --stacktrace`.
- Focused release/update source-contract bundle passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --tests org.adaway.scripts.ReleaseReadinessScriptTest
  --tests org.adaway.scripts.UxMatrixScriptTest
  --tests org.adaway.model.update.ApkIntegrityVerifierTest
  --dependency-verification=strict --stacktrace`.
- REL-001 is advanced with local source-report evidence, but remains open for legal review and
  release artifact boundary proof.

## Plan - 2026-06-27 Second-Wave Local Story Proofs
- [x] Dispatch parallel lanes for VPN lifecycle, external command security, diagnostics UI,
  backup/notification proof, UX matrix portability, and hardware release-smoke handoff.
- [x] Integrate only verified code/test slices from those lanes.
- [x] Run focused JVM contracts for the edited source/test surfaces.
- [x] Run focused connected Android tests for the emulator-runnable stories.
- [x] Update `tasks/user-story-status.tsv` without closing external hardware, legal, release, or
  human-review gates.

## Review - 2026-06-27 Second-Wave Local Story Proofs
- PR #6 remained green after the second-wave evidence batch was pushed to head `9e0f3574`:
  Analyze (cpp), Analyze (java), CodeQL, Connected Android tests, Development build, and
  Validate locales all passed.
- `RUNTIME-009`: fixed `VpnConnectionMonitor.reset()` to re-arm the monitor after recovery stop.
  Focused JVM proof passed for `VpnConnectionMonitorTest`, `VpnWatchdogTest`, and
  `VpnWorkerIdleTimeoutTest`. Connected `VpnLifecycleInstrumentedTest` passed heartbeat
  start/stop proof on `adaway-api34-16g`; the full start/stop/resume tunnel proof skipped safely
  because it requires pre-granted AdAway VPN consent and no active VPN.
- `DOMAIN-001`: added explicit Domain Checker result states for blocked, allowed, redirected, and
  unknown. Focused `DomainCheckerTest` passed, and connected `DomainCheckerRuntimeTruthTest`
  passed in the 9-test diagnostics/system batch on `adaway-api34-16g`.
- `LOG-001`: added connected `LogRuntimeTruthTest` proving a real `VpnModel.getEntry()` log
  generation path appears in `LogActivity`. This strengthens the Log UI story, but packet-through
  running-TUN DNS logging remains a later runtime smoke.
- `PREF-011` and `NOTIF-001`: added `BackupSafContractTest`,
  `PrefsBackupRestoreSafInstrumentedTest`, and `NotificationHelperChannelInstrumentedTest`.
  Focused JVM contracts passed; connected SAF/channel proof passed in the same 9-test batch.
  Real DocumentsUI/provider file round trip and notification upgrade/user-modified channel behavior
  remain separate manual/release-smoke gaps.
- `SYS-003`: added `CommandReceiverSecurityContractTest` and
  `CommandReceiverSecurityInstrumentedTest`. Focused JVM and connected tests passed, proving the
  external command API remains a single exported signature-permission receiver. A separately
  signed hostile helper APK remains the stronger future proof; shell broadcasts on API 34 are not a
  reliable hostile-app model.
- `REL-004`: made `scripts/run-ux-matrix.ps1` discover Unix `adb`, `ANDROID_SDK_ROOT`, native path
  separators, and the native Gradle wrapper. `UxMatrixScriptTest` passed with Unix adb/Gradle
  discovery coverage. Human review of a checked screenshot packet is still required before the
  release sign-off gate can close.
- Hardware/release-smoke handoff remains external: `RUNTIME-007`, `REL-003`, `UPDATE-002`, and
  `UPDATE-004` require real rooted/non-root physical devices, release artifacts, signer cert hash,
  manifest keys, and safe restore procedures.

## Plan - 2026-06-27 Home Update/Error UI Proof
- [x] Re-ground `HOME-011` and `HOME-012` against current Home code and tracker status.
- [x] Add focused connected UI proof for app-update manifest rendering and Home error Help flow.
- [x] Run androidTest Java compile after adding the proof.
- [x] Run the focused connected Home UI test on the API 34 emulator.
- [x] Update canonical trackers without closing unrelated release or hardware gates.

## Review - 2026-06-27 Home Update/Error UI Proof
- Added `HomeUpdateSignalErrorInstrumentedTest` with no production-code changes.
- `HOME-011`: injected a deterministic signed update manifest into the real `UpdateModel`, proved
  the Home version label renders `Update available!` in bold, then clicked the label and verified
  `UpdateActivity` resumed.
- `HOME-012`: injected `HostError.NO_CONNECTION` into the real `HomeViewModel`, proved the dialog
  shows the error title, details, and Help copy, then clicked Help while an
  `Instrumentation.ActivityMonitor` blocked and counted the exact GitHub wiki `ACTION_VIEW` path.
- Compile proof passed:
  `:app:compileDebugAndroidTestJavaWithJavac`.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateSignalErrorInstrumentedTest`
  finished `2` tests on `adaway-api34-16g` with `0` failures.
- Advanced `HOME-011` and `HOME-012` to connected UI coverage in
  `tasks/user-story-status.tsv`. External release, hardware, root, VPN consent, direct-release
  self-update, and human screenshot-review gates remain open.

## Plan - 2026-06-27 Lists Search UI Proof
- [x] Re-ground `LIST-002` against current Lists code, existing unit coverage, and connected
  fixtures.
- [x] Add a focused connected UI proof for the real list search field.
- [x] Run the focused connected Lists search test on the API 34 emulator.
- [x] Update canonical trackers without touching adjacent edit/delete/toggle stories.

## Review - 2026-06-27 Lists Search UI Proof
- Added `ListsSearchInstrumentedTest` with no production-code changes.
- The test uses an isolated Room database fixture, opens the real blocked-rules tab, verifies both
  seeded rules render, enters a search query in `hostsSearchEditText`, proves the matching row stays
  visible while the non-matching row disappears, then searches a no-match term and verifies the
  `No matching rules` empty state.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsSearchInstrumentedTest`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Advanced `LIST-002` to connected UI coverage in `tasks/user-story-status.tsv`. `LIST-004`,
  `LIST-005`, and `LIST-006` remain separate rule mutation/override gaps.

## Plan - 2026-06-27 Discover Profile Status UI Proof
- [x] Re-ground `DISC-003` against Discover profile status code and existing resolver tests.
- [x] Add a focused connected UI proof for exact, extended, partial, and custom profile states.
- [x] Run the focused Discover profile-status test on the API 34 emulator.
- [x] Update canonical trackers without touching schedule/delete source stories.

## Review - 2026-06-27 Discover Profile Status UI Proof
- Added `DiscoverProfileStatusInstrumentedTest` with no production-code changes.
- The test seeds three deterministic sources and a Safe profile URL set, navigates to the real
  Discover tab, and proves `discoverProfileStatus` renders `Profile: Safe Mode`,
  `Profile: Safe Mode + custom`, `Profile: Modified from Safe Mode`, and `Profile: Custom` as
  enabled sources and the active profile change.
- First focused run exposed a fixture race: the test cleared sources before
  `AppDatabase.onCreate` finished its queued safety allowlist seed. The fixture now forces the DB
  open and drains the disk executor before clearing sources, matching established Discover tests.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.discover.DiscoverProfileStatusInstrumentedTest#profileStatusReflectsExactExtendedPartialAndCustomState
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Advanced `DISC-003` to connected UI coverage in `tasks/user-story-status.tsv`. `SRC-009` and
  `SRC-010` remain separate schedule/destructive-source gaps.

## Plan - 2026-06-27 Preferences Hosts Scheduling UI Proof
- [x] Re-ground `PREF-008` against Preferences update UI and SourceUpdateService WorkManager
  behavior.
- [x] Add a focused connected UI proof for hosts-update daily and unmetered-only toggles.
- [x] Run the focused Preferences scheduling test on the API 34 emulator.
- [x] Update canonical trackers without touching app-update or release-update stories.

## Review - 2026-06-27 Preferences Hosts Scheduling UI Proof
- Added `PrefsUpdateSchedulingInstrumentedTest` with no production-code changes.
- The test opens real Preferences, enters the Updates screen, clicks the visible hosts-update
  daily row, verifies one active `HostsUpdateWork` with `CONNECTED` network constraints, clicks
  the visible unmetered-only row, verifies the same work updates to `UNMETERED`, then turns daily
  scheduling off and verifies no active periodic work remains.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsUpdateSchedulingInstrumentedTest#hostsUpdatePreferenceTogglesPeriodicWorkAndUnmeteredConstraint
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Advanced `PREF-008` to connected UI coverage in `tasks/user-story-status.tsv`. App self-update,
  release-update, and physical-device smoke gates remain separate.

## Plan - 2026-06-27 Root Redirection Validation Proof
- [x] Re-ground `PREF-003` against `PrefsRootFragment` and the root preferences XML.
- [x] Extract the existing IPv4/IPv6 address-family policy into a small testable helper.
- [x] Add focused JVM coverage for valid defaults, invalid strings, and cross-family addresses.
- [x] Run the focused unit proof and update the canonical tracker row.

## Review - 2026-06-27 Root Redirection Validation Proof
- Added `RedirectionAddressValidator` and wired `PrefsRootFragment` through it without changing the
  toast/error path or the accepted address-family policy.
- Added `RedirectionAddressValidatorTest` covering IPv4 defaults, IPv6 defaults, cross-family
  rejection, hostname/malformed/null rejection, and the generic `InetAddress` family case.
- Focused JVM proof passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.prefs.RedirectionAddressValidatorTest
  --dependency-verification=strict --stacktrace`.
- Advanced `PREF-003` to unit validation coverage in `tasks/user-story-status.tsv`. Rooted hosts
  apply, physical-device smoke, direct-release self-update, and human UX sign-off gates remain
  separate.

## Plan - 2026-06-27 Lists Rule Actions UI Proof
- [x] Re-ground `LIST-004`, `LIST-005`, and `LIST-006` against the current list fragments,
  adapter, and ViewModel mutation paths.
- [x] Fix the ordinary row-click toggle path so non-clickable row switches are still reachable by
  users.
- [x] Move the downloaded-rule override prompt copy from hardcoded English into resources.
- [x] Add focused connected UI proof for user-rule toggle, edit, cancel-delete, confirm-delete, and
  downloaded-rule override creation.
- [x] Run focused compile and connected verification on the API 34 emulator.

## Review - 2026-06-27 Lists Rule Actions UI Proof
- Fixed `ListsAdapter` so tapping a list row toggles the row switch and routes through
  `AbstractListFragment.onToggleListItem`. Before this fix, the embedded switch was marked
  non-clickable in XML and the row had only long-click behavior, leaving ordinary toggle behavior
  unreachable.
- Replaced the downloaded-rule override dialog's hardcoded English message with
  `checkbox_list_override_downloaded_message`.
- Added `ListsRuleActionsInstrumentedTest`. The connected test uses an isolated Room database,
  opens the real blocked-rules tab, proves user-row disable/enable, edits one user rule through
  action mode, proves cancel leaves a second user rule intact, proves confirm delete removes it,
  then proves turning off a downloaded blocked row shows the override prompt, disables the
  downloaded row, and creates an enabled user `ALLOWED` override.
- First focused run failed in `ActivityScenario.close()` after assertions completed, so the harness
  was aligned with existing list tests by finishing activities in teardown. A later focused run
  showed the downloaded override path passing and the edited-row reuse was split from destructive
  delete so edit persistence and delete confirmation are proven independently.
- Compile proof passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Final focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsRuleActionsInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `2` tests on `adaway-api34-16g` with `0` failures.
- Advanced `LIST-004`, `LIST-005`, and `LIST-006` to connected UI coverage in
  `tasks/user-story-status.tsv`. External release, rooted-device, physical-device, and human UX
  sign-off gates remain separate.

## Plan - 2026-06-27 Filter Set Schedule UI Proof
- [x] Re-ground `SRC-009` against `HostsSourcesFragment`, `FilterSetStore`, and the existing
  WorkManager contract.
- [x] Add a focused connected UI method to the existing filter-set save/apply test harness.
- [x] Prove saved-set daily schedule persistence and unique `FilterSetUpdateWork` enqueue.
- [x] Update canonical trackers without touching source-delete or release gates.

## Review - 2026-06-27 Filter Set Schedule UI Proof
- Extended `FilterSetSaveApplyInstrumentedTest` with
  `scheduleSavedFilterSetDailyPersistsScheduleAndEnqueuesWorker`.
- The test seeds a saved `Travel Pack`, opens the real Sources toolbar schedule action, selects the
  saved set, chooses `Daily`, accepts the `TimePickerDialog`, then asserts
  `FilterSetStore.getSchedule(...) == SCHEDULE_DAILY` and one active
  `FilterSetUpdateService.WORK_NAME` WorkManager job.
- The first focused schedule runs exposed a test-helper gap: the schedule dialogs rendered the
  expected text but their list rows did not always expose a direct accessibility click action. The
  helper now falls back to a real tap at the matching node's bounds, and the persisted schedule plus
  WorkManager assertions prove the tap took effect.
- Compile proof passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Final focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetSaveApplyInstrumentedTest#scheduleSavedFilterSetDailyPersistsScheduleAndEnqueuesWorker
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Advanced `SRC-009` to connected UI coverage in `tasks/user-story-status.tsv`. `SRC-010`
  destructive source deletion remains a separate gap, and external release/hardware gates remain
  separate.

## Plan - 2026-06-27 Source Delete UI Proof
- [x] Re-ground `SRC-010` against `SourceEditActivity`, the source list row navigation path, and
  the Room cascade contract for downloaded rules.
- [x] Add focused connected UI proof for delete cancel and confirm.
- [x] Prove source row deletion cascades downloaded `hosts_lists` rows and removes the source card.
- [x] Run focused connected verification on the API 34 emulator.
- [x] Update canonical trackers without closing broader CI, release, root, VPN, or hardware gates.

## Review - 2026-06-27 Source Delete UI Proof
- Extended `SourceEditAddEditInstrumentedTest` with
  `deleteSourceRequiresConfirmationAndRemovesDownloadedRules`.
- The test seeds a custom source plus one downloaded blocked rule, opens the real Sources row and
  source editor, clicks the Delete toolbar action, proves the confirmation title/message, cancels
  and verifies the source plus downloaded row remain, then confirms removal and verifies the source
  row, downloaded `hosts_lists` rows, and visible source card are gone.
- Focused connected method proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.source.SourceEditAddEditInstrumentedTest#deleteSourceRequiresConfirmationAndRemovesDownloadedRules
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Full source-edit connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.source.SourceEditAddEditInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `2` tests on `adaway-api34-16g` with `0` failures.
- Advanced `SRC-010` to connected UI coverage in `tasks/user-story-status.tsv`. PR CI then passed
  on `9b4a7b6f`: Connected Android tests, Development build, Validate locales, Analyze (java),
  Analyze (cpp), and CodeQL. External release, rooted-device, physical-device, VPN consent, and
  human UX sign-off gates remain separate.

## Plan - 2026-06-27 Filter Set Manage UI Proof
- [x] Re-ground `SRC-008` against `HostsSourcesFragment`, `FilterSetStore`, and the existing
  filter-set connected harness.
- [x] Add focused connected UI proof for Manage Filter Sets rename validation and delete.
- [x] Prove rename preserves saved URLs, schedule, and active profile state.
- [x] Run focused method and full filter-set connected verification on the API 34 emulator.
- [x] Update canonical trackers without closing external release, hardware, root, VPN, or human
  UX sign-off gates.

## Review - 2026-06-27 Filter Set Manage UI Proof
- Extended `FilterSetSaveApplyInstrumentedTest` with
  `manageFilterSetsRenameValidationPersistsThenDeleteRemovesSet`.
- The test seeds a reserved Safe profile plus a manageable `Travel Pack`, opens the real Sources
  toolbar Manage Filter Sets action, verifies reserved profiles are not listed, rejects a rename
  to the reserved Safe profile name, renames to `Weekend Pack`, then verifies saved URL membership,
  weekly schedule state, and active profile state moved to the new name.
- The same flow then reopens Manage Filter Sets, selects `Weekend Pack`, confirms delete, verifies
  the saved set is removed, active profile falls back to Custom, the reserved Safe profile remains,
  and no manageable user filter sets remain in `FilterSetStore`.
- Focused connected method proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetSaveApplyInstrumentedTest#manageFilterSetsRenameValidationPersistsThenDeleteRemovesSet
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Full filter-set connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetSaveApplyInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `3` tests on `adaway-api34-16g` with `0` failures.
- Advanced `SRC-008` to connected UI coverage in `tasks/user-story-status.tsv`. External release,
  rooted-device, physical-device, VPN consent, and human UX sign-off gates remain separate.

## Plan - 2026-06-27 About UI Smoke
- [x] Re-ground `ABOUT-001` against `AboutActivity`, `about_activity.xml`, and the Preferences
  entrypoint.
- [x] Show the generated build version on the visible About screen.
- [x] Add connected UI smoke for app name, version, credits, and GPL/open-source attribution.
- [x] Run focused android-test compile and connected verification on the API 34 emulator.
- [x] Update canonical trackers without claiming legal/provenance or relicensing clearance.

## Review - 2026-06-27 About UI Smoke
- Updated `AboutActivity` so `aboutVersion` includes `BuildConfig.VERSION_NAME` alongside the app
  description.
- Added `AboutActivitySmokeInstrumentedTest`. The test opens Preferences, clicks the real About
  preference, waits for `AboutActivity`, and verifies app name, app description, generated version
  name, credits, AdAway attribution, Open source copy, and GPL-3.0 attribution are visible.
- Focused compile proof passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.about.AboutActivitySmokeInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Advanced `ABOUT-001` to partial UI coverage in `tasks/user-story-status.tsv`. Legal/provenance
  review, MIT relicensing clearance, external release, rooted-device, physical-device, VPN consent,
  and human UX sign-off gates remain separate.

## Plan - 2026-06-27 CI Fix: Filter Set Manage Full-Suite Assertion
- [x] Triage the red PR connected Android job on head `49bb80d5`.
- [x] Replace the full-suite-sensitive final snackbar assertion with durable filter-set state
  proof.
- [x] Run focused and affected connected verification.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-27 CI Fix: Filter Set Manage Full-Suite Assertion
- PR CI failed only `Connected Android tests` on `49bb80d5`; static/build lanes passed. The log
  failure was
  `FilterSetSaveApplyInstrumentedTest.manageFilterSetsRenameValidationPersistsThenDeleteRemovesSet`
  at the final `No user saved filter sets` snackbar assertion.
- The manage/rename/delete UI path had already completed in the failing run: the failure happened
  after delete confirmation, after the saved set was gone, active profile reset to Custom, and the
  reserved Safe profile remained.
- Hardened the test by replacing the final snackbar-only empty-manage assertion with a durable
  `FilterSetStore.getSetNames(...)` assertion that removes reserved profile names and verifies no
  manageable user filter sets remain. This keeps the user-story proof focused on the destructive UI
  path and persistent state without depending on transient snackbar exposure in the full suite.
- Focused connected retest passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetSaveApplyInstrumentedTest#manageFilterSetsRenameValidationPersistsThenDeleteRemovesSet
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Affected connected retest passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterSetSaveApplyInstrumentedTest,org.adaway.ui.about.AboutActivitySmokeInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `4` tests on `adaway-api34-16g` with `0` failures.
- Pushed hardening commit `f93a5142`; PR #6 CI passed on that head: Connected Android tests
  `7m49s`, Development build `3m56s`, Validate locales `17s`, Analyze java `4m27s`,
  Analyze cpp `1m39s`, and CodeQL `3s`.

## Plan - 2026-06-27 Parallel Proof Slices
- [x] Ask the CTO coordinator to triage remaining release gates and split parallel lanes.
- [x] Add verified proof for Domain Checker pasted-input normalization (`DOMAIN-002`).
- [x] Add verified proof for app-update Preferences controls and startup checks
  (`PREF-009`, `UPDATE-005`).
- [x] Add verified JVM contract for adware matcher signatures (`ADW-003`).
- [x] Fix launcher shortcut package drift with a generated application-id resource and contract
  (`SYS-005`).
- [x] Reject the unstable notification permission worker test and keep `PREF-010`/`NOTIF-003`
  open with evidence.
- [x] Commit, push, and recheck PR CI.

## Review - 2026-06-27 Parallel Proof Slices
- CTO coordinator verified PR #6 checks were green on `f93a5142` and reported that the remaining
  P0 board is now mostly external: rooted-device hosts apply, legal/provenance, signed release
  artifacts, physical-device smoke, human UX matrix sign-off, and final release readiness.
- `DOMAIN-002`: added a connected UI proof that pastes a messy URL with whitespace, uppercase
  host, trailing root dot, port, path, query, and fragment into the real Domain Checker, then
  verifies the normalized blocked host, blocked status, and source attribution render.
- `PREF-009`/`UPDATE-005`: extended `PrefsUpdateSchedulingInstrumentedTest` to prove visible
  app-update Preferences controls persist startup checks, enqueue/cancel daily `ApkUpdateService`
  work, respect the beta/channel store gate, and call app update check on fresh Home launch only
  when the startup preference is enabled.
- `ADW-003`: added `AdwareLiveDataMatcherTest` to keep the static adware signature table
  well-formed and prove the matcher scans activities, receivers, and services using package-prefix
  boundaries rather than arbitrary substrings.
- `SYS-005`: made `applicationId` explicit, generated `shortcut_target_package` from it, pointed
  all static launcher shortcut intents at that resource, and added a contract that keeps the
  Preferences, DNS requests, and Your lists shortcuts from drifting.
- Rejected `PREF-010`/`NOTIF-003` worker output: changing `POST_NOTIFICATIONS` with
  `UiAutomation.grantRuntimePermission()` / `revokeRuntimePermission()` killed the API 34
  instrumentation process. No flaky notification permission test was kept; the gate remains open
  for a safer harness or manual smoke.
- Focused JVM proof passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.adware.AdwareLiveDataMatcherTest --tests
  org.adaway.security.SystemReceiverContractTest --dependency-verification=strict --stacktrace`.
- Android test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest#domainCheckerFragmentNormalizesPastedInputBeforeLookup,org.adaway.ui.prefs.PrefsUpdateSchedulingInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `4` tests on `adaway-api34-16g` with `0` failures.
- Pushed proof commit `0ea2fb59`; PR #6 CI passed on that head: Analyze (cpp), Analyze (java),
  CodeQL, Connected Android tests, Development build, and Validate locales.

## Plan - 2026-06-27 Startup Swarm Local Proofs
- [x] Launch a CTO coordinator and specialist lanes for release gates, root/VPN/manual gates,
  connected preference proof, and JVM-only source contracts.
- [x] Integrate the verified `PREF-001` connected theme-mode proof.
- [x] Integrate the verified `LOG-002` sort-action source contract.
- [x] Integrate the verified `ADW-002` uninstall-intent source contract.
- [x] Record external blockers without re-running already proven `RUNTIME-010`.
- [x] Run integrated local verification for this swarm slice.
- [x] Commit, push, and recheck PR CI for this swarm slice.

## Review - 2026-06-27 Startup Swarm Local Proofs
- CTO/release/root-VPN audits agree with the current canonical evidence: `RUNTIME-010` already
  has fresh 100k, 1M, and 5M full parse/import/sync/root-write proof; the remaining P0 release
  board is mostly external, especially rooted-device hosts apply, signed release artifacts,
  physical-device release smoke, human UX sign-off, final readiness aggregation, and
  legal/provenance clearance.
- `PREF-001`: added `PrefsThemeModeInstrumentedTest`, which opens the real Preferences screen,
  proves Light, Dark, and System default are visible in the theme dialog, selects each option,
  verifies the stored preference value, verifies `PreferenceHelper.getDarkThemeMode(...)` and
  `AppCompatDelegate.getDefaultNightMode()`, and waits for a stable resumed `PrefsActivity`
  after recreation.
- `LOG-002`: added `LogSortActionContractTest` to guard the visible DNS log sort menu wiring:
  `LogActivity` inflates `log_menu`, consumes `R.id.sort`, calls `LogViewModel.toggleSort()`,
  and republishes the currently loaded rows through the selected comparator.
- `ADW-002`: added `AdwareUninstallIntentContractTest` to prove `AdwareInstall` keeps display and
  package names separate, row clicks call `uninstallAdware(...)`, and the uninstall intent uses
  `Intent.ACTION_DELETE` with a `package:` URI built from the detected package-name key.
- Worker-local verification passed before integration: focused `PrefsThemeModeInstrumentedTest`
  connected proof finished `1` test on `adaway-api34-16g` with `0` failures; focused JVM contracts
  for `LogSortActionContractTest` and `AdwareUninstallIntentContractTest`, plus the affected
  existing log/adware tests, ended `BUILD SUCCESSFUL`.
- Integrated JVM proof passed:
  `:app:testDebugUnitTest --tests org.adaway.ui.log.LogEntrySortTest --tests
  org.adaway.ui.log.LogSortActionContractTest --tests
  org.adaway.ui.adware.AdwareLiveDataMatcherTest --tests
  org.adaway.ui.adware.AdwareUninstallIntentContractTest --dependency-verification=strict
  --stacktrace`.
- Integrated android-test compile passed:
  `:app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace`.
- Integrated connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsThemeModeInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Pushed proof commit `b23409be`; PR #6 CI passed on that head: Analyze (cpp), Analyze (java),
  CodeQL, Connected Android tests, Development build, and Validate locales.

## Plan - 2026-06-27 Update Check Network Failure Proof
- [x] Re-ground `UPDATE-001` against `UpdateModel`, `ManifestTest`, Home update-signal proof, and
  existing release/update source contracts.
- [x] Add a narrow test seam so connected tests can inject the manifest URL, HTTP client,
  public key, update store, and direct-update gate without changing production defaults.
- [x] Add a connected HTTPS MockWebServer proof for valid manifest then failed manifest response.
- [x] Run compile and focused connected verification.
- [x] Run affected existing manifest and security hardening JVM contracts.
- [x] Commit, push, and recheck PR CI for this update slice.

## Review - 2026-06-27 Update Check Network Failure Proof
- Added package-private `UpdateModel` construction parameters for test-controlled
  `VersionInfo`, `OkHttpClient`, manifest URL, direct-update flag, store override, and manifest
  public key. The public production constructor still uses the same constants, build flag, APK
  store detection, app resource public key, and default OkHttp client.
- Added `UpdateModelNetworkFailureInstrumentedTest`. The test starts a local HTTPS
  `MockWebServer`, injects a client that trusts only the test certificate, serves a valid signed
  `stable/adaway` manifest, verifies update availability and request query parameters, then serves
  HTTP 500 and verifies the previously published manifest is cleared to `null`.
- The first connected attempt failed red before the HTTPS harness change with
  `UnknownServiceException: CLEARTEXT communication to localhost not permitted by network security
  policy`. I kept the cleartext policy intact and switched the harness to HTTPS instead of
  weakening app network security.
- Compile proof passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.update.UpdateModelNetworkFailureInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Existing JVM regression contracts passed:
  `:app:testDebugUnitTest --tests org.adaway.model.update.ManifestTest --tests
  org.adaway.security.SecurityHardeningTest --dependency-verification=strict --stacktrace`.
- Pushed proof commit `61103f66`; PR #6 CI passed on that head: Analyze (cpp), Analyze (java),
  CodeQL, Connected Android tests, Development build, and Validate locales.

## Plan - 2026-06-27 Language Telemetry Preference Proof
- [x] Re-ground `PREF-012` against `PrefsMainFragment`, `SentryLog`, preference resources, and
  existing preference connected-test patterns.
- [x] Add a focused connected preference proof for Force English locale side effects and crash
  reporting support state.
- [x] Fix unsupported telemetry so real-Sentry/no-DSN debug builds cannot crash when stale state
  or UI interaction attempts to enable telemetry.
- [x] Run compile, focused connected proof, and affected security hardening JVM contracts.
- [x] Commit, push, and recheck PR CI for this preference slice.

## Review - 2026-06-27 Language Telemetry Preference Proof
- Added `PrefsLanguageTelemetryInstrumentedTest` for the real Preferences screen. It proves the
  Force English switch persists, applies app locales to `en`, clears app locales when disabled,
  and verifies the crash-report preference is disabled with the unsupported-build summary when
  this build has no usable Sentry DSN.
- The first focused connected attempt failed red with a product crash:
  `IllegalArgumentException: DSN is required` from `SentryAndroid.init(...)` after tapping
  "Send crash reports" in a debug build using the real Sentry SDK without a configured DSN.
- Fixed `SentryLog` to expose `isSupported(Application)`, require both a non-stub Sentry runtime
  and configured `io.sentry.dsn` manifest metadata before initialization, and treat unsupported
  or disabled telemetry as a no-op shutdown path. The shutdown uses reflection so the sentry stub
  build remains source-compatible.
- Updated `PrefsMainFragment` to clear stale telemetry preference state, uncheck the switch, and
  disable the control with the unsupported-build summary whenever telemetry is not supported.
- Compile proof passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsLanguageTelemetryInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Existing JVM hardening contracts passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --dependency-verification=strict --stacktrace`.
- PR CI on pushed head `e015c78a` failed only in Connected Android tests. The downloaded CI
  report showed `PrefsLanguageTelemetryInstrumentedTest` failed before reaching the product
  assertion because the CI emulator viewport did not show the offscreen `Send crash reports`
  preference row: `Text was not visible: Send crash reports`.
- Stabilized the connected proof by scrolling the real Preferences `RecyclerView` to the debug
  section before asserting or toggling the crash-report preference. Focused retest passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsLanguageTelemetryInstrumentedTest#languageAndTelemetryPreferencesPersistAndApplyTheirSideEffects
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Pushed stabilization commit `ebedf9eb`; PR #6 CI passed on that head: Analyze (cpp),
  Analyze (java), CodeQL, Connected Android tests, Development build, and Validate locales.

## Plan - 2026-06-27 Update Download Status UI Proof
- [x] Re-ground `UPDATE-003` against `UpdateActivity`, `UpdateViewModel`,
  `PendingDownloadStatus`, `CompleteDownloadStatus`, and the update activity layout.
- [x] Add a focused connected proof for update-available, pending progress, complete progress,
  and reset states in the real `UpdateActivity`.
- [x] Fix stale progress reset so a new update attempt cannot briefly show the previous complete
  `100%` bar before the next download progress event.
- [x] Run compile and focused connected verification.
- [x] Commit, push, and recheck PR CI for this update-status slice.

## Review - 2026-06-27 Update Download Status UI Proof
- Added `UpdateActivityDownloadStatusInstrumentedTest`. The test publishes a signed test
  manifest into the app `UpdateModel`, launches the real `UpdateActivity`, verifies the update
  available header/changelog/button, then injects `PendingDownloadStatus`,
  `CompleteDownloadStatus`, and `null` reset progress through the activity `UpdateViewModel`.
- The first focused connected run failed red because `UpdateActivity` hid the progress bar on
  reset but left its internal progress at `100` after completion. That could make the next update
  attempt briefly show stale complete progress before the first new progress event.
- Fixed `UpdateActivity` to reset `downloadProgressBar` to `0` when progress becomes `null` and
  when a new update starts; starting a new update also clears stale progress text.
- Compile proof passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- Focused connected proof passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.update.UpdateActivityDownloadStatusInstrumentedTest
  --dependency-verification=strict --stacktrace`
  finished `1` test on `adaway-api34-16g` with `0` failures.
- Pushed proof commit `0a037a74`; PR #6 CI passed on that head: Analyze (cpp),
  Analyze (java), CodeQL, Connected Android tests, Development build, and Validate locales.

## Plan - 2026-06-27 FilterLists Network/Offline Proof
- [x] Recheck PR #6 CI and canonical tracker integrity before selecting the next slice.
- [x] Split read-only expert probes for `LOG-003`, `PREF-007`, and adjacent local-testable gaps.
- [x] Add deterministic `FilterListsDirectoryApi` network/offline contract coverage without
  depending on live FilterLists.com.
- [x] Run focused JVM verification and update the canonical story tracker.

## Review - 2026-06-27 FilterLists Network/Offline Proof
- PR #6 CI was green on head `00b1c1e7`: Analyze (cpp), Analyze (java), CodeQL,
  Connected Android tests, Development build, and Validate locales all passed.
- `tasks/user-story-status.tsv` still has `15` fields per row after the earlier structural fix.
- Added `FilterListsDirectoryApiTest` coverage proving the directory client requests the expected
  HTTPS host and `/lists`, `/syntaxes`, `/tags`, and `/languages` paths through its injected
  `OkHttpClient`, parses `/lists/{id}` detail responses through the same network entry point,
  throws on HTTP failure, and propagates a simulated offline `IOException`.
- Focused JVM proof passed:
  `:app:testDebugUnitTest --tests org.adaway.model.source.FilterListsDirectoryApiTest
  --dependency-verification=strict --stacktrace`.
- `DISC-004` is no longer waiting on network/offline tests. It still records live
  FilterLists.com availability and schema drift as an external-service risk, but the app behavior
  now has deterministic fail-closed/offline proof without requiring live internet.

## Plan - 2026-06-27 DNS Log Redirect Rule Proof
- [x] Use the LOG-003 specialist finding to identify the narrow redirect-from-log path and
  validator mismatch.
- [x] Align the DNS-log redirect dialog with the shared public redirect-IP policy.
- [x] Fix the redirect row action accessibility copy so it no longer announces allowlist behavior.
- [x] Add connected UI proof that a real VPN-generated log row can create a redirected user rule.
- [x] Run compile, JVM policy contract, and connected log verification.
- [x] Update the canonical tracker with evidence.

## Review - 2026-06-27 DNS Log Redirect Rule Proof
- Fixed `LogActivity` so the DNS-log redirect dialog uses `RegexUtils.isValidRedirectIp(...)`
  both for the positive-button action and for live dialog validation. This matches
  `RedirectedHostsFragment` and rejects private/reserved redirect targets instead of accepting
  every syntactically valid IP.
- Fixed `log_entry.xml` so the redirect action uses distinct accessibility copy
  (`tcpdump_entry_add_redirect`) instead of reusing the allowlist content description.
- Extended `RedirectedHostsValidationContractTest` to guard the shared redirect-IP policy in the
  redirected-host editor, backup import, and DNS-log redirect dialog, plus the redirect action
  content description.
- Extended `LogRuntimeTruthTest` with a connected flow that records a real VPN-model DNS log row,
  clicks the row redirect action, enters a public redirect IP, and polls Room for one enabled
  redirected user rule under the user source.
- First focused connected run failed red only at `ActivityScenario.close()` after the assertion
  path, because the rule action shows a persistent apply snackbar and the scenario waited forever
  for idle. The harness now avoids try-with-resources for that snackbar-producing path and lets
  test cleanup finish resumed activities without an idle wait.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  `:app:testDebugUnitTest --tests
  org.adaway.ui.lists.type.RedirectedHostsValidationContractTest --dependency-verification=strict
  --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.log.LogRuntimeTruthTest#redirectActionStoresRedirectedUserRuleFromLog
  --dependency-verification=strict --stacktrace`; and full connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.log.LogRuntimeTruthTest
  --dependency-verification=strict --stacktrace` with `2` tests on `adaway-api34-16g`.
- Pushed proof commit `dc732e24`; PR #6 CI passed on that head: Analyze (cpp),
  Analyze (java), CodeQL, Connected Android tests, Development build, and Validate locales.

## Plan - 2026-06-27 Root DNS Log Expectation Copy
- [x] Re-ground `LOG-004` against disabled root tcpdump capture and the current Log screen copy.
- [x] Replace generic recording guidance with root-mode unavailable copy when the active blocking
  method is root.
- [x] Add a focused connected proof for the root-mode Log empty state.
- [x] Run compile and connected verification.
- [x] Update the canonical tracker with evidence.

## Review - 2026-06-27 Root DNS Log Expectation Copy
- `TcpdumpUtils` keeps root DNS capture hard-disabled (`TCPDUMP_CAPTURE_ENABLED = false`) and
  `getLogs(...)` returns an empty list in that mode.
- Renamed the Log view-model helper to `isDnsRequestLoggingUnavailable()` and changed
  `LogActivity` to set `log_root_recording_unavailable` when the active model is root. VPN mode
  keeps the existing recording instructions.
- Added `LogRootExpectationInstrumentedTest`, which launches the real `LogActivity` in root mode
  and asserts the unavailable DNS logging empty-state copy is visible.
- Accepted the follow-up automatic locale normalization commit `cb10e8fa`, which only changed the
  `strings_log.xml` XML declaration casing; compile still passed after fast-forwarding it.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  and focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.log.LogRootExpectationInstrumentedTest
  --dependency-verification=strict --stacktrace` with `1` test on `adaway-api34-16g`.

## Plan - 2026-06-27 CTO Practical Remaining-Gates Split
- [x] Re-ground the branch from current PR CI and the canonical story TSV before doing more work.
- [x] Close stale subagent context whose recommendation was already superseded by current tracker
  evidence.
- [x] Split independent specialist probes for the next local-testable gaps: `PREF-007`,
  `ADW-001`, and release/external gate classification.
- [x] Implement one narrow, verified local slice in the main PR worktree without touching rooted,
  physical-device, signed-release, or legal/human gates that cannot be honestly closed here.
- [x] Update `tasks/user-story-status.tsv` and this log with focused evidence, then commit and
  push only if the slice is verified.

## Review - 2026-06-27 CTO Practical Remaining-Gates Split
- PR #6 CI is green on `cd95fa42`: Analyze (cpp), Analyze (java), CodeQL, Connected Android
  tests, Development build, and Validate locales all passed.
- Current canonical TSV already closes `RUNTIME-010` with fresh 100k, 1M, and 5M connected
  benchmark evidence. The open P0 board is now mainly external release evidence:
  `RUNTIME-007`, `UPDATE-002`, `UPDATE-004`, `REL-001`, `REL-002`, `REL-003`, `REL-004`, and
  `REL-005`.
- Practical local next slices are P1/P2 proof gaps that can reduce risk without pretending to
  finish the release: `PREF-007` VPN builder system-app exclusions, `ADW-001` adware scanner
  fixture/privacy proof, selected visual/large-font passes, and permission-safe notification
  affordance evidence if a stable harness exists.
- Added a package-private `VpnBuilder.excludeApplicationsFromVpn(Context, VpnApplicationExcluder)`
  seam so the existing production path still calls Android's
  `VpnService.Builder.addDisallowedApplication(...)`, while tests can record builder decisions
  before VPN consent or tunnel establishment.
- Added `VpnBuilderExcludedApplicationsInstrumentedTest` using the real connected-device
  `PackageManager` and app preferences to prove system-app exclusion modes:
  `none` keeps visible system apps inside the VPN, `all` disallows all visible system apps except
  AdAway itself, and `allExceptBrowsers` preserves browser packages while disallowing the rest.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.worker.VpnBuilderExcludedApplicationsInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `3` tests on `adaway-api34-16g`.
- Adjacent VPN guardrails passed:
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsVpnSettingsInstrumentedTest,org.adaway.ui.home.HomeLeakStatusInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `2` tests on `adaway-api34-16g`.
- Focused source-security guardrail passed:
  `:app:testDebugUnitTest --tests org.adaway.security.SecurityHardeningTest
  --dependency-verification=strict --stacktrace`.
- `PREF-007` specialist agreed the package-private builder excluder seam is the smallest robust
  slice, avoids VPN consent, and does not require a physical device. They called out package
  visibility variability, so the test uses real visible package sets instead of fixed package
  names.
- `ADW-001` specialist recommended the next local slice: add a test-only launcher activity in the
  instrumentation APK with a known adware-prefix class, then prove the scanner detects that
  installed test package through the existing launcher package-visibility queries without adding
  `QUERY_ALL_PACKAGES`.
- Release-gate specialist classified the remaining P0 board:
  `RUNTIME-007` needs rooted-device hosts-apply smoke; `UPDATE-002` and `UPDATE-004` need signed
  APK/directRelease install evidence; `REL-001` can keep source/artifact reports current but still
  needs legal/provenance signoff; `REL-002` needs signed/tagged artifacts; `REL-003` needs a
  physical-device release smoke; `REL-004` needs a generated UX packet plus human signoff; and
  `REL-005` closes only after those upstream artifacts agree on release tag, APK, cert, hashes,
  UX packet, and source commit.

## Plan - 2026-06-27 ADW-001 Adware Scanner Device Fixture
- [x] Re-ground the scanner path from `AdwareLiveData`, `AdwareFragment`, `MoreFragment`, package
  visibility queries, and existing adware source contracts.
- [x] Add an androidTest-only launchable fixture activity whose class name starts with a known
  adware signature prefix.
- [x] Add connected UI proof that the real scanner detects the installed instrumentation package
  via the More tab without adding broad package visibility permissions.
- [x] Run compile, focused adware JVM contracts, connected fixture proof, and tracker hygiene.
- [x] Update `tasks/user-story-status.tsv` and this log; commit/push only after the slice passes.

## Review - 2026-06-27 ADW-001 Adware Scanner Device Fixture
- Added an androidTest-only launchable `com.airpush.fixture.AdwareFixtureActivity` and labeled the
  instrumentation APK `Airpush Test Fixture`. The component class starts with the existing
  `com.airpush.` adware prefix and is visible through the production app's existing
  launcher-intent package-visibility query.
- Added `AdwareScannerInstrumentedTest`, which first proves the target app can see the
  instrumentation package and fixture activity through `PackageManager`, then opens Home -> More
  -> Adware Scanner and waits for the real `AdwareFragment` list to show the fixture package and
  label.
- No production package-visibility permission was added. This closes the current launchable-app
  scanner behavior while preserving the boundary that non-launcher package discovery would need a
  separate product/privacy decision.
- Updated `README.md` so the permissions table no longer claims `QUERY_ALL_PACKAGES`; it now
  documents the launcher package-visibility query used by the manifest and scanner proof.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.adware.AdwareScannerInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `1` test on `adaway-api34-16g`;
  focused JVM
  `:app:testDebugUnitTest --tests org.adaway.ui.adware.AdwareLiveDataMatcherTest --tests
  org.adaway.ui.adware.AdwareUninstallIntentContractTest --dependency-verification=strict
  --stacktrace`.

## Plan - 2026-06-27 PREF-006 VPN Excluded Apps Device List
- [x] Re-ground `PrefsVpnExcludedAppsActivity`, `UserAppRecycleViewAdapter`, layout bindings, and
  the existing instrumentation package fixture.
- [x] Add connected proof that the real excluded-apps picker lists a visible non-system app and
  persists row toggle changes to `PreferenceHelper.getVpnExcludedApps(...)`.
- [x] Run compile, focused connected verification, and tracker hygiene.
- [x] Update `tasks/user-story-status.tsv` and this log; commit/push only after verification.

## Review - 2026-06-27 PREF-006 VPN Excluded Apps Device List
- Added `PrefsVpnExcludedAppsInstrumentedTest`, which uses the existing androidTest-only
  launchable fixture package as a real visible non-system app under the production app's launcher
  package-visibility query.
- The test launches the real `PrefsVpnExcludedAppsActivity`, verifies the fixture appears in the
  RecyclerView data, clicks the rendered row, and waits for `PreferenceHelper.getVpnExcludedApps`
  to include and then remove the fixture package after the second row toggle.
- This proves the user-app picker, row binding, switch listener, and persisted selected package
  names work together without adding broad package enumeration.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `1` test on `adaway-api34-16g`;
  adjacent connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsVpnSettingsInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `1` test on `adaway-api34-16g`.

## Plan - 2026-06-27 MORE-001 More Tools Entry Points
- [x] Recheck the current branch, PR CI, `MORE-001` tracker row, and project cache state before
  touching code.
- [x] Re-ground `MoreFragment`, `fragment_more.xml`, `PrefsActivity` backup routing, and existing
  connected lifecycle helpers.
- [x] Add connected proof that every More row reaches its intended app-owned destination, while
  keeping the external GitHub link guarded and non-flaky.
- [x] Run compile, focused connected More proof, adjacent crash-surface JVM guard, and tracker
  hygiene.
- [x] Update `tasks/user-story-status.tsv` and this log with evidence; commit/push only after the
  verified slice is clean.

## Review - 2026-06-27 MORE-001 More Tools Entry Points
- PR #6 CI was green before this slice: Analyze (cpp), Analyze (java), CodeQL, Connected Android
  tests, Development build, and Validate locales all passed.
- Added `MoreToolsEntryPointsInstrumentedTest`, a connected UI proof that launches the real
  `HomeActivity`, navigates to More, clicks each row, and verifies the actual destination:
  `DomainCheckerFragment`, `LogActivity`, `ListsActivity`, Sources tab,
  `AdwareFragment`, `PrefsMainFragment`, `PrefsBackupRestoreFragment`, and `AboutActivity`.
- The GitHub row is covered without depending on an external browser UI. The test intercepts
  `ACTION_VIEW https://github.com/AdAway/AdAway` when the emulator has a handler; otherwise it
  verifies Home remains resumed through the guarded no-handler path. The source-level
  `CrashSurfaceHardeningTest` still guards the resolver and exception handling contract.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.more.MoreToolsEntryPointsInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `9` tests on `adaway-api34-16g`;
  and focused JVM
  `:app:testDebugUnitTest --tests org.adaway.security.CrashSurfaceHardeningTest
  --dependency-verification=strict --stacktrace`.
- `MORE-001` is now covered by connected UI flow. The broader external release gates remain open
  and unchanged: rooted hosts apply, direct signed self-update, release artifacts, physical smoke,
  UX human signoff, legal/provenance, and final readiness aggregation.

## Plan - 2026-06-27 DOMAIN-003 Domain Checker Actions
- [x] Wait for PR #6 CI to finish on the More entry-point commit before starting another slice.
- [x] Re-ground Domain Checker UI, ViewModel mutation methods, runtime resolver semantics, and
  existing no-AI removal contracts.
- [x] Extend the connected runtime-truth test to click the visible Domain Checker actions and prove
  normalized user-rule effects.
- [x] Run compile, focused connected Domain Checker proof, no-AI/advice JVM contracts, and tracker
  hygiene.
- [x] Update `tasks/user-story-status.tsv` and this log with evidence; commit/push only after the
  verified slice is clean.

## Review - 2026-06-27 DOMAIN-003 Domain Checker Actions
- PR #6 CI was fully green on `ea5c9553` before this slice, including full Connected Android tests
  (`8m53s`) plus Analyze (cpp/java), CodeQL, Development build, and Validate locales.
- Extended `DomainCheckerRuntimeTruthTest` with a connected action flow that opens the real
  `DomainCheckerFragment`, enters messy pasted host strings, clicks Add to Allow List, Remove
  Allow List rule, Add to Block List, and Delete rule, then polls Room/runtime truth and visible
  status text for the expected result.
- The proof keeps Domain Checker as a non-AI runtime-truth tool. `AiSurfaceContractTest` remains
  the guard that the removed AI gate, AI UI/settings packages, AI layouts/resources, AI network
  config, and default README AI copy do not return.
- Red/green notes: the first action proof was too brittle because it expected exact rendered
  user-source copy; the second expected a null runtime type for unknown hosts after deleting a
  user block. The current resolver intentionally maps unknown/default-allowed runtime state to
  `ALLOWED` internally while Domain Checker renders it as Unknown when there is no explicit allow
  rule, so the final proof asserts the actual product contract.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest
  --dependency-verification=strict --stacktrace` finished `4` tests on `adaway-api34-16g`;
  and focused JVM
  `:app:testDebugUnitTest --tests org.adaway.ui.domainchecker.DomainCheckerViewModelAdviceTest
  --tests org.adaway.ui.home.AiSurfaceContractTest --dependency-verification=strict --stacktrace`.
- `DOMAIN-003` is now covered by connected UI flow. The remaining P0 board is still external:
  rooted hosts apply, direct signed self-update, release artifacts, physical smoke, UX human
  signoff, legal/provenance, and final readiness aggregation.

## Plan - 2026-06-27 PREF-011 Backup Provider Round Trip
- [x] Re-ground backup/restore Preferences, `BackupExporter`, `BackupImporter`, the androidTest
  provider fixture, and the existing SAF launch proof.
- [x] Extend the androidTest provider with a backup JSON document URI that supports real
  `ContentResolver` write/read streams without changing production backup code.
- [x] Strengthen `PrefsBackupRestoreSafInstrumentedTest` so it keeps the UI SAF launch checks and
  proves exporter/importer round-trip behavior through the provider URI.
- [x] Run compile, focused backup JVM contracts, focused connected backup proof, adjacent provider
  consumer proof, and tracker hygiene.
- [x] Update `tasks/user-story-status.tsv` and this log with evidence; commit/push only after the
  verified slice is clean.

## Review - 2026-06-27 PREF-011 Backup Provider Round Trip
- Added backup-document behavior to the androidTest-only `TestHostsContentProvider`. The existing
  `/success.txt` source fixture remains intact, and the new `/backup.json` URI supports
  cross-process `openOutputStream`, `openInputStream`, metadata query, and cleanup through the
  provider boundary.
- Extended `PrefsBackupRestoreSafInstrumentedTest` with a provider fixture smoke and a connected
  export-clear-restore proof. The test seeds a source plus blocked, allowed, and redirected user
  rules, exports them through `BackupExporter.exportToBackup(...)`, clears the seeded rows, imports
  from the same provider URI through `BackupImporter.importFromBackup(...)`, and waits for Room to
  show the restored source and user rules. It also asserts imported source redirects remain disabled
  by the backup importer policy.
- Red/green notes: a first attempt tried to make `Instrumentation.ActivityMonitor` return
  `RESULT_OK` into the `ActivityResultLauncher`, but this runner proved that monitor hits are a
  launch signal, not a reliable callback-delivery proof. The final slice keeps UI launch proof in
  the two existing connected tests, keeps callback wiring covered by `BackupSafContractTest`, and
  adds the missing runtime provider-stream proof through the public backup APIs.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused JVM
  `:app:testDebugUnitTest --tests org.adaway.model.backup.BackupSafContractTest --tests
  org.adaway.model.backup.BackupFormatSecurityTest --dependency-verification=strict --stacktrace`;
  focused connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsBackupRestoreSafInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `3` tests on `adaway-api34-16g`;
  adjacent connected
  `:app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateActionsInstrumentedTest
  --dependency-verification=strict --stacktrace` finished `2` tests on `adaway-api34-16g`.
- `PREF-011` is now covered by connected UI launch and provider stream proof. The remaining open
  board is still practical release work: rooted hosts apply, signed direct-release install/update,
  physical device smoke, release artifacts, legal/provenance, human UX signoff, and final readiness
  aggregation.

## Plan - 2026-06-27 CI Tracker Integrity Guard
- [x] Recheck PR #6 CI after the backup round-trip commit and confirm it is pending or green before
  touching another slice.
- [x] Inspect existing CI/test conventions for the lightest tracker guard that still runs in PR CI.
- [x] Add a focused JVM validator for `tasks/user-story-status.tsv` shape, unique IDs, P0 evidence,
  release-gate honesty, and `RUNTIME-010` fresh 5M closure.
- [x] Wire the validator into Android CI as a named fast gate.
- [x] Run focused verification, update tracker evidence, and only then commit/push the slice.

## Review - 2026-06-27 CI Tracker Integrity Guard
- PR #6 CI was green on `d2369d24` before the backup slice; after pushing backup commit
  `ae2b50d0`, PR checks were pending with no failures before this tracker-guard slice started.
- Added `UserStoryStatusTrackerTest`, a fast JVM contract for the canonical
  `tasks/user-story-status.tsv`. It guards the exact 15-column header, one row per unique story ID,
  required evidence fields, valid priorities, P0 evidence state, the external release/root/update
  gates that must remain open until real proof exists, and the fresh `RUNTIME-010` 5M benchmark
  closure.
- Wired Android CI to run the tracker validator as a named `Validate user-story tracker` step before
  the full unit suite. The full `./gradlew test` step still reruns it as part of the normal JVM
  gate, but the named step gives batched PRs a fast, obvious ledger failure.
- `REL-005` remains partial. This slice protects the final readiness ledger against accidental
  overclaiming; it does not replace tagged release artifacts, signed APK hashes, physical smoke,
  UX signoff, or legal/provenance proof.
- Focused verification passed:
  `:app:testDebugUnitTest --tests org.adaway.tasks.UserStoryStatusTrackerTest
  --dependency-verification=strict --stacktrace`.

## Plan - 2026-06-27 Discover Sources UX Matrix
- [x] Re-ground `DISC-001` and `SRC-001` from the canonical tracker and existing UX matrix harness.
- [x] Run the scripted UX matrix before editing to get real screenshot evidence.
- [x] Inspect the generated Discover and Sources large-font screenshots instead of relying only on
  the automated text/touch-target audit.
- [x] Fix any concrete visual/accessibility defect found in the Discover/Sources path.
- [x] Run focused source contract, compile, and the full UX matrix again before recording evidence.

## Review - 2026-06-27 Discover Sources UX Matrix
- Baseline evidence run:
  `scripts/run-ux-matrix.ps1 -OutputDir app/build/reports/ux-matrix-pr6
  -InstrumentationTimeoutSeconds 360` passed all `5` variants and generated `40` screenshots, but
  manual inspection of `font-1.6/ux-matrix/discover.png` and
  `font-1.6-rtl/ux-matrix/discover.png` showed the quick-start preset strip clipping the
  `Balanced Mode` chip at the right edge.
- Fixed `fragment_discover.xml` by replacing the horizontal quick-start strip with a labeled
  wrapping Material `ChipGroup`. This keeps Safe, Balanced, and Aggressive visible at 1.6 font
  scale instead of asking users to discover a hidden horizontal scroll.
- Strengthened `DiscoverPresetSubscriptionTest` so the persistent Discover header must use a
  wrapping chip group and must not regress back to a horizontally clipped preset strip.
- Verification passed:
  `:app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`;
  focused JVM
  `:app:testDebugUnitTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest
  --dependency-verification=strict --stacktrace`;
  and final UX matrix
  `scripts/run-ux-matrix.ps1 -OutputDir app/build/reports/ux-matrix-pr6-discover-wrap
  -InstrumentationTimeoutSeconds 360`, which passed all `5` variants.
- Manual screenshot inspection after the fix confirmed the 1.6 LTR and 1.6 RTL Discover screenshots
  show `Safe Mode`, `Balanced Mode`, and `Aggressive Mode` fully visible. The 1.6 Sources screenshot
  remained readable; broader human UX signoff is still tracked under `REL-004`.

## CTO Jira Board - 2026-06-27 Remaining Goal Work

### External-Blocked Release Gates
- [ ] ADA-P0-ROOT-007 / `RUNTIME-007`: run rooted-device hosts apply smoke with real remount/write
  evidence. Fresh API 34 `-writable-system` emulator probe now proves `adb root`, `adb remount`,
  and shell-level `/system/etc/hosts` write/restore, but the app/libsu path still runs as the
  `u0_a` app UID. Closure remains blocked until a rooted physical, Magisk/root-manager emulator, or
  trusted target grants root to `org.adaway`.
- [ ] ADA-P0-UPD-002 / `UPDATE-002`: run signed APK self-update install smoke with verified APK hash
  and signing certificate evidence. Blocked until signed release/test artifact exists.
- [ ] ADA-P0-UPD-004 / `UPDATE-004`: run `directRelease` install/update gate proof. Blocked until
  signed directRelease artifact and install target exist.
- [ ] ADA-P0-REL-001 / `REL-001`: complete legal/provenance signoff. Local source reports passed;
  closure remains blocked on legal/license review.
- [ ] ADA-P0-REL-002 / `REL-002`: run tagged release artifact verification. Blocked until a real
  tagged release artifact set exists.
- [ ] ADA-P0-REL-003 / `REL-003`: run physical-device release smoke. Blocked until physical device
  and release APK are available.
- [ ] ADA-P0-REL-004 / `REL-004`: collect human-reviewed UX matrix signoff. Local matrix scripts
  work; closure requires checked screenshot packet and human approval.
- [ ] ADA-P0-REL-005 / `REL-005`: aggregate final readiness report. Blocked until upstream release,
  physical smoke, UX, and license artifacts exist.

### Locally Completable Proof Gaps
- [x] ADA-P1-VPN-009 / `RUNTIME-009`: prove full VPN lifecycle start/stop/resume when consent is
  already granted, or document the remaining device-precondition boundary more tightly.
- [x] ADA-P1-LOG-001 / `LOG-001`: prove DNS log rows from packet-processor/proxy traffic,
  beyond direct `VpnModel` log generation.
- [x] ADA-P1-PREF-004 / `PREF-004`: audit root web server settings/native exposure and add a focused
  source or JVM guard for binding/auth/availability assumptions.
- [x] ADA-P1-SYS-001 / `SYS-001`: prove Quick Settings tile interaction with Android tile service,
  or add the strongest local source/connected boundary proof that avoids flaky system UI.
- [x] ADA-P1-SYS-003 / `SYS-003`: design hostile-sender/separately signed proof for exported command
  receiver signature-permission enforcement.
- [x] ADA-P1-SYS-004 / `SYS-004`: strengthen real app-upgrade/package-replaced behavior proof if a
  non-release install flow can safely simulate it.
- [x] ADA-P1-ABOUT-001 / `ABOUT-001`: review license/about copy after the latest license-boundary
  reports and keep GPL/provenance language honest.
- [x] ADA-P2-LIST-007 / `LIST-007`: add device/visual proof for loading, empty, error, retry, and
  no-match custom-rules states.
- [x] ADA-P2-PREF-010-NOTIF-003 / `PREF-010`, `NOTIF-003`: design a safer API 33/34 notification
  permission proof that does not kill instrumentation.
- [x] ADA-P2-PREF-013 / `PREF-013`: add Android backup-agent restore-side-effect contract for
  preferences/rules, or document why platform backup remains manual.
- [x] ADA-P2-NOTIF-001-002 / `NOTIF-001`, `NOTIF-002`: prove notification channel/update-alert
  contracts while leaving permission-state UX to the safer `PREF-010/NOTIF-003` harness.
- [x] ADA-P2-ADW-003 / `ADW-003`: define signature freshness ownership/cadence or document static
  confidence limits.

### CTO Subagent Dispatch Results
- Runtime lead: closed the local `RUNTIME-009` paperwork by documenting the prepared-device VPN
  consent boundary, while keeping the full live tunnel lifecycle as a manual release-smoke gate.
  Also completed the local `LOG-001` packet-processor/proxy-to-LogActivity bridge proof without
  production changes.
- System/security lead: completed `PREF-004`, `SYS-004`, `SYS-001`, and `SYS-003` local proofs.
  `SYS-003` now has connected denied-sender coverage; a separately signed fixture APK remains a
  future release-smoke enhancement, not a local blocker.
- Preferences/notifications/backup lead: closed the backup-agent eligibility contradiction earlier
  and strengthened `NOTIF-002`; notification permission mutation remains under `NOTIF-003/PREF-010`
  until a safer API 33/34 harness exists.
- Preferences/notifications lead: closed `PREF-010`/`NOTIF-003` with a safer non-mutating API 34
  harness. The app-owned Settings affordance is now tested without grant/revoke permission changes.
- Product UX lead: fix `LIST-007` first because loading resolved to a hidden state, leaving the
  user-facing list area blank during initial refresh.
- Product trust lead: closed `ADW-003` by making the scanner's static signature limit visible in
  the UI and executable in tests; this is intentionally not a claim of live adware intelligence.
- License/product lead: closed local `ABOUT-001` by adding visible GPL/MIT-boundary copy and source
  contracts. `REL-001` legal/provenance signoff remains the release gate.

### Verified Local Slices
- `LIST-007`: Added a visible loading spinner/copy, kept retry visible only for load failure, and
  added connected proof for empty/no-match list states. Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests org.adaway.ui.lists.type.ListsUiStateTest
  --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsSearchInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 2 connected tests.
- `PREF-013`: Enabled the declared `AppBackupAgent` by setting `allowBackup=true` and added a
  source/XML contract for manifest eligibility, constrained backup XML, helper registration, and
  export/restore ordering. Verification passed:
  `./gradlew --no-daemon :app:processDebugMainManifest :app:testDebugUnitTest
  --tests org.adaway.model.backup.AppBackupAgentContractTest --dependency-verification=strict
  --stacktrace`.
- `PREF-004`: Added connected Preferences proof that the dormant root webserver controls render
  disabled with unavailable copy, force both toggles off, and do not launch `https://localhost`
  from the disabled test row. Verification passed:
  `./gradlew --no-daemon :app:compileDebugJavaWithJavac
  :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsRootWebServerUnavailableInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.
- `LOG-001`: Added connected packet bridge proof that a blocked DNS query goes through
  `VpnPacketProcessor` and `DnsPacketProxy`, queues the blocked response, records the VPN log host,
  and renders that host in `LogActivity`. Verification passed:
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.worker.VpnPacketProcessorRuntimeTruthTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.
- `SYS-001`: Added connected Quick Settings tile contract proving installed service metadata,
  `BIND_QUICK_SETTINGS_TILE`, `ACTION_QS_TILE` resolution, and a conditional add/click/remove shell
  path that cleans up when SystemUI accepts tile commands but does not dispatch the callback.
  Verification passed in the focused 4-test system batch on `adaway-api34-16g` with 1 expected
  SystemUI dispatch skip.
- `SYS-004`: Added connected package-replaced receiver proof that persisted hosts/app/filter-set
  schedule preferences repair their WorkManager jobs after `ACTION_MY_PACKAGE_REPLACED`.
  Verification passed in the focused 4-test system batch on `adaway-api34-16g`.
- `NOTIF-002`: Strengthened the JVM notification contract for actionable hosts/app update alerts:
  distinct IDs, expected activity targets, immutable content intents, auto-cancel, low priority, and
  notification-disabled early return. Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.helper.NotificationHelperContractTest --tests
  org.adaway.tasks.UserStoryStatusTrackerTest --dependency-verification=strict --stacktrace`.
- `ADW-003`: Added visible scanner copy explaining that adware detection uses a static known-prefix
  table, may miss newer SDKs, and requires signature review before each release. Guarded the copy and
  layout with `AdwareLiveDataMatcherTest`, then extended the connected scanner proof to assert the
  notice is visible while retaining the Airpush fixture detection proof. Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.adware.AdwareLiveDataMatcherTest --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.adware.AdwareScannerInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.
- `ABOUT-001`: Added visible About copy stating the current app license is GPL-3.0-or-later and MIT
  relicensing is unavailable until GPL-derived code, assets, and notices are cleared. Added
  `AboutLicenseBoundaryContractTest` to guard About copy, `THIRD_PARTY_LICENSES.md`, and the MIT plan
  against accidental current-MIT claims, and extended the connected About smoke to assert the new copy
  through Preferences. Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.about.AboutLicenseBoundaryContractTest --tests
  org.adaway.tasks.UserStoryStatusTrackerTest --dependency-verification=strict --stacktrace`,
  `java .github/workflows/AndroidLocaleChecker.java app/src/main/res/values/strings.xml`, and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.about.AboutActivitySmokeInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.
- `PREF-010`/`NOTIF-003`: Extracted a pure notification-settings visibility helper and added a
  source/JVM contract proving Android 13+ denied-permission logic, package-scoped
  `ACTION_APP_NOTIFICATION_SETTINGS` intent wiring, visible copy, and no runtime permission mutation.
  Extended `PrefsUpdateSchedulingInstrumentedTest` with a non-mutating connected proof that the row
  visibility matches the current API 34 permission state. Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.ui.prefs.PrefsUpdateNotificationPermissionContractTest --dependency-verification=strict
  --stacktrace` and `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsUpdateSchedulingInstrumentedTest#notificationSettingsPreferenceMatchesCurrentPermissionStateWithoutMutation
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.
- `RUNTIME-009`: Kept the automated proof boundary honest: JVM monitor/watchdog/idle lifecycle tests
  cover monitor recovery, watchdog restart behavior, and idle timeout semantics; the connected
  lifecycle test covers heartbeat start/stop and contains a full start/stop/resume path that runs
  only on a prepared device with AdAway VPN consent already granted and no other active VPN. The
  consent-gated boundary is documented in
  `tasks/benchmarks/2026-06-27-runtime009-vpn-lifecycle-boundary.md`.
- `SYS-003`: Added connected denied-sender proof for the exported command receiver. The test injects
  a recording `AdBlockModel`, sends a direct command broadcast from the instrumentation package
  without `org.adaway.permission.SEND_COMMAND`, accepts either platform `SecurityException` or
  ignored delivery, and asserts `AdBlockModel.apply()` is not reached. Verification passed:
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.broadcast.CommandReceiverSecurityInstrumentedTest#packageWithoutCommandPermissionCannotDeliverCommandBroadcast
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.
- `RUNTIME-007`: Re-probed the local API 34 emulator for rooted hosts-file apply viability.
  Superseded evidence now shows `adb root`, `adb remount`, and shell-level `/system/etc/hosts`
  write/restore work on a fresh `-writable-system` `adaway-api34-16g` launch. The remaining blocker
  is app-granted root: the opt-in `RootModelApplyInstrumentedTest` compiled, but
  `Shell.getShell()`/`Shell.cmd("id")` still ran as `u0_a` before any app-level hosts mutation; a
  temporary `/system/xbin/su` mode bootstrap from `4750` to `4755` did not change that. The hosts
  hash and `su` mode were restored. The rooted-hosts apply smoke remains blocked until a rooted
  physical device, Magisk/root-manager emulator, or trusted target grants root to `org.adaway`.

## Review - 2026-06-27 Filter Catalog Preset Safety
- CTO split this slice across three read-only expert lanes: catalog/product quality,
  runtime/source-generation safety, and release tracker evidence. Runtime/DB review found the PR
  branch already has the stronger active-generation carry-forward and active runtime-query proofs,
  so no duplicate connected DB test was added in this catalog slice.
- `DISC-001`/`DISC-002`/`ONB-002`: Made `OISD Full` opt-in instead of a first-run default,
  removed stale/browser-syntax Israeli/Hebrew static catalog entries, kept `EasyList Hebrew
  (hosts)`, and changed Balanced/Aggressive presets from broad category sweeps to curated sets.
- Added `FilterListCatalogPresetTest` to guard that defaults exclude OISD, Balanced stays moderate,
  Aggressive is curated instead of "everything", social/YouTube/device/service/regional lists stay
  opt-in, and Hebrew regional coverage uses the hosts-compatible feed.
- Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.model.source.FilterListCatalogPresetTest --tests
  org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests
  org.adaway.ui.onboarding.DefaultListsSubscriberTest --tests
  org.adaway.tasks.UserStoryStatusTrackerTest --dependency-verification=strict --stacktrace`.
  Current PR CI was green before this local commit; re-check CI after push.

## Review - 2026-06-27 LIST-007 Paging Failure State Proof
- `LIST-007`: Added a connected Your Lists proof for the remaining load-failure/retry visual path.
  The test temporarily renames `hosts_lists` before the PagingSource refresh, waits for the visible
  load-failed title/message and Retry button, restores the table, taps Retry, and verifies the
  blocked list rows render again.
- This closes the prior local proof gap for loading/empty/error/no-match states without changing
  production UI behavior; the existing state resolver and layout remain the source of truth.
- Verification passed:
  `./gradlew --no-daemon :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.lists.ListsSearchInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 3 connected tests.

## Plan - 2026-06-27 CTO Convergence Notification Slice
- [x] Re-ground PR #7 CI, dirty state, and remaining partial tracker rows.
- [x] Split the remaining question set across expert lanes: notifications, release/update gates,
  runtime scale, and tracker triage.
- [x] Close the strongest locally finishable slice without touching manual-only release gates:
  `NOTIF-001` notification channel upgrade behavior.
- [x] Run focused connected proof and update canonical evidence.

## Review - 2026-06-27 NOTIF-001 Existing Channel Upgrade Proof
- `NOTIF-001`: Strengthened `NotificationHelperChannelInstrumentedTest` from fresh metadata only to
  two connected proofs: production Updates, FilterLists, and VPN channel metadata on a clean install;
  and an Android channel-upgrade probe showing an existing low-importance FilterLists-style channel
  is not silently upgraded to default importance by recreating the channel. This keeps the app-owned
  behavior honest without mutating `POST_NOTIFICATIONS`.
- The first overbroad assertion intentionally failed on device: Android allowed an app-created,
  unmodified high-importance channel to be lowered to the app's requested low importance. The kept
  proof was narrowed to the product risk that matters here: older/existing low-importance channels
  cannot be upgraded silently, so users keep notification settings control through Android Settings.
- Verification passed:
  `./gradlew --no-daemon :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.helper.NotificationHelperChannelInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 2 connected tests.
- CTO triage result: after this local slice, the convergence blockers are primarily release
  operations and real devices/artifacts: signed direct-release/update artifacts, rooted writable
  hosts apply, physical release smoke, legal/provenance signoff, human UX matrix signoff, and final
  readiness aggregation.

## Plan - 2026-06-28 Update Release Gate Preflight
- [x] Re-ground the active PR worktree instead of editing the dirty `master` checkout.
- [x] Re-check PR #7 CI before starting new work.
- [x] Verify the local app-update release contracts for `UPDATE-002` and `UPDATE-004`.
- [x] Run unsigned `assembleDirectRelease` as a fail-closed preflight.
- [x] Update canonical release-gate evidence without claiming signed install smoke is done.

## Review - 2026-06-28 UPDATE-002/UPDATE-004 Release Preflight
- `UPDATE-002`: Revalidated the APK verifier unit contract and recorded that signed device install
  smoke remains open until a real signed artifact exists. The row now points at
  `tasks/benchmarks/2026-06-28-update-release-preflight-evidence.md` instead of saying the local
  verifier retest was not started.
- `UPDATE-004`: Revalidated the direct APK boundary source contract: normal builds do not declare
  `REQUEST_INSTALL_PACKAGES`, `directRelease` owns that permission, runtime self-update is gated by
  `BuildConfig.DIRECT_APK_UPDATES_ENABLED` and the AdAway store, and the APK receiver checks unknown
  app install permission before launching install UI.
- Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.model.update.ApkIntegrityVerifierTest --tests
  org.adaway.security.SecurityHardeningTest.atk34_apkSelfUpdateRequiresInstallPermissionAndAdAwayStoreBoundary
  --dependency-verification=strict --stacktrace`.
- Fail-closed preflight passed by failing as expected: unsigned
  `./gradlew --no-daemon :app:assembleDirectRelease --dependency-verification=strict --stacktrace`
  exited `1` with `Release and release-SBOM builds require signingStoreLocation,
  signingStorePassword, signingKeyAlias, and signingKeyPassword.`
- Remaining release proof is external: produce a signed direct-release artifact, verify manifest/APK
  hash/signing certificate evidence, run the install/update path on a target device, and feed those
  reports into the release readiness aggregation.

## Plan - 2026-06-28 PREF-004 Dormant Webserver Closure
- [x] Re-read the current `PREF-004` row, `PrefsRootFragment`, connected test, and webserver
  hardening contracts.
- [x] Convert the row from partial to covered for the current product behavior: dormant and
  unavailable unless a future webserver product/security project reintroduces it.
- [x] Re-run focused JVM hardening contracts and connected Preferences proof.
- [x] Re-run tracker guard and hygiene.

## Review - 2026-06-28 PREF-004 Dormant Webserver Closure
- `PREF-004`: The current app-owned behavior is not a half-enabled webserver; it is an intentionally
  dormant unavailable feature. `PrefsRootFragment` disables the switch, test row, and icon toggle,
  forces the toggles off, and avoids launching `https://localhost` while the native executable is
  absent.
- The tracker now says `Covered by connected dormant-boundary proof` and keeps the security
  caveat explicit: re-enabling a native localhost webserver would be a new product/security project,
  not an open bug in current behavior.
- Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.security.SecurityHardeningTest.atk30_noPackagedWebServerCredentialMaterial --tests
  org.adaway.security.SecurityHardeningTest.atk30_bootAndRootDoNotStartDormantWebServer
  --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.prefs.PrefsRootWebServerUnavailableInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 1 connected test.

## Plan - 2026-06-28 NOTIF-002 Non-Mutating Alert Proof
- [x] Use the clean PR worktree and avoid the dirty `master` checkout.
- [x] Split the work across expert lanes for notification proof, release-tracker triage, and
  environment/CI risk.
- [x] Strengthen the update-alert builder so the notification object can be inspected without
  posting or mutating runtime notification permission.
- [x] Add connected proof for distinct actionable hosts/app alerts and current permission-state
  posting behavior.
- [x] Update tracker evidence only after focused JVM and connected proofs pass.

## Review - 2026-06-28 NOTIF-002 Non-Mutating Alert Proof
- `NOTIF-002`: Refactored `NotificationHelper` so hosts/app update alert builders are inspectable
  package-private contracts while the public posting methods still guard on
  `NotificationManager.areNotificationsEnabled()` before notifying.
- Extended `NotificationHelperChannelInstrumentedTest` from channel-only coverage to four connected
  proofs: channel metadata, existing-channel upgrade immutability, distinct actionable hosts/app
  update notification builders, and current permission-state posting/no-post behavior without
  `POST_NOTIFICATIONS` grant/revoke mutation.
- The canonical tracker now marks `NOTIF-002` covered for the app-owned alert/channel contract while
  keeping Android notification permission UX under `NOTIF-003`/`PREF-010`.
- Verification passed:
  `./gradlew --no-daemon :app:testDebugUnitTest --tests
  org.adaway.helper.NotificationHelperContractTest :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace` and
  `./gradlew --no-daemon :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.helper.NotificationHelperChannelInstrumentedTest
  --dependency-verification=strict --stacktrace` on `adaway-api34-16g` with 4 connected tests.

## Plan - 2026-06-28 PREF-013 Platform Backup Restore Smoke
- [x] Re-check whether the API 34 emulator exposes a usable Android backup transport before
  leaving `PREF-013` as manual-only.
- [x] Add a phase-gated instrumentation proof that seeds backup state and later asserts restored
  state without pretending normal CI alone proves platform restore.
- [x] Run the real shell sequence: enable Backup Manager, select local transport, `bmgr backupnow`,
  clear app data, restore from the local restore set, and assert the restored app state.
- [x] Update the canonical tracker with exact evidence and keep cloud/OEM transport availability as
  an OS-owned boundary.

## Review - 2026-06-28 PREF-013 Platform Backup Restore Smoke
- `PREF-013`: Added `AppBackupAgentPlatformInstrumentedTest`, a phase-gated connected proof for
  Android Backup Manager restore. The seed phase writes a SharedPreferences probe plus source,
  blocked, allowed, and redirected user rules. The assert phase verifies the preference and rules
  after platform restore.
- The accepted proof uses manual APK install and `adb shell am instrument`, because Gradle
  `connectedDebugAndroidTest` cleans up the target package before shell `bmgr backupnow` can run.
- Verification passed on `adaway-api34-16g`: `bmgr enable true`, selected
  `com.android.localtransport/.LocalTransport`, seed phase `OK (1 test)`, `bmgr backupnow
  org.adaway` succeeded for `@pm@` and `org.adaway`, `pm clear org.adaway` succeeded,
  `bmgr restore 1 org.adaway` finished with status `0`, and assert phase `OK (1 test)`.
- The canonical tracker now marks `PREF-013` covered for the app-owned BackupAgent contract while
  keeping cloud account availability and OEM transport behavior as OS-owned boundaries.

## Plan - 2026-06-28 REL-001 Debug Artifact Boundary CI Guard
- [x] Check whether any remaining release gate can be locally strengthened without pretending debug
  artifacts are signed release artifacts.
- [x] Prove a debug-safe CycloneDX SBOM path with `:app:cyclonedxBom` while preserving the
  release-gated `:app:generateSbom` fail-closed behavior.
- [x] Add Android CI steps that inspect the built debug APK plus development SBOM using
  `check-license-boundary.ps1 -StrictArtifacts`.
- [x] Add JVM workflow guards and update the canonical tracker/evidence without closing legal,
  signed-release, physical-device, or root gates.

## Review - 2026-06-28 REL-001 Debug Artifact Boundary CI Guard
- `REL-001`: Android CI now generates a development CycloneDX SBOM, checks
  `app/build/outputs/apk/debug/app-debug.apk` plus `app/build/reports/cyclonedx/bom.json` with
  strict artifact mode, and uploads both the boundary report and SBOM.
- The release boundary remains honest: unsigned `:app:generateSbom` still fails closed on missing
  release trust material, and this debug artifact proof does not replace signed release APK/SBOM,
  attestation, or legal/provenance clearance.
- Local verification passed: `:app:assembleDebug :app:cyclonedxBom` succeeded; strict artifact
  boundary report inspected 1119 APK entries, 265 APK resources, 116 SBOM components, and found
  Issues 0 for debug APK SHA-256
  `cc587365535bae924e7a12cd0f3c35b58fb6595320243c6f37b37580b1e26771`.

## Plan - 2026-06-28 UPDATE-004 DirectRelease Dry-Run CI Guard
- [x] Probe whether `directRelease` packaging can be exercised locally without production signing
  secrets.
- [x] Reject the mismatched store/key password dry-run and use a shared ephemeral password that
  Android packaging can read.
- [x] Add Android CI directRelease dry-run packaging with a temporary keystore and public key
  derived from the dry-run certificate.
- [x] Run strict artifact-boundary checking against the dry-run `app-directRelease.apk` plus release
  CycloneDX SBOM while keeping production signed artifact/device gates open.

## Review - 2026-06-28 UPDATE-004 DirectRelease Dry-Run CI Guard
- `UPDATE-004` / `REL-002`: Added a pull-request CI dry-run that builds
  `:app:assembleDirectRelease :app:generateSbom` with an ephemeral keystore. This exercises
  directRelease manifest merge, release signing configuration, R8/minification, lint-vital, and
  release SBOM generation without production secrets.
- The first local attempt with different store/key passwords failed at `:app:packageDirectRelease`,
  so the workflow deliberately uses one `DRY_RUN_SIGNING_PASSWORD` for keytool and Gradle signing
  properties.
- Local verification passed for the shared-password dry-run and strict artifact boundary:
  `app-directRelease.apk` SHA-256
  `287b535363e1c1672978ff94117d1a129e6f12654c4ff6cb0f5d4ee1fd73722e`,
  release SBOM SHA-256
  `e99220606350d95ae2be18b1c001a3f327d3b4ef463041e5397347daced92861`,
  847 APK entries, 202 APK resources, 105 SBOM components, Issues 0.

## Plan - 2026-06-28 CTO Expert Swarm Convergence Audit
- [x] Re-check PR #7 from the pushed head instead of restarting the plan.
- [x] Split the remaining work into expert lanes: CI/release workflow, release-gate challenger,
  and product/runtime Hebrew plus large-import audit.
- [x] Classify every open P0 gate as locally closable, locally strengthenable, or truly
  external/manual before taking more slices.
- [x] Accept the CTO board rule: do not spend more local churn on `RUNTIME-007` or `REL-003`
  without a real writable rooted target, physical device, and release artifact.
- [x] Wait for the latest PR connected Android test job to finish.
- [x] Record final CI evidence and commit only if the canonical task files change with verified
  facts.

## Review - 2026-06-28 CTO Expert Swarm Convergence Audit
- CI lane: PR #7 head `9d4b9d24` is green. CodeQL, Analyze cpp, Analyze java, Development
  build, and Connected Android tests passed. GitHub Actions run `28310433209` completed
  successfully; Development build passed in `6m17s`, Connected Android tests passed in `9m8s`,
  and the new `Run directRelease packaging dry run` plus strict dry-run artifact-boundary steps
  passed before the connected suite ran.
- Release-gate lane: no remaining P0 can be honestly closed by local-only work. `RUNTIME-007`
  requires an app-granted root target for `org.adaway`/libsu; `REL-003` requires a physical release
  device. `UPDATE-002`, `UPDATE-004`, `REL-001`, `REL-002`, `REL-004`, and `REL-005` are only
  locally strengthenable until real signed artifacts, legal/provenance review, human UX signoff,
  and upstream readiness reports exist.
- Product/runtime lane: the old large-entry issue was missing `root_host_entries_stage`
  population on the full parse/import path, forcing slow direct root export. It is already closed
  by the stage-backed import/export fix and fresh 5M connected benchmark evidence. Hebrew regional
  coverage is intentionally kept to the valid hosts-compatible EasyList Hebrew feed; stale
  AdGuard/browser-syntax Israeli lists are guarded against by catalog tests.
- Verification for this evidence-only slice passed: `git diff --check` and focused
  `UserStoryStatusTrackerTest` with strict dependency verification.

## Plan - 2026-06-28 REL-004 Current-Head UX Packet Refresh
- [x] Re-ground PR #7 head and confirm the remaining `REL-004` boundary is human signoff rather
  than missing local packet generation.
- [x] Boot `adaway-api34-16g` and run `scripts/run-ux-matrix.ps1` against current head
  `e762f2b4`.
- [x] Hash/count the packet, run `verify-ux-signoff.ps1` preflight, and spot-check high-risk
  large-font/RTL screenshots.
- [x] Update canonical REL-004 evidence while keeping the release gate open for checked human
  review.

## Review - 2026-06-28 REL-004 Current-Head UX Packet Refresh
- Superseded by the later `REL-004/REL-005 Source-Under-Test Refresh` at source commit
  `1a25953daf58d51b56d796e641e34e6cf34bfc65`; this earlier `e762f2b4` packet remains archival
  evidence only.
- `REL-004`: Fresh current-head UX matrix passed all five variants on `adaway-api34-16g`:
  baseline, `font-1.3`, `font-1.6`, `font-1.3-rtl`, and `font-1.6-rtl`.
- Generated `40` screenshots and
  `app/build/reports/ux-matrix-2026-06-28-rel004-current-head/ux-matrix-review.md` from source
  commit `e762f2b4f73bcd1e20342572a2285c23d9c3c52b`.
- Review packet SHA-256:
  `0fb50e3a0781ca455908612fc0f9914d2c839ed28a9e481267c05d51e633f2bf`.
- `verify-ux-signoff.ps1` preflight failed as expected with matching current/review packet source
  commits, `Status: failed`, `Checked items: 0`, `Unchecked items: 45`, and
  `Review packet still has unchecked items.`
- Spot-checked `font-1.6-rtl` Home, Discover, Sources, and `font-1.6` More; no obvious clipping,
  hidden bottom navigation, or unreachable primary action was found. This remains a non-human
  sanity check and does not close human UX signoff.

## Plan - 2026-06-28 REL-005 Current-Head Readiness Preflight
- [x] Re-ground PR #7 after the current-head UX packet refresh.
- [x] Run the release-readiness verifier with missing release/smoke reports, the failed UX
  signoff preflight, the current UX review packet, and the dry-run license-boundary report.
- [x] Record the expected fail-closed readiness output without treating it as final release
  readiness.
- [x] Add a tracker guard so `REL-005` keeps naming the current-head packet hash and upstream
  blockers.

## Review - 2026-06-28 REL-005 Current-Head Readiness Preflight
- Superseded by the later `REL-004/REL-005 Source-Under-Test Refresh` at source commit
  `1a25953daf58d51b56d796e641e34e6cf34bfc65`; this earlier `0fb50e3a` UX packet readiness
  preflight remains archival evidence only.
- PR #7 head `69623d1f` was green before this slice: Development build `6m34s`, Connected Android
  tests `8m56s`, CodeQL, Analyze cpp, and Analyze java all passed.
- `scripts/verify-release-readiness.ps1` failed closed as intended when fed the refreshed current
  UX packet plus non-final inputs: missing `release-artifacts/verification-report.md`, missing
  `release-smoke/release-smoke-report.md`, failed UX signoff preflight, and dry-run
  license-boundary report.
- The generated preflight report recorded matching UX packet hashes
  `0fb50e3a0781ca455908612fc0f9914d2c839ed28a9e481267c05d51e633f2bf`, failed release artifact,
  failed physical smoke, failed UX signoff, failed license boundary, and `Issues: 7`.
- `REL-005` remains open. It still requires real release artifact, physical smoke, checked human UX
  signoff, and release artifact license-boundary reports from one source commit and release
  identity.

## Plan - 2026-06-28 RUNTIME-007 Writable Emulator Re-Probe
- [x] Launch `adaway-api34-16g` with `-writable-system` and verify boot/root/remount state.
- [x] Prove controlled shell-level `/system/etc/hosts` write and exact restoration.
- [x] Add an opt-in connected `RootModel.apply()` smoke that is skipped in normal CI and only runs
  with `rootHostsApplySmoke=true`.
- [x] Run the app-level smoke and document whether the emulator provides app-granted root.
- [x] Update the canonical tracker without closing `RUNTIME-007` from shell-only evidence.

## Review - 2026-06-28 RUNTIME-007 Writable Emulator Re-Probe
- The old read-only conclusion is stale. A fresh writable-system emulator launch reached
  `boot_completed=1`, accepted `adb root`, remounted `/system` with overlayfs read/write, and
  allowed a controlled shell write of `codex-root-write-smoke.local` into `/system/etc/hosts`.
  Restoring the backup returned the hosts SHA-256 to
  `425c3e713d5bae19b031bc8639c20c6a23e311a54647ba1824cbf45969a11ff4`.
- Added `RootModelApplyInstrumentedTest`, an explicit opt-in connected harness that seeds one
  blocked host, calls the real `RootModel.apply()` path, and restores `/system/etc/hosts` in
  teardown if it reaches mutation. Normal connected suites skip this test unless
  `rootHostsApplySmoke=true` is supplied.
- Verification passed for the harness compile:
  `./gradlew --no-daemon :app:compileDebugAndroidTestJavaWithJavac
  --dependency-verification=strict --stacktrace`.
- The opt-in smoke failed before mutation because this AOSP emulator does not grant root to the app:
  after `Shell.getShell()`, `Shell.cmd("id")` reported `u0_a194`/`u0_a196`, not `uid=0`.
  `/system/xbin/su` was shell-only (`4750 root:shell`); temporarily changing it to `4755` still
  left libsu in an untrusted app shell. `su` mode and `/system/etc/hosts` were restored.
- `RUNTIME-007` remains a real external gate, but now the ask is precise: provide a rooted physical
  device, Magisk/root-manager emulator, or trusted target where `org.adaway` gets app-granted root,
  then rerun the opt-in smoke.

## Plan - 2026-06-28 Android Release Candidate Scope
- [x] Decide whether rooted-hosts apply remains blocking for the Android release candidate.
- [x] Clarify iPhone/iOS support boundary before promising cross-platform installation.
- [x] Bump the Android app version for owner device testing.
- [x] Run focused version/release verification and push the release-candidate bump.

## Review - 2026-06-28 Android Release Candidate Scope
- Product decision: do not block the Android release candidate on `RUNTIME-007` because root usage
  is a niche path and the remaining blocker is external app-granted root, not an observed Android
  VPN-mode defect. Keep the opt-in `RootModelApplyInstrumentedTest` ready for any later rooted
  physical/Magisk target.
- iPhone/iOS is out of scope for this repository and release. This project builds an Android APK and
  depends on Android package, VPN service, WorkManager, notification, backup, and root/libsu APIs.
  An iPhone product would need a separate iOS implementation using Safari content blockers,
  DNS/VPN-style Network Extension approaches, or another Apple-approved architecture.
- Bumped the Android fork release-candidate version from `13.5.0`/`130500` to
  `13.5.1`/`130501` in `gradle/libs.versions.toml` and added a `13.5.1` changelog entry.
- Verification passed: `git diff --check`, `:app:processDebugMainManifest`,
  focused `ManifestTest`, `ReleaseReadinessScriptTest`, `UserStoryStatusTrackerTest`,
  `:app:assembleDebug`, and APK badging showed package `org.adaway`, versionCode `130501`,
  versionName `13.5.1`.

## Plan - 2026-06-28 FilterLists Row Toggle Fix
- [x] Reproduce the user-reported toggle issue from code: unsupported FilterLists row switches were
  disabled or bounced through the bulk-safe/manual-review gate.
- [x] Keep bulk subscribe DNS-safe while allowing explicit single-row toggles to resolve a direct
  URL and subscribe a limited-support source.
- [x] Update user-facing compatibility copy from `Manual review` to `Limited support` where the row
  can now be explicitly subscribed.
- [x] Add JVM and connected regression coverage for the actual switch path.
- [x] Update canonical task evidence and lessons.

## Review - 2026-06-28 FilterLists Row Toggle Fix
- Root cause: `DiscoverFilterListsFragment` reused the bulk-subscribe compatibility gate for the
  single-row switch. Unsupported/browser-syntax rows could be disabled in the UI, and the
  subscription method still returned `filterlists_manual_review_required` instead of adding the
  user-selected source.
- Fix: single-row toggles now stay enabled, resolve `FilterListsDirectoryApi` details, insert an
  enabled `HostsSource` with FilterLists provenance, and enqueue the normal source update. Bulk
  subscribe remains DNS-safe-only through `FilterListCompatibility.isBulkSafe(...)` and the worker.
- UX copy now labels these rows as `Limited support` and the review dialog says bulk subscribe skips
  the row by default, avoiding the old implication that the switch is a dead end.
- Connected proof passed on `adaway-api34-16g`:
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.discover.DiscoverUnsupportedReviewInstrumentedTest`.
- Focused JVM proof passed:
  `./gradlew testDebugUnitTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests org.adaway.model.source.FilterListCompatibilityTest --tests org.adaway.ui.hosts.CategorizedSourcesAdapterTest`.

## Plan - 2026-06-28 Ynet Browsing Compatibility
- [x] Investigate the reported `ynet.co.il` intermittent connection-lost behavior under AdAway.
- [x] Determine whether the risk is parser over-flattening, selected-list overblocking, VPN DNS
  handling, or a missing product allowlist for an essential browsing domain.
- [x] Add a focused regression proof before/with any behavior change.
- [x] Preserve ad-blocking value while avoiding full-site breakage.
- [x] Run focused verification, update canonical evidence, and commit/push only if verified.

## Review - 2026-06-28 Ynet Browsing Compatibility
- Live `https://www.ynet.co.il/` was externally reachable, so the user-visible intermittent
  connection-loss report is most consistent with local VPN/filter runtime overblocking rather than
  a site outage.
- Regional list inspection found intentional ad/tracker blocks such as `stats.ynet.co.il` and
  `totalmedia2.ynet.co.il`; the fix therefore adds exact allow entries only for core first-party
  Ynet browsing/media hosts and intentionally avoids `*.ynet.co.il`, `stats.ynet.co.il`,
  `p.ynet.co.il`, and `totalmedia2.ynet.co.il`.
- Existing installs are backfilled at app startup; when new site-compatibility rows are inserted,
  `SourceModel.syncHostEntries()` rebuilds runtime truth and invalidates the VPN rules cache.
- Fresh database creation and onboarding default-list subscription seed the same compatibility
  allowlist together with the existing WhatsApp/Telegram safety allowlist.
- Added a connected packet regression in `DnsPacketProxyRuntimeTruthTest`: a broad suffix block on
  `ynet.co.il` must still forward `www.ynet.co.il`, while `stats.ynet.co.il` remains locally
  blocked. The test compiles locally; execution is pending a working connected device.
- First pushed PR connected run caught a real startup race before tests executed:
  `SQLiteConstraintException: FOREIGN KEY constraint failed` from inserting site-compatibility user
  rows before the user source row existed. The allowlist now inserts/ignores the user source first,
  matching the normal `AppDatabase` user-list defaults.
- Verification passed with OpenJDK 21: full `./gradlew testDebugUnitTest`,
  `./gradlew assembleDebugAndroidTest`, and `./gradlew assembleDebug`.
- A fresh debug APK was copied to
  `/Users/steves/Downloads/AdAway/AdAway-13.5.1-debug-ynet-compat.apk`.
- Local connected execution was blocked by environment, not app code: after cold and wiped
  `adaway-api34-16g` launches, the emulator reached startup logging but never registered in
  `adb devices`; no emulator process was left running afterward.

## Plan - 2026-06-28 FilterLists Selected/All Bulk Actions
- [x] Split FilterLists bulk commands into explicit selected/current-view and all-directory actions.
- [x] Keep bulk subscribe DNS-safe-only while leaving unsupported rows to explicit single-row or
  manual-review paths.
- [x] Make destructive unsubscribe scope visible before mutation and support removing every
  FilterLists-derived source, not only rows currently loaded in the visible directory cache.
- [x] Add focused source/UI regression coverage and run local verification before pushing.

## Review - 2026-06-28 FilterLists Selected/All Bulk Actions
- Discover FilterLists now exposes two separate bulk scopes: `Subscribe selected` /
  `Unsubscribe selected` for the current filtered view, and `Subscribe all` / `Unsubscribe all`
  for the full loaded directory or all stored FilterLists-derived sources.
- Bulk subscribe remains DNS-safe-only. Unsupported browser-rule rows are still skipped by the
  worker and remain available through the explicit single-row review/add path.
- Selected subscribe passes the current search/tag/language/compatible filters plus exact visible
  IDs to `FilterListsSubscribeAllWorker`; all-directory subscribe clears those filters before
  enqueueing the worker.
- Selected unsubscribe resolves only sources matching the current view. All unsubscribe scans
  stored sources and removes every source carrying FilterLists provenance, including sources no
  longer visible in the current directory cache.
- Verification passed with OpenJDK 21 from `/opt/homebrew/opt/openjdk@21`:
  `./gradlew testDebugUnitTest --rerun-tasks --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests org.adaway.ui.discover.FilterListsSubscriptionStateTest --tests org.adaway.tasks.UserStoryStatusTrackerTest`,
  `./gradlew assembleDebugAndroidTest`, and `./gradlew assembleDebug`.
- First PR connected run caught one test-helper fragility after the new bulk-action row reduced
  visible RecyclerView space: `FilterListsVisibleBulkActionsInstrumentedTest` timed out waiting
  for the unsupported row. The helper now cycles RecyclerView adapter positions while waiting for
  row text instead of assuming every seeded row is attached in the first viewport; Android test APK
  compilation passed again with `./gradlew assembleDebugAndroidTest`.
- A fresh debug APK was copied to
  `/Users/steves/Downloads/AdAway/AdAway-13.5.1-debug-filterlists-bulk-actions.apk`
  with SHA-256 `4766e62e3a6ea86ce69d2f5e9dff83a2838a8cfffa2097e3e1faff873411ce9b`.

## Plan - 2026-06-28 FilterLists Checked Selection
- [x] Replace the ambiguous selected/current-view behavior with explicit row checkboxes.
- [x] Keep the existing one-row subscription switch separate from bulk row selection.
- [x] Route Subscribe selected and Unsubscribe selected through checked FilterLists IDs only.
- [x] Update source-contract and connected UI proof to require row selection before bulk actions.
- [x] Run focused JVM/APK verification, update canonical evidence, and push only if verified.

## Review - 2026-06-28 FilterLists Checked Selection
- User clarified that `Subscribe selected` should mean a visible tick/checkbox selection, not the
  currently filtered view. The previous scope split was functionally safe but product-ambiguous.
- `filterlists_import_item.xml` now includes `filterlistsItemSelectionCheckBox`; this checkbox
  controls bulk selection only. The existing row switch still controls the individual
  subscribe/unsubscribe path.
- `DiscoverFilterListsFragment` tracks checked IDs in `selectedListIds`, prunes hidden selections
  when search/tag/language/DNS-safe filters change, and enables selected bulk commands only when
  rows are checked.
- Selected subscribe now passes exact checked IDs into `FilterListsSubscribeAllWorker`; selected
  unsubscribe resolves only checked-row FilterLists source URLs. All-directory commands remain
  separate and explicit.
- Verification passed with OpenJDK 21 and `ANDROID_HOME=/Users/steves/.local/android-sdk`:
  `./gradlew testDebugUnitTest --rerun-tasks --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests org.adaway.ui.discover.FilterListsSubscriptionStateTest --tests org.adaway.tasks.UserStoryStatusTrackerTest`,
  `./gradlew assembleDebugAndroidTest assembleDebug`, and focused connected
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsVisibleBulkActionsInstrumentedTest`
  on `adaway-api34-16g`.
- First focused connected attempt exposed a test-helper issue: `performClick()` on the checkbox
  did not report success from the helper. The helper now toggles `CompoundButton` state directly
  on the UI thread, which fires the real row-selection listener; the rerun passed.
- A fresh debug APK was copied to
  `/Users/steves/Downloads/AdAway/AdAway-13.5.1-debug-filterlists-checkbox-selection.apk`
  with SHA-256 `35689a24ce47ad41c1889b5cb0a0661003c27b9dacac3d6da4e4d94016e6b6a5`.

## Plan - 2026-06-28 FilterLists Selected Button Responsiveness
- [x] Remove DNS-safe-count gating from the `Subscribe selected` enabled state.
- [x] Keep bulk safety checks on click so unsupported-only selected rows explain the boundary.
- [x] Add a connected regression for unsupported-only row selection enabling the button.
- [x] Run focused verification, refresh APK, update status evidence, and push.

## Review - 2026-06-28 FilterLists Selected Button Responsiveness
- User reported `Subscribe selected` stayed grey after checking multiple filters.
- Root cause: the selected subscribe button was enabled only when checked rows included at least
  one DNS-safe row and were not already all subscribed. Unsupported-only or already-covered
  selections therefore looked like the app ignored the checkbox.
- `Subscribe selected` now enables whenever one or more visible rows are checked and the UI is not
  busy. The click path still prevents unsafe bulk import and shows `No DNS-safe selected lists`
  for unsupported-only selections.
- Verification passed with OpenJDK 21 and `ANDROID_HOME=/Users/steves/.local/android-sdk`:
  `./gradlew testDebugUnitTest --rerun-tasks --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests org.adaway.ui.discover.FilterListsSubscriptionStateTest --tests org.adaway.tasks.UserStoryStatusTrackerTest`,
  `./gradlew assembleDebugAndroidTest assembleDebug`, and focused connected
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsVisibleBulkActionsInstrumentedTest`
  on `adaway-api34-16g`.
- The connected proof now first checks only an unsupported row, verifies `Subscribe selected`
  becomes enabled, and verifies tapping it shows `No DNS-safe selected lists` instead of leaving
  the button grey.
- A fresh debug APK was copied to `/Users/steves/Downloads/AdAway/AdAway_13.5.1.apk`
  with SHA-256 `7b2b212528dbd3bbe61b0f20c4a55c7b63862909cc7043f1ad61b56ba80fd224`.

## Plan - 2026-06-28 FilterLists Unsubscribe Responsiveness And Subscribed Filter
- [x] Make `Unsubscribe selected` responsive whenever rows are checked.
- [x] Add no-op feedback for selected unsubscribe when no checked row is subscribed.
- [x] Add `Show subscribed` filter backed by live FilterLists source metadata.
- [x] Compact filter/action controls so the list keeps more vertical room.
- [x] Run focused verification, refresh APK, update status evidence, and push.

## Review - 2026-06-28 FilterLists Unsubscribe Responsiveness And Subscribed Filter
- User reported the same grey-button problem for `Unsubscribe selected` and asked for a
  subscribed-only view plus more proportional controls.
- `Unsubscribe selected` now enables whenever visible rows are checked. If none of those checked
  rows are currently subscribed, tapping it shows `No subscribed selected lists` instead of staying
  grey or silently doing nothing.
- Added a `Show subscribed` switch next to `DNS-safe only`; it filters rows through the live
  FilterLists source URL/index state and re-filters immediately after subscribe/unsubscribe changes.
- The DNS-safe and subscribed switches now share one compact row, and bulk action buttons use
  shorter equal sizing so more of the list remains visible.
- Verification passed with OpenJDK 21 and `ANDROID_HOME=/Users/steves/.local/android-sdk`:
  `./gradlew testDebugUnitTest --rerun-tasks --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest --tests org.adaway.ui.discover.FilterListsSubscriptionStateTest --tests org.adaway.tasks.UserStoryStatusTrackerTest`,
  `./gradlew assembleDebugAndroidTest assembleDebug`, and focused connected
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsVisibleBulkActionsInstrumentedTest`
  on `adaway-api34-16g`.
- The connected proof now checks unsupported-only selection enables both selected actions, verifies
  `Unsubscribe selected` reports `No subscribed selected lists`, verifies `Show subscribed` reduces
  the list to the subscribed row, and verifies hidden selections are pruned when that filter hides
  rows.
- A fresh debug APK was copied to `/Users/steves/Downloads/AdAway/AdAway_13.5.1.apk`
  with SHA-256 `3abe5ea934ee683f6d47c96ca0176f42312fc16f9f93fafa8436f449a8c39f06`.

## Plan - 2026-06-29 FilterLists Unsupported Selected Review
- [x] Keep `Subscribe selected` enabled for checked unsupported rows without weakening DNS-safe
  automatic bulk import.
- [x] Route unsupported-only checked selections to the existing manual review/add dialog instead of
  the `No DNS-safe selected lists` dead end.
- [x] Prove the selected-row path with deterministic connected UI coverage and source-contract
  coverage.
- [x] Refresh `/Users/steves/Downloads/AdAway_13.5.1.apk` with the verified build.

## Review - 2026-06-29 FilterLists Unsupported Selected Review
- User reported that `Subscribe selected` now clicks but still says `No DNS-safe selected lists`
  and will not let the user continue.
- Root cause: the prior fix made the button responsive, but the click path still treated an
  unsupported-only checked selection as a terminal no-op. Single unsupported row taps already had a
  manual-review path, but checked-row bulk selection did not reuse it.
- `confirmSubscribeAll(false)` now keeps automatic bulk import DNS-safe, and when the checked scope
  has zero DNS-safe rows it collects unsupported selected summaries and opens the manual
  unsupported review flow. One unsupported row opens its review directly; multiple unsupported rows
  use the existing unsupported-list chooser before review.
- The focused connected proof now checks only the unsupported browser-rule row, verifies
  `Subscribe selected` opens the unsupported review disclosure with `Domain extraction only` and
  the resolved URL, closes it, then verifies mixed safe+unsupported selected bulk subscription
  still imports only the DNS-safe row and skips the unsupported row.
- Verification passed with OpenJDK 21 and `ANDROID_HOME=/Users/steves/.local/android-sdk`:
  `./gradlew testDebugUnitTest --tests org.adaway.ui.discover.DiscoverPresetSubscriptionTest`,
  `./gradlew assembleDebugAndroidTest`, and focused connected
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.hosts.FilterListsVisibleBulkActionsInstrumentedTest`
  on `adaway-api34-16g`.
- A fresh debug APK was copied to `/Users/steves/Downloads/AdAway_13.5.1.apk`
  with SHA-256 `c35353c4551281d356ce41b72329f4399dc74cb7d96341ec6f835075b0855e61`.
## Plan - 2026-10-12 Product Review And Release

Goal: review every first-party subsystem, log reproducible bugs, fix release-relevant
defects, verify custom filter URLs and core Android workflows, and deliver an updated APK.
This is a finite review and release pass; unavailable physical-device/signing evidence
must be reported explicitly rather than silently counted as passed.

- [x] Confirm clean baseline, remote revision, previous APK, and published release.
- [x] Review source parsing/downloads/database, DNS/VPN/root, app screens/backup,
  and update/release security in parallel; consolidate findings in one bug register.
- [x] Verify and improve URL-based filter selection, import, enable/disable, and errors.
- [x] Reproduce and fix confirmed release-relevant bugs with focused regression coverage.
- [x] Run JVM tests, Android lint/build, connected tests, and emulator smoke checks.
- [x] Bump version, document changes and limitations, build and verify the updated APK.
- [ ] Submit one reviewed PR, pass required CI, and publish the Android release candidate.
- [x] Resolve signing: owner confirmed the old key is unavailable and authorized a new
  identity. Protected local keys and eight GitHub secrets are configured. Older builds
  require backup/uninstall/reinstall; physical-device approval remains a stable-release gate.

Current bug register and verification ledger: `tasks/review-2026-10-12.md`.
PR 10 follow-up: the first protected run found two test synchronization failures.
Both fixtures are corrected without changing production behavior or removing
assertions. A fresh full API 34 run passed 297 tests with six intentional skips
and zero failures, including live VPN/DNS checks; protected CI remains open.
Historical root, physical-device, and legal/provenance gates above are not automatically
closed by this bounded Android candidate release.
