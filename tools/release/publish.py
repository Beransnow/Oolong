"""Publishes a release APK for the in-app updater: uploads it to the R2 bucket behind
oolong.huanchengfly.top, then points the channel's manifest at it.

    CLOUDFLARE_API_TOKEN=… python3 tools/release/publish.py app.apk --channel stable --notes notes.json

The token needs Workers R2 Storage Write on the account. notes.json holds what changed, by language
and then by group in the order shown, e.g. {"zh": {"新增": ["…"], "修复": ["…"]}, "en": {…}}.

Only an APK signed with the Oolong release key is published: the app installs an update only when it
is signed as the app installed is, so anything else would be offered and then refused. The APK goes
up first and the manifest last, so a manifest never names an APK that isn't there yet.
"""
import argparse
import datetime
import glob
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

ACCOUNT_ID = "ed899b858f579425335f6c5e9bc52bc6"
BUCKET = "oolong-releases"
HOST = "https://oolong.huanchengfly.top"
RELEASE_CERT_SHA256 = "9cbc612f0bbdfba80000d021cd73b1e6e619c186fdc6b32f1e11382bd9e4b725"


def build_tool(name):
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/android-sdk")
    found = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", name)))
    if not found:
        sys.exit(f"{name} not found under {sdk}/build-tools; set ANDROID_HOME")
    return found[-1]


def badging(apk):
    out = subprocess.run([build_tool("aapt2"), "dump", "badging", apk], capture_output=True, text=True, check=True).stdout
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", out)
    if not m:
        sys.exit("could not read the APK's package line")
    return m.group(1), int(m.group(2)), m.group(3)


def signer_digests(apk):
    out = subprocess.run([build_tool("apksigner"), "verify", "--print-certs", apk], capture_output=True, text=True, check=True).stdout
    return set(re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", out))


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def upload(token, key, data, content_type, cache_control=None):
    url = f"https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/r2/buckets/{BUCKET}/objects/{key}"
    headers = {"Authorization": f"Bearer {token}", "Content-Type": content_type}
    if cache_control:
        headers["Cache-Control"] = cache_control
    request = urllib.request.Request(url, data=data, method="PUT", headers=headers)
    try:
        with urllib.request.urlopen(request) as response:
            body = json.load(response)
    except urllib.error.HTTPError as e:
        sys.exit(f"upload of {key} failed: HTTP {e.code} {e.read().decode(errors='replace')}")
    if not body.get("success"):
        sys.exit(f"upload of {key} failed: {body.get('errors')}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("apk")
    parser.add_argument("--channel", choices=["stable", "beta"], required=True)
    parser.add_argument("--notes", required=True, help="JSON file of what changed, by language and group")
    parser.add_argument("--min-supported", type=int, default=0, help="versions below this one must update")
    parser.add_argument("--dry-run", action="store_true", help="print the manifest without uploading")
    args = parser.parse_args()

    package, version_code, version_name = badging(args.apk)
    if package != "oolong.client":
        sys.exit(f"not Oolong: {package}")
    if RELEASE_CERT_SHA256 not in signer_digests(args.apk):
        sys.exit("not signed with the Oolong release key (Astral Studio); refusing to publish")
    # Pre-releases go to beta, releases to stable; beta offers releases too, by reading both
    pre_release = "-" in version_name.split("+")[0]
    if pre_release != (args.channel == "beta"):
        sys.exit(f"{version_name} is {'a pre-release' if pre_release else 'a release'}: publish it to {'beta' if pre_release else 'stable'}")
    with open(args.notes, encoding="utf-8") as f:
        notes = json.load(f)

    key = f"apk/oolong-{version_name}.apk"
    manifest = {
        "versionCode": version_code,
        "versionName": version_name,
        "publishedAt": datetime.date.today().isoformat(),
        "url": f"{HOST}/{key}",
        "size": os.path.getsize(args.apk),
        "sha256": sha256(args.apk),
        "notes": notes,
        "minSupported": args.min_supported,
    }
    text = json.dumps(manifest, ensure_ascii=False, indent=2)
    print(text)
    if args.dry_run:
        return

    token = os.environ.get("CLOUDFLARE_API_TOKEN") or sys.exit("set CLOUDFLARE_API_TOKEN")
    with open(args.apk, "rb") as f:
        upload(token, key, f.read(), "application/vnd.android.package-archive")
    # Read fresh on every check, so a release shows as soon as it is published
    upload(token, f"update/{args.channel}.json", text.encode("utf-8"), "application/json", cache_control="no-cache")
    print(f"published {version_name} ({version_code}) to {args.channel}: {HOST}/update/{args.channel}.json")


if __name__ == "__main__":
    main()
