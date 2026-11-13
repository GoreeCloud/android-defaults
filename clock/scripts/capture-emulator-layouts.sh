#!/usr/bin/env bash
# Screenshot QA runs only inside a disposable GitHub-hosted Android emulator.
# Never execute on an authorized user's physical Android device.
set -euo pipefail

# This script is CI-only. Never choose the default ADB device: a personal
# phone may be connected to the host. Enforce the hosted emulator serial and
# independently verify Android reports a QEMU guest before installs/UI edits.
serial="emulator-5554"
adb_target() { adb -s "$serial" "$@"; }
if [[ "$(adb_target get-state)" != "device" ]] ||
   [[ "$(adb_target shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "Refusing screenshot QA outside the verified disposable Android emulator" >&2
  exit 1
fi

out="clock/build/reports/emulator-screenshots"
mkdir -p "$out"
package="com.goreecloud.clock"
activity="$package/.MainActivity"
apk="clock/build/outputs/apk/debug/clock-debug.apk"

# connectedDebugAndroidTest may remove its app under test during cleanup.
# Reinstall the exact debug APK produced in this disposable CI workspace,
# then verify the target is resolvable before attempting any screenshot.
test -s "$apk" || { echo "Clock emulator QA APK is missing" >&2; exit 1; }
adb_target install -r "$apk" >/dev/null
adb_target shell pm path "$package" | grep -q '^package:' || {
  echo "Clock package could not be resolved after emulator install" >&2
  exit 1
}

capture() {
  local label="$1"
  local launch
  adb_target shell am force-stop "$package"
  if ! launch="$(adb_target shell am start -W -n "$activity" 2>&1)"; then
    printf 'Clock activity launch failed for %s: %s\n' "$label" "$launch" >&2
    return 1
  fi
  sleep 3
  adb_target exec-out screencap -p > "$out/$label.png"
}

# The emulator is ephemeral: these display settings cannot affect owner devices.
adb_target shell cmd uimode night no
capture "phone-light"
adb_target shell cmd uimode night yes
capture "phone-dark"

adb_target shell wm size 1800x1200
adb_target shell wm density 240
adb_target shell cmd uimode night no
capture "tablet-light"
adb_target shell cmd uimode night yes
capture "tablet-dark"

python3 - "$out" <<'PY'
from pathlib import Path
import sys

directory = Path(sys.argv[1])
for name in ("phone-light", "phone-dark", "tablet-light", "tablet-dark"):
    data = (directory / f"{name}.png").read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 10000:
        raise SystemExit(f"Invalid or unexpectedly tiny screenshot: {name}")
    print(f"Captured {name}: {len(data)} PNG bytes")
PY
