import Foundation

/// Compares the installed app with the Sparkle-style appcast. No network in the compare itself.
public enum ReleaseCheck {
    public static let feed = "https://github.com/CAPPY7/bridgelink/releases/latest/download/appcast.xml"
    public static let releases = "https://github.com/CAPPY7/bridgelink/releases/latest"

    /// 1 when remote is newer, -1 when local is newer, 0 when they match.
    public static func compare(_ local: String, _ remote: String) -> Int {
        let a = parts(local)
        let b = parts(remote)
        let n = max(a.count, b.count)
        for i in 0..<n {
            let av = i < a.count ? a[i] : 0
            let bv = i < b.count ? b[i] : 0
            if bv != av { return bv > av ? 1 : -1 }
        }
        return 0
    }

    public static func shortVersion(_ xml: String) -> String {
        let key = "sparkle:shortVersionString=\""
        guard let start = xml.range(of: key) else { return "" }
        let rest = xml[start.upperBound...]
        guard let end = rest.firstIndex(of: "\"") else { return "" }
        return String(rest[..<end]).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func tagMatches(_ tag: String, _ versionName: String) -> Bool {
        !versionName.isEmpty && tag == "v" + versionName
    }

    /// Sparkle.framework is not linked. There is no Sparkle signature.
    public static func installsWithSparkle() -> Bool { false }

    public enum SumVerdict: Equatable {
        case match
        case mismatch
        case missing
    }

    /// Find the hex line for a file in SHA256SUMS. Accepts "hash  name" and "hash *name".
    public static func checkSum(_ sums: String, _ name: String, _ digest: String) -> SumVerdict {
        var found: String?
        for raw in sums.split(whereSeparator: \.isNewline) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }
            let parts = line.split(whereSeparator: \.isWhitespace).map(String.init)
            guard parts.count >= 2 else { continue }
            let file = parts[1].hasPrefix("*") ? String(parts[1].dropFirst()) : parts[1]
            if file == name {
                found = parts[0]
                break
            }
        }
        guard let found else { return .missing }
        if found.lowercased() == digest.lowercased() { return .match }
        return .mismatch
    }

    public static func fetchShortVersion() async -> String {
        guard let data = await fetchBytes(feed) else { return "" }
        return shortVersion(String(decoding: data, as: UTF8.self))
    }

    public static func fetchBytes(_ address: String) async -> Data? {
        guard let url = URL(string: address) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 60
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode >= 400 { return nil }
            return data
        } catch {
            return nil
        }
    }

    private static func parts(_ raw: String) -> [Int] {
        raw.split(separator: ".").map { bit in
            var n = 0
            for ch in bit {
                guard let digit = ch.wholeNumberValue else { break }
                n = n * 10 + digit
            }
            return n
        }
    }
}
