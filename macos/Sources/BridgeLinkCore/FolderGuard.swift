import Foundation

public enum FolderGuard {
    /// True when the file stays inside the folder after symlinks are resolved.
    public static func staysInside(root: URL, file: URL) -> Bool {
        let base = root.standardizedFileURL.resolvingSymlinksInPath().path
        let path = file.standardizedFileURL.resolvingSymlinksInPath().path
        return path == base || path.hasPrefix(base + "/")
    }
}
