import Foundation

/// Rules for texts, the shared folder, automations, and the phone trackpad. Matches the Android class.
public enum StageFive {
    public static let smsCap = 80
    public static let ruleCap = 8
    public static let folderCap = 200
    public static let part = 24 * 1024
    public static let folderFileCap: Int64 = 32 * 1024 * 1024
    public static let events = ["connect", "battery_low", "home_wifi", "sms_code", "file_in", "clip", "notify", "leave"]
    public static let actions = ["open_app", "alert", "start_sync", "copy_code", "ring", "pause_sync", "note", "none"]
    public static let smsSteps = """
    1. Open Settings.
    2. Tap Apps, then BridgeLink.
    3. Tap the three dots in the top corner.
    4. Tap Allow restricted settings, then confirm.
    5. Go back, tap Permissions, then SMS.
    6. Allow SMS.
    """
    public static let pointerSteps = """
    1. Open System Settings.
    2. Go to Privacy and Security, then Accessibility.
    3. Turn BridgeLink on.
    4. If BridgeLink is missing, click + and choose the app in Applications.
    """

    public struct Sms: Equatable, Identifiable {
        public let id: String
        public let address: String
        public let body: String
        public let incoming: Bool
        public let sim: Int
        public let at: Int64

        public init(id: String, address: String, body: String, incoming: Bool, sim: Int, at: Int64) {
            self.id = id
            self.address = address
            self.body = body
            self.incoming = incoming
            self.sim = sim
            self.at = at
        }
    }

    public struct Rule: Equatable, Identifiable {
        public let id: String
        public let when: String
        public let thenAction: String
        public let extra: String
        public let enabled: Bool

        public init(id: String, when: String, thenAction: String, extra: String, enabled: Bool) {
            self.id = id
            self.when = when
            self.thenAction = thenAction
            self.extra = extra
            self.enabled = enabled
        }
    }

    public struct Fire: Equatable {
        public let thenAction: String
        public let extra: String
        public let detail: String

        public init(thenAction: String, extra: String, detail: String) {
            self.thenAction = thenAction
            self.extra = extra
            self.detail = detail
        }
    }

    public struct Entry: Equatable {
        public let path: String
        public let size: Int64
        public let mtime: Int64
        public let sha: String
        public let deleted: Bool

        public init(path: String, size: Int64, mtime: Int64, sha: String, deleted: Bool) {
            self.path = path
            self.size = size
            self.mtime = mtime
            self.sha = sha
            self.deleted = deleted
        }
    }

    public enum Choice: String { case take, keep, conflict, delete, skip }

    public static func addressOk(_ raw: String) -> Bool {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.count < 3 || text.count > 20 { return false }
        var digits = 0
        for char in text {
            if char.isNumber { digits += 1 }
            else if char != "+" && char != " " && char != "-" && char != "(" && char != ")" { return false }
        }
        return digits >= 3
    }

    public static func simSlot(_ raw: Int, _ count: Int) -> Int {
        let slots = count < 1 ? 1 : min(count, 4)
        if raw < 0 { return 0 }
        if raw >= slots { return slots - 1 }
        return raw
    }

    public static func addSms(_ existing: [Sms], _ item: Sms?) -> [Sms] {
        var next: [Sms] = []
        if let item, !item.id.isEmpty { next.append(item) }
        for sms in existing {
            if next.count >= smsCap { break }
            if let item, item.id == sms.id { continue }
            next.append(sms)
        }
        return next
    }

    public static func encodeSms(_ items: [Sms]) -> String {
        items.map { "\(escape($0.id))\t\(escape($0.address))\t\($0.incoming ? "1" : "0")\t\($0.sim)\t\($0.at)\t\(escape($0.body))" }
            .joined(separator: "\n")
    }

