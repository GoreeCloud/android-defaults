#!/usr/bin/env bash
# Offline, no-device safety regression for shared screenshot ADB preflight.
# Synthetic ADB is first on PATH. No real handset, emulator, install, or input.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$GC_QA_ADB_TRACE"
case "$*" in
  "-s emulator-5554 get-state")
    printf '%s\n' "${GC_QA_STATE:-device}" ;;
  "-s emulator-5554 shell getprop ro.kernel.qemu")
    printf '%s\n' "${GC_QA_QEMU:-1}" ;;
  "-s emulator-5554 shell getprop ro.build.version.sdk")
    printf '%s\n' "${GC_QA_SDK:-35}" ;;
  *)
    echo 'UNSAFE: attempted non-preflight ADB command' >&2
    exit 99 ;;
esac
SH
chmod 700 "$tmp/adb"
export GC_QA_ADB_TRACE="$tmp/trace"
export PATH="$tmp:$PATH"

# The helper itself is read-only: exactly three explicitly pinned ADB probes.
: > "$GC_QA_ADB_TRACE"
serial="$(bash -c 'source ci/emulator-only.sh; goreecloud_require_disposable_emulator 35')"
[[ "$serial" == "emulator-5554" ]]
[[ "$(wc -l < "$GC_QA_ADB_TRACE")" -eq 3 ]]
if grep -Ev '^-s emulator-5554 (get-state|shell getprop ro.kernel.qemu|shell getprop ro.build.version.sdk)$' "$GC_QA_ADB_TRACE"; then
  echo 'Unexpected ADB command in preflight' >&2
  exit 1
fi

for script in contacts/ci/capture-emulator.sh dialer/ci/capture-emulator.sh; do
  for mode in not-qemu wrong-api offline; do
    : > "$GC_QA_ADB_TRACE"
    export GC_QA_STATE=device GC_QA_QEMU=1 GC_QA_SDK=35
    case "$mode" in
      not-qemu) GC_QA_QEMU=0 ;;
      wrong-api) GC_QA_SDK=26 ;;
      offline) GC_QA_STATE=offline ;;
    esac
    if bash "$script" 35 > "$tmp/output" 2>&1; then
      echo "$script admitted unsafe synthetic device: $mode" >&2
      exit 1
    fi
    if grep -Ev '^-s emulator-5554 (get-state|shell getprop ro.kernel.qemu|shell getprop ro.build.version.sdk)$' "$GC_QA_ADB_TRACE"; then
      echo "$script issued an unsafe ADB command before refusal: $mode" >&2
      exit 1
    fi
    if [[ ! -s "$GC_QA_ADB_TRACE" ]]; then
      echo "$script skipped required emulator identity preflight" >&2
      exit 1
    fi
  done
  : > "$GC_QA_ADB_TRACE"
  if bash "$script" 29 > "$tmp/output" 2>&1; then
    echo "$script admitted unsupported emulator API" >&2
    exit 1
  fi
  grep -Fq 'Unsupported disposable emulator API level.' "$tmp/output"
  [[ ! -s "$GC_QA_ADB_TRACE" ]]
done
echo 'PASS: Contacts and Dialer deny non-emulator, wrong API, offline and unsupported QA targets without device changes'
