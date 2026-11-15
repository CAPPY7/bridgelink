import Foundation

/// Sleep the display. Do not write Mac system settings.
public enum LockPlan {
    public static func command() -> [String] { ["/usr/bin/pmset", "displaysleepnow"] }

    public static func writesSettings() -> Bool { false }

    public static func note(_ passwordNow: Bool) -> String {
        if passwordNow { return "Locked the screen" }
        return "Locked the screen. Turn on Require password immediately in Lock Screen settings if the Mac did not ask."
    }
}