    public static func decodeSms(_ raw: String) -> [Sms] {
        if raw.isEmpty { return [] }
        var items: [Sms] = []
        for line in raw.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.isEmpty { continue }
            let parts = line.split(separator: "\t", maxSplits: 5, omittingEmptySubsequences: false).map(String.init)
            if parts.count < 6 { continue }
            guard let sim = Int(parts[3]), let at = Int64(parts[4]) else { continue }
            items.append(Sms(id: unescape(parts[0]), address: unescape(parts[1]), body: unescape(parts[5]), incoming: parts[2] == "1", sim: sim, at: at))
        }
        return items
    }

    public static func knownEvent(_ event: String) -> Bool { events.contains(event) }
    public static func knownAction(_ action: String) -> Bool { actions.contains(action) }

    public static func eventLabel(_ event: String) -> String {
        switch event {
        case "connect": return "Phone connects"
        case "battery_low": return "Phone battery under 20%"
        case "home_wifi": return "Phone joins home Wi-Fi"
        case "sms_code": return "Text has a code"
        case "file_in": return "A file arrives"
        case "clip": return "Clipboard arrives"
        case "notify": return "A notification arrives"
        case "leave": return "Phone leaves the Mac"
        default: return event
        }
    }

    public static func actionLabel(_ action: String) -> String {
        switch action {
        case "open_app": return "Open an app"
        case "alert": return "Show an alert"
        case "start_sync": return "Start shared folder sync"
        case "copy_code": return "Copy the code"
        case "ring": return "Ring the phone"
        case "pause_sync": return "Pause shared folder sync"
        case "note": return "Show a note"
        case "none": return "Do nothing"
        default: return action
        }
    }

    public static func addRule(_ existing: [Rule], _ when: String, _ thenAction: String, _ extra: String) -> [Rule] {
        var next = existing
        if !knownEvent(when) || !knownAction(thenAction) || next.count >= ruleCap { return next }
        var clean = extra.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.count > 80 { clean = String(clean.prefix(80)) }
        var n = next.count + 1
        var id = "\(when)-\(thenAction)-\(n)"
        while next.contains(where: { $0.id == id }) {
            n += 1
            id = "\(when)-\(thenAction)-\(n)"
        }
        next.append(Rule(id: id, when: when, thenAction: thenAction, extra: clean, enabled: true))
        return next
    }

    public static func toggle(_ existing: [Rule], _ id: String, _ on: Bool) -> [Rule] {
        existing.map { rule in
            rule.id == id ? Rule(id: rule.id, when: rule.when, thenAction: rule.thenAction, extra: rule.extra, enabled: on) : rule
        }
    }

    public static func delete(_ existing: [Rule], _ id: String) -> [Rule] {
        existing.filter { $0.id != id }
    }

    public static func fire(_ rules: [Rule], _ event: String, _ detail: String) -> [Fire] {
        if !knownEvent(event) { return [] }
        return rules.compactMap { rule in
            if !rule.enabled || rule.when != event { return nil }
            if event == "home_wifi" && !homeWifi(detail, rule.extra) { return nil }
            if event == "sms_code" && detail.isEmpty { return nil }
            return Fire(thenAction: rule.thenAction, extra: rule.extra, detail: detail)
        }
    }

    public static func encodeRules(_ rules: [Rule]) -> String {
        rules.map { "\(escape($0.id))\t\($0.enabled ? "1" : "0")\t\($0.when)\t\($0.thenAction)\t\(escape($0.extra))" }
            .joined(separator: "\n")
    }

    public static func decodeRules(_ raw: String) -> [Rule] {
        if raw.isEmpty { return [] }
        var rules: [Rule] = []
        for line in raw.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.isEmpty || rules.count >= ruleCap { continue }
            let parts = line.split(separator: "\t", maxSplits: 4, omittingEmptySubsequences: false).map(String.init)
            if parts.count < 5 || !knownEvent(parts[2]) || !knownAction(parts[3]) { continue }
            rules.append(Rule(id: unescape(parts[0]), when: parts[2], thenAction: parts[3], extra: unescape(parts[4]), enabled: parts[1] == "1"))
        }
        return rules
    }

    public static func batteryLow(_ level: Int, _ charging: Bool) -> Bool {
        level >= 0 && level < 20 && !charging
    }

    public static func homeWifi(_ ssid: String, _ wanted: String) -> Bool {
        var name = ssid.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty || name.lowercased() == "<unknown ssid>" || name.lowercased() == "unknown" { return false }
        if name.hasPrefix("\""), name.hasSuffix("\""), name.count >= 2 {
            name = String(name.dropFirst().dropLast())
        }
        if name.isEmpty { return false }
        let want = wanted.trimmingCharacters(in: .whitespacesAndNewlines)
        if want.isEmpty { return false }
        return name.lowercased() == want.lowercased()
    }

    public static func appName(_ raw: String) -> String? {
        var name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.hasSuffix(".app") { name = String(name.dropLast(4)).trimmingCharacters(in: .whitespaces) }
        if name.isEmpty || name.count > 80 { return nil }
        if name.contains("/") || name.contains("\\") || name.contains("..") || name.contains(":") { return nil }
        return name
    }

    public static func decide(paused: Bool, localExists: Bool, localSha: String, remoteDeleted: Bool, remoteSha: String) -> Choice {
        if paused { return .skip }
        if remoteDeleted {
            if localExists && localSha.lowercased() == remoteSha.lowercased() { return .delete }
            return .keep
        }
        if remoteSha.isEmpty { return .skip }
        if !localExists { return .take }
        if localSha.lowercased() == remoteSha.lowercased() { return .skip }
        return .conflict
    }

    public static func conflictName(_ path: String) -> String? {
        guard let safe = Features.safeRelative(path) else { return nil }
        let slash = safe.lastIndex(of: "/")
        let dir = slash == nil ? "" : String(safe[...slash!])
        let name = slash == nil ? safe : String(safe[safe.index(after: slash!)...])
        if let dot = name.lastIndex(of: "."), dot != name.startIndex {
            return dir + String(name[..<dot]) + " (BridgeLink conflict)" + String(name[dot...])
        }
        return dir + name + " (BridgeLink conflict)"
    }

    public static func partOk(_ path: String, _ offset: Int64, _ length: Int, _ size: Int64) -> Bool {
        if Features.safeRelative(path) == nil { return false }
        if offset < 0 || length < 0 || size < 0 || size > folderFileCap { return false }
        if length > part { return false }
        return offset <= size && offset + Int64(length) <= size
    }

    public static func removed(_ before: [Entry], _ now: [Entry]) -> [Entry] {
        let paths = Set(now.filter { !$0.deleted }.map(\.path))
        return before.filter { !$0.deleted && !paths.contains($0.path) }
    }

    public static func encodeIndex(_ items: [Entry]) -> String {
        items.filter { !$0.deleted }.prefix(folderCap)
            .map { "\(escape($0.path))\t\($0.size)\t\($0.mtime)\t\($0.sha)" }
            .joined(separator: "\n")
    }

    public static func decodeIndex(_ raw: String) -> [Entry] {
        if raw.isEmpty { return [] }
        var items: [Entry] = []
        for line in raw.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.isEmpty || items.count >= folderCap { continue }
            let parts = line.split(separator: "\t", maxSplits: 3, omittingEmptySubsequences: false).map(String.init)
            if parts.count < 4 { continue }
            guard let path = Features.safeRelative(unescape(parts[0])) else { continue }
            guard let size = Int64(parts[1]), let mtime = Int64(parts[2]) else { continue }
            let deleted = parts[3].hasPrefix("deleted:")
            let sha = deleted ? String(parts[3].dropFirst("deleted:".count)) : parts[3]
            items.append(Entry(path: path, size: size, mtime: mtime, sha: sha, deleted: deleted))
        }
        return items
    }

    public static func pointer(_ kind: String, _ x: Int, _ y: Int) -> (x: Int, y: Int)? {
        if !knownPointer(kind) { return nil }
        if kind == "laser" { return (x == 0 ? 0 : 1, 0) }
        return (clamp(x), clamp(y))
    }

    public static func knownPointer(_ kind: String) -> Bool {
        ["move", "tap", "right", "scroll", "drag", "key", "slide", "laser"].contains(kind)
    }

    public static func keyName(_ raw: String) -> String? {
        var text = raw
        if text.count > 40 { text = String(text.prefix(40)) }
        switch text.lowercased() {
        case "return", "delete", "escape", "tab", "left", "right", "up", "down", "space":
            return text.lowercased()
        default:
            break
        }
        if text.isEmpty { return nil }
        for scalar in text.unicodeScalars {
            if scalar.value < 32 || scalar.value == 127 { return nil }
        }
        return text
    }

    public static func slideKey(_ which: String) -> String? {
        if which == "next" { return "right" }
        if which == "prev" || which == "previous" { return "left" }
        return nil
    }

    private static func clamp(_ value: Int) -> Int { min(4000, max(-4000, value)) }

    private static func escape(_ text: String) -> String {
        text.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\t", with: " ")
    }

    private static func unescape(_ text: String) -> String {
        var out = ""
        var pending = false
        for char in text {
            if pending {
                if char == "n" { out.append("\n") } else if char == "\\" { out.append("\\") } else {
                    out.append("\\")
                    out.append(char)
                }
                pending = false
            } else if char == "\\" {
                pending = true
            } else {
                out.append(char)
            }
        }
        if pending { out.append("\\") }
        return out
    }
}
