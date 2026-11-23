package app.bridgelink.android;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;

import java.util.Locale;

/** Decline a ringing call from the notification, or end it if the user allowed that. */
public final class CallDecline {
    private CallDecline() {}

    public static boolean end(Context context, String key) {
        NotifyMirror mirror = NotifyMirror.current();
        if (mirror != null && mirror.decline(key)) return true;
        if (Build.VERSION.SDK_INT < 28) return false;
        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            return telecom != null && telecom.endCall();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    static boolean isDecline(Notification.Action action) {
        if (action == null || action.title == null || action.actionIntent == null) return false;
        String title = action.title.toString().toLowerCase(Locale.US);
        return title.contains("decline") || title.contains("reject") || title.contains("hang")
                || title.contains("end call") || title.contains("dismiss");
    }
}
