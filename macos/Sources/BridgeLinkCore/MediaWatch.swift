import Foundation

/// The Mac asks Music or Spotify only while the phone's Remote page is open.
public enum MediaWatch {
    public static func command(_ raw: String) -> (action: String, volume: String)? {
        let action = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if action == "watch" || action == "idle" { return (action, "") }
        return nil
    }

    public static func shouldPoll(_ screenOpen: Bool, _ paired: Bool, _ busy: Bool) -> Bool {
        screenOpen && paired && !busy
    }
}
