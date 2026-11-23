import Foundation

/// The menu switch shows the phone's reply, not the Mac's guess.
public enum DndFollow {
    public static func shown(guess: Bool, reply: Bool) -> Bool {
        reply
    }

    public static let needAccess = "Allow Do Not Disturb access on the phone. Open Settings, then Special app access, then Do Not Disturb access, and turn BridgeLink on."
}
