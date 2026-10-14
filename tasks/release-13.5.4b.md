# AdAway 13.5.4b Release Checkpoint

## Scope

This is the bounded follow-up to `israeli-vpn-smoke-2026-10-14.md`, covering
IL-VPN-01, IL-UX-02, and IL-UX-03. Version 13.5.4b, code 130504, retains the
13.5.3b signing identity and beta channel. It does not reopen root validation,
claim physical-phone approval, or expand into another whole-repository audit.

## Fixes

- **IL-VPN-01:** onboarding downloads enabled never-downloaded subscriptions even
  when the production database already seeded the default lists. Retained state
  avoids duplicate initialization on recreation and preserves user selections.
- **IL-UX-02:** source guidance tracks configuration rather than download metadata.
  Successful automatic imports and toolbar updates acknowledge the exact unchanged
  configuration; later edits and failed applications remain pending. Failure/retry
  feedback survives background refreshes instead of being replaced by Apply.
- **IL-UX-03:** enabled external subscriptions without a successful download count
  as needing an update. Successfully downloaded empty lists remain up to date;
  disabled sources and user rules do not inflate either freshness count.

## Validation Ledger

- Nine Room freshness tests and three Home counter tests: 12 passed on API 34.
- Focused source/apply/worker regression run: 13 passed on API 34, no skips/failures.
- The first integrated run exposed a test helper waiting for global UI idle around
  an indefinite snackbar. Dispatch now uses the main thread directly. A subsequent
  failure test exposed the production feedback race, which was fixed and retested.
- Final full JVM run: 610 tests, three skipped, zero failures/errors.
- Final full API 34 connected run: 310 tests, ten intentionally skipped, zero
  failures/errors. Counts come from JUnit XML; Gradle's console double-counts skips.
- Android lint: zero errors, 32 existing nonfatal warnings.
- Signed, minified directRelease build and SBOM generation passed with strict
  dependency verification. APK identity is 13.5.4b / 130504, non-debuggable, with
  certificate SHA-256 `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
- Clean signed APK install passed the foreground/process startup gate. Normal
  Android VPN consent followed by onboarding automatically reached 72.7k blocking
  rules, 75 allow rules, and four up-to-date sources without tapping Update.
- Adding the EasyList Hebrew hosts URL with the name empty automatically imported
  181 hosts and skipped one non-domain entry. The Sources screen had no redundant
  Apply prompt, including after expanding the Regional category. Live DNS allowed
  `example.com` and blocked `pagead2.googlesyndication.com` and
  `totalmedia2.ynet.co.il`. No root access was used.
- Local test APK SHA-256:
  `2b696f2dc6216852556379e987f83ab493e0ce76ae9874113cc70e3d3cdf52dd`.
  The published CI-built APK must be verified separately; this hash is not a claim
  about a not-yet-published asset.
- This committed document is a pre-publication checkpoint. Publication is not
  claimed until the protected PR and release jobs succeed and downloaded assets
  verify. The tagged release and its verification report record that later result.

Raw local evidence is ignored under `app/build/reports/release-13.5.4b/`; the earlier
browsing matrix remains under `app/build/reports/israeli-sites-2026-10-14/`.
GitHub's merged PR checks and tagged release are the durable publication evidence.

## Product Limits

VPN mode is the supported test target for this release. The earlier completed
Ynet, KSP, and Calcalist comparisons passed with defaults plus EasyList Hebrew
hosts. Walla remains unverified because the host emulator crashed with VPN off.
DNS filtering does not implement cosmetic rules, scriptlets, or all first-party
advertising. Physical-phone and stable-browser approval remains outstanding.
