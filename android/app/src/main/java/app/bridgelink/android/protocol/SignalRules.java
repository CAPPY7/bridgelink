package app.bridgelink.android.protocol;

/** Phone signal bars. Sent only when the number of bars changes. */
public final class SignalRules {
    private SignalRules() {}

    public static int bars(int level) {
        if (level < 0) return 0;
        if (level > 4) return 4;
        return level;
    }

    public static boolean changed(int last, boolean saw, int next) {
        int bars = bars(next);
        if (!saw) return true;
        return last != bars;
    }
}
