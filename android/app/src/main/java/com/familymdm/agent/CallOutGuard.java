package com.familymdm.agent;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import android.widget.Toast;

/**
 * Outgoing calls: once the Agent holds the call-redirection role (see Calls), Android asks this
 * service before placing each call. A number that is not allowed is cancelled. Android never asks
 * about emergency calls, so those always go through.
 */
public class CallOutGuard extends CallRedirectionService {
    @Override
    public void onPlaceCall(Uri handle, PhoneAccountHandle initialPhoneAccount, boolean allowInteractiveResponse) {
        String number = handle == null ? null : handle.getSchemeSpecificPart();
        boolean ok;
        try {
            ok = Calls.allowed(this, number, false);
        } catch (Exception e) {
            ok = true;
        }
        if (ok) {
            placeCallUnmodified();
            return;
        }
        cancelCall();
        Calls.noteBlocked(this, number, false);
        final android.content.Context app = getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, "Calls to this number are not allowed on this phone.", Toast.LENGTH_LONG).show());
    }
}
