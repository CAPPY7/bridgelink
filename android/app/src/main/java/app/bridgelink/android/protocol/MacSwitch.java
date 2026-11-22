package app.bridgelink.android.protocol;

/**
 * Which saved Mac the next hello should use. The handshake must not swap
 * the active token by itself, and forget must drop that Mac only.
 */
public final class MacSwitch {
    public final String fingerprint;
    public final String token;
    /** True when the certificate is a different Mac that is already saved. */
    public final boolean switched;

    private MacSwitch(String fingerprint, String token, boolean switched) {
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.token = token == null ? "" : token;
        this.switched = switched;
    }

    /**
     * After the certificate is seen. A saved Mac keeps its own token.
     * An unknown certificate keeps the pinned token so a mismatch cannot erase it.
     */
    public static MacSwitch choose(String pinned, String activeToken, String book, String seen) {
        if (seen != null && !seen.isEmpty() && MacBook.known(book, seen)) {
            boolean switched = pinned == null || !pinned.equalsIgnoreCase(seen);
            String token;
            if (!switched && activeToken != null && activeToken.length() >= 16) {
                token = activeToken;
            } else {
                token = MacTokens.tokenFor(book, seen);
            }
            return new MacSwitch(seen, token, switched);
        }
        String fp = pinned == null ? "" : pinned;
        String token = activeToken == null ? "" : activeToken;
        return new MacSwitch(fp, token, false);
    }

    /** Drop one Mac. Every other saved token stays. */
    public static String forget(String book, String fingerprint) {
        return MacTokens.remove(book, fingerprint);
    }
}
