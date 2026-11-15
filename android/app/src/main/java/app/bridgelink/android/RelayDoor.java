package app.bridgelink.android;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.RelayRules;
import app.bridgelink.android.protocol.StageSix;

/** WebSocket to the free relay. Only locked frames leave this phone. */
public final class RelayDoor {
    public interface Sink {
        void frames(List<Frame> frames);

        void up();

        void down(String reason);
    }

    private final AtomicBoolean live = new AtomicBoolean();
    private final AtomicLong counter = new AtomicLong();
    private final SecureRandom random = new SecureRandom();
    private volatile SSLSocket socket;
    private volatile OutputStream out;
    private byte[] sendKey = new byte[0];
    private byte[] recvKey = new byte[0];
    private long sessionId;
    private final RelayRules.Replay replay = new RelayRules.Replay();

    public boolean up() {
        return live.get();
    }

    public void start(String url, String secret, Sink sink) {
        stop();
        String[] parts = StageSix.relayParts(url);
        String room = StageSix.room(secret);
        String ticket = RelayRules.hello(secret, "phone", System.currentTimeMillis() / 1000);
        if (parts == null || room.isEmpty() || ticket.isEmpty()) {
            sink.down("Pair on the same Wi-Fi once, and save a wss relay address.");
            return;
        }
        sendKey = RelayRules.phoneToMac(secret);
        recvKey = RelayRules.macToPhone(secret);
        sessionId = random.nextLong();
        if (sessionId == 0) sessionId = 1;
        replay.armed = false;
        replay.seen = 0;
        Thread thread = new Thread(() -> run(parts, room, ticket, sink), "bridgelink-relay");
        thread.start();
    }

    public void stop() {
        live.set(false);
        SSLSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean send(byte[] framed) {
        if (!live.get() || framed == null) return false;
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            long count = counter.incrementAndGet();
            byte[] sealed = Features.seal(sendKey, count, nonce, framed);
            byte[] packet = new byte[8 + sealed.length];
            java.nio.ByteBuffer.wrap(packet).putLong(sessionId);
            System.arraycopy(sealed, 0, packet, 8, sealed.length);
            if (!RelayRules.fits(packet.length)) return false;
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            byte[] frame = StageSix.wsFrame(2, packet, mask);
            OutputStream stream = out;
            if (stream == null) return false;
            stream.write(frame);
            stream.flush();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void run(String[] parts, String room, String ticket, Sink sink) {
        try {
            SSLSocket next = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            next.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 8000);
            next.startHandshake();
            socket = next;
            OutputStream stream = next.getOutputStream();
            out = stream;
            String path = parts[2];
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String target = path + "/room/" + room;
            byte[] keyRaw = new byte[16];
            random.nextBytes(keyRaw);
            String wsKey = java.util.Base64.getEncoder().encodeToString(keyRaw);
            String hello = "GET " + target + " HTTP/1.1\r\n"
                    + "Host: " + parts[0] + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + wsKey + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            stream.write(hello.getBytes(StandardCharsets.US_ASCII));
            stream.flush();
            InputStream input = next.getInputStream();
            if (!accepted(input)) {
                sink.down("The relay refused the connection.");
                stop();
                return;
            }
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            byte[] greet = StageSix.wsFrame(1, ticket.getBytes(StandardCharsets.US_ASCII), mask);
            stream.write(greet);
            stream.flush();
            live.set(true);
            sink.up();
            readLoop(input, sink);
        } catch (Exception error) {
            sink.down("The away relay could not be reached.");
        } finally {
            live.set(false);
        }
    }

    private void readLoop(InputStream input, Sink sink) throws Exception {
        Frame.Parser parser = new Frame.Parser();
        byte[] buf = new byte[0];
        byte[] scratch = new byte[8192];
        while (live.get()) {
            int n = input.read(scratch);
            if (n < 0) break;
            buf = join(buf, scratch, n);
            while (true) {
                int size = StageSix.wsSize(buf, buf.length);
                if (size == 0) return;
                if (size < 0) break;
                byte[] frame = Arrays.copyOf(buf, size);
                buf = Arrays.copyOfRange(buf, size, buf.length);
                byte[] payload = StageSix.wsPayload(frame);
                if (payload == null) return;
                if ((frame[0] & 0x0f) != 2) continue;
                if (payload.length < 8 + 12 + 8 + 16) continue;
                long session = java.nio.ByteBuffer.wrap(payload, 0, 8).getLong();
                byte[] sealed = Arrays.copyOfRange(payload, 8, payload.length);
                byte[] opened;
                try {
                    opened = Features.open(recvKey, sealed);
                } catch (Exception rejected) {
                    continue;
                }
                long count = Features.counterOf(sealed);
                if (!replay.accept(session, count)) continue;
                List<Frame> frames = parser.push(opened);
                if (!frames.isEmpty()) sink.frames(frames);
            }
        }
    }

    private static boolean accepted(InputStream input) throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        while (header.size() < 8192) {
            int value = input.read();
            if (value < 0) return false;
            header.write(value);
            byte[] all = header.toByteArray();
            if (all.length >= 4
                    && all[all.length - 4] == '\r'
                    && all[all.length - 3] == '\n'
                    && all[all.length - 2] == '\r'
                    && all[all.length - 1] == '\n') {
                String text = new String(header.toByteArray(), StandardCharsets.US_ASCII);
                return text.startsWith("HTTP/1.1 101");
            }
        }
        return false;
    }

    private static byte[] join(byte[] left, byte[] right, int count) {
        byte[] out = Arrays.copyOf(left, left.length + count);
        System.arraycopy(right, 0, out, left.length, count);
        return out;
    }
}
