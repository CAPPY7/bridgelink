import AppKit
import BridgeLinkCore
import Foundation
import Network

enum ClipFile {
    static func url() -> URL { store("clip-history.txt") }

    static func load() -> [StageFour.Clip] {
        guard let raw = try? String(contentsOf: url(), encoding: .utf8) else { return [] }
        return StageFour.decodeClips(raw)
    }

    static func save(_ clips: [StageFour.Clip]) {
        try? StageFour.encodeClips(clips).write(to: url(), atomically: true, encoding: .utf8)
    }
}

enum InboxFile {
    static func url() -> URL { store("inbox.txt") }

    static func load() -> [StageFour.Item] {
        guard let raw = try? String(contentsOf: url(), encoding: .utf8) else { return [] }
        return StageFour.decodeInbox(raw)
    }

    static func save(_ items: [StageFour.Item]) {
        try? StageFour.encodeInbox(items).write(to: url(), atomically: true, encoding: .utf8)
    }
}

private func store(_ name: String) -> URL {
    let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        ?? URL(fileURLWithPath: NSTemporaryDirectory())
    let folder = dir.appendingPathComponent("BridgeLink", isDirectory: true)
    try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    return folder.appendingPathComponent(name)
}

extension LinkModel {
    func loadEasy() {
        clips = ClipFile.load()
        inbox = InboxFile.load()
        beacon = LeaveBeacon()
        beacon?.start()
        loadFive()
    }

    func rememberClip(_ text: String) {
        clips = StageFour.add(clips, text, Features.sensitiveText(text), nowMs())
        ClipFile.save(clips)
    }

    func rememberInbox(kind: String, title: String, detail: String) {
        let hidden = Features.sensitiveText(detail)
        inbox = StageFour.addInbox(inbox, kind, title, detail, hidden, nowMs())
        InboxFile.save(inbox)
    }

    func pinClip(_ id: String) {
        let on = !(clips.first { $0.id == id }?.pinned ?? false)
        clips = StageFour.pin(clips, id, on)
        ClipFile.save(clips)
    }

    func deleteClip(_ id: String) {
        clips = StageFour.delete(clips, id)
        ClipFile.save(clips)
    }

    func useClip(_ id: String) {
        guard let clip = clips.first(where: { $0.id == id }) else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(clip.text, forType: .string)
        note("Copied from history")
    }

    func sendMedia(_ raw: String) {
        guard case .paired = phase else {
            note("Connect and pair before sending")
            return
        }
        guard let parts = StageFour.splitCommand(raw) else {
            note("That media button is not supported")
            return
        }
        send(type: "media", payload: ["action": parts.action, "volume": parts.volume], on: live)
        note("Sent a media command")
    }

    func pollMacMedia() {
        let open = mediaWatch
        let paired: Bool
        if case .paired = phase { paired = true } else { paired = false }
        guard MediaWatch.shouldPoll(open, paired, mediaBusy) else { return }
        mediaBusy = true
        Task.detached { [weak self] in
            let sample = MacMedia.now()
            await MainActor.run {
                guard let self else { return }
                self.mediaBusy = false
                let stillOpen = self.mediaWatch
                let stillPaired: Bool
                if case .paired = self.phase { stillPaired = true } else { stillPaired = false }
                guard MediaWatch.shouldPoll(stillOpen, stillPaired, false) else { return }
                let line = StageFour.nowLine(sample.title, sample.artist, sample.playing == "1")
                if line == self.lastMacNow { return }
                self.lastMacNow = line
                self.nowPlaying = line
                self.send(type: "media_now", payload: [
                    "title": sample.title,
                    "artist": sample.artist,
                    "playing": sample.playing,
                    "volume": sample.volume,
                ], on: self.live)
            }
        }
    }

    func setLock(on: Bool) {
        lockOn = on
        pushLock()
    }

    func setDesk(_ on: Bool) {
        atDesk = on
        pushLock()
        note(on ? "At the desk. The Mac will not lock." : "Leaving the desk can lock the Mac.")
    }

    func pushLock() {
        let body = StageFour.formatSetup(on: lockOn, far: lockFar, delay: lockDelay, desk: atDesk)
        guard let setup = StageFour.parseSetup(body) else { return }
        lockFar = setup.far
        lockDelay = setup.delay
        UserDefaults.standard.set(lockOn, forKey: "lockOn")
        UserDefaults.standard.set(atDesk, forKey: "atDesk")
        UserDefaults.standard.set(lockFar, forKey: "lockFar")
        UserDefaults.standard.set(lockDelay, forKey: "lockDelay")
        guard case .paired = phase else {
            note(lockOn ? "Lock when you leave is on. Pair the phone so it can watch the signal." : "Lock when you leave is off.")
            return
        }
        send(type: "lock_setup", payload: ["body": body], on: live)
        note(lockOn ? "Lock when you leave is on. This Mac never unlocks itself from the phone." : "Lock when you leave is off.")
    }

    func handleEasy(_ envelope: ParsedEnvelope, _ connection: NWConnection) -> Bool {
        switch envelope.type {
        case "media":
            if let watch = MediaWatch.command(envelope.text("action")) {
                mediaWatch = watch.action == "watch"
                if mediaWatch { pollMacMedia() }
                return true
            }
            let action = envelope.text("action")
            let volume = envelope.text("volume")
            let connection = connection
            Task.detached { [weak self] in
                let line = MacMedia.perform(action, volume)
                let sample = MacMedia.now()
                await MainActor.run {
                    guard let self else { return }
                    self.note(line)
                    self.nowPlaying = StageFour.nowLine(sample.title, sample.artist, sample.playing == "1")
                    self.send(type: "media_now", payload: [
                        "title": sample.title,
                        "artist": sample.artist,
                        "playing": sample.playing,
                        "volume": sample.volume,
                    ], on: connection)
                }
            }
            return true
        case "media_now":
            nowPlaying = StageFour.nowLine(envelope.text("title"), envelope.text("artist"), envelope.text("playing") == "1")
            nowArt = envelope.text("art")
            note(nowPlaying)
            return true
        case "lock":
            if StageFour.lockRequest(envelope.text("reason")) {
                note(MacLock.lock())
                fireAuto("leave", "")
            } else {
                note("Ignored a request to unlock. BridgeLink never unlocks the Mac.")
            }
            return true
        case "lock_setup":
            guard let setup = StageFour.parseSetup(envelope.text("body")) else {
                note("Lock settings were not understood")
                return true
            }
            lockOn = setup.on == 1
            atDesk = setup.desk == 1
            lockFar = setup.far
            lockDelay = setup.delay
            UserDefaults.standard.set(lockOn, forKey: "lockOn")
            UserDefaults.standard.set(atDesk, forKey: "atDesk")
            UserDefaults.standard.set(lockFar, forKey: "lockFar")
            UserDefaults.standard.set(lockDelay, forKey: "lockDelay")
            note(lockOn ? "Lock when you leave is on" : "Lock when you leave is off")
            return true
        default:
            return false
        }
    }

    private func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
