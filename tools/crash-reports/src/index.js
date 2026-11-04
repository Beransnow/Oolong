/**
 * Takes crash reports and usage heartbeats from the app, and serves the console that reads them
 * (see the README).
 *
 * POST /crash only checks a report and keeps it. Every minute, the scheduled run files kept reports
 * whose trace it has seen before, then works on one new trace: it reads the lines that trace needs
 * from the build's mapping file in R2, a few shards per run so as to stay within the CPU an
 * invocation has, and once all are read retraces it and finds its group. Once a day it drops what
 * has expired, mapping files included (see retention.js).
 *
 * POST /stats adds a heartbeat to the counts (see stats.js). /console is the console (console.js).
 */

import { digest, groupOf, traceKey } from "./group.js";
import { handleConsole } from "./console.js";
import { DAYS, expiredVersions } from "./retention.js";
import { retrace } from "./retrace.js";
import { headerSize, linesLookup, parseHeader, readLines, shardsOf, wantedKeys } from "./shards.js";
import { parseHeartbeat, recordHeartbeat } from "./stats.js";

// As scripts/shard.mjs writes them
const SHARDS = 4096;
// Shards read per run: about 0.2 ms of CPU each
const SHARDS_PER_RUN = 24;
// Seen traces filed per run
const FILED_PER_RUN = 50;
const MAX_BODY = 64 * 1024;
const REPORTS_PER_INSTALL_PER_DAY = 20;
// Past this many waiting reports, new ones are turned away until the backlog clears
const INBOX_LIMIT = 5000;
const SAMPLES_PER_GROUP = 20;
// D1 takes up to 100 bound values per statement
const KEYS_PER_QUERY = 90;
const DAILY = "17 3 * * *";

const KINDS = new Set(["crash", "anr", "native"]);
const VERSION = /^[\w.+-]{1,64}$/;
const INSTALL = /^[\w-]{8,64}$/;

export default {
    async fetch(request, env) {
        const url = new URL(request.url);
        if (url.pathname === "/stats") return heartbeat(request, env);
        if (url.pathname === "/console" || url.pathname.startsWith("/console/")) return handleConsole(request, env, url);
        if (url.pathname !== "/crash") return new Response("not found", { status: 404 });
        if (request.method !== "POST") return new Response("method not allowed", { status: 405 });
        if (Number(request.headers.get("content-length") ?? 0) > MAX_BODY) return new Response("too large", { status: 413 });
        const text = await request.text();
        if (text.length > MAX_BODY) return new Response("too large", { status: 413 });
        let report;
        try {
            report = parseReport(JSON.parse(text));
        } catch {
            return new Response("bad report", { status: 400 });
        }

        const day = new Date().toISOString().slice(0, 10);
        const quota = await env.DB.prepare(
            `INSERT INTO crash_quota (install, day, reports) VALUES (?, ?, 1)
             ON CONFLICT (install, day) DO UPDATE SET reports = reports + 1 RETURNING reports`,
        )
            .bind(report.install, day)
            .first("reports");
        if (quota > REPORTS_PER_INSTALL_PER_DAY) return new Response("enough for today", { status: 429 });
        const waiting = await env.DB.prepare("SELECT COUNT(*) AS n FROM crash_inbox").first("n");
        if (waiting >= INBOX_LIMIT) return new Response("busy", { status: 503 });

        await env.DB.prepare("INSERT INTO crash_inbox (received, body) VALUES (?, ?)")
            .bind(Date.now(), JSON.stringify(report))
            .run();
        return new Response(null, { status: 202 });
    },

    async scheduled(controller, env) {
        if (controller.cron === DAILY) await expire(env);
        else await file(env);
    },
};

/**
 * Adds a heartbeat to the counts. The reply tells the app the share of devices that should send
 * (STATS_RATE, 1 unless set), so that sending can be thinned to stay within the free plan; each
 * heartbeat then counts for 1 / that share. Nothing of the request beyond its body is read.
 */
async function heartbeat(request, env) {
    if (request.method !== "POST") return new Response("method not allowed", { status: 405 });
    const text = await request.text();
    if (text.length > 2048) return new Response("too large", { status: 413 });
    let beat;
    try {
        beat = parseHeartbeat(JSON.parse(text));
    } catch {
        return new Response("bad heartbeat", { status: 400 });
    }
    const rate = Math.min(1, Math.max(0.01, Number(env.STATS_RATE ?? 1) || 1));
    await env.DB.batch(recordHeartbeat(env.DB, beat, Date.now(), rate));
    return new Response(JSON.stringify({ rate }), { headers: { "content-type": "application/json" } });
}

