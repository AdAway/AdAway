#!/usr/bin/env bash
set -euo pipefail

apk="${1:?Usage: check-minified-startup.sh APK [EMULATOR_SERIAL] [REPORT_DIRECTORY]}"
serial="${2:-emulator-5554}"
report="${3:-app/build/ci-artifacts/minified-startup}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
adb="$sdk/platform-tools/adb"
aapt="$sdk/build-tools/36.0.0/aapt"
apksigner="$sdk/build-tools/36.0.0/apksigner"
timeout_tool="$(command -v timeout || command -v gtimeout || true)"

fail() { echo "Minified startup smoke: $*" >&2; exit 1; }
[[ -s "$apk" ]] || fail "APK is missing or empty: $apk"
[[ -n "$sdk" && -x "$adb" && -x "$aapt" && -x "$apksigner" ]] \
  || fail "Set ANDROID_HOME to an SDK with platform-tools and build-tools 36.0.0."
[[ -n "$timeout_tool" ]] || fail "GNU timeout (or gtimeout) is required."
mkdir -p "$report"

device() { "$timeout_tool" 20 "$adb" -s "$serial" "$@"; }
emulator_verified=false
# shellcheck disable=SC2329 # Invoked indirectly by the EXIT trap.
collect_diagnostics() {
  local status=$?
  trap - EXIT
  if [[ "$emulator_verified" == true ]]; then
    device logcat -d -v threadtime > "$report/logcat.txt" 2>&1 || true
    device logcat -b crash -d -v threadtime > "$report/crash-logcat.txt" 2>&1 || true
    device shell dumpsys activity activities > "$report/activities-final.txt" 2>&1 || true
    device shell dumpsys activity processes > "$report/processes-final.txt" 2>&1 || true
  fi
  printf 'exit_code=%s\n' "$status" > "$report/result.txt"
  exit "$status"
}
trap 'collect_diagnostics' EXIT

"$aapt" dump badging "$apk" > "$report/apk-badging.txt"
grep -q "^package: name='org.adaway' " "$report/apk-badging.txt" \
  || fail "APK package must be org.adaway."
if grep -q '^application-debuggable' "$report/apk-badging.txt"; then
  fail "Refusing a debuggable APK; this gate must exercise directRelease."
fi
"$apksigner" verify --verbose --print-certs "$apk" > "$report/apk-signature.txt" 2>&1

# A clean install crosses signing identities and deletes data: never do this on a phone.
kernel_qemu="$(device shell getprop ro.kernel.qemu | tr -d '\r')"
[[ "$kernel_qemu" == "1" ]] || fail "Refusing to change a non-emulator device."
emulator_verified=true
device shell pm list packages org.adaway > "$report/packages-before.txt"
if grep -Fxq 'package:org.adaway' "$report/packages-before.txt"; then
  device uninstall org.adaway > "$report/uninstall.txt" 2>&1
fi
"$timeout_tool" 120 "$adb" -s "$serial" install "$apk" > "$report/install.txt" 2>&1
device shell am force-stop org.adaway
device shell am start -W -n org.adaway/.ui.home.HomeActivity > "$report/launch.txt" 2>&1
grep -Eq '^Status: ok[[:space:]]*$' "$report/launch.txt" || fail "Activity launch did not succeed."

# An accepted launch is not success: require a live process and resumed app UI for 10 polls.
# Home may legitimately hand off to onboarding, without VPN consent or network setup.
stable_polls=0
previous_pid=""
for attempt in {1..30}; do
  pid="$(device shell pidof org.adaway | tr -d '\r' || true)"
  device shell dumpsys activity activities > "$report/activities-latest.txt"
  printf 'poll=%s pid=%s\n' "$attempt" "$pid" >> "$report/process-polls.txt"
  if [[ "$pid" =~ ^[0-9]+$ ]] && \
      grep -Eq '(mResumedActivity|topResumedActivity)[:=].*[[:space:]]org\.adaway/[^[:space:]]+' \
        "$report/activities-latest.txt"; then
    if [[ "$pid" == "$previous_pid" ]]; then
      stable_polls=$((stable_polls + 1))
    else
      stable_polls=1
    fi
    previous_pid="$pid"
    if [[ "$stable_polls" -ge 10 ]]; then
      echo "Minified startup passed: live process and foreground AdAway activity."
      echo "Startup only; not full minified functionality, VPN, or physical-device coverage."
      exit 0
    fi
  else
    stable_polls=0
    previous_pid=""
  fi
  sleep 1
done
fail "App did not remain alive with a foreground activity within 30 polls."
