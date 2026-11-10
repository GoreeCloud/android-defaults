#!/usr/bin/env bash
# Shared CI-only ADB safety preflight for Android Dialer/Contacts screenshot jobs.
# Must be sourced by a caller; no file or device state changes.
# Never use the default ADB target or a caller-controlled ADB serial.
goreecloud_require_disposable_emulator() {
  local expected_api="${1:-}"
  local serial="emulator-5554"
  local state qemu actual_api
  case "$expected_api" in
    26|35|36) ;;
    *) printf 'Unsupported disposable emulator API level.\n' >&2; return 2 ;;
  esac
  if ! command -v adb >/dev/null 2>&1; then
    printf 'ADB unavailable for disposable emulator QA.\n' >&2
    return 2
  fi
  state="$(command adb -s "$serial" get-state 2>/dev/null | tr -d '\r')" || {
    printf 'Disposable emulator not connected.\n' >&2
    return 2
  }
  if [[ "$state" != "device" ]]; then
    printf 'Disposable emulator not ready.\n' >&2
    return 2
  fi
  qemu="$(command adb -s "$serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" || {
    printf 'Disposable emulator identity unavailable.\n' >&2
    return 2
  }
  if [[ "$qemu" != "1" ]]; then
    printf 'Refusing screenshot QA on a non-emulator device.\n' >&2
    return 2
  fi
  actual_api="$(command adb -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')" || {
    printf 'Disposable emulator API unavailable.\n' >&2
    return 2
  }
  if [[ "$actual_api" != "$expected_api" ]]; then
    printf 'Disposable emulator API does not match requested test matrix.\n' >&2
    return 2
  fi
  printf '%s\n' "$serial"
}
