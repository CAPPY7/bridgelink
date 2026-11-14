package app.bridgelink.android;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;

import app.bridgelink.android.protocol.Features;

/** Mirrors notifications only after the user turns the feature on. Text is not logged. */
public class NotifyMirror extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification notice) {
        if (notice == null || !Features.enabled(Prefs.open(this).getString("flags", ""), "notify", false)) return;
        Notification notification = notice.getNotification();
        if (notification == null) return;
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean silent = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (!Features.shouldMirror(ongoing, silent, true, false)) return;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String shown = (title == null ? "" : title.toString());
        String body = text == null ? "" : text.toString();
        Intent send = new Intent(this, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "notify");
        send.putExtra(LinkService.EXTRA_BODY, shown);
        send.putExtra("app", notice.getPackageName() == null ? "" : notice.getPackageName());
        send.putExtra("detail", body);
        String code = Features.otp(shown + "\n" + body);
        if (code != null && Features.enabled(Prefs.open(this).getString("flags", ""), "otp", false)) {
            send.putExtra("otp", "1");
        }
        ContextCompat.startForegroundService(this, send);
    }
}
