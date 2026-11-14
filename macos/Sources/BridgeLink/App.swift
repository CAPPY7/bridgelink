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
    @Published var phase: Phase = .idle
    @Published var addresses = ""
    @Published var fingerprint = ""
    @Published var draft = ""
    @Published var lines: [String] = ["Waiting for the phone."]
    @Published var launchAtLogin = SMAppService.mainApp.status == .enabled
    @Published var clipboardMode = UserDefaults.standard.string(forKey: "clipboardMode") ?? "auto"
    @Published var pendingClipboard = ""

    private var listener: NWListener?
    private var live: NWConnection?
    private var pending: NWConnection?
    private var pendingID = UUID()
    private var liveID = UUID()
    private let queue = DispatchQueue(label: "app.bridgelink.mac")
    private var gate: PairingGate
    private var gateStarted = Int64(Date().timeIntervalSince1970 * 1000)

    init() {
        let token = PairingGate.newToken()
        pin = token
        gate = PairingGate(token: token, now: Int64(Date().timeIntervalSince1970 * 1000))
        addresses = Self.lanAddresses().joined(separator: "\n")
        if addresses.isEmpty { addresses = "No Wi-Fi address yet" }
        start()
    }

    func newPin() {
        let token = PairingGate.newToken()
        pin = token
        gateStarted = Int64(Date().timeIntervalSince1970 * 1000)
        gate = PairingGate(token: token, now: gateStarted)
        UserDefaults.standard.removeObject(forKey: "peerToken")
        phase = .listening
        note("New PIN. The phone must pair again.")
    }

    func forget() {
        UserDefaults.standard.removeObject(forKey: "peerToken")
        live?.cancel()
        live = nil
        phase = .listening
        note("Forgot the phone.")
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
            let listener = try NWListener(using: params, on: 8787)
            listener.service = NWListener.Service(name: "BridgeLink", type: "_bridgelink._tcp")
            listener.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in self?.accept(connection) }
            }
            listener.stateUpdateHandler = { [weak self] state in
                Task { @MainActor in
                    switch state {
                    case .ready: self?.phase = .listening
                    case .failed(let error): self?.phase = .failed(error.localizedDescription)
                    default: break
                    }
                }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            phase = .failed(String(describing: error))
        }
    }

    private func accept(_ connection: NWConnection) {
        let id = UUID()
        pendingID = id
        pending = connection
        connection.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                guard let self, self.pendingID == id else { return }
                if case .failed = state {
                    self.pending = nil
                    self.note("A connection failed before it paired. The current phone stays linked.")
                }
            }
        }
        connection.start(queue: queue)
        receive(connection, id: id, buffer: FrameBuffer())
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
        if envelope.type == "hello" {
            let saved = UserDefaults.standard.string(forKey: "peerToken") ?? ""
            let token = envelope.payload["token"] ?? ""
            let guess = envelope.payload["pin"] ?? ""
            let name = envelope.payload["name"] ?? "Phone"
            if !saved.isEmpty && constantTimeEquals(saved, token) {
                promote(connection, id: id, name: name, token: saved)
                return
            }
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            if gate.check(guess, now: now) {
                let issued = UUID().uuidString.replacingOccurrences(of: "-", with: "")
                promote(connection, id: id, name: name, token: issued)
            } else {
                send(type: "error", payload: ["body": "Wrong or expired PIN"], on: connection)
                connection.cancel()
                if self.pendingID == id { self.pending = nil }
                note("Rejected a PIN. The paired phone was not disconnected.")
            }
            return
        }
        guard !pending, liveID == id else {
            send(type: "error", payload: ["body": "Pair first"], on: connection)
            return
        }
        switch envelope.type {
        case "text":
            note(envelope.payload["body"] ?? "")
            notify("Note from the phone", envelope.payload["body"] ?? "")
        case "clipboard":
            let text = envelope.payload["body"] ?? ""
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
        default:
            note(envelope.type)
        }
    }

    private func promote(_ connection: NWConnection, id: UUID, name: String, token: String) {
        guard pendingID == id else { return }
        UserDefaults.standard.set(token, forKey: "peerToken")
        live?.cancel()
        liveID = id
        live = connection
        pending = nil
        phase = .paired(name)
        send(type: "paired", payload: ["token": token], on: connection)
        note("Paired with \(name)")
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
                if getnameinfo(address, socklen_t(address.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                    let value = String(cString: host)
                    if value != "127.0.0.1" { result.append(value) }
                }
            }
            cursor = item.pointee.ifa_next
        }
        return result
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
            if let image = qrCode(pairingText) {
                Image(nsImage: image)
                    .interpolation(.none)
                    .frame(width: 148, height: 148)
                    .accessibilityLabel("Pairing QR code")
            }
            Text(pairingText).font(.caption2.monospaced()).textSelection(.enabled).lineLimit(3)
            Text("Key \(model.fingerprint)").font(.caption.monospaced()).textSelection(.enabled)
            Text("PIN \(model.pin)").font(.title3.monospacedDigit().weight(.medium))
            Text("PIN expires in 2 minutes. Scan the QR, or paste the link into the phone.")
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
                Text("BridgeLink 0.3.0").font(.headline)
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
        let host = model.addresses.split(separator: "\n").first.map(String.init) ?? ""
        let usable = host == "No Wi-Fi address yet" ? "" : host
        return pairingLink(host: usable, port: 8787, fingerprint: model.fingerprint, pin: model.pin)
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
        case .listening: return "Listening on port 8787"
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
