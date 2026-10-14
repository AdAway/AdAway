# Israeli Sites VPN Smoke - 2026-10-14

Follow-up: the bounded fixes and release validation for all three findings are
tracked in `release-13.5.4b.md`. The observations below describe the original
13.5.3b smoke and its first local fix, not the later release status.

## Result

The published 13.5.3b candidate blocked advertising DNS requests on a non-root
Android emulator. Ynet, KSP, and Calcalist remained readable with downloaded
defaults and with defaults plus EasyList Hebrew hosts. The earlier reported
"connection lost" symptom was not reproduced in these completed runs. This is a
bounded browsing smoke test, not a guarantee for every page, phone, or network.

One important first-run defect was reproduced and fixed locally: the VPN started
with zero blocking rules because seeded default subscriptions prevented the first
download. The fix is verified but is NOT in the published 13.5.3b APK.

## Environment And Method

- Published APK: `AdAway_13.5.3b.apk`, versionCode 130503, from release v13.5.3b.
  SHA-256: `9c7f8f76ca173f9ae6430322f931c93d7b3015f7e9be6eb0918278599e55ddfa`.
- Baseline source: master `cf453b1a7d2f250e143a4d86fce7c79f1f3d2b7c`.
- AVD: `adaway-review-ci-api34`, API 34 Google APIs arm64, Pixel 2, 1080x1920,
  four emulator cores and 4 GiB RAM. No root commands or root hosts apply.
- Android VPN authorization was granted through the normal system dialog during
  published-APK onboarding. Connectivity evidence shows AdAway-owned `tun0`,
  `CELLULAR|VPN`, UID 10192, and DNS address 192.0.2.2. Private DNS was not given a
  custom provider. A local DNS VPN is not a remote anonymizing VPN service.
- Bundled Chrome 113 was replaced for these tests with official Chromium Android
  arm64 snapshot 1718469, version 158.0.8105.0. This is a development browser,
  not stable Chrome; physical-phone/stable-browser coverage is still separate.
- Browser media acceleration, Vulkan, and WebGPU were disabled after emulator
  crashes. Final successful runs used SwiftShader with emulator Vulkan disabled.
- Compared protection off, four downloaded default sources, and those defaults
  plus the Hebrew hosts URL. Browser HTTP cache was cleared for each recorded
  navigation, followed by a 22-second observation and a scrolled screenshot.
  The browser was restarted between filter sets to discard its in-memory DNS and
  connection state; OS-wide DNS cache flushing was not assumed.
- Recorded actual page/network results through browser DevTools, plus Android
  screenshots, VPN state, visible DNS log, and independent DNS canaries.
- Baseline VPN-off was established by stopping AdAway and checking connectivity.
  Protected runs used normal onboarding and source update paths. Shell-triggered
  Quick Settings actions were not accepted as proof of the human tile workflow.

## Filter Setup

Keep the four defaults: AdAway Official, StevenBlack Unified, Peter Lowe, and
URLhaus Malware. Add the DNS-compatible Hebrew feed:

`https://raw.githubusercontent.com/easylist/EasyListHebrew/master/hosts.txt`

It was pasted into the custom-source URL editor with the optional name empty.
Saving downloaded the list automatically. The Sources screen showed 181 imported
hosts and one skipped entry (a numeric IP, not a domain). No broad site allowlist
or protection bypass was added for these tests.

Feed snapshot SHA-256:
`56e41978e208b2a73fb3efe6eda79c72c530668512732d8f61aa2a0b92761cf1`.
Maintainer revision: `5ecbd1ad46ea4c3fc43ed84d09e40c16949100b3`.
The maintainer's `adguard_hosts.txt` covers the same targets, so it is not a
meaningful second filter comparison. Browser-format `EasyListHebrew.txt` contains
cosmetic/path rules that this DNS blocker cannot fully implement.

## Observed Browsing

Every main document below returned HTTP 200 with a completed DOM. Counts are
observations from changing live pages, NOT a controlled speed or blocking-rate
benchmark. DNS failures include blocked trackers, not just visible advertisements.

| Page | VPN off: requests / DNS failures | Defaults: requests / DNS failures | Defaults + Hebrew: requests / DNS failures |
| --- | --- | --- | --- |
| Ynet home | 764 / 0 | 405 / 18 | 405 / 21 |
| Ynet article | 498 / 0 | 134 / 21 | 136 / 25 |
| KSP home | 57 / 0 | 53 / 0 | 55 / 0 |
| Calcalist home | 998 / 0 | 382 / 7 | 381 / 7 |

- **Ynet:** a fullscreen car advertisement appeared with VPN off. It was absent
  in the protected captures; news and images remained visible. Both Hebrew-enabled
  home loads had 328/328 loaded images. The article had 13/13 with protection;
  the unprotected page also loaded advertising images, so totals differ.
- **Incremental Hebrew benefit:** `totalmedia2.ynet.co.il` resolved and returned
  responses with defaults alone, then failed DNS with the Hebrew feed enabled.
  This change was also confirmed with direct DNS probes and on Calcalist.
- **Default benefit:** DoubleClick, Taboola, and other advertising/tracking hosts
  returned responses without protection and failed DNS with defaults enabled.
  `example.com` remained reachable while `pagead2.googlesyndication.com` failed DNS.
