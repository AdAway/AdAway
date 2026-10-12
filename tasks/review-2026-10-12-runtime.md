# Runtime Review: 2026-10-12

Scope: VPN/DNS, VPN/root/adblocking models, hostname handling, and native integration.
Shared workspace on `codex/october-release-review`; no commits, pushes, Gradle runs,
UI/source/database/release edits, or vendored native rewrites.

## Finite Pass

- [x] Read CLAUDE.md and lessons; inspect runtime call paths and existing tests.
- [x] Repair evidenced DNS framing, mapping, synthetic answers, and resource ownership.
- [x] Repair cancellation/failure cleanup and root command failure propagation.
- [x] Add focused regressions; inspect diffs and run available non-Gradle checks.
- [x] Record findings, coverage, limits, and main-reviewer test commands.

## Initial Evidence

- `DnsQuery.handleResponse()` receives into 1024 bytes and forwards the whole buffer.
- `DnsQuery` discards the ParcelFileDescriptor wrapper and only closes its socket.
- `DnsQueryQueue` lacks teardown; its capacity check is off by one.
- `DnsServerMapper` accepts addresses solely by their final byte; IPv6-only networks
  with multiple resolvers can lose every resolver when IPv6 preference is off.
- `DnsPacketProxy` synthesizes A/AAAA regardless of QTYPE and resolves redirect strings
  using DNS on the packet thread.
- `VpnWorker` ignores failed socket protection, polls indefinitely with watchdog off,
  does not handle null establishment, and retains old queries/device writes on reconnect.
- `VpnService.onDestroy()` does not stop its worker; queued statuses/network callbacks
  can act after an intentional stop.
- `RootModel.copyHostsFile()` runs dd/chown/chmod as separate shell commands, allowing
  a later success to mask an earlier failure.

## Native Boundary

`settings.gradle` includes only app/sentrystub. The app does not depend on tcpdump;
root capture is disabled by `TCPDUMP_CAPTURE_ENABLED = false`; webserver availability
is hard-disabled and startup does not execute a binary. Standalone tcpdump build glue
and old vendor sources were inspected but are not part of the app build and are not
being rewritten or enabled.

## Confirmed Fixes and Proofs

| Priority | Confirmed behavior and completed patch | Regression proof |
| --- | --- | --- |
| P1 | DNS replies larger than 1024 bytes were truncated, while short replies gained zero padding. Receive a full UDP payload and forward only its actual length. | `DnsQueryResponseTest.shortResponseHasNoTrailingPadding`, `largeResponseIsNotTruncatedAt1024Bytes` use real loopback UDP, including a 4096-byte payload. |
| P1 | DNS polling descriptors were not owned/closed; reconnect retained queries and pending device writes. Retain and close the descriptor, drain queues on tunnel exit, enforce the 1024-query limit, expire against a monotonic clock, and bound poll waits. Failed poll descriptors are retired without a blocking receive. | `DnsQueryQueueTest` adds capacity and teardown cases; `VpnWorkerSocketTest.idlePollsAreBoundedEvenWhenWatchdogIsDisabled`; Android-only `DnsQueryDescriptorTest` covers descriptor closure and poll errors. |
| P1 | Unconnected forwarding sockets accepted any sender, ignored failed VPN protection, and did not check the returned DNS transaction/question. Require protection before connecting/sending, connect to the requested resolver/port, validate the response, and restrict the active proxy to UDP/53 standard single-question queries. | `VpnWorkerSocketTest` protection/connection cases; `DnsPacketProxyResponseTest.upstreamResponseMustMatchTransactionAndQuestion` and `rejectsResponsePacketsAndMultiQuestionQueries`. |
| P2 | Alias lookup accepted unrelated addresses sharing the final byte. Multiple IPv6-only DNS servers were all omitted when IPv6 was disabled in preferences. Use exact address mappings, retain IPv6 when it is the only resolver family, explicitly route IPv6 aliases, fall back to a non-VPN network when transport matching fails, and reject establishment with no usable DNS servers. | `DnsServerMapperTest` exact-address and multiple-IPv6-only cases; existing `DnsServerMapperDohRoutesTest`. Network discovery and actual routes need device smoke. |
| P2 | Redirects answered AAAA/MX requests with A records (or vice versa), resolved malformed redirect hostnames on the packet thread, and synthetic replies retained client answer/authentication data. Parse numeric redirects with the existing Guava helper, respect QTYPE/class, return NODATA for other types/families and SERVFAIL for invalid addresses, and build clean replies. | Five `DnsPacketProxyResponseTest` cases, including A, AAAA, MX, ANY, invalid/null redirects, and injected answer/AD flag rejection. |
| P1 | Null VPN establishment escaped as an NPE; service destruction did not stop the worker; late statuses/network callbacks could restart or overwrite stopped/replacement state. Handle revoked consent, clean up all worker exits, check cancellation around establishment/polling, stop workers on destroy/revoke, isolate replacement worker state, and accept status only from the currently requested worker. | New Android-only `VpnServiceRuntimeStateTest` covers stale STOPPED and late network-available events; existing `VpnLifecycleInstrumentedTest` is the real TUN/consent gate. Worker poll/socket unit cases pass. |
| P1 | Root hosts-copy failure could be masked by successful chmod; source label/URL newlines could inject non-comment hosts entries. Chain quoted dd/chown/chmod with `&&` and flatten CR/LF in source metadata comments. | `RootModelShellSafetyTest.failedCopyCannotBeMaskedBySuccessfulChmod` executes a missing-source command against a quoted temporary path and verifies nonzero status, unchanged content and unchanged permissions; `sourceMetadataCannotInjectHostsFileLines`. No system hosts files are touched by these tests. |

