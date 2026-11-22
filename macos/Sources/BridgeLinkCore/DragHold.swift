import Foundation

/// Whether a trackpad drag is still holding the Mac mouse button.
public enum DragHold {
    /// release is true when a held button must go up before this message.
    public static func step(holding: Bool, kind: String, quietSeconds: Int) -> (holding: Bool, release: Bool) {
        if kind == "drag" { return (true, false) }
        if kind == "drag_end" || kind == "tap" || kind == "right" || kind == "drop" {
            return (false, holding)
        }
        if kind == "idle", quietSeconds >= 3 { return (false, holding) }
        return (holding, false)
    }
}
