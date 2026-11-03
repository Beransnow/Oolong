#!/usr/bin/env bash
# Writes keystore.properties for the Oolong release key from CI secrets: KEYSTORE_BASE64 (the .jks,
# base64), KEYSTORE_PASSWORD, KEY_ALIAS (oolong unless set) and KEY_PASSWORD. Without them it writes
# nothing and release builds fall back to the debug key, unless REQUIRE_SIGNING is set.
set -euo pipefail

if [ -z "${KEYSTORE_BASE64:-}" ]; then
    if [ -n "${REQUIRE_SIGNING:-}" ]; then
        echo "::error::the release key's secrets are not set" >&2
        exit 1
    fi
    echo "No release key: release builds are signed with the debug key"
    exit 0
fi

base64 -d <<<"$KEYSTORE_BASE64" > release.jks
{
    echo "keystore.file=release.jks"
    echo "keystore.password=$KEYSTORE_PASSWORD"
    echo "keystore.key.alias=${KEY_ALIAS:-oolong}"
    echo "keystore.key.password=$KEY_PASSWORD"
} > keystore.properties
