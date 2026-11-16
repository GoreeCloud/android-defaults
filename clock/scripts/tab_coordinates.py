#!/usr/bin/env python3
"""Select a visible Clock bottom-navigation tab from disposable emulator UI XML.

No input events, files, accounts or devices are accessed here. The calling
CI shell performs strict emulator QEMU checks before acting on the coordinates.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET

TABS = frozenset({"Alarms", "Timer", "Stopwatch", "Settings"})
BOUNDS = re.compile(r"^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")


def find_tab_center(xml: str, tab: str) -> tuple[int, int]:
    if tab not in TABS:
        raise ValueError("Unknown Clock navigation tab")
    if len(xml) > 1_000_000:
        raise ValueError("Android UI hierarchy too large")
    root = ET.fromstring(xml)
    if root.tag != "hierarchy":
        raise ValueError("Invalid Android UI hierarchy root")

    all_nodes = []
    for node in root.iter("node"):
        match = BOUNDS.fullmatch(node.get("bounds", ""))
        if match is None:
            continue
        left, top, right, bottom = map(int, match.groups())
        if left >= right or top >= bottom:
            continue
        all_nodes.append((node, left, top, right, bottom))
    if not all_nodes:
        raise ValueError("Android hierarchy has no visible bounds")
    height = max(bottom for _, _, _, _, bottom in all_nodes)

    candidates = []
    for node, left, top, right, bottom in all_nodes:
        label = node.get("text", "").strip()
        description = node.get("content-desc", "").strip()
        # Compose may expose either a text child or a merged tab name.
        if label != tab and description != tab:
            continue
        center_y = (top + bottom) // 2
        # Reject top-page headings and notifications, never tap by label alone.
        if center_y < height * 0.6:
            continue
        candidates.append(((left + right) // 2, center_y))

    if len(candidates) != 1:
        raise ValueError("Clock bottom-tab match was missing or ambiguous")
    return candidates[0]


def main() -> int:
    if len(sys.argv) != 2:
        print("Expected one Clock tab name", file=sys.stderr)
        return 2
    try:
        x, y = find_tab_center(sys.stdin.read(), sys.argv[1])
    except (ValueError, ET.ParseError) as exc:
        print(f"Clock tab navigation refused: {exc}", file=sys.stderr)
        return 1
    print(f"{x} {y}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
