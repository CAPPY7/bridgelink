package app.bridgelink.android.protocol;

/** One connection attempt at a time. Fatal errors do not retry. */
public final class LinkState {
    public enum Phase { IDLE, CONNECTING, PAIRED, BACKOFF, FATAL }

    public Phase phase = Phase.IDLE;
    public int attempt;
    public String fatal;
    public boolean retryScheduled;

    public void begin() {
        retryScheduled = false;
        fatal = null;
        phase = Phase.CONNECTING;
    }

    public void paired() {
        retryScheduled = false;
        fatal = null;
        attempt = 0;
        phase = Phase.PAIRED;
    }

    public void retryable(String reason) {
        phase = Phase.BACKOFF;
        retryScheduled = true;
        attempt += 1;
        fatal = null;
        if (reason == null) fatal = null;
    }

    public void failFatal(String reason) {
        retryScheduled = false;
        phase = Phase.FATAL;
        fatal = reason == null || reason.isEmpty() ? "Link stopped" : reason;
    }

    public void stop() {
        retryScheduled = false;
        if (phase != Phase.FATAL) phase = Phase.IDLE;
    }

    public long delayMs() {
        return Backoff.nextMs(Math.max(0, attempt - 1));
    }

    public static boolean isFatalCode(String code) {
        return "bad_proof".equals(code) || "bad_pin".equals(code) || "bad_token".equals(code)
                || "bad_version".equals(code) || "key_changed".equals(code) || "bad_code".equals(code);
    }
}
