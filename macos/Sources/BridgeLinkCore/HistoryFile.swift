import Foundation

/// Activity lines saved on disk. The file is JSON. It is not a secret store.
public enum HistoryFile {
    public static func load(from url: URL) -> [String] {
        guard let data = try? Data(contentsOf: url),
              let lines = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return Array(lines.prefix(200))
    }

    public static func append(line: String, url: URL) -> [String] {
        let next = Features.rememberLine(existing: load(from: url), line: line, cap: 200)
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(next) {
            try? data.write(to: url, options: .atomic)
        }
        return next
    }
}
