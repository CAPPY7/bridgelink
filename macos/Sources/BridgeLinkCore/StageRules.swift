import Foundation

/// Decisions for the half-done features. Kept next to the Android StageRules class.
public enum StageRules {
    public static let otpClearMs: Int64 = 60_000
    public static let imageCap = 700_000

    public static func fullBattery(level: Int, alreadyAlerted: Bool) -> Bool {
        level == 100 && !alreadyAlerted
    }

    public static func progressPercent(written: Int64, size: Int64) -> Int {
        if size <= 0 || written <= 0 { return 0 }
        if written >= size { return 100 }
        let percent = (written * 100) / size
        if percent > 99 { return 99 }
        return Int(percent)
    }

    public static func appBlocked(_ list: String, _ app: String) -> Bool {
        if app.isEmpty || list.isEmpty { return false }
        for part in list.split(separator: ",") where part.trimmingCharacters(in: .whitespaces) == app {
            return true
        }
        return false
    }

    /// A link opens by itself only when Always open links is on.
    public static func openLinkNow(screenOn: Bool, alwaysAllow: Bool) -> Bool {
        if screenOn && !alwaysAllow { return false }
        return alwaysAllow
    }

    public static func clipKind(hasText: Bool, hasImage: Bool, sensitive: Bool) -> String {
        if hasText && sensitive { return "skip" }
        if hasText { return "text" }
        if hasImage { return "image" }
        return "empty"
    }

    /// Password-manager clips are never synced or stored.
    public static func skipPasswordClip(_ concealed: Bool, _ transientType: Bool, _ androidSensitive: Bool) -> Bool {
        concealed || transientType || androidSensitive
    }

    public static func imageFits(_ bytes: Int) -> Bool {
        bytes > 0 && bytes <= imageCap
    }

    public static func tidyName(_ raw: String, _ fallback: String) -> String {
        var name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
        if name.count > 40 { name = String(name.prefix(40)).trimmingCharacters(in: .whitespaces) }
        return name.isEmpty ? fallback : name
    }

    public static func findUsesAlarmStream() -> Bool { true }

    public static func bundleName(_ folder: String) -> String {
        let base = Transfers.sanitizeName(folder)
        if base.lowercased().hasSuffix(".zip") { return base }
        return base + ".zip"
    }

    public static func setFlag(_ settings: String, _ feature: String, _ on: Bool) -> String {
        var kept: [String] = []
        for part in settings.split(separator: ",") {
            let bits = part.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            if bits.count == 2 && bits[0] == feature { continue }
            let trimmed = part.trimmingCharacters(in: .whitespaces)
            if !trimmed.isEmpty { kept.append(trimmed) }
        }
        kept.append(feature + (on ? "=1" : "=0"))
        return kept.joined(separator: ",")
    }
}
