#!/usr/bin/env python3
"""Return the center of the *specific* Pixel Launcher ANR 'Close app' action.

Never interacts with Android itself. Called only by disposable-emulator CI
after a UIAutomator hierarchy is captured. Unknown dialogs produce no output.
"""
import re
import sys
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\Z")


def dismiss_coordinates(xml: str):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    nodes = list(root.iter("node"))
    text = [node.get("text", "") for node in nodes]
    # Never dismiss Dialer, privacy, permission or carrier dialogs.
    if not any("Pixel Launcher" in item and "responding" in item for item in text):
        return None
    for node in nodes:
        if node.get("text", "").strip() != "Close app":
            continue
        match = BOUNDS.fullmatch(node.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = (int(v) for v in match.groups())
        if 0 <= x1 < x2 <= 10000 and 0 <= y1 < y2 <= 10000:
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


if __name__ == "__main__":
    point = dismiss_coordinates(sys.stdin.read())
    if point is not None:
        print(f"{point[0]} {point[1]}")
