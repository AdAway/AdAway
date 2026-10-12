# Release, CI, Security, and Update Audit - 2026-10-12

## Final Worker Handoff (Supersedes Initial Snapshot Below)

Scope is frozen for integrated testing. No Gradle, commits, pushes, releases,
workflow dispatches, or secret changes were performed by this worker. Parent
reports eight release secrets configured and owns `13.5.2b` / `130502`, signing,
version/changelog, integration, PR, and publication. Physical-phone approval is
still an explicit gate, not satisfied by emulator tests.

### Confirmed Defects and Completed Patches

| Defect / proof | Patch and regression proof |
| --- | --- |
| Required Java CodeQL run failed finding retired SDK `tools` package (run linked below). | Every pinned `setup-android` step explicitly requests `platform-tools`; CodeQL installs SDK/build-tools 36. `ReleaseWorkflowRegressionTest.androidSetupNeverUsesRetiredDefaultPackages`. |
| Tagged verifier launched `gh attestation verify` without a token. | Explicit `GH_TOKEN` on verification step. `attestationCliStepHasExplicitAuthentication`. |
| Fork signer was UNKNOWN and upstream manifest URL did not serve fork release assets. | `app/build.gradle:12` embeds required certificate pin; `UpdateStore.java:111` recognizes only the configured direct signer, with store exclusions retained. `ReleaseManifestSource` uses fork stable URL and validated fork beta asset locators. `UpdateStoreTest` (5 tests), `ReleaseManifestSourceTest` (5 tests), existing `ManifestTest` verify fail-closed boundaries. |
| Tag-only manifests expire after 14 days. | Weekly stable/beta metadata-only refresh, plus manual retry; verifies actual published APK before re-signing/attesting metadata. `weeklyRefreshVerifiesApkAndOnlyReplacesSignedMetadata`; 14-day consumer/generator limit unchanged. |
| Worker read LiveData immediately after queued postValue. | `UpdateModel.java:146` returns fresh result; `ApkUpdateService.java:101` consumes it. `failedManifestDownloadClearsPreviouslyAvailableUpdate` asserts synchronous fresh/null results. |
| Null progress restored Update after manifest became unavailable. | `UpdateActivity` shows Update only for a present newer unexpired manifest. `resetDownloadStatusDoesNotOfferAnUnavailableUpdate`. |
| Unknown-app permission settings return lost the completed APK. | Pending URI retained in app model; `UpdateActivity.java:57` resumes via worker executor, and `ApkDownloadReceiver.java:158` rechecks hash and installed signer before installing once. `ApkInstallContinuationInstrumentedTest`: permission grant resumes once, denial does not loop settings, changed APK is rejected. |
| New workflow regression test used desktop-only `Files.readString` and failed consolidated Android compilation. | Replaced with `new String(Files.readAllBytes(...), StandardCharsets.UTF_8)`; parent must rerun Android compilation. |

### Signer Integration

- Public APK certificate supplied by parent: `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
- Local build API: `-PreleaseSigningCertificateSha256=<that SHA-256>` plus protected `signingStoreLocation`, `signingStorePassword`, `signingKeyAlias`, `signingKeyPassword`, and `updateManifestPublicKeyBase64`. CI maps existing `ANDROID_RELEASE_CERT_SHA256` to the new property. The pin is not hardcoded in source.
- Manifest signer API: `UPDATE_MANIFEST_PRIVATE_KEY_BASE64` is base64 PEM private key, `UPDATE_MANIFEST_PUBLIC_KEY_BASE64` is base64 X.509 DER RSA public key. Existing `scripts/generate-update-manifest.sh` reads both from environment; no new key format or crypto algorithm.
- Generator arguments: `--apk <APK> --version 13.5.2b --version-code 130502 --cert-sha256 <SHA-256> --apk-url https://github.com/stevesolun/AdAway/releases/download/v13.5.2b/AdAway_13.5.2b.apk --channel beta --store adaway --valid-days 14 --out <manifest.json>`.
- `directRelease` retains APK hash and installed-certificate verification. Ordinary/store variants do not enable direct updates. Use isolated debug signing for tests; existing build configuration signs debug too when release signing properties are present.

### Verification Actually Run

