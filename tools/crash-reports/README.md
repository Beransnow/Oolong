# Crash reports

A Worker that takes crash reports the app sends, with the user's consent, and files them under
problems. Release builds are obfuscated, so each report is first turned back into the original
classes, methods and lines with its build's mapping, within the free plan's 10 ms of CPU per
invocation; reports of one problem are grouped across builds, and the owner hears of new problems
on Telegram.

- `src/index.js` is the Worker: `POST /crash`, and the scheduled runs that file reports.
- `src/group.js` says which reports are one problem.
- `src/retrace.js` retraces a trace from looked-up records.
- `src/mapping.js` reads an R8 mapping file and lays it out as shards.
- `src/shards.js` reads records from a build's mapping file, at once or over several runs.
- `scripts/shard.mjs` writes a build's mapping file.

Commands below run from this directory.

## Reports

`POST /crash` takes JSON of at most 64 KB:

| Field | |
|---|---|
| `version` | The build's versionName, with `+<commit>` on CI builds |
| `kind` | `crash`, `anr` or `native` |
| `trace` | The obfuscated stack trace; for an ANR, the main thread |
| `install` | A random id the app makes once, to count the installs a problem reaches |
| `time`, `sdk`, `device`, `rom`, `page`, `thread` | Optional |

It answers 202 once the report is kept, 400 for a malformed one, 429 past 20 reports from one
install in a day, and 503 while 5,000 wait to be filed.

Every minute the scheduled run files waiting reports. A trace seen before in the same build goes
straight to its problem. A new one needs lines from its build's mapping file: they are read 24
shards per run and kept in `crash_lines`, where later traces of the build find them, and once all
are there the trace is retraced and grouped. A build without a mapping file (a debug build, or one
whose file has gone) is grouped by its obfuscated names.

A problem is its kind, the exception at the root of the trace and the first three frames of the
app's own code, without line numbers. A problem marked `fixed` opens again when it shows up in a
build it hadn't come from before; builds that had it before the fix still have it.

## Setup

The app posts its reports to `/crash` on the update host. That host serves the update bucket; the
Worker runs on a route for this one path in front of it, and every other path stays with the bucket.
The host is kept out of the repository, so the route is passed when deploying: set `UPDATE_HOST` to
the host name and pass the route with every deploy.

```sh
npx wrangler d1 create oolong-crash            # then fill its id into wrangler.jsonc
npx wrangler r2 bucket create oolong-mappings  # private: no public access, no custom domain
npx wrangler d1 execute oolong-crash --remote --file schema.sql
npx wrangler deploy --route "$UPDATE_HOST/crash"
npx wrangler secret put BOT_TOKEN
npx wrangler secret put OWNER_ID
```

## Reading

```sh
npx wrangler d1 execute oolong-crash --remote --command "SELECT id, title, reports, last_version FROM crash_groups WHERE status = 'open' ORDER BY last_seen DESC"
npx wrangler d1 execute oolong-crash --remote --command "SELECT retraced FROM crash_samples WHERE group_id = … ORDER BY id DESC LIMIT 1"
npx wrangler d1 execute oolong-crash --remote --command "UPDATE crash_groups SET status = 'fixed', fixed_by = '<commit>', note = '…' WHERE id = …"
```

To try it locally, put a mapping file in the local bucket, run `npx wrangler dev --local
--test-scheduled`, post reports and call `/__scheduled?cron=*+*+*+*+*` for each run.

## Mapping file

A build's mapping becomes one file, kept in the private bucket as `mapping/<versionName>.bin`, `versionName`
exactly as the app reports it (with `+<commit>` on CI builds). A lookup reads it by range: the
header once, then each shard it needs.

| Bytes | Holds |
|---|---|
| 0–3 | `OLM1` |
| 4–7 | Shard count `n`, u32 little-endian |
| 8 – 8 + 4(n + 1) | Where each shard starts and the last ends, u32 little-endian |
| Then | The shards, each gzipped on its own |

A shard is lines `key<TAB>json`. A line's key is an obfuscated class name, holding `{ n, f?, y? }`
for the class, or `<class> <method>`, holding `{ n, f?, y?, g }` with that method's groups. A line
goes to shard `fnv1a(key) % n`, FNV-1a over the key's UTF-8 bytes; a lookup decompresses that
shard, finds `\n<key>\t` and parses that line alone. The fields are described at the top of
`src/mapping.js`.

With 4096 shards a mapping of 20,000 classes makes a 13 MB file with shards of at most 20 KB, and a
trace costs about 1.6 ms of CPU (3.6 ms at the 95th percentile) over some 9 reads.

```sh
node scripts/shard.mjs ../../app/build/outputs/mapping/release/mapping.txt mapping.bin
```

## Tests

```sh
node --test test/*.test.mjs
```

`test/fixture/` is the part of a release mapping a sample trace touches, with what R8's own retrace
makes of it. `test/compare.mjs` checks against R8 at scale: it builds traces from a whole mapping,
reaching every kind of entry in it, retraces them both ways and reports the differences.

```sh
JAVA=/usr/lib/jvm/java-17-openjdk-amd64/bin/java \
R8_JAR=$(ls ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/builder/<agp>/*/builder-<agp>.jar) \
node test/compare.mjs ../../app/build/outputs/mapping/release/mapping.txt 2000
```

Use the R8 that wrote the mapping (`# compiler_version` at its top); AGP's builder jar carries it.

Frames with a line number come out as R8's do, with these exceptions, where this gives more or is
more exact:

- An outline's call site whose code maps to the method's own lines: R8 shows no frame; this shows
  the line.
- An outline's call site in one of several overloads sharing an obfuscated name: R8 offers or picks
  the lines of another overload; this takes the call site's own.

For a frame without a line number, the candidates listed may differ from R8's: R8 sometimes lists
fewer, or none. In about 6,000 generated traces, 5–6% differ in one of these ways and no other.
