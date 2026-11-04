"""Prints the caption of a release's APK in the group, as Telegram HTML: the version, the date and
the Android versions it runs on, the release's own word if it has one, the APK's SHA-256 and the
request not to repost it.

    python3 tools/release/caption.py oolong-1.0.0.apk 1.0.0 stable

A release's own word is kept in tools/release/notes/<version>.intro.txt, plain text, for what only
that release needs to say, such as the new name in the first one.
"""
import datetime
import html
import os
import re
import subprocess
import sys
from zoneinfo import ZoneInfo

from publish import build_tool, sha256

# Android's version for each API level an Oolong release may need
ANDROID_VERSIONS = {24: "7.0", 25: "7.1", 26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11", 31: "12", 32: "12L", 33: "13"}


def min_android(apk):
    out = subprocess.run([build_tool("aapt2"), "dump", "badging", apk], capture_output=True, text=True, check=True).stdout
    level = int(re.search(r"minSdkVersion:'(\d+)'", out).group(1))
    return ANDROID_VERSIONS.get(level) or sys.exit(f"no Android version known for API {level}")


def main():
    apk, version, channel = sys.argv[1:4]
    today = datetime.datetime.now(ZoneInfo("Asia/Shanghai")).date()
    lines = [
        f"<b>Oolong {html.escape(version)} {'测试版' if channel == 'beta' else '正式版'}</b>",
        f"{today.year} 年 {today.month} 月 {today.day} 日 · Android {min_android(apk)} 及以上",
    ]
    intro = os.path.join(os.path.dirname(__file__), "notes", f"{version}.intro.txt")
    if os.path.exists(intro):
        with open(intro, encoding="utf-8") as f:
            lines += ["", html.escape(f.read().strip())]
    lines += ["", f"SHA-256 <code>{sha256(apk)}</code>", "", "请勿以任何形式二次转载，感谢理解与配合。"]
    print("\n".join(lines))


if __name__ == "__main__":
    main()
