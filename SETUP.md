# Setup (about 30 minutes, $0)

## 1. Google Cloud (one time)
1. Go to https://console.cloud.google.com and create a project (e.g. `family-mdm`). Note the **project ID**.
2. **APIs & Services → Library** → search **Android Management API** → **Enable**.
3. **IAM & Admin → Service Accounts → Create service account** (name it `mdm`). No roles needed.
4. Open the account → **Keys → Add key → Create new key → JSON**. A `.json` file downloads. Keep it private; never commit it.

## 2. Cloudflare (one time)
1. Sign up free at https://dash.cloudflare.com.
2. On your computer (Node 18+): `npm install` then `npx wrangler login`.
3. `npx wrangler kv namespace create STATE` → copy the `id` into `wrangler.toml`.
4. Secrets:
   ```
   npx wrangler secret put ADMIN_PASSWORD            # choose a LONG password
   npx wrangler secret put GOOGLE_SERVICE_ACCOUNT    # paste the entire JSON key file contents
   ```
5. `npm run deploy` → prints your dashboard URL (`https://mdm-dashboard.<you>.workers.dev`).

## 3. Connect and enroll
1. Open the URL, sign in, click **Start setup** and finish Google's short form. You land back on the dashboard.
2. Click **Generate QR code**.
3. On a factory-reset phone: at the first "Welcome" screen tap the same spot 6 times, scan the QR, and let it finish. Don't sign into any Google account.
4. On the dashboard, **Refresh** until the phone's apps appear, choose what to allow, then **Save & push**.

## Tips
- Start with **Block unlisted** off, confirm the phone works, then turn it on.
- Add Google Maps etc. from **Quick add**; for other apps enter the Play Store package name (the `id=` in the app's URL).
- Content filtering (e.g. a locked filtering DNS) goes in **Advanced policy overrides**.
- Test on a spare device first: a wrong block can make a phone awkward to use, though protected system components are kept on by default.
