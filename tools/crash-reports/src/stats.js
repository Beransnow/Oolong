/**
 * Usage statistics without identifiers. A device sends at most one heartbeat a UTC day; it says
 * whether that is its first in the ISO week and in the month, so active devices are counted by
 * adding those flags up, and nothing in a heartbeat ties it to another. Heartbeats are not kept:
 * each only adds to counts. Values crossed with each other are few-valued; model, full ROM version
 * and locale are counted on their own, once a month, so no row stands for one device's profile.
 * Counts under SMALL are never given out.
 */

export const SMALL = 5;
const VERSION = /^[\w.+-]{1,64}$/;
const FORMS = new Set(["phone", "tablet", "foldable"]);
const CHANNELS = new Set(["stable", "beta", ""]);

/** A heartbeat's fields, checked and trimmed; throws when one is malformed. */
export function parseHeartbeat(body) {
    const text = (value, max) => {
        if (value === undefined || value === null) return "";
        if (typeof value !== "string" || value.length > max || /[\n\t]/.test(value)) throw new Error("bad field");
        return value.trim();
    };
    const flag = (value) => {
        if (typeof value !== "boolean") throw new Error("bad flag");
        return value;
    };
    const beat = {
        version: text(body.version, 64),
        channel: text(body.channel, 16),
        sdk: body.sdk,
        brand: text(body.brand, 32),
        rom: text(body.rom, 32),
        form: text(body.form, 16),
        crashed: flag(body.crashed),
        week: flag(body.week),
        month: flag(body.month),
        installWeek: body.installWeek ?? null,
    };
    if (!VERSION.test(beat.version) || !CHANNELS.has(beat.channel) || !FORMS.has(beat.form)) throw new Error("bad heartbeat");
    if (!Number.isInteger(beat.sdk) || beat.sdk < 1 || beat.sdk > 99) throw new Error("bad sdk");
    if (beat.installWeek !== null && !(Number.isInteger(beat.installWeek) && beat.installWeek >= 0 && beat.installWeek < 8)) {
        throw new Error("bad install week");
    }
    if (beat.month) {
        beat.model = text(body.model, 64);
        beat.romVersion = text(body.romVersion, 100);
        beat.locale = text(body.locale, 20);
    }
    return beat;
}

/** The UTC day of [time], as YYYY-MM-DD. */
export function dayOf(time) {
    return new Date(time).toISOString().slice(0, 10);
}

/** The Monday starting the ISO week of [time], as YYYY-MM-DD. */
export function weekOf(time) {
    const date = new Date(time);
    const back = (date.getUTCDay() + 6) % 7;
    return dayOf(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() - back));
}

/** The statements adding [beat], received at [time] and sampled at [rate], to the counts. */
export function recordHeartbeat(db, beat, time, rate) {
    const weight = 1 / rate;
    const statements = [
        db
            .prepare(
                `INSERT INTO stats_daily (day, version, channel, sdk, brand, rom, form, crashed, devices, week_first, month_first)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT (day, version, channel, sdk, brand, rom, form, crashed) DO UPDATE SET
                     devices = devices + excluded.devices,
                     week_first = week_first + excluded.week_first,
                     month_first = month_first + excluded.month_first`,
            )
            .bind(
                dayOf(time),
                beat.version,
                beat.channel,
                beat.sdk,
                beat.brand,
                beat.rom,
                beat.form,
                beat.crashed ? 1 : 0,
                weight,
                beat.week ? weight : 0,
                beat.month ? weight : 0,
            ),
    ];
    if (beat.week && beat.installWeek !== null) {
        const installed = weekOf(time - beat.installWeek * 7 * 86_400_000);
        statements.push(
            db
                .prepare(
                    `INSERT INTO stats_retention (install_week, weeks, devices) VALUES (?, ?, ?)
                     ON CONFLICT (install_week, weeks) DO UPDATE SET devices = devices + excluded.devices`,
                )
                .bind(installed, beat.installWeek, weight),
        );
    }
    if (beat.month) {
        const month = dayOf(time).slice(0, 7);
        for (const [field, value] of [["model", beat.model], ["romVersion", beat.romVersion], ["locale", beat.locale]]) {
            statements.push(
                db
                    .prepare(
                        `INSERT INTO stats_monthly (month, field, value, devices) VALUES (?, ?, ?, ?)
                         ON CONFLICT (month, field, value) DO UPDATE SET devices = devices + excluded.devices`,
                    )
                    .bind(month, field, value, weight),
            );
        }
    }
    return statements;
}

/** [count] rounded, or null when it is under SMALL and must not be shown. */
export function shown(count) {
    const n = Math.round(count);
    return n < SMALL ? null : n;
}

