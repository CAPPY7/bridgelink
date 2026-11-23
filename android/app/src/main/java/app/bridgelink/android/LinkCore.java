package app.bridgelink.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;

import app.bridgelink.android.protocol.BrowseAgain;
import app.bridgelink.android.protocol.Dedup;
import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.MediaQueue;
import app.bridgelink.android.protocol.MediaWatch;
import app.bridgelink.android.protocol.StageFour;
import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.FrameChannel;
import app.bridgelink.android.protocol.HelloPlan;
import app.bridgelink.android.protocol.HostMerge;
import app.bridgelink.android.protocol.LinkFailure;
import app.bridgelink.android.protocol.LinkState;
import app.bridgelink.android.protocol.MacBook;
import app.bridgelink.android.protocol.MacList;
import app.bridgelink.android.protocol.Outbox;
import app.bridgelink.android.protocol.PairGate;
import app.bridgelink.android.protocol.ServiceDoor;

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

class LinkCore extends Service {
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

    static final String CHANNEL = "link";
    static final String RING_CHANNEL = "find";
    static final int NOTE_ID = 1;
    static final int RING_ID = 2;

    private final IBinder binder = new LocalBinder();
    final Outbox outbox = new Outbox();
    private final MediaQueue media = new MediaQueue();
    private final AtomicBoolean mediaPumping = new AtomicBoolean();
    private static volatile Runnable needKey;
    final AtomicInteger generation = new AtomicInteger();
    final Dedup seen = new Dedup(500);
    final ConcurrentHashMap<String, Long> fileHave = new ConcurrentHashMap<>();
    final Handler main = new Handler(Looper.getMainLooper());
    private static final Object PARK = new Object();
    private static final ArrayDeque<Intent> parked = new ArrayDeque<>();
    static volatile LinkCore running;
    final LinkDesk desk = new LinkDesk(this);
    private final LinkInbox inbox = new LinkInbox(this);

