import AppKit
import BridgeLinkCore
import SwiftUI

struct MediaButtons: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        CardBox {
            Text("Music").font(.headline)
            HStack(spacing: 10) {
                if let data = Data(base64Encoded: model.nowArt), let image = NSImage(data: data) {
                    Image(nsImage: image).resizable().frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 8))
                }
                Text(model.nowPlaying).font(.body)
            }
            Text("Phone buttons control this Mac. These buttons control the phone. Song art is sent only when it is small.")
                .font(.callout).foregroundStyle(.secondary)
            HStack {
                Button("Play") { model.sendMedia("play") }
                Button("Pause") { model.sendMedia("pause") }
                Button("Next") { model.sendMedia("next") }
                Button("Previous") { model.sendMedia("previous") }
            }
            HStack {
                TextField("Volume 0 to 100", text: Binding(get: { model.volumeText }, set: { model.volumeText = $0 })).frame(maxWidth: 160)
                Button("Set phone volume") { model.sendMedia("volume:" + model.volumeText) }
            }
            Text("If the media keys do nothing, allow BridgeLink in Accessibility.")
                .font(.callout).foregroundStyle(.secondary)
        }
    }
}

struct InboxPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Notifications, text messages, and files you received. Search does not show passwords or codes.")
                .foregroundStyle(.secondary)
            Picker("Show", selection: Binding(get: { model.inboxFilter }, set: { model.inboxFilter = $0 })) {
                Text("All").tag("all")
                Text("Notifications").tag("notify")
                Text("Messages").tag("sms")
                Text("Files").tag("file")
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 420)
            TextField("Search", text: Binding(get: { model.inboxQuery }, set: { model.inboxQuery = $0 }))
            let rows = StageFour.filter(model.inbox, model.inboxFilter, model.inboxQuery)
            if rows.isEmpty {
                EmptyNote(
                    symbol: "tray",
                    title: "Nothing in the inbox yet.",
                    line: "Notifications and received files show up here. Sending texts arrives in a later update."
                )
            } else {
                ForEach(Array(rows.prefix(30).enumerated()), id: \.offset) { _, item in
                    CardBox {
                        Text(label(item.kind) + " · " + item.title).font(.headline)
                        if !item.detail.isEmpty {
                            Text(item.detail).font(.callout).foregroundStyle(.secondary).lineLimit(3)
                        }
                    }
                }
            }
        }
    }

    private func label(_ kind: String) -> String {
        switch kind {
        case "notify": return "Notification"
        case "sms": return "Message"
        case "file": return "File"
        default: return "Item"
        }
    }
}

struct ClipHistoryPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Clipboard history").font(.headline)
            Text("The last 100 copies stay on this Mac. Passwords, card numbers, and codes are not kept.")
                .font(.callout).foregroundStyle(.secondary)
            TextField("Search history", text: Binding(get: { model.clipQuery }, set: { model.clipQuery = $0 }))
            let rows = StageFour.search(model.clips, model.clipQuery)
            if rows.isEmpty {
                Text("Nothing in clipboard history yet.").foregroundStyle(.secondary)
            }
            ForEach(Array(rows.prefix(12).enumerated()), id: \.offset) { _, clip in
                HStack {
                    Text((clip.pinned ? "Pinned. " : "") + clip.text.replacingOccurrences(of: "\n", with: " "))
                        .lineLimit(2)
                    Spacer()
                    Button(clip.pinned ? "Unpin" : "Pin") { model.pinClip(clip.id) }
                    Button("Use") { model.useClip(clip.id) }
                    Button("Delete") { model.deleteClip(clip.id) }
                }
            }
        }
    }
}

struct LockPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Lock the Mac when you leave").font(.headline)
            Text("The phone watches this Mac over Bluetooth. A weak signal for a while locks the screen. BridgeLink never unlocks the Mac. Charging the phone, or At my desk, skips the lock.")
                .font(.callout).foregroundStyle(.secondary)
            Toggle("Lock when the phone leaves", isOn: Binding(get: { model.lockOn }, set: { model.setLock(on: $0) }))
            Toggle("I am at my desk", isOn: Binding(get: { model.atDesk }, set: { model.setDesk($0) }))
            Stepper("How far: \(model.lockFar) (40 close, 95 far)", value: Binding(get: { model.lockFar }, set: { model.lockFar = $0 }), in: 40...95)
            Stepper("Wait \(model.lockDelay) seconds", value: Binding(get: { model.lockDelay }, set: { model.lockDelay = $0 }), in: 5...180)
            Button("Save lock settings") { model.pushLock() }
            Text("If the screen does not lock, allow BridgeLink in Accessibility. The phone also needs Bluetooth allowed.")
                .font(.callout).foregroundStyle(.secondary)
        }
    }
}
