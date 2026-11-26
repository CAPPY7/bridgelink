package app.bridgelink.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

import app.bridgelink.android.protocol.ServiceStart;

/** Holds the screen-share permission while the Mac window is open. */
public final class MirrorService extends Service {
    public static final String ACTION_STOP = "app.bridgelink.android.MIRROR_STOP";
    private MirrorCast cast;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ServiceStart.stopFirst(intent == null ? null : intent.getAction(), ACTION_STOP)) {
            stopQuiet();
            return START_NOT_STICKY;
        }
        Intent data = intent == null ? null : dataOf(intent);
        if (!ServiceStart.hasPayload(data != null)) {
            LinkService.postStatus("Screen share was not allowed.");
            stopQuiet();
            return START_NOT_STICKY;
        }
        try {
            Notification note = note();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(71, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(71, note);
            }
        } catch (SecurityException error) {
            LinkService.postStatus("Screen share was not allowed.");
            stopQuiet();
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra("code", 0);
        String quality = intent.getStringExtra("quality");
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            LinkService.postStatus("Screen share was not allowed.");
            stop();
            return START_NOT_STICKY;
        }
        MediaProjection projection = manager.getMediaProjection(code, data);
        if (projection == null) {
            LinkService.postStatus("Screen share was not allowed.");
            stop();
            return START_NOT_STICKY;
        }
        if (cast != null) cast.stop();
        cast = new MirrorCast();
        cast.start(this, projection, quality);
        LinkService.postStatus("Sharing the screen with the Mac.");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void stopQuiet() {
        if (cast != null) cast.stop();
        cast = null;
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignored) {
        }
        stopSelf();
    }

    private void stop() {
        if (cast != null) cast.stop();
        cast = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @SuppressWarnings("deprecation")
    private static Intent dataOf(Intent intent) {
        return intent.getParcelableExtra("data");
    }

    private Notification note() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel("mirror") == null) {
            manager.createNotificationChannel(new NotificationChannel("mirror", "Screen mirror", NotificationManager.IMPORTANCE_LOW));
        }
        return new Notification.Builder(this, "mirror")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("BridgeLink")
                .setContentText("Sharing the screen with the Mac")
                .build();
    }
}
