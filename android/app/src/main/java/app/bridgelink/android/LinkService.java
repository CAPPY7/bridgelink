package app.bridgelink.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LinkService extends Service {
    public static final String ACTION_CONNECT = "app.bridgelink.android.CONNECT";
    public static final String ACTION_SEND = "app.bridgelink.android.SEND";
    public static final String ACTION_STOP_RING = "app.bridgelink.android.STOP_RING";
    public static final String EVENT = "app.bridgelink.android.EVENT";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_PIN = "pin";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_LINE = "line";

    private static final String CHANNEL = "link";
    private static final int NOTE_ID = 1;
    private static final int RING_ID = 2;

    private final IBinder binder = new LocalBinder();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Object writeLock = new Object();
    private final AtomicInteger generation = new AtomicInteger();

    private Socket socket;
    private BufferedWriter writer;
    private MediaPlayer player;
    private volatile boolean paired;

    public class LocalBinder extends Binder {
        public LinkService getService() {
            return LinkService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "BridgeLink", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Phone to Mac link");
        manager().createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTE_ID, note("BridgeLink", paired ? "Paired with the Mac" : "Not connected"));
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            connect(
                    intent.getStringExtra(EXTRA_HOST),
                    intent.getIntExtra(EXTRA_PORT, 8787),
                    intent.getStringExtra(EXTRA_PIN)
            );
        } else if (ACTION_SEND.equals(action)) {
            send(intent.getStringExtra(EXTRA_KIND), intent.getStringExtra(EXTRA_BODY));
        } else if (ACTION_STOP_RING.equals(action)) {
            stopRing();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRing();
        closeSocket();
        io.shutdownNow();
        super.onDestroy();
    }

    private void connect(String host, int port, String pin) {
        final String address = host == null ? "" : host.trim();
        final String code = pin == null ? "" : pin.trim();
        final int gen = generation.incrementAndGet();
        closeSocket();
        io.execute(() -> {
            if (generation.get() != gen) return;
            paired = false;
            if (address.isEmpty() || code.length() != 6) {
                publish("Enter the Mac address and the 6-digit PIN", null);
                return;
            }
            publish("Connecting to " + address + "…", null);
            Socket next = new Socket();
            try {
                next.connect(new InetSocketAddress(address, port), 8000);
                next.setTcpNoDelay(true);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(next.getOutputStream(), StandardCharsets.UTF_8));
                synchronized (writeLock) {
                    if (generation.get() != gen) {
                        next.close();
                        return;
                    }
                    socket = next;
                    writer = out;
                }
                JSONObject hello = new JSONObject();
                hello.put("v", 1);
                hello.put("type", "hello");
                hello.put("from", "android");
                hello.put("name", Build.MODEL);
                hello.put("pin", code);
                writeJson(hello);
                BufferedReader reader = new BufferedReader(new InputStreamReader(next.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (generation.get() == gen && (line = reader.readLine()) != null) {
                    onLine(line);
                }
            } catch (Exception error) {
                if (generation.get() == gen) {
                    publish("Link dropped: " + error.getMessage(), null);
                }
            } finally {
                if (generation.get() == gen) {
                    paired = false;
                    closeSocket();
                    publish("Disconnected", null);
                }
            }
        });
    }

    private void onLine(String line) {
        try {
            JSONObject message = new JSONObject(line);
            String type = message.optString("type", "");
            String body = message.optString("body", "");
            switch (type) {
                case "paired" -> {
                    paired = true;
                    publish("Paired with the Mac", "Mac accepted the PIN");
                }
                case "error" -> publish(body.isEmpty() ? "Mac rejected the link" : body, null);
                case "text" -> {
                    publish("Note from the Mac", body);
                    alert("Note from the Mac", body);
                }
                case "clipboard" -> {
                    copy(body);
                    publish("Clipboard updated from the Mac", body);
                    alert("Clipboard from the Mac", body);
                }
                case "ring" -> {
                    publish("Mac is ringing this phone", null);
                    startRing();
                }
                default -> publish("Mac: " + type, body);
            }
        } catch (Exception error) {
            publish("Bad message from the Mac", line);
        }
    }

    public void send(String kind, String body) {
        if (!paired) {
            publish("Connect and pair before sending", null);
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("v", 1);
            message.put("type", kind == null ? "text" : kind);
            message.put("from", "android");
            message.put("body", body == null ? "" : body);
            writeJson(message);
            publish("Sent", body == null || body.isEmpty() ? kind : body);
        } catch (Exception error) {
            publish("Send failed: " + error.getMessage(), null);
        }
    }

    private void writeJson(JSONObject obj) throws Exception {
        synchronized (writeLock) {
            if (writer == null) throw new IllegalStateException("not connected");
            writer.write(obj.toString());
            writer.write('\n');
            writer.flush();
        }
    }

    private void closeSocket() {
        synchronized (writeLock) {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }
            socket = null;
            writer = null;
        }
    }

    private void copy(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BridgeLink", text));
        }
    }

    private void startRing() {
        stopRing();
        try {
            Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            MediaPlayer next = new MediaPlayer();
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            next.setDataSource(this, alarm);
            next.setLooping(true);
            next.prepare();
            next.start();
            player = next;
        } catch (Exception ignored) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 400}, 0));
            }
        }
        Intent stop = new Intent(this, LinkService.class);
        stop.setAction(ACTION_STOP_RING);
        PendingIntent pending = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification ring = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("BridgeLink is ringing")
                .setContentText("The Mac asked this phone to ring")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", pending).build())
                .build();
        manager().notify(RING_ID, ring);
    }

    private void stopRing() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.cancel();
        manager().cancel(RING_ID);
    }

    private void alert(String title, String body) {
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build();
        manager().notify((int) (System.currentTimeMillis() & 0xffff), note);
    }

    private void publish(String status, String line) {
        if (status != null) {
            manager().notify(NOTE_ID, note("BridgeLink", status));
        }
        Intent event = new Intent(EVENT);
        event.setPackage(getPackageName());
        if (status != null) event.putExtra(EXTRA_STATUS, status);
        if (line != null) event.putExtra(EXTRA_LINE, line);
        sendBroadcast(event);
    }

    private Notification note(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private NotificationManager manager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
}