## Executed Validation

- No Gradle invocation, commit, push, native rebuild, device operation, or release edit by this pass.
- Compiled all changed runtime Java files, all VPN/root unit tests, and both new
  instrumentation test classes with JDK 21 `javac -proc:none`, SDK 36 `android.jar`,
  restored cached dependencies, and existing debug application classes. Exit 0;
  deprecated-API notes only. This is a focused compilation, not a clean full-app build.
- Ran JUnit 4.13.2 directly against those freshly compiled classes: **OK (30 tests)**,
  exit 0, final run 0.188 seconds. No Android instrumentation tests were run.
- Test classes executed: `DnsQueryResponseTest`, `DnsPacketProxyResponseTest`,
  `DnsQueryQueueTest`, `DnsServerMapperTest`, `DnsServerMapperDohRoutesTest`,
  `VpnWorkerSocketTest`, `VpnWorkerIdleTimeoutTest`, `VpnConnectionMonitorTest`,
  `VpnPacketProcessorTest`, `VpnWatchdogTest`, and `RootModelShellSafetyTest`.
- Scoped `git diff --check` passed. Other reviewers' simultaneous changes were left intact.

## Main Reviewer Gates

Run after collecting the completed shared-workspace edits; an earlier build can predate
these tests and the final service/descriptor changes.

