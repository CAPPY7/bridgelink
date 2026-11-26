package app.bridgelink.android.protocol;

/** A missing SMS list is an empty list, not a crash. */
public final class SmsBatch {
    private SmsBatch() {}

    public static boolean missing(Object messages) {
        return messages == null;
    }
}
