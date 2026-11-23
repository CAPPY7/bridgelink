package app.bridgelink.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import app.bridgelink.android.protocol.CallRules;

/** Hears the phone ring only while call alerts are on and the link is up. */
public final class CallWatch {
    private final Context context;
    private TelephonyCallback modern;
    private PhoneStateListener legacy;
    private boolean registered;

    public CallWatch(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void update(boolean featureOn, boolean linked) {
        if (CallRules.listen(featureOn, linked)) start();
        else stop();
    }

    public void stop() {
        if (!registered) return;
        TelephonyManager manager = manager();
        if (manager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 31 && modern != null) manager.unregisterTelephonyCallback(modern);
                else if (legacy != null) manager.listen(legacy, PhoneStateListener.LISTEN_NONE);
            } catch (SecurityException ignored) {
            }
        }
        registered = false;
        modern = null;
        legacy = null;
    }

    private void start() {
        if (registered) return;
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        TelephonyManager manager = manager();
        if (manager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                modern = new TelephonyCallback() {};
                manager.registerTelephonyCallback(context.getMainExecutor(), modern);
            } else {
                legacy = new PhoneStateListener();
                manager.listen(legacy, PhoneStateListener.LISTEN_CALL_STATE);
            }
            registered = true;
        } catch (SecurityException ignored) {
            registered = false;
        }
    }

    private TelephonyManager manager() {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }
}
