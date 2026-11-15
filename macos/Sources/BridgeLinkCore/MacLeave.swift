import Foundation

/// The Mac locks itself after the phone link has been gone. It never unlocks.
public enum MacLeave {
    public static func shouldLock(_ enabled: Bool, _ atDesk: Bool, _ linkUp: Bool, _ goneMs: Int64, _ delayMs: Int64) -> Bool {
        if !enabled || atDesk || linkUp { return false }
        if delayMs < 0 || goneMs < delayMs { return false }
        return true
    }
}
