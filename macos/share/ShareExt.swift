import AppKit
import Foundation

/// Not shipped. A dylib cannot be a share extension. Use Finder Services, the Dock drop, or the drop zone.
@objc(ShareExt)
final class ShareExt: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        let items = context.inputItems.compactMap { $0 as? NSExtensionItem }
        let group = DispatchGroup()
        let lock = NSLock()
        var paths: [String] = []
        for item in items {
            for attachment in item.attachments ?? [] where attachment.hasItemConformingToTypeIdentifier("public.file-url") {
                group.enter()
                attachment.loadItem(forTypeIdentifier: "public.file-url", options: nil) { value, _ in
                    if let url = value as? URL {
                        lock.lock()
                        paths.append(url.path)
                        lock.unlock()
                    }
                    group.leave()
                }
            }
        }
        group.notify(queue: .main) {
            Self.write(paths)
            let app = URL(fileURLWithPath: "/Applications/BridgeLink.app")
            if FileManager.default.fileExists(atPath: app.path) {
                NSWorkspace.shared.openApplication(at: app, configuration: NSWorkspace.OpenConfiguration())
            }
            context.completeRequest(returningItems: nil)
        }
    }

    private static func write(_ paths: [String]) {
        if paths.isEmpty { return }
        let dir = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Application Support/BridgeLink", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("share-inbox.txt")
        let existing = (try? String(contentsOf: file, encoding: .utf8)) ?? ""
        let next = existing + paths.joined(separator: "\n") + "\n"
        try? next.write(to: file, atomically: true, encoding: .utf8)
    }
}
