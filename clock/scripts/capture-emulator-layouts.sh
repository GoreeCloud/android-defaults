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

# A PNG header alone cannot prove the Clock UI was visible. Historical
# screenshots captured an Android "Pixel Launcher isn't responding" dialog
# over the entire app while CI incorrectly reported screenshot success.
# Require Clock to own the focused window before AND after the capture.
# Never tap "Wait", dismiss system dialogs, or alter a user's physical device.
clock_has_focus() {
  local focused
  focused="$(adb_target shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 'mCurrentFocus=' || true)"
  [[ "$focused" == *"$package/"* ]]
}

# Read accessibility bounds from a fresh UI hierarchy rather than using
# hard-coded taps that miss the floating dock on phones/tablets.
hierarchy() {
  adb_target shell uiautomator dump /sdcard/clock-window.xml >/dev/null 2>&1
  adb_target shell cat /sdcard/clock-window.xml
}

tap_tab() {
  local tab="$1" xml coords cx cy
  if [[ "$tab" == "Alarms" ]]; then return 0; fi
  clock_has_focus || return 1
  xml="$(hierarchy)" || return 1
  if ! coords="$(printf '%s' "$xml" | python3 clock/scripts/tab_coordinates.py "$tab")"; then
    printf 'Cannot find visible Clock %s navigation control\n' "$tab" >&2
    return 1
  fi
  read -r cx cy <<< "$coords"
  adb_target shell input tap "$cx" "$cy"
  sleep 1
  clock_has_focus
}

# Other Android Defaults previews observed the emulator-only Pixel Launcher
# ANR obscuring a healthy app. Recover ONLY that exact system dialog once, by
# restarting the disposable emulator launcher; never tap arbitrary dialogs.
# A foreground mismatch without that evidence must still fail closed.
recover_launcher_anr_once() {
  local xml
  xml="$(hierarchy 2>/dev/null || true)"
  if [[ "$xml" != *"Pixel Launcher isn't responding"* ]]; then
    return 1
  fi
  if ! adb_target shell pm path com.google.android.apps.nexuslauncher >/dev/null 2>&1; then
    return 1
  fi
  echo "Observed disposable emulator Pixel Launcher ANR; restarting launcher once" >&2
  adb_target shell am force-stop com.google.android.apps.nexuslauncher >/dev/null || return 1
  sleep 2
  adb_target shell am start -W -n "$activity" >/dev/null || return 1
  sleep 3
  clock_has_focus
}

capture() {
  local label="$1"
  local tab="$2"
  local expected="$3"
  local launch
  adb_target shell am force-stop "$package"
  if ! launch="$(adb_target shell am start -W -n "$activity" 2>&1)"; then
    printf 'Clock activity launch failed for %s: %s\n' "$label" "$launch" >&2
    return 1
  fi
  sleep 3
  local attempt
  for attempt in 1 2 3; do
    if clock_has_focus; then break; fi
    sleep 2
  done
  if ! clock_has_focus; then
    recover_launcher_anr_once || true
  fi
  if ! clock_has_focus; then
    local diagnostic
    diagnostic="$(adb_target shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 'mCurrentFocus=' || true)"
    printf 'Clock screenshot blocked for %s: focus verification failed (%s)\n' "$label" "$diagnostic" >&2
    return 1
  fi
  tap_tab "$tab" || { echo "Clock $tab tab not reachable on emulator" >&2; return 1; }
  local xml
  xml="$(hierarchy)" || return 1
  if [[ "$xml" != *"$expected"* ]]; then
    printf 'Clock %s page readiness marker not present before capture: %s\n' "$tab" "$expected" >&2
    return 1
  fi
  clock_has_focus || return 1
  adb_target exec-out screencap -p > "$out/$label.png"
  if ! clock_has_focus; then
    rm -f "$out/$label.png"
    printf 'Clock screenshot invalid for %s: focus changed during capture\n' "$label" >&2
    return 1
  fi
}

# The emulator is ephemeral: these display settings cannot affect owner devices.
# Audit every tab; preserving the old four filenames supports existing consumers.
capture_tabs() {
  local form="$1" appearance="$2"
  capture "$form-$appearance" "Alarms" "NEXT ALARM"
  capture "$form-$appearance-timer" "Timer" "Duration"
  capture "$form-$appearance-stopwatch" "Stopwatch" "No laps yet"
  capture "$form-$appearance-settings" "Settings" "Appearance"
}

adb_target shell cmd uimode night no
capture_tabs phone light
adb_target shell cmd uimode night yes
capture_tabs phone dark

adb_target shell wm size 1800x1200
adb_target shell wm density 240
adb_target shell cmd uimode night no
capture_tabs tablet light
adb_target shell cmd uimode night yes
capture_tabs tablet dark

python3 - "$out" <<'PY'
from pathlib import Path
import sys

directory = Path(sys.argv[1])
tabs = ("", "-timer", "-stopwatch", "-settings")
for form in ("phone", "tablet"):
    for appearance in ("light", "dark"):
        for tab in tabs:
            name = f"{form}-{appearance}{tab}"
            data = (directory / f"{name}.png").read_bytes()
            if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 10000:
                raise SystemExit(f"Invalid or unexpectedly tiny screenshot: {name}")
            print(f"Captured {name}: {len(data)} PNG bytes")
PY
