package app.bridgelink.android.protocol;

/** The Mac locks itself after the phone link has been gone. It never unlocks. */
public final class MacLeave {
    private MacLeave() {
    }

    public static boolean shouldLock(boolean enabled, boolean atDesk, boolean linkUp, long goneMs, long delayMs) {
        if (!enabled || atDesk || linkUp) return false;
        if (delayMs < 0 || goneMs < delayMs) return false;
        return true;
    }
}
