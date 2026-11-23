import AppKit
import ApplicationServices
import BridgeLinkCore
import CoreGraphics
import CryptoKit
import Darwin
import Foundation
import Network

enum MacInput {
    static func trusted() -> Bool { AXIsProcessTrusted() }

    static func prompt() {
        let key = kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String
        let options = [key: true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(options)
    }

    @MainActor
    static func perform(kind: String, x: Int, y: Int, text: String) -> String {
        guard let point = StageFive.pointer(kind, x, y) else { return "That pointer move was ignored" }
        if kind == "laser" {
            Laser.shared.set(point.x == 1)
            return point.x == 1 ? "Laser pointer is on" : "Laser pointer is off"
        }
        if !trusted() {
            return "Turn on Accessibility for BridgeLink. System Settings, Privacy and Security, Accessibility."
        }
        switch kind {
        case "move":
            return move(point.x, point.y, drag: false)
        case "drag":
            _ = hold(kind)
            armIdle()
            return move(point.x, point.y, drag: true)
        case "drag_end":
            _ = hold(kind)
            return "Drag ended"
        case "tap":
            _ = hold(kind)
            guard let spot = cursor() else { return "Could not read the pointer" }
            click(spot, right: false)
            return "Clicked"
        case "right":
            _ = hold(kind)
            guard let spot = cursor() else { return "Could not read the pointer" }
            click(spot, right: true)
            return "Right-clicked"
        case "scroll":
            guard let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 1, wheel1: Int32(clamping: point.y), wheel2: 0, wheel3: 0) else {
                return "Could not scroll"
            }
            event.post(tap: .cghidEventTap)
            return "Scrolled"
        case "key":
            return typeText(text)
        case "slide":
            guard let key = StageFive.slideKey(text) else { return "That slide button was ignored" }
            return typeText(key)
        default:
            return "That pointer move was ignored"
        }
    }

    private static func cursor() -> CGPoint? {
        CGEvent(source: nil)?.location
    }

    private static var dragging = false
    private static var holding = false
    private static var dragWatch: DispatchWorkItem?

    static func releaseHeld() {
        _ = hold("drop")
    }

    private static func hold(_ kind: String, quiet: Int = 0) -> Bool {
        if kind != "idle" {
            dragWatch?.cancel()
            dragWatch = nil
        }
        let next = DragHold.step(holding: holding, kind: kind, quietSeconds: quiet)
        holding = next.holding
        if next.release { mouseUp() }
        return next.release
    }

    private static func armIdle() {
        dragWatch?.cancel()
        let work = DispatchWorkItem { _ = hold("idle", quiet: 3) }
        dragWatch = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 3, execute: work)
    }

    private static func mouseUp() {
        guard dragging, let spot = cursor() else {
            dragging = false
            return
        }
        post(.leftMouseUp, spot, .left)
        dragging = false
    }

    private static func move(_ dx: Int, _ dy: Int, drag: Bool) -> String {
        guard let current = cursor() else { return "Could not read the pointer" }
        let next = CGPoint(x: current.x + CGFloat(dx), y: current.y + CGFloat(dy))
        if drag {
            if !dragging {
                post(.leftMouseDown, next, .left)
                dragging = true
            }
            post(.leftMouseDragged, next, .left)
        } else {
            post(.mouseMoved, next, .left)
        }
        return "Moved the pointer"
    }

    private static func click(_ spot: CGPoint, right: Bool) {
        let down: CGEventType = right ? .rightMouseDown : .leftMouseDown
        let up: CGEventType = right ? .rightMouseUp : .leftMouseUp
        let button: CGMouseButton = right ? .right : .left
        post(down, spot, button)
        post(up, spot, button)
    }

    private static func post(_ type: CGEventType, _ spot: CGPoint, _ button: CGMouseButton) {
        CGEvent(mouseEventSource: nil, mouseType: type, mouseCursorPosition: spot, mouseButton: button)?.post(tap: .cghidEventTap)
    }

    private static func typeText(_ text: String) -> String {
        guard let clean = StageFive.keyName(text) else { return "That key was ignored" }
        if let code = namedCode(clean) {
            postKey(code)
            return "Typed a key"
        }
        let source = CGEventSource(stateID: .hidSystemState)
        var units = Array(clean.utf16)
        guard let down = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: true),
              let up = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: false) else { return "Could not type" }
        units.withUnsafeMutableBufferPointer { buffer in
            if let base = buffer.baseAddress {
                down.keyboardSetUnicodeString(stringLength: buffer.count, unicodeString: base)
                up.keyboardSetUnicodeString(stringLength: buffer.count, unicodeString: base)
            }
        }
        down.post(tap: .cghidEventTap)
        up.post(tap: .cghidEventTap)
        return "Typed"
    }

    private static func namedCode(_ name: String) -> CGKeyCode? {
        switch name {
        case "return": return 36
        case "delete": return 51
        case "escape": return 53
        case "tab": return 48
        case "space": return 49
        case "left": return 123
        case "right": return 124
        case "down": return 125
        case "up": return 126
        default: return nil
        }
    }

    private static func postKey(_ code: CGKeyCode) {
        let source = CGEventSource(stateID: .hidSystemState)
        CGEvent(keyboardEventSource: source, virtualKey: code, keyDown: true)?.post(tap: .cghidEventTap)
        CGEvent(keyboardEventSource: source, virtualKey: code, keyDown: false)?.post(tap: .cghidEventTap)
    }
}

