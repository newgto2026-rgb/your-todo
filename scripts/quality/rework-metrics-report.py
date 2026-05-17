#!/usr/bin/env python3

import argparse
import re
from pathlib import Path


FIELD_RE = re.compile(r"^\|\s*([^|]+?)\s*\|\s*`?([^|`]+?)`?\s*\|\s*$")


def parse_summary(path: Path) -> dict[str, str]:
    values: dict[str, str] = {"Document": str(path)}
    in_summary = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() == "## Branch Summary":
            in_summary = True
            continue
        if in_summary and line.startswith("## "):
            break
        if in_summary:
            match = FIELD_RE.match(line)
            if match and match.group(1) not in {"Field", "---"}:
                values[match.group(1).strip()] = match.group(2).strip()
    return values


def int_value(summary: dict[str, str], key: str) -> int:
    try:
        return int(summary.get(key, "0"))
    except ValueError:
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate a Markdown report from branch rework metrics docs.")
    parser.add_argument("--branches-dir", default="docs/ai-rework/branches")
    parser.add_argument("--output", default="")
    args = parser.parse_args()

    branches_dir = Path(args.branches_dir)
    summaries = [parse_summary(path) for path in sorted(branches_dir.glob("*.md"))]

    total_rework = sum(int_value(item, "Rework Count") for item in summaries)
    p0 = sum(int_value(item, "P0 Issues") for item in summaries)
    p1 = sum(int_value(item, "P1 Issues") for item in summaries)
    p2 = sum(int_value(item, "P2 Issues") for item in summaries)
    automation_possible = sum(int_value(item, "Automation Possible Issues") for item in summaries)
    automation_added = sum(int_value(item, "Automation Added Issues") for item in summaries)
    open_events = sum(int_value(item, "Open Events") for item in summaries)

    lines = [
        "# AI Rework Metrics Report",
        "",
        "## Summary",
        "",
        "| Metric | Value |",
        "|---|---|",
        f"| Branch Documents | `{len(summaries)}` |",
        f"| Rework Count | `{total_rework}` |",
        f"| P0/P1/P2 Issues | `{p0}/{p1}/{p2}` |",
        f"| Automation Possible Issues | `{automation_possible}` |",
        f"| Automation Added Issues | `{automation_added}` |",
        f"| Open Events | `{open_events}` |",
        "",
        "## Branches",
        "",
        "| Branch | PR | Model | Rework | P0/P1/P2 | Automation Possible | Automation Added | Open | Document |",
        "|---|---|---|---:|---:|---:|---:|---:|---|",
    ]

    for item in summaries:
        lines.append(
            "| {branch} | {pr} | {model} | `{rework}` | `{p0}/{p1}/{p2}` | `{possible}` | `{added}` | `{open_events}` | `{doc}` |".format(
                branch=item.get("Branch", "TBD"),
                pr=item.get("PR", "TBD"),
                model=item.get("Primary AI Model", "TBD"),
                rework=item.get("Rework Count", "0"),
                p0=item.get("P0 Issues", "0"),
                p1=item.get("P1 Issues", "0"),
                p2=item.get("P2 Issues", "0"),
                possible=item.get("Automation Possible Issues", "0"),
                added=item.get("Automation Added Issues", "0"),
                open_events=item.get("Open Events", "0"),
                doc=item.get("Document", ""),
            )
        )

    output = "\n".join(lines) + "\n"
    if args.output:
      Path(args.output).parent.mkdir(parents=True, exist_ok=True)
      Path(args.output).write_text(output, encoding="utf-8")
    else:
      print(output, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
