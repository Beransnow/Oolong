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
    -- open, fixed or ignored; a fixed problem seen again is open again
    status TEXT NOT NULL DEFAULT 'open',
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
