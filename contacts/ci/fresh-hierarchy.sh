#!/usr/bin/env bash
# Sourced only after ci/emulator-only.sh positively admitted the target.
# Never consume /sdcard/window.xml from a previous failed UIAutomator dump.
capture_fresh_contacts_hierarchy() {
  local remote=/sdcard/window.xml
  adb_target shell rm -f "$remote" >/dev/null 2>&1 || return 1
  adb_target shell uiautomator dump "$remote" >/dev/null 2>&1 || return 1
  adb_target shell cat "$remote" 2>/dev/null
}
