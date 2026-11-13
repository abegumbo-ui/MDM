# Family MDM Phone Setup (phone-to-phone, over a USB cable)

An Android app that does what `windows/FamilyMdmSetup` does, with a **phone in place of the computer**: this phone
(the one with the app) plays the computer, the OTHER phone gets the Family MDM app and is made to be managed by it.

## Use
1. Other phone: Settings > About phone > tap *Build number* 7 times, then Developer options > **USB debugging** on.
2. Join the two phones: USB cable + an **OTG adapter** on THIS phone (this phone must be able to act as a USB host).
3. This phone needs the internet (it downloads the newest Family MDM app and checks it against the published checksum).
4. Dashboard: Codes > Enrollment code > *Generate new code*. Type it into this app and press **Set up the other phone**.
5. Tap **Allow** on the other phone when it asks about USB debugging (tick *Always allow*).

*Only check it* shows what would be done and changes nothing. *Put things back* switches on whatever an interrupted run left off.

## What it does
Same steps as the Windows program: look at the phone (refuses when another app already manages it or when it has more than
one user); install the app by streaming it over the cable (`cmd package install -S`, Android 7 or newer on the other phone);
switch off only the account services of accounts that exist, written down first; `dpm set-device-owner`; switch the account
services straight back on; start the app with the code. On Android 14 and newer the first try can fail until the other phone is
restarted: the app then leaves the services off, asks you to restart the other phone, and finishes (and switches everything
back on) the next time you press the button.

## How it works
- `UsbTransport` finds the ADB interface of the other phone (class 0xFF / subclass 0x42 / protocol 1) and wraps its two bulk
  endpoints as streams. A message's 24-byte header and its data go as two transfers, as the other phone expects.
- `com.cgutman.adblib` is the ADB protocol library by Cameron Gutman, BSD-3-Clause (`LICENSE-AdbLib.txt`), with changes (marked 'Changed from the original
  library' in the code): a factory so it can run on any pair of streams instead of a TCP socket, and three fixes for quick commands.
- `OtgSetup` holds the steps (a port of the Windows program), `Parse` reads the other phone's output (the same patterns that the
  Windows program's self-test checks against output copied from a real phone).

## Tested
The ADB protocol layer is checked on every build against a pretend phone (src/test/.../AdbLibCheck.java). That check found two flaws in the original library, now fixed in the copy here: a quick command was reported as 'rejected' because it had already closed, and output that arrived just before the close was thrown away.

The USB side (UsbTransport) and the whole thing on real phones are not tested. It compiles. **It has not been tried on two phones**: that needs a second phone and an OTG cable, and nothing here had them.
The ADB-over-USB framing is the part most likely to need a fix on first use; the log on screen says what failed.
