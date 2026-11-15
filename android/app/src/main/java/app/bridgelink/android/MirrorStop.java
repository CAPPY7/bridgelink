package app.bridgelink.android;

/**
 * The screen encoder must not be released while the drain thread is still
 * calling into it. Wait for that thread first, then release.
 */
public final class MirrorStop {
    public static final long JOIN_MS = 500;

    private MirrorStop() {
    }

    public static boolean joinDrain(Thread drain, long timeoutMs) {
        if (drain == null) return true;
        try {
            drain.join(Math.max(0, timeoutMs));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        return !drain.isAlive();
    }

    /** Join the drain thread, then run release. A codec error must not escape. */
    public static void stopEncoder(Thread drain, long timeoutMs, Runnable release) {
        joinDrain(drain, timeoutMs);
        if (release == null) return;
        try {
            release.run();
        } catch (RuntimeException ignored) {
        }
    }
}
