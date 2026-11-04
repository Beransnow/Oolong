import assert from "node:assert/strict";
import { test } from "node:test";
import { parseHeartbeat, recordHeartbeat, shown, weekOf } from "../src/stats.js";

const beat = { version: "1.0.0-alpha.3+701f5613", channel: "beta", sdk: 35, brand: "Xiaomi", rom: "HyperOS 2", form: "phone", crashed: false, week: true, month: false };

test("takes a well-formed heartbeat", () => {
    const parsed = parseHeartbeat({ ...beat, installWeek: 3 });
    assert.equal(parsed.installWeek, 3);
    assert.equal(parsed.model, undefined);
});

test("keeps the monthly values only in the month's first heartbeat", () => {
    const monthly = { model: "2210132C", romVersion: "OS2.0.6.0.VMCCNXM", locale: "zh-CN" };
    assert.equal(parseHeartbeat({ ...beat, ...monthly }).model, undefined);
    assert.equal(parseHeartbeat({ ...beat, month: true, ...monthly }).model, "2210132C");
});

test("turns away malformed heartbeats", () => {
    for (const bad of [
        { ...beat, version: "1.0 beta" },
        { ...beat, form: "watch" },
        { ...beat, sdk: 0 },
        { ...beat, crashed: "no" },
        { ...beat, installWeek: 8 },
        { ...beat, brand: "x".repeat(33) },
        { ...beat, rom: "a\tb" },
    ]) {
        assert.throws(() => parseHeartbeat(bad));
    }
});

test("starts ISO weeks on Monday in UTC", () => {
    assert.equal(weekOf(Date.UTC(2026, 10, 5, 12)), "2026-11-02");
    assert.equal(weekOf(Date.UTC(2026, 10, 2, 0, 0)), "2026-11-02");
    assert.equal(weekOf(Date.UTC(2026, 10, 1, 23, 59)), "2026-10-26");
});

test("never gives out a count under 5", () => {
    assert.equal(shown(4.4), null);
    assert.equal(shown(5), 5);
    assert.equal(shown(12.6), 13);
});

test("writes the cohort only for the week's first heartbeat, and the monthly values only for the month's", () => {
    const tables = (b) => {
        const sql = [];
        const db = { prepare: (s) => ({ bind: () => sql.push(s.match(/INTO (\w+)/)[1]) }) };
        recordHeartbeat(db, parseHeartbeat(b), Date.UTC(2026, 10, 5), 1);
        return sql;
    };
    assert.deepEqual(tables({ ...beat, week: false, installWeek: 2 }), ["stats_daily"]);
    assert.deepEqual(tables({ ...beat, installWeek: 2 }), ["stats_daily", "stats_retention"]);
    assert.deepEqual(tables({ ...beat, month: true, model: "m", romVersion: "r", locale: "zh-CN" }), ["stats_daily", "stats_monthly", "stats_monthly", "stats_monthly"]);
});