/** The fields of a report the app sends, checked; throws when one is missing or malformed. */
function parseReport(body) {
    const text = (value, max) => {
        if (value === undefined || value === null) return null;
        if (typeof value !== "string" || value.length > max) throw new Error("bad field");
        return value;
    };
    const report = {
        version: text(body.version, 64),
        kind: text(body.kind, 16),
        trace: text(body.trace, MAX_BODY),
        install: text(body.install, 64),
        time: Number.isFinite(body.time) ? body.time : null,
        sdk: Number.isInteger(body.sdk) ? body.sdk : null,
        device: text(body.device, 100),
        rom: text(body.rom, 100),
        page: text(body.page, 100),
        thread: text(body.thread, 100),
    };
    if (!VERSION.test(report.version ?? "") || !KINDS.has(report.kind) || !INSTALL.test(report.install ?? "") || !report.trace) {
        throw new Error("bad report");
    }
    return report;
}

/** Files what it can of the waiting reports. */
async function file(env) {
    const { results } = await env.DB.prepare("SELECT id, received, body FROM crash_inbox ORDER BY id LIMIT ?")
        .bind(FILED_PER_RUN)
        .all();
    let unseen = null;
    for (const row of results) {
        const report = JSON.parse(row.body);
        const key = await digest(traceKey(report.version, report.kind, report.trace));
        const seen = await env.DB.prepare("SELECT group_id, retraced FROM crash_traces WHERE version = ? AND key = ?")
            .bind(report.version, key)
            .first();
        if (seen) {
            await reopen(env, seen.group_id, report);
            await record(env, row, report, seen.group_id, seen.retraced);
        }
        else unseen ??= { row, report, key };
    }
    if (unseen) await work(env, unseen);
}

/** Moves one new trace on: reads some of the lines it needs, or retraces and groups it. */
async function work(env, { row, report, key }) {
    const { version, kind, trace } = report;
    const mapping = await mappingOf(env, version);
    let lookup = () => null;
    if (mapping) {
        const keys = await wantedKeys(trace);
        const lines = await cachedLines(env, version, keys);
        const missing = keys.filter((k) => !lines.has(k));
        if (missing.length > 0) {
            // The rest comes in the next runs
            const indices = shardsOf(missing, SHARDS).slice(0, SHARDS_PER_RUN);
            const found = await readLines(mapping.read, mapping.offsets, SHARDS, indices, missing);
            await cacheLines(env, version, found);
            if (missing.some((k) => !found.has(k))) return;
            for (const [k, json] of found) lines.set(k, json);
        }
        lookup = linesLookup(lines);
    }
    const { text, exceptions } = await retrace(trace, lookup);
    const group = groupOf(kind, exceptions, trace);
    const groupId = await groupFor(env, group, report, row.received);
    await env.DB.prepare("INSERT OR IGNORE INTO crash_traces (version, key, group_id, retraced) VALUES (?, ?, ?, ?)")
        .bind(version, key, groupId, text)
        .run();
    await reopen(env, groupId, report);
    await record(env, row, report, groupId, text);
}

/**
 * The mapping file of [version], with a reader and its shard offsets, or null when there is none:
 * a debug build, or one whose file has expired. Which it is, is kept, and so is when it was needed.
 */
async function mappingOf(env, version) {
    const known = await env.DB.prepare("SELECT state FROM crash_mappings WHERE version = ?").bind(version).first("state");
    if (known === "missing") return null;
    const name = `mapping/${version}.bin`;
    const read = async (offset, length) => {
        const object = await env.MAPPINGS.get(name, { range: { offset, length } });
        if (!object) throw new Error(`${name} is gone`);
        return new Uint8Array(await object.arrayBuffer());
    };
    const header = await env.MAPPINGS.get(name, { range: { offset: 0, length: headerSize(SHARDS) } });
    const state = header ? "present" : "missing";
    await env.DB.prepare(
        `INSERT INTO crash_mappings (version, state, last_seen) VALUES (?, ?, ?)
         ON CONFLICT (version) DO UPDATE SET state = excluded.state, last_seen = excluded.last_seen`,
    )
        .bind(version, state, Date.now())
        .run();
    if (!header) return null;
    return { read, offsets: parseHeader(new Uint8Array(await header.arrayBuffer()), SHARDS) };
}

/** The lines of [keys] already read for [version]: key to JSON, or to null where the mapping has none. */
async function cachedLines(env, version, keys) {
    const lines = new Map();
    for (let i = 0; i < keys.length; i += KEYS_PER_QUERY) {
        const part = keys.slice(i, i + KEYS_PER_QUERY);
        const { results } = await env.DB.prepare(
            `SELECT key, json FROM crash_lines WHERE version = ? AND key IN (${part.map(() => "?").join(", ")})`,
        )
            .bind(version, ...part)
            .all();
        for (const row of results) lines.set(row.key, row.json);
    }
    return lines;
}

