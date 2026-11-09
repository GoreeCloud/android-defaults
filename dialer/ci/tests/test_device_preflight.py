"""A synthetic ADB only; CI never accesses any physical device."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "device-preflight.sh"
FAKE = r'''#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$ADB_FAKE_LOG"
if [[ "$1" == devices ]]; then
  printf 'List of devices attached\n%s\n' "$FAKE_DEVICES"; exit 0
fi
if [[ "$1" != -s ]]; then exit 7; fi
shift 2
case "$*" in
  'shell getprop ro.build.version.sdk') printf '%s\n' "$FAKE_SDK" ;;
  'shell pm path com.goreecloud.dialer')
    if [[ "$FAKE_INSTALLED" == yes ]]; then
      printf 'package:/data/app/com.goreecloud.dialer/base.apk\n'
    fi ;;
  'shell pm has-feature android.hardware.telephony') printf '%s\n' "$FAKE_TELEPHONY" ;;
  'shell cmd role holders android.app.role.DIALER') printf '%s\n' "$FAKE_ROLE" ;;
  *) exit 8 ;;
esac
'''

class DevicePreflightTests(unittest.TestCase):
    def run_fake(self, devices="emulator-5554\tdevice", sdk="35", installed="yes",
                 telephony="true", role="com.goreecloud.dialer", flags=()):
        with tempfile.TemporaryDirectory() as tmp:
            adb = Path(tmp) / "adb"
            adb.write_text(FAKE)
            adb.chmod(0o700)
            log = Path(tmp) / "commands"
            env = {**os.environ, "PATH": tmp + os.pathsep + os.environ["PATH"],
                   "ADB_FAKE_LOG": str(log), "FAKE_DEVICES": devices,
                   "FAKE_SDK": sdk, "FAKE_INSTALLED": installed,
                   "FAKE_TELEPHONY": telephony, "FAKE_ROLE": role}
            r = subprocess.run(["bash", str(SCRIPT), *flags], capture_output=True,
                               text=True, env=env, timeout=10)
            cmds = log.read_text().splitlines() if log.exists() else []
            self.assertNotIn("emulator-5554", r.stdout + r.stderr)
            self.assertTrue(all(c == "devices" or " shell " in f" {c} " for c in cmds))
            self.assertFalse(any(x in c for c in cmds for x in
                                 ("install", "am start", "logcat", "dumpsys", "input keyevent")))
            return r, cmds

    def test_authorized_device(self):
        r, c = self.run_fake()
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("role: GoreeCloud Dialer reported", r.stdout)
        self.assertEqual(len(c), 5)

    def test_missing_device(self):
        r, c = self.run_fake(devices="")
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(c, ["devices"])

    def test_multiple_devices_block(self):
        r, c = self.run_fake(devices="emulator-5554\tdevice\nother\tdevice")
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(c, ["devices"])

    def test_device_selection(self):
        r, c = self.run_fake(devices="emulator-5554\tdevice\nother\tdevice",
                             flags=("--serial", "emulator-5554"))
        self.assertEqual(r.returncode, 0)
        self.assertEqual(len(c), 5)

    def test_unconnected_selected_serial(self):
        r, c = self.run_fake(flags=("--serial", "missing"))
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(c, ["devices"])

    def test_missing_install_does_not_install(self):
        r, c = self.run_fake(installed="no")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("not installed", r.stdout)
        self.assertFalse(any("has-feature" in x for x in c))

    def test_no_telephony_is_compatibility_only(self):
        r, c = self.run_fake(telephony="false")
        self.assertEqual(r.returncode, 0)
        self.assertIn("non-telephony", r.stdout)
        self.assertFalse(any("role holders" in x for x in c))

    def test_other_role_holder(self):
        r, _ = self.run_fake(role="com.android.dialer")
        self.assertEqual(r.returncode, 0)
        self.assertIn("not confirmed", r.stdout)

    def test_legacy_role_manual(self):
        r, c = self.run_fake(sdk="26")
        self.assertEqual(r.returncode, 0)
        self.assertIn("manual", r.stdout)
        self.assertFalse(any("role holders" in x for x in c))

    def test_minimum_api_enforced(self):
        r, c = self.run_fake(sdk="25")
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(len(c), 2)
        self.assertIn("API 26", r.stderr)
