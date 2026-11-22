package app.bridgelink.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.Proof;
import app.bridgelink.android.protocol.Sas;

import java.security.cert.X509Certificate;

/**
 * Installs the app on a device, pairs with a fake Mac on this device, sends a
 * note, forgets, and pairs again with the match code. CI runs this on an API 34
 * emulator. It is not a unit test.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PairingEmulatorTest {
    private static final String PIN = "123456";
    private static final String NOTE = "emulator-note";

    @Rule
    public GrantPermissionRule notifications =
            GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS);

    private final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
    private Context target;
    private FakeMac mac;
    private BroadcastReceiver receiver;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() throws Exception {
        target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.stopService(new Intent(target, LinkService.class));
        Prefs.open(target).edit().clear().commit();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String status = intent.getStringExtra(LinkService.EXTRA_STATUS);
                if (status != null) statuses.add(status);
            }
        };
        ContextCompat.registerReceiver(
                target,
                receiver,
                new IntentFilter(LinkService.EVENT),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        mac = new FakeMac();
        mac.start();
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
        if (mac != null) mac.close();
        if (receiver != null) {
            try {
                target.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }
        target.stopService(new Intent(target, LinkService.class));
    }

    @Test(timeout = 180_000)
    public void qrPairNoteForgetThenMatchCode() throws Exception {
        String link = "bridgelink://pair?v=1&host=127.0.0.1&port=" + mac.port
                + "&fp=" + mac.fingerprint + "&pin=" + PIN;
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(link), target, MainActivity.class);
        scenario = ActivityScenario.launch(open);
        waitUntil(() -> {
            final boolean[] ready = {false};
            scenario.onActivity(activity -> ready[0] = activity.getModel().getAskPair());
            return ready[0];
        }, 20_000, "the pair question never showed");
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        if (!device.wait(Until.hasObject(By.text("Pair")), 10_000)) {
            Assert.fail("The Pair button never showed. " + dump());
        }
        device.findObject(By.text("Pair")).click();

        waitFor("Paired with the Mac", 40_000);
        String firstToken = Prefs.open(target).getString("token", "");
        Assert.assertTrue("QR pair did not save a token. " + dump(), firstToken != null && firstToken.length() >= 16);
        Assert.assertEquals("Phone saved a different token than the Mac issued. " + dump(), mac.lastToken, firstToken);

        scenario.onActivity(activity -> activity.uiSendNote(NOTE));
        waitUntil(() -> NOTE.equals(mac.lastText), 20_000, "Mac never got the note");

        int sessions = mac.finishedSessions;
        scenario.onActivity(activity -> activity.uiForget());
        waitFor("Forgot this Mac", 20_000);
        waitUntil(() -> mac.finishedSessions > sessions, 20_000, "Fake Mac did not see the socket close");
        String forgotten = Prefs.open(target).getString("token", "");
        Assert.assertTrue("Forget left the token in place. " + dump(), forgotten == null || forgotten.isEmpty());
        String fp = Prefs.open(target).getString("fp", "");
        Assert.assertTrue("Forget left the certificate pin in place. " + dump(), fp == null || fp.isEmpty());

        int pairedBefore = countOf("Paired with the Mac");
        scenario.onActivity(activity -> activity.uiConnectWithPin(""));
        waitFor("Match code ", 40_000);
        scenario.onActivity(activity -> activity.uiConfirm());
        waitUntil(() -> countOf("Paired with the Mac") > pairedBefore, 40_000, "match-code pair did not finish");

        String secondToken = Prefs.open(target).getString("token", "");
        Assert.assertTrue("Match-code pair did not save a token. " + dump(), secondToken != null && secondToken.length() >= 16);
        Assert.assertNotEquals("Second pair reused the forgotten token", firstToken, secondToken);
        Assert.assertEquals(mac.lastToken, secondToken);
    }

    private void waitFor(String part, long timeoutMs) {
        waitUntil(() -> {
            synchronized (statuses) {
                for (String status : statuses) {
                    if (status.contains(part)) return true;
                }
            }
            return false;
        }, timeoutMs, "never saw \"" + part + "\"");
    }

    private int countOf(String exact) {
        int count = 0;
        synchronized (statuses) {
            for (String status : statuses) {
                if (exact.equals(status)) count++;
            }
        }
        return count;
    }

    private void waitUntil(Check check, long timeoutMs, String why) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            try {
                if (check.ok()) return;
            } catch (Exception error) {
                Assert.fail(why + " " + error + " " + dump());
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                Assert.fail("interrupted");
            }
        }
        Assert.fail(why + ". " + dump());
    }

    private String dump() {
        return "statuses=" + statuses + " mac=" + mac.log;
    }

    private interface Check {
        boolean ok();
    }

    /** TLS stand-in for the Mac menu-bar app. Speaks the 0.4.0 pairing messages. */
    private static final class FakeMac implements AutoCloseable {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        final String fingerprint;
        final int port;
        volatile String lastToken = "";
        volatile String lastText = "";
        volatile int finishedSessions;
        private final SSLServerSocket server;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final Thread thread;

        FakeMac() throws Exception {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("fake-mac.p12")) {
                store.load(in, "android".toCharArray());
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, "android".toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            server = (SSLServerSocket) context.getServerSocketFactory()
                    .createServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            port = server.getLocalPort();
            fingerprint = fingerprintOf((X509Certificate) store.getCertificate("mac"));
            thread = new Thread(this::acceptLoop, "fake-mac");
            thread.setDaemon(true);
        }

        void start() {
            thread.start();
            note("listening " + port);
        }

        @Override
        public void close() {
            open.set(false);
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }

        private void acceptLoop() {
            while (open.get()) {
                try {
                    SSLSocket socket = (SSLSocket) server.accept();
                    socket.setSoTimeout(90_000);
                    try {
                        session(socket);
                    } finally {
                        finishedSessions++;
                        try {
                            socket.close();
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception error) {
                    if (open.get()) note("accept " + error.getMessage());
                }
            }
        }

        private void session(SSLSocket socket) throws Exception {
            socket.startHandshake();
            note("handshake");
            Frame.Parser parser = new Frame.Parser();
            byte[] buffer = new byte[8192];
            String phoneCommit = "";
            String phoneNonce = "";
            String macNonce = randomHex(16);
            InputStream in = socket.getInputStream();
            int count;
            while (open.get() && (count = in.read(buffer)) >= 0) {
                if (count == 0) continue;
                for (Frame frame : parser.push(java.util.Arrays.copyOf(buffer, count))) {
                    if (frame.type != Frame.JSON) continue;
                    Envelope message = Envelope.parse(new String(frame.payload, StandardCharsets.UTF_8));
                    note(message.type);
                    switch (message.type) {
                        case "hello" -> {
                            phoneCommit = message.text("commit");
                            onHello(socket, message, macNonce, phoneCommit);
                        }
                        case "sas_reveal" -> {
                            phoneNonce = message.text("nonce");
                            if (!Sas.revealMatches(phoneCommit, phoneNonce)) {
                                sendError(socket, "bad_code", "Reveal did not match the commit");
                                return;
                            }
                            JsonObject reveal = new JsonObject();
                            reveal.addProperty("nonce", macNonce);
                            write(socket, Envelope.make("sas_reveal", "mac", reveal));
                        }
                        case "sas_confirm" -> {
                            String expected = Sas.code(fingerprint, phoneNonce, macNonce);
                            if (!expected.equals(message.text("code"))) {
                                sendError(socket, "bad_code", "Match codes differ");
                                return;
                            }
                            sendPaired(socket);
                        }
                        case "text" -> lastText = message.text("body");
                        case "ping" -> write(socket, Envelope.make("pong", "mac", new JsonObject()));
                        case "forget" -> note("forget");
                        default -> {
                        }
                    }
                }
            }
        }

        private void onHello(SSLSocket socket, Envelope message, String macNonce, String commit) throws Exception {
            String proof = message.text("proof");
            String token = message.text("token");
            if (!proof.isEmpty()) {
                if (!Proof.matches(PIN, fingerprint, proof)) {
                    sendError(socket, "bad_proof", "Wrong PIN");
                    return;
                }
                sendPaired(socket);
                return;
            }
            if (!token.isEmpty()) {
                if (!token.equals(lastToken)) {
                    sendError(socket, "bad_token", "Unknown phone");
                    return;
                }
                sendPaired(socket);
                return;
            }
            if (commit.isEmpty()) {
                sendError(socket, "bad_proof", "Missing proof");
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("commit", Sas.commit(macNonce));
            write(socket, Envelope.make("sas", "mac", payload));
        }

        private void sendPaired(SSLSocket socket) throws Exception {
            String token = randomHex(32);
            JsonObject payload = new JsonObject();
            payload.addProperty("token", token);
            payload.addProperty("deviceId", UUID.randomUUID().toString());
            write(socket, Envelope.make("paired", "mac", payload));
            lastToken = token;
            note("paired");
        }

        private void sendError(SSLSocket socket, String code, String body) throws Exception {
            JsonObject payload = new JsonObject();
            payload.addProperty("code", code);
            payload.addProperty("body", body);
            write(socket, Envelope.make("error", "mac", payload));
            note("error " + code);
        }

        private static void write(SSLSocket socket, Envelope envelope) throws Exception {
            byte[] frame = Frame.encode(Frame.JSON, envelope.toJsonString().getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().write(frame);
            socket.getOutputStream().flush();
        }

        private static String fingerprintOf(X509Certificate certificate) throws Exception {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        }

        private static String randomHex(int bytes) {
            byte[] raw = new byte[bytes];
            new SecureRandom().nextBytes(raw);
            StringBuilder builder = new StringBuilder(raw.length * 2);
            for (byte value : raw) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        }

        private void note(String line) {
            log.add(line);
            android.util.Log.i("FakeMac", line);
        }
    }
}
