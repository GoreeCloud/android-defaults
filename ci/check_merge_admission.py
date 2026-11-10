#!/usr/bin/env python3
"""Dependency-free, always-on Android Defaults source admission preflight.

Checks safe CI trigger/pin invariants. This is NOT branch protection and does
not substitute for Contacts/Dialer compiled Android/emulator workflows.
"""
from __future__ import annotations

import re
from pathlib import Path

APPROVED = {
    "actions/checkout": "3d3c42e5aac5ba805825da76410c181273ba90b1",
    "actions/setup-java": "b843ebf9b913146b38da2eaa7aeddf76074ae1ad",
    "gradle/actions/setup-gradle": "8da46b539cd8fe6ab27ca75128919a18d757604c",
    "actions/upload-artifact": "cf430e030ddbb5b0abf93d22962f4752f3646cd9",
    "ReactiveCircus/android-emulator-runner": "a421e43855164a8197daf9d8d40fe71c6996bb0d",
}
WORKFLOWS = ("contacts.yml", "dialer.yml", "admission.yml")
USE_PATTERN = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)", re.MULTILINE)


def verify(workflows: Path) -> list[str]:
    if not workflows.is_dir():
        raise ValueError("Missing Android Defaults workflow directory")
    found = sorted(path.name for path in workflows.glob("*.yml"))
    if sorted(WORKFLOWS) != found:
        raise ValueError("Unexpected or missing Android Defaults workflow files")
    for filename in WORKFLOWS:
        source = (workflows / filename).read_text(encoding="utf-8")
        if not re.search(r"(?m)^on:\s*$", source):
            raise ValueError(f"{filename}: missing explicit trigger")
        if not re.search(r"(?m)^  pull_request:\s*$", source):
            raise ValueError(f"{filename}: pull requests are not admitted")
        if not re.search(r"(?m)^  push:\s*$", source):
            raise ValueError(f"{filename}: main push validation missing")
        if not re.search(r"(?m)^    branches:\s*\[main\]\s*$", source):
            raise ValueError(f"{filename}: main push selector missing")
        permission_block = re.search(
            r"(?m)^permissions:\n((?:  [^\n]+\n)+)", source
        )
        if permission_block is None or permission_block.group(1).strip() != "contents: read":
            raise ValueError(f"{filename}: permissions must be exactly contents: read")
        if not re.search(r"(?m)^\s+persist-credentials:\s*false\s*$", source):
            raise ValueError(f"{filename}: checkout credentials must be ephemeral")
        actions = USE_PATTERN.findall(source)
        if not actions:
            raise ValueError(f"{filename}: no pinned checkout found")
        for action in actions:
            if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}", action):
                raise ValueError(f"{filename}: unpinned action")
            name, sha = action.rsplit("@", 1)
            if APPROVED.get(name) != sha:
                raise ValueError(f"{filename}: unreviewed action revision")
        if f"actions/checkout@{APPROVED['actions/checkout']}" not in actions:
            raise ValueError(f"{filename}: reviewed checkout is required")
        filtered = filename != "admission.yml"
        has_paths = bool(re.search(r"(?m)^\s+paths(?:-ignore)?:\s*$", source))
        if has_paths != filtered:
            raise ValueError(f"{filename}: unexpected pull request or push path filters")
    return found


if __name__ == "__main__":
    checked = verify(Path(__file__).resolve().parents[1] / ".github/workflows")
    print(f"Android Defaults source preflight: checked {', '.join(checked)}")
