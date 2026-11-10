"""Positive and negative synthetic tests of the always-on CI admission contract."""
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_merge_admission import verify  # noqa: E402


class MergeAdmissionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="android-defaults-admission-")
        source = Path(__file__).resolve().parents[2] / ".github" / "workflows"
        self.workflows = Path(self.temp.name) / "workflows"
        shutil.copytree(source, self.workflows)

    def tearDown(self):
        self.temp.cleanup()

    def change(self, name, original, replacement):
        path = self.workflows / name
        text = path.read_text(encoding="utf-8")
        self.assertIn(original, text)
        path.write_text(text.replace(original, replacement, 1), encoding="utf-8")

    def test_accepts_explicit_always_on_main_and_source_specific_workflows(self):
        self.assertEqual(verify(self.workflows),
                         ["admission.yml", "contacts.yml", "dialer.yml"])

    def test_rejects_broken_or_unapproved_action_pins(self):
        self.change("admission.yml",
                    "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                    "actions/checkout@v4")
        with self.assertRaisesRegex(ValueError, "unpinned action"):
            verify(self.workflows)

    def test_rejects_path_filters_on_the_always_on_gate(self):
        self.change("admission.yml", "  pull_request:\n",
                    "  pull_request:\n    paths:\n      - 'ci/**'\n")
        with self.assertRaisesRegex(ValueError, "path filters"):
            verify(self.workflows)

    def test_rejects_missing_pull_request_admission(self):
        self.change("admission.yml", "  pull_request:\n", "")
        with self.assertRaisesRegex(ValueError, "pull requests"):
            verify(self.workflows)

    def test_rejects_write_permissions(self):
        self.change("admission.yml", "  contents: read", "  contents: write")
        with self.assertRaisesRegex(ValueError, "permissions"):
            verify(self.workflows)

    def test_rejects_extra_privileged_token_scopes(self):
        self.change("admission.yml", "  contents: read\n",
                    "  contents: read\n  id-token: write\n")
        with self.assertRaisesRegex(ValueError, "permissions"):
            verify(self.workflows)

    def test_rejects_unreviewed_additional_workflow(self):
        (self.workflows / "invisible.yml").write_text(
            "name: Invisible\non: push\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "Unexpected or missing"):
            verify(self.workflows)


if __name__ == "__main__":
    unittest.main()