- **KSP:** home, Samsung search/brand navigation, the Samsung phone category, and
  a product detail page worked with Hebrew protection. Category: 56 requests,
  no failures; product: 64 requests, no failures, visible product photo/text.
  Lazy-loaded images were not all loaded at once. No login, cart, payment, or
  purchase was attempted. Essential-only cookie preferences were saved after the
  default run; the later Hebrew run therefore has a different consent state.
- **Calcalist:** 202/202 images loaded in both protected runs; market widgets
  from TradingView and Infogram returned HTTP 200. Image ORB rejections also
  occurred without AdAway, so they are not classified as filter regressions.
- **Reload/navigation:** Ynet home -> article -> home with Hebrew protection
  completed without a main-document connection error. KSP category and product
  URLs were discovered from the rendered site, not guessed.

Article: `https://www.ynet.co.il/news/article/byvnfyaofg`.
KSP category: `https://ksp.co.il/mob/cat/573..137`.
KSP product: `https://ksp.co.il/mob/item/445811`.

## Findings And Fix Verification

### IL-VPN-01 / ONB-002 - First Run Starts With No Downloaded Rules

Priority P1. Reproduced twice on the published APK with clean app data:
"Protection active", zero blocked entries, and no automatic source download.
Manual update loaded about 72.7k rules from the four defaults.

Cause: `AppDatabase.initialize` already inserts default sources. The old
`subscribeDefaultsIfEmpty` returned false for those seeded rows, and Home only
started the initial update when it inserted new rows itself.

Local fix: prepare sources without replacing existing subscriptions, then trigger
one initial download when an enabled external source has no local download date.
A retained `InitialSourcesViewModel` prevents duplicate attempts on recreation;
application context avoids retaining the Activity. Disabled/custom selections and
successfully downloaded empty lists are preserved. No automatic retry loop was
added. A normal later launcher start does not automatically retry a failed setup;
the existing manual update remains available.

Verification:

- Full `testDebugUnitTest`: **602 tests, 3 skipped, zero failures/errors**.
- Seeded-default regression tests fail when the old insertion-only guard is restored.
- Signed, minified `assembleDirectRelease` and release vital lint passed with
  JDK 21, strict dependency verification, and the existing signing identity.
- Local test APK SHA-256:
  `f9cdd0856f2694e2308075d15de13b85af0ab30e865f8fe25de3d408ef12969d`.
- APK signature verified; certificate SHA-256:
  `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
- Installed patched APK, cleared only AdAway's emulator app data, and completed
  onboarding. Android's prior VPN authorization remained granted. Without tapping
  Update, Home reached **72.7k rules, 75 allow entries, 4 up-to-date sources** and
  "VPN configuration successfully updated". The new retained model survived
  minification and instantiated successfully. Real VPN blocking and allowed DNS
  canaries both passed. The 72.7k number is configured rules, not blocked requests.
- An independent code review found no actionable issue in the bounded fix.
  Physical devices, real rotation/offline recovery, and the full connected suite
  were not rerun for this local change; unit tests cover attempt/recreation logic.

Status: **fixed and verified locally; not published**. The Downloads copy of the
published APK was not replaced with the same-version unpublished test build.

### IL-UX-02 - Apply Prompt Persists After Automatic Import

Priority P2, open. After custom Hebrew-source import completed, the Sources screen
still said configuration must be applied. The domain was already blocked before
tapping Apply. `HostsSourcesFragment` observes all source-row changes through
`ApplyConfigurationSnackbar.createObserver`, while the automatic worker applies
independently. This is confusing redundant guidance, not proof that import failed.
Evidence: `hebrew-source-loaded.png`, browser/DNS probes. No broad snackbar/state
refactor was included in the first-run fix.

### IL-UX-03 - Never-Downloaded Sources Are Absent From Freshness Totals

Priority P2, open. The unpatched clean Home showed zero up-to-date and zero outdated
sources despite four seeded subscriptions. The null local/online dates are outside
the current freshness categories. The first-run fix normally removes this state
after successful download but does not improve offline/empty-state wording.
Evidence: `fresh-install-home.png` and source DAO freshness queries.

## Limits And Evidence

- Walla repeatedly crashed the **host emulator process** with VPN off, including
  after graphics/media workarounds. No valid completed Walla result is claimed;
  Calcalist was used as the third site. This is not an AdAway failure attribution.
- No CAPTCHA blocked the completed test pages. No CAPTCHA bypass was attempted.
- These are DNS rules: first-party promotions, empty ad slots, cosmetic elements,
  scriptlets, and video/audio functionality are not comprehensively covered.
  Third-party encrypted DNS can also bypass coverage; this test does not prove
  every encrypted DNS route or OEM network handoff.
- Rooted devices were intentionally excluded. No physical Android device was used.
- Raw evidence remains local and ignored at
  `app/build/reports/israeli-sites-2026-10-14/`: each `off-*`, `default-*`, and
  `hebrew-*` run has JSON plus viewport/scrolled PNGs; additional files hold VPN
  state, DNS probes, source import, first-run screenshots/XML, and build logs.
- The corrected KSP product capture contains only that product navigation; an
  earlier overlapping diagnostic capture was replaced by an isolated rerun.
- Temporary browser installation archives are removed after testing. No published
  APK, signing key, existing worktree, or unrelated user data is removed.

Recommended tested setup: **VPN mode + four defaults + EasyList Hebrew hosts**.
On the published candidate, manually update once after setup and confirm nonzero
rules until the locally verified first-run fix is released.
