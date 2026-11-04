"""Puts a build's mapping file in the crash Worker's bucket, or marks a build as released there, keyed
by the APK's versionName, the one its crash reports carry:

    python3 tools/crash-reports/scripts/upload.py mapping app.apk mapping.bin    # every CI build
    python3 tools/crash-reports/scripts/upload.py released app.apk               # on release

CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN (Workers R2 Storage Write) come from the environment.
Released builds keep their mapping file longer; see src/retention.js.
"""
import argparse
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "release"))
from publish import badging  # noqa: E402

BUCKET = "oolong-mappings"


def put(account, token, key, data, content_type):
    url = f"https://api.cloudflare.com/client/v4/accounts/{account}/r2/buckets/{BUCKET}/objects/{urllib.parse.quote(key)}"
    request = urllib.request.Request(
        url, data=data, method="PUT", headers={"Authorization": f"Bearer {token}", "Content-Type": content_type}
    )
    try:
        with urllib.request.urlopen(request) as response:
            response.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"upload of {key} failed: HTTP {e.code} {e.read().decode(errors='replace')}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("what", choices=["mapping", "released"])
    parser.add_argument("apk")
    parser.add_argument("file", nargs="?", help="the mapping file scripts/shard.mjs wrote, for mapping")
    args = parser.parse_args()
    account = os.environ.get("CLOUDFLARE_ACCOUNT_ID") or sys.exit("set CLOUDFLARE_ACCOUNT_ID")
    token = os.environ.get("CLOUDFLARE_API_TOKEN") or sys.exit("set CLOUDFLARE_API_TOKEN")
    _, _, version = badging(args.apk)
    if args.what == "mapping":
        if not args.file:
            sys.exit("give the mapping file")
        with open(args.file, "rb") as f:
            put(account, token, f"mapping/{version}.bin", f.read(), "application/octet-stream")
    else:
        put(account, token, f"released/{version}", b"", "text/plain")
    print(f"{args.what} of {version} uploaded")


if __name__ == "__main__":
    main()
