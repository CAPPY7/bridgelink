package app.bridgelink.android.protocol;

/** Message types the two apps must understand. A test lists the same words. */
public final class FeatureMessages {
    private FeatureMessages() {}

    public static final String[] TYPES = {
            "text", "clipboard", "clipboard_image", "link", "ping", "pong",
            "file_begin", "file_chunk", "file_end", "file_have", "file_cancel",
            "notify", "notify_reply", "notify_dismiss", "otp", "battery",
            "find_mac", "find_stop", "media", "media_now", "lock", "lock_setup",
            "dnd", "sms", "folder", "open_app", "pointer", "mirror_start", "mirror_stop",
            "cam_start", "cam_stop", "control", "forget", "hello", "paired", "error"
    };

    public static boolean known(String type) {
        if (type == null) return false;
        for (String item : TYPES) {
            if (item.equals(type)) return true;
        }
        return false;
    }
}
