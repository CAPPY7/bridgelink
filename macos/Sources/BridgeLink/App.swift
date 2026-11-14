import AppKit
import BridgeLinkCore
import CoreImage
import Darwin
import Foundation
import Network
import Security
import ServiceManagement
import SwiftUI
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
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        send(type: "text", payload: ["body": body], on: live)
        draft = ""
        note("Sent to the phone")
    }

    func sendClipboard() {
        let text = NSPasteboard.general.string(forType: .string) ?? ""
        send(type: "clipboard", payload: ["body": text], on: live)
        note(text.isEmpty ? "Clipboard was empty" : "Sent clipboard")
    }

    func ring() {
        send(type: "ring", payload: [:], on: live)
        note("Ringing the phone")
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
                connection.cancel()
                self.finishPending(id)
                self.note("A connection did not pair in time.")
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
            let expected = pairingProof(pin: pin, fingerprint: fingerprint)
            if gate.observe(matched: constantTimeEquals(expected, proof), now: now) {
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
        note("Clipboard updated")
        notify("Clipboard from the phone", "Clipboard updated")
    }

    private func send(type: String, payload: [String: String], on connection: NWConnection?) {
        let frame = encodeFrame(json: EnvelopeCodec.json(type: type, from: "mac", payload: payload))
        connection?.send(content: frame, completion: .contentProcessed { _ in })
    }

    private func note(_ line: String) {
        lines.insert(line, at: 0)
        if lines.count > 200 { lines.removeLast(lines.count - 200) }
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
            Text("Expires in \(model.pinLeft) seconds. Scan the QR, or paste the link into the phone.")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack {
                Button("New PIN") { model.newPin() }
                Button("Forget phone") { model.forget() }
            }
            Toggle("Launch at login", isOn: Binding(
                get: { model.launchAtLogin },
                set: { _ in model.toggleLogin() }
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
            }
            Text(model.lines.prefix(8).joined(separator: "\n"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(width: 380)
        .sheet(isPresented: $showAbout) {
            VStack(alignment: .leading, spacing: 8) {
                Text("BridgeLink 0.4.0").font(.headline)
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
    @StateObject private var model = LinkModel()

    var body: some Scene {
        MenuBarExtra {
            DeskView().environmentObject(model)
        } label: {
            Image(systemName: "link")
        }
        .menuBarExtraStyle(.window)
    }
}
