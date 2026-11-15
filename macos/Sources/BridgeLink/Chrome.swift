import SwiftUI

let bridgeAccent = Color(red: 0.05, green: 0.42, blue: 0.40)

struct LinkDot: View {
    let phase: Phase

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 12, height: 12)
            .accessibilityLabel(label)
    }

    private var color: Color {
        switch phase {
        case .paired: return Color(red: 0.10, green: 0.55, blue: 0.32)
        case .listening, .idle: return Color(red: 0.85, green: 0.55, blue: 0.10)
        case .failed: return Color(red: 0.75, green: 0.22, blue: 0.18)
        }
    }

    private var label: String {
        switch phase {
        case .paired: return "Connected"
        case .listening: return "Waiting"
        case .idle: return "Starting"
        case .failed: return "Needs attention"
        }
    }
}

struct EmptyNote: View {
    let symbol: String
    let title: String
    let line: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 36)).foregroundStyle(.secondary)
            Text(title).font(.title3.weight(.semibold))
            Text(line).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: 460)
        .padding(.vertical, 8)
    }
}

struct CardBox<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8, content: content)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

extension View {
    /// A file dropped on the window or the menu panel is sent to the phone.
    func bridgeDrop(_ model: LinkModel) -> some View {
        onDrop(of: [.fileURL], isTargeted: nil) { providers in
            for provider in providers {
                provider.loadItem(forTypeIdentifier: "public.file-url", options: nil) { item, _ in
                    let url: URL?
                    if let data = item as? Data {
                        url = URL(dataRepresentation: data, relativeTo: nil)
                    } else {
                        url = item as? URL
                    }
                    guard let url else { return }
                    Task { @MainActor in model.sendFile(url) }
                }
            }
            return !providers.isEmpty
        }
    }
}

func phoneTitle(_ phase: Phase) -> String {
    if case .paired(let name) = phase { return name }
    return "No phone"
}

func statusLine(_ model: LinkModel) -> String {
    switch model.phase {
    case .idle: return "Starting…"
    case .listening: return "Listening on port \(model.listenPort)"
    case .paired: return "Connected"
    case .failed(let reason): return reason
    }
}
