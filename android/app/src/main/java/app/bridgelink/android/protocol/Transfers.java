package app.bridgelink.android.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

/** File names, link checks, and one incoming file. No Android APIs, so unit tests can run it. */
public final class Transfers {
    public static final int CHUNK = 32 * 1024;
    public static final long MAX_BYTES = 4L * 1024 * 1024 * 1024;

    private Transfers() {}

    public static String sanitizeName(String raw) {
        if (raw == null) return "file";
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
        else if (slash >= 0) name = "";
        name = name.trim();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < name.length() && out.length() < 1024) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 32 || cp == '/' || cp == '\\' || cp == ':' || rtlOverride(cp)) out.append('_');
            else out.appendCodePoint(cp);
        }
        return clipName(out.toString().trim());
    }

    static boolean rtlOverride(int cp) {
        return (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069);
    }

    static String clipName(String cleaned) {
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) return "file";
        if (cleaned.length() <= 120) return cleaned;
        int dot = cleaned.lastIndexOf('.');
        if (dot <= 0 || cleaned.length() - dot > 12) return cleaned.substring(0, 120);
        String ext = cleaned.substring(dot);
        String head = cleaned.substring(0, dot);
        int keep = 120 - ext.length();
        if (keep < 1) return cleaned.substring(0, 120);
        if (head.length() > keep) head = head.substring(0, keep);
        return head + ext;
    }

    /** Only http and https. Returns the trimmed URL, or null. */
    public static String acceptUrl(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty() || text.length() > 2000) return null;
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\u0000') >= 0) return null;
        String lower = text.toLowerCase(Locale.US);
        if (lower.startsWith("https://") || lower.startsWith("http://")) return text;
        return null;
    }

    /** A transfer id is 1 to 64 hex characters. Anything else could be a path. */
    public static boolean safeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 64) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return hex(digest);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static final class Incoming {
        public final String id;
        public final String name;
        public final long size;
        private long written;
        private boolean failed;
        private final MessageDigest digest;

        private Incoming(String id, String name, long size, MessageDigest digest) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.digest = digest;
        }

        public static Incoming begin(String id, String name, long size) {
            if (!safeId(id)) return null;
            if (size < 0 || size > MAX_BYTES) return null;
            try {
                return new Incoming(id, sanitizeName(name), size, MessageDigest.getInstance("SHA-256"));
            } catch (Exception error) {
                return null;
            }
        }

        public long written() {
            return written;
        }

        /** Hash bytes already on disk so a later chunk can continue this file. */
        public void seed(byte[] prefix) {
            if (failed || prefix == null || prefix.length == 0) return;
            if (written > size || prefix.length > size - written) {
                failed = true;
                return;
            }
            digest.update(prefix);
            written += prefix.length;
        }

        public boolean failed() {
            return failed;
        }

        /** Bytes to append, or null if this chunk must not be written. */
        public byte[] accept(long offset, String base64) {
            if (failed || offset != written) return null;
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(base64 == null ? "" : base64);
            } catch (IllegalArgumentException error) {
                failed = true;
                return null;
            }
            if (raw.length == 0 || raw.length > CHUNK || written + raw.length > size) {
                failed = true;
                return null;
            }
            digest.update(raw);
            written += raw.length;
            return raw;
        }

        public boolean matches(String sha256) {
            if (failed || written != size || sha256 == null) return false;
            try {
                byte[] actual = ((MessageDigest) digest.clone()).digest();
                return MessageDigest.isEqual(hex(actual).getBytes(StandardCharsets.UTF_8), sha256.toLowerCase(Locale.US).getBytes(StandardCharsets.UTF_8));
            } catch (CloneNotSupportedException error) {
                failed = true;
                return false;
            }
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) builder.append(String.format(Locale.US, "%02x", value));
        return builder.toString();
    }
}
