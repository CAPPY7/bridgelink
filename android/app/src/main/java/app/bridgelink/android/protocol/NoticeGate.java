package app.bridgelink.android.protocol;

/** The same notification is not sent again. A progress bar update is sent once. */
public final class NoticeGate {
    private NoticeGate() {}

    public static String stamp(String key, String title, String body) {
        return (key == null ? "" : key) + "\n" + (title == null ? "" : title) + "\n" + (body == null ? "" : body);
    }

    public static boolean skipUpdate(boolean progress, String previous, String next) {
        if (previous == null) return false;
        if (progress) return true;
        return previous.equals(next);
    }
}
