import SwiftUI
import BridgeLinkCore

struct DeskDetail: View {
    @EnvironmentObject private var model: LinkModel
    let tab: DeskTab

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(tab.title).font(.largeTitle.weight(.semibold))
                content
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(nsColor: .windowBackgroundColor))
    }

    @ViewBuilder private var content: some View {
        switch tab {
        case .home: home
        case .notifications: notifications
        case .messages:
            MessagesPane()
        case .files: files
        case .clipboard: clipboard
        case .shared:
            SharedPane()
        case .mirror:
            MirrorPane()
        case .webcam:
            WebcamPane()
        case .automations:
            AutomationsPane()
        case .devices: devices
        case .settings: settings
        }
    }

    private var home: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardBox {
                HStack(spacing: 10) {
                    LinkDot(phase: model.phase)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(phoneTitle(model.phase)).font(.title2.weight(.semibold))
                        Text(statusLine(model)).foregroundStyle(.secondary)
                    }
                }
            }
            HStack(spacing: 12) {
                battery("Phone", model.phoneBattery.isEmpty ? "—" : model.phoneBattery)
                battery("Mac", model.macBattery)
            }
            CardBox {
                Text("Quick actions").font(.headline)
                HStack {
                    Button("Send file") { model.pickFile() }
                    Button("Send clipboard") { model.sendClipboard() }
                    Button("Ring phone") { model.ring() }
                    Button("Stop ring") { model.stopRing() }
                }
                TextField("Note for the phone", text: $model.draft).textFieldStyle(.roundedBorder)
                Button("Send note") { model.sendDraft() }
                TextField("Link for the phone", text: $model.linkDraft).textFieldStyle(.roundedBorder)
                Button("Open on phone") { model.sendLink() }
            }
            Text("Recent").font(.headline)
            recent(5)
            MediaButtons()
        }
    }

    private var notifications: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle("Mirror notifications", isOn: Binding(get: { model.mirrorOn }, set: { model.setMirror($0) }))
            Toggle("Show notification text", isOn: Binding(get: { model.showNotificationText }, set: { model.setShowNotificationText($0) }))
            Text("Notification text is shown only while this Mac is unlocked and that switch is on. Otherwise only the app name is shown.")
                .font(.callout).foregroundStyle(.secondary)
            InboxPane()
            Button("Allow notifications") { model.notifications() }
            if model.canReply {
                TextField("Reply", text: $model.replyDraft)
                Button("Send reply") { model.replyToNotification() }
            }
            Button("Dismiss on the phone") { model.dismissNotification() }
            Toggle("Quiet while the phone is in Do Not Disturb", isOn: Binding(get: { model.dndSync }, set: { model.setDndSync($0) }))
            Text("Phone Do Not Disturb quiets these banners. Sending Mac Focus to the phone needs a paid Apple account, so that direction stays off.")
                .font(.callout).foregroundStyle(.secondary)
            EmptyNote(symbol: "bell", title: "Banners show above, not in a stored list.", line: "Message text is not written into the activity log.")
        }
    }

    private var files: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Drop files anywhere on this window, on the menu-bar icon, or in the drop zone. This window sends files, not a whole folder. Files can be up to 4 GB.")
                .foregroundStyle(.secondary)
            Button("Send files") { model.pickFile() }
            Button("Show drop zone") { model.showDropZone() }
            Button("Cancel send") { model.cancelSend() }
            recentFiltered("file")
        }
    }

    private var clipboard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Clipboard from phone", selection: Binding(get: { model.clipboardMode }, set: { model.setClipboardMode($0) })) {
                Text("Auto").tag("auto")
                Text("Ask").tag("ask")
                Text("Off").tag("off")
            }
            .frame(maxWidth: 280)
            Button("Send clipboard") { model.sendClipboard() }
            Text("Auto sends text and pictures. Passwords and card numbers are skipped.")
                .font(.callout).foregroundStyle(.secondary)
            Toggle("Open links from the phone right away", isOn: Binding(get: { model.linksAlways }, set: { model.setLinksAlways($0) }))
            if !model.pendingClipboard.isEmpty {
                Button("Use phone clipboard") { model.acceptClipboard() }
            }
            if !model.pendingLink.isEmpty {
                Button("Open link from phone") { model.openPendingLink() }
                Text(model.pendingLink).font(.callout).lineLimit(2).textSelection(.enabled)
            }
            ClipHistoryPane()
        }
    }

    private var devices: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.addresses).font(.system(.body, design: .monospaced)).textSelection(.enabled)
            if model.addressChoices.count > 1 {
                Picker("Address in the QR", selection: $model.chosenAddress) {
                    ForEach(model.addressChoices, id: \.self) { Text($0).tag($0) }
                }
                .frame(maxWidth: 320)
            }
            if !model.pairedSummary.isEmpty {
                Text("Paired phones: \(model.pairedSummary)")
            }
            ForEach(model.phoneList) { phone in
                HStack {
                    Text(phone.name)
                    if phone.id == model.liveDeviceId {
                        Text("Linked").font(.callout).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Forget") { model.forgetSaved(phone.id) }
                }
            }
            TextField("Rename the paired phone", text: $model.renameDraft)
            Button("Rename phone") { model.renamePhone() }
            Text("If this Mac key changed, forget the phone and scan the QR again. That is the safe re-pair.")
                .font(.callout).foregroundStyle(.secondary)
            if let image = qrCode(pairingText(model)) {
                Image(nsImage: image)
                    .interpolation(.none)
                    .frame(width: 180, height: 180)
                    .accessibilityLabel("Pairing QR code")
            }
            Text(pairingText(model)).font(.caption.monospaced()).textSelection(.enabled).lineLimit(3)
            Text("Key \(model.fingerprint)").font(.caption.monospaced()).textSelection(.enabled)
            Text("PIN \(model.pin)").font(.title.monospacedDigit().weight(.medium))
            Text("Expires in \(model.pinLeft) seconds. Scan this QR on the phone. That is the safe way to pair.")
                .font(.callout).foregroundStyle(.secondary)
            if !model.matchCode.isEmpty {
                Text("Match code \(model.matchCode)").font(.title2.monospacedDigit().weight(.semibold))
                Button("Codes match") { model.confirmMatch() }
            }
            HStack {
                Button("New PIN") { model.newPin() }
                Button("Forget phone") { model.forget() }
            }
        }
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle("Launch at login", isOn: Binding(get: { model.launchAtLogin }, set: { _ in model.toggleLogin() }))
            Toggle("Open scans automatically", isOn: Binding(
                get: { UserDefaults.standard.object(forKey: "openScans") == nil || UserDefaults.standard.bool(forKey: "openScans") },
                set: { UserDefaults.standard.set($0, forKey: "openScans") }
            ))
            Text("Other files stay closed until you press Open on the notification.")
                .font(.callout).foregroundStyle(.secondary)
            Button("Send diagnostics") { model.saveDiagnostics() }
            Button("Check for update") { model.checkForUpdate() }
            Button("Show setup again") { model.replaySetup() }
            Button("Reset BridgeLink") { model.resetBridgeLink() }
            LockPane()
            Text("Phone trackpad").font(.headline)
            Text(StageFive.pointerSteps).font(.callout).foregroundStyle(.secondary)
            Toggle("Let the phone move the pointer and type", isOn: Binding(get: { model.trackpadOn }, set: { model.setTrackpad($0) }))
            Button("Show the Accessibility steps") { model.explainPointer() }
            RelayPane()
            Text("The first open needs one extra step because this app is not from the Mac App Store. macOS 13 or 14: right-click the app, choose Open, then Open. macOS 15 or newer: System Settings, Privacy and Security, Open Anyway.")
                .font(.callout).foregroundStyle(.secondary)
            Text("BridgeLink 1.0.1").font(.headline)
            Text("Links this Mac to a paired Android phone on the local network. Traffic uses TLS. The phone pins this Mac's key after the first PIN.")
                .foregroundStyle(.secondary)
            Button("Quit") { model.quit() }
        }
    }

    private func battery(_ title: String, _ value: String) -> some View {
        CardBox {
            Text(title).font(.callout).foregroundStyle(.secondary)
            Text(value).font(.title2.weight(.semibold))
        }
    }

    private func recent(_ count: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if model.lines.isEmpty {
                Text("Nothing yet.").foregroundStyle(.secondary)
            } else {
                ForEach(Array(model.lines.prefix(count).enumerated()), id: \.offset) { _, line in
                    Text(line).font(.callout).foregroundStyle(.secondary).lineLimit(2)
                }
            }
        }
    }

    private func recentFiltered(_ word: String) -> some View {
        let lines = model.lines.filter { $0.lowercased().contains(word) }
        return VStack(alignment: .leading, spacing: 4) {
            if lines.isEmpty {
                EmptyNote(symbol: "folder", title: "No files yet.", line: "Files you send and receive will show here.")
            } else {
                ForEach(Array(lines.prefix(8).enumerated()), id: \.offset) { _, line in
                    Text(line).font(.callout)
                }
            }
        }
    }
}
