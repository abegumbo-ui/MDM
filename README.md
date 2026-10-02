# MDM Dashboard

A small, free, self-hosted device manager for locking down an Android phone, with no Google
quota and no server to run.

- **Dashboard** (`src/`): a Cloudflare Worker (free tier) with a password login. Choose which apps
  are allowed, hide the rest, toggle restrictions, and send commands (lock, reboot, install,
  uninstall, wipe, release).
- **Agent** (`android/`): an Android app that becomes the phone's *device owner* and applies the
  dashboard's settings with Android's own device-policy APIs. The same mechanism Google's
  Android Device Policy uses.

See [SETUP.md](SETUP.md). Run the dashboard tests with `npm test`.
