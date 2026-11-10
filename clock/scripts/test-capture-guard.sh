#!/usr/bin/env bash
# Offline safety regression: an ADB-connected non-emulator is never targeted.
set -euo pipefail
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CAPTURE_QA_TRACE"
if [[ "$*" == "-s emulator-5554 get-state" ]]; then
  printf 'device\n'
  exit 0
fi
if [[ "$*" == "-s emulator-5554 shell getprop ro.kernel.qemu" ]]; then
  printf '0\n'
  exit 0
fi
echo 'Unsafe ADB command was attempted' >&2
exit 70
SH
chmod +x "$tmp/adb"
export CAPTURE_QA_TRACE="$tmp/calls"
if PATH="$tmp:$PATH" bash clock/scripts/capture-emulator-layouts.sh > "$tmp/output" 2>&1; then
  echo 'Clock QA script accepted a physical/non-QEMU device' >&2
  exit 1
fi
grep -Fq 'Refusing screenshot QA outside the verified disposable Android emulator' "$tmp/output"
test "$(wc -l < "$tmp/calls")" -eq 2
if grep -E ' install | am | uimode | exec-out ' "$tmp/calls"; then
  echo 'Clock QA ran a state-changing ADB action on the wrong device' >&2
  exit 1
fi
echo 'PASS: physical-device guard refuses all install, app and display actions'
