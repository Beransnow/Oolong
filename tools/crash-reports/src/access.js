/**
 * Lets a console request through only with a valid Cloudflare Access token: Access sits in front
 * of the console, and this checks again in the Worker, so that a mistake in Access's setup leaves
 * the console closed rather than open. ACCESS_TEAM (the team's *.cloudflareaccess.com host) and
 * ACCESS_AUD (the application's audience tag) come from the Worker's settings; CONSOLE_DEV, set
 * only in .dev.vars, lets `wrangler dev` through.
 */

let certs = null;
let certsFetched = 0;

/** The signed-in person's email, or null when the request may not see the console. */
export async function accessEmail(request, env) {
    if (env.CONSOLE_DEV === "1") return "dev@localhost";
    const token = request.headers.get("Cf-Access-Jwt-Assertion");
    if (!token || !env.ACCESS_TEAM || !env.ACCESS_AUD) return null;
    try {
        const [head, body, signature] = token.split(".");
        const header = JSON.parse(decode(head));
        const claims = JSON.parse(decode(body));
        if (header.alg !== "RS256") return null;
        const key = (await keys(env.ACCESS_TEAM)).find((k) => k.kid === header.kid);
        if (!key) return null;
        const verified = await crypto.subtle.verify(
            "RSASSA-PKCS1-v1_5",
            await crypto.subtle.importKey("jwk", key, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]),
            bytes(signature),
            new TextEncoder().encode(`${head}.${body}`),
        );
        const audience = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
        const now = Date.now() / 1000;
        if (!verified || !audience.includes(env.ACCESS_AUD) || claims.iss !== `https://${env.ACCESS_TEAM}` || claims.exp < now) return null;
        return claims.email ?? null;
    } catch {
        return null;
    }
}

/** The team's signing keys, fetched at most every hour. */
async function keys(team) {
    if (!certs || Date.now() - certsFetched > 3_600_000) {
        const response = await fetch(`https://${team}/cdn-cgi/access/certs`);
        certs = (await response.json()).keys;
        certsFetched = Date.now();
    }
    return certs;
}

function decode(part) {
    return new TextDecoder().decode(bytes(part));
}

function bytes(part) {
    const base64 = part.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}