    private volatile Attempt attempt;
    volatile boolean paired;
    volatile String lastHost = "";
    volatile int lastPort = 8787;
    final LinkState linkState = new LinkState();
    FileBridge files;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver batteryWatch;
    private final List<String> pendingHosts = new ArrayList<>();
    volatile long lastRx;
    volatile String sessionFp = "";
    volatile String matchCode = "";
    final AtomicBoolean fileCancel = new AtomicBoolean();
    volatile String sasPhoneNonce = "";
    volatile String macCommit = "";
    LeaveWatch leave;
    PhoneFive five;
    PhoneSix six;
    private MacBrowser browser;
    final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (!paired) return;
            if (lastRx > 0 && System.currentTimeMillis() - lastRx > 45_000) {
                closeSocket();
                return;
            }
            send("ping", "");
            if (leave != null) leave.tick();
            desk.sendNowPlaying();
            main.postDelayed(this, 20_000);
        }
    };

    public class LocalBinder extends Binder {
        public LinkCore getService() {
            return LinkCore.this;
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

            @Override
            public long have(String id) {
                Long value = fileHave.get(id);
                return value == null ? -1 : value;
            }

            @Override
            public boolean linkedNow() {
                return paired;
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
        });
        running = this;
        watchNetwork();
        batteryWatch = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (paired) desk.sendBattery();
            }
        };
        registerReceiver(batteryWatch, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        desk.watchClipboard();
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
            if ("file".equals(kind)) desk.sendFile(intent.getStringExtra(EXTRA_BODY));
            else if ("files".equals(kind)) desk.sendFiles(intent.getStringExtra(EXTRA_BODY));
            else if ("link".equals(kind)) desk.sendLink(intent.getStringExtra(EXTRA_BODY));
            else if ("notify".equals(kind)) desk.sendNotify(intent);
            else if ("notify_dismiss".equals(kind)) desk.sendDismiss(intent.getStringExtra("key"));
            else if ("otp".equals(kind)) desk.sendOtp(intent.getStringExtra(EXTRA_BODY));
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
            desk.stopRing();
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
        LinkCore current = running;
        ServiceDoor.Result result = ServiceDoor.deliver(current != null, () -> {
            LinkCore live = running;
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
        if (five != null) five.onUnlinked();
        desk.stopRing();
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
        linkState.stop();
        generation.incrementAndGet();
        if (running == this) running = null;
        closeSocket();
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
        markUnpaired();
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
            String book = prefs.getString("macTokens", "");
            app.bridgelink.android.protocol.MacSwitch choice =
                    app.bridgelink.android.protocol.MacSwitch.choose(pinned, savedToken, book, seen);
            if (choice.switched) {
                pinned = choice.fingerprint;
                savedToken = choice.token;
                prefs.edit().putString("fp", pinned).putString("token", savedToken).commit();
            }
            byte[] nonceRaw = new byte[16];
            new SecureRandom().nextBytes(nonceRaw);
            StringBuilder nonce = new StringBuilder(nonceRaw.length * 2);
            for (byte value : nonceRaw) nonce.append(String.format("%02x", value));
            sasPhoneNonce = nonce.toString();
            HelloPlan plan = HelloPlan.decide(Build.MODEL, code, PairGate.helloToken(pinned, savedToken, seen), pinned, seen, nonce.toString());
            if (plan.kind != null) {
                next.close();
                linkState.failFatal(plan.fatal);
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
                        if (kind == LinkFailure.Kind.TOKEN_REJECTED) {
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
                markUnpaired();
                String next = mine.hopTo;
                if (next != null && !next.isEmpty()) {
                    main.post(() -> openAttempt(next, port, code));
                } else if (linkState.holding(System.currentTimeMillis()) && !address.isEmpty()) {
                    long delay = Math.max(0, linkState.holdUntil - System.currentTimeMillis());
                    publish("Another phone is connected. This phone will wait 5 minutes.", null);
                    main.postDelayed(() -> connect(address, port, code), delay);
                } else if (linkState.afterClose(userStopped, tokenSaved) && !address.isEmpty()) {
                    long delay = linkState.delayMs();
                    publish("Reconnecting…", null);
                    if (BrowseAgain.shouldBrowse(linkState.attempt)) browseSaved();
                    main.postDelayed(() -> connect(address, port, code), delay);
                } else if (!userStopped && linkState.phase != LinkState.Phase.FATAL) {
                    publish("Disconnected", null);
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
                String saved = Prefs.open(LinkCore.this).getString("hosts", "");
                Prefs.open(LinkCore.this).edit().putString("host", host).putString("hosts", HostMerge.merge(saved, host)).apply();
                lastHost = host;
                publish("Found the Mac at " + host, null);
                main.post(() -> connect(host, lastPort, ""));
            }

            @Override
            public void onStatus(String status) {
                if (status != null) publish(status, null);
            }

            @Override
            public void onStopped() {
                browser = null;
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
        inbox.onLine(line);
    }

    void markUnpaired() {
        paired = false;
        if (five != null) five.onUnlinked();
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
        String fp = prefs.getString("fp", "");
        if (fp == null || fp.isEmpty()) fp = sessionFp == null ? "" : sessionFp;
        String macs = MacList.removeHost(prefs.getString("macs", "[]"), lastHost);
        String tokens = app.bridgelink.android.protocol.MacSwitch.forget(prefs.getString("macTokens", ""), fp);
        prefs.edit().remove("token").remove("fp").remove("pendingFp").remove("deviceId").remove("pin")
                .putString("macTokens", tokens)
                .putString("macs", macs).commit();
        linkState.stop();
        generation.incrementAndGet();
        closeSocket();
        markUnpaired();
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
                    desk.sendClipboardText(text);
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


    private static String safe(String value) {
        return value == null ? "" : value;
    }


    void writeEnvelope(Envelope envelope) throws Exception {
        writeRaw(Frame.encode(Frame.JSON, envelope.toJsonString().getBytes(StandardCharsets.UTF_8)));
    }

    void writeRaw(byte[] framed) throws Exception {
        Attempt current = attempt;
        if (current != null && current.channel != null && !current.closed) {
            current.channel.write(framed);
            return;
        }
        throw new IOException("not connected");
    }

    public static void onNeedKey(Runnable ask) {
        needKey = ask;
    }

    public static void sendRaw(byte[] framed) {
        LinkCore live = running;
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
        LinkCore live = running;
        if (live != null && line != null) live.publish(line, null);
    }

    void closeSocket() {
        Attempt current = attempt;
        if (current != null) current.close();
    }


    void publish(String status, String line) {
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
        Intent clip = new Intent(this, ClipSendActivity.class);
        clip.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        PendingIntent clipPending = PendingIntent.getActivity(
                this, 6, clip, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Send clipboard", clipPending).build())
                .build();
    }

    NotificationManager manager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private void watchNetwork() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null || networkCallback != null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                SharedPreferences prefs = Prefs.open(LinkCore.this);
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
                    boolean wifiLinked = paired;
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