```sh
./gradlew :app:testDebugUnitTest --tests 'org.adaway.vpn.*' --tests 'org.adaway.model.root.*'
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Run these instrumentation classes on an isolated test device/emulator:

- `org.adaway.vpn.dns.DnsQueryDescriptorTest` (4 cases; run on API 26/28 and 29+).
- `org.adaway.vpn.VpnServiceRuntimeStateTest` (3 cases).
- `org.adaway.vpn.dns.DnsPacketProxyRuntimeTruthTest`.
- `org.adaway.vpn.worker.VpnPacketProcessorRuntimeTruthTest`.
- `org.adaway.vpn.VpnLifecycleInstrumentedTest` (requires VPN consent and no other VPN).
- `org.adaway.model.vpn.VpnModelCacheInvalidationTest`.
- `org.adaway.model.root.RootModelApplyInstrumentedTest` with its existing opt-in safeguards.

Manual device smoke: stop/revoke while DNS is active; repeated start/stop/reconnect;
Wi-Fi/cellular changes; IPv6-only DNS; upstream unreachable/large DNS replies;
root apply failure followed by successful apply and restore. Check final release APK
native contents through the release owner's existing artifact gates.

## Material Limits

- No fresh whole-app build, Android instrumentation execution, privileged write,
  real network handoff, or final APK packaging proof was performed here. Unit tests do
  not establish Android fd-close/poll behavior, TUN MTU behavior, or lifecycle timing.
- Poll waits now wake at most every second for cancellation/query expiry, including
  when the watchdog is disabled; battery impact was not measured.
- Root writes still use dd into the existing target (needed for existing mount/symlink
  arrangements); failure reporting is fixed, but power-loss atomicity/rollback and
  read-only remount success remain unproven on devices.
- Existing TCP DNS/fragment reassembly limitations and broad provider-IP blocking
  policy were not redesigned. The unreferenced experimental `DohPacketProxy` still
  uses lookup-based synthesis and is not the active `VpnWorker` path.
- Inspected VPN/adblocking models and `Hostnames`; no database-rule precedence,
  source parsing, UI, release, or custom-filter URL changes were made here. Existing
  rule/cache instrumentation remains a parent integration gate.

Platform contracts checked against official Android documentation:
[ParcelFileDescriptor ownership](https://developer.android.com/reference/android/os/ParcelFileDescriptor#fromDatagramSocket(java.net.DatagramSocket))
and [VPN establishment](https://developer.android.com/reference/android/net/VpnService.Builder#establish()).
The former documents duplicated descriptors from API 29 onward; the latter documents
null when the app is not prepared and default DNS inheritance if none is configured.

## Finite Parent-Review Correction

- Verified the API 26/28 implementation and current Android contract: before API 29,
  `fromDatagramSocket` aliases the socket's FileDescriptor instead of duplicating it.
  `DnsQuery` now explicitly calls `dup()` on that path. It retains the legacy wrapper
  until socket teardown so the wrapper's finalizer cannot close the live socket.
  After the socket owner closes its descriptor, the legacy wrapper is closed to retire
  its close guard; it is never detached. API 29+ keeps the already-duplicated descriptor.
  Duplication failures propagate as IOException through the existing forwarding cleanup.
- Added cross-version `DnsQueryDescriptorTest` cases:
  `pollingDescriptorSurvivesSocketCloseOnEverySupportedApi` checks the independent
  descriptor with kernel `fstat`; `closingPollingDescriptorDoesNotCloseLiveSocketOnEverySupportedApi`
  closes the polling PFD and proves the socket still receives a loopback datagram.
  These complement complete teardown and poll-error ownership tests. They are compiled,
  not executed; both platform branches still require API 26/28 and 29+ instrumentation.
- Confirmed the monitor's old `stop(); if (!interrupted) start(...)` sequence could
  enqueue a service start before executor interruption. It no longer calls startService.
  It submits a worker-tagged recovery message to the owning service, which rechecks
  current-worker identity and requested state on its handler. Added host tests for
  stop-before-interrupt and one-shot recovery, plus instrumentation for recovery queued
  before stop/replacement. No wider network lifecycle rewrite was made.
- Fixed `RootModelShellSafetyTest` to use `Files.write(byte[])` and
  `Files.readAllBytes` with explicit UTF-8. The earlier host-JDK compilation did not
  validate Android's Java API surface and missed these unsupported convenience APIs.
- Recompiled all changed runtime classes, focused unit tests, and both instrumentation
  classes using JDK 21 javac with `--system` pointing to Gradle's cached SDK 36
  transformed `jdkImage`, plus cached dependencies/existing app classes. Exit 0,
  deprecated-API notes only. No Gradle task was launched by this pass.
- Re-ran the same 11 JUnit classes with the two new monitor tests: **OK (32 tests)**,
  exit 0, 0.181 seconds. Android instrumentation, full consolidated Gradle build,
  root apply/restore, and release packaging remain parent gates.

Additional source confirmation:
[AOSP Android 8 ParcelFileDescriptor](https://android.googlesource.com/platform/frameworks/base/+/android-8.0.0_r1/core/java/android/os/ParcelFileDescriptor.java)
and [AOSP Android 9 ParcelFileDescriptor](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/java/android/os/ParcelFileDescriptor.java).

## OCT-VPN-02: Preference Restart Drops START

- Confirmed production ordering defect: `PrefsVpnFragment.restartVpn()` queued STOP,
  then `VpnServiceControls.start()` returned early because the old tunnel was still
  RUNNING. The queued STOP then disabled blocking. Preference and service callbacks
  execute on the same main thread, so STOP cannot complete within that callback pair.
- Minimal fix: preferences now send one explicit `VpnServiceControls.restart()` START
  request, bypassing only the already-running shortcut. Existing `startVpn()` replaces
  the worker without stopping or destroying the service; ordinary start behavior is unchanged.
- Added `PrefsVpnRestartInstrumentedTest#bypassPreferenceReplacesTunnelWithoutStoppingService`.
  Opt-in `adawayLiveVpnSmoke=true` plus prepared consent; exercises the actual registered
  bypass-preference callback, requires a different Android VPN Network and replacement
  worker start, and rejects service destruction. Cleanup waits for service and network
  removal and restores the original preference and blocking method. No separate DNS
  fixture: packet blocking remains covered by the existing live-tunnel regression.
- Handoff state: source review and diff checks only; compilation and device red/green
  execution belong to the parent. This test uses existing production APIs and can run
  against the prior installed APK after installing only the test APK.
- Boundary: the foreground-start crash was reproduced by the old absent-service STOP
  fixture, not independently through a user flow. Unconditional `stopSelf()` remains
  unchanged; arbitrary rapid STOP/START ordering is not claimed verified or fixed.
