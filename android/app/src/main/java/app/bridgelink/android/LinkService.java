package app.bridgelink.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
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

import app.bridgelink.android.protocol.Backoff;
import app.bridgelink.android.protocol.BrowseAgain;
import app.bridgelink.android.protocol.Dedup;
import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.MediaQueue;
import app.bridgelink.android.protocol.MediaWatch;
import app.bridgelink.android.protocol.StageFour;
import app.bridgelink.android.protocol.StageRules;
import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.FrameChannel;
import app.bridgelink.android.protocol.HelloPlan;
import app.bridgelink.android.protocol.HostMerge;
import app.bridgelink.android.protocol.LinkFailure;
import app.bridgelink.android.protocol.LinkState;
import app.bridgelink.android.protocol.MacBook;
import app.bridgelink.android.protocol.MacList;
import app.bridgelink.android.protocol.MacTokens;
import app.bridgelink.android.protocol.Outbox;
import app.bridgelink.android.protocol.PairGate;
import app.bridgelink.android.protocol.Sas;
import app.bridgelink.android.protocol.ServiceDoor;
import app.bridgelink.android.protocol.SecondPhone;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String RING_CHANNEL = "find";
    private static final int NOTE_ID = 1;
    private static final int RING_ID = 2;

    private final IBinder binder = new LocalBinder();
    private final Outbox outbox = new Outbox();
    private final MediaQueue media = new MediaQueue();
    private final AtomicBoolean mediaPumping = new AtomicBoolean();
    private static volatile Runnable needKey;
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger alertIds = new AtomicInteger(100);
    private final AtomicInteger fileNoteIds = new AtomicInteger();
    private final Dedup seen = new Dedup(500);
    private final ConcurrentHashMap<String, Long> fileHave = new ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final Object PARK = new Object();
    private static final ArrayDeque<Intent> parked = new ArrayDeque<>();
    private static volatile LinkService running;

    private volatile Attempt attempt;
    private MediaPlayer player;
    private volatile boolean paired;
    private int relayAttempt;

    private void rejoinRelay() {
        if (six == null) return;
        if (six.relayLive()) return;
        if (paired && !onRelay) return;
        six.tryRelay();
    }
    private volatile String lastHost = "";
    private volatile int lastPort = 8787;
    private int savedAlarmVolume = -1;
    private final LinkState linkState = new LinkState();
    private FileBridge files;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver batteryWatch;
    private final List<String> pendingHosts = new ArrayList<>();
    private volatile long lastRx;
    private volatile String sessionFp = "";
    private volatile String matchCode = "";
    private volatile String lastEcho = "";
    private final AtomicBoolean fileCancel = new AtomicBoolean();
    private boolean macLowAlerted;
    private boolean macFullAlerted;
    private boolean sawDnd;
    private boolean lastDnd;
    private volatile String sasPhoneNonce = "";
    private volatile String macCommit = "";
    private volatile String lastNow = "";
    private LeaveWatch leave;
    private PhoneFive five;
    private PhoneSix six;
    private MacBrowser browser;
    private volatile boolean onRelay;
    private volatile boolean relayAuthed;
    private volatile boolean replyOnRelay;
    private final Runnable rejoinTick = new Runnable() {
        @Override
        public void run() {
            rejoinRelay();
        }
    };
    private final app.bridgelink.android.protocol.AwayAuth awayAuth =
            new app.bridgelink.android.protocol.AwayAuth(new app.bridgelink.android.protocol.AwayAuth.Sink() {
                @Override
                public void writeAuth() {
                    outbox.submit(() -> {
                        if (!onRelay || six == null || !six.relayLive()) return;
                        if (!app.bridgelink.android.protocol.RelayAuth.keepSending(relayAuthed, "")) return;
                        try {
                            JsonObject auth = new JsonObject();
                            String token = Prefs.open(LinkService.this).getString("token", "");
                            auth.addProperty("token", token == null ? "" : token);
                            writeEnvelope(Envelope.make(app.bridgelink.android.protocol.RelayAuth.AUTH, "android", auth));
                        } catch (Exception ignored) {
                        }
                    });
                }

                @Override
                public void post(Runnable task, long delayMs) {
                    main.postDelayed(task, delayMs);
                }

                @Override
                public void remove(Runnable task) {
                    main.removeCallbacks(task);
                }
            });
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (!paired) return;
            if (lastRx > 0 && System.currentTimeMillis() - lastRx > 45_000) {
                if (six != null && six.relayLive()) {
                    main.postDelayed(this, 20_000);
                    return;
                }
                closeSocket();
                return;
            }
            send("ping", "");
            if (leave != null) leave.tick();
            sendNowPlaying();
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
        NotificationChannel ring = new NotificationChannel(RING_CHANNEL, "Find my phone", NotificationManager.IMPORTANCE_HIGH);
        ring.setDescription("Rings even when the phone is on silent");
        ring.setBypassDnd(true);
        manager().createNotificationChannel(ring);
        files = new FileBridge(this);
        five = new PhoneFive(this, new PhoneFive.Out() {
            @Override
            public void frame(String type, JsonObject payload) throws Exception {
                writeEnvelope(Envelope.make(type, "android", payload));
            }

            @Override
            public void status(String line) {
                publish(line, null);
            }
        });
        six = new PhoneSix(this, new PhoneSix.Out() {
            @Override
            public void frame(String type, JsonObject payload) throws Exception {
                writeEnvelope(Envelope.make(type, "android", payload));
            }

            @Override
            public void status(String line) {
                publish(line, null);
            }

            @Override
            public void relayUp() {
                relayAttempt = 0;
                relayAuthed = false;
                main.removeCallbacks(rejoinTick);
                onRelay = true;
                replyOnRelay = true;
                paired = false;
                publish("Connecting through the away relay…", null);
                awayAuth.start();
            }

            @Override
            public void relayDown(String reason) {
                boolean wasRelay = onRelay;
                replyOnRelay = false;
                if (app.bridgelink.android.protocol.RelayRetry.rejoin(reason)) {
                    relayAuthed = false;
                    awayAuth.stop();
                    if (wasRelay) {
                        paired = false;
                        onRelay = false;
                    }
                    publish(app.bridgelink.android.protocol.RelayRetry.DOWN, null);
                    if (wasRelay) {
                        long delay = app.bridgelink.android.protocol.RelayRetry.delayMs(relayAttempt++);
                        main.removeCallbacks(rejoinTick);
                        main.postDelayed(rejoinTick, delay);
                    }
                    return;
                }
                publish(reason, null);
            }

            @Override
            public void relayFrames(java.util.List<Frame> frames) {
                replyOnRelay = true;
                for (Frame frame : frames) {
                    if (frame.type == Frame.JSON) onLine(new String(frame.payload, StandardCharsets.UTF_8));
                }
            }
        });
        running = this;
        watchNetwork();
        batteryWatch = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (paired) sendBattery();
            }
        };
        registerReceiver(batteryWatch, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        watchClipboard();
        leave = new LeaveWatch(this, new LeaveWatch.Sink() {
            @Override
            public void lock() {
                send("lock", "away");
            }

            @Override
            public void status(String text) {
                publish(text, null);
            }
        });
        leave.start();
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
        if (intent == null) {
            reconnectSaved();
            return START_STICKY;
        }
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
            else if ("files".equals(kind)) sendFiles(intent.getStringExtra(EXTRA_BODY));
            else if ("link".equals(kind)) sendLink(intent.getStringExtra(EXTRA_BODY));
            else if ("notify".equals(kind)) sendNotify(intent);
            else if ("notify_dismiss".equals(kind)) sendDismiss(intent.getStringExtra("key"));
            else if ("otp".equals(kind)) sendOtp(intent.getStringExtra(EXTRA_BODY));
            else if ("sms_in".equals(kind) && five != null) {
                five.smsIn(safe(intent.getStringExtra("address")) + "\n" + safe(intent.getStringExtra(EXTRA_BODY)));
            }
            else if ("sms_local".equals(kind) && five != null) {
                int sim = 0;
                try {
                    sim = Integer.parseInt(safe(intent.getStringExtra("sim")).trim());
                } catch (NumberFormatException ignored) {
                }
                int chosen = sim;
                five.sendLocalSms(intent.getStringExtra("address"), intent.getStringExtra(EXTRA_BODY), chosen);
            }
            else if ("cancel-file".equals(kind)) fileCancel.set(true);
            else if ("lock-refresh".equals(kind) && leave != null) leave.start();
            else send(kind, intent.getStringExtra(EXTRA_BODY));
        } else if (ACTION_STOP_RING.equals(action)) {
            stopRing();
            send("find_stop", "");
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
        if (leave != null) leave.stop();
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
        if (batteryWatch != null) {
            try {
                unregisterReceiver(batteryWatch);
            } catch (Exception ignored) {
            }
            batteryWatch = null;
        }
        main.removeCallbacksAndMessages(null);
        awayAuth.stop();
        linkState.stop();
        generation.incrementAndGet();
        if (running == this) running = null;
        closeSocket();
        if (six != null) six.stopRelay();
        if (browser != null) browser.stop();
        outbox.close();
        super.onDestroy();
    }

    private void connect(String host, int port, String pin) {
        String extra = Prefs.open(this).getString("hosts", "");
        String first;
        synchronized (pendingHosts) {
            pendingHosts.clear();
            pendingHosts.addAll(Features.addressOrder(host, extra));
            first = pendingHosts.isEmpty() ? (host == null ? "" : host.trim()) : pendingHosts.remove(0);
        }
        openAttempt(first, port, pin);
    }

    private void openAttempt(String host, int port, String pin) {
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
        if (!onRelay) paired = false;
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
            HelloPlan plan = HelloPlan.decide(Build.MODEL, code, PairGate.helloToken(pinned, savedToken, seen), pinned, seen, nonce.toString());
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
                String next = "";
                synchronized (pendingHosts) {
                    if (!kind.fatal && !pendingHosts.isEmpty() && !paired) next = pendingHosts.remove(0);
                }
                if (!next.isEmpty()) {
                    mine.hopTo = next;
                    publish("Could not reach " + address + ". Trying the next Mac address.", null);
                } else {
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
            }
        } finally {
            main.removeCallbacks(heartbeat);
            boolean userStopped = mine.closed || generation.get() != mine.gen;
            String liveToken = Prefs.open(this).getString("token", "");
            boolean tokenSaved = liveToken != null && liveToken.length() >= 16;
            mine.close();
            if (attempt == mine) {
                attempt = null;
                boolean relayStill = onRelay && six != null && six.relayLive();
                if (!app.bridgelink.android.protocol.RelayRetry.leaveIt(relayStill)) paired = false;
                String next = mine.hopTo;
                if (next != null && !next.isEmpty()) {
                    main.post(() -> openAttempt(next, port, code));
                } else if (linkState.holding(System.currentTimeMillis()) && !address.isEmpty()) {
                    long delay = Math.max(0, linkState.holdUntil - System.currentTimeMillis());
                    publish("Another phone is connected. This phone will wait 5 minutes.", null);
                    main.postDelayed(() -> connect(address, port, code), delay);
                } else if (linkState.afterClose(userStopped, tokenSaved) && !address.isEmpty()) {
                    long delay = linkState.delayMs();
                    if (linkState.attempt >= 2 && six != null && !six.relayLive()) {
                        six.tryRelay();
                        publish("Wi-Fi missed the Mac. Trying away from home, and still retrying Wi-Fi.", null);
                    } else {
                        publish("Reconnecting…", null);
                    }
                    if (BrowseAgain.shouldBrowse(linkState.attempt)) browseSaved();
                    main.postDelayed(() -> connect(address, port, code), delay);
                } else if (!userStopped && linkState.phase != LinkState.Phase.FATAL) {
                    if (six != null && !six.relayLive() && six.tryRelay()) {
                        publish("Wi-Fi missed the Mac. Trying the away relay.", null);
                    } else {
                        publish("Disconnected", null);
                    }
                }
            }
        }
    }

    private void browseSaved() {
        if (browser != null) return;
        String pinned = Prefs.open(this).getString("fp", "");
        browser = new MacBrowser(this, new MacBrowser.Callback() {
            @Override
            public void onHost(String host) {
                if (host == null || host.isEmpty()) return;
                String saved = Prefs.open(LinkService.this).getString("hosts", "");
                Prefs.open(LinkService.this).edit().putString("host", host).putString("hosts", HostMerge.merge(saved, host)).apply();
                lastHost = host;
                publish("Found the Mac at " + host, null);
                main.post(() -> connect(host, lastPort, ""));
            }

            @Override
            public void onStatus(String status) {
                if (status != null) publish(status, null);
            }
        }, pinned);
        browser.start();
    }

    private void reconnectSaved() {
        SharedPreferences prefs = Prefs.open(this);
        String host = prefs.getString("host", "");
        String token = prefs.getString("token", "");
        if (host == null || host.isEmpty() || token == null || token.length() < 16) return;
        int port = 8787;
        try {
            port = Integer.parseInt(prefs.getString("port", "8787"));
        } catch (Exception ignored) {
        }
        connect(host, port, "");
    }

    private void onLine(String line) {
        try {
            Envelope message = Envelope.parse(line);
            if (!message.id.isEmpty() && !seen.firstTime(message.id)) return;
            String body = message.body();
            switch (message.type) {
                case "paired" -> {
                    paired = true;
                    onRelay = false;
                    replyOnRelay = false;
                    relayAuthed = false;
                    relayAttempt = 0;
                    main.removeCallbacks(rejoinTick);
                    awayAuth.stop();
                    if (six != null) six.stopRelay();
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
                        editor.putString("macs", MacList.add(macs, "Mac", lastHost, lastPort, fp, device));
                        if (fp != null && !fp.isEmpty()) {
                            editor.putString("macTokens", MacTokens.put(prefs.getString("macTokens", ""), fp, issued));
                        }
                        editor.putString("host", lastHost);
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
                    if (five != null) five.onPaired();
                    if (six != null && attempt != null && attempt.channel != null) six.stopRelay();
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
                case "ping" -> {
                    if (onRelay) {
                        relayAuthed = false;
                        awayAuth.macJoined();
                    }
                    try {
                        writeEnvelope(Envelope.make("pong", "android", new JsonObject()));
                    } catch (Exception ignored) {
                    }
                }
                case "relay_ok" -> {
                    relayAuthed = true;
                    awayAuth.ok();
                    if (onRelay) {
                        paired = true;
                        publish("Away (relay). The relay cannot read the link.", null);
                        main.removeCallbacks(heartbeat);
                        main.postDelayed(heartbeat, 1_000);
                    }
                }
                case "battery" -> {
                    if ("mac".equals(message.text("label"))) {
                        noteMacBattery(message.text("level"), "1".equals(message.text("charging")));
                    }
                }
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
                    if (onRelay && app.bridgelink.android.protocol.RelayAuth.forgotten(code)) {
                        relayAuthed = false;
                        awayAuth.forgot();
                        onRelay = false;
                        replyOnRelay = false;
                        paired = false;
                        if (six != null) six.stopRelay();
                        publish("This phone is not paired with that Mac.", null);
                    } else if ("busy".equals(code)) {
                        linkState.holdUntil = SecondPhone.until(System.currentTimeMillis());
                        publish("Another phone is connected. This phone will wait 5 minutes.", null);
                        closeSocket();
                    } else {
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
                }
                case "text" -> {
                    publish("Note from the Mac", body);
                    alert("Note from the Mac", body);
                }
                case "clipboard" -> {
                    if (!acceptClipboard()) break;
                    copy(body);
                    publish("Clipboard updated from the Mac", null);
                    alert("Clipboard from the Mac", "Clipboard updated");
                }
                case "clipboard_image" -> acceptClipboardImage(message.text("data"));
                case "notify_reply" -> {
                    boolean sent = NotifyMirror.reply(message.text("key"), message.text("body"));
                    publish(sent ? "Sent a reply" : "That notification does not accept a reply", null);
                }
                case "notify_dismiss" -> {
                    NotifyMirror.dismiss(message.text("key"));
                    publish("Dismissed a notification", null);
                }
                case "dnd" -> applyMacDnd("1".equals(message.text("on")));
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
                case "file_end" -> {
                    String saved = files.finish(message.text("id"), message.text("sha256"));
                    publish(saved, null);
                    if (saved != null && saved.startsWith("Saved")) {
                        askFile(files.lastName(), files.lastUri());
                        if (five != null) five.fire("file_in", saved);
                    }
                }
                case "file_cancel" -> {
                    files.cancel(message.text("id"));
                    publish("File cancelled", null);
                }
                case "media" -> {
                    MediaControl.Result result = MediaControl.fromMac(this, message.text("action"), message.text("volume"));
                    publish(result.status, null);
                    if (result.reply != null) writeEnvelope(Envelope.make("media_now", "android", result.reply));
                }
                case "media_now" -> publish(StageFour.nowLine(
                        message.text("title"), message.text("artist"), "1".equals(message.text("playing"))), null);
                case "lock_setup" -> {
                    int[] setup = StageFour.parseSetup(message.text("body"));
                    if (setup == null) {
                        publish("Lock settings were not understood", null);
                    } else {
                        LeaveWatch.saveSetup(this, setup);
                        if (leave != null) leave.start();
                        publish(setup[0] == 1 ? "Lock when you leave is on" : "Lock when you leave is off", null);
                    }
                }
                default -> {
                    if (five != null && five.handle(message)) break;
                    if (six != null && six.handle(message)) break;
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
        String device = prefs.getString("deviceId", "");
        String book = app.bridgelink.android.protocol.RelaySecrets.drop(prefs.getString("relayBook", ""), device);
        prefs.edit().remove("token").remove("fp").remove("pendingFp").remove("deviceId").remove("pin")
                .remove("relaySecret").putString("relayBook", book)
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
                if ("clipboard".equals(kind)) {
                    sendClipboardNow(true);
                    return;
                }
                if ("media".equals(kind)) {
                    String[] watch = MediaWatch.command(text);
                    String[] parts = watch != null ? watch : StageFour.splitCommand(text);
                    if (parts == null) {
                        publish("That media button is not supported", null);
                        return;
                    }
                    payload.addProperty("action", parts[0]);
                    payload.addProperty("volume", parts[1]);
                    writeEnvelope(Envelope.make("media", "android", payload));
                    if (watch == null) publish("Sent a media command", null);
                    return;
                }
                if ("lock".equals(kind)) {
                    if (!StageFour.lockRequest(text.isEmpty() ? "away" : text)) {
                        publish("This phone never unlocks the Mac", null);
                        return;
                    }
                    payload.addProperty("reason", "away");
                    writeEnvelope(Envelope.make("lock", "android", payload));
                    if (five != null) five.fire("leave", "");
                    publish("Asked the Mac to lock", null);
                    return;
                }
                if ("lock_setup".equals(kind)) {
                    if (StageFour.parseSetup(text) == null) {
                        publish("Lock settings were not understood", null);
                        return;
                    }
                    payload.addProperty("body", text);
                    writeEnvelope(Envelope.make("lock_setup", "android", payload));
                    publish("Sent lock settings", null);
                    return;
                }
                if (five != null && ("pointer".equals(kind) || "sms_push".equals(kind) || "folder_sync".equals(kind)
                        || "folder_pause".equals(kind) || "auto".equals(kind) || "wifi".equals(kind) || "input_on".equals(kind))) {
                    five.command(kind, text);
                    return;
                }
                if (six != null && six.known(kind)) {
                    six.command(kind, text);
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
                payload.addProperty("key", safe(intent.getStringExtra("key")));
                payload.addProperty("reply", safe(intent.getStringExtra("reply")));
                payload.addProperty("body", safe(intent.getStringExtra("detail")));
                writeEnvelope(Envelope.make("notify", "android", payload));
                if (five != null) five.fire("notify", safe(intent.getStringExtra("app")));
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
        sendFiles(source);
    }

    private void sendFiles(String source) {
        fileCancel.set(false);
        new Thread(() -> {
            if (!paired || files == null) {
                publish("Connect and pair before sending", null);
                return;
            }
            try {
                files.sendMany(source, fileOut());
                publish("Sent the file", null);
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not send the file" : error.getMessage();
                publish(message, null);
            }
        }, "bridgelink-file").start();
    }

    private FileBridge.Out fileOut() {
        return new FileBridge.Out() {
            @Override
            public void frame(String type, JsonObject payload) throws Exception {
                writeEnvelope(Envelope.make(type, "android", payload));
            }

            @Override
            public long have(String id) {
                return waitHave(id);
            }

            @Override
            public void progress(String name, int percent) {
                publish("Sending " + name + " — " + percent + "%", null);
            }

            @Override
            public boolean cancelled() {
                return fileCancel.get();
            }
        };
    }

    private int lastBattery = -1;
    private boolean lastCharging;
    private boolean sawBattery;

    private void sendNowPlaying() {
        outbox.submit(() -> {
            if (!paired) return;
            try {
                JsonObject now = MediaControl.snapshot(this);
                String key = now.get("title").getAsString() + "|" + now.get("playing").getAsString() + "|" + now.get("volume").getAsString();
                if (key.equals(lastNow)) return;
                lastNow = key;
                writeEnvelope(Envelope.make("media_now", "android", now));
            } catch (Exception ignored) {
            }
        });
    }

    private boolean passwordClip(ClipboardManager clipboard) {
        if (clipboard == null || Build.VERSION.SDK_INT < 33) return false;
        ClipDescription description = clipboard.getPrimaryClipDescription();
        if (description == null || description.getExtras() == null) return false;
        boolean marked = description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
        return StageRules.skipPasswordClip(false, false, marked);
    }

    private static String clipboardText(ClipboardManager clipboard) {
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) return "";
        ClipData clip = clipboard.getPrimaryClip();
        if (clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) return "";
        return clip.getItemAt(0).getText().toString();
    }

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
            if (!Features.batteryChanged(lastBattery, lastCharging, sawBattery, percent, charging)) {
                maybeSendDnd();
                return;
            }
            sawBattery = true;
            lastBattery = percent;
            lastCharging = charging;
            if (leave != null) leave.setCharging(charging);
            if (five != null) five.noteBattery(percent, charging);
            outbox.submit(() -> {
                if (!paired) return;
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("level", Integer.toString(percent));
                    payload.addProperty("charging", charging ? "1" : "0");
                    payload.addProperty("label", "phone");
                    writeEnvelope(Envelope.make("battery", "android", payload));
                    maybeSendDnd();
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
        boolean always = "1".equals(Prefs.open(this).getString("linksAlways", "0"));
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean screenOn = power != null && power.isInteractive();
        if (!StageRules.openLinkNow(screenOn, always)) {
            askLink(url);
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
        writeRaw(Frame.encode(Frame.JSON, envelope.toJsonString().getBytes(StandardCharsets.UTF_8)));
    }

    void writeRaw(byte[] framed) throws Exception {
        Attempt current = attempt;
        boolean wifiUp = current != null && current.channel != null && !current.closed;
        boolean relayUp = six != null && six.relayLive();
        if (app.bridgelink.android.protocol.StageSix.useRelay(replyOnRelay, wifiUp, relayUp)) {
            if (six.relaySend(framed)) return;
            throw new IOException("not connected");
        }
        if (wifiUp) {
            current.channel.write(framed);
            return;
        }
        throw new IOException("not connected");
    }

    public static void onNeedKey(Runnable ask) {
        needKey = ask;
    }

    public static void sendRaw(byte[] framed) {
        LinkService live = running;
        if (live == null || framed == null) return;
        MediaQueue.Result result = live.media.offer(framed, MediaQueue.keyFrame(framed));
        if (result.askKey) {
            Runnable ask = needKey;
            if (ask != null) {
                try {
                    ask.run();
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (result.kept) live.pumpMedia();
    }

    private void pumpMedia() {
        if (!mediaPumping.compareAndSet(false, true)) return;
        outbox.submit(() -> {
            try {
                byte[] next;
                while ((next = media.poll()) != null) {
                    try {
                        writeRaw(next);
                    } catch (Exception error) {
                        publish("The picture could not be sent.", null);
                        break;
                    }
                }
            } finally {
                mediaPumping.set(false);
                if (media.size() > 0) pumpMedia();
            }
        });
    }

    public static void postStatus(String line) {
        LinkService live = running;
        if (live != null && line != null) live.publish(line, null);
    }

    private void closeSocket() {
        Attempt current = attempt;
        if (current != null) current.close();
    }

    private void noteMacBattery(String levelText, boolean charging) {
        int level;
        try {
            level = Integer.parseInt(levelText);
        } catch (Exception error) {
            publish("Mac battery " + levelText + "%", null);
            return;
        }
        if (StageRules.fullBattery(level, macFullAlerted)) {
            macFullAlerted = true;
            alert("Mac is fully charged", "The Mac battery is at 100%.");
        }
        if (level < 100) macFullAlerted = false;
        if (Features.lowBattery(level, charging, macLowAlerted)) {
            macLowAlerted = true;
            alert("Mac battery is low", "The Mac battery is at " + level + "%.");
        }
        if (level > 15 || charging) macLowAlerted = false;
        publish("Mac battery " + level + "%", null);
    }

    private void maybeSendDnd() {
        boolean dnd = manager().getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        if (sawDnd && dnd == lastDnd) return;
        sawDnd = true;
        lastDnd = dnd;
        if (!Features.enabled(Prefs.open(this).getString("flags", ""), "dnd", false)) return;
        outbox.submit(() -> {
            if (!paired) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("on", dnd ? "1" : "0");
                payload.addProperty("label", "phone");
                writeEnvelope(Envelope.make("dnd", "android", payload));
            } catch (Exception ignored) {
            }
        });
    }

    private void applyMacDnd(boolean on) {
        if (!Features.enabled(Prefs.open(this).getString("flags", ""), "dnd", false)) {
            publish(on ? "Mac is in Do Not Disturb" : "Mac left Do Not Disturb", null);
            return;
        }
        try {
            if (manager().isNotificationPolicyAccessGranted()) {
                manager().setInterruptionFilter(on
                        ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        : NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        } catch (Exception ignored) {
        }
        publish(on ? "Mac is in Do Not Disturb" : "Mac left Do Not Disturb", null);
    }

    private boolean acceptClipboard() {
        return Features.enabled(Prefs.open(this).getString("flags", ""), "clipboard", true);
    }

    private void watchClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.addPrimaryClipChangedListener(() -> {
            if (passwordClip(clipboard)) return;
            ClipStore.addText(this, clipboardText(clipboard));
            if (!paired) return;
            if (!Features.enabled(Prefs.open(this).getString("flags", ""), "clipboard", false)) return;
            sendClipboardNow(false);
        });
    }

    private void sendClipboardNow(boolean manual) {
        outbox.submit(() -> {
            if (!paired) {
                if (manual) publish("Connect and pair before sending", null);
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) {
                if (manual) publish("Clipboard was empty", null);
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (passwordClip(clipboard)) {
                if (manual) publish("Skipped a password-manager clipboard", null);
                return;
            }
            CharSequence text = clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
            String value = text == null ? "" : text.toString();
            boolean image = clip.getItemCount() > 0 && clip.getItemAt(0).getUri() != null && value.isEmpty();
            String kind = StageRules.clipKind(!value.isEmpty(), image, Features.looksSensitive(value));
            if ("skip".equals(kind)) {
                publish("Skipped sensitive clipboard", null);
                return;
            }
            if ("text".equals(kind)) {
                if (Features.isEcho(lastEcho, value)) {
                    if (manual) publish("Skipped a clipboard echo", null);
                    return;
                }
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("body", value);
                    writeEnvelope(Envelope.make("clipboard", "android", payload));
                    ClipStore.addText(this, value);
                    publish("Sent clipboard", null);
                } catch (Exception error) {
                    publish("Send failed. Try again.", null);
                }
                return;
            }
            if ("image".equals(kind)) {
                sendClipboardImage(clip.getItemAt(0).getUri());
                return;
            }
            if (manual) publish("Clipboard was empty", null);
        });
    }

    private static byte[] readCap(java.io.InputStream in, int cap) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int count;
        while (out.size() <= cap && (count = in.read(buffer)) >= 0) {
            if (count > 0) out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private void sendClipboardImage(Uri uri) {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                publish("Could not read the clipboard image", null);
                return;
            }
            byte[] raw = readCap(in, StageRules.IMAGE_CAP + 1);
            if (!StageRules.imageFits(raw.length)) {
                publish("That clipboard image is too big", null);
                return;
            }
            String key = Features.echoKey(java.util.Base64.getEncoder().encodeToString(raw));
            if (key.equals(lastEcho)) return;
            lastEcho = key;
            JsonObject payload = new JsonObject();
            payload.addProperty("data", java.util.Base64.getEncoder().encodeToString(raw));
            writeEnvelope(Envelope.make("clipboard_image", "android", payload));
            publish("Sent a clipboard image", null);
        } catch (Exception error) {
            publish("Could not send the clipboard image", null);
        }
    }

    private void acceptClipboardImage(String data) {
        if (!acceptClipboard()) return;
        byte[] raw;
        try {
            raw = java.util.Base64.getDecoder().decode(data == null ? "" : data);
        } catch (IllegalArgumentException error) {
            publish("Skipped a clipboard image", null);
            return;
        }
        if (!StageRules.imageFits(raw.length)) {
            publish("Skipped a clipboard image", null);
            return;
        }
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "clips");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            java.io.File file = new java.io.File(dir, "clip.png");
            java.nio.file.Files.write(file.toPath(), raw);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "app.bridgelink.android.files", file);
            lastEcho = Features.echoKey(data);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "BridgeLink", uri));
            }
            publish("Clipboard image updated", null);
        } catch (Exception error) {
            publish("Could not save the clipboard image", null);
        }
    }

    private void sendOtp(String code) {
        outbox.submit(() -> {
            if (!paired || code == null || code.isEmpty()) return;
            if (!Features.enabled(Prefs.open(this).getString("flags", ""), "otp", false)) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("body", code);
                writeEnvelope(Envelope.make("otp", "android", payload));
                if (five != null) five.fire("sms_code", code);
                publish("Copied a code to the Mac", null);
            } catch (Exception error) {
                publish("Could not copy the code", null);
            }
        });
    }

    private void sendDismiss(String key) {
        outbox.submit(() -> {
            if (!paired || key == null || key.isEmpty()) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("key", key);
                writeEnvelope(Envelope.make("notify_dismiss", "android", payload));
            } catch (Exception ignored) {
            }
        });
    }

    private void copy(String text) {
        lastEcho = Features.echoKey(text == null ? "" : text);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BridgeLink", text));
        }
        if (five != null) five.fire("clip", "");
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
        Notification ring = new Notification.Builder(this, RING_CHANNEL)
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

    private void askFile(String name, android.net.Uri uri) {
        String title = StageRules.fileTitle(name);
        int id = StageRules.fileNoteId(fileNoteIds.incrementAndGet());
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText("Saved in Downloads. It was not opened.")
                .setAutoCancel(true);
        Intent show = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.addAction(new Notification.Action.Builder(null, "Show in Downloads",
                PendingIntent.getActivity(this, id, show, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        if (uri != null && StageRules.canOfferOpen(name, fileMime(uri))) {
            Intent open = new Intent(Intent.ACTION_VIEW, uri);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    | PendingIntent.FLAG_GRANT_READ_URI_PERMISSION;
            PendingIntent pending = PendingIntent.getActivity(this, id + 50_000, open, flags);
            builder.setContentIntent(pending);
            builder.addAction(new Notification.Action.Builder(null, "Open", pending).build());
        }
        manager().notify(id, builder.build());
        publish(title, null);
    }

    /** The file type stored with the download, when MediaStore has one. */
    private String fileMime(android.net.Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{android.provider.MediaStore.MediaColumns.MIME_TYPE},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                String mime = cursor.getString(0);
                if (mime != null && !mime.isEmpty()) return mime;
            }
        } catch (Exception ignored) {
        }
        try {
            String mime = getContentResolver().getType(uri);
            return mime == null ? "" : mime;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void askLink(String url) {
        publish("Link from the Mac is waiting", null);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Link from the Mac")
                .setContentText("Tap Open. The link was not opened by itself.")
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Open",
                        PendingIntent.getActivity(this, 43, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build())
                .build();
        manager().notify(43, note);
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
                String chosenHost = host;
                main.post(() -> {
                    boolean wifiLinked = paired && !onRelay;
                    if (!LinkState.networkShouldConnect(linkState.phase, wifiLinked)) return;
                    connect(chosenHost, chosen, "");
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
            if (MacBook.known(prefs.getString("macTokens", ""), fp)) {
                if (pinned == null || !pinned.equalsIgnoreCase(fp)) {
                    String token = MacTokens.tokenFor(prefs.getString("macTokens", ""), fp);
                    prefs.edit().putString("fp", fp).putString("token", token).apply();
                }
                return;
            }
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
        volatile String hopTo;

        Attempt(int gen) {
            this.gen = gen;
        }

        void close() {
            closed = true;
            FrameChannel current = channel;
            if (current != null) {
                current.close();
                return;
            }
            SSLSocket open = socket;
            if (open == null) return;
            Thread closer = new Thread(() -> {
                try {
                    open.close();
                } catch (Exception ignored) {
                }
            }, "bridgelink-close");
            closer.setDaemon(true);
            closer.start();
        }
    }
}
