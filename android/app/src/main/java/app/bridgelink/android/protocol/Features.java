package app.bridgelink.android.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure rules for every feature. No Android APIs, so unit tests can run them
 * and the Mac port can match the same decisions.
 */
public final class Features {
    public static final String[] NAMES = {
            "files", "links", "text", "clipboard", "find", "battery", "media",
            "notify", "otp", "sms", "inbox", "clips", "scan", "auto", "folder",
            "input", "mirror", "control", "camera", "relay", "lock"
    };

    private static final Pattern CODE = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)");
    private static final String[] KEYWORDS = {
            "one-time", "one time", "onetime", "verification", "passcode", "password", "otp", "pin", "code"
    };

    private Features() {}

    /** Features both sides announced. Order follows the phone list. */
    public static List<String> shared(List<String> local, List<String> remote) {
        LinkedHashSet<String> other = new LinkedHashSet<>();
        if (remote != null) other.addAll(remote);
        List<String> both = new ArrayList<>();
        if (local == null) return both;
        for (String name : local) {
            if (other.contains(name) && !both.contains(name)) both.add(name);
        }
        return both;
    }

    /** Private features stay off until the settings string turns them on. */
    public static boolean enabled(String settings, String feature, boolean fallback) {
        if (feature == null || feature.isEmpty()) return false;
        if (settings == null || settings.isEmpty()) return fallback;
        for (String part : settings.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            if (feature.equals(part.substring(0, eq).trim()) && "1".equals(part.substring(eq + 1).trim())) return true;
            if (feature.equals(part.substring(0, eq).trim()) && "0".equals(part.substring(eq + 1).trim())) return false;
        }
        return fallback;
    }

    public static boolean privateByDefault(String feature) {
        return "clipboard".equals(feature) || "notify".equals(feature) || "otp".equals(feature)
                || "sms".equals(feature) || "clips".equals(feature) || "folder".equals(feature)
                || "input".equals(feature) || "mirror".equals(feature) || "control".equals(feature)
                || "camera".equals(feature) || "lock".equals(feature);
    }

    /** Bytes already stored. -1 means the offer is impossible. */
    public static long resumeFrom(long have, long size) {
        if (size < 0 || size > Transfers.MAX_BYTES || have < 0 || have > size) return -1;
        return have;
    }

    public static boolean looksSensitive(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("password") || lower.contains("passcode")) return true;
        StringBuilder compact = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) compact.append(c);
        }
        if (compact.length() >= 6 && compact.length() <= 8) {
            boolean digits = true;
            for (int i = 0; i < compact.length(); i++) {
                if (!Character.isDigit(compact.charAt(i))) digits = false;
            }
            if (digits) return true;
        }
        return card(text) || otp(text) != null;
    }

    /** SHA-256 of the text. Used so a received clipboard is not sent back. */
    public static String echoKey(String text) {
        return Transfers.sha256Hex((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isEcho(String lastKey, String text) {
        return lastKey != null && !lastKey.isEmpty() && lastKey.equals(echoKey(text));
    }

    /** A 4 to 8 digit code next to an OTP word, or null. Prices and dates are ignored. */
    public static String otp(String message) {
        if (message == null || message.isEmpty() || message.length() > 4000) return null;
        String lower = message.toLowerCase(Locale.US);
        List<Integer> keys = new ArrayList<>();
        for (String word : KEYWORDS) {
            int from = 0;
            while (from < lower.length()) {
                int at = lower.indexOf(word, from);
                if (at < 0) break;
                if (wordEdge(lower, at, word.length())) keys.add(at);
                from = at + word.length();
            }
        }
        if (keys.isEmpty()) return null;
        Matcher matcher = CODE.matcher(message);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            if (isPrice(lower, start) || isDate(message, start, end)) continue;
            int dist = Integer.MAX_VALUE;
            for (int key : keys) dist = Math.min(dist, Math.abs(key - start));
            if (dist <= 40 && dist < bestDist) {
                bestDist = dist;
                best = matcher.group(1);
            }
        }
        return best;
    }

    public static boolean batteryChanged(int previousLevel, boolean previousCharging, boolean hadSample,
                                         int level, boolean charging) {
        if (level < 0 || level > 100) return false;
        if (!hadSample) return true;
        return level != previousLevel || charging != previousCharging;
    }

    public static boolean lowBattery(int level, boolean charging, boolean alreadyAlerted) {
        return level >= 0 && level <= 15 && !charging && !alreadyAlerted;
    }

    public static String mediaAction(String raw) {
        if (raw == null) return null;
        return switch (raw) {
            case "play", "pause", "next", "previous", "seek", "volume" -> raw;
            default -> null;
        };
    }

    public static boolean pointerKind(String kind) {
        return "move".equals(kind) || "tap".equals(kind) || "right".equals(kind)
                || "scroll".equals(kind) || "drag".equals(kind) || "key".equals(kind);
    }

    /** GSM 160 / Unicode 70, with the multipart limit once the text needs more than one part. */
    public static List<String> smsParts(String text) {
        if (text == null || text.isEmpty()) return List.of();
        boolean gsm = gsm(text);
        int single = gsm ? 160 : 70;
        if (text.length() <= single) return List.of(text);
        int chunk = gsm ? 153 : 67;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunk) {
            parts.add(text.substring(i, Math.min(text.length(), i + chunk)));
        }
        return parts;
    }

    public static String scanName(long epochMs) {
        if (epochMs < 0) epochMs = 0;
        java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMs);
        String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(instant);
        return "scan-" + stamp + ".pdf";
    }

    public static boolean triggerMatches(String rule, String event, boolean enabled) {
        return enabled && rule != null && rule.equals(event);
    }

    public static List<String> unseen(List<String> already, List<String> current) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (already != null) seen.addAll(already);
        List<String> fresh = new ArrayList<>();
        if (current == null) return fresh;
        for (String id : current) {
            if (id == null || id.isEmpty() || seen.contains(id)) continue;
            fresh.add(id);
        }
        return fresh;
    }

    /** Relative path inside a shared folder. Null means a path trick. */
    public static String safeRelative(String raw) {
        if (raw == null) return null;
        String path = raw.replace('\\', '/').trim();
        if (path.isEmpty() || path.length() > 240 || path.indexOf('\u0000') >= 0) return null;
        if (path.startsWith("/") || path.contains(":")) return null;
        String[] parts = path.split("/");
        List<String> keep = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) return null;
            keep.add(Transfers.sanitizeName(part));
        }
        if (keep.isEmpty()) return null;
        return String.join("/", keep);
    }

    public static boolean shouldMirror(boolean ongoing, boolean silent, boolean appAllowed, boolean duplicate) {
        return !ongoing && !silent && appAllowed && !duplicate;
    }

    public static String roomId(String pairingSecret) {
        if (pairingSecret == null || pairingSecret.length() < 16) return "";
        return Transfers.sha256Hex(pairingSecret.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    public static byte[] deriveKey(String pairingSecret) {
        byte[] ikm = (pairingSecret == null ? "" : pairingSecret).getBytes(StandardCharsets.UTF_8);
        return hkdf(ikm, "bridgelink-e2e-v1".getBytes(StandardCharsets.UTF_8), "key".getBytes(StandardCharsets.UTF_8), 32);
    }

    public static byte[] seal(byte[] key, long counter, byte[] nonce, byte[] plaintext) throws Exception {
        if (key == null || key.length != 32 || nonce == null || nonce.length != 12 || plaintext == null) {
            throw new IllegalArgumentException("Bad seal");
        }
        if (counter < 0) throw new IllegalArgumentException("Bad counter");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(ByteBuffer.allocate(8).putLong(counter).array());
        byte[] body = cipher.doFinal(plaintext);
        ByteBuffer out = ByteBuffer.allocate(12 + 8 + body.length);
        out.put(nonce);
        out.putLong(counter);
        out.put(body);
        return out.array();
    }

    public static byte[] open(byte[] key, byte[] sealed) throws Exception {
        if (key == null || key.length != 32 || sealed == null || sealed.length < 12 + 8 + 16) {
            throw new IllegalArgumentException("Bad seal");
        }
        ByteBuffer in = ByteBuffer.wrap(sealed);
        byte[] nonce = new byte[12];
        in.get(nonce);
        long counter = in.getLong();
        byte[] body = new byte[in.remaining()];
        in.get(body);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(ByteBuffer.allocate(8).putLong(counter).array());
        return cipher.doFinal(body);
    }

    public static long counterOf(byte[] sealed) {
        if (sealed == null || sealed.length < 20) return -1;
        return ByteBuffer.wrap(sealed, 12, 8).getLong();
    }

    /** 4 byte id, 8 byte offset, 1 flag byte, 4 byte payload length. No media bytes. */
    public static byte[] streamHeader(int streamId, long offset, int flags, int length) {
        if (streamId < 0 || offset < 0 || length < 0 || length > 64 * 1024) {
            throw new IllegalArgumentException("Bad stream");
        }
        ByteBuffer out = ByteBuffer.allocate(17);
        out.putInt(streamId);
        out.putLong(offset);
        out.put((byte) (flags & 0xff));
        out.putInt(length);
        return out.array();
    }

    public static int streamLength(byte[] header) {
        if (header == null || header.length != 17) return -1;
        int length = ByteBuffer.wrap(header, 13, 4).getInt();
        if (length < 0 || length > 64 * 1024) return -1;
        return length;
    }

    public static final class RateLimit {
        private final int max;
        private final long windowMs;
        private long windowStart = Long.MIN_VALUE;
        private int count;

        public RateLimit(int max, long windowMs) {
            this.max = max;
            this.windowMs = windowMs;
        }

        public synchronized boolean allow(long now) {
            if (windowStart == Long.MIN_VALUE || now - windowStart >= windowMs) {
                windowStart = now;
                count = 0;
            }
            if (count >= max) return false;
            count++;
            return true;
        }
    }

    /** Lock only after the phone stays weak for the grace period. A stronger signal clears it. */
    public static final class LeaveLock {
        public final int weakRssi;
        public final long graceMs;
        private long weakSince;
        private boolean away;

        public LeaveLock(int weakRssi, long graceMs) {
            this.weakRssi = weakRssi;
            this.graceMs = graceMs;
        }

        public boolean observe(boolean connected, int rssi, long now) {
            boolean strong = connected && rssi >= weakRssi + 8;
            boolean weak = !connected || rssi < weakRssi;
            if (strong || (!weak && !away)) {
                weakSince = 0;
                return false;
            }
            if (!weak) return false;
            if (weakSince == 0) weakSince = now;
            if (!away && now - weakSince >= graceMs) {
                away = true;
                return true;
            }
            return false;
        }

        public boolean away() {
            return away;
        }
    }

    /** Last 50 clipboard items. Sensitive text and OTPs are refused. */
    public static final class ClipHistory {
        public static final int MAX = 50;
        private final List<Item> items = new ArrayList<>();

        public static final class Item {
            public final String id;
            public final String text;
            public boolean pinned;

            Item(String id, String text) {
                this.id = id;
                this.text = text;
            }
        }

        public boolean add(String id, String text) {
            if (id == null || id.isEmpty() || text == null || text.isEmpty()) return false;
            if (looksSensitive(text)) return false;
            items.removeIf(item -> item.text.equals(text) || item.id.equals(id));
            items.add(0, new Item(id, text));
            for (int i = items.size() - 1; i >= 0 && items.size() > MAX; i--) {
                if (!items.get(i).pinned) items.remove(i);
            }
            for (Item item : items) {
                if (item.id.equals(id)) return true;
            }
            return false;
        }

        public List<String> search(String query) {
            String needle = query == null ? "" : query.toLowerCase(Locale.US);
            List<String> found = new ArrayList<>();
            for (Item item : items) {
                if (needle.isEmpty() || item.text.toLowerCase(Locale.US).contains(needle)) found.add(item.text);
            }
            return found;
        }

        public boolean pin(String id) {
            for (Item item : items) {
                if (item.id.equals(id)) {
                    item.pinned = true;
                    return true;
                }
            }
            return false;
        }

        public boolean delete(String id) {
            return items.removeIf(item -> item.id.equals(id));
        }

        public int size() {
            return items.size();
        }
    }

    public static final class Replay {
        private long highest = -1;

        public boolean accept(long counter) {
            if (counter <= highest) return false;
            highest = counter;
            return true;
        }
    }

    private static boolean wordEdge(String text, int at, int length) {
        if (at > 0 && Character.isLetter(text.charAt(at - 1))) return false;
        int after = at + length;
        return after >= text.length() || !Character.isLetter(text.charAt(after));
    }

    private static boolean isPrice(String lower, int start) {
        int i = start - 1;
        while (i >= 0 && (lower.charAt(i) == ' ' || lower.charAt(i) == ',' || lower.charAt(i) == '.')) i--;
        if (i >= 0) {
            char c = lower.charAt(i);
            if (c == '$' || c == '₹' || c == '€' || c == '£') return true;
        }
        int from = Math.max(0, start - 4);
        String pre = lower.substring(from, start);
        return pre.contains("rs") || pre.contains("inr") || pre.contains("usd");
    }

    private static boolean isDate(String text, int start, int end) {
        if (separatedNumber(text, start, -1)) return true;
        return separatedNumber(text, end, 1);
    }

    /** A slash or dash counts as a date only when the other side is also a number. */
    private static boolean separatedNumber(String text, int edge, int direction) {
        int mark = direction < 0 ? edge - 1 : edge;
        if (mark < 0 || mark >= text.length()) return false;
        char sep = text.charAt(mark);
        if (sep != '/' && sep != '-') return false;
        int i = mark + direction;
        while (i >= 0 && i < text.length() && text.charAt(i) == ' ') i += direction;
        return i >= 0 && i < text.length() && Character.isDigit(text.charAt(i));
    }

    private static boolean gsm(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') continue;
            if (c < 32 || c > 126) return false;
            if ("{}[]~|^\\".indexOf(c) >= 0) return false;
        }
        return true;
    }

    private static boolean card(String text) {
        Matcher matcher = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)").matcher(text);
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[ -]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) return true;
        }
        return false;
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            byte[] okm = new byte[length];
            byte[] block = new byte[0];
            int offset = 0;
            int counter = 1;
            while (offset < length) {
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(block);
                mac.update(info);
                mac.update((byte) counter);
                block = mac.doFinal();
                int take = Math.min(block.length, length - offset);
                System.arraycopy(block, 0, okm, offset, take);
                offset += take;
                counter++;
            }
            return okm;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    /** Used by the fuzz test so a hash of random bytes stays available without logging them. */
    public static int digestLength(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data == null ? new byte[0] : data).length;
        } catch (Exception error) {
            return 0;
        }
    }

    public static List<String> names() {
        return Collections.unmodifiableList(Arrays.asList(NAMES));
    }
}