async function cacheLines(env, version, lines) {
    const statement = env.DB.prepare("INSERT OR REPLACE INTO crash_lines (version, key, json) VALUES (?, ?, ?)");
    const all = [...lines].map(([key, json]) => statement.bind(version, key, json));
    if (all.length > 0) await env.DB.batch(all);
}

/** The id of [group]'s row, made when the problem is new. */
async function groupFor(env, group, report, received) {
    const key = await digest(group.key);
    const existing = await env.DB.prepare("SELECT id FROM crash_groups WHERE key = ?").bind(key).first("id");
    if (existing !== null) return existing;
    return env.DB.prepare(
        `INSERT INTO crash_groups (key, kind, title, first_version, last_version, first_seen, last_seen)
         VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id`,
    )
        .bind(key, report.kind, group.title, report.version, report.version, received, received)
        .first("id");
}

/**
 * Opens a fixed problem again, as come back, when [report] shows it in a build it hadn't come from
 * before. Builds it came from before the fix still have it, and don't count.
 */
async function reopen(env, groupId, report) {
    await env.DB.prepare(
        `UPDATE crash_groups SET status = 'open', reopened_at = ? WHERE id = ? AND status = 'fixed'
         AND NOT EXISTS (SELECT 1 FROM crash_counts WHERE group_id = ? AND version = ?)`,
    )
        .bind(Date.now(), groupId, groupId, report.version)
        .run();
}

/** Counts a report under its group, keeps it as a sample and takes it out of the inbox. */
async function record(env, row, report, groupId, retraced) {
    const day = new Date(row.received).toISOString().slice(0, 10);
    await env.DB.batch([
        env.DB.prepare("UPDATE crash_groups SET reports = reports + 1, last_seen = ?, last_version = ? WHERE id = ?").bind(
            row.received,
            report.version,
            groupId,
        ),
        env.DB.prepare(
            `INSERT INTO crash_counts (group_id, day, version, reports) VALUES (?, ?, ?, 1)
             ON CONFLICT (group_id, day, version) DO UPDATE SET reports = reports + 1`,
        ).bind(groupId, day, report.version),
        env.DB.prepare("INSERT OR IGNORE INTO crash_installs (group_id, install) VALUES (?, ?)").bind(groupId, report.install),
        env.DB.prepare("INSERT INTO crash_samples (group_id, received, version, body, retraced) VALUES (?, ?, ?, ?, ?)").bind(
            groupId,
            row.received,
            report.version,
            row.body,
            retraced,
        ),
        env.DB.prepare(
            `DELETE FROM crash_samples WHERE group_id = ? AND id NOT IN
             (SELECT id FROM crash_samples WHERE group_id = ? ORDER BY id DESC LIMIT ?)`,
        ).bind(groupId, groupId, SAMPLES_PER_GROUP),
        env.DB.prepare("DELETE FROM crash_inbox WHERE id = ?").bind(row.id),
    ]);
}

/** Drops quotas of past days, and the mapping files retention.js says have expired. */
async function expire(env) {
    const now = Date.now();
    const today = new Date(now).toISOString().slice(0, 10);
    await env.DB.prepare("DELETE FROM crash_quota WHERE day < ?").bind(today).run();

    const since = new Date(now - DAYS * 86_400_000).toISOString().slice(0, 10);
    const { results } = await env.DB.prepare("SELECT DISTINCT version FROM crash_counts WHERE day >= ?").bind(since).all();
    const reported = new Set(results.map((row) => row.version));
    const files = await listed(env.MAPPINGS, "mapping/", ".bin");
    const released = await listed(env.MAPPINGS, "released/", "");
    for (const version of expiredVersions(files, released, reported, now)) {
        await env.MAPPINGS.delete(`mapping/${version}.bin`);
        await env.DB.batch([
            env.DB.prepare("DELETE FROM crash_lines WHERE version = ?").bind(version),
            env.DB.prepare(
                `INSERT INTO crash_mappings (version, state, last_seen) VALUES (?, 'missing', ?)
                 ON CONFLICT (version) DO UPDATE SET state = 'missing'`,
            ).bind(version, now),
        ]);
    }
}

/** The objects under [prefix] in [bucket], as { version, uploaded }: the key without prefix and [suffix]. */
async function listed(bucket, prefix, suffix) {
    const all = [];
    let cursor;
    do {
        const page = await bucket.list({ prefix, cursor });
        for (const object of page.objects) {
            if (!object.key.endsWith(suffix)) continue;
            all.push({ version: object.key.slice(prefix.length, object.key.length - suffix.length), uploaded: object.uploaded.getTime() });
        }
        cursor = page.truncated ? page.cursor : undefined;
    } while (cursor);
    return all;
}
