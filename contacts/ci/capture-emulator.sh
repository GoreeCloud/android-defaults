#!/usr/bin/env bash
# CI-only GoreeCloud Contacts visual evidence. No personal directory reads.
# Never execute this script on an owned phone: it clears the Contacts test app.
set -euo pipefail

api="${1:?Android API level is required}"
if [[ ! "$api" =~ ^(26|35|36)$ ]]; then
  printf 'Unsupported disposable emulator API level.\n' >&2
  exit 2
fi
# Defense in depth: fail rather than ever clearing a personal handset.
serial="$(adb get-serialno | tr -d '\r')"
if [[ "$serial" != emulator-* ]]; then
  printf 'Refusing screenshot setup on a non-emulator device.\n' >&2
  exit 2
fi
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
screens="$repo_root/contacts/build/emulator-screenshots"
mkdir -p "$screens"

cleanup() {
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  if (( api >= 29 )); then
    adb shell cmd uimode night no >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

adb install -r contacts/app/build/outputs/apk/debug/app-debug.apk >/dev/null
# Ensure screenshots contain NO personal/synthetic contacts, even when
# instrumentation previously exercised test-only in-memory provider records.
adb shell pm clear com.goreecloud.contacts >/dev/null

launch() {
  adb shell am start -S -f 0x10008000 -n \
    com.goreecloud.contacts/.ContactsActivity >/dev/null
}

ready() {
  local xml
  for attempt in {1..12}; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    xml="$(adb shell cat /sdcard/window.xml 2>/dev/null || true)"
    if [[ "$xml" == *"Your people"* && "$xml" == *"Your contacts stay yours"* ]]; then
      sleep 1
      return 0
    fi
    # No arbitrary dialog clicks or permissions may be granted in CI.
    sleep 2
  done
  adb exec-out screencap -p > "$screens/diagnostic-android-$api.png" || true
  printf 'Synthetic empty-state Contacts gate was not visible.\n' >&2
  exit 1
}

launch
ready
adb exec-out screencap -p > "$screens/contacts-default-android-$api.png"

adb shell settings put system font_scale 1.5
adb shell am force-stop com.goreecloud.contacts
launch
ready
actual="$(adb shell settings get system font_scale | tr -d '\r')"
if [[ "$actual" != "1.5" ]]; then
  printf 'Android refused the requested 1.5 font scale.\n' >&2
  exit 1
fi
adb exec-out screencap -p > "$screens/contacts-large-text-android-$api.png"

if (( api >= 29 )); then
  adb shell settings put system font_scale 1.0
  adb shell cmd uimode night yes
  adb shell am force-stop com.goreecloud.contacts
  launch
  ready
  adb exec-out screencap -p > "$screens/contacts-dark-android-$api.png"
fi

printf 'Captured permission-denied Contacts UI on disposable Android %s.\n' "$api"
