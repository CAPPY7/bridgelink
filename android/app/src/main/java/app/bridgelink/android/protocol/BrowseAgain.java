package app.bridgelink.android.protocol;

/** After two misses, look on the network for the Mac that matches the saved key. */
public final class BrowseAgain {
    private BrowseAgain() {
    }

    public static boolean shouldBrowse(int attempt) {
        return attempt >= 2;
    }

    public static boolean sameMac(String pinned, String seen) {
        if (pinned == null || pinned.isBlank()) return false;
        return pinned.equalsIgnoreCase(seen == null ? "" : seen.trim());
    }
}
