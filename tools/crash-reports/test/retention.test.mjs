import assert from "node:assert/strict";
import { test } from "node:test";
import { DAYS, RELEASES, expiredVersions } from "../src/retention.js";

const day = 86_400_000;
const now = 1_000 * day;

test("drops an old build no report has come from", () => {
    const files = [{ version: "1.0+a", uploaded: now - (DAYS + 1) * day }];
    assert.deepEqual(expiredVersions(files, [], new Set(), now), ["1.0+a"]);
});

test("keeps a build uploaded within the last days", () => {
    const files = [{ version: "1.0+a", uploaded: now - (DAYS - 1) * day }];
    assert.deepEqual(expiredVersions(files, [], new Set(), now), []);
});

test("keeps an old build reports still come from", () => {
    const files = [{ version: "1.0+a", uploaded: now - 90 * day }];
    assert.deepEqual(expiredVersions(files, [], new Set(["1.0+a"]), now), []);
});

test("keeps the latest released builds however quiet, and only those", () => {
    const released = Array.from({ length: RELEASES + 1 }, (_, i) => ({ version: `r${i}`, uploaded: now - (100 + i) * day }));
    const files = released.map((r) => ({ ...r }));
    // The oldest release falls out of the latest RELEASES
    assert.deepEqual(expiredVersions(files, released, new Set(), now), [`r${RELEASES}`]);
});
