import AppKit
import BridgeLinkCore
import SwiftUI

/// Draws the real SwiftUI screens to PNG files. Used by the Mac build, not by a person.
enum ShotBook {
    @MainActor
    static func write(model: LinkModel, to folder: URL) throws {
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        model.phase = .paired("Pixel")
        model.phoneBattery = "Phone battery 86%"
        model.macBattery = "91% charging"
        model.lines = [
            "Sent a note",
            "Received photo.jpg",
            "Paired with Pixel",
            "Clipboard updated",
            "Waiting for the phone.",
        ]
        model.pin = "482913"
        model.pinLeft = 90
        model.fingerprint = "ab12cd34ef56"
        model.addresses = "192.168.1.20"
        model.addressChoices = ["192.168.1.20", "10.0.0.8"]
        model.chosenAddress = "192.168.1.20"
        model.listenPort = 8787
        model.pairedSummary = "Pixel"
        model.launchAtLogin = false
        model.mirrorOn = true
        model.nowPlaying = "Playing Sunny - Ada"
        model.clips = StageFour.add([], "Grocery list", false, 5)
        model.inbox = StageFour.addInbox([], "file", "scan.pdf", "Saved on this Mac", false, 2)
        model.inbox = StageFour.addInbox(model.inbox, "notify", "Mail", "Package arrived", false, 3)
        model.lockOn = true
        model.lockFar = 70
        model.lockDelay = 30
        try saveImage(PopoverView().environmentObject(model).frame(width: 360), "mac-popover.png", folder, .light)
        try saveImage(PopoverView().environmentObject(model).frame(width: 360), "mac-popover-dark.png", folder, .dark)
        for tab in DeskTab.allCases {
            try saveWindow(model, tab, "mac-\(tab.rawValue).png", folder, .light)
        }
        try saveWindow(model, .home, "mac-home-dark.png", folder, .dark)
    }

    @MainActor
    private static func saveImage<V: View>(_ view: V, _ name: String, _ folder: URL, _ scheme: ColorScheme) throws {
        let renderer = ImageRenderer(content: view.environment(\.colorScheme, scheme))
        renderer.scale = 2
        guard let image = renderer.nsImage else { throw CocoaError(.fileWriteUnknown) }
        try writePNG(image, folder.appendingPathComponent(name))
    }

    /// NavigationSplitView does not draw in ImageRenderer, so the window is shown off to the side.
    @MainActor
    private static func saveWindow(_ model: LinkModel, _ tab: DeskTab, _ name: String, _ folder: URL, _ scheme: ColorScheme) throws {
        let view = DeskWindow(tab: tab)
            .environmentObject(model)
            .environment(\.colorScheme, scheme)
            .frame(width: 980, height: 640)
        let host = NSHostingView(rootView: view)
        host.frame = NSRect(x: 0, y: 0, width: 980, height: 640)
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 980, height: 640),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "BridgeLink"
        window.appearance = NSAppearance(named: scheme == .dark ? .darkAqua : .aqua)
        window.contentView = host
        window.setFrameOrigin(NSPoint(x: -4000, y: -4000))
        window.orderFrontRegardless()
        host.layoutSubtreeIfNeeded()
        for _ in 0..<8 {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }
        host.layoutSubtreeIfNeeded()
        guard let rep = host.bitmapImageRepForCachingDisplay(in: host.bounds) else {
            window.close()
            throw CocoaError(.fileWriteUnknown)
        }
        host.cacheDisplay(in: host.bounds, to: rep)
        guard let png = rep.representation(using: .png, properties: [:]), !png.isEmpty else {
            window.close()
            throw CocoaError(.fileWriteUnknown)
        }
        try png.write(to: folder.appendingPathComponent(name))
        window.close()
    }

    @MainActor
    private static func writePNG(_ image: NSImage, _ url: URL) throws {
        guard let tiff = image.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff),
              let png = rep.representation(using: .png, properties: [:]),
              !png.isEmpty else {
            throw CocoaError(.fileWriteUnknown)
        }
        try png.write(to: url)
    }
}
