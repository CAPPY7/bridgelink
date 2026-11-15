package app.bridgelink.android.protocol;

/** The Mac asks Music or Spotify only while the phone's Remote page is open. */
public final class MediaWatch {
    private MediaWatch() {
    }

    public static String[] command(String raw) {
        if (raw == null) return null;
        String action = raw.trim();
        if ("watch".equals(action) || "idle".equals(action)) return new String[]{action, ""};
        return null;
    }

    public static boolean shouldPoll(boolean screenOpen, boolean paired, boolean busy) {
        return screenOpen && paired && !busy;
    }
}
