package app.bridgelink.android.protocol;

/** One phone stays connected. A different phone waits instead of kicking it off. */
public final class SecondPhone {
    public static final long HOLD_MS = 5 * 60 * 1000L;

    private SecondPhone() {
    }

    public static boolean allow(String currentId, String incomingId, boolean hasLive) {
        if (!hasLive) return true;
        String current = currentId == null ? "" : currentId;
        String incoming = incomingId == null ? "" : incomingId;
        return !current.isEmpty() && current.equals(incoming);
    }

    public static long until(long now) {
        return now + HOLD_MS;
    }

    public static boolean retry(long now, long holdUntil) {
        return now >= holdUntil;
    }
}
