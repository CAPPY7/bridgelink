import AppKit
import BridgeLinkCore
import CoreImage
import Darwin
import Foundation
import Network
import Security
import ServiceManagement
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

func savedLockInt(_ key: String, _ fallback: Int) -> Int {
    if UserDefaults.standard.object(forKey: key) == nil { return fallback }
    let value = UserDefaults.standard.integer(forKey: key)
    return value == 0 ? fallback : value
}

enum Phase: Equatable {
    case idle
    case listening
    case paired(String)
    case failed(String)
}

@MainActor
final class LinkModel: ObservableObject {
    @Published var pin: String
    @Published var pinLeft = 120
    @Published var matchCode = ""
    @Published var phase: Phase = .idle
    @Published var addresses = ""
    @Published var addressChoices: [String] = []
    @Published var chosenAddress = ""
    @Published var listenPort: UInt16 = 8787
    @Published var pairedSummary = ""
    @Published var fingerprint = ""
    @Published var draft = ""
    @Published var lines: [String] = ["Waiting for the phone."]
    @Published var launchAtLogin = SMAppService.mainApp.status == .enabled
    @Published var clipboardMode = UserDefaults.standard.string(forKey: "clipboardMode") ?? "auto"
    @Published var pendingClipboard = ""
    @Published var pendingLink = ""
    @Published var linkDraft = ""
    @Published var phoneBattery = ""
    @Published var macBattery = "—"
    var openWindow: (() -> Void)?
    var openSetup: (() -> Void)?
    var closeSetup: (() -> Void)?
    var macLevel = -1
    var macCharging = false
    var sawMacBattery = false
    @Published var mirrorOn = Features.enabled(UserDefaults.standard.string(forKey: "flags") ?? "", "notify", fallback: false)
    @Published var showNotificationText = UserDefaults.standard.bool(forKey: "showNotificationText")
    @Published var linksAlways = UserDefaults.standard.bool(forKey: "linksAlways")
    @Published var dndSync = UserDefaults.standard.bool(forKey: "dndSync")
    @Published var phoneQuiet = false
    @Published var lastNotifyKey = ""
    @Published var canReply = false
    @Published var replyDraft = ""
    @Published var renameDraft = ""
    @Published var nowPlaying = "Nothing is playing"
    @Published var nowArt = ""
    var mediaWatch = false
    var mediaBusy = false
    @Published var clips: [StageFour.Clip] = []
    @Published var clipQuery = ""
    @Published var inbox: [StageFour.Item] = []
    @Published var inboxQuery = ""
    @Published var inboxFilter = "all"
    @Published var lockOn = UserDefaults.standard.bool(forKey: "lockOn")
    @Published var atDesk = UserDefaults.standard.bool(forKey: "atDesk")
    @Published var lockFar = savedLockInt("lockFar", 70)
    @Published var lockDelay = savedLockInt("lockDelay", 30)
    @Published var volumeText = "40"
    @Published var messages: [StageFive.Sms] = []
    @Published var smsTo = ""
    @Published var smsBody = ""
    @Published var smsSim = 0
    @Published var simCount = 1
    @Published var folderOn = false {
        didSet { folderFlags.update(on: folderOn, paused: folderPaused) }
    }
    @Published var folderPaused = false {
        didSet { folderFlags.update(on: folderOn, paused: folderPaused) }
    }
    let folderFlags = FolderFlags()
    @Published var folderPath = ""
    @Published var rules: [StageFive.Rule] = []
    let six = SixHold()
    var away: AwayLink?
    var relayAttempt = 0
    var relayJoin: DispatchWorkItem?
    @Published var ruleWhen = "connect"
    @Published var ruleThen = "alert"
    @Published var ruleExtra = ""
    @Published var trackpadOn = false
    var pointerExplained = false
    var batteryAuto = false
    let sharedBox = SharedBox()
    var beacon: LeaveBeacon?
    var linkGoneAt: Date?
    var leaveTimer: Timer?
    var lastMacNow = ""
    let fileCancel = CancelFlag()
    var phoneLowAlerted = false
    var phoneFullAlerted = false
    var dropPanel: DropPanel?
    var haves: [String: Int] = [:]
    var haveWaiters: [String: (Int) -> Void] = [:]
    var lastEcho = ""
    var macFinder: DispatchSourceTimer?
    var pasteCount = 0
    var focusTicks = 0
    var sawFocus = false
    var lastFocus = false

