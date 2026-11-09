#!/usr/bin/env bash
# Read-only Dialer preflight: no calls, settings changes, installs, logs or contact reads.
set -euo pipefail
usage() { printf 'Usage: %s [--serial ADB_SERIAL]\n' "$0"; }
selected=""
while (($#)); do
  case "$1" in
    --serial) if (($# < 2)) || [[ -z "$2" ]]; then usage >&2; exit 2; fi
              selected="$2"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done
if ! command -v adb >/dev/null 2>&1; then
  printf 'BLOCKED: adb is unavailable.\n' >&2; exit 1
fi
# Do not print ADB serials: they may identify a device.
connected="$(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')"
if [[ -n "$selected" ]]; then
  if ! grep -Fxq -- "$selected" <<< "$connected"; then
    printf 'BLOCKED: Selected device not connected and authorized.\n' >&2; exit 1
  fi
  serial="$selected"
else
  count="$(printf '%s\n' "$connected" | awk 'NF {n++} END {print n+0}')"
  if [[ "$count" != 1 ]]; then
    printf 'BLOCKED: Found %s authorized devices; require exactly one or use --serial.\n' "$count" >&2
    exit 1
  fi
  serial="$connected"
fi
sdk="$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ ! "$sdk" =~ ^[0-9]{1,3}$ ]]; then
  printf 'BLOCKED: Android API level unavailable.\n' >&2; exit 1
fi
printf 'Android API level: %s\n' "$sdk"
if (( sdk < 26 )); then
  printf 'BLOCKED: Dialer needs Android API 26 or newer.\n' >&2; exit 1
fi
path="$(adb -s "$serial" shell pm path com.goreecloud.dialer 2>/dev/null || true)"
if [[ "$path" != package:* ]]; then
  printf 'Dialer package: not installed (no installation attempted).\n'
  printf 'RESULT: device acceptance incomplete.\n'; exit 1
fi
printf 'Dialer package: installed.\n'
feature="$(adb -s "$serial" shell pm has-feature android.hardware.telephony 2>/dev/null | tr -d '\r' || true)"
if [[ "$feature" == false ]]; then
  printf 'Cellular feature: absent. RESULT: non-telephony compatibility review only.\n'; exit 0
elif [[ "$feature" == true ]]; then
  printf 'Cellular feature: advertised; carrier connectivity unverified.\n'
else
  printf 'Cellular feature: unknown; treat as unverified.\n'
fi
if (( sdk >= 29 )); then
  holders="$(adb -s "$serial" shell cmd role holders android.app.role.DIALER 2>/dev/null | tr -d '\r' || true)"
  if grep -Fxq 'com.goreecloud.dialer' <<< "$holders"; then
    printf 'Default Phone role: GoreeCloud Dialer reported as holder.\n'
  else
    printf 'Default Phone role: GoreeCloud Dialer not confirmed.\n'
  fi
else
  printf 'Default Phone role: requires manual verification on Android 8/9.\n'
fi
printf 'RESULT: read-only preflight complete; physical call, audio, accessibility, rollback and emergency-routing acceptance remain OPEN.\n'