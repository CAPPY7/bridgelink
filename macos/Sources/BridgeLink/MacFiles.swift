import BridgeLinkCore
import CryptoKit
import Foundation

/// Saves received files under Downloads/BridgeLink and reads files the user picked.
final class MacFiles {
    private let queue = DispatchQueue(label: "app.bridgelink.files")
    private var incoming: [String: IncomingFile] = [:]
    private var handles: [String: FileHandle] = [:]
    private var partials: [String: URL] = [:]

    func begin(id: String, name: String, size: Int, done: @escaping (String) -> Void) {
        queue.async { done(self.beginNow(id: id, name: name, size: size)) }
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

    func finish(id: String, sha256: String, done: @escaping (String) -> Void) {
        queue.async { done(self.finishNow(id: id, sha256: sha256)) }
    }

    func cancel(id: String) {
        queue.async { self.cancelNow(id) }
    }

    func send(url: URL, emit: @escaping (String, [String: String]) -> Void, done: @escaping (String) -> Void) {
        queue.async {
            do {
                let size = (try url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
                if size > Transfers.maxBytes {
                    done("That file is over 100 MB.")
                    return
                }
                let id = UUID().uuidString
                let name = Transfers.sanitizeName(url.lastPathComponent)
                self.hop(emit, "file_begin", ["id": id, "name": name, "size": String(size)])
                let handle = try FileHandle(forReadingFrom: url)
                defer { try? handle.close() }
                var offset = 0
                var hasher = SHA256()
                while true {
                    let chunk = try handle.read(upToCount: Transfers.chunk) ?? Data()
                    if chunk.isEmpty { break }
                    hasher.update(data: chunk)
                    let encoded = chunk.base64EncodedString()
                    self.hop(emit, "file_chunk", ["id": id, "offset": String(offset), "data": encoded])
                    offset += chunk.count
                }
                if offset != size {
                    done("The file changed while it was sending.")
                    return
                }
                let sha = hasher.finalize().map { String(format: "%02x", $0) }.joined()
                self.hop(emit, "file_end", ["id": id, "sha256": sha])
                done("Sent \(name)")
            } catch {
                done("Could not send the file.")
            }
        }
    }

    private func hop(_ emit: @escaping (String, [String: String]) -> Void, _ type: String, _ payload: [String: String]) {
        let gate = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            emit(type, payload)
            gate.signal()
        }
        gate.wait()
    }

    private func beginNow(id: String, name: String, size: Int) -> String {
        guard safe(id), let file = IncomingFile(id: id, name: name, size: size) else {
            return "Refused that file."
        }
        cancelNow(id)
        do {
            let folder = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-incoming", isDirectory: true)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let url = folder.appendingPathComponent(id)
            FileManager.default.createFile(atPath: url.path, contents: nil)
            handles[id] = try FileHandle(forWritingTo: url)
            partials[id] = url
            incoming[id] = file
            return size == 0 ? "Receiving \(file.name)" : "Receiving \(file.name)"
        } catch {
            return "Could not store the file."
        }
    }

    private func finishNow(id: String, sha256: String) -> String {
        guard let file = incoming[id] else { return "That file is not in progress." }
        try? handles[id]?.close()
        handles[id] = nil
        let partial = partials.removeValue(forKey: id)
        incoming[id] = nil
        guard file.matches(sha256), let partial else {
            if let partial { try? FileManager.default.removeItem(at: partial) }
            return "The file hash did not match. It was not saved."
        }
        do {
            let folder = try downloadsFolder()
            var dest = folder.appendingPathComponent(file.name)
            if FileManager.default.fileExists(atPath: dest.path) {
                dest = folder.appendingPathComponent("\(UUID().uuidString.prefix(8))-\(file.name)")
            }
            try FileManager.default.moveItem(at: partial, to: dest)
            return "Saved \(dest.lastPathComponent) in Downloads/BridgeLink"
        } catch {
            try? FileManager.default.removeItem(at: partial)
            return "Could not save the file."
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
