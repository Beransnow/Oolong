"""Prints a release's notes as Markdown, for its GitHub release: Chinese first, then English.

    python3 tools/release/notes.py tools/release/notes/1.0.0.json > notes.md
"""
import json
import sys


def section(groups):
    lines = []
    for group, items in groups.items():
        lines += [f"### {group}", ""]
        for item in items:
            title, _, detail = item.partition("\n")
            lines.append(f"- **{title}**：{detail}" if detail else f"- {title}")
        lines.append("")
    return lines


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with open(sys.argv[1], encoding="utf-8") as f:
        notes = json.load(f)
    lines = section(notes.get("zh", {}))
    if "en" in notes:
        lines += ["---", ""] + [line.replace("**：", "**: ") for line in section(notes["en"])]
    print("\n".join(lines).strip())


if __name__ == "__main__":
    main()
