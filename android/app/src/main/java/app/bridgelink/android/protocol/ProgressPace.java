package app.bridgelink.android.protocol;

/** "Sending — N%" is shown at most once a second. 100% is always shown. */
public final class ProgressPace {
    private ProgressPace() {}

    public static boolean show(long lastAtMs, long nowMs, int percent) {
        if (percent >= 100) return true;
        return nowMs - lastAtMs >= 1000;
    }
}
