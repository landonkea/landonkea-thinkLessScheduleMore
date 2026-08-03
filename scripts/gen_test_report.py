#!/usr/bin/env python3
"""Parse Android JUnit XML results and/or an iOS xcodebuild test log, and
write (or update) a combined Markdown summary at test-results/latest.md.

Not meant to be run directly in normal usage -- scripts/run_all_tests.sh
calls this after running (or locating the results of) each platform's
test suite. See that script and README.md ("Testing" section) for how
the two fit together.
"""

from __future__ import annotations

import argparse
import glob
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path


def parse_android(xml_dir: str) -> dict:
    files = sorted(glob.glob(str(Path(xml_dir) / "TEST-*.xml")))
    if not files:
        return {
            "ran": True,
            "found_results": False,
            "tests": 0,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
            "passed": 0,
            "failure_list": [],
        }

    tests = failures = errors = skipped = 0
    failure_list: list[str] = []

    for f in files:
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
        for tc in root.findall("testcase"):
            node = tc.find("failure")
            if node is None:
                node = tc.find("error")
            if node is None:
                continue
            name = tc.get("name", "?")
            classname = tc.get("classname", "?")
            message = (node.get("message") or "").strip().splitlines()[0] if node.get("message") else "(no message)"
            failure_list.append(f"{classname}.{name}: {message}")

    return {
        "ran": True,
        "found_results": True,
        "tests": tests,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "passed": tests - failures - errors - skipped,
        "failure_list": failure_list,
    }


EXEC_RE = re.compile(
    r"Executed (\d+) tests?, with (\d+) failures? \((\d+) unexpected\) in [\d.]+ \([\d.]+\) seconds"
)
FAILURE_DETAIL_RE = re.compile(r"error: -\[(\S+) (\w+)\] : (.+)")
FAILURE_CASE_RE = re.compile(r"Test Case '-\[(\S+) (\w+)\]' failed")


def parse_ios(log_path: str) -> dict:
    path = Path(log_path)
    if not path.exists() or path.stat().st_size == 0:
        return {
            "ran": True,
            "found_results": False,
            "total": 0,
            "failures": 0,
            "passed": 0,
            "failure_list": [],
        }

    text = path.read_text(encoding="utf-8", errors="replace")

    matches = EXEC_RE.findall(text)
    if matches:
        total, failures, _unexpected = (int(x) for x in matches[-1])
    else:
        total = failures = 0

    failure_list = [
        f"{m.group(1)}.{m.group(2)}: {m.group(3).strip()}" for m in FAILURE_DETAIL_RE.finditer(text)
    ]
    if not failure_list:
        seen = set()
        for m in FAILURE_CASE_RE.finditer(text):
            key = (m.group(1), m.group(2))
            if key in seen:
                continue
            seen.add(key)
            failure_list.append(f"{m.group(1)}.{m.group(2)}: failed (see full log)")

    found_results = bool(matches)

    return {
        "ran": True,
        "found_results": found_results,
        "total": total,
        "failures": failures,
        "passed": total - failures,
        "failure_list": failure_list,
    }


def render_android_section(data: dict | None) -> str:
    if data is None:
        return "### Android\n\n_Not run in this invocation._\n"
    if not data["found_results"]:
        return (
            "### Android\n\n"
            "**No results found** — the build likely failed before any tests ran. "
            "Check the raw `./gradlew testDebugUnitTest` output.\n"
        )
    status = "PASS" if data["failures"] == 0 and data["errors"] == 0 else "FAIL"
    lines = [
        "### Android",
        "",
        f"**{status}** — {data['passed']} passed, {data['failures']} failed, "
        f"{data['errors']} errors, {data['skipped']} skipped (of {data['tests']} total)",
    ]
    if data["failure_list"]:
        lines.append("")
        lines.append("Failures:")
        for f in data["failure_list"]:
            lines.append(f"- {f}")
    lines.append("")
    return "\n".join(lines)


def render_ios_section(data: dict | None) -> str:
    if data is None:
        return "### iOS\n\n_Not run in this invocation._\n"
    if not data["found_results"]:
        return (
            "### iOS\n\n"
            "**No results found** — the build likely failed before any tests ran. "
            "Check the raw `xcodebuild test` output.\n"
        )
    status = "PASS" if data["failures"] == 0 else "FAIL"
    lines = [
        "### iOS",
        "",
        f"**{status}** — {data['passed']} passed, {data['failures']} failed (of {data['total']} total)",
    ]
    if data["failure_list"]:
        lines.append("")
        lines.append("Failures:")
        for f in data["failure_list"]:
            lines.append(f"- {f}")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-xml-dir")
    parser.add_argument("--ios-log")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    android_data = parse_android(args.android_xml_dir) if args.android_xml_dir else None
    ios_data = parse_ios(args.ios_log) if args.ios_log else None

    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    parts = [
        "# Test results",
        "",
        f"Generated: {timestamp}",
        "",
        render_android_section(android_data),
        render_ios_section(ios_data),
    ]

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(parts).rstrip() + "\n", encoding="utf-8")
    print(f"Wrote {out_path}")

    failed = False
    if android_data and android_data["found_results"] and (android_data["failures"] or android_data["errors"]):
        failed = True
    if android_data and not android_data["found_results"]:
        failed = True
    if ios_data and ios_data["found_results"] and ios_data["failures"]:
        failed = True
    if ios_data and not ios_data["found_results"]:
        failed = True

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
