#!/usr/bin/env bash
# Offline safety regression: an ADB-connected non-emulator is never targeted.
set -euo pipefail
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CAPTURE_QA_TRACE"
if [[ "$*" == "-s emulator-5554 get-state" ]]; then
  printf 'device\n'
  exit 0
fi
if [[ "$*" == "-s emulator-5554 shell getprop ro.kernel.qemu" ]]; then
  printf '0\n'
  exit 0
fi
echo 'Unsafe ADB command was attempted' >&2
exit 70
SH
chmod +x "$tmp/adb"
export CAPTURE_QA_TRACE="$tmp/calls"
if PATH="$tmp:$PATH" bash clock/scripts/capture-emulator-layouts.sh > "$tmp/output" 2>&1; then
  echo 'Clock QA script accepted a physical/non-QEMU device' >&2
  exit 1
fi
grep -Fq 'Refusing screenshot QA outside the verified disposable Android emulator' "$tmp/output"
test "$(wc -l < "$tmp/calls")" -eq 2
if grep -E ' install | am | uimode | exec-out ' "$tmp/calls"; then
  echo 'Clock QA ran a state-changing ADB action on the wrong device' >&2
  exit 1
fi
echo 'PASS: physical-device guard refuses all install, app and display actions'

# Regression: an ANR/system dialog must fail screenshot capture before it can
# produce a misleading "valid PNG" artifact. All actions are mocked; no ADB
# device or emulator is required, and only a temporary disposable tree is used.
script="$(pwd)/clock/scripts/capture-emulator-layouts.sh"
mkdir -p "$tmp/disposable/clock/build/outputs/apk/debug"
printf 'synthetic APK fixture\n' > "$tmp/disposable/clock/build/outputs/apk/debug/clock-debug.apk"
cat > "$tmp/adb" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CAPTURE_QA_TRACE"
case "$*" in
  "-s emulator-5554 get-state")
    printf 'device\n' ;;
  "-s emulator-5554 shell getprop ro.kernel.qemu")
    printf '1\n' ;;
  "-s emulator-5554 shell pm path com.goreecloud.clock")
    printf 'package:/data/app/synthetic/base.apk\n' ;;
  "-s emulator-5554 shell dumpsys window")
    printf 'mCurrentFocus=Window{123 u0 Application Not Responding: com.google.android.apps.nexuslauncher}\n' ;;
  *"exec-out screencap -p"*)
    echo "A blocked screenshot was captured" >&2
    exit 75 ;;
  *)
    # This is a mocked emulator, never an actual Android device.
    exit 0 ;;
esac
SH
cat > "$tmp/sleep" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$tmp/adb" "$tmp/sleep"
export CAPTURE_QA_TRACE="$tmp/blocked-calls"
if (cd "$tmp/disposable" && PATH="$tmp:$PATH" bash "$script") > "$tmp/blocked-output" 2>&1; then
  echo "Clock screenshot QA accepted a system dialog in the foreground" >&2
  exit 1
fi
grep -Fq 'Clock screenshot blocked for phone-light' "$tmp/blocked-output"
if grep -Fq 'exec-out screencap' "$tmp/blocked-calls"; then
  echo "Clock screenshot QA captured an Android dialog" >&2
  exit 1
fi
test ! -e "$tmp/disposable/clock/build/reports/emulator-screenshots/phone-light.png"
echo 'PASS: ANR/system dialog is rejected before screenshot capture'