- Integration follow-up: parent reports 583 JVM tests with two failures and connected release tests passing so far. The release failure was the ATK-34 permission source-order assertion, not signer recognition. Reproduced it failing, then passed the focused JUnit method after checking the permission-helper guard before installer creation and requiring the denial branch to route to settings and return. All existing direct-build/store boundary assertions are unchanged; no production code or DNS tests changed in this correction.
- Parent reports actionlint syntax clean. Fixed actual ShellCheck findings in tagged release workflow: separate `GNUPGHOME` assignment/export so `mktemp` failure is not masked, and quote the two remaining `GITHUB_OUTPUT` redirections. Harmless grouping/jq style suggestions left untouched.
- Worker reran actionlint on `fork-release-apk.yml`, `refresh-update-manifest.yml`, `android-analysis.yml`, and `android-ci.yml` with only `SC2129|SC2016` style suggestions excluded: passed. Scoped whitespace check passed. Scope is frozen again; parent still owns final full-suite rerun.
- Workflow YAML parse and every Bash/default-shell run block syntax check: passed.
- Standalone JDK 21, Java 17 target: `ManifestTest`, `ReleaseManifestSourceTest`, `ReleaseWorkflowRegressionTest`: **37 tests passed**. Annotation metadata produced warnings because optional Kotlin annotation classes were not on this small standalone classpath.
- This is host-JVM evidence, not Android bootclasspath compilation, cloud CI, actionlint, actual release signing, or instrumentation. Parent's actionlint and consolidated Gradle rerun remain required.
- The installer regression uses Android Context/Intent with a recording receiver; it exercises continuation branching without launching real settings/installer and substitutes the cryptographic-check result. Existing APK integrity tests and the signed physical-device smoke remain required.
- Pending installer state survives activity recreation in the app model, not process death. After process death the user must check/retry the update. No durable unattended installation is claimed.

### Exact Parent Test Commands

Run debug tests without release signing properties; provide protected release properties only for packaging.

```bash
./gradlew :app:testDebugUnitTest --dependency-verification=strict \
  --tests org.adaway.model.update.ManifestTest \
  --tests org.adaway.model.update.ApkIntegrityVerifierTest \
  --tests org.adaway.model.update.ReleaseManifestSourceTest \
  --tests org.adaway.model.update.UpdateStoreTest \
  --tests org.adaway.security.ReleaseWorkflowRegressionTest \
  --tests org.adaway.security.SecurityHardeningTest
./gradlew :app:compileDebugAndroidTestJavaWithJavac --dependency-verification=strict
./gradlew :app:connectedDebugAndroidTest --dependency-verification=strict \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.update.UpdateModelNetworkFailureInstrumentedTest,org.adaway.ui.update.UpdateActivityDownloadStatusInstrumentedTest,org.adaway.model.update.ApkInstallContinuationInstrumentedTest,org.adaway.model.update.ApkUpdateServiceWorkManagerTest
./gradlew :app:assembleDirectRelease :app:generateSbom --dependency-verification=strict
```

### Remaining Exact Gates

1. Parent reruns Android compilation/tests after the Files API correction, actionlint, focused update instrumentation, and full integration; obtain fresh six required PR checks including Java CodeQL on the final commit. This worker did not run Gradle or cloud CI.
2. Build and verify actual new-identity directRelease APK, matching version/tag/code, signature, checksum, manifest signature, SBOM, license boundary and attestations. Eight secrets are user-reported configured, not end-to-end release proof.
3. Physical-device install/launch, permission denial/grant/return and same-new-key upgrade, custom URL filtering smoke, and human UX/legal release sign-off remain external gates. An RC is not full stable/physical approval.
4. Disclose old-key migration: new identity cannot update the historical v13.5.0 signer in place. Do not silently uninstall or imply signer continuity. Back up user settings first.
5. Publish first signed feed, merge weekly refresh to default branch, enable scheduled workflow and prove a manual run. New beta uses opt-in; historical stable has no manifest, so stable feed needs a signed stable release. Retain mutable metadata assets and the installed clients' manifest key. Weekly failures/inactivity require owner attention and manual retry before 14-day expiry; see `RELEASING.md`.
6. Re-run canonical published-artifact/readiness checks for the intended tag/ref. Refresh attestations describe refreshed metadata; APK/SBOM identities and their original provenance are unchanged.

## Initial Audit Snapshot (Historical, Before Authorized Follow-Up)

The sections below retain original evidence and initial findings. Parent-owned,
unpatched, absent-secret, and no-tests statements below describe that earlier
snapshot only; current patch/test/blocker status is the handoff above.

### Scope and Outcome

