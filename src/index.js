import { amapi, parseServiceAccount } from "./google.js";
import { buildPolicy, isProtected, normalizeConfig, packagesFromDevices } from "./policy.js";
import { loginPage, dashboardPage } from "./ui.js";

const POLICY_ID = "default";
const SESSION_SECONDS = 60 * 60 * 12;

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
const html = (body, status = 200, headers = {}) =>
  new Response(body, { status, headers: { "content-type": "text/html;charset=utf-8", ...headers } });

// ---- auth: one admin password (Worker secret) -> HMAC-signed cookie ----
const enc = new TextEncoder();
async function hmac(secret, msg) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(msg));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function safeEqual(a, b) {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}
async function isAuthed(request, env) {
  const m = /(?:^|;\s*)sess=(\d+)\.([0-9a-f]+)/.exec(request.headers.get("cookie") || "");
  if (!m || Number(m[1]) < Date.now() / 1000) return false;
  return safeEqual(m[2], await hmac(env.ADMIN_PASSWORD, `sess:${m[1]}`));
}
async function sessionCookie(env) {
  const exp = Math.floor(Date.now() / 1000) + SESSION_SECONDS;
  const sig = await hmac(env.ADMIN_PASSWORD, `sess:${exp}`);
  return `sess=${exp}.${sig}; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=${SESSION_SECONDS}`;
}

// ---- state in KV ----
const getJSON = async (env, key, dflt) => (await env.STATE.get(key, "json")) ?? dflt;
const putJSON = (env, key, val) => env.STATE.put(key, JSON.stringify(val));

async function listDevices(env, enterprise) {
  const out = [];
  let pageToken;
  do {
    const r = await amapi(env, "GET", `${enterprise}/devices`, undefined, { pageSize: "100", ...(pageToken && { pageToken }) });
    out.push(...(r.devices || []));
    pageToken = r.nextPageToken;
  } while (pageToken);
  return out;
}

async function applyPolicy(env, enterprise) {
  const config = normalizeConfig(await getJSON(env, "config", null));
  const devices = await listDevices(env, enterprise);
  const policy = buildPolicy(config, packagesFromDevices(devices));
  await amapi(env, "PATCH", `${enterprise}/policies/${POLICY_ID}`, policy);
  return policy;
}

async function api(request, env, url) {
  const path = url.pathname;
  const method = request.method;
  const body = method === "GET" ? null : await request.json().catch(() => ({}));
  const enterprise = await getJSON(env, "enterprise", null);

  if (path === "/api/state" && method === "GET") {
    return json({
      enterprise,
      projectId: parseServiceAccount(env).project_id,
      config: normalizeConfig(await getJSON(env, "config", null)),
    });
  }

  if (path === "/api/enterprise/signup-url" && method === "POST") {
    const r = await amapi(env, "POST", "signupUrls", undefined, {
      projectId: parseServiceAccount(env).project_id,
      callbackUrl: `${url.origin}/enterprise/callback`,
    });
    await putJSON(env, "signupUrlName", r.name);
    return json({ url: r.url });
  }

  if (!enterprise) return json({ error: "Create the enterprise first (Setup step)." }, 409);

  if (path === "/api/devices" && method === "GET") {
    const devices = await listDevices(env, enterprise);
    return json(
      devices.map((d) => ({
        id: d.name.split("/").pop(),
        state: d.state,
        model: d.hardwareInfo?.model,
        manufacturer: d.hardwareInfo?.manufacturer,
        androidVersion: d.softwareInfo?.androidVersion,
        lastSync: d.lastStatusReportTime || d.lastPolicySyncTime,
        policyCompliant: d.policyCompliant,
        apps: (d.applicationReports || []).map((a) => ({
          packageName: a.packageName,
          protected: isProtected(a.packageName),
          label: a.displayName,
          source: a.applicationSource,
          state: a.state,
        })),
      })),
    );
  }

  if (path === "/api/config" && method === "PUT") {
    await putJSON(env, "config", normalizeConfig(body));
    return json({ ok: true });
  }

  if (path === "/api/policy/apply" && method === "POST") {
    const policy = await applyPolicy(env, enterprise);
    return json({ ok: true, applied: policy.applications.length });
  }

  if (path === "/api/enrollment" && method === "POST") {
    // Make sure the policy exists before a device tries to enroll with it.
    await applyPolicy(env, enterprise);
    const t = await amapi(env, "POST", `${enterprise}/enrollmentTokens`, {
      policyName: `${enterprise}/policies/${POLICY_ID}`,
      duration: "3600s",
      oneTimeOnly: true,
    });
    return json({ qrCode: t.qrCode, token: t.value, expires: t.expirationTimestamp });
  }

  const cmd = /^\/api\/devices\/([\w-]+)\/(lock|reboot|wipe)$/.exec(path);
  if (cmd && method === "POST") {
    const [, id, action] = cmd;
    if (action === "wipe") {
      await amapi(env, "DELETE", `${enterprise}/devices/${id}`);
    } else {
      await amapi(env, "POST", `${enterprise}/devices/${id}:issueCommand`, {
        type: action === "lock" ? "LOCK" : "REBOOT",
      });
    }
    return json({ ok: true });
  }

  return json({ error: "Not found" }, 404);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!env.ADMIN_PASSWORD) return html("ADMIN_PASSWORD secret is not set. See SETUP.md.", 500);

    if (url.pathname === "/login" && request.method === "POST") {
      const form = await request.formData();
      const ok = safeEqual(String(form.get("password") || ""), env.ADMIN_PASSWORD);
      if (!ok) return html(loginPage("Wrong password."), 401);
      return new Response(null, { status: 303, headers: { location: "/", "set-cookie": await sessionCookie(env) } });
    }
    if (url.pathname === "/logout") {
      return new Response(null, { status: 303, headers: { location: "/", "set-cookie": "sess=; Max-Age=0; Path=/" } });
    }

    if (!(await isAuthed(request, env))) {
      return url.pathname.startsWith("/api/") ? json({ error: "Unauthorized" }, 401) : html(loginPage());
    }

    try {
      if (url.pathname === "/enterprise/callback") {
        const token = url.searchParams.get("enterpriseToken");
        const signupUrlName = await getJSON(env, "signupUrlName", null);
        if (!token || !signupUrlName) return html("Missing enterprise token. Start setup again from the dashboard.", 400);
        const ent = await amapi(
          env,
          "POST",
          "enterprises",
          { enterpriseDisplayName: "Family MDM" },
          { projectId: parseServiceAccount(env).project_id, signupUrlName, enterpriseToken: token },
        );
        await putJSON(env, "enterprise", ent.name);
        return new Response(null, { status: 303, headers: { location: "/" } });
      }
      if (url.pathname.startsWith("/api/")) return await api(request, env, url);
      if (url.pathname === "/") return html(dashboardPage());
      return html("Not found", 404);
    } catch (e) {
      const msg = e.message || String(e);
      return url.pathname.startsWith("/api/") ? json({ error: msg }, e.status || 500) : html(`Error: ${msg}`, 500);
    }
  },
};
