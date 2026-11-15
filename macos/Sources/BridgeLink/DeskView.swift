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
            Toggle("Show notification text", isOn: Binding(
                get: { model.showNotificationText },
                set: { model.setShowNotificationText($0) }
            ))
            Text("Notification text is shown only while this Mac is unlocked and that switch is on. Otherwise only the app name is shown.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("The first open needs one extra step because this app is not from the Mac App Store. macOS 13 or 14: right-click the app, choose Open, then Open. macOS 15 or newer: System Settings, Privacy and Security, Open Anyway.")
                .font(.caption)
                .foregroundStyle(.secondary)
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
            Button("Send diagnostics") { model.saveDiagnostics() }
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
                Text("BridgeLink 0.8.0").font(.headline)
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
        return pairingLink(host: usable, port: Int(model.listenPort), fingerprint: model.fingerprint, pin: model.pin, hosts: model.addressChoices)
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

