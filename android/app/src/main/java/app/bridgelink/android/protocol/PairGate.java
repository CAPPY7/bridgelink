package app.bridgelink.android.protocol;

/**
 * A pairing link never connects by itself. A different Mac fingerprint
 * must not be given the token that belongs to the Mac already saved.
 */
public final class PairGate {
    public final boolean askFirst;
    public final boolean differentMac;
    public final String title;
    public final String button;
    public final String detail;
    /** Token the next hello may carry. Empty when the fingerprint changed. */
    public final String tokenAfterAccept;
    /** Fingerprint whose token should be kept aside, not sent to the new Mac. */
    public final String parkFingerprint;

    private PairGate(boolean differentMac, String title, String button, String detail,
                     String tokenAfterAccept, String parkFingerprint) {
        this.askFirst = true;
        this.differentMac = differentMac;
        this.title = title;
        this.button = button;
        this.detail = detail;
        this.tokenAfterAccept = tokenAfterAccept == null ? "" : tokenAfterAccept;
        this.parkFingerprint = parkFingerprint == null ? "" : parkFingerprint;
    }

    public static PairGate offer(String savedFingerprint, String savedToken, String linkFingerprint, String macLabel) {
        String saved = savedFingerprint == null ? "" : savedFingerprint.trim();
        String link = linkFingerprint == null ? "" : linkFingerprint.trim();
        String token = savedToken == null ? "" : savedToken;
        boolean different = !saved.isEmpty() && !link.isEmpty() && !saved.equalsIgnoreCase(link);
        String label = macLabel == null || macLabel.isBlank() ? "this Mac" : macLabel.trim();
        String key = shortFingerprint(link);
        if (different) {
            return new PairGate(true, "This is a different Mac", "Pair new Mac",
                    "The saved key does not match " + label + ". Key " + key
                            + ". Pair only if you trust this Mac.",
                    "", saved);
        }
        String kept = token.length() >= 16 ? token : "";
        return new PairGate(false, "Pair with " + label + "?", "Pair",
                "Key " + key + ". Nothing is sent until you tap Pair.",
                kept, "");
    }

    /** Hello may carry a token only when it belongs to the certificate just seen. */
    public static String helloToken(String pinnedFingerprint, String token, String seenFingerprint) {
        if (pinnedFingerprint == null || seenFingerprint == null) return "";
        if (pinnedFingerprint.isEmpty() || !pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) return "";
        if (token == null || token.length() < 16) return "";
        return token;
    }

    public static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return "not shown";
        String hex = fingerprint.trim().toLowerCase();
        if (hex.length() <= 12) return hex;
        return hex.substring(0, 8) + "…" + hex.substring(hex.length() - 4);
    }
}
