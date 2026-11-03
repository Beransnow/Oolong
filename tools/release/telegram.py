"""Sends a build to Telegram through the notification bot: the APK with a caption, then, given
release notes, the notes as a message of their own.

    TELEGRAM_BOT_TOKEN=… python3 tools/release/telegram.py --chat 123 --apk app.apk --caption "…"
    TELEGRAM_BOT_TOKEN=… python3 tools/release/telegram.py --chat -100… --apk app.apk --caption "…" \\
        --notes tools/release/notes/1.0.0.json

Captions and notes go as HTML, escaped here, so a commit message can hold any character. Telegram
cuts a caption at 1024 characters and a message at 4096; longer text is shortened to fit.
"""
import argparse
import html
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

CAPTION_LIMIT = 1024
MESSAGE_LIMIT = 4096


def call(token, method, fields, files=None):
    boundary = uuid.uuid4().hex
    body = bytearray()
    for name, value in fields.items():
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
    for name, path in (files or {}).items():
        with open(path, "rb") as f:
            data = f.read()
        filename = os.path.basename(path)
        body += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
            "Content-Type: application/vnd.android.package-archive\r\n\r\n"
        ).encode()
        body += data + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}",
        data=bytes(body),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urllib.request.urlopen(request) as response:
            result = json.load(response)
    except urllib.error.HTTPError as e:
        result = json.loads(e.read() or b"{}")
    if not result.get("ok"):
        sys.exit(f"{method} failed: {result.get('description')}")


def escaped_within(text, limit):
    """[text] escaped as HTML, shortened first if its escaped form would not fit in [limit]."""
    if len(html.escape(text)) <= limit:
        return html.escape(text)
    cut = limit - 1
    while cut > 0 and len(html.escape(text[:cut])) > limit - 1:
        cut -= 1
    return html.escape(text[:cut]) + "…"


def lines_within(lines, limit):
    """As many whole [lines] as fit in [limit] when joined, ending in … if some were left out."""
    kept = []
    for line in lines:
        if len("\n".join(kept + [line, "…"])) > limit:
            return "\n".join(kept + ["…"])
        kept.append(line)
    return "\n".join(kept)


def render_notes(path, lang):
    """The notes as HTML lines: each group in bold, then its items, a title in bold before what it does."""
    with open(path, encoding="utf-8") as f:
        notes = json.load(f)
    groups = notes.get(lang) or notes.get("zh") or {}
    lines = []
    for group, items in groups.items():
        lines.append(f"<b>{html.escape(group)}</b>")
        for item in items:
            title, _, detail = item.partition("\n")
            lines.append(f"· <b>{html.escape(title)}</b>：{html.escape(detail)}" if detail else f"· {html.escape(title)}")
        lines.append("")
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--chat", required=True, help="the chat to send to: a user's id, or a group's")
    parser.add_argument("--apk", required=True)
    parser.add_argument("--caption", required=True, help="plain text, escaped here")
    parser.add_argument("--notes", help="release notes JSON, sent after the APK")
    parser.add_argument("--lang", default="zh")
    args = parser.parse_args()

    token = os.environ.get("TELEGRAM_BOT_TOKEN") or sys.exit("set TELEGRAM_BOT_TOKEN")
    caption = escaped_within(args.caption, CAPTION_LIMIT)
    call(token, "sendDocument", {"chat_id": args.chat, "caption": caption, "parse_mode": "HTML"}, {"document": args.apk})
    if args.notes:
        text = lines_within(render_notes(args.notes, args.lang), MESSAGE_LIMIT).strip()
        call(token, "sendMessage", {"chat_id": args.chat, "text": text, "parse_mode": "HTML"})


if __name__ == "__main__":
    main()
