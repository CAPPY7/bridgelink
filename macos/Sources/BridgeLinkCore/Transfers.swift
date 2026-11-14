import CryptoKit
import Foundation

public enum Transfers {
    public static let chunk = 32 * 1024
    public static let maxBytes = 100 * 1024 * 1024

    public static func sanitizeName(_ raw: String) -> String {
        var name = raw.replacingOccurrences(of: "\\", with: "/")
        if let slash = name.lastIndex(of: "/") {
            let next = name.index(after: slash)
            name = next < name.endIndex ? String(name[next...]) : ""
        }
        name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        var out = ""
        for scalar in name.unicodeScalars {
            if out.count >= 120 { break }
            if scalar.value < 32 || scalar == "/" || scalar == "\\" || scalar == ":" {
                out.append("_")
            } else {
                out.unicodeScalars.append(scalar)
            }
        }
        let cleaned = out.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.isEmpty || cleaned == "." || cleaned == ".." { return "file" }
        return cleaned
    }

    public static func acceptUrl(_ raw: String) -> String? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty || text.count > 2000 { return nil }
        if text.contains("\n") || text.contains("\r") || text.contains("\u{0}") { return nil }
        let lower = text.lowercased()
        if lower.hasPrefix("https://") || lower.hasPrefix("http://") { return text }
        return nil
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
    private var digest = SHA256()

    public init?(id: String, name: String, size: Int) {
        if id.isEmpty || size < 0 || size > Transfers.maxBytes { return nil }
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
