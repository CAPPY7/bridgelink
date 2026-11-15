package app.bridgelink.android.protocol;

/** A certificate the phone has already paired is a switch, not a new key. */
public final class MacBook {
    private MacBook() {
    }

    public static boolean known(String tokens, String fingerprint) {
        return MacTokens.tokenFor(tokens, fingerprint).length() >= 16;
    }
}
