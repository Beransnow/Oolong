# Crash reports

Turns obfuscated stack traces from release builds back into the original classes, methods and
lines, in a Worker within the free plan's 10 ms of CPU per invocation. The design is in
`docs/crash-reporting.md`; this directory so far holds the retrace and the mapping shards it reads.

- `src/mapping.js` reads an R8 mapping file and lays it out as shards.
- `src/retrace.js` retraces a trace from looked-up records.
- `src/shards.js` looks records up in the shards.
- `scripts/shard.mjs` writes a build's shards.

## Shards

A build's mapping is kept under `mapping/<versionName>/` in R2, `versionName` exactly as the app
reports it:

| File | Holds |
|---|---|
| `meta.json` | `{ version, shards, classes, mapping }`: the shard count and the mapping's SHA-256 |
| `<n>.gz` | Shard `n`, gzipped lines `key<TAB>json` |

A line's key is an obfuscated class name, holding `{ n, f?, y? }` for the class, or
`<class> <method>`, holding `{ n, f?, y?, g }` with that method's groups. A line goes to shard
`fnv1a(key) % shards`, FNV-1a over the key's UTF-8 bytes; a lookup decompresses that shard, finds
`\n<key>\t` and parses that line alone. The fields are described at the top of `src/mapping.js`.

At 4096 shards a mapping of 20,000 classes makes 13 MB, a file per shard of at most 20 KB, and a
trace costs about 2 ms of CPU (4 ms at the 95th percentile) over some 8 files.

```sh
node scripts/shard.mjs app/build/outputs/mapping/release/mapping.txt 1.0.0 out 4096
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
node test/compare.mjs app/build/outputs/mapping/release/mapping.txt 2000
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
