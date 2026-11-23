import SwiftUI

struct MessagesPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Texts from the paired phone. Allow SMS on the phone first. A sideloaded phone may need Allow restricted settings.")
                .foregroundStyle(.secondary)
            HStack {
                Button("Refresh") { model.askSms() }
                if model.simCount > 1 {
                    Picker("SIM", selection: $model.smsSim) {
                        Text("SIM 1").tag(0)
                        Text("SIM 2").tag(1)
                    }
                    .frame(maxWidth: 160)
                }
            }
            TextField("Phone number", text: $model.smsTo).textFieldStyle(.roundedBorder)
            TextField("Message", text: $model.smsBody, axis: .vertical).textFieldStyle(.roundedBorder).lineLimit(2...4)
            Button("Send text") { model.sendSms() }
            if model.messages.isEmpty {
                EmptyNote(symbol: "message", title: "No texts yet.", line: "Turn texts on from the phone Messages page, then tap Refresh.")
            } else {
                ForEach(model.messages.prefix(40)) { sms in
                    VStack(alignment: .leading, spacing: 2) {
                        Text((sms.incoming ? "From " : "To ") + sms.address).font(.headline)
                        Text(sms.body).foregroundStyle(.secondary).lineLimit(4)
                    }
                }
            }
        }
    }
}

struct SharedPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("This Mac folder stays in sync with the phone folder. A file that changed on both sides is kept, and the other copy is saved with BridgeLink conflict in the name. Files over 32 MB are skipped.")
                .foregroundStyle(.secondary)
            Text(model.folderPath.isEmpty ? "BridgeLink Shared in your home folder" : model.folderPath)
                .font(.callout).textSelection(.enabled)
            Toggle("Sync the shared folder", isOn: Binding(get: { model.folderOn }, set: { model.setShared($0) }))
            Toggle("Pause", isOn: Binding(get: { model.folderPaused }, set: { model.setFolderPaused($0) }))
            HStack {
                Button("Sync now") { model.pushFolder() }
                Button("Show in Finder") { model.revealFolder() }
            }
        }
    }
}
