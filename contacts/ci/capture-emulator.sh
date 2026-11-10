#!/usr/bin/env bash
# CI-only GoreeCloud Contacts visual evidence. No personal directory reads.
# Never execute this script on an owned phone: it clears the Contacts test app.
set -euo pipefail

api="${1:?Android API level is required}"
if [[ ! "$api" =~ ^(26|35|36)$ ]]; then
  printf 'Unsupported disposable emulator API level.\n' >&2
  exit 2
fi
# Verify the explicitly targeted disposable emulator before clearing the app.
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/ci/emulator-only.sh"
serial="$(goreecloud_require_disposable_emulator "$api")" || exit 2
adb_target() { command adb -s "$serial" "$@"; }
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
source contacts/ci/fresh-hierarchy.sh
screens="$repo_root/contacts/build/emulator-screenshots"
mkdir -p "$screens"

cleanup() {
  adb_target shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  if (( api >= 29 )); then
    adb_target shell cmd uimode night no >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

adb_target install -r contacts/app/build/outputs/apk/debug/app-debug.apk >/dev/null
# Ensure screenshots contain NO personal/synthetic contacts, even when
# instrumentation previously exercised test-only in-memory provider records.
adb_target shell pm clear com.goreecloud.contacts >/dev/null
# A freshly booted disposable emulator may still be locked or asleep.
# These actions do not grant permissions, interact with contacts, or click UI dialogs.
adb_target shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb_target shell wm dismiss-keyguard >/dev/null 2>&1 || true

launch() {
  adb_target shell am start -W -S -f 0x10008000 -n \
    com.goreecloud.contacts/.ContactsActivity >/dev/null
}

ready() {
  local xml
  local launcher_recovered=0
  # Bounded retries: uiautomator may momentarily expose a system startup overlay.
  for attempt in {1..20}; do
    xml="$(capture_fresh_contacts_hierarchy)" || xml=""
    if [[ "$xml" == *"Your people"* && "$xml" == *"Your contacts stay yours"* ]]; then
      sleep 1
      return 0
    fi
    # The Android 35 diagnostic screenshot from run 42736882461 showed
    # "Pixel Launcher isn't responding" over our correctly rendered app.
    # Recover this *specific* emulator system-app ANR once; never click ANR
    # dialogs, grant READ_CONTACTS, clear accounts or touch a real handset.
    if [[ "$launcher_recovered" -eq 0 &&
          "$xml" == *"Pixel Launcher isn't responding"* ]]; then
      launcher_recovered=1
      printf 'Detected synthetic Pixel Launcher ANR overlay; restarting emulator launcher once.\n' >&2
      if adb_target shell pm path com.google.android.apps.nexuslauncher >/dev/null 2>&1; then
        adb_target shell am force-stop com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
        sleep 3
        launch
      fi
    fi
    # No arbitrary dialog clicks or permissions may be granted in CI.
    sleep 2
  done
  # Leave useful, privacy-safe diagnostics; never dump raw contact details or grant access.
  printf 'Contacts foreground probe (disposable emulator only):\n' >&2
  adb_target shell pidof com.goreecloud.contacts >&2 || true
  adb_target shell dumpsys activity activities 2>/dev/null | \
    grep -E "mResumedActivity|topResumedActivity|com.goreecloud.contacts" | head -n 8 >&2 || true
  adb_target exec-out screencap -p > "$screens/diagnostic-android-$api.png" || true
  printf 'Synthetic empty-state Contacts gate not confirmed after 20 attempts.\n' >&2
  exit 1
}

launch
ready
adb_target exec-out screencap -p > "$screens/contacts-default-android-$api.png"

adb_target shell settings put system font_scale 1.5
adb_target shell am force-stop com.goreecloud.contacts
launch
ready
actual="$(adb_target shell settings get system font_scale | tr -d '\r')"
if [[ "$actual" != "1.5" ]]; then
  printf 'Android refused the requested 1.5 font scale.\n' >&2
  exit 1
fi
adb_target exec-out screencap -p > "$screens/contacts-large-text-android-$api.png"

if (( api >= 29 )); then
  adb_target shell settings put system font_scale 1.0
  adb_target shell cmd uimode night yes
  adb_target shell am force-stop com.goreecloud.contacts
  launch
  ready
  adb_target exec-out screencap -p > "$screens/contacts-dark-android-$api.png"
fi

printf 'Captured permission-denied Contacts UI on disposable Android %s.\n' "$api"
