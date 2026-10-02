// Minimal Google API client for Cloudflare Workers: service-account auth + Android Management API.

const SCOPE = "https://www.googleapis.com/auth/androidmanagement";
const AMAPI = "https://androidmanagement.googleapis.com/v1";

const b64url = (buf) =>
  btoa(String.fromCharCode(...new Uint8Array(buf)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
const enc = (s) => new TextEncoder().encode(s);

async function signJwt(sa) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(enc(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = b64url(
    enc(
      JSON.stringify({
        iss: sa.client_email,
        scope: SCOPE,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    ),
  );
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc(`${header}.${claim}`));
  return `${header}.${claim}.${b64url(sig)}`;
}

let cached = { token: null, exp: 0 };

export function parseServiceAccount(env) {
  if (!env.GOOGLE_SERVICE_ACCOUNT) throw new Error("GOOGLE_SERVICE_ACCOUNT secret is not set");
  return JSON.parse(env.GOOGLE_SERVICE_ACCOUNT);
}

async function accessToken(env) {
  if (cached.token && Date.now() < cached.exp) return cached.token;
  const sa = parseServiceAccount(env);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: await signJwt(sa),
    }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(`Google auth failed: ${data.error_description || data.error}`);
  cached = { token: data.access_token, exp: Date.now() + (data.expires_in - 120) * 1000 };
  return cached.token;
}

export async function amapi(env, method, path, body, query) {
  const url = new URL(`${AMAPI}/${path}`);
  for (const [k, v] of Object.entries(query || {})) url.searchParams.set(k, v);
  const res = await fetch(url, {
    method,
    headers: {
      authorization: `Bearer ${await accessToken(env)}`,
      "content-type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) {
    const err = new Error(data.error?.message || `AMAPI ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return data;
}
