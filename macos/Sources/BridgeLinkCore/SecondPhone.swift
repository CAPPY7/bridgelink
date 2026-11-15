import Foundation

/// One phone stays connected. A different phone waits instead of kicking it off.
public enum SecondPhone {
    public static let holdMs: Int64 = 5 * 60 * 1000

    public static func allow(_ currentId: String, _ incomingId: String, _ hasLive: Bool) -> Bool {
        if !hasLive { return true }
        return !currentId.isEmpty && currentId == incomingId
    }
}
