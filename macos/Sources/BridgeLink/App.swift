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
    @Published var mirrorOn = Features.enabled(UserDefaults.standard.string(forKey: "flags") ?? "", "notify", fallback: false)
    private var haves: [String: Int] = [:]
    private var haveWaiters: [String: (Int) -> Void] = [:]
    private var lastEcho = ""
    private var macFinder: DispatchSourceTimer?

    private let files = MacFiles()

    private var listener: NWListener?
    private var listenerGen = 0
    private var listenAttempt = 0
    private var pathMonitor: NWPathMonitor?
    private var pinClock: DispatchSourceTimer?
    private var silenceClock: DispatchSourceTimer?
    private var lastRx = Date()
    private var live: NWConnection?
    private var pending: NWConnection?
    private var pendingID = UUID()
    private var liveID = UUID()
    private let queue = DispatchQueue(label: "app.bridgelink.mac")
    private var book = DeviceBook()
    private var pendingGate = PendingGate()
    private var pendingCount = 0
    private var liveDeviceId = ""
    private var sasConnectionID: UUID?
    private var sasPhoneCommit = ""
    private var sasPhoneNonce = ""
    private var sasMacNonce = ""
    private var sasExpected = ""
    private var sasName = "Phone"
    private var sasMacConfirmed = false
    private var sasPhoneCode: String?
    private var sasFails = 0
    private var gate: PairingGate
    private var gateStarted = Int64(Date().timeIntervalSince1970 * 1000)

    init() {
        let token = PairingGate.newToken()
        pin = token
        gate = PairingGate(token: token, now: Int64(Date().timeIntervalSince1970 * 1000))
        loadBook()
        refreshAddresses()
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

    func setMirror(_ on: Bool) {
        mirrorOn = on
        UserDefaults.standard.set(on ? "notify=1,otp=1" : "notify=0,otp=0", forKey: "flags")
    }

    func setClipboardMode(_ mode: String) {
        clipboardMode = mode
        UserDefaults.standard.set(mode, forKey: "clipboardMode")
    }
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
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url else { return }
        sendFile(url)
    }

    func sendFile(_ url: URL) {
        guard case .paired = phase else {
            note("Connect and pair before sending")
            return
        }
        files.send(url: url, emit: { [weak self] type, payload in
            Task { @MainActor in self?.send(type: type, payload: payload, on: self?.live) }
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

    private func noteHave(id: String, offset: Int) {
        if let waiter = haveWaiters.removeValue(forKey: id) {
            waiter(offset)
        } else {
            haves[id] = offset
        }
    }

    func sendClipboard() {
        let text = NSPasteboard.general.string(forType: .string) ?? ""
        if Features.isEcho(lastEcho, text) {
            note("Skipped a clipboard echo")
            return
        }
        send(type: "clipboard", payload: ["body": text], on: live)
        note(text.isEmpty ? "Clipboard was empty" : "Sent clipboard")
    }

    func ring() {
        send(type: "ring", payload: ["flash": "0", "vibrate": "1"], on: live)
        note("Ringing the phone")
    }

    func stopRing() {
        send(type: "find_stop", payload: [:], on: live)
        note("Asked the phone to stop")
    }

    private func start() {
        listenAttempt = 0
        openListener()
    }

    private func openListener() {
        listenerGen += 1
        let gen = listenerGen
        listener?.cancel()
        let portNumber = UInt16(8787 + listenAttempt)
        guard let port = NWEndpoint.Port(rawValue: portNumber) else { return }
        do {
            let loaded = try MacIdentity.load()
            fingerprint = loaded.fingerprint
            let tls = NWProtocolTLS.Options()
            guard let secIdentity = sec_identity_create(loaded.identity) else {
                phase = .failed("Could not use the TLS identity")
                return
            }
            sec_protocol_options_set_local_identity(tls.securityProtocolOptions, secIdentity)
            let params = NWParameters(tls: tls)
            params.allowLocalEndpointReuse = true
            let listener = try NWListener(using: params, on: port)
            listener.service = NWListener.Service(name: "BridgeLink", type: "_bridgelink._tcp")
            listener.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in self?.accept(connection) }
            }
            listener.stateUpdateHandler = { [weak self] state in
                Task { @MainActor in
                    guard let self, self.listenerGen == gen else { return }
                    switch state {
                    case .ready:
                        self.listenPort = listener.port?.rawValue ?? portNumber
                        if case .paired = self.phase {} else { self.phase = .listening }
                    case .failed(let error):
                        self.listenAttempt += 1
                        if self.listenAttempt < 8 {
                            self.openListener()
                        } else {
                            self.phase = .failed(error.localizedDescription)
                        }
                    default:
                        break
                    }
                }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            phase = .failed(String(describing: error))
        }
    }

    private func watchNetwork() {
        let monitor = NWPathMonitor()
        pathMonitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self else { return }
                self.refreshAddresses()
                if path.status == .satisfied, self.listener == nil {
                    self.start()
                }
            }
        }
        monitor.start(queue: queue)
    }

    private func refreshAddresses() {
        let list = Self.lanAddresses()
        addressChoices = list
        addresses = list.isEmpty ? "No Wi-Fi address yet" : list.joined(separator: "\n")
        if chosenAddress.isEmpty || !list.contains(chosenAddress) {
            chosenAddress = list.first ?? ""
        }
    }

    private func armPinClock() {
        let clock = DispatchSource.makeTimerSource(queue: queue)
        clock.schedule(deadline: .now() + 1, repeating: 1)
        clock.setEventHandler { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                if self.pinLeft <= 1 {
                    self.newPin()
                } else {
                    self.pinLeft -= 1
                }
            }
        }
        pinClock = clock
        clock.resume()
    }

    private func armSilenceClock() {
        let clock = DispatchSource.makeTimerSource(queue: queue)
        clock.schedule(deadline: .now() + 15, repeating: 15)
        clock.setEventHandler { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                guard case .paired = self.phase else { return }
                if Date().timeIntervalSince(self.lastRx) > 45 {
                    self.live?.cancel()
                    self.live = nil
                    self.liveDeviceId = ""
                    self.phase = .listening
                    self.note("The phone went quiet. Still listening.")
                }
            }
        }
        silenceClock = clock
        clock.resume()
    }

    private func accept(_ connection: NWConnection) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let ip = Self.endpointHost(connection)
        if !pendingGate.admit(ip: ip, openCount: pendingCount, now: now) {
            connection.cancel()
            note("Refused an extra connection so a real phone can still pair.")
            return
        }
        let id = UUID()
        pendingCount += 1
        pendingID = id
        pending = connection
        connection.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                guard let self, self.pendingID == id, self.liveID != id else { return }
                if case .failed = state {
                    self.finishPending(id)
                    self.note("A connection failed before it paired. The current phone stays linked.")
                }
            }
        }
        connection.start(queue: queue)
        receive(connection, id: id, buffer: FrameBuffer())
        queue.asyncAfter(deadline: .now() + 10) { [weak self] in
            Task { @MainActor in
                guard let self, self.pendingID == id, self.liveID != id else { return }
                if self.sasConnectionID == id { return }
                connection.cancel()
                self.finishPending(id)
                self.note("A connection did not pair in time.")
            }
        }
        queue.asyncAfter(deadline: .now() + 120) { [weak self] in
            Task { @MainActor in
                guard let self, self.sasConnectionID == id, self.liveID != id else { return }
                connection.cancel()
                self.clearSas()
                self.finishPending(id)
                self.note("The match code expired. Connect again.")
            }
        }
    }

    private func finishPending(_ id: UUID) {
        if pendingID == id { pending = nil }
        pendingCount = max(0, pendingCount - 1)
    }

    private func receive(_ connection: NWConnection, id: UUID, buffer: FrameBuffer) {
        var buffer = buffer
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            Task { @MainActor in
                guard let self else { return }
                if self.liveID != id && self.pendingID != id { return }
                let stillPending = self.liveID != id
                if let data {
                    do {
                        let messages = try buffer.push(data)
                        for message in messages {
                            self.handle(message, connection: connection, id: id, pending: stillPending)
                        }
                    } catch {
                        connection.cancel()
                        self.note("Closed a connection that sent too much data.")
                        return
                    }
                }
                if isComplete || error != nil {
                    if self.pendingID == id && self.liveID != id { self.pending = nil }
                    if self.liveID == id {
                        self.live = nil
                        self.phase = .listening
                        self.note("Phone disconnected")
                    }
                    return
                }
                self.receive(connection, id: id, buffer: buffer)
            }
        }
    }

    private func handle(_ data: Data, connection: NWConnection, id: UUID, pending: Bool) {
        guard let envelope = try? EnvelopeCodec.parse(data) else { return }
        lastRx = Date()
        if envelope.type == "hello" {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let token = envelope.text("token")
            let proof = envelope.text("proof")
            let name = envelope.text("name").isEmpty ? "Phone" : envelope.text("name")
            if !token.isEmpty, let device = book.match(token: token) {
                book.touch(id: device.id, now: now)
                saveBook()
                promote(connection, id: id, name: device.name, token: token, deviceId: device.id)
                return
            }
            if !token.isEmpty {
                pendingGate.fail(ip: Self.endpointHost(connection), now: now)
                send(type: "error", payload: ["code": "bad_token", "body": "This phone is not paired"], on: connection)
                connection.cancel()
                finishPending(id)
                note("Rejected an unknown phone. Paired phones stay linked.")
                return
            }
            let phoneCommit = envelope.text("commit")
            if proof.isEmpty && !phoneCommit.isEmpty {
                let macNonce = UUID().uuidString.replacingOccurrences(of: "-", with: "")
                sasConnectionID = id
                sasPhoneCommit = phoneCommit
                sasMacNonce = macNonce
                sasPhoneNonce = ""
                sasName = name
                sasExpected = ""
                sasMacConfirmed = false
                sasPhoneCode = nil
                sasFails = 0
                matchCode = ""
                send(type: "sas", payload: ["commit": sasCommit(macNonce)], on: connection)
                note("Phone started a match. The code appears after both sides commit.")
                return
            }
            let expected = pairingProof(pin: pin, fingerprint: fingerprint)
            if !proof.isEmpty && gate.observe(matched: constantTimeEquals(expected, proof), now: now) {
                let issued = UUID().uuidString.replacingOccurrences(of: "-", with: "")
                let device = book.remember(name: name, token: issued, now: now)
                saveBook()
                promote(connection, id: id, name: name, token: issued, deviceId: device.id)
            } else {
                pendingGate.fail(ip: Self.endpointHost(connection), now: now)
                send(type: "error", payload: ["code": "bad_proof", "body": "Wrong or expired PIN"], on: connection)
                connection.cancel()
                finishPending(id)
                note("Rejected a PIN. Paired phones stay linked.")
            }
            return
        }
        if envelope.type == "sas_reveal" && pending {
            guard id == sasConnectionID else { return }
            let revealed = envelope.text("nonce")
            if !sasRevealMatches(commit: sasPhoneCommit, nonce: revealed) {
                send(type: "error", payload: ["code": "bad_code", "body": "The other side changed its code"], on: connection)
                connection.cancel()
                clearSas()
                finishPending(id)
                note("The phone changed its code. Paired phones stay linked.")
                return
            }
            sasPhoneNonce = revealed
            send(type: "sas_reveal", payload: ["nonce": sasMacNonce], on: connection)
            sasExpected = sasCode(macFingerprint: fingerprint, phoneNonce: revealed, macNonce: sasMacNonce)
            matchCode = sasExpected
            note("Match code \(sasExpected). Tap Codes match on both screens.")
            return
        }
        if envelope.type == "sas_confirm" && pending {
            guard id == sasConnectionID else { return }
            sasPhoneCode = envelope.text("code")
            finishSas(connection: connection, id: id)
            return
        }
        guard !pending, liveID == id else {
            send(type: "error", payload: ["code": "pair_first", "body": "Pair first"], on: connection)
            return
        }
        switch envelope.type {
        case "text":
            note(envelope.text("body"))
            notify("Note from the phone", envelope.text("body"))
        case "clipboard":
            let text = envelope.text("body")
            if clipboardMode == "off" {
                note("Ignored clipboard from the phone")
                return
            }
            if clipboardMode == "ask" || looksSensitive(text) {
                pendingClipboard = text
                note(looksSensitive(text) ? "Sensitive clipboard is waiting" : "Clipboard is waiting")
                notify("Clipboard from the phone", "Open BridgeLink to accept it")
                return
            }
            applyClipboard(text)
        case "ping":
            send(type: "pong", payload: [:], on: connection)
        case "link":
            if let url = Transfers.acceptUrl(envelope.text("url")) {
                pendingLink = url
                note("Link from the phone. Open it only if you trust it.")
                notify("Link from the phone", "Open BridgeLink to open it")
            } else {
                note("Refused a link that is not http or https")
            }
        case "file_begin":
            let fileId = envelope.text("id")
            let from = Int(envelope.text("from")) ?? 0
            files.begin(id: fileId, name: envelope.text("name"), size: Int(envelope.text("size")) ?? -1, from: from) { [weak self] line, have in
                Task { @MainActor in
                    self?.send(type: "file_have", payload: ["id": fileId, "offset": String(have)], on: self?.live)
                    self?.note(line)
                }
            }
        case "file_chunk":
            files.chunk(id: envelope.text("id"), offset: Int(envelope.text("offset")) ?? -1, base64: envelope.text("data")) { [weak self] line in
                if let line { Task { @MainActor in self?.note(line) } }
            }
        case "file_end":
            files.finish(id: envelope.text("id"), sha256: envelope.text("sha256")) { [weak self] line in
                Task { @MainActor in
                    self?.note(line)
                    self?.notify("File from the phone", line)
                }
            }
        case "file_cancel":
            files.cancel(id: envelope.text("id"))
            note("File cancelled")
        case "file_have":
            noteHave(id: envelope.text("id"), offset: Int(envelope.text("offset")) ?? 0)
        case "find_mac":
            startFindMac()
        case "find_stop":
            stopFindMac()
            note("Find stopped")
        case "notify":
            let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
            guard Features.enabled(flags, "notify", fallback: false) else { return }
            let title = envelope.text("title")
            notify(title.isEmpty ? "Notification" : title, envelope.text("app"))
            note("Notification from the phone")
        case "otp":
            let otpFlags = UserDefaults.standard.string(forKey: "flags") ?? ""
            guard Features.enabled(otpFlags, "otp", fallback: false) else { return }
            copyConcealed(envelope.text("body"))
            note("Copied a code. It will clear in a minute.")
        case "battery":
            let level = envelope.text("level")
            phoneBattery = level.isEmpty ? "" : "Phone battery \(level)%"
        case "forget":
            let device = envelope.text("deviceId")
            if !device.isEmpty {
                book.forget(id: device)
            } else if !liveDeviceId.isEmpty {
                book.forget(id: liveDeviceId)
            }
            saveBook()
            live?.cancel()
            live = nil
            liveDeviceId = ""
            phase = .listening
            note("The phone forgot this Mac")
        default:
            note(envelope.type)
            send(type: "error", payload: ["code": "unsupported_type", "body": "Unsupported"], on: connection)
        }
    }

    private func promote(_ connection: NWConnection, id: UUID, name: String, token: String, deviceId: String) {
        guard pendingID == id || liveID == id else { return }
        live?.cancel()
        liveID = id
        live = connection
        liveDeviceId = deviceId
        pending = nil
        pendingCount = max(0, pendingCount - 1)
        phase = .paired(name)
        send(type: "paired", payload: ["token": token, "deviceId": deviceId], on: connection)
        lastRx = Date()
        note("Paired with \(name)")
    }

    private func finishSas(connection: NWConnection, id: UUID) {
        guard id == sasConnectionID else { return }
        let decision = sasDecision(macConfirmed: sasMacConfirmed, phoneCode: sasPhoneCode, expected: sasExpected)
        switch decision {
        case .waitForMac:
            note("The phone confirmed. Tap Codes match on this Mac if the codes are the same.")
        case .accept:
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let issued = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            let device = book.remember(name: sasName, token: issued, now: now)
            saveBook()
            clearSas()
            promote(connection, id: id, name: sasName, token: issued, deviceId: device.id)
        case .reject:
            sasFails += 1
            if sasFails >= 3 {
                pendingGate.fail(ip: Self.endpointHost(connection), now: Int64(Date().timeIntervalSince1970 * 1000))
                send(type: "error", payload: ["code": "bad_code", "body": "The match codes did not agree"], on: connection)
                connection.cancel()
                clearSas()
                finishPending(id)
                note("Match codes did not agree. Paired phones stay linked.")
            } else {
                sasPhoneCode = nil
                note("Those codes do not match. Look at both screens.")
            }
        }
    }

    private func clearSas() {
        sasConnectionID = nil
        sasPhoneCode = nil
        sasMacConfirmed = false
        matchCode = ""
    }

    private func loadBook() {
        guard let data = UserDefaults.standard.data(forKey: "devices"),
              let decoded = try? JSONDecoder().decode([PairedDevice].self, from: data) else { return }
        book = DeviceBook(devices: decoded)
        publishPairs()
    }

    private func saveBook() {
        if let data = try? JSONEncoder().encode(book.devices) {
            UserDefaults.standard.set(data, forKey: "devices")
        }
        publishPairs()
    }

    private func publishPairs() {
        pairedSummary = book.devices.map(\.name).joined(separator: ", ")
    }

    private func applyClipboard(_ text: String) {
        let board = NSPasteboard.general
        board.clearContents()
        board.setString(text, forType: .string)
        lastEcho = Features.echoKey(text)
        note("Clipboard updated")
        notify("Clipboard from the phone", "Clipboard updated")
    }

    private func copyConcealed(_ text: String) {
        guard !text.isEmpty else { return }
        let board = NSPasteboard.general
        board.clearContents()
        board.setString(text, forType: .string)
        board.setString("", forType: NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType"))
        lastEcho = Features.echoKey(text)
        DispatchQueue.main.asyncAfter(deadline: .now() + 60) {
            if NSPasteboard.general.string(forType: .string) == text {
                NSPasteboard.general.clearContents()
            }
        }
    }

    private func send(type: String, payload: [String: String], on connection: NWConnection?) {
        let frame = encodeFrame(json: EnvelopeCodec.json(type: type, from: "mac", payload: payload))
        connection?.send(content: frame, completion: .contentProcessed { _ in })
    }

    private func note(_ line: String) {
        lines.insert(line, at: 0)
        if lines.count > 200 { lines.removeLast(lines.count - 200) }
    }

    private func startFindMac() {
        stopFindMac()
        note("The phone is ringing this Mac")
        let clock = DispatchSource.makeTimerSource(queue: .main)
        clock.schedule(deadline: .now(), repeating: .seconds(1))
        clock.setEventHandler { NSSound.beep() }
        clock.resume()
        macFinder = clock
        DispatchQueue.main.asyncAfter(deadline: .now() + 30) { [weak self] in self?.stopFindMac() }
    }

    private func stopFindMac() {
        macFinder?.cancel()
        macFinder = nil
    }

    private func notify(_ title: String, _ body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    private static func lanAddresses() -> [String] {
        var result: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return result }
        defer { freeifaddrs(ifaddr) }
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let item = cursor {
            let flags = Int32(item.pointee.ifa_flags)
            if (flags & IFF_UP) != 0, (flags & IFF_LOOPBACK) == 0,
               let address = item.pointee.ifa_addr, address.pointee.sa_family == UInt8(AF_INET) {
                var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                let interfaceName = String(cString: item.pointee.ifa_name)
                if getnameinfo(address, socklen_t(address.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                    let value = String(cString: host)
                    if preferredLanAddress(interface: interfaceName, address: value) { result.append(value) }
                }
            }
            cursor = item.pointee.ifa_next
        }
        return result
    }

    private static func endpointHost(_ connection: NWConnection) -> String {
        switch connection.endpoint {
        case .hostPort(let host, _):
            return "\(host)"
        default:
            return "unknown"
        }
    }
}

struct DeskView: View {
    @EnvironmentObject private var model: LinkModel
    @State private var showAbout = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("BridgeLink").font(.title2.weight(.semibold))
                Spacer()
                Button("About") { showAbout = true }
                Button("Quit") { model.quit() }.keyboardShortcut("q")
            }
            Text(statusText).font(.callout).foregroundStyle(.secondary)
            if !model.phoneBattery.isEmpty {
                Text(model.phoneBattery).font(.caption).foregroundStyle(.secondary)
            }
            Text(model.addresses).font(.system(.body, design: .monospaced)).textSelection(.enabled)
            if model.addressChoices.count > 1 {
                Picker("Address in the QR", selection: $model.chosenAddress) {
                    ForEach(model.addressChoices, id: \.self) { Text($0).tag($0) }
                }
            }
            if !model.pairedSummary.isEmpty {
                Text("Paired phones: \(model.pairedSummary)").font(.caption)
            }
            if let image = qrCode(pairingText) {
                Image(nsImage: image)
                    .interpolation(.none)
                    .frame(width: 148, height: 148)
                    .accessibilityLabel("Pairing QR code")
            }
            Text(pairingText).font(.caption2.monospaced()).textSelection(.enabled).lineLimit(3)
            Text("Key \(model.fingerprint)").font(.caption.monospaced()).textSelection(.enabled)
            Text("PIN \(model.pin)").font(.title3.monospacedDigit().weight(.medium))
            Text("Expires in \(model.pinLeft) seconds. Scan the QR. That is the safe way to pair.")
                .font(.caption)
                .foregroundStyle(.secondary)
            if !model.matchCode.isEmpty {
                Text("Match code \(model.matchCode)").font(.title3.monospacedDigit().weight(.medium))
                Button("Codes match") { model.confirmMatch() }
            }
            HStack {
                Button("New PIN") { model.newPin() }
                Button("Forget phone") { model.forget() }
            }
            Toggle("Launch at login", isOn: Binding(
                get: { model.launchAtLogin },
                set: { _ in model.toggleLogin() }
            ))
            Toggle("Mirror notifications", isOn: Binding(
                get: { model.mirrorOn },
                set: { model.setMirror($0) }
            ))
            Picker("Clipboard from phone", selection: Binding(
                get: { model.clipboardMode },
                set: { model.setClipboardMode($0) }
            )) {
                Text("Auto").tag("auto")
                Text("Ask").tag("ask")
                Text("Off").tag("off")
            }
            if !model.pendingClipboard.isEmpty {
                Button("Use phone clipboard") { model.acceptClipboard() }
            }
            Button("Notifications") { model.notifications() }
            TextField("Note for the phone", text: $model.draft).textFieldStyle(.roundedBorder)
            HStack {
                Button("Send") { model.sendDraft() }
                Button("Send clipboard") { model.sendClipboard() }
                Button("Ring phone") { model.ring() }
                Button("Stop ring") { model.stopRing() }
            }
            Button("Send file") { model.pickFile() }
            TextField("Link for the phone", text: $model.linkDraft).textFieldStyle(.roundedBorder)
            Button("Open on phone") { model.sendLink() }
            if !model.pendingLink.isEmpty {
                Button("Open link from phone") { model.openPendingLink() }
                Text(model.pendingLink).font(.caption).lineLimit(2).textSelection(.enabled)
            }
            Text(model.lines.prefix(8).joined(separator: "\n"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(width: 380)
        .onDrop(of: [.fileURL], isTargeted: nil) { providers in
            guard let provider = providers.first else { return false }
            provider.loadItem(forTypeIdentifier: "public.file-url", options: nil) { item, _ in
                var url: URL?
                if let data = item as? Data {
                    url = URL(dataRepresentation: data, relativeTo: nil)
                } else if let item = item as? URL {
                    url = item
                } else {
                    url = nil
                }
                guard let url else { return }
                Task { @MainActor in model.sendFile(url) }
            }
            return true
        }
        .sheet(isPresented: $showAbout) {
            VStack(alignment: .leading, spacing: 8) {
                Text("BridgeLink 0.6.0").font(.headline)
                Text("Links this Mac to a paired Android phone on the local network. Traffic uses TLS. The phone pins this Mac's key after the first PIN.")
                Button("Close") { showAbout = false }
            }
            .padding(20)
            .frame(width: 320)
        }
        .onAppear {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        }
    }

    private var pairingText: String {
        let host = model.chosenAddress
        let usable = host == "No Wi-Fi address yet" ? "" : host
        return pairingLink(host: usable, port: Int(model.listenPort), fingerprint: model.fingerprint, pin: model.pin)
    }

    private func qrCode(_ text: String) -> NSImage? {
        guard text.contains("host="), !text.contains("host=&"),
              let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(Data(text.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 6, y: 6))
        let rep = NSCIImageRep(ciImage: scaled)
        let image = NSImage(size: rep.size)
        image.addRepresentation(rep)
        return image
    }

    private var statusText: String {
        switch model.phase {
        case .idle: return "Starting…"
        case .listening: return "Listening on port \(model.listenPort)"
        case .paired(let name): return "Paired with \(name)"
        case .failed(let reason): return reason
        }
    }
}

@main
struct BridgeLinkApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        Settings {
            Text("Use the menu-bar icon. Drop a file on it, or press Command-Shift-D.")
                .padding()
                .frame(width: 320)
        }
    }
}
