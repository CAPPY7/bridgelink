package app.bridgelink.android.protocol;

/** One connection attempt at a time. Fatal errors do not retry. */
public final class LinkState {
    public enum Phase { IDLE, CONNECTING, PAIRED, BACKOFF, FATAL }

    public Phase phase = Phase.IDLE;
    public int attempt;
    public String fatal;
    public boolean retryScheduled;
    public long holdUntil;

    public boolean holding(long now) {
        return holdUntil > now;
    }

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

    /**
     * The socket closed. A clean close is not an exception, so the caller asks here.
     * A saved token schedules reconnect. A fatal error or an explicit stop does not.
     * The phase is never left at PAIRED after this returns.
     */
    public boolean afterClose(boolean userStopped, boolean tokenSaved) {
        if (userStopped) {
            if (phase != Phase.FATAL) stop();
            return false;
        }
        if (holding(System.currentTimeMillis())) {
            phase = Phase.BACKOFF;
            retryScheduled = false;
            return false;
        }
        if (phase == Phase.FATAL) return false;
        if (retryScheduled) return true;
        if (!tokenSaved) {
            if (phase != Phase.IDLE) phase = Phase.IDLE;
            return false;
        }
        retryable("Link closed");
        return true;
    }

    /** Reconnect when the phone is not on Wi-Fi, even if an away relay is up. */
    public static boolean networkShouldConnect(Phase phase, boolean linkedOnWifi) {
        if (linkedOnWifi) return false;
        return phase != Phase.CONNECTING && phase != Phase.FATAL;
    }

    public static boolean isFatalCode(String code) {
        return "bad_proof".equals(code) || "bad_pin".equals(code) || "bad_token".equals(code)
                || "bad_version".equals(code) || "key_changed".equals(code) || "bad_code".equals(code);
    }
}
