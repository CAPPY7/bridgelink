package app.bridgelink.android.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Away-from-home rules the phone, the Mac, and the relay all share.
 * The relay may see the ticket key. It never sees the frame keys.
 */
public final class RelayRules {
    public static final int MAX_MESSAGE = 512 * 1024;
    public static final long TICKET_WINDOW_SEC = 120;

    private RelayRules() {}

    public static byte[] phoneToMac(String secret) {
        return direction(secret, "phone-to-mac");
    }

    public static byte[] macToPhone(String secret) {
        return direction(secret, "mac-to-phone");
    }

    public static String ticketKey(String secret) {
        if (secret == null || secret.length() < 16) return "";
        return Features.echoKey(secret + "\nrelay-ticket");
    }

    /** Hello the relay can check. Role, unix time, and a short mac. The key is last. */
    public static String hello(String secret, String role, long epochSeconds) {
        String key = ticketKey(secret);
        if (key.isEmpty()) return "";
        String who = "mac".equals(role) ? "mac" : "phone";
        String mac = helloMac(key, who, epochSeconds);
        return who + " " + epochSeconds + " " + mac + " " + key;
    }

    public static String helloMac(String ticketKey, String role, long epochSeconds) {
        return Features.echoKey(ticketKey + "\n" + role + "\n" + epochSeconds).substring(0, 32);
    }

    public static boolean ticketOk(String hello, long nowSeconds) {
        if (hello == null) return false;
        String[] bits = hello.trim().split(" ");
        if (bits.length != 4) return false;
        if (!"phone".equals(bits[0]) && !"mac".equals(bits[0])) return false;
        long stamp;
        try {
            stamp = Long.parseLong(bits[1]);
        } catch (NumberFormatException error) {
            return false;
        }
        if (Math.abs(nowSeconds - stamp) > TICKET_WINDOW_SEC) return false;
        if (bits[3].length() < 32) return false;
        String expect = helloMac(bits[3], bits[0], stamp);
        return expect.equals(bits[2]);
    }

    public static boolean fits(int bytes) {
        return bytes > 0 && bytes <= MAX_MESSAGE;
    }

    private static byte[] direction(String secret, String info) {
        byte[] ikm = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
        return Features.hkdf(ikm, "bridgelink-e2e-v1".getBytes(StandardCharsets.UTF_8), info.getBytes(StandardCharsets.UTF_8), 32);
    }

    /** One counter per session. A restart on the other side starts a new session. */
    public static final class Replay {
        public long session;
        public long seen;
        public boolean armed;

        public boolean accept(long sessionId, long counter) {
            if (!armed || sessionId != session) {
                armed = true;
                session = sessionId;
                seen = 0;
            }
            if (counter <= seen) return false;
            seen = counter;
            return true;
        }
    }

    /** In-memory relay. A live slot is replaced only by a fresh ticket with the same key. */
    public static final class Hub {
        public String key = "";
        public byte[] phone;
        public byte[] mac;
        public int dropped;

        public boolean join(String role, String helloLine, long now, byte[] socket) {
            if (!ticketOk(helloLine, now)) return false;
            String offered = helloLine.trim().split(" ")[3];
            boolean live = ("phone".equals(role) ? phone : mac) != null;
            if (!key.isEmpty() && live && !key.equals(offered)) {
                dropped++;
                return false;
            }
            if (key.isEmpty() || (!live && phone == null && mac == null)) key = offered;
            if (!key.equals(offered)) {
                dropped++;
                return false;
            }
            if ("mac".equals(role)) mac = socket;
            else phone = socket;
            return true;
        }

        public byte[] forward(String fromRole, byte[] frame) {
            if (frame == null || !fits(frame.length)) {
                dropped++;
                return null;
            }
            return "phone".equals(fromRole) ? (mac == null ? null : Arrays.copyOf(frame, frame.length))
                    : (phone == null ? null : Arrays.copyOf(frame, frame.length));
        }
    }

    public static boolean knownPhone(String token, String[] paired, String[] forgotten) {
        if (token == null || token.length() < 16) return false;
        if (forgotten != null) {
            for (String gone : forgotten) {
                if (token.equals(gone)) return false;
            }
        }
        if (paired == null) return false;
        for (String kept : paired) {
            if (token.equals(kept)) return true;
        }
        return false;
    }
}
