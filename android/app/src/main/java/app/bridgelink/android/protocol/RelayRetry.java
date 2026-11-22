package app.bridgelink.android.protocol;

/** After the away relay drops, wait and try again. A working relay is left alone. */
public final class RelayRetry {
    public static final String DOWN = "Away link down";

    private RelayRetry() {}

    public static boolean rejoin(String reason) {
        return DOWN.equals(reason);
    }

    public static long delayMs(int attempt) {
        return Backoff.nextMs(attempt);
    }

    /** A Wi-Fi retry must not tear down a relay that is already up. */
    public static boolean leaveIt(boolean relayUp) {
        return relayUp;
    }
}
