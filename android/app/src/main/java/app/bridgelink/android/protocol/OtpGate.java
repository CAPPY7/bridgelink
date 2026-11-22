package app.bridgelink.android.protocol;

/** The same code is sent once. A new code, or the same code after a minute, is sent. */
public final class OtpGate {
    private OtpGate() {}

    public static boolean fresh(String previous, long previousAt, String code, long now) {
        if (code == null || code.isEmpty()) return false;
        if (previous != null && previous.equals(code) && now - previousAt < 60_000) return false;
        return true;
    }
}
