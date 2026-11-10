#!/usr/bin/env bash
# No-device synthetic freshness contract. The adb_target function is fully mocked.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
source contacts/ci/fresh-hierarchy.sh
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

adb_target() {
  printf '%s\n' "$*" >> "$tmp/calls"
  case "$*" in
    "shell rm -f /sdcard/window.xml")
      if [[ "$mode" == remove-fails ]]; then return 1; fi
      rm -f "$tmp/remote";;
    "shell uiautomator dump /sdcard/window.xml")
      if [[ "$mode" == dump-fails ]]; then return 1; fi
      printf '%s\n' '<hierarchy><node text="Your people" content-desc="Your contacts stay yours"/></hierarchy>' > "$tmp/remote";;
    "shell cat /sdcard/window.xml")
      if [[ "$mode" == cat-fails ]]; then return 1; fi
      cat "$tmp/remote";;
    *) echo 'UNSAFE: unexpected ADB operation' >&2; return 99;;
  esac
}

for mode in remove-fails dump-fails cat-fails; do
  printf '%s\n' '<hierarchy><node text="Your people" content-desc="Your contacts stay yours"/></hierarchy>' > "$tmp/remote"
  : > "$tmp/calls"
  if output="$(capture_fresh_contacts_hierarchy)"; then
    echo "FAIL: stale hierarchy accepted after $mode" >&2; exit 1
  fi
  [[ -z "${output:-}" ]] || { echo "FAIL: stale output leaked for $mode" >&2; exit 1; }
  if [[ "$mode" == remove-fails ]]; then
    [[ "$(wc -l < "$tmp/calls")" -eq 1 ]] || exit 1
  elif [[ "$mode" == dump-fails ]]; then
    [[ "$(wc -l < "$tmp/calls")" -eq 2 ]] || exit 1
  fi
done
mode=fresh
printf '%s\n' 'outdated' > "$tmp/remote"
: > "$tmp/calls"
output="$(capture_fresh_contacts_hierarchy)"
[[ "$output" == *'Your people'* && "$output" == *'Your contacts stay yours'* ]]
[[ "$output" != *'outdated'* ]]
[[ "$(wc -l < "$tmp/calls")" -eq 3 ]]
if grep -Ev '^shell (rm -f|uiautomator dump|cat) /sdcard/window.xml$' "$tmp/calls"; then
  echo 'Unexpected synthetic ADB command' >&2; exit 1
fi
grep -Fq 'xml="$(capture_fresh_contacts_hierarchy)" || xml=""' contacts/ci/capture-emulator.sh
echo 'PASS: Contacts UI readiness never reuses a stale hierarchy after failed delete/dump/read'
