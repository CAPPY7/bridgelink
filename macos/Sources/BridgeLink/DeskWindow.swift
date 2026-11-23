import SwiftUI

enum DeskTab: String, CaseIterable, Identifiable {
    case home, notifications, messages, files, photos, clipboard, shared, mirror, webcam, devices, settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .notifications: return "Notifications"
        case .messages: return "Messages"
        case .files: return "Files"
        case .photos: return "Photos"
        case .clipboard: return "Clipboard"
        case .shared: return "Shared Folder"
        case .mirror: return "Mirror"
        case .webcam: return "Webcam"
        case .devices: return "Devices"
        case .settings: return "Settings"
        }
    }

    var symbol: String {
        switch self {
        case .home: return "house"
        case .notifications: return "bell"
        case .messages: return "message"
        case .files: return "folder"
        case .photos: return "photo"
        case .clipboard: return "doc.on.clipboard"
        case .shared: return "externaldrive"
        case .mirror: return "rectangle.on.rectangle"
        case .webcam: return "video"
        case .devices: return "iphone"
        case .settings: return "gearshape"
        }
    }
}

/// The main window. The menu bar stays a short panel.
struct DeskWindow: View {
    @EnvironmentObject private var model: LinkModel
    @State private var tab: DeskTab?

    init(tab: DeskTab = .home) {
        _tab = State(initialValue: tab)
    }

    var body: some View {
        NavigationSplitView {
            List(DeskTab.allCases, selection: $tab) { item in
                Label(item.title, systemImage: item.symbol).tag(item)
            }
            .navigationSplitViewColumnWidth(min: 196, ideal: 220, max: 260)
            .listStyle(.sidebar)
        } detail: {
            DeskDetail(tab: tab ?? .home)
        }
        .frame(minWidth: 860, minHeight: 560)
        .tint(bridgeAccent)
        .bridgeDrop(model)
        .animation(.easeInOut(duration: 0.18), value: tab)
    }
}
