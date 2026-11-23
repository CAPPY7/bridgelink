import Foundation

/// Phone signal bars. The same rule lives on Android.
public enum SignalRules {
    public static func bars(_ level: Int) -> Int {
        if level < 0 { return 0 }
        if level > 4 { return 4 }
        return level
    }

    public static func changed(last: Int, saw: Bool, next: Int) -> Bool {
        if !saw { return true }
        return last != bars(next)
    }

    public static func words(_ level: Int) -> String {
        "Signal \(bars(level)) of 4"
    }
}
