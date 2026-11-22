package app.bridgelink.android.protocol;

/** The phone keeps sending relay_auth until the Mac answers relay_ok. Forgotten stops it. */
public final class RelayAuth {
    public static final String AUTH = "relay_auth";
    public static final String OK = "relay_ok";

    private RelayAuth() {}

    public static boolean forgotten(String code) {
        return "forgotten".equals(code);
    }

    /** True while the Mac has not accepted this phone and has not forgotten it. */
    public static boolean keepSending(boolean accepted, String errorCode) {
        if (forgotten(errorCode)) return false;
        return !accepted;
    }
}
