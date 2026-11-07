#!/usr/bin/env bash
# Screenshot QA runs only inside a disposable GitHub-hosted Android emulator.
# Never execute on an authorized user's physical Android device.
set -euo pipefail

out="clock/build/reports/emulator-screenshots"
mkdir -p "$out"
package="com.goreecloud.clock"
activity="$package/.MainActivity"

capture() {
  local label="$1"
  adb shell am force-stop "$package"
  adb shell am start -W -n "$activity" >/dev/null
  sleep 3
  adb exec-out screencap -p > "$out/$label.png"
}

# The emulator is ephemeral: these display settings cannot affect owner devices.
adb shell cmd uimode night no
capture "phone-light"
adb shell cmd uimode night yes
capture "phone-dark"

adb shell wm size 1800x1200
adb shell wm density 240
adb shell cmd uimode night no
capture "tablet-light"
adb shell cmd uimode night yes
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
