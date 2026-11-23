package app.bridgelink.android.protocol;

/** Text read from a photo, trimmed and limited so a huge picture cannot fill memory. */
public final class TextCap {
    public static final int MAX = 100_000;

    private TextCap() {}

    public static String trimCap(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.length() <= MAX) return text;
        return text.substring(0, MAX);
    }
}