@MainActor
final class Laser {
    static let shared = Laser()
    private var window: NSWindow?
    private var timer: Timer?

    func set(_ on: Bool) {
        if !on {
            timer?.invalidate()
            timer = nil
            window?.orderOut(nil)
            window = nil
            return
        }
        if window == nil {
            let win = NSWindow(
                contentRect: NSRect(x: 0, y: 0, width: 18, height: 18),
                styleMask: .borderless,
                backing: .buffered,
                defer: false
            )
            win.isOpaque = false
            win.backgroundColor = .clear
            win.level = .statusBar
            win.ignoresMouseEvents = true
            let dot = NSView(frame: NSRect(x: 0, y: 0, width: 18, height: 18))
            dot.wantsLayer = true
            dot.layer?.backgroundColor = NSColor.systemRed.cgColor
            dot.layer?.cornerRadius = 9
            win.contentView = dot
            window = win
        }
        window?.orderFrontRegardless()
        follow()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            Task { @MainActor in Laser.shared.follow() }
        }
    }

    func follow() {
        let mouse = NSEvent.mouseLocation
        window?.setFrameOrigin(NSPoint(x: mouse.x - 9, y: mouse.y - 9))
    }
}

final class SharedBox {
    private var incoming: [String: Partial] = [:]

    func root() -> URL {
        let url = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("BridgeLink Shared", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    func byteCount(_ path: String) -> Int64 {
        guard let file = resolve(Features.safeRelative(path)),
              FileManager.default.fileExists(atPath: file.path),
              let size = try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize else { return -1 }
        return Int64(size)
    }

    func fileURL(_ path: String) -> URL? {
        guard let file = resolve(Features.safeRelative(path)),
              FileManager.default.fileExists(atPath: file.path) else { return nil }
        return file
    }

    func storeFile(path: String, from tmp: URL) -> String? {
        guard let dest = resolve(Features.safeRelative(path)) else { return "Refused a shared file." }
        try? FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? FileManager.default.removeItem(at: dest)
        do {
            try FileManager.default.moveItem(at: tmp, to: dest)
            return nil
        } catch {
            return "Could not store the shared file."
        }
    }

    func scan() -> [StageFive.Entry] {
        let seen = (try? String(contentsOf: root().appendingPathComponent(".seen"), encoding: .utf8)) ?? ""
        var prior: [String: StageFive.Entry] = [:]
        for item in StageFive.decodeIndex(seen) where !item.deleted {
            prior[item.path] = item
        }
        var items: [StageFive.Entry] = []
        walk(root(), "", &items, prior)
        return items
    }

    func part(path: String, offset: Int64, size: Int64, mtime: Int64, sha: String, data: String) -> String? {
        let raw = data.isEmpty ? Data() : (Data(base64Encoded: data) ?? Data([0]))
        if data.isEmpty == false && Data(base64Encoded: data) == nil { return "Refused a shared file." }
        if !StageFive.partOk(path, offset, raw.count, size) { return "Refused a shared file." }
        guard let safe = Features.safeRelative(path), !sha.isEmpty else { return "Refused a shared file." }
        if incoming[safe] == nil || offset == 0 {
            let dir = root().appendingPathComponent(".incoming", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let tmp = dir.appendingPathComponent(safe.replacingOccurrences(of: "/", with: "_"))
            try? FileManager.default.removeItem(at: tmp)
            FileManager.default.createFile(atPath: tmp.path, contents: nil)
            incoming[safe] = Partial(url: tmp, size: size, sha: sha, written: 0)
        }
        guard var slot = incoming[safe], offset == slot.written, size == slot.size, sha.lowercased() == slot.sha.lowercased() else {
            incoming[safe] = nil
            return "Refused a shared file."
        }
        if let handle = try? FileHandle(forWritingTo: slot.url) {
            handle.seekToEndOfFile()
            try? handle.write(contentsOf: raw)
            try? handle.close()
        }
        slot.written += Int64(raw.count)
        incoming[safe] = slot
        if slot.written < size { return nil }
        incoming[safe] = nil
        guard let got = try? Transfers.sha256Hex(Data(contentsOf: slot.url)), got.lowercased() == sha.lowercased() else {
            try? FileManager.default.removeItem(at: slot.url)
            return "A shared file did not match."
        }
        if let existing = resolve(safe), FileManager.default.fileExists(atPath: existing.path),
           let local = try? Transfers.sha256Hex(Data(contentsOf: existing)), local.lowercased() == sha.lowercased() {
            try? FileManager.default.removeItem(at: slot.url)
            return nil
        }
        guard let dest = place(safe, sha) else { return "Refused a shared file." }
        try? FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? FileManager.default.removeItem(at: dest)
        do {
            try FileManager.default.moveItem(at: slot.url, to: dest)
        } catch {
            return "Could not store the shared file."
        }
        return nil
    }

    func send(path: String, chunk: (Int64, Int64, Int64, String, String) -> Void) -> String? {
        guard let safe = Features.safeRelative(path), let file = resolve(safe),
              FileManager.default.fileExists(atPath: file.path) else { return "That shared file is gone." }
        guard let data = try? Data(contentsOf: file) else { return "Could not read the shared file." }
        let sha = Transfers.sha256Hex(data)
        let size = Int64(data.count)
        let mtime = Int64((try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate?.timeIntervalSince1970) ?? 0) * 1000
        if data.isEmpty {
            chunk(0, 0, mtime, sha, "")
            return nil
        }
        var offset = 0
        while offset < data.count {
            let end = min(data.count, offset + StageFive.part)
            chunk(Int64(offset), size, mtime, sha, data.subdata(in: offset..<end).base64EncodedString())
            offset = end
        }
        return nil
    }

    func remove(path: String, sha: String) -> Bool {
        guard let file = resolve(Features.safeRelative(path) ?? ""),
              FileManager.default.fileExists(atPath: file.path),
              let data = try? Data(contentsOf: file) else { return false }
        if Transfers.sha256Hex(data).lowercased() != sha.lowercased() { return false }
        return (try? FileManager.default.removeItem(at: file)) != nil || !FileManager.default.fileExists(atPath: file.path)
    }

    private func place(_ safe: String, _ sha: String) -> URL? {
        _ = sha
        guard let dest = resolve(safe) else { return nil }
        if !FileManager.default.fileExists(atPath: dest.path) { return dest }
        guard let name = StageFive.conflictName(safe), let copy = resolve(name) else { return nil }
        return copy
    }

    private func walk(_ dir: URL, _ prefix: String, _ items: inout [StageFive.Entry], _ prior: [String: StageFive.Entry]) {
        let kids = (try? FileManager.default.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey],
            options: .skipsHiddenFiles
        )) ?? []
        for kid in kids {
            if items.count >= StageFive.folderCap { return }
            let name = kid.lastPathComponent
            if name.hasPrefix(".") { continue }
            let path = prefix.isEmpty ? name : prefix + "/" + name
            var isDir: ObjCBool = false
            if FileManager.default.fileExists(atPath: kid.path, isDirectory: &isDir), isDir.boolValue {
                walk(kid, path, &items, prior)
            } else {
                let values = try? kid.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
                let size = Int64(values?.fileSize ?? 0)
                let mtime = Int64((values?.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000)
                let old = prior[path]
                let sha: String
                if let old, old.size == size, old.mtime == mtime, !old.sha.isEmpty {
                    sha = old.sha
                } else {
                    sha = streamSha(kid)
                }
                items.append(StageFive.Entry(path: path, size: size, mtime: mtime, sha: sha, deleted: false))
            }
        }
    }

    private func streamSha(_ file: URL) -> String {
        guard let handle = try? FileHandle(forReadingFrom: file) else { return "" }
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            let chunk = (try? handle.read(upToCount: 64 * 1024)) ?? Data()
            if chunk.isEmpty { break }
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private func resolve(_ safe: String?) -> URL? {
        guard let safe else { return nil }
        let file = root().appendingPathComponent(safe)
        let rootPath = root().path
        if file.path != rootPath && !file.path.hasPrefix(rootPath + "/") { return nil }
        return file
    }

    private struct Partial {
        var url: URL
        var size: Int64
        var sha: String
        var written: Int64
    }
}

/// A big shared file arrives as file_begin / file_chunk / file_end and is checked with the normal sha256.
final class FolderSink {
    private let lock = NSLock()
    private var incoming: [String: IncomingFile] = [:]
    private var handles: [String: FileHandle] = [:]
    private var paths: [String: String] = [:]
    private var temps: [String: URL] = [:]

    func begin(_ id: String, _ path: String, _ size: Int) -> Bool {
        onFolderQueue { self.beginNow(id, path, size) }
    }

    func has(_ id: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return incoming[id] != nil
    }

    func chunk(_ id: String, _ offset: Int, _ base64: String) -> String? {
        onFolderQueue { self.chunkNow(id, offset, base64) }
    }

    func finish(_ id: String, _ sha: String, _ box: SharedBox) -> String {
        onFolderQueue { self.finishNow(id, sha, box) }
    }

    func drop(_ id: String) {
        onFolderQueue { self.dropNow(id) }
    }

    private func beginNow(_ id: String, _ path: String, _ size: Int) -> Bool {
        guard StageFive.folderUsesChunks(Int64(size)), let file = IncomingFile(id: id, name: path, size: size) else { return false }
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-folder-\(id)")
        FileManager.default.createFile(atPath: tmp.path, contents: nil)
        guard let handle = try? FileHandle(forWritingTo: tmp) else { return false }
        lock.lock()
        incoming[id] = file
        handles[id] = handle
        paths[id] = path
        temps[id] = tmp
        lock.unlock()
        return true
    }

    private func chunkNow(_ id: String, _ offset: Int, _ base64: String) -> String? {
        lock.lock()
        let file = incoming[id]
        let handle = handles[id]
        lock.unlock()
        guard let file, let raw = file.accept(offset: offset, base64: base64), let handle else {
            dropNow(id)
            return "A file chunk was rejected."
        }
        do {
            try handle.write(contentsOf: raw)
            return nil
        } catch {
            dropNow(id)
            return "Could not store the shared file."
        }
    }

    private func finishNow(_ id: String, _ sha: String, _ box: SharedBox) -> String {
        lock.lock()
        let file = incoming[id]
        let path = paths[id]
        let tmp = temps[id]
        let handle = handles[id]
        incoming[id] = nil
        handles[id] = nil
        paths[id] = nil
        temps[id] = nil
        lock.unlock()
        try? handle?.close()
        guard let file, let path, let tmp else { return "A shared file did not match." }
        if !file.matches(sha) {
            try? FileManager.default.removeItem(at: tmp)
            return "A shared file did not match."
        }
        if let problem = box.storeFile(path: path, from: tmp) { return problem }
        return "Shared folder updated"
    }

    private func dropNow(_ id: String) {
        lock.lock()
        let handle = handles[id]
        let tmp = temps[id]
        incoming[id] = nil
        handles[id] = nil
        paths[id] = nil
        temps[id] = nil
        lock.unlock()
        try? handle?.close()
        if let tmp { try? FileManager.default.removeItem(at: tmp) }
    }
}

/// Holds the folder-watch callback so the event stream can call it.
final class FolderWatchBox {
    let fire: () -> Void
    init(_ fire: @escaping () -> Void) { self.fire = fire }
}

private let folderPartKey = DispatchSpecificKey<UInt8>()
private let folderPartQueue: DispatchQueue = {
    let queue = DispatchQueue(label: "app.bridgelink.mac.folder-part", qos: .utility)
    queue.setSpecific(key: folderPartKey, value: 1)
    return queue
}()

private func onFolderQueue<T>(_ body: () -> T) -> T {
    if DispatchQueue.getSpecific(key: folderPartKey) != nil { return body() }
    return folderPartQueue.sync(execute: body)
}

/// A copy of the folder switches that the folder queue can read without touching the window.
final class FolderFlags: @unchecked Sendable {
    private let lock = NSLock()
    private var on = false
    private var paused = false
    private var linked = false

    func update(on: Bool, paused: Bool) {
        lock.lock()
        self.on = on
        self.paused = paused
        lock.unlock()
    }

    func setLinked(_ value: Bool) {
        lock.lock()
        linked = value
        lock.unlock()
    }

    func allows() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return on && !paused
    }

    /// Watch and push only while the link is up and the folder is on and not paused.
    func live() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return on && !paused && linked
    }
}

extension LinkModel {
    func loadFive() {
        let raw = UserDefaults.standard.string(forKey: "autoRules") ?? ""
        rules = StageFive.decodeRules(raw)
        folderPaused = UserDefaults.standard.bool(forKey: "folderPaused")
        folderPath = sharedBox.root().path
        if let saved = try? String(contentsOf: messagesURL(), encoding: .utf8) {
            messages = StageFive.decodeSms(saved)
        }
    }

