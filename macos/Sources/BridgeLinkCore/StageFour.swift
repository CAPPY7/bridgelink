import Foundation

/// Rules for media, leave-lock, the inbox, and clipboard history. Matches the Android class.
public enum StageFour {
    public static let clipCap = 100
    public static let inboxCap = 200
    public static let artCap = 48_000
    public static let bleUUID = "f1a0b4c0-6d3e-4a5b-8c7d-9e0f1a2b3c4d"

    public struct Clip: Equatable {
        public let id: String
        public let text: String
        public let pinned: Bool
        public let at: Int64

        public init(id: String, text: String, pinned: Bool, at: Int64) {
            self.id = id
            self.text = text
            self.pinned = pinned
            self.at = at
        }
    }

    public struct Item: Equatable {
        public let id: String
        public let kind: String
        public let title: String
        public let detail: String
        public let at: Int64

        public init(id: String, kind: String, title: String, detail: String, at: Int64) {
            self.id = id
            self.kind = kind
            self.title = title
            self.detail = detail
            self.at = at
        }
    }

    public static func clipId(_ text: String, _ now: Int64) -> String {
        var hash: Int32 = 0
        for unit in text.utf16 {
            hash = hash &* 31 &+ Int32(truncatingIfNeeded: unit)
        }
        return "\(now)-\(String(UInt32(bitPattern: hash), radix: 16))"
    }

    public static func clampVolume(_ raw: Int) -> Int { min(100, max(0, raw)) }
    public static func farSteps(_ raw: Int) -> Int { min(95, max(40, raw)) }
    public static func delaySeconds(_ raw: Int) -> Int { min(180, max(5, raw)) }
    public static func threshold(_ far: Int) -> Int { -farSteps(far) }
    public static func artFits(_ bytes: Int) -> Bool { bytes > 0 && bytes <= artCap }
    public static func lockRequest(_ reason: String) -> Bool { reason == "away" }

    public static func shouldLock(enabled: Bool, rssi: Int, heard: Bool, threshold: Int, awayMs: Int64,
                                   delayMs: Int64, charging: Bool, atDesk: Bool, alreadyLocked: Bool) -> Bool {
        if !enabled || !heard || alreadyLocked || charging || atDesk { return false }
        if delayMs < 0 || awayMs < delayMs { return false }
        if rssi == 0 { return true }
        return rssi <= threshold
    }

    public static func awayFor(previous: Int64, rssi: Int, threshold: Int, elapsed: Int64, heard: Bool) -> Int64 {
        if !heard { return 0 }
        var step = elapsed
        if step < 0 { step = 0 }
        if step > 60_000 { step = 60_000 }
        let away = rssi == 0 || rssi <= threshold
        if !away { return 0 }
        let next = previous &+ step
        return next < 0 ? Int64.max : next
    }

