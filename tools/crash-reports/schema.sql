-- Reports as they arrive, until the scheduled run files them under a group
CREATE TABLE IF NOT EXISTS crash_inbox (
    id INTEGER PRIMARY KEY,
    received INTEGER NOT NULL,
    -- The report as the app sent it, JSON
    body TEXT NOT NULL
);

-- Reports per install per day, to cap what one install can send
CREATE TABLE IF NOT EXISTS crash_quota (
    install TEXT NOT NULL,
    day TEXT NOT NULL,
    reports INTEGER NOT NULL,
    PRIMARY KEY (install, day)
);

-- One row per problem
CREATE TABLE IF NOT EXISTS crash_groups (
    id INTEGER PRIMARY KEY,
    -- Digest of the group key (see group.js)
    key TEXT NOT NULL UNIQUE,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    first_version TEXT NOT NULL,
    last_version TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL,
    reports INTEGER NOT NULL DEFAULT 0,
    -- open, fixed or ignored; a fixed problem seen again in a new build is open again
    status TEXT NOT NULL DEFAULT 'open',
    -- When it was opened again that way, to show it as come back
    reopened_at INTEGER,
    -- The commit that fixed it, and what was done or decided, in a line
    fixed_by TEXT,
    note TEXT
);

-- Reports per group, day and build
CREATE TABLE IF NOT EXISTS crash_counts (
    group_id INTEGER NOT NULL,
    day TEXT NOT NULL,
    version TEXT NOT NULL,
    reports INTEGER NOT NULL,
    PRIMARY KEY (group_id, day, version)
);

-- The installs each group has reached
CREATE TABLE IF NOT EXISTS crash_installs (
    group_id INTEGER NOT NULL,
    install TEXT NOT NULL,
    PRIMARY KEY (group_id, install)
);

-- The latest reports of each group, in full
CREATE TABLE IF NOT EXISTS crash_samples (
    id INTEGER PRIMARY KEY,
    group_id INTEGER NOT NULL,
    received INTEGER NOT NULL,
    version TEXT NOT NULL,
    -- The report as the app sent it, JSON
    body TEXT NOT NULL,
    retraced TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS crash_samples_group ON crash_samples (group_id, id);

-- Obfuscated traces already retraced: (build, trace key digest) to group, so a trace is retraced once
CREATE TABLE IF NOT EXISTS crash_traces (
    version TEXT NOT NULL,
    key TEXT NOT NULL,
    group_id INTEGER NOT NULL,
    retraced TEXT NOT NULL,
    PRIMARY KEY (version, key)
);

-- Lines read from a build's mapping file, null where the mapping has none, shared by its traces
CREATE TABLE IF NOT EXISTS crash_lines (
    version TEXT NOT NULL,
    key TEXT NOT NULL,
    json TEXT,
    PRIMARY KEY (version, key)
);

-- Each build reports have come from: whether its mapping file is there, and when it was last needed
CREATE TABLE IF NOT EXISTS crash_mappings (
    version TEXT PRIMARY KEY,
    -- present or missing
    state TEXT NOT NULL,
    last_seen INTEGER NOT NULL
);

-- Added after the tables above were created:
-- ALTER TABLE crash_groups ADD COLUMN reopened_at INTEGER;

-- Usage statistics: counts only, never single reports. A device sends at most one heartbeat a day,
-- with nothing that ties it to another; see src/stats.js. Every column of a key is NOT NULL, as
-- SQLite would take NULLs as distinct and split a count.

-- Devices per UTC day and combination of the few values that may be crossed. Counts are REAL, as a
-- sampled heartbeat counts for 1 / the sampling rate
CREATE TABLE IF NOT EXISTS stats_daily (
    day TEXT NOT NULL,
    version TEXT NOT NULL,
    channel TEXT NOT NULL,
    sdk INTEGER NOT NULL,
    brand TEXT NOT NULL,
    rom TEXT NOT NULL,
    form TEXT NOT NULL,
    -- 1 when the app crashed or froze since the device's previous heartbeat
    crashed INTEGER NOT NULL,
    devices REAL NOT NULL DEFAULT 0,
    -- Of those, the devices using the app for the first time that ISO week, and that month
    week_first REAL NOT NULL DEFAULT 0,
    month_first REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (day, version, channel, sdk, brand, rom, form, crashed)
);

-- Devices using the app in a week, by the ISO week they installed it in (its Monday) and the weeks
-- since; sent only in the first 8 weeks
CREATE TABLE IF NOT EXISTS stats_retention (
    install_week TEXT NOT NULL,
    weeks INTEGER NOT NULL,
    devices REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (install_week, weeks)
);

-- Devices per month for values with too many kinds to cross with anything: model, full ROM version
-- and locale, each counted on its own
CREATE TABLE IF NOT EXISTS stats_monthly (
    month TEXT NOT NULL,
    field TEXT NOT NULL,
    value TEXT NOT NULL,
    devices REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (month, field, value)
);
