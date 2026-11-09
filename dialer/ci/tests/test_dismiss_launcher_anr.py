"""CI-only tests of precise Pixel Launcher ANR dismissal coordinates."""
import subprocess
import sys
from pathlib import Path
import unittest
from xml.sax.saxutils import escape

PARSER = Path(__file__).resolve().parents[1] / "dismiss-launcher-anr.py"


def run_parser(title, button="Close app", bounds="[100,200][300,240]"):
    safe_title = escape(title, {'"': '&quot;'})
    safe_button = escape(button, {'"': '&quot;'})
    xml = (
        '<hierarchy rotation="0">'
        f'<node text="{safe_title}" bounds="[0,0][400,500]"/>'
        f'<node text="{safe_button}" bounds="{bounds}"/>'
        '</hierarchy>'
    )
    return subprocess.run([sys.executable, str(PARSER)], input=xml, text=True,
                          capture_output=True, timeout=5)


class PixelLauncherAnrTests(unittest.TestCase):
    def test_only_expected_pixel_launcher_anr_has_coordinates(self):
        r = run_parser("Pixel Launcher isn't responding")
        self.assertEqual(r.returncode, 0)
        self.assertEqual(r.stdout.strip(), "200 220")

    def test_dialer_anr_is_never_dismissed(self):
        self.assertEqual(run_parser("GoreeCloud Dialer isn't responding").stdout, "")

    def test_other_system_dialog_is_never_dismissed(self):
        self.assertEqual(run_parser("Pixel Launcher needs permissions").stdout, "")

    def test_no_close_button_is_never_dismissed(self):
        self.assertEqual(run_parser("Pixel Launcher isn't responding", button="Wait").stdout, "")

    def test_unparseable_bounds_cannot_issue_taps(self):
        self.assertEqual(run_parser("Pixel Launcher isn't responding", bounds="INVALID").stdout, "")

    def test_malformed_xml_is_ignored(self):
        r = subprocess.run([sys.executable, str(PARSER)], input="<notxml", text=True,
                           capture_output=True, timeout=5)
        self.assertEqual(r.returncode, 0)
        self.assertEqual(r.stdout, "")


if __name__ == "__main__":
    unittest.main()
