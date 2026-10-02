# MDM Dashboard

A tiny, free, self-hosted MDM for locking down an Android phone/tablet, built on Google's
Android Management API and hosted on Cloudflare Workers' free tier.

- Allow / force-install / block any app, system apps included
- Push apps remotely; they auto-update
- Prevent uninstalling, factory reset, safe mode, adding accounts
- Enroll a device by scanning a QR code on a factory-fresh phone
- Lock / reboot / wipe from the dashboard

See [SETUP.md](SETUP.md) to deploy. Run tests with `npm test`.