/** The overview of [channel] ("" for all) as of [now]: every count masked by shown(). */
export async function overview(db, channel, now) {
    const day = 86_400_000;
    const today = dayOf(now);
    const yesterday = dayOf(now - day);
    const from = dayOf(now - 29 * day);
    const thisWeek = weekOf(now);
    const lastWeek = weekOf(now - 7 * day);
    const thisMonth = today.slice(0, 7);
    const lastMonth = dayOf(Date.UTC(new Date(now).getUTCFullYear(), new Date(now).getUTCMonth(), 0)).slice(0, 7);
    const where = channel ? "AND channel = ?" : "";
    const bind = (...args) => (channel ? [...args, channel] : args);
    const all = async (sql, ...args) => (await db.prepare(sql).bind(...args).all()).results;

    const daily = await all(
        `SELECT day, SUM(devices) AS devices, SUM(crashed * devices) AS crashed FROM stats_daily
         WHERE day >= ? ${where} GROUP BY day ORDER BY day`,
        ...bind(from),
    );
    const sum = async (column, since, until) =>
        (await all(`SELECT SUM(${column}) AS n FROM stats_daily WHERE day >= ? AND day < ? ${where}`, ...bind(since, until)))[0]?.n ?? 0;
    const nextDay = dayOf(now + day);
    const weekActive = await sum("week_first", thisWeek, nextDay);
    const lastWeekActive = await sum("week_first", lastWeek, thisWeek);
    const monthActive = await sum("month_first", `${thisMonth}-01`, nextDay);
    const lastMonthActive = await sum("month_first", `${lastMonth}-01`, `${thisMonth}-01`);

    const byDay = new Map(daily.map((row) => [row.day, row]));
    const days = [];
    for (let t = now - 29 * day; dayOf(t) <= today; t += day) {
        const row = byDay.get(dayOf(t));
        days.push({ day: dayOf(t), devices: shown(row?.devices ?? 0) });
    }
    const y = byDay.get(yesterday);
    const crashFree = (devices, crashed) => (shown(devices) === null ? null : Math.round((1 - crashed / devices) * 1000) / 10);

    // Versions by yesterday's devices; their crash-free rate over the last 7 days
    const versions = await all(
        `SELECT version, SUM(CASE WHEN day = ? THEN devices ELSE 0 END) AS yesterday,
                SUM(devices) AS devices, SUM(crashed * devices) AS crashed
         FROM stats_daily WHERE day >= ? AND day < ? ${where} GROUP BY version ORDER BY yesterday DESC`,
        ...bind(yesterday, dayOf(now - 7 * day), today),
    );
    const versionTotal = versions.reduce((a, v) => a + v.yesterday, 0);

    // Distributions over the last full ISO week, each device counted once by its first heartbeat
    const distribution = async (column) =>
        masked(
            await all(
                `SELECT ${column} AS value, SUM(week_first) AS devices FROM stats_daily
                 WHERE day >= ? AND day < ? ${where} GROUP BY ${column} ORDER BY devices DESC`,
                ...bind(lastWeek, thisWeek),
            ),
        );
    const monthly = async (field) =>
        masked(
            await all(
                "SELECT value, devices FROM stats_monthly WHERE month = ? AND field = ? ORDER BY devices DESC LIMIT 12",
                thisMonth,
                field,
            ),
        );
    const retention = await all(
        "SELECT install_week, weeks, devices FROM stats_retention WHERE install_week >= ? ORDER BY install_week, weeks",
        weekOf(now - 9 * 7 * day),
    );
    const cohorts = new Map();
    for (const row of retention) {
        const cohort = cohorts.get(row.install_week) ?? [];
        cohort[row.weeks] = row.devices;
        cohorts.set(row.install_week, cohort);
    }

    return {
        today,
        days,
        dayActive: shown(y?.devices ?? 0),
        weekActive: shown(weekActive),
        lastWeekActive: shown(lastWeekActive),
        monthActive: shown(monthActive),
        lastMonthActive: shown(lastMonthActive),
        crashFree: y ? crashFree(y.devices, y.crashed) : null,
        versions: versions
            .filter((v) => shown(v.yesterday) !== null)
            .map((v) => ({
                version: v.version,
                devices: shown(v.yesterday),
                share: Math.round((v.yesterday / versionTotal) * 100),
                crashFree: crashFree(v.devices, v.crashed),
            })),
        sdk: await distribution("sdk"),
        brand: await distribution("brand"),
        rom: await distribution("rom"),
        form: await distribution("form"),
        model: await monthly("model"),
        romVersion: await monthly("romVersion"),
        locale: await monthly("locale"),
        cohorts: [...cohorts].map(([week, counts]) => {
            const size = counts[0] ?? 0;
            return {
                week,
                devices: shown(size),
                // A share of a cohort under SMALL would give its counts away
                kept: shown(size) === null ? [] : counts.map((n) => (n === undefined ? null : shown(n) === null ? null : Math.round((n / size) * 100))),
            };
        }),
    };
}

/** [rows] of { value, devices } with counts under SMALL folded into one "<5" row's total. */
function masked(rows) {
    const out = [];
    let rest = 0;
    for (const row of rows) {
        if (shown(row.devices) === null) rest += row.devices;
        else out.push({ value: row.value, devices: shown(row.devices) });
    }
    const total = rows.reduce((a, r) => a + r.devices, 0);
    for (const row of out) row.share = Math.round((row.devices / total) * 100);
    if (rest > 0) out.push({ value: null, devices: shown(rest), share: null });
    return out;
}
