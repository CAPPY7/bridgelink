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
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraManager;
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
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;

import app.bridgelink.android.protocol.Dedup;
import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.FrameChannel;
import app.bridgelink.android.protocol.HelloPlan;
import app.bridgelink.android.protocol.LinkFailure;
import app.bridgelink.android.protocol.LinkState;
import app.bridgelink.android.protocol.MacList;
import app.bridgelink.android.protocol.Outbox;
import app.bridgelink.android.protocol.Sas;
import app.bridgelink.android.protocol.ServiceDoor;
import app.bridgelink.android.protocol.Transfers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LinkService extends Service {
    public static final String ACTION_CONNECT = "app.bridgelink.android.CONNECT";
    public static final String ACTION_SEND = "app.bridgelink.android.SEND";
    public static final String ACTION_STOP_RING = "app.bridgelink.android.STOP_RING";
    public static final String ACTION_CONFIRM = "app.bridgelink.android.CONFIRM";
    public static final String ACTION_FORGET = "app.bridgelink.android.FORGET";
    public static final String ACTION_FIND_MAC = "app.bridgelink.android.FIND_MAC";
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
    private final Outbox outbox = new Outbox();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger alertIds = new AtomicInteger(100);
    private final Dedup seen = new Dedup(500);
    private final ConcurrentHashMap<String, Long> fileHave = new ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final Object PARK = new Object();
    private static final ArrayDeque<Intent> parked = new ArrayDeque<>();
    private static volatile LinkService running;

    private volatile Attempt attempt;
    private MediaPlayer player;
    private volatile boolean paired;
    private volatile String lastHost = "";
    private volatile int lastPort = 8787;
    private int savedAlarmVolume = -1;
    private final LinkState linkState = new LinkState();
    private FileBridge files;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile long lastRx;
    private volatile String sessionFp = "";
    private volatile String matchCode = "";
    private volatile String lastEcho = "";
    private volatile String sasPhoneNonce = "";
    private volatile String macCommit = "";
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
    private Runnable flashBlink;
    private boolean torchOn;
    private String torchId = "";

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
        files = new FileBridge(this);
        running = this;
        watchNetwork();
        flushParked();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground(paired ? "Paired with the Mac" : "Not connected");
        running = this;
        flushParked();
        if (intent == null) return START_STICKY;
        handleCommand(intent);
        return START_STICKY;
    }

    private void handleCommand(Intent intent) {
        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            connect(
                    intent.getStringExtra(EXTRA_HOST),
                    intent.getIntExtra(EXTRA_PORT, 8787),
                    intent.getStringExtra(EXTRA_PIN)
            );
        } else if (ACTION_SEND.equals(action)) {
            String kind = intent.getStringExtra(EXTRA_KIND);
            if ("file".equals(kind)) sendFile(intent.getStringExtra(EXTRA_BODY));
            else if ("link".equals(kind)) sendLink(intent.getStringExtra(EXTRA_BODY));
            else if ("notify".equals(kind)) sendNotify(intent);
            else send(kind, intent.getStringExtra(EXTRA_BODY));
        } else if (ACTION_STOP_RING.equals(action)) {
            stopRing();
        } else if (ACTION_CONFIRM.equals(action)) {
            new Thread(this::confirmSas, "bridgelink-confirm").start();
        } else if (ACTION_FORGET.equals(action)) {
            new Thread(this::forgetMac, "bridgelink-forget").start();
        } else if (ACTION_FIND_MAC.equals(action)) {
            send("find_mac", "");
        }
    }

    /** Used by background callers. Never throws if Android blocks a foreground start. */
    public static void post(Context context, Intent intent) {
        LinkService current = running;
        ServiceDoor.Result result = ServiceDoor.deliver(current != null, () -> {
            LinkService live = running;
            if (live != null) live.handleCommand(intent);
        }, () -> ContextCompat.startForegroundService(context, intent), ticket -> {
            synchronized (PARK) {
                parked.offer(ticket);
            }
        }, intent);
        if (result.notice != null) notifyReconnect(context);
    }

    private void flushParked() {
        List<Intent> waiting = new ArrayList<>();
        synchronized (PARK) {
            waiting.addAll(parked);
            parked.clear();
        }
        for (Intent intent : waiting) handleCommand(intent);
    }

    private static void notifyReconnect(Context context) {
        NotificationManager notifications = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (notifications == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "BridgeLink", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Phone to Mac link");
        notifications.createNotificationChannel(channel);
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 3, open, PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("BridgeLink")
                .setContentText(ServiceDoor.RECONNECT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        notifications.notify(3, note);
    }

    @Override
    public void onDestroy() {
        stopRing();
        if (networkCallback != null) {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager != null) {
                try {
                    manager.unregisterNetworkCallback(networkCallback);
                } catch (Exception ignored) {
                }
            }
            networkCallback = null;
        }
        main.removeCallbacksAndMessages(null);
        linkState.stop();
        generation.incrementAndGet();
        if (running == this) running = null;
        closeSocket();
        outbox.close();
        super.onDestroy();
    }

    private void connect(String host, int port, String pin) {
        final String address = host == null ? "" : host.trim();
        final String code = pin == null ? "" : pin.trim();
        lastHost = address;
        lastPort = port;
        final int gen = generation.incrementAndGet();
        Attempt previous = attempt;
        attempt = null;
        if (previous != null) previous.close();
        linkState.begin();
        Attempt mine = new Attempt(gen);
        attempt = mine;
        Thread thread = new Thread(() -> runConnect(mine, address, port, code), "bridgelink-conn-" + gen);
        thread.start();
    }

    private void runConnect(Attempt mine, String address, int port, String code) {
        if (mine.closed || generation.get() != mine.gen) return;
        paired = false;
        SharedPreferences prefs = Prefs.open(this);
        String savedToken = prefs.getString("token", "");
        String pinned = prefs.getString("fp", "");
        boolean tokenReady = savedToken != null && savedToken.length() >= 16 && pinned != null && !pinned.isEmpty();
        boolean qrReady = code.length() == 6 && pinned != null && !pinned.isEmpty();
        boolean sasReady = pinned == null || pinned.isEmpty();
        if (address.isEmpty() || !(tokenReady || qrReady || sasReady)) {
            linkState.failFatal("Scan the Mac code, or enter the Mac address and confirm the match code.");
            publish(linkState.fatal, null);
            return;
        }
        publish("Connecting to " + address + "…", null);
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new PinningTrust(this)}, null);
            SSLSocket next = (SSLSocket) context.getSocketFactory().createSocket();
            mine.socket = next;
            next.connect(new InetSocketAddress(address, port), 8000);
            next.setTcpNoDelay(true);
            next.startHandshake();
            if (mine.closed || generation.get() != mine.gen) {
                next.close();
                return;
            }
            String seen = fingerprint((X509Certificate) next.getSession().getPeerCertificates()[0]);
            sessionFp = seen;
            matchCode = "";
            macCommit = "";
            byte[] nonceRaw = new byte[16];
            new SecureRandom().nextBytes(nonceRaw);
            StringBuilder nonce = new StringBuilder(nonceRaw.length * 2);
            for (byte value : nonceRaw) nonce.append(String.format("%02x", value));
            sasPhoneNonce = nonce.toString();
            HelloPlan plan = HelloPlan.decide(Build.MODEL, code, savedToken, pinned, seen, nonce.toString());
            if (plan.kind != null) {
                next.close();
                linkState.failFatal(plan.fatal);
                if (plan.kind == LinkFailure.Kind.KEY_CHANGED) prefs.edit().remove("token").apply();
                publish(plan.fatal, null);
                return;
            }
            mine.channel = new FrameChannel(next);
            if (mine.closed || generation.get() != mine.gen) {
                mine.close();
                return;
            }
            writeEnvelope(Envelope.make("hello", "android", plan.payload));
            Frame.Parser parser = new Frame.Parser();
            byte[] buffer = new byte[8192];
            int count;
            while (!mine.closed && generation.get() == mine.gen && (count = next.getInputStream().read(buffer)) >= 0) {
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
            if (generation.get() == mine.gen && linkState.phase != LinkState.Phase.FATAL) {
                LinkFailure.Kind kind = LinkFailure.classify(error);
                String message = LinkFailure.message(kind);
                if (kind.fatal) {
                    linkState.failFatal(message);
                    if (kind == LinkFailure.Kind.KEY_CHANGED || kind == LinkFailure.Kind.TOKEN_REJECTED) {
                        Prefs.open(this).edit().remove("token").apply();
                    }
                    publish(message, null);
                } else {
                    linkState.retryable(message);
                    publish(message, null);
                }
            }
        } finally {
            main.removeCallbacks(heartbeat);
            mine.close();
            if (attempt == mine) {
                attempt = null;
                paired = false;
                if (linkState.retryScheduled && !address.isEmpty()) {
                    long delay = linkState.delayMs();
                    publish("Reconnecting…", null);
                    main.postDelayed(() -> connect(address, port, code), delay);
                } else if (linkState.phase != LinkState.Phase.FATAL) {
                    publish("Disconnected", null);
                }
            }
        }
    }

    private void onLine(String line) {
        try {
            Envelope message = Envelope.parse(line);
            if (!message.id.isEmpty() && !seen.firstTime(message.id)) return;
            String body = message.body();
            switch (message.type) {
                case "paired" -> {
                    paired = true;
                    linkState.paired();
                    String issued = message.text("token");
                    if (issued.length() >= 16) {
                        SharedPreferences prefs = Prefs.open(this);
                        SharedPreferences.Editor editor = prefs.edit().putString("token", issued);
                        String fp = sessionFp == null || sessionFp.isEmpty() ? prefs.getString("fp", "") : sessionFp;
                        if (fp != null && !fp.isEmpty()) editor.putString("fp", fp);
                        String device = message.text("deviceId");
                        if (!device.isEmpty()) editor.putString("deviceId", device);
                        String macs = prefs.getString("macs", "[]");
                        editor.putString("macs", MacList.add(macs, "Mac", lastHost, lastPort, fp));
                        editor.remove("pendingFp").remove("pin").commit();
                        if (prefs.getString("token", "").length() < 16) {
                            paired = false;
                            linkState.failFatal(Prefs.WARNING);
                            publish(Prefs.WARNING, null);
                            return;
                        }
                    }
                    publish("Paired with the Mac", "Mac accepted the phone");
                    main.removeCallbacks(heartbeat);
                    main.postDelayed(heartbeat, 20_000);
                    sendBattery();
                }
                case "sas" -> {
                    String commit = message.text("commit");
                    if (commit.isEmpty() || sasPhoneNonce.isEmpty()) {
                        linkState.failFatal("The Mac code did not match. Connect again.");
                        publish(linkState.fatal, null);
                        generation.incrementAndGet();
                        closeSocket();
                    } else {
                        macCommit = commit;
                        JsonObject reveal = new JsonObject();
                        reveal.addProperty("nonce", sasPhoneNonce);
                        writeEnvelope(Envelope.make("sas_reveal", "android", reveal));
                        publish("Checking the match code…", null);
                    }
                }
                case "sas_reveal" -> {
                    String revealed = message.text("nonce");
                    if (!Sas.revealMatches(macCommit, revealed)) {
                        linkState.failFatal("The Mac code did not match. Connect again.");
                        publish(linkState.fatal, null);
                        generation.incrementAndGet();
                        closeSocket();
                    } else {
                        matchCode = Sas.code(sessionFp, sasPhoneNonce, revealed);
                        publish("Match code " + matchCode + ". Tap Codes match on the phone and the Mac.", null);
                    }
                }
                case "pong" -> lastRx = System.currentTimeMillis();
                case "forget" -> {
                    SharedPreferences prefs = Prefs.open(this);
                    String macs = MacList.removeHost(prefs.getString("macs", "[]"), lastHost);
                    prefs.edit().remove("token").remove("fp").remove("pendingFp").remove("deviceId")
                            .putString("macs", macs).apply();
                    linkState.failFatal("The Mac forgot this phone");
                    publish("The Mac forgot this phone", null);
                    generation.incrementAndGet();
                    closeSocket();
                }
                case "error" -> {
                    String code = message.code();
                    LinkFailure.Kind kind = LinkFailure.fromCode(code);
                    boolean fatal = LinkState.isFatalCode(code);
                    String shown = fatal ? LinkFailure.message(kind) : "The Mac rejected that message.";
                    if (fatal) {
                        linkState.failFatal(shown);
                        if (kind == LinkFailure.Kind.TOKEN_REJECTED || kind == LinkFailure.Kind.WRONG_CODE || kind == LinkFailure.Kind.KEY_CHANGED) {
                            Prefs.open(this).edit().remove("token").apply();
                        }
                        generation.incrementAndGet();
                        closeSocket();
                    }
                    publish(shown, null);
                }
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
                    startRing("1".equals(message.text("flash")), !"0".equals(message.text("vibrate")));
                }
                case "find_stop" -> {
                    stopRing();
                    publish("Ring stopped", null);
                }
                case "link" -> openLink(message.text("url"));
                case "file_begin" -> {
                    String id = message.text("id");
                    long from = longText(message, "from");
                    String status = files.begin(id, message.text("name"), longText(message, "size"), Math.max(0, from));
                    JsonObject reply = new JsonObject();
                    reply.addProperty("id", id);
                    reply.addProperty("offset", Long.toString(files.offered(id)));
                    writeEnvelope(Envelope.make("file_have", "android", reply));
                    if (status != null) publish(status, null);
                }
                case "file_have" -> fileHave.put(message.text("id"), longText(message, "offset"));
                case "file_chunk" -> {
                    String status = files.chunk(message.text("id"), longText(message, "offset"), message.text("data"));
                    if (status != null) publish(status, null);
                }
                case "file_end" -> publish(files.finish(message.text("id"), message.text("sha256")), null);
                case "file_cancel" -> {
                    files.cancel(message.text("id"));
                    publish("File cancelled", null);
                }
                default -> {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("code", "unsupported_type");
                    payload.addProperty("body", "This phone does not handle " + message.type);
                    writeEnvelope(Envelope.make("error", "android", payload));
                }
            }
        } catch (Exception error) {
            LinkFailure.Kind kind = LinkFailure.classify(error);
            if (kind == LinkFailure.Kind.VERSION_MISMATCH) {
                linkState.failFatal(LinkFailure.message(kind));
                publish(LinkFailure.message(kind), null);
                generation.incrementAndGet();
                closeSocket();
                return;
            }
            publish("Bad message from the Mac", null);
        }
    }

    private void forgetMac() {
        try {
            JsonObject payload = new JsonObject();
            String id = Prefs.open(this).getString("deviceId", "");
            payload.addProperty("deviceId", id == null ? "" : id);
            writeEnvelope(Envelope.make("forget", "android", payload));
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = Prefs.open(this);
        String macs = MacList.removeHost(prefs.getString("macs", "[]"), lastHost);
        prefs.edit().remove("token").remove("fp").remove("pendingFp").remove("deviceId").remove("pin")
                .putString("macs", macs).commit();
        linkState.stop();
        generation.incrementAndGet();
        closeSocket();
        paired = false;
        publish("Forgot this Mac", null);
    }

    private void confirmSas() {
        if (matchCode == null || matchCode.isEmpty()) {
            publish("Wait until both screens show a code.", null);
            return;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("code", matchCode);
            writeEnvelope(Envelope.make("sas_confirm", "android", payload));
            publish("Waiting for the Mac to confirm the same code.", null);
        } catch (Exception error) {
            publish("Could not confirm. Connect again.", null);
        }
    }

    public void send(String kind, String body) {
        outbox.submit(() -> {
            if (!paired) {
                publish("Connect and pair before sending", null);
                return;
            }
            try {
                JsonObject payload = new JsonObject();
                String text = body == null ? "" : body;
                if ("clipboard".equals(kind) && Features.isEcho(lastEcho, text)) {
                    publish("Skipped a clipboard echo", null);
                    return;
                }
                payload.addProperty("body", text);
                writeEnvelope(Envelope.make(kind == null ? "text" : kind, "android", payload));
                if (!"ping".equals(kind)) {
                    publish("Sent", body == null || body.isEmpty() ? kind : "Sent");
                }
            } catch (Exception error) {
                publish("Send failed. Try again.", null);
            }
        });
    }

    private void sendNotify(Intent intent) {
        outbox.submit(() -> {
            if (!paired) return;
            if (!Features.enabled(Prefs.open(this).getString("flags", ""), "notify", false)) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("title", safe(intent.getStringExtra(EXTRA_BODY)));
                payload.addProperty("app", safe(intent.getStringExtra("app")));
                payload.addProperty("body", safe(intent.getStringExtra("detail")));
                writeEnvelope(Envelope.make("notify", "android", payload));
                if ("1".equals(intent.getStringExtra("otp"))
                        && Features.enabled(Prefs.open(this).getString("flags", ""), "otp", false)) {
                    String code = Features.otp(safe(intent.getStringExtra(EXTRA_BODY)) + "\n" + safe(intent.getStringExtra("detail")));
                    if (code != null) {
                        JsonObject otp = new JsonObject();
                        otp.addProperty("body", code);
                        writeEnvelope(Envelope.make("otp", "android", otp));
                    }
                }
                publish("Mirrored a notification", null);
            } catch (Exception error) {
                publish("Could not mirror a notification", null);
            }
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void sendFile(String source) {
        new Thread(() -> {
            if (!paired || files == null) {
                publish("Connect and pair before sending", null);
                return;
            }
            try {
                files.send(source, new FileBridge.Out() {
                    @Override
                    public void frame(String type, JsonObject payload) throws Exception {
                        writeEnvelope(Envelope.make(type, "android", payload));
                    }

                    @Override
                    public long have(String id) {
                        return waitHave(id);
                    }
                });
                publish("Sent the file", null);
            } catch (Exception error) {
                publish(error.getMessage() == null ? "Could not send the file" : error.getMessage(), null);
            }
        }, "bridgelink-file").start();
    }

    private int lastBattery = -1;
    private boolean lastCharging;
    private boolean sawBattery;

    private void sendBattery() {
        try {
            Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky == null) return;
            int level = sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            int status = sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            int percent = scale <= 0 ? level : (int) (level * 100f / scale);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            if (!Features.batteryChanged(lastBattery, lastCharging, sawBattery, percent, charging)) return;
            sawBattery = true;
            lastBattery = percent;
            lastCharging = charging;
            outbox.submit(() -> {
                if (!paired) return;
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("level", Integer.toString(percent));
                    payload.addProperty("charging", charging ? "1" : "0");
                    writeEnvelope(Envelope.make("battery", "android", payload));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private long waitHave(String id) {
        long end = System.currentTimeMillis() + 800;
        while (System.currentTimeMillis() < end) {
            Long value = fileHave.remove(id);
            if (value != null) return Math.max(0, value);
            try {
                Thread.sleep(40);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Long value = fileHave.remove(id);
        return value == null ? 0 : Math.max(0, value);
    }

    private void sendLink(String raw) {
        new Thread(() -> {
            if (!paired || files == null) {
                publish("Connect and pair before sending", null);
                return;
            }
            try {
                files.sendLink(raw, (type, payload) -> writeEnvelope(Envelope.make(type, "android", payload)));
                publish("Sent the link", null);
            } catch (Exception error) {
                publish(error.getMessage() == null ? "Could not send the link" : error.getMessage(), null);
            }
        }, "bridgelink-link").start();
    }

    private void openLink(String raw) {
        String url = Transfers.acceptUrl(raw);
        if (url == null) {
            publish("Refused a link that is not http or https", null);
            return;
        }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power == null || !power.isInteractive()) {
            publish("Link from the Mac is waiting", null);
            alert("Link from the Mac", "The screen was off, so the link was not opened.");
            return;
        }
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(view);
            publish("Opened a link from the Mac", null);
        } catch (Exception error) {
            publish("Could not open that link", null);
        }
    }

    private static long longText(Envelope message, String key) {
        try {
            return Long.parseLong(message.text(key));
        } catch (Exception error) {
            return -1;
        }
    }

    private void writeEnvelope(Envelope envelope) throws Exception {
        Attempt current = attempt;
        if (current == null || current.channel == null || current.closed) {
            throw new IOException("not connected");
        }
        byte[] frame = Frame.encode(Frame.JSON, envelope.toJsonString().getBytes(StandardCharsets.UTF_8));
        current.channel.write(frame);
    }

    private void closeSocket() {
        Attempt current = attempt;
        if (current != null) current.close();
    }

    private void copy(String text) {
        lastEcho = Features.echoKey(text == null ? "" : text);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BridgeLink", text));
        }
    }

    private void startRing(boolean flash, boolean vibrate) {
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
            vibrate = true;
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 400}, 0));
        }
        if (flash) blinkTorch();
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
        if (flashBlink != null) main.removeCallbacks(flashBlink);
        flashBlink = null;
        setTorch(false);
    }

    private void blinkTorch() {
        flashBlink = new Runnable() {
            @Override
            public void run() {
                torchOn = !torchOn;
                setTorch(torchOn);
                main.postDelayed(this, 400);
            }
        };
        main.post(flashBlink);
    }

    private void setTorch(boolean on) {
        try {
            CameraManager cameras = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameras == null) return;
            if (torchId.isEmpty()) {
                String[] ids = cameras.getCameraIdList();
                if (ids.length == 0) return;
                torchId = ids[0];
            }
            cameras.setTorchMode(torchId, on);
            torchOn = on;
        } catch (Exception ignored) {
            torchOn = false;
        }
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
        if (manager == null || networkCallback != null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
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
                main.post(() -> {
                    if (linkState.phase == LinkState.Phase.CONNECTING || linkState.phase == LinkState.Phase.PAIRED) return;
                    connect(host, chosen, "");
                });
            }
        };
        manager.registerDefaultNetworkCallback(networkCallback);
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
                return;
            }
            if (!MessageDigest.isEqual(pinned.getBytes(StandardCharsets.UTF_8), fp.getBytes(StandardCharsets.UTF_8))) {
                throw new CertificateException(new LinkFailure(LinkFailure.Kind.KEY_CHANGED, LinkFailure.message(LinkFailure.Kind.KEY_CHANGED)));
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /** One connection attempt. Its threads own the socket. close() does not wait for a write. */
    private static final class Attempt {
        final int gen;
        volatile SSLSocket socket;
        volatile FrameChannel channel;
        volatile boolean closed;

        Attempt(int gen) {
            this.gen = gen;
        }

        void close() {
            closed = true;
            FrameChannel current = channel;
            if (current != null) current.close();
            SSLSocket open = socket;
            if (open != null) {
                try {
                    open.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
