#!/usr/bin/env bash
# GoreeCloud Dialer: evidence capture on a disposable GitHub Actions emulator.
# The emulator runner invokes each workflow script line in a separate shell.
# Keep all conditional logic inside this checked-in file, not in YAML lines.
set -euo pipefail

api="${1:?Android API level is required}"
if [[ ! "$api" =~ ^[0-9]+$ ]]; then
  printf 'Invalid Android API level: %s\n' "$api" >&2
  exit 2
fi
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
screens="$repo_root/dialer/build/emulator-screenshots"
mkdir -p "$screens"

cleanup() {
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  if (( api >= 29 )); then
    adb shell cmd uimode night no >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# Instrumentation cleanup may remove the application's external files directory.
# A missing screenshot directory is not a test failure. All exported images
# here come from empty-state emulator UIs with no user contacts or call logs.
adb pull /sdcard/Android/data/com.goreecloud.dialer/files/screenshots "$screens/android-$api" || true
adb install -r "$repo_root/dialer/app/build/outputs/apk/debug/app-debug.apk"

launch() {
  adb shell am start -n com.goreecloud.dialer/.DialerActivity >/dev/null
}

ensure_rendered_keypad() {
  for _ in {1..8}; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    if adb shell cat /sdcard/window.xml 2>/dev/null | grep -Fq 'Call a number'; then
      sleep 1
      return 0
    fi
    # A Google APIs image occasionally overlays an unrelated crash popup.
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    launch
    sleep 2
  done
  printf 'Dialer keypad was not visible in the Android accessibility tree.\n' >&2
  exit 1
}

# Base visual evidence.
launch
ensure_rendered_keypad
adb exec-out screencap -p > "$screens/keypad-android-$api.png"

# Font scale 1.5 is representative, not exhaustive accessibility acceptance.
adb shell settings put system font_scale 1.5
adb shell am force-stop com.goreecloud.dialer
launch
ensure_rendered_keypad
actual_scale="$(adb shell settings get system font_scale | tr -d '\r')"
if [[ "$actual_scale" != "1.5" ]]; then
  printf 'Expected font scale 1.5, Android reported %s\n' "$actual_scale" >&2
  exit 1
fi
adb exec-out screencap -p > "$screens/keypad-large-text-android-$api.png"

# Android 10+ system night mode; skip unsupported Android 8.0 API 26.
if (( api >= 29 )); then
  adb shell settings put system font_scale 1.0
  adb shell cmd uimode night yes
  adb shell am force-stop com.goreecloud.dialer
  launch
  ensure_rendered_keypad
  adb exec-out screencap -p > "$screens/keypad-dark-android-$api.png"
fi

printf 'Captured offline Dialer keypad evidence for Android API %s\n' "$api"
