#!/usr/bin/env python3
"""Synthetic, dependency-free Clock visual QA navigation parser regressions."""
import unittest
from tab_coordinates import find_tab_center


def fixture(label="Timer", duplicated=False, dock=True):
    top = '<node text="Timer" bounds="[100,80][250,170]" />'
    if dock:
        bottom = f'<node text="{label}" bounds="[240,2020][480,2260]" />'
    else:
        bottom = '<node text="Other" bounds="[240,2020][480,2260]" />'
    extra = bottom if duplicated else ""
    return ('<?xml version="1.0" encoding="UTF-8"?>'
            '<hierarchy><node text="" bounds="[0,0][1080,2400]">'
            + top + bottom + extra + '</node></hierarchy>')


class ClockVisualCoordinatesTests(unittest.TestCase):
    def test_finds_only_dock_item_not_page_heading(self):
        self.assertEqual((360, 2140), find_tab_center(fixture(), "Timer"))

    def test_disambiguates_tabs(self):
        self.assertEqual((360, 2140), find_tab_center(
            fixture(label="Settings"), "Settings"))

    def test_refuses_ambiguous_bottom_tabs(self):
        with self.assertRaisesRegex(ValueError, "ambiguous"):
            find_tab_center(fixture(duplicated=True), "Timer")

    def test_refuses_heading_only(self):
        with self.assertRaisesRegex(ValueError, "missing"):
            find_tab_center(fixture(dock=False), "Timer")

    def test_refuses_unknown_tab_and_bad_xml(self):
        with self.assertRaisesRegex(ValueError, "Unknown"):
            find_tab_center(fixture(), "Security")
        with self.assertRaises(Exception):
            find_tab_center("<not-android />", "Timer")

    def test_rejects_oversized_hierarchy(self):
        with self.assertRaisesRegex(ValueError, "too large"):
            find_tab_center("x" * 1_000_001, "Timer")


if __name__ == "__main__":
    unittest.main()
