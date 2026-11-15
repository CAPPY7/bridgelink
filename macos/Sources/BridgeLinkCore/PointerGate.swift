import Foundation

/// One Accessibility explanation per session. Later pointer moves stay quiet.
public enum PointerGate {
    public static func shouldAlert(_ alreadyShown: Bool, _ trusted: Bool) -> Bool {
        !alreadyShown && !trusted
    }
}