- Branch: `codex/october-release-review`; starting HEAD: `59b7e131cc3acdec47b8bec399a07a121de9f87a`.
- Read `CLAUDE.md` and `tasks/lessons.md`. Other reviewers' concurrent edits were left intact.
- Reviewed `.github` workflows/settings, release scripts, `app/build.gradle`, version catalog, manifests, update model/UI, signing paths, source/archive boundaries, and existing update/release tests.
- Two confirmed update defects patched in the allowed scope, with regression assertions/tests. No Gradle, commits, pushes, release creation, workflow dispatch, or secret changes performed.
- Release is **blocked**, not approved. A locally tested development APK is feasible through the parent's restored SDK/build; an in-place replacement of the latest public APK is not established.

## Confirmed Findings

### P1: Required CI Currently Fails During SDK Setup (Parent-Owned)

- Evidence: [CodeQL run 37102296922](https://github.com/stevesolun/AdAway/actions/runs/37102296922), Java job `111144059702`, failed on 2026-10-03 at `Set up Android SDK` with `Failed to find package 'tools'`; C++ succeeded. September 19 and 26 scheduled runs also failed; September 12 succeeded.
- `.github/workflows/android-analysis.yml:71`, `android-ci.yml:37`, `android-ci.yml:260`, and `fork-release-apk.yml:63` use the same setup action SHA without overriding its package list.
- The [pinned action definition](https://github.com/android-actions/setup-android/blob/40fd30fb8d7440372e1316f5d1809ec01dcd3699/action.yml) defaults to `tools platform-tools`. Current [maintainer documentation](https://github.com/android-actions/setup-android#the-deprecated-tools-package) confirms that Google no longer serves `tools`.
- Minimal parent fix: explicitly request `packages: platform-tools` at these setup steps, preserving the existing pin, then rerun CI. Separately align CodeQL's explicit SDK 34 package installation at `android-analysis.yml:128` with compile SDK 36; that mismatch was not the observed failing step.

### P1: Release Attestation Verification Has No CLI Authentication (Parent-Owned)

- `.github/workflows/fork-release-apk.yml:359` invokes the artifact verifier with `--verify-attestations`, but supplies only the manifest public-key environment variable, not `GH_TOKEN`/`GITHUB_TOKEN`.
- `scripts/VerifyReleaseArtifacts.java:335` launches `gh attestation verify`. Previous attestation actions receive their own action inputs; they do not configure authentication for subsequent CLI processes.
- [GitHub's workflow documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-github-cli) requires token configuration for CLI steps. The separate published-artifact verification workflow already sets `GH_TOKEN` correctly at `verify-release-artifacts.yml:36`.
- Minimal parent fix: provide `GH_TOKEN: ${{ github.token }}` for the tagged workflow's verification step. Not executed in CI here; currently masked by earlier absent-secret/setup blockers.

### P1: Fork APKs Cannot Use the Current Self-Update Route (Parent-Owned Identity Decision)

- `app/src/main/java/org/adaway/model/update/UpdateStore.java:27` recognizes only the upstream AdAway signer as `ADAWAY`; the actual published fork signer below is different and becomes `UNKNOWN`.
- `app/src/main/java/org/adaway/model/update/UpdateModel.java:268` requires both direct-release mode and `getStore() == ADAWAY` before even fetching a manifest.
- `UpdateModel.java:42` still fetches `https://app.adaway.org/manifest.json`; `fork-release-apk.yml:280` publishes the fork's manifest only as a GitHub release asset. No fork feed-selection path connects those assets to the app.
- Required design before claiming working self-update: identify/pin the intended fork certificate, retain F-Droid/unknown-store exclusions, and provide an authenticated fork stable/beta feed using the matching embedded public key. Do not change identity or relax signature validation opportunistically.
- `scripts/GenerateUpdateManifest.java:81` and `:262` generate a manifest expiring after 14 days; `Manifest.java:196` rejects expired manifests. The tag-only workflow has no refresh route. An operational feed needs renewed signed metadata, even when the latest APK has not changed.

### P2: Daily Update Worker Reads Stale LiveData (Patched)

- Before this patch, `ApkUpdateService.ApkUpdateWorker.doWork()` called `checkForUpdate()` and immediately read `getManifest().getValue()`. The check publishes with `postValue`, so the worker could miss a new update or notify from an old result after a failed check.
- Proof: [LiveData's documented dispatch contract](https://developer.android.com/reference/androidx/lifecycle/MutableLiveData#postValue(T)) queues the state change on the main thread; there was no synchronization before the worker read.
- Patch: package-private `UpdateModel.checkForUpdateManifest()` returns the fresh result directly while retaining UI publication and the existing public `void checkForUpdate()` API. `ApkUpdateService.java:101` consumes that direct result.
- Regression: `UpdateModelNetworkFailureInstrumentedTest.failedManifestDownloadClearsPreviouslyAvailableUpdate` now asserts the immediate result, identity with the eventual observed manifest, and null on subsequent HTTP 500. Existing HTTPS/signature/query assertions remain.

### P2: Progress Reset Restores an Unusable Update Button (Patched)

- Before this patch, `UpdateActivity.java:70` always showed Update when progress became null, including after the manifest was cleared by a failed/expired check. This reverses `markUnavailable()` and offers an action that cannot start.
- Patch: reset only exposes the button for a present, newer, unexpired manifest.
- Regression: `UpdateActivityDownloadStatusInstrumentedTest.resetDownloadStatusDoesNotOfferAnUnavailableUpdate` publishes available -> downloading -> unavailable -> reset and requires both button and progress to stay hidden. Existing `updateActivityRendersPendingCompleteAndResetDownloadStatus` retains the valid-update reset case.

### P2: First-Time Install Permission Does Not Resume Installation (Unchanged)

- `ApkDownloadReceiver.java:130` opens unknown-app permission settings and returns. `processTerminalDownload()` always unregisters in `finally` at `:118`; no pending install is retained and `UpdateActivity` has no permission-return handler.
- Independently, `UpdateViewModel.java:142` reports completion solely from DownloadManager success, while the UI hides the button. A user who grants permission and returns has no automatic installer continuation on that screen.
- Confirmed by control flow, not a device reproduction in this pass. Parent should require a real permission-denied -> grant -> return -> install smoke before claiming self-update works. A lifecycle-safe pending-install change is intentionally not bundled into the two small patches.

## Live GitHub and Signing Evidence

Read-only GitHub API snapshot on 2026-10-12:

- Repository `stevesolun/AdAway` is public; default branch `master`; current CLI identity has admin/push access. Permission is not proof of possession of signing keys.
- Master protection requires strict/up-to-date `Analyze (cpp)`, `Analyze (java)`, `CodeQL`, `Connected Android tests`, `Development build`, and `Validate locales`. Admin enforcement and conversation resolution are on; force pushes/deletions are off. Required commit signatures are off; no required-review block was returned.
- Repository rulesets: none. Environments: none. Repository Actions secrets: **zero names**. This is a user-owned repository, not an organization-secret inheritance case.
- Default workflow token is read-only; workflows may override it. Workflow PR approval is disabled.
- No registered repository Actions runners. The physical smoke workflow requires `[self-hosted, android-device]`, so that workflow has no current execution target.
- Secret scanning/push protection enabled. Dependabot security updates disabled; alerts API returned that Dependabot alerts are disabled (HTTP 403). Dependency update PR configuration exists but is not equivalent to vulnerability alert coverage.
- Eight open CodeQL alerts, all high-severity classifications in source-only `tcpdump/jni`: alerts 5, 6, 7, 8, 9, 10, 13, 14. Not claimed to affect the current APK; native runtime investigation is outside this pass.
- Last Android CI run on starting HEAD passed on June 29: [28372963621](https://github.com/stevesolun/AdAway/actions/runs/28372963621). This is historical, not proof for the current dirty October changes.

Expected release-secret **names only**, all currently absent:

`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, `ANDROID_RELEASE_CERT_SHA256`,
`RELEASE_TAG_PUBLIC_KEY_BASE64`, `UPDATE_MANIFEST_PUBLIC_KEY_BASE64`,
`UPDATE_MANIFEST_PRIVATE_KEY_BASE64`.

Local availability inspection found no `~/.gradle/gradle.properties`, `.release/`, `~/.keystore/`, or `~/.gnupg/`. Repository Gradle/local properties have none of the five release-signing/public-key property names; matching signing environment-variable names were absent. Only `~/.android/debug.keystore` was found in conventional signing locations. This is not an exhaustive search of backups, hardware tokens, other computers, or password managers. No private-key contents/passwords were printed or changed.

### Published Identity Versus Local Development Identity

The public [v13.5.0 release](https://github.com/stevesolun/AdAway/releases/tag/v13.5.0) has one asset, `AdsAway_13.5.0.apk`, with no signed manifest, checksum sidecars, or SBOM. The historical release workflow allowed debug-key fallback.

The APK was downloaded to a temporary directory, hashed, examined with `apksigner verify --verbose --print-certs` and `aapt dump badging`, then removed. Both published/local APKs verified using v2/v3 signatures:

| Metadata | Published v13.5.0 | Existing local debug APK |
| --- | --- | --- |
| Package | `org.adaway` | `org.adaway` |
| Version | `13.5.0` / `130500` | `13.5.1` / `130501` |
| Certificate subject | `CN=Android Debug, O=Android, C=US` | `CN=Android Debug, O=Android, C=US` |
| Signer SHA-256 | `8177babd71dc956b894913db9cbaec56d109ddad743e387795e10a177186713a` | `860f37dffc9ac36c9ffc4d2c2eeb8ed489be4374dca3a7fe5e8aa80b29d09705` |
| Debuggable | No | Yes |

Published APK SHA-256: `ee543a042caee5fd5fd6a9e3926b7916ad1add9df4325efbba7702b3e00fcb86`, matching GitHub's asset digest. Local artifact SHA-256 at inspection: `c35353c4551281d356ce41b72329f4399dc74cb7d96341ec6f835075b0855e61`. The parent's ongoing build can replace the local artifact afterward.

**Consequence:** the available local development identity is not the published identity. Recover and verify the original private key before promising an in-place update of v13.5.0. A certificate or APK cannot recover its private key. Otherwise a new signing identity requires an explicit migration decision; do not silently uninstall users' existing app/data or label a new key as continuity with the old release.

## Packaging and Release Route

1. Parent completes October fixes and tests custom HTTPS filter URL add/save/download/parse/apply, failed refresh preservation, duplicate/invalid URLs, root/VPN behavior, and upgrade persistence. Keep debug builds isolated from release trust material: `app/build.gradle:108` signs **debug too** when release properties are present.
2. Use JDK 21 (available at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`) and restored SDK for the parent's build. `java` was not configured in the default shell during this audit; no Gradle was run here. Version catalog currently says `13.5.1` / `130501`, higher than public `130500`.
3. Resolve the old signer/private-key question, signed-tag identity, manifest key pair, and fork feed together. Preserve `org.adaway` and launcher identity. Secrets must be supplied through an approved protected path by the owner; this audit changed none.
4. Parent fixes the two workflow blockers above, then obtains fresh required CI results. Tagged publication currently runs independently of PR CI and final readiness; require evidence for the exact release commit before tagging. Do not treat the older March release run or June dry-run evidence as current proof.
5. Build the signed `directRelease` artifact with strict dependency verification and generate SBOM. This variant alone declares `REQUEST_INSTALL_PACKAGES`; normal debug/release disable direct APK updates. Verify package, increasing versionCode, tag-matching versionName, non-debuggable flag, hash, and the approved certificate against the actual APK before install/publication.
6. Run source/archive and packaged artifact boundary checks with `GitTracked`, `StrictSourceArchive`, and `StrictArtifacts`. `settings.gradle` includes only `:app`/`:sentrystub`; `.gitattributes` excludes dormant native/history resources from archives. License files/notices describe the current app as GPLv3+; these scripts are provenance checks, not legal clearance or permission to relicense. Keep required corresponding source/notices with the release.
7. Before publication approval, test the actual signed artifact on a physical device, including update-over-existing-install and installer permission denial/grant/return. The existing physical smoke script proves install/launch/non-debuggable/signer checks, not URL filtering, self-update continuation, or full user behavior.
8. Produce the six release assets: APK, APK checksum, signed manifest, manifest checksum, SBOM, SBOM checksum, with attestations. Run published-artifact verification, physical smoke, checked human UX sign-off, and readiness aggregation against the **same tag/commit/APK/certificate**. Manual verifier/smoke workflows default to the dispatch checkout; dispatch with the intended release ref to avoid reporting a different `GITHUB_SHA` as artifact source. Final announcement remains the parent's responsibility.

## Verification and Handoff

- Passed: scoped `git diff --check` for update production/tests; read-only GitHub queries; published/local APK hash, package/version, and signer verification described above.
- Changed production files: `model/update/UpdateModel.java`, `model/update/ApkUpdateService.java`, `ui/update/UpdateActivity.java` only.
- Changed tests: `model/update/UpdateModelNetworkFailureInstrumentedTest.java`, `ui/update/UpdateActivityDownloadStatusInstrumentedTest.java` only, both under `app/src/androidTest/java/org/adaway/`.
- A standalone JDK 21 compilation of those five files was attempted without Gradle, using existing app classes and cached dependency JARs. It could not resolve required cached classes including JUnit, AndroidX test/LifecycleOwner, and MockWebServer; diagnostic output also hit the subprocess buffer limit. **Compilation is not verified.** No build-output files were changed by that attempt; temporary compiler output was removed.
- **No unit, instrumentation, or release smoke tests were executed by this audit.** Parent must compile Android tests and run the two test classes above; retain existing `ManifestTest`, `ApkIntegrityVerifierTest`, `ApkUpdateServiceWorkManagerTest`, `SecurityHardeningTest`, `ReleaseReadinessScriptTest`, and `UxMatrixScriptTest` in the integration gates.
- Existing unrelated worktree changes, including `tasks/todo.md`, were not modified. A whole-tree whitespace check reported an existing/new concurrent trailing blank line in that file; the scoped update check passed.
- Material limitations: no production private-key possession proof; no new signed APK; no physical-device update test; no live fork update feed; no final legal/human UX sign-off; no current passing October CI. This finite audit does not close those gates.

## REL07 - Minified Startup Regression Guard (2026-10-13)

**P1, confirmed by parent:** installing the signed, minified `directRelease` APK
crashed before the first Activity. WorkManager's reflective Room construction
failed with `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`.
Debug connected tests and successful release packaging did not expose the missing
constructor. Parent also confirmed with `apkanalyzer` that the old shrunk APK's
`OverwritingInputMerger` is an empty class without a constructor. WorkManager
constructs this merger reflectively, so losing its public no-argument constructor
can prevent queued filter downloads/updates from executing even after startup is
fixed. Parent owns both Room and InputMerger R8 rules, rebuild, and real APK smoke.

This worker added a narrow startup gate, without changing production code or
tagged release workflows:

- `android-ci.yml` uploads only `app-directRelease.apk` from the existing
  ephemeral-key packaging dry run. No keystore, signing properties, or private
  keys are included. The connected job downloads this separate artifact using
  pinned `actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093`
  (verified against the action's v4 ref), then tests it after the debug suite on
  the existing API 34 emulator. Failures fail the required connected-test job.
- `scripts/check-minified-startup.sh` verifies package, non-debuggable flag and
  APK signature using SDK build-tools 36.0.0. It requires the explicitly targeted
  device's `ro.kernel.qemu` to equal `1` **before any install/uninstall**, and never
  uses a physical device. Clean install is intentional because the dry-run and
  debug keys differ. All device commands have timeouts; the CI step is bounded
  to five minutes. Local callers need GNU `timeout` or `gtimeout`.
- It launches exported HomeActivity, accepts the normal onboarding handoff, and
  requires both a stable live PID and a resumed `org.adaway` Activity for ten
  polls. A successful launch command or background process alone cannot pass.
  It does not grant VPN consent or claim minified filtering/update coverage.
- Exit diagnostics preserve logcat, the crash buffer, final activity/process
  state, launch output, APK identity/signature, polls, and status under
  `app/build/ci-artifacts/minified-startup`. Existing always-run artifact upload
  retains them on failure; debug diagnostics remain intact.
- `ReleaseWorkflowRegressionTest` guards the APK-only transfer, pinned actions,
  test ordering, required failure propagation, retained diagnostics, and the
  explicit public no-argument constructors for Room and InputMerger subclasses.
  Mock SDK tests reject physical targets,
  debug APKs, bad signatures, missing processes, and non-foreground apps; a stable
  process plus resumed onboarding passes. Mocks never contact a device.

**Worker verification:** Bash syntax passed; ShellCheck passed (only the EXIT
callback's indirect-invocation false positive is locally annotated); actionlint
passed with existing `SC2129|SC2016` style exclusions; standalone JDK 21 / Java 17
compilation and all **13** `ReleaseWorkflowRegressionTest` methods passed; scoped
whitespace check passed. No Gradle or emulator commands were run by this worker.
Actual rebuilt APK startup and real URL-download validation remain parent-owned.
The InputMerger source-rule regression protects constructor retention, but the
startup-only gate is not proof of real downloads or scheduled worker execution.
