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

    public static func fetchShortVersion() async -> String {
        guard let url = URL(string: feed) else { return "" }
        var request = URLRequest(url: url)
        request.timeoutInterval = 12
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode >= 400 { return "" }
            return shortVersion(String(decoding: data, as: UTF8.self))
        } catch {
            return ""
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