    let files = MacFiles()

    var listener: NWListener?
    var listenerGen = 0
    var listenAttempt = 0
    var pathMonitor: NWPathMonitor?
    var pinClock: DispatchSourceTimer?
    var silenceClock: DispatchSourceTimer?
    var lastRx = Date()
    var live: NWConnection?
    var pending: NWConnection?
    var pendingID = UUID()
    var pendingLinks: [UUID: NWConnection] = [:]
    var pendingSet = PendingSet()
    var liveID = UUID()
    var replyOnRelay = false
    var relayTrusted = false
    let queue = DispatchQueue(label: "app.bridgelink.mac")
    var book = DeviceBook()
    @Published var phoneList: [PairedDevice] = []
    var pendingGate = PendingGate()
    var pendingCount = 0
    var liveDeviceId = ""
    var awayDeviceId = ""
    var sasConnectionID: UUID?
    var sasPhoneCommit = ""
    var sasPhoneNonce = ""
    var sasMacNonce = ""
    var sasExpected = ""
    var sasName = "Phone"
    var sasMacConfirmed = false
    var sasPhoneCode: String?
    var sasFails = 0
    var gate: PairingGate
    var gateStarted = Int64(Date().timeIntervalSince1970 * 1000)

    init() {
        let token = PairingGate.newToken()
        pin = token
        gate = PairingGate(token: token, now: Int64(Date().timeIntervalSince1970 * 1000))
        loadBook()
        let saved = HistoryFile.load(from: historyURL())
        if !saved.isEmpty { lines = saved }
        refreshAddresses()
        refreshMacBatteryLabel()
        if ProcessInfo.processInfo.environment["BRIDGELINK_SHOTS"] != nil { return }
        loadEasy()
        start()
        watchNetwork()
        armPinClock()
        armSilenceClock()
    }

    func newPin() {
        let token = PairingGate.newToken()
        pin = token
        pinLeft = 120
        gateStarted = Int64(Date().timeIntervalSince1970 * 1000)
        gate = PairingGate(token: token, now: gateStarted)
        if case .paired = phase {} else { phase = .listening }
        note("New PIN. Phones that are already paired stay linked.")
    }

    func confirmMatch() {
        guard sasConnectionID != nil else {
            note("No phone is waiting to match a code.")
            return
        }
        sasMacConfirmed = true
        note("This Mac confirmed \(matchCode). Confirm the same code on the phone.")
        if let connection = pending, let id = sasConnectionID {
            finishSas(connection: connection, id: id)
        }
    }

    func forget() {
        if !liveDeviceId.isEmpty {
            send(type: "forget", payload: ["deviceId": liveDeviceId], on: live)
            book.forget(id: liveDeviceId)
            saveBook()
        }
        liveDeviceId = ""
        live?.cancel()
        live = nil
        phase = .listening
        note("Forgot the connected phone. Other paired phones stay linked.")
    }

    func toggleLogin() {
        do {
            if SMAppService.mainApp.status == .enabled {
                try SMAppService.mainApp.unregister()
            } else {
                try SMAppService.mainApp.register()
            }
            launchAtLogin = SMAppService.mainApp.status == .enabled
        } catch {
            note("Login item failed. Move BridgeLink to Applications, then try again. \(error.localizedDescription)")
        }
    }

    func quit() {
        NSApp.terminate(nil)
    }

    func refreshMacBatteryLabel() {
        if let sample = MacPower.sample() {
            macBattery = sample.charging ? "\(sample.level)% charging" : "\(sample.level)%"
        } else {
            macBattery = ""
        }
    }

    func showDesk() {
        openWindow?()
    }

    func mirrorLater() {
        showDesk()
        note("Open Mirror in the window. Allow the screen on the phone when it asks.")
    }

    func sendDropped(_ urls: [URL]) {
        for url in urls {
            sendFile(url)
        }
    }

    func setMirror(_ on: Bool) {
        mirrorOn = on
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        UserDefaults.standard.set(MirrorFlags.apply(flags, on), forKey: "flags")
    }

    func setShowNotificationText(_ on: Bool) {
        showNotificationText = on
        UserDefaults.standard.set(on, forKey: "showNotificationText")
    }

    func setClipboardMode(_ mode: String) {
        clipboardMode = mode
        UserDefaults.standard.set(mode, forKey: "clipboardMode")
    }

