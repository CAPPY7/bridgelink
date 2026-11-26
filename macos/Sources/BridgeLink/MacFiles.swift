import AppKit
import BridgeLinkCore
import CryptoKit
import Darwin
import Foundation

/// Saves received files under Downloads/BridgeLink and reads files the user picked.
final class MacFiles {
    private let queue = DispatchQueue(label: "app.bridgelink.files")
    private let sendQueue = DispatchQueue(label: "app.bridgelink.files.send")
    private let window = SendWindow()
    private var incoming: [String: IncomingFile] = [:]
    private var handles: [String: FileHandle] = [:]
    private var partials: [String: URL] = [:]

    func begin(id: String, name: String, size: Int, from: Int, scan: Bool = false, done: @escaping (String, Int) -> Void) {
        queue.async {
            _ = from
            let result = self.beginNow(id: id, name: name, size: size, scan: scan)
            done(result.status, result.have)
        }
    }

    func chunk(id: String, offset: Int, base64: String, done: @escaping (String?) -> Void) {
        queue.async {
            guard let file = self.incoming[id], let raw = file.accept(offset: offset, base64: base64), let handle = self.handles[id] else {
                self.cancelNow(id)
                done("A file chunk was rejected.")
                return
            }
            do {
                try handle.write(contentsOf: raw)
                done(nil)
            } catch {
                self.cancelNow(id)
                done("Could not store the file.")
            }
        }
    }

    func finish(id: String, sha256: String, done: @escaping (String, URL?, Bool) -> Void) {
        queue.async {
            let result = self.finishNow(id: id, sha256: sha256)
            done(result.line, result.url, result.opened)
        }
    }

    func cancel(id: String) {
        queue.async { self.cancelNow(id) }
    }

