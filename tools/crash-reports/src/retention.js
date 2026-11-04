/**
 * Which builds' mapping files to drop. Every build uploads one; a released build also leaves a
 * marker. The latest RELEASES released builds keep theirs; any other build's goes once it is older
 * than DAYS and no report has come from it in that time.
 */

export const RELEASES = 20;
export const DAYS = 14;

/**
 * The versions whose files to delete. [files] and [released] are { version, uploaded } (uploaded in
 * ms), from the bucket's mapping files and release markers; [reported] the versions reports came
 * from in the last DAYS; [now] in ms.
 */
export function expiredVersions(files, released, reported, now) {
    const kept = new Set(
        [...released]
            .sort((a, b) => b.uploaded - a.uploaded)
            .slice(0, RELEASES)
            .map((r) => r.version),
    );
    const cutoff = now - DAYS * 86_400_000;
    return files
        .filter((f) => !kept.has(f.version) && f.uploaded < cutoff && !reported.has(f.version))
        .map((f) => f.version);
}