    func acceptClipboard() {
        let text = pendingClipboard
        pendingClipboard = ""
        guard !text.isEmpty else { return }
        applyClipboard(text)
    }

    func notifications() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            if settings.authorizationStatus == .denied {
                Task { @MainActor in
                    if let url = URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension") {
                        NSWorkspace.shared.open(url)
                    }
                    self.note("Notifications are off. Turn them on for BridgeLink in System Settings.")
                }
                return
            }
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { ok, _ in
                Task { @MainActor in
                    self.note(ok ? "Notifications allowed" : "Notifications were not allowed")
                }
            }
        }
    }

    func sendDraft() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        draft = ""
        guard !text.isEmpty else { return }
        send(type: "text", payload: ["body": text], on: live)
        note("Sent")
    }

    func sendLink() {
        guard let url = Transfers.acceptUrl(linkDraft) else {
            note("Only http and https links can be sent.")
            return
        }
        linkDraft = ""
        send(type: "link", payload: ["url": url], on: live)
        note("Sent a link")
    }

    func openPendingLink() {
        guard let url = URL(string: pendingLink) else { return }
        pendingLink = ""
        NSWorkspace.shared.open(url)
    }

    func pickFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = true
        guard panel.runModal() == .OK else { return }
        for url in panel.urls { sendFile(url) }
    }

    func sendFile(_ url: URL) {
        guard case .paired = phase else {
            note("Connect and pair before sending")
            return
        }
        fileCancel.on = false
        let flag = fileCancel
        files.send(url: url, emit: { [weak self] type, payload, finished in
            Task { @MainActor in
                guard let self else {
                    finished()
                    return
                }
                self.send(type: type, payload: payload, on: self.live, done: finished)
            }
        }, wait: { [weak self] id, fallback in
            let gate = DispatchSemaphore(value: 0)
            var value = fallback
            DispatchQueue.main.async {
                self?.waitHave(id: id, fallback: fallback) { got in
                    value = got
                    gate.signal()
                }
            }
            gate.wait()
            return value
        }, progress: { [weak self] line in
            Task { @MainActor in self?.note(line) }
        }, cancelled: {
            flag.on
        }, done: { [weak self] line in
            Task { @MainActor in self?.note(line) }
        })
    }

    func waitHave(id: String, fallback: Int, done: @escaping (Int) -> Void) {
        if let known = haves.removeValue(forKey: id) {
            done(known)
            return
        }
        haveWaiters[id] = done
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
            guard let self, let waiter = self.haveWaiters.removeValue(forKey: id) else { return }
            waiter(self.haves.removeValue(forKey: id) ?? fallback)
        }
    }

    func noteHave(id: String, offset: Int) {
        if let waiter = haveWaiters.removeValue(forKey: id) {
            waiter(offset)
        } else {
            haves[id] = offset
        }
    }

    func sendClipboard() {
        let board = NSPasteboard.general
        if pasteMarked(board) {
            note("Skipped a password-manager clipboard")
            return
        }
        let text = board.string(forType: .string) ?? ""
        if !text.isEmpty && Features.sensitiveText(text) {
            note("Skipped sensitive clipboard")
            return
        }
        if text.isEmpty {
            if let image = board.data(forType: .png) ?? board.data(forType: .tiff), StageRules.imageFits(image.count) {
                let key = Features.echoKey(image.base64EncodedString())
                if lastEcho == key { return }
                lastEcho = key
                send(type: "clipboard_image", payload: ["data": image.base64EncodedString()], on: live)
                note("Sent a clipboard image")
            }
            return
        }
        if Features.isEcho(lastEcho, text) { return }
        rememberClip(text)
        send(type: "clipboard", payload: ["body": text], on: live)
        note("Sent clipboard")
    }

    func pasteMarked(_ board: NSPasteboard) -> Bool {
        let types = board.types ?? []
        let concealed = types.contains(NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType"))
        let transient = types.contains(NSPasteboard.PasteboardType("org.nspasteboard.TransientType"))
        return StageRules.skipPasswordClip(concealed, transient, false)
    }

    func ring() {
        send(type: "ring", payload: ["flash": "0", "vibrate": "1"], on: live)
        note("Ringing the phone")
    }

    func stopRing() {
        send(type: "find_stop", payload: [:], on: live)
        note("Asked the phone to stop")
    }
}