    func send(url: URL, emit: @escaping (String, [String: String], @escaping () -> Void) -> Void, wait: @escaping (String, Int) -> Int, progress: @escaping (String) -> Void, cancelled: @escaping () -> Bool, done: @escaping (String) -> Void) {
        sendQueue.async {
            var cleanup: URL?
            let source: URL
            if let zip = self.zipped(url) {
                cleanup = zip
                source = zip
            } else {
                source = url
            }
            defer { if let cleanup { try? FileManager.default.removeItem(at: cleanup) } }
            do {
                let size = (try source.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
                if size > Transfers.maxBytes {
                    done("That file is over 4 GB.")
                    return
                }
                let display = source == url ? Transfers.sanitizeName(url.lastPathComponent) : StageRules.bundleName(url.lastPathComponent)
                let name = Transfers.sanitizeName(display)
                let id = String(Transfers.sha256Hex(Data("\(source.path)\n\(size)".utf8)).prefix(32))
                self.deliver(emit, "file_begin", ["id": id, "name": name, "size": String(size), "from": "0"], flush: true)
                let remote = wait(id, 0)
                let start = remote >= 0 && remote <= size ? remote : 0
                let handle = try FileHandle(forReadingFrom: source)
                defer { try? handle.close() }
                var hasher = SHA256()
                var offset = 0
                var lastPercent = -1
                while offset < start {
                    if cancelled() {
                        self.deliver(emit, "file_cancel", ["id": id], flush: true)
                        done("File send cancelled.")
                        return
                    }
                    let want = min(Transfers.chunk, start - offset)
                    let chunk = try handle.read(upToCount: want) ?? Data()
                    if chunk.isEmpty { done("The file changed while it was sending."); return }
                    hasher.update(data: chunk)
                    offset += chunk.count
                }
                while true {
                    if cancelled() {
                        self.deliver(emit, "file_cancel", ["id": id], flush: true)
                        done("File send cancelled.")
                        return
                    }
                    let chunk = try handle.read(upToCount: Transfers.chunk) ?? Data()
                    if chunk.isEmpty { break }
                    hasher.update(data: chunk)
                    self.deliver(emit, "file_chunk", ["id": id, "offset": String(offset), "data": chunk.base64EncodedString()], flush: false)
                    offset += chunk.count
                    let percent = StageRules.progressPercent(written: Int64(offset), size: Int64(size))
                    if percent == 100 || percent >= lastPercent + 10 {
                        lastPercent = percent
                        let line = "Sending \(name) — \(percent)%"
                        DispatchQueue.main.async { progress(line) }
                    }
                }
                if offset != size {
                    done("The file changed while it was sending.")
                    return
                }
                let sha = hasher.finalize().map { String(format: "%02x", $0) }.joined()
                self.deliver(emit, "file_end", ["id": id, "sha256": sha], flush: true)
                done("Sent \(name)")
            } catch {
                done("Could not send the file.")
            }
        }
    }

    private func zipped(_ url: URL) -> URL? {
        let directory = (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
        if !directory { return nil }
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-\(UUID().uuidString).zip")
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
        task.arguments = ["-c", "-k", "--keepParent", url.path, dest.path]
        do {
            try task.run()
            task.waitUntilExit()
        } catch {
            return nil
        }
        if task.terminationStatus != 0 { return nil }
        return dest
    }

    private func deliver(_ emit: @escaping (String, [String: String], @escaping () -> Void) -> Void, _ type: String, _ payload: [String: String], flush: Bool) {
        window.acquire()
        let gate = DispatchSemaphore(value: 0)
        let once = NSLock()
        var fired = false
        emit(type, payload) {
            once.lock()
            if fired {
                once.unlock()
                return
            }
            fired = true
            once.unlock()
            self.window.release()
            if flush { gate.signal() }
        }
        if flush { gate.wait() }
    }

    private func beginNow(id: String, name: String, size: Int, scan: Bool) -> (status: String, have: Int) {
        guard safe(id), let file = IncomingFile(id: id, name: name, size: size) else {
            return ("Refused that file.", 0)
        }
        file.markedScan = scan
        closeHandle(id)
        do {
            let folder = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-incoming", isDirectory: true)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let url = folder.appendingPathComponent(id)
            var have = 0
            let existingSize = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            if FileManager.default.fileExists(atPath: url.path) && existingSize > 0 && existingSize <= size {
                let reader = try FileHandle(forReadingFrom: url)
                while have < existingSize {
                    let chunk = try reader.read(upToCount: min(Transfers.chunk, existingSize - have)) ?? Data()
                    if chunk.isEmpty { break }
                    file.seed(chunk)
                    have += chunk.count
                }
                try? reader.close()
            } else if existingSize > size {
                try? FileManager.default.removeItem(at: url)
            }
            if !FileManager.default.fileExists(atPath: url.path) {
                FileManager.default.createFile(atPath: url.path, contents: nil)
                have = 0
            }
            let handle = try FileHandle(forWritingTo: url)
            if have > 0 { try handle.seekToEnd() }
            handles[id] = handle
            partials[id] = url
            incoming[id] = file
            return ("Receiving \(file.name)", have)
        } catch {
            return ("Could not store the file.", 0)
        }
    }

    private func closeHandle(_ id: String) {
        try? handles[id]?.close()
        handles[id] = nil
        incoming[id] = nil
        partials[id] = nil
    }

    private func finishNow(id: String, sha256: String) -> (line: String, url: URL?, opened: Bool) {
        guard let file = incoming[id] else { return ("That file is not in progress.", nil, false) }
        try? handles[id]?.close()
        handles[id] = nil
        let partial = partials.removeValue(forKey: id)
        incoming[id] = nil
        guard file.matches(sha256), let partial else {
            if let partial { try? FileManager.default.removeItem(at: partial) }
            return ("The file hash did not match. It was not saved.", nil, false)
        }
        do {
            let folder = try downloadsFolder()
            var dest = folder.appendingPathComponent(file.name)
            if FileManager.default.fileExists(atPath: dest.path) {
                dest = folder.appendingPathComponent("\(UUID().uuidString.prefix(8))-\(file.name)")
            }
            try FileManager.default.moveItem(at: partial, to: dest)
            markQuarantine(dest)
            let openScans = UserDefaults.standard.object(forKey: "openScans") != nil
                && UserDefaults.standard.bool(forKey: "openScans")
            let opened = Features.autoOpenScan(dest.lastPathComponent, openScans, file.markedScan)
            if opened { NSWorkspace.shared.open(dest) }
            return ("Saved \(dest.lastPathComponent) in Downloads/BridgeLink", dest, opened)
        } catch {
            try? FileManager.default.removeItem(at: partial)
            return ("Could not save the file.", nil, false)
        }
    }

    private func markQuarantine(_ url: URL) {
        let raw = "0083;\(Int(Date().timeIntervalSince1970));BridgeLink;"
        raw.withCString { bytes in
            url.withUnsafeFileSystemRepresentation { path in
                guard let path else { return }
                setxattr(path, "com.apple.quarantine", bytes, strlen(bytes), 0, 0)
            }
        }
    }

    private func cancelNow(_ id: String) {
        try? handles[id]?.close()
        handles[id] = nil
        incoming[id] = nil
        if let partial = partials.removeValue(forKey: id) {
            try? FileManager.default.removeItem(at: partial)
        }
    }

    private func downloadsFolder() throws -> URL {
        let base = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let folder = base.appendingPathComponent("BridgeLink", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    private func safe(_ id: String) -> Bool {
        if id.isEmpty || id.count > 80 { return false }
        return id.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" }
    }
}
