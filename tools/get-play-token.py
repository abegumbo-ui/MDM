#!/usr/bin/env python3
"""
Run this ONCE, on your own computer -- never on a phone, never paste its output anywhere but
Cloudflare's own secrets page. It logs a dedicated Google account into Google's real Play Store
protocol (the same login Aurora Store's own "Google" account mode uses) and prints a long-lived
master token for it. That token -- not your password -- is what the dashboard stores, and it uses
it to mint a fresh ~50-minute Play Store token every time the agent asks for one, so a small
allow-listed set of apps (Waze, Google Maps, Android Auto, Play services -- whatever those actually
need) can keep updating themselves even while every other install/update stays blocked on the phone.

Before running this:
  1. Make a throwaway Google account used for nothing else but this.
  2. Turn on 2-Step Verification on it, then create an "App Password" for it at
     https://myaccount.google.com/apppasswords -- use that 16-character app password below, not
     the account's regular password. Google's login servers are far more likely to let a scripted
     login like this one through with an app password; a regular password on a brand-new account
     is often blocked outright as "suspicious," with no way for a script to clear that challenge.
  3. pip install gpsoauth

What this never does: it never sends your password (or app password) anywhere but Google's own
https://android.clients.google.com/auth -- the exact same endpoint the real Play Store app on an
Android phone talks to. Nothing in this script, and nothing it prints, ever reaches this repo,
Claude, or anyone else.

After it prints the three values, set them as Cloudflare Worker secrets (not vars -- they must
stay encrypted and out of `wrangler.toml`):
    wrangler secret put PLAY_EMAIL
    wrangler secret put PLAY_ANDROID_ID
    wrangler secret put PLAY_MASTER_TOKEN
(or paste them into the same place in the Cloudflare dashboard you set ADMIN_PASSWORD).
"""

import getpass
import secrets
import sys


def main():
    try:
        import gpsoauth
    except ImportError:
        sys.exit("Missing dependency -- run: pip install gpsoauth")

    email = input("Dedicated Google account email: ").strip()
    app_password = getpass.getpass("App password (16 characters, from myaccount.google.com/apppasswords): ").strip()
    android_id = secrets.token_hex(8)
    print(f"\nGenerated a device ID for this account: {android_id}")
    print("This has to stay the same for every future token refresh -- save it along with the master token below.\n")

    result = gpsoauth.perform_master_login(email, app_password, android_id)
    if "Token" not in result:
        sys.exit(
            "Google did not return a token -- it said:\n"
            f"  {result}\n\n"
            "If this says a captcha or a suspicious-login challenge is required, sign into that\n"
            "account normally in a real browser first (approve the device, confirm any prompts),\n"
            "then try this script again. If it keeps failing, 2-Step Verification + an app password\n"
            "(see the instructions at the top of this file) is the most reliable way through this."
        )

    print("Success. Set these three as Cloudflare Worker secrets:\n")
    print(f"  PLAY_EMAIL={email}")
    print(f"  PLAY_ANDROID_ID={android_id}")
    print(f"  PLAY_MASTER_TOKEN={result['Token']}")
    print("\nThis master token does not expire the way a normal login session would, but it can be")
    print("revoked from the account's own Google security settings at any time -- that's also how")
    print("you'd ever turn this off: revoke it there, not just remove the Cloudflare secrets.")


if __name__ == "__main__":
    main()
