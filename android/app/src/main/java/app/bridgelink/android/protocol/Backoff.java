package app.bridgelink.android.protocol;

public final class Backoff {
    private Backoff() {}

    public static long nextMs(int attempt) {
        int n = Math.max(0, attempt);
        long delay = 500L << Math.min(n, 16);
        return Math.min(30_000L, delay);
    }
}
