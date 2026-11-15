package app.bridgelink.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the phone camera on while the Mac window is open. */
public final class CamService extends Service {
    public static final String ACTION_STOP = "app.bridgelink.android.CAM_STOP";
    private CamCast cast;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean mic = intent != null && intent.getBooleanExtra("mic", false);
        if (Build.VERSION.SDK_INT >= 30) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            if (mic && Build.VERSION.SDK_INT >= 34) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(72, note(), type);
        } else {
            startForeground(72, note());
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            halt();
            return START_NOT_STICKY;
        }
        if (cast != null) cast.stop();
        cast = new CamCast();
        cast.start(
                this,
                intent == null ? "back" : intent.getStringExtra("facing"),
                intent == null ? 1 : intent.getIntExtra("zoom", 1),
                intent != null && intent.getBooleanExtra("flash", false),
                mic
        );
        LinkService.postStatus("The Mac can see the camera.");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        halt();
        super.onDestroy();
    }

    private void halt() {
        if (cast != null) cast.stop();
        cast = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification note() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel("camera") == null) {
            manager.createNotificationChannel(new NotificationChannel("camera", "Phone camera", NotificationManager.IMPORTANCE_LOW));
        }
        return new Notification.Builder(this, "camera")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("BridgeLink")
                .setContentText("The Mac is using the camera")
                .build();
    }
}
