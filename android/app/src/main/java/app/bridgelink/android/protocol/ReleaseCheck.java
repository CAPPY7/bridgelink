package app.bridgelink.android.protocol;

/** Compares this install with the Sparkle-style appcast. The compare itself does not use the network. */
public final class ReleaseCheck {
    public static final String FEED =
            "https://github.com/CAPPY7/bridgelink/releases/latest/download/appcast.xml";
    public static final String RELEASES = "https://github.com/CAPPY7/bridgelink/releases/latest";

    private ReleaseCheck() {}

    /** 1 when remote is newer, -1 when local is newer, 0 when they match. */
    public static int compare(String local, String remote) {
        int[] a = parts(local);
        int[] b = parts(remote);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (bv != av) return bv > av ? 1 : -1;
        }
        return 0;
    }

    public static String shortVersion(String xml) {
        if (xml == null) return "";
        String key = "sparkle:shortVersionString=\"";
        int at = xml.indexOf(key);
        if (at < 0) return "";
        int start = at + key.length();
        int end = xml.indexOf('"', start);
        if (end < 0) return "";
        return xml.substring(start, end).trim();
    }

    public static boolean tagMatches(String tag, String versionName) {
        return tag != null && versionName != null && !versionName.isEmpty() && tag.equals("v" + versionName);
    }

    /** Sparkle.framework is not linked. Check for update opens the release page. */
    public static boolean installsWithSparkle() {
        return false;
    }

    private static int[] parts(String raw) {
        if (raw == null || raw.isEmpty()) return new int[] {0};
        String[] bits = raw.split("\\.");
        int[] out = new int[bits.length];
        for (int i = 0; i < bits.length; i++) {
            int n = 0;
            String bit = bits[i];
            for (int c = 0; c < bit.length(); c++) {
                char ch = bit.charAt(c);
                if (ch < '0' || ch > '9') break;
                n = n * 10 + (ch - '0');
            }
            out[i] = n;
        }
        return out;
    }
}
