import Foundation

/// Notification mirroring changes only notify and otp. Other switches stay.
public enum MirrorFlags {
    public static func apply(_ settings: String, _ on: Bool) -> String {
        StageRules.setFlag(StageRules.setFlag(settings, "notify", on), "otp", on)
    }
}
