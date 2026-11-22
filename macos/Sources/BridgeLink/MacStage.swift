import AppKit
import BridgeLinkCore
import Foundation
import Intents

/// Shared by the file sender. The file queue can read it without touching the screen.
final class CancelFlag: @unchecked Sendable {
    var on = false
}

extension LinkModel {
    func cancelSend() {
        fileCancel.on = true
        note("Cancelling the file send")
    }

    func showDropZone() {
        if dropPanel == nil {
            dropPanel = DropPanel { [weak self] urls in
                Task { @MainActor in
                    self?.fileCancel.on = false
                    for url in urls { self?.sendFile(url) }
                }
            }
        }
        dropPanel?.orderFrontRegardless()
    }

    func renamePhone() {
        guard !liveDeviceId.isEmpty else {
            note("Pair a phone before renaming it")
            return
        }
        book.rename(id: liveDeviceId, name: renameDraft)
        saveBook()
        note("Renamed the phone")
    }

    func forgetSaved(_ id: String) {
        if id == liveDeviceId {
            forget()
            return
        }
        let name = book.devices.first { $0.id == id }?.name ?? "that phone"
        book.forget(id: id)
        saveBook()
        note("Forgot \(name). The phone you are using stays linked.")
    }

    func setLinksAlways(_ on: Bool) {
        linksAlways = on
        UserDefaults.standard.set(on, forKey: "linksAlways")
    }

    func setDndSync(_ on: Bool) {
        dndSync = on
        UserDefaults.standard.set(on, forKey: "dndSync")
    }

    func replyFromBanner(_ key: String, _ text: String) {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, !body.isEmpty else { return }
        send(type: "notify_reply", payload: ["key": key, "body": body], on: live)
        note("Sent a reply")
    }

    func replyToNotification() {
        let text = replyDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !lastNotifyKey.isEmpty, !text.isEmpty else {
            note("There is no notification to reply to")
            return
        }
        send(type: "notify_reply", payload: ["key": lastNotifyKey, "body": text], on: live)
        replyDraft = ""
        note("Sent a reply")
    }

    func dismissNotification() {
        guard !lastNotifyKey.isEmpty else { return }
        send(type: "notify_dismiss", payload: ["key": lastNotifyKey], on: live)
        note("Asked the phone to dismiss that notification")
    }

    func startClipboardWatch() {
        pasteCount = NSPasteboard.general.changeCount
        Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.clipboardTick() }
        }
    }

    func clipboardTick() {
        let board = NSPasteboard.general
        if board.changeCount != pasteCount {
            pasteCount = board.changeCount
            if pasteMarked(board) { return }
            let text = board.string(forType: .string) ?? ""
            let image = board.data(forType: .png) ?? board.data(forType: .tiff)
            if clipboardMode == "auto", case .paired = phase, !text.isEmpty || image != nil {
                sendClipboard()
            }
            if !text.isEmpty { rememberClip(text) }
        }
        focusTicks += 1
        if focusTicks % 8 == 0 {
            sendMacFocus()
            pollMacMedia()
        }
    }

    func sendMacFocus() {
        guard dndSync, case .paired = phase else { return }
        let on = INFocusStatusCenter.default.focusStatus.isFocused == true
        if sawFocus && on == lastFocus { return }
        sawFocus = true
        lastFocus = on
        send(type: "dnd", payload: ["on": on ? "1" : "0", "label": "mac"], on: live)
    }

    func drainShareInbox() {
        let file = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Application Support/BridgeLink/share-inbox.txt")
        guard let text = try? String(contentsOf: file, encoding: .utf8), !text.isEmpty else { return }
        try? FileManager.default.removeItem(at: file)
        fileCancel.on = false
        for line in text.split(separator: "\n") {
            let path = String(line).trimmingCharacters(in: .whitespacesAndNewlines)
            if path.isEmpty { continue }
            sendFile(URL(fileURLWithPath: path))
        }
    }
}

final class DropPanel: NSPanel {
    init(onFiles: @escaping ([URL]) -> Void) {
        super.init(
            contentRect: NSRect(x: 80, y: 80, width: 240, height: 120),
            styleMask: [.titled, .closable, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        title = "Drop files"
        isFloatingPanel = true
        level = .floating
        collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        let view = DropTarget(frame: NSRect(x: 0, y: 0, width: 240, height: 120))
        view.onFiles = onFiles
        contentView = view
    }

    required init?(coder: NSCoder) {
        return nil
    }
}

final class DropTarget: NSView {
    var onFiles: (([URL]) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        registerForDraggedTypes([.fileURL])
    }

    required init?(coder: NSCoder) {
        return nil
    }

    override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation { .copy }

    override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
        let urls = sender.draggingPasteboard.readObjects(forClasses: [NSURL.self], options: nil) as? [URL] ?? []
        if urls.isEmpty { return false }
        onFiles?(urls)
        return true
    }

    override func draw(_ dirtyRect: NSRect) {
        NSColor.windowBackgroundColor.setFill()
        bounds.fill()
        let text = "Drop files to send" as NSString
        text.draw(at: NSPoint(x: 28, y: 48), withAttributes: [
            .font: NSFont.systemFont(ofSize: 16, weight: .medium),
        ])
    }
}
