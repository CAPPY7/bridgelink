import CryptoKit
import Foundation

public enum Transfers {
    public static let chunk = 32 * 1024
    public static let maxBytes = 4 * 1024 * 1024 * 1024

    public static func sanitizeName(_ raw: String) -> String {
        var name = raw.replacingOccurrences(of: "\\", with: "/")
        if let slash = name.lastIndex(of: "/") {
            let next = name.index(after: slash)
            name = next < name.endIndex ? String(name[next...]) : ""
        }
        name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        var out = ""
        var count = 0
        for scalar in name.unicodeScalars {
            if count >= 1024 { break }
            count += 1
            if scalar.value < 32 || scalar == "/" || scalar == "\\" || scalar == ":" || rtlOverride(scalar.value) {
                out.unicodeScalars.append("_")
            } else {
                out.unicodeScalars.append(scalar)
            }
        }
        return clipName(out.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    public static func rtlOverride(_ value: UInt32) -> Bool {
        (value >= 0x202A && value <= 0x202E) || (value >= 0x2066 && value <= 0x2069)
    }

    static func clipName(_ cleaned: String) -> String {
        if cleaned.isEmpty || cleaned == "." || cleaned == ".." { return "file" }
        if cleaned.count <= 120 { return cleaned }
        guard let dot = cleaned.lastIndex(of: "."), dot != cleaned.startIndex else {
            return String(cleaned.prefix(120))
        }
        let ext = String(cleaned[dot...])
        if ext.count > 12 { return String(cleaned.prefix(120)) }
        let head = String(cleaned[..<dot])
        let keep = 120 - ext.count
        if keep < 1 { return String(cleaned.prefix(120)) }
        return String(head.prefix(keep)) + ext
    }

    public static func acceptUrl(_ raw: String) -> String? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty || text.count > 2000 { return nil }
        if text.contains("\n") || text.contains("\r") || text.contains("\u{0}") { return nil }
        let lower = text.lowercased()
        if lower.hasPrefix("https://") || lower.hasPrefix("http://") { return text }
        return nil
    }

    /// A transfer id is 1 to 64 hex characters. Anything else could be a path.
    public static func safeId(_ id: String) -> Bool {
        if id.isEmpty || id.count > 64 { return false }
        return id.allSatisfy { ($0 >= "0" && $0 <= "9") || ($0 >= "a" && $0 <= "f") }
    }

    public static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

public final class IncomingFile {
    public let id: String
    public let name: String
    public let size: Int
    public private(set) var written = 0
    public private(set) var failed = false
    public var markedScan = false
    private var digest = SHA256()

    public init?(id: String, name: String, size: Int) {
        if !Transfers.safeId(id) || size < 0 || size > Transfers.maxBytes { return nil }
        self.id = id
        self.name = Transfers.sanitizeName(name)
        self.size = size
    }

    public func seed(_ prefix: Data) {
        if failed || prefix.count > size {
            failed = true
            return
        }
        digest.update(data: prefix)
        written = prefix.count
    }

    public func accept(offset: Int, base64: String) -> Data? {
        if failed || offset != written { return nil }
        guard let raw = Data(base64Encoded: base64) else {
            failed = true
            return nil
        }
        if raw.isEmpty || raw.count > Transfers.chunk || written + raw.count > size {
            failed = true
            return nil
        }
        digest.update(data: raw)
        written += raw.count
        return raw
    }

    public func matches(_ sha256: String) -> Bool {
        if failed || written != size { return false }
        var copy = digest
        let hex = copy.finalize().map { String(format: "%02x", $0) }.joined()
        return constantTimeEquals(hex, sha256.lowercased())
    }
}
