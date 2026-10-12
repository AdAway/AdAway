# Releasing

## 1. Checking bugs and technical debt

### Lint checks

Android development tools provide linter to check common errors.
Use `./gradlew :app:lint` to run the linter and produce (human readable) reports as HTML file located at `app/build/reports/lint-results.html`.

> [!IMPORTANT] 
> Check no new warning was introduced before releasing.

### SonarCloud analysis

The AdAway application source code is [analyzed by SonarCloud](https://sonarcloud.io/dashboard?id=org.adaway) to find bugs, code smells and compute technical debt.
While the overall score may be not perfect, each new release should not increase it.  

> [!IMPORTANT]
> Check no new bug nor debt was introduced before releasing.

## 2. Updating application version

Each version has its own number that follows the [Semantic Versioning](https://semver.org/) principle (starting from version 4).

> [!IMPORTANT]
> Update application version name (`appName`) and code (`appCode`) from the `gradle/libs.versions.toml` catalog file.

## 3. Updating the changelog

The AdAway project provides [a global changelog](CHANGELOG.md).

> [!IMPORTANT]
> Update the changelog to let users know what is inside each new version before releasing it.

## 4. Building release APK

Tagged GitHub releases build the `directRelease` variant with
`.github/workflows/fork-release-apk.yml`. The release artifact name is
`AdAway_<version>.apk`, where `<version>` is the tag without the leading `v`.
For example, tag `v13.5.0` produces `AdAway_13.5.0.apk`.

The workflow also generates `app/build/reports/sbom/adaway.cdx.json`,
SHA-256 checksum files for the APK, signed manifest, and SBOM,
signer-certificate verification, provenance attestations for all six uploaded
assets, and the SBOM predicate attestation. After the attestations are created,
the workflow re-runs the canonical artifact verifier with
`--verify-attestations` before creating the GitHub Release.

Required repository secrets:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Release key alias |
| `ANDROID_KEY_PASSWORD` | Release key password |
| `UPDATE_MANIFEST_PUBLIC_KEY_BASE64` | Public key embedded for signed update-manifest verification |
| `UPDATE_MANIFEST_PRIVATE_KEY_BASE64` | Base64-encoded PEM private key used to sign `manifest.json` |
| `ANDROID_RELEASE_CERT_SHA256` | Expected APK signing certificate SHA-256 digest |
| `RELEASE_TAG_PUBLIC_KEY_BASE64` | Base64-encoded public key used by `git verify-tag` for release tags |

The workflow embeds `ANDROID_RELEASE_CERT_SHA256` using the Gradle property
`releaseSigningCertificateSha256`. Local release builds must supply that property,
the four existing `signing*` properties, and `updateManifestPublicKeyBase64` through
protected Gradle configuration. Missing or malformed certificate pins fail the
release build; only `directRelease` enables the installer and pinned fork updater.
Never commit the keystore, passwords, or manifest private key. Changing the APK
signing identity does not allow an in-place update of an existing differently
signed installation; disclose the required backup/reinstall migration.

Local release verification should run the same gates where possible:

```bash
./gradlew :app:assembleDirectRelease --dependency-verification=strict --no-daemon --stacktrace
./gradlew :app:generateSbom --dependency-verification=strict --no-daemon --stacktrace
```

## 5. Distributing release

Before sharing the any release, remember to test it.
Release variant apk does not behave like debug variant.
Same goes for real device versus emulator.

> [!IMPORTANT]
> Final tests should be done with release apk variant on real device.

Once tested, releases are posted on XDA development thread using the following template:
```
Hi all,

<welcoming message about the new version>

[U][SIZE="4"]Changelog:[/SIZE][/U]
[LIST]
[*] Item 1
[*] Item 2
[*] ...
[*] Item n
[/LIST]

[U][SIZE="4"]Thanks:[/SIZE][/U]

Special thanks to <contributors> for theirs contributions and <bug reporters> for theirs helpful bug reports.

[U][SIZE="4"]Download:[/SIZE][/U]

[URL="https://app.adaway.org/adaway.apk"]AdAway <application version>[/URL]
```

### Beta releases

The beta releases are only announced in the XDA development thread.

### Keeping the Signed Update Feed Available

The stable updater fetches
`https://github.com/stevesolun/AdAway/releases/latest/download/manifest.json`.
The existing beta preference opts into the fork's published release list and may
select either a newer beta or stable manifest. The signed payload, channel,
store, APK hash, and installed signer checks remain mandatory.

Manifest validity remains limited to 14 days. The **Refresh signed update
manifest** workflow renews the latest stable and latest beta independently every
Tuesday at 06:17 UTC. It verifies the existing APK checksum, certificate, package,
version, and non-debuggable state, then re-signs and attests only `manifest.json`
and its checksum. It does not rebuild, re-sign, or replace the APK. Releases that
predate signed manifests are reported and skipped, not retroactively trusted.

Merge this workflow to the default branch and enable scheduled Actions before
relying on unattended refresh. Run it manually after the first signed release
to prove secrets and permissions; blank `tag` refreshes both channels, or specify
one published tag. Keep the same manifest keypair used by installed clients.
Retain mutable manifest assets; immutable release assets cannot be refreshed by
this workflow. Keep failure notifications enabled and verify a successful run
each week; retry manually before expiry after a failure. GitHub can delay
schedules and disables schedules in inactive public repositories after 60 days,
so the owner must re-enable them when necessary. See
[GitHub scheduled workflow requirements](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).
The consumer deliberately fails closed if refresh stops. A beta-only release does
not create a stable feed: stable remains unavailable until a signed stable release
is published under the configured identity.

### Stable releases

Fork release tags publish the direct APK through
[GitHub releases](https://github.com/stevesolun/AdAway/releases). Store
releases such as F-Droid are separate store/build-pipeline work and should use
the normal `release` variant, not the direct APK updater variant.
Once ready, create and push a signed tag on this GitHub repository using
`vX.Y.Z` format (or `vX.Y.Zb` for pre-releases). Tags ending in `b` are
published as GitHub pre-releases and use the `beta` update-manifest channel;
all other release tags use the `stable` channel. The workflow imports
`RELEASE_TAG_PUBLIC_KEY_BASE64`, runs `git verify-tag`, creates the GitHub
release with fixed GPL boundary wording, disables generated release notes, and
uploads:

* `AdAway_<version>.apk`
* `AdAway_<version>.apk.sha256`
* `manifest.json`
* `manifest.json.sha256`
* `app/build/reports/sbom/adaway.cdx.json`
* `app/build/reports/sbom/adaway.cdx.json.sha256`

Before pushing a release tag locally, run:

```powershell
$Version = "<version>"
$Apk = "app\build\outputs\apk\directRelease\AdAway_$Version.apk"
$Sbom = "app\build\reports\sbom\adaway.cdx.json"
$Manifest = "app\build\outputs\update\manifest.json"

./gradlew :app:assembleDirectRelease --dependency-verification=strict --no-daemon --stacktrace
./gradlew :app:generateSbom --dependency-verification=strict --no-daemon --stacktrace
Copy-Item app\build\outputs\apk\directRelease\app-directRelease.apk $Apk -Force
Get-FileHash $Apk -Algorithm SHA256 |
  ForEach-Object { "$($_.Hash.ToLowerInvariant())  $(Split-Path $Apk -Leaf)" } |
  Set-Content "$Apk.sha256"
Get-FileHash $Sbom -Algorithm SHA256 |
  ForEach-Object { "$($_.Hash.ToLowerInvariant())  $(Split-Path $Sbom -Leaf)" } |
  Set-Content "$Sbom.sha256"
.\scripts\generate-update-manifest.ps1 `
  --apk $Apk `
  --version $Version `
  --version-code "<versionCode>" `
  --cert-sha256 "<release-certificate-sha256>" `
  --apk-url "https://github.com/stevesolun/AdAway/releases/download/v$Version/$(Split-Path $Apk -Leaf)" `
  --private-key-base64 "$env:UPDATE_MANIFEST_PRIVATE_KEY_BASE64" `
  --public-key-base64 "$env:UPDATE_MANIFEST_PUBLIC_KEY_BASE64" `
  --out $Manifest `
  --channel stable `
  --store adaway
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1 `
  -SourceMode GitTracked -StrictSourceArchive `
  -ApkPath $Apk `
  -SbomPath $Sbom `
  -StrictArtifacts `
  -ReportPath artifact-license-boundary-report.md
.\scripts\run-release-smoke.ps1 `
  -ApkPath $Apk `
  -ExpectedCertSha256 "<release-certificate-sha256>" `
  -VerifyOnly
.\scripts\run-release-smoke.ps1 `
  -ApkPath $Apk `
  -ExpectedCertSha256 "<release-certificate-sha256>"
```

`generate-update-manifest.ps1` and `generate-update-manifest.sh` both delegate
to the same JDK-based manifest generator, so local Windows verification does not
require WSL, Bash, or OpenSSL. The release workflow still invokes the Bash
wrapper on GitHub-hosted Linux runners.

`run-release-smoke.ps1 -VerifyOnly` checks release APK badging and optional
signer identity without requiring a connected device, and writes the APK
SHA-256 plus signer certificate status when `-ReportPath` is provided. The full
`run-release-smoke.ps1` command still refuses debuggable APKs and emulators,
installs the release APK on an attached physical device, launches `org.adaway`,
and fails if the process is not running after launch.

The same full device smoke can be run from the manual **Physical release smoke**
workflow (`.github/workflows/physical-release-smoke.yml`). Use a self-hosted
runner labeled `android-device` with PowerShell, GitHub CLI, `adb`, Android
build-tools, and one attached physical device, or provide the optional
`device_serial` input when several physical devices are attached. The workflow downloads
`AdAway_<version>.apk` from the provided release tag and runs
`run-release-smoke.ps1` without `-VerifyOnly`. A successful run uploads the
`physical-release-smoke-report` artifact with the release tag, APK name,
APK SHA-256, signing certificate identity, physical device status, hashed
device serial, observed launch pid, and source commit.

On Unix-like shells with PowerShell available, the boundary checker wrapper can
also be run directly:

```bash
bash ./scripts/check-license-boundary.sh -SourceMode GitTracked -StrictSourceArchive
```

The regular Android CI workflow uploads the source boundary report as
`license-boundary-report`. Tagged direct-APK releases upload
`release-license-boundary-reports`, including source and APK/SBOM artifact
boundary reports.

Use the tagged release artifact boundary report, not the regular CI source-only
report, when running `verify-release-readiness.ps1`. The final readiness check
expects all generated proof reports to describe the same source commit. It also
expects the generated `verify-release-artifacts` report with
`Checksum verification: passed`, `Manifest signature: passed`,
`Manifest payload: passed`, a checked `Expected certificate SHA-256`, and the
release tag inferred from the signed manifest APK URL; it also expects
`Strict artifacts: true` plus the same APK and SBOM artifact names from the
release artifact verification report.
The physical smoke report must be generated by `run-release-smoke.ps1` in
physical-device mode and include `Package`, `Signer certificate check: True`,
`Signer certificate SHA-256`, `Release tag`, `Source commit`,
`Device serial SHA-256`, and `Launch pid observed`.
The UX sign-off report must be generated by `verify-ux-signoff.ps1` and include
a reviewer, review packet, `Source commit`, `Review packet source commit`,
`Review packet SHA-256`, checked item count, `Unchecked items: 0`, and
`Issues: 0`. The checked `ux-matrix-review.md` must carry the same source
commit as the verifier run; stale packets from another source revision fail.
The same sign-off check can be run from the manual **Verify UX sign-off**
workflow (`.github/workflows/verify-ux-signoff.yml`). Provide
`review_packet_base64`, the base64-encoded checked `ux-matrix-review.md`, plus
the reviewer identity. Successful runs upload the `ux-signoff-report` artifact
with both `ux-signoff-report.md` and the checked `ux-matrix-review.md`.
The generated readiness report repeats release tag, APK, APK SHA-256, SBOM, and
source commit, plus the UX review packet hash, then records SHA-256 hashes for
the release artifact, physical smoke, UX sign-off, and license-boundary proof
reports it consumed.

The final aggregation can also be run in GitHub Actions from the manual
**Verify release readiness** workflow
(`.github/workflows/verify-release-readiness.yml`). Provide the run IDs that
uploaded `release-artifact-verification-report`,
`physical-release-smoke-report`, and `release-license-boundary-reports`, plus
`ux_signoff_run_id`, the run that uploaded `ux-signoff-report`. The workflow
downloads the proof artifacts, verifies that the checked review packet hash
matches the UX sign-off report, runs `verify-release-readiness.ps1`, and uploads
the final `release-readiness-report` artifact.

After the GitHub release is published, download the six uploaded assets to a
clean checkout and verify them as a single artifact set. With
`--verify-attestations`, the verifier checks GitHub attestations for the APK,
signed manifest, SBOM, and each `.sha256` checksum sidecar:

The same post-publish check can be run in GitHub Actions from the manual
**Verify release artifacts** workflow (`.github/workflows/verify-release-artifacts.yml`).
Provide the release tag and expected APK signing certificate SHA-256 digest;
the workflow downloads the six release assets, verifies manifest/signature and
checksum semantics, verifies GitHub attestations against this repository, and
uploads a `release-artifact-verification-report` artifact.

```powershell
$Version = "<version>"
$Apk = "AdAway_$Version.apk"
.\scripts\verify-release-artifacts.ps1 `
  --apk $Apk `
  --apk-sha256 "$Apk.sha256" `
  --manifest manifest.json `
  --manifest-sha256 manifest.json.sha256 `
  --sbom adaway.cdx.json `
  --sbom-sha256 adaway.cdx.json.sha256 `
  --public-key-base64 "$env:UPDATE_MANIFEST_PUBLIC_KEY_BASE64" `
  --expected-version $Version `
  --expected-channel stable `
  --expected-store adaway `
  --expected-apk-url "https://github.com/stevesolun/AdAway/releases/download/v$Version/$Apk" `
  --expected-cert-sha256 "<release-certificate-sha256>" `
  --repo stevesolun/AdAway `
  --report verification-report.md `
  --verify-attestations
```

On Unix-like shells:

```bash
VERSION="<version>"
APK="AdAway_${VERSION}.apk"
bash ./scripts/verify-release-artifacts.sh \
  --apk "$APK" \
  --apk-sha256 "$APK.sha256" \
  --manifest manifest.json \
  --manifest-sha256 manifest.json.sha256 \
  --sbom adaway.cdx.json \
  --sbom-sha256 adaway.cdx.json.sha256 \
  --public-key-base64 "$UPDATE_MANIFEST_PUBLIC_KEY_BASE64" \
  --expected-version "$VERSION" \
  --expected-channel stable \
  --expected-store adaway \
  --expected-apk-url "https://github.com/stevesolun/AdAway/releases/download/v$VERSION/$APK" \
  --expected-cert-sha256 "<release-certificate-sha256>" \
  --repo stevesolun/AdAway \
  --report verification-report.md \
  --verify-attestations
```

The signed update manifest may only point to `app.adaway.org` or to this fork's
GitHub APK release path:
`https://github.com/stevesolun/AdAway/releases/download/<tag>/<apk>.apk`.
Check the embedded payload before publishing or announcing a release.

Tagged releases are retained for durable provenance; the tagged release workflow
does not automatically delete older release artifacts.

APK self-update is only for the AdAway-signed direct APK distribution. Store
builds such as F-Droid should rely on their store update mechanism rather than
the in-app APK installer path. Build store releases with the normal `release`
variant so the installer permission is absent from the merged manifest; build
only the AdAway-signed direct APK channel with `directRelease`.

Pushing a fork release tag publishes the GitHub direct APK only. F-Droid updates
through its own store/build pipeline; when that pipeline runs, logs are
available at `https://monitor.f-droid.org/builds/log/org.adaway/<versioncode>`.