    public static func splitCommand(_ raw: String) -> (action: String, volume: String)? {
        var action = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        var volume = ""
        if let colon = action.firstIndex(of: ":") {
            volume = String(action[action.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
            action = String(action[..<colon]).trimmingCharacters(in: .whitespaces)
        }
        if Features.mediaAction(action) == nil { return nil }
        if !volume.isEmpty {
            if action == "seek" {
                guard let ms = seekMs(volume) else { return nil }
                volume = String(ms)
            } else {
                guard let number = Int(volume) else { return nil }
                volume = String(clampVolume(number))
            }
        }
        return (action, volume)
    }

    /// Milliseconds into a song. Nil means the number is not a position.
    public static func seekMs(_ raw: String) -> Int64? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let n = Int64(text), n >= 0, n <= 24 * 60 * 60 * 1000 else { return nil }
        return n
    }

    public static func parseSetup(_ raw: String) -> (on: Int, far: Int, delay: Int, desk: Int)? {
        let parts = raw.split(separator: ",", omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }
        if parts.count != 4 { return nil }
        guard let onRaw = Int(parts[0]), let farRaw = Int(parts[1]), let delayRaw = Int(parts[2]), let deskRaw = Int(parts[3]) else {
            return nil
        }
        return (onRaw == 0 ? 0 : 1, farSteps(farRaw), delaySeconds(delayRaw), deskRaw == 0 ? 0 : 1)
    }

    public static func formatSetup(on: Bool, far: Int, delay: Int, desk: Bool) -> String {
        "\(on ? 1 : 0),\(farSteps(far)),\(delaySeconds(delay)),\(desk ? 1 : 0)"
    }

    public static func nowLine(_ title: String, _ artist: String, _ playing: Bool) -> String {
        var name = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let who = artist.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty && who.isEmpty { return "Nothing is playing" }
        if name.isEmpty { name = "Unknown song" }
        let line = who.isEmpty ? name : name + " - " + who
        return (playing ? "Playing " : "Paused ") + line
    }

    public static func fileTitle(_ line: String) -> String {
        var text = line.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("Saved ") { text = String(text.dropFirst(6)) }
        if let range = text.range(of: " in ") { text = String(text[..<range.lowerBound]) }
        return text.isEmpty ? "File" : text
    }

    public static func add(_ existing: [Clip], _ text: String, _ sensitive: Bool, _ now: Int64) -> [Clip] {
        var clean = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.count > 4000 { clean = String(clean.prefix(4000)) }
        if sensitive || !storable(clean) { return cap(existing) }
        var wasPin = false
        var rest: [Clip] = []
        for clip in existing {
            if clip.text == clean { wasPin = clip.pinned } else { rest.append(clip) }
        }
        rest.insert(Clip(id: clipId(clean, now), text: clean, pinned: wasPin, at: now), at: 0)
        return cap(rest)
    }

    public static func pin(_ existing: [Clip], _ id: String, _ on: Bool) -> [Clip] {
        cap(existing.map { clip in
            clip.id == id ? Clip(id: clip.id, text: clip.text, pinned: on, at: clip.at) : clip
        })
    }

    public static func delete(_ existing: [Clip], _ id: String) -> [Clip] {
        existing.filter { $0.id != id }
    }

    public static func search(_ existing: [Clip], _ query: String) -> [Clip] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return existing }
        return existing.filter { $0.text.lowercased().contains(q) }
    }

    public static func encodeClips(_ clips: [Clip]) -> String {
        clips.map { "\($0.id)\t\($0.pinned ? "1" : "0")\t\($0.at)\t\(escape($0.text))" }.joined(separator: "\n")
    }

    public static func decodeClips(_ raw: String) -> [Clip] {
        if raw.isEmpty { return [] }
        var clips: [Clip] = []
        for line in raw.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.isEmpty { continue }
            let parts = line.split(separator: "\t", maxSplits: 3, omittingEmptySubsequences: false).map(String.init)
            if parts.count < 4 { continue }
            guard let at = Int64(parts[2]) else { continue }
            clips.append(Clip(id: parts[0], text: unescape(parts[3]), pinned: parts[1] == "1", at: at))
        }
        return clips
    }

    public static func addInbox(_ existing: [Item], _ kind: String, _ title: String, _ detail: String,
                                 _ sensitive: Bool, _ now: Int64) -> [Item] {
        if kind != "notify" && kind != "sms" && kind != "file" { return existing }
        var shownTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        if shownTitle.isEmpty { shownTitle = "Item" }
        if shownTitle.count > 120 { shownTitle = String(shownTitle.prefix(120)) }
        var shown = detail.trimmingCharacters(in: .whitespacesAndNewlines)
        if !shown.isEmpty && (sensitive || !storable(shown) || looksSecret(shown)) { shown = "Hidden" }
        if shown.count > 240 { shown = String(shown.prefix(240)) }
        var next = [Item(id: clipId(kind + " " + shownTitle, now), kind: kind, title: shownTitle, detail: shown, at: now)]
        for item in existing where next.count < inboxCap { next.append(item) }
        return next
    }

    public static func filter(_ items: [Item], _ kind: String, _ query: String) -> [Item] {
        let want = kind.isEmpty ? "all" : kind
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return items.filter { item in
            if want != "all" && item.kind != want { return false }
            if q.isEmpty { return true }
            return (item.title + " " + item.detail).lowercased().contains(q)
        }
    }

    public static func encodeInbox(_ items: [Item]) -> String {
        items.map { "\($0.id)\t\($0.kind)\t\($0.at)\t\(escape($0.title))\t\(escape($0.detail))" }.joined(separator: "\n")
    }

    public static func decodeInbox(_ raw: String) -> [Item] {
        if raw.isEmpty { return [] }
        var items: [Item] = []
        for line in raw.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.isEmpty { continue }
            let parts = line.split(separator: "\t", maxSplits: 4, omittingEmptySubsequences: false).map(String.init)
            if parts.count < 5 { continue }
            guard let at = Int64(parts[2]) else { continue }
            items.append(Item(id: parts[0], kind: parts[1], title: unescape(parts[3]), detail: unescape(parts[4]), at: at))
        }
        return items
    }

    private static func storable(_ clean: String) -> Bool {
        if clean.isEmpty { return false }
        let compact = clean.replacingOccurrences(of: " ", with: "")
        if compact.count >= 4 && compact.count <= 8 && compact.allSatisfy({ $0.isNumber }) { return false }
        return true
    }

    private static func looksSecret(_ text: String) -> Bool {
        text.lowercased().contains("password")
    }

    private static func cap(_ items: [Clip]) -> [Clip] {
        let pinned = items.filter(\.pinned)
        let rest = items.filter { !$0.pinned }
        var ordered = pinned + rest
        while ordered.count > clipCap {
            if let index = ordered.lastIndex(where: { !$0.pinned }) {
                ordered.remove(at: index)
            } else {
                ordered.removeLast()
            }
        }
        return ordered
    }

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
