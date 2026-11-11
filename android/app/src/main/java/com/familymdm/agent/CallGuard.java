package com.familymdm.agent;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;

/**
 * Incoming calls: Android asks this service about each call once the Agent holds the call-screening
 * role (see Calls). Calls from numbers that are not allowed are rejected without ringing; they still
 * show in the phone's call log as blocked. Hidden numbers are never passed here by Android.
 */
public class CallGuard extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details details) {
        boolean ok = true;
        String number = null;
        try {
            boolean incoming = Build.VERSION.SDK_INT < 29 || details.getCallDirection() == Call.Details.DIRECTION_INCOMING;
            if (incoming) {
                number = details.getHandle() == null ? null : details.getHandle().getSchemeSpecificPart();
                ok = Calls.allowed(this, number, true);
            }
        } catch (Exception e) {
            ok = true; // never block a call because of our own mistake
        }
        CallResponse.Builder b = new CallResponse.Builder();
        if (!ok) b.setDisallowCall(true).setRejectCall(true).setSkipNotification(true);
        respondToCall(details, b.build());
        if (!ok) Calls.noteBlocked(this, number, true);
    }
}
