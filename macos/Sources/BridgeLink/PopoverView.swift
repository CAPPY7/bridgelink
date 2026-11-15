import SwiftUI

/// The menu-bar panel. The full window is a separate button.
struct PopoverView: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                LinkDot(phase: model.phase)
                VStack(alignment: .leading, spacing: 2) {
                    Text(phoneTitle(model.phase)).font(.title3.weight(.semibold))
                    Text(statusLine(model)).font(.callout).foregroundStyle(.secondary).lineLimit(3)
                }
                Spacer()
            }
            HStack {
                Label(model.phoneBattery.isEmpty ? "Phone —" : model.phoneBattery, systemImage: "iphone")
                Spacer()
                Label("Mac \(model.macBattery)", systemImage: "laptopcomputer")
            }
            .font(.callout)
            Text(model.nowPlaying).font(.callout).lineLimit(2)
            HStack {
                Button("Play on phone") { model.sendMedia("play") }
                Button("Pause") { model.sendMedia("pause") }
                Button("Next") { model.sendMedia("next") }
            }
            .buttonStyle(.bordered)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                Button("Send file") { model.pickFile() }
                Button("Send clipboard") { model.sendClipboard() }
                Button("Ring phone") { model.ring() }
                Button("Stop ring") { model.stopRing() }
                Button("Mirror screen") { model.mirrorLater() }
            }
            .buttonStyle(.bordered)
            Text("Recent").font(.headline)
            recent
            Button("Open BridgeLink") { model.showDesk() }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            HStack {
                Button("Quit") { model.quit() }
                Spacer()
                Text("Command-Shift-D").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(width: 360)
        .background(.ultraThinMaterial)
        .tint(bridgeAccent)
        .bridgeDrop(model)
        .onAppear { model.refreshMacBatteryLabel() }
    }

    @ViewBuilder private var recent: some View {
        if model.lines.isEmpty {
            Text("Nothing yet. Pair the phone, then send a note or a file.")
                .font(.callout)
                .foregroundStyle(.secondary)
        } else {
            ForEach(Array(model.lines.prefix(5).enumerated()), id: \.offset) { _, line in
                Text(line).font(.callout).lineLimit(2)
            }
        }
    }
}
