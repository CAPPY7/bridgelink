import AppKit
import Darwin
import Foundation
import Network
import SwiftUI

final class LinkModel: ObservableObject {
    @Published var pin: String
    @Published var status = "Starting…"
    @Published var addresses = ""
    @Published var draft = ""
    @Published var log = "Open this on the Mac, then connect the phone with the address and PIN."

    private var listener: NWListener?
    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "app.bridgelink.mac")
    private var buffer = Data()
    private var paired = false

    init() {
        pin = String(format: "%06d", Int.random(in: 0...999999))
        addresses = Self.lanAddresses().joined(separator: "\n")
        if addresses.isEmpty { addresses = "No Wi-Fi address yet" }
        start()
    }

    func newPin() {
        pin = String(format: "%06d", Int.random(in: 0...999999))
        paired = false
        status = "New PIN. Connect the phone again."
        connection?.cancel()
        connection = nil
    }

    func sendDraft() {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        send(type: "text", body: body)
        draft = ""
        note("Sent to the phone")
    }

    func sendClipboard() {
        let text = NSPasteboard.general.string(forType: .string) ?? ""
        send(type: "clipboard", body: text)
        note(text.isEmpty ? "Clipboard was empty" : "Sent clipboard")
    }

    func ring() {
        send(type: "ring", body: "")
        note("Ringing the phone")
    }

    private func start() {
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            let listener = try NWListener(using: params, on: 8787)
            listener.newConnectionHandler = { [weak self] connection in
                self?.accept(connection)
            }
            listener.stateUpdateHandler = { [weak self] state in
                DispatchQueue.main.async {
                    switch state {
                    case .ready:
                        self?.status = "Listening on port 8787"
                    case .failed(let error):
                        self?.status = "Could not listen: \(error.localizedDescription)"
                    default:
                        break
                    }
                }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            status = "Could not listen: \(error.localizedDescription)"
        }
    }

    private func accept(_ connection: NWConnection) {
        self.connection?.cancel()
        self.connection = connection
        buffer.removeAll(keepingCapacity: true)
        paired = false
        connection.stateUpdateHandler = { [weak self] state in
            if case .failed(let error) = state {
                DispatchQueue.main.async {
                    self?.paired = false
                    self?.status = "Phone disconnected: \(error.localizedDescription)"
                }
            }
        }
        connection.start(queue: queue)
        receive(connection)
        DispatchQueue.main.async { self.status = "Phone is connecting…" }
    }

    private func receive(_ connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data { self.buffer.append(data); self.drain() }
            if isComplete || error != nil {
                DispatchQueue.main.async {
                    self.paired = false
                    if self.status.hasPrefix("Paired") || self.status.contains("connecting") {
                        self.status = "Phone disconnected"
                    }
                }
                return
            }
            self.receive(connection)
        }
    }

    private func drain() {
        while let newline = buffer.firstIndex(of: 10) {
            let chunk = buffer.subdata(in: buffer.startIndex..<newline)
            buffer.removeSubrange(buffer.startIndex...newline)
            guard let text = String(data: chunk, encoding: .utf8),
                  let raw = text.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: raw) as? [String: Any],
                  let type = json["type"] as? String else { continue }
            let body = json["body"] as? String ?? ""
            let name = json["name"] as? String ?? "Phone"
            if type == "hello" {
                let got = json["pin"] as? String ?? ""
                let expected = currentPin()
                if got != expected {
                    send(type: "error", body: "Wrong PIN")
                    connection?.cancel()
                    DispatchQueue.main.async { self.status = "Wrong PIN" }
                    return
                }
                paired = true
                send(type: "paired", body: "")
                DispatchQueue.main.async { self.status = "Paired with \(name)" }
                note("Paired with \(name)")
                continue
            }
            guard paired else {
                send(type: "error", body: "Pair first")
                continue
            }
            DispatchQueue.main.async {
                switch type {
                case "text":
                    self.note(body.isEmpty ? "Empty note" : body)
                    self.notify("Note from the phone", body)
                case "clipboard":
                    let board = NSPasteboard.general
                    board.clearContents()
                    board.setString(body, forType: .string)
                    self.note("Clipboard updated")
                    self.notify("Clipboard from the phone", body)
                default:
                    self.note(body.isEmpty ? type : body)
                }
            }
        }
    }

    private func send(type: String, body: String) {
        let payload: [String: Any] = ["v": 1, "type": type, "from": "mac", "body": body]
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              var line = String(data: data, encoding: .utf8) else { return }
        line.append("\n")
        connection?.send(content: Data(line.utf8), completion: .contentProcessed { _ in })
    }

    private func currentPin() -> String {
        if Thread.isMainThread { return pin }
        return DispatchQueue.main.sync { self.pin }
    }

    private func note(_ line: String) {
        let prior = log
        log = line + (prior.isEmpty ? "" : "\n" + prior)
    }

    private func notify(_ title: String, _ body: String) {
        let notification = NSUserNotification()
        notification.title = title
        notification.informativeText = body
        NSUserNotificationCenter.default.deliver(notification)
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

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("BridgeLink")
                .font(.title2.weight(.semibold))
            Text(model.status)
                .font(.callout)
                .foregroundStyle(.secondary)
            Text(model.addresses)
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
            HStack {
                Text("PIN \(model.pin)")
                    .font(.title3.monospacedDigit().weight(.medium))
                Spacer()
                Button("New PIN") { model.newPin() }
            }
            TextField("Note for the phone", text: $model.draft)
                .textFieldStyle(.roundedBorder)
            HStack {
                Button("Send") { model.sendDraft() }
                Button("Send clipboard") { model.sendClipboard() }
                Button("Ring phone") { model.ring() }
            }
            Text(model.log)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(width: 360)
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
