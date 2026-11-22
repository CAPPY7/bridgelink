package app.bridgelink.android;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.NoticeGate;
import app.bridgelink.android.protocol.StageRules;

/** Mirrors notifications only after the user turns the feature on. Text is not logged. */
public class NotifyMirror extends NotificationListenerService {
    private static volatile NotifyMirror current;
    private static final Set<String> SELF = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, String> seenNotes = new ConcurrentHashMap<>();

    @Override
    public void onListenerConnected() {
        current = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (current == this) current = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notice) {
        if (notice == null || !Features.enabled(Prefs.open(this).getString("flags", ""), "notify", false)) return;
        Notification notification = notice.getNotification();
        if (notification == null) return;
        String pkg = notice.getPackageName() == null ? "" : notice.getPackageName();
        if (getPackageName().equals(pkg)) return;
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean silent = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        boolean allowed = !StageRules.appBlocked(Prefs.open(this).getString("notifyOff", ""), pkg);
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String shown = title == null ? "" : title.toString();
        String body = text == null ? "" : text.toString();
        String key = notice.getKey() == null ? "" : notice.getKey();
        boolean progress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0;
        String stamp = NoticeGate.stamp(key, shown, body);
        boolean duplicate = NoticeGate.skipUpdate(progress, seenNotes.get(key), stamp);
        if (!Features.shouldMirror(ongoing, silent, allowed, duplicate)) return;
        seenNotes.put(key, stamp);
        Intent send = new Intent(this, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "notify");
        send.putExtra(LinkService.EXTRA_BODY, shown);
        send.putExtra("app", pkg);
        send.putExtra("detail", body);
        send.putExtra("key", key);
        send.putExtra("reply", canReply(notification) ? "1" : "0");
        String code = Features.otp(shown + "\n" + body);
        if (code != null && Features.enabled(Prefs.open(this).getString("flags", ""), "otp", false)) {
            send.putExtra("otp", "1");
        }
        LinkService.post(this, send);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notice) {
        if (notice == null || notice.getKey() == null) return;
        seenNotes.remove(notice.getKey());
        if (SELF.remove(notice.getKey())) return;
        if (!Features.enabled(Prefs.open(this).getString("flags", ""), "notify", false)) return;
        Intent send = new Intent(this, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "notify_dismiss");
        send.putExtra("key", notice.getKey());
        LinkService.post(this, send);
    }

    public static void dismiss(String key) {
        NotifyMirror live = current;
        if (live == null || key == null || key.isEmpty()) return;
        SELF.add(key);
        try {
            live.cancelNotification(key);
        } catch (Exception ignored) {
            SELF.remove(key);
        }
    }

    public static boolean reply(String key, String text) {
        NotifyMirror live = current;
        if (live == null || key == null || key.isEmpty() || text == null || text.isEmpty()) return false;
        StatusBarNotification[] all;
        try {
            all = live.getActiveNotifications();
        } catch (Exception error) {
            return false;
        }
        if (all == null) return false;
        for (StatusBarNotification notice : all) {
            if (!key.equals(notice.getKey()) || notice.getNotification() == null) continue;
            Notification.Action[] actions = notice.getNotification().actions;
            if (actions == null) return false;
            for (Notification.Action action : actions) {
                RemoteInput[] inputs = action.getRemoteInputs();
                if (inputs == null || inputs.length == 0 || action.actionIntent == null) continue;
                Intent fill = new Intent();
                Bundle bundle = new Bundle();
                bundle.putString(inputs[0].getResultKey(), text);
                RemoteInput.addResultsToIntent(inputs, fill, bundle);
                try {
                    action.actionIntent.send(live, 0, fill);
                    return true;
                } catch (PendingIntent.CanceledException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean canReply(Notification notification) {
        if (notification.actions == null) return false;
        for (Notification.Action action : notification.actions) {
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) return true;
        }
        return false;
    }
}