    func explainPointer() {
        pointerExplained = true
        note("Allow BridgeLink under Accessibility so the phone can move the pointer.")
        MacInput.prompt()
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
            NSWorkspace.shared.open(url)
        }
    }

    private func notePointerOnce() {
        guard PointerGate.shouldAlert(pointerExplained, MacInput.trusted()) else { return }
        pointerExplained = true
        note("Allow BridgeLink under Accessibility. The pointer waits until you allow it. Open the steps from the window when you want them.")
    }

    func setTrackpad(_ on: Bool) {
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        UserDefaults.standard.set(StageRules.setFlag(flags, "input", on), forKey: "flags")
        trackpadOn = on
        send(type: "input", payload: ["on": on ? "1" : "0"], on: live)
        note(on ? "Phone trackpad is on" : "Phone trackpad is off")
        if on && !MacInput.trusted() { notePointerOnce() }
    }

    func sendSms() {
        let address = smsTo.trimmingCharacters(in: .whitespacesAndNewlines)
        if !StageFive.addressOk(address) {
            note("That number looks wrong")
            return
        }
        if smsBody.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            note("Write a message first")
            return
        }
        guard case .paired = phase else {
            note("Connect and pair before sending")
            return
        }
        send(type: "sms_send", payload: ["address": address, "body": smsBody, "sim": String(smsSim)], on: live)
        smsBody = ""
        note("Asked the phone to send a text")
    }

    func askSms() {
        guard case .paired = phase else {
            note("Connect and pair before reading texts")
            return
        }
        send(type: "sms_ask", payload: [:], on: live)
    }

    func setShared(_ on: Bool) {
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        UserDefaults.standard.set(StageRules.setFlag(flags, "folder", on), forKey: "flags")
        folderOn = on
        send(type: "folder", payload: ["on": on ? "1" : "0"], on: live)
        note(on ? "Shared folder is on" : "Shared folder is off")
        if on { pushFolder() }
    }

    func setFolderPaused(_ on: Bool) {
        folderPaused = on
        UserDefaults.standard.set(on, forKey: "folderPaused")
        send(type: "folder_pause", payload: ["on": on ? "1" : "0"], on: live)
        note(on ? "Shared folder is paused" : "Shared folder sync is on")
        if !on { pushFolder() }
    }

    func revealFolder() {
        NSWorkspace.shared.open(sharedBox.root())
    }

    func pushFolder() {
        guard folderOn, !folderPaused else {
            if folderPaused { note("Shared folder is paused") }
            return
        }
        guard case .paired = phase else { return }
        folderPartQueue.async { [weak self] in
            guard let self else { return }
            guard self.folderFlags.allows() else { return }
            let now = self.sharedBox.scan()
            let seenURL = self.sharedBox.root().appendingPathComponent(".seen")
            let seen = (try? String(contentsOf: seenURL, encoding: .utf8)) ?? ""
            let gone = StageFive.removed(StageFive.decodeIndex(seen), now)
            let body = StageFive.encodeIndex(now)
            if seen != body {
                try? body.write(to: seenURL, atomically: true, encoding: .utf8)
            }
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                guard self.folderOn, !self.folderPaused else { return }
                guard case .paired = self.phase else { return }
                for item in gone {
                    self.send(type: "folder_delete", payload: ["path": item.path, "sha": item.sha], on: self.live)
                }
                self.send(type: "folder_index", payload: ["body": body, "paused": "0"], on: self.live)
                self.note("Shared folder checked")
            }
        }
    }

    func addAutomation() {
        let before = rules.count
        rules = StageFive.addRule(rules, ruleWhen, ruleThen, ruleExtra)
        if rules.count == before {
            note("That automation was not added. Eight is the limit, and the names must match.")
            return
        }
        ruleExtra = ""
        saveRules(sendPeer: true)
        note("Saved an automation")
    }

    func toggleRule(_ id: String) {
        let on = !(rules.first { $0.id == id }?.enabled ?? false)
        rules = StageFive.toggle(rules, id, on)
        saveRules(sendPeer: true)
    }

    func deleteRule(_ id: String) {
        rules = StageFive.delete(rules, id)
        saveRules(sendPeer: true)
    }

    func startFive() {
        folderOn = Features.enabled(UserDefaults.standard.string(forKey: "flags") ?? "", "folder", fallback: false)
        trackpadOn = Features.enabled(UserDefaults.standard.string(forKey: "flags") ?? "", "input", fallback: false)
        askSms()
        pushFolder()
        send(type: "auto_rules", payload: ["body": StageFive.encodeRules(rules)], on: live)
        fireAuto("connect", "")
    }

    func fireAuto(_ event: String, _ detail: String) {
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        if !Features.enabled(flags, "auto", fallback: false) && !rules.contains(where: { $0.enabled }) { return }
        for item in StageFive.fire(rules, event, detail) {
            switch item.thenAction {
            case "open_app":
                if let name = StageFive.appName(item.extra) {
                    let url = URL(fileURLWithPath: "/Applications/\(name).app")
                    if FileManager.default.fileExists(atPath: url.path) {
                        NSWorkspace.shared.open(url)
                        note("Opened \(name)")
                    } else {
                        note("Could not find \(name) in Applications")
                    }
                } else {
                    note("That app name was not allowed")
                }
            case "alert":
                notify(item.extra.isEmpty ? "BridgeLink" : item.extra, "Automation")
                note(item.extra.isEmpty ? "Automation" : item.extra)
            case "note":
                note(item.extra.isEmpty ? "Automation" : item.extra)
            case "start_sync":
                setFolderPaused(false)
            case "pause_sync":
                setFolderPaused(true)
            case "copy_code":
                if !item.detail.isEmpty {
                    copyConcealed(item.detail)
                    note("Copied a code")
                }
            case "ring":
                ring()
            default:
                break
            }
        }
    }

    func handleFive(_ envelope: ParsedEnvelope, _: NWConnection) -> Bool {
        switch envelope.type {
        case "sms_list":
            messages = StageFive.decodeSms(envelope.text("body"))
            simCount = max(1, Int(envelope.text("sims")) ?? 1)
            try? envelope.text("body").write(to: messagesURL(), atomically: true, encoding: .utf8)
            note("Texts updated")
            return true
        case "sms_new":
            let item = StageFive.Sms(
                id: envelope.text("id").isEmpty ? "new" : envelope.text("id"),
                address: envelope.text("address"),
                body: envelope.text("body"),
                incoming: envelope.text("incoming") != "0",
                sim: Int(envelope.text("sim")) ?? 0,
                at: Int64(envelope.text("at")) ?? Int64(Date().timeIntervalSince1970 * 1000)
            )
            messages = StageFive.addSms(messages, item)
            try? StageFive.encodeSms(messages).write(to: messagesURL(), atomically: true, encoding: .utf8)
            let preview = item.body.count > 80 ? String(item.body.prefix(80)) : item.body
            notify("Text from \(item.address)", preview)
            note("New text message")
            if let code = Features.otp(item.body) { fireAuto("sms_code", code) }
            return true
        case "folder_index":
            let indexBody = envelope.text("body")
            folderPartQueue.async { [weak self] in
                guard let self else { return }
                guard self.folderFlags.allows() else { return }
                let local = self.sharedBox.scan()
                var wants: [String] = []
                var removed = false
                for remote in StageFive.decodeIndex(indexBody) {
                    let mine = local.first { $0.path == remote.path }
                    let choice = StageFive.decide(
                        paused: false,
                        localExists: mine != nil,
                        localSha: mine?.sha ?? "",
                        remoteDeleted: remote.deleted,
                        remoteSha: remote.sha
                    )
                    if remote.deleted {
                        if choice == .delete, self.sharedBox.remove(path: remote.path, sha: remote.sha) {
                            removed = true
                        }
                        continue
                    }
                    if choice == .take || choice == .conflict {
                        wants.append(remote.path)
                    }
                }
                let didRemove = removed
                let paths = wants
                DispatchQueue.main.async {
                    guard self.folderOn, !self.folderPaused else { return }
                    if didRemove { self.note("Removed a shared file") }
                    for path in paths {
                        self.send(type: "folder_want", payload: ["path": path], on: self.live)
                    }
                }
            }
            return true
        case "folder_want":
            let wantPath = envelope.text("path")
            folderPartQueue.async { [weak self] in
                guard let self else { return }
                guard self.folderFlags.allows() else { return }
                let count = self.sharedBox.byteCount(wantPath)
                if StageFive.folderUsesChunks(count), let file = self.sharedBox.fileURL(wantPath) {
                    self.shipLarge(file, path: wantPath, size: count)
                    return
                }
                let problem = self.sharedBox.send(path: wantPath) { offset, size, mtime, sha, data in
                    let payload = [
                        "path": wantPath,
                        "offset": String(offset),
                        "size": String(size),
                        "mtime": String(mtime),
                        "sha": sha,
                        "data": data,
                    ]
                    DispatchQueue.main.async {
                        guard self.folderOn, !self.folderPaused else { return }
                        self.send(type: "folder_part", payload: payload, on: self.live)
                    }
                }
                if let problem {
                    DispatchQueue.main.async { self.note(problem) }
                }
            }
            return true
        case "folder_part":
            let path = envelope.text("path")
            let offset = Int64(envelope.text("offset")) ?? -1
            let size = Int64(envelope.text("size")) ?? -1
            let mtime = Int64(envelope.text("mtime")) ?? 0
            let sha = envelope.text("sha")
            let data = envelope.text("data")
            folderPartQueue.async { [weak self] in
                guard let self else { return }
                guard self.folderFlags.allows() else { return }
                let problem = self.sharedBox.part(
                    path: path,
                    offset: offset,
                    size: size,
                    mtime: mtime,
                    sha: sha,
                    data: data
                )
                let length = Int64(Data(base64Encoded: data)?.count ?? 0)
                DispatchQueue.main.async {
                    if let problem {
                        self.note(problem)
                    } else if offset + length >= size {
                        self.note("Shared folder updated")
                    }
                }
            }
            return true
        case "folder_delete":
            let deletePath = envelope.text("path")
            let deleteSha = envelope.text("sha")
            folderPartQueue.async { [weak self] in
                guard let self else { return }
                guard self.folderFlags.allows() else { return }
                let removed = self.sharedBox.remove(path: deletePath, sha: deleteSha)
                if removed {
                    DispatchQueue.main.async {
                        guard self.folderOn, !self.folderPaused else { return }
                        self.note("Removed a shared file")
                    }
                }
            }
            return true
        case "folder_pause":
            folderPaused = envelope.text("on") == "1"
            UserDefaults.standard.set(folderPaused, forKey: "folderPaused")
            note(folderPaused ? "Shared folder is paused" : "Shared folder sync is on")
            if !folderPaused { pushFolder() }
            return true
        case "folder":
            folderOn = envelope.text("on") == "1"
            let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
            UserDefaults.standard.set(StageRules.setFlag(flags, "folder", folderOn), forKey: "flags")
            note(folderOn ? "Shared folder is on" : "Shared folder is off")
            if folderOn && !folderPaused { pushFolder() }
            return true
        case "open_app":
            if let name = StageFive.appName(envelope.text("name")) {
                let url = URL(fileURLWithPath: "/Applications/\(name).app")
                if FileManager.default.fileExists(atPath: url.path) {
                    NSWorkspace.shared.open(url)
                    note("Opened \(name)")
                } else {
                    note("Could not find \(name) in Applications")
                }
            } else {
                note("That app name was not allowed")
            }
            return true
        case "auto_rules":
            let next = StageFive.decodeRules(envelope.text("body"))
            if StageFive.encodeRules(next) != StageFive.encodeRules(rules) {
                rules = next
                UserDefaults.standard.set(StageFive.encodeRules(rules), forKey: "autoRules")
                if rules.contains(where: \.enabled) {
                    let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
                    UserDefaults.standard.set(StageRules.setFlag(flags, "auto", true), forKey: "flags")
                }
                note("Automations updated")
            }
            return true
        case "input":
            trackpadOn = envelope.text("on") == "1"
            let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
            UserDefaults.standard.set(StageRules.setFlag(flags, "input", trackpadOn), forKey: "flags")
            note(trackpadOn ? "Phone trackpad is on" : "Phone trackpad is off")
            if trackpadOn && !MacInput.trusted() { notePointerOnce() }
            return true
        case "pointer":
            let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
            guard Features.enabled(flags, "input", fallback: false) else {
                note("Phone trackpad is off")
                return true
            }
            if !MacInput.trusted() {
                notePointerOnce()
                return true
            }
            _ = MacInput.perform(
                kind: envelope.text("kind"),
                x: Int(envelope.text("x")) ?? 0,
                y: Int(envelope.text("y")) ?? 0,
                text: envelope.text("text")
            )
            return true
        case "wifi":
            return true
        default:
            return false
        }
    }

    private func saveRules(sendPeer: Bool) {
        let body = StageFive.encodeRules(rules)
        UserDefaults.standard.set(body, forKey: "autoRules")
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        UserDefaults.standard.set(StageRules.setFlag(flags, "auto", true), forKey: "flags")
        if sendPeer { send(type: "auto_rules", payload: ["body": body], on: live) }
    }

    private func messagesURL() -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let folder = dir.appendingPathComponent("BridgeLink", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("sms.txt")
    }

    func refreshFolderWatch() {
        if folderFlags.live() {
            startFolderWatch()
        } else {
            stopFolderWatch()
        }
    }

    func startFolderWatch() {
        stopFolderWatch()
        guard folderFlags.live() else { return }
        let root = sharedBox.root().path
        let box = FolderWatchBox { [weak self] in
            DispatchQueue.main.async { self?.armFolderDebounce() }
        }
        var context = FSEventStreamContext(
            version: 0,
            info: Unmanaged.passUnretained(box).toOpaque(),
            retain: nil,
            release: nil,
            copyDescription: nil
        )
        let flags = FSEventStreamCreateFlags(kFSEventStreamCreateFlagNoDefer)
        guard let stream = FSEventStreamCreate(
            nil,
            { _, info, _, _, _, _ in
                guard let info else { return }
                Unmanaged<FolderWatchBox>.fromOpaque(info).takeUnretainedValue().fire()
            },
            &context,
            [root] as CFArray,
            FSEventStreamEventId(kFSEventStreamEventIdSinceNow),
            1.0,
            flags
        ) else { return }
        folderWatchBox = box
        folderStream = stream
        FSEventStreamSetDispatchQueue(stream, folderPartQueue)
        FSEventStreamStart(stream)
    }

    func stopFolderWatch() {
        folderDebounce?.cancel()
        folderDebounce = nil
        if let stream = folderStream {
            FSEventStreamStop(stream)
            FSEventStreamInvalidate(stream)
            FSEventStreamRelease(stream)
            folderStream = nil
        }
        folderWatchBox = nil
    }

    func shipLarge(_ file: URL, path: String, size: Int64) {
        let queue = folderSendQueue
        queue.async { [weak self] in
            self?.shipLargeNow(file, path: path, size: size)
        }
    }

    private func shipLargeNow(_ file: URL, path: String, size: Int64) {
        guard let safe = Features.safeRelative(path) else { return }
        let name = (safe as NSString).lastPathComponent
        let id = String(Transfers.sha256Hex(Data("\(safe)\n\(size)".utf8)).prefix(32))
        guard linkOpen() else { return }
        guard let handle = try? FileHandle(forReadingFrom: file) else { return }
        defer { try? handle.close() }
        let began = sendAndWait(type: "file_begin", payload: [
            "id": id, "name": name, "path": safe, "dest": "folder", "size": String(size), "from": "0",
        ])
        if !began || !linkOpen() {
            _ = sendAndWait(type: "file_cancel", payload: ["id": id])
            return
        }
        let remote = awaitHave(id)
        let start = remote >= 0 && remote <= Int(size) ? remote : 0
        var hasher = SHA256()
        var offset = 0
        while offset < start {
            if !linkOpen() {
                _ = sendAndWait(type: "file_cancel", payload: ["id": id])
                return
            }
            let want = min(Transfers.chunk, start - offset)
            let chunk = (try? handle.read(upToCount: want)) ?? Data()
            if chunk.isEmpty {
                _ = sendAndWait(type: "file_cancel", payload: ["id": id])
                return
            }
            hasher.update(data: chunk)
            offset += chunk.count
        }
        while true {
            if !linkOpen() {
                _ = sendAndWait(type: "file_cancel", payload: ["id": id])
                return
            }
            let data = (try? handle.read(upToCount: Transfers.chunk)) ?? Data()
            if data.isEmpty { break }
            hasher.update(data: data)
            largeWindow.acquire()
            let payload = ["id": id, "offset": String(offset), "data": data.base64EncodedString()]
            offset += data.count
            let sent = sendTracked(type: "file_chunk", payload: payload)
            if !sent {
                largeWindow.release()
                _ = sendAndWait(type: "file_cancel", payload: ["id": id])
                return
            }
        }
        if offset != Int(size) || !linkOpen() {
            _ = sendAndWait(type: "file_cancel", payload: ["id": id])
            return
        }
        let sha = hasher.finalize().map { String(format: "%02x", $0) }.joined()
        _ = sendAndWait(type: "file_end", payload: ["id": id, "sha256": sha])
        DispatchQueue.main.async { [weak self] in
            self?.note("Sent \(name)")
        }
    }

    private func linkOpen() -> Bool {
        if Thread.isMainThread { return live != nil }
        return DispatchQueue.main.sync { self.live != nil }
    }

    private func awaitHave(_ id: String) -> Int {
        let gate = DispatchSemaphore(value: 0)
        var value = 0
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                gate.signal()
                return
            }
            self.waitHave(id: id, fallback: 0) { got in
                value = got
                gate.signal()
            }
        }
        gate.wait()
        return value
    }

    private func sendAndWait(type: String, payload: [String: String]) -> Bool {
        let gate = DispatchSemaphore(value: 0)
        var open = false
        DispatchQueue.main.async { [weak self] in
            guard let self, self.live != nil else {
                gate.signal()
                return
            }
            open = true
            self.send(type: type, payload: payload, on: self.live) { gate.signal() }
        }
        gate.wait()
        return open
    }

    private func sendTracked(type: String, payload: [String: String]) -> Bool {
        let gate = DispatchSemaphore(value: 0)
        var open = false
        DispatchQueue.main.async { [weak self] in
            guard let self, self.live != nil else {
                gate.signal()
                return
            }
            open = true
            self.send(type: type, payload: payload, on: self.live) { [weak self] in
                self?.largeWindow.release()
            }
            gate.signal()
        }
        gate.wait()
        return open
    }

    private func armFolderDebounce() {
        folderDebounce?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            guard self.folderFlags.live() else { return }
            DispatchQueue.main.async { [weak self] in
                self?.pushFolder()
            }
        }
        folderDebounce = work
        folderPartQueue.asyncAfter(deadline: .now() + 60, execute: work)
    }
}
