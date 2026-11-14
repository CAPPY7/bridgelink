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
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import app.bridgelink.android.protocol.Backoff;
import app.bridgelink.android.protocol.Dedup;
import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.Outbox;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    private final Outbox outbox = new Outbox();
    private final Object writeLock = new Object();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger alertIds = new AtomicInteger(100);
    private final Dedup seen = new Dedup(500);
    private final Handler main = new Handler(Looper.getMainLooper());

    private SSLSocket socket;
    private BufferedWriter writer;
    private MediaPlayer player;
    private volatile boolean paired;
    private int savedAlarmVolume = -1;
    private final AtomicInteger retryAttempt = new AtomicInteger();
    private volatile long lastRx;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (!paired) return;
            if (lastRx > 0 && System.currentTimeMillis() - lastRx > 45_000) {
                closeSocket();
                return;
            }
            send("ping", "");
            main.postDelayed(this, 20_000);
        }
    };
    private Runnable ringStop;

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
        watchNetwork();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground(paired ? "Paired with the Mac" : "Not connected");
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
        outbox.close();
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
            String savedToken = Prefs.open(this).getString("token", "");
            boolean haveToken = savedToken != null && savedToken.length() >= 16;
            if (address.isEmpty() || (!haveToken && code.length() != 6)) {
                publish("Enter the Mac address and the 6-digit PIN", null);
                return;
            }
            publish("Connecting to " + address + "…", null);
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new PinningTrust(this)}, null);
                SSLSocket next = (SSLSocket) context.getSocketFactory().createSocket();
                next.connect(new InetSocketAddress(address, port), 8000);
                next.setTcpNoDelay(true);
                next.startHandshake();
                synchronized (writeLock) {
                    if (generation.get() != gen) {
                        next.close();
                        return;
                    }
                    socket = next;
                    writer = null;
                }
                JSONObject hello = new JSONObject();
                hello.put("v", 1);
                hello.put("id", java.util.UUID.randomUUID().toString());
                hello.put("type", "hello");
                hello.put("from", "android");
                hello.put("ts", System.currentTimeMillis());
                JSONObject payload = new JSONObject();
                payload.put("name", Build.MODEL);
                payload.put("pin", code);
                if (haveToken) payload.put("token", savedToken);
                hello.put("payload", payload);
                writeJson(hello);
                Frame.Parser parser = new Frame.Parser();
                byte[] buffer = new byte[8192];
                int count;
                while (generation.get() == gen && (count = next.getInputStream().read(buffer)) >= 0) {
                    if (count == 0) continue;
                    byte[] chunk = java.util.Arrays.copyOf(buffer, count);
                    for (Frame frame : parser.push(chunk)) {
                        lastRx = System.currentTimeMillis();
                        if (frame.type == Frame.JSON) {
                            onLine(new String(frame.payload, StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (Exception error) {
                if (generation.get() == gen) {
                    publish("Link dropped: " + error.getMessage(), null);
                }
            } finally {
                main.removeCallbacks(heartbeat);
                if (generation.get() == gen) {
                    paired = false;
                    closeSocket();
                    String token = Prefs.open(this).getString("token", "");
                    if (token != null && token.length() >= 16 && !address.isEmpty()) {
                        int attempt = retryAttempt.incrementAndGet();
                        publish("Reconnecting…", null);
                        main.postDelayed(() -> connect(address, port, code), Backoff.nextMs(attempt));
                    } else {
                        publish("Disconnected", null);
                    }
                }
            }
        });
    }

    private void onLine(String line) {
        try {
            JSONObject message = new JSONObject(line);
            String type = message.optString("type", "");
            String body = message.optString("body", "");
            JSONObject payload = message.optJSONObject("payload");
            if (payload != null) {
                if (payload.has("body")) body = payload.optString("body", body);
                if (payload.has("name") && "hello".equals(type)) body = payload.optString("name", body);
            }
            String id = message.optString("id", "");
            if (!id.isEmpty() && !seen.firstTime(id)) return;
            switch (type) {
                case "paired" -> {
                    paired = true;
                    retryAttempt.set(0);
                    String issued = payload == null ? "" : payload.optString("token", "");
                    if (issued.length() >= 16) {
                        SharedPreferences prefs = Prefs.open(this);
                        SharedPreferences.Editor editor = prefs.edit().putString("token", issued);
                        String pending = prefs.getString("pendingFp", "");
                        if (pending != null && !pending.isEmpty()) editor.putString("fp", pending);
                        editor.remove("pin").apply();
                    }
                    publish("Paired with the Mac", "Mac accepted the phone");
                    main.removeCallbacks(heartbeat);
                    main.postDelayed(heartbeat, 20_000);
                }
                case "pong" -> lastRx = System.currentTimeMillis();
                case "error" -> publish(body.isEmpty() ? "Mac rejected the link" : body, null);
                case "text" -> {
                    publish("Note from the Mac", body);
                    alert("Note from the Mac", body);
                }
                case "clipboard" -> {
                    copy(body);
                    publish("Clipboard updated from the Mac", null);
                    alert("Clipboard from the Mac", "Clipboard updated");
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
        outbox.submit(() -> {
            if (!paired) {
                publish("Connect and pair before sending", null);
                return;
            }
            try {
                JSONObject message = new JSONObject();
                message.put("v", 1);
                message.put("id", java.util.UUID.randomUUID().toString());
                message.put("type", kind == null ? "text" : kind);
                message.put("from", "android");
                message.put("ts", System.currentTimeMillis());
                JSONObject payload = new JSONObject();
                payload.put("body", body == null ? "" : body);
                message.put("payload", payload);
                writeJson(message);
                if (!"ping".equals(kind)) {
                    publish("Sent", body == null || body.isEmpty() ? kind : "Sent");
                }
            } catch (Exception error) {
                publish("Send failed: " + error.getMessage(), null);
            }
        });
    }

    private void writeJson(JSONObject obj) throws Exception {
        byte[] frame = Frame.encode(Frame.JSON, obj.toString().getBytes(StandardCharsets.UTF_8));
        synchronized (writeLock) {
            if (socket == null) throw new IllegalStateException("not connected");
            socket.getOutputStream().write(frame);
            socket.getOutputStream().flush();
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
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        }
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
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", pending).build())
                .build();
        manager().notify(RING_ID, ring);
        ringStop = this::stopRing;
        main.postDelayed(ringStop, 120_000);
    }

    private void stopRing() {
        if (ringStop != null) main.removeCallbacks(ringStop);
        ringStop = null;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null && savedAlarmVolume >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
            savedAlarmVolume = -1;
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
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(this, CHANNEL)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle(title)
                        .setContentText("Open BridgeLink")
                        .build())
                .setAutoCancel(true)
                .build();
        manager().notify(alertIds.incrementAndGet(), note);
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

    private void startInForeground(String text) {
        Notification notification = note("BridgeLink", text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTE_ID, notification);
        }
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

    private void watchNetwork() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) return;
        manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                if (paired) return;
                SharedPreferences prefs = Prefs.open(LinkService.this);
                String host = prefs.getString("host", "");
                String token = prefs.getString("token", "");
                if (host == null || host.isEmpty() || token == null || token.length() < 16) return;
                int port = 8787;
                try {
                    port = Integer.parseInt(prefs.getString("port", "8787"));
                } catch (Exception ignored) {
                }
                int chosen = port;
                main.post(() -> connect(host, chosen, ""));
            }
        });
    }

    private static String fingerprint(X509Certificate certificate) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) builder.append(String.format("%02x", value));
            return builder.toString();
        } catch (Exception error) {
            throw new CertificateException(error);
        }
    }

    private static final class PinningTrust implements X509TrustManager {
        private final android.content.Context context;

        PinningTrust(android.content.Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String auth) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("No certificate");
            String fp = fingerprint(chain[0]);
            android.content.SharedPreferences prefs = Prefs.open(context);
            String pinned = prefs.getString("fp", "");
            if (pinned == null || pinned.isEmpty()) {
                prefs.edit().putString("pendingFp", fp).apply();
                return;
            }
            if (!MessageDigest.isEqual(pinned.getBytes(StandardCharsets.UTF_8), fp.getBytes(StandardCharsets.UTF_8))) {
                throw new CertificateException("This Mac key changed. Forget the Mac, then pair again.");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
