import Foundation

/// Message types the two apps must understand. The same list lives on Android.
public enum FeatureMessages {
    public static let types = [
        "text", "clipboard", "clipboard_image", "link", "ping", "pong",
        "file_begin", "file_chunk", "file_end", "file_have", "file_cancel",
        "notify", "notify_reply", "notify_dismiss", "otp", "battery",
        "find_mac", "find_stop", "media", "media_now", "lock", "lock_setup",
        "dnd", "sms", "folder", "open_app", "pointer", "mirror_start", "mirror_stop",
        "cam_start", "cam_stop", "control", "relay_key", "relay_set",
        "relay_use", "relay_auth", "relay_ok", "forget", "hello", "paired", "error",
    ]
}
