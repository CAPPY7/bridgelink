import AppKit
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
        try save(PopoverView().environmentObject(model).frame(width: 360), "mac-popover.png", folder, .light)
        try save(PopoverView().environmentObject(model).frame(width: 360), "mac-popover-dark.png", folder, .dark)
        for tab in DeskTab.allCases {
            let view = DeskWindow(tab: tab).environmentObject(model).frame(width: 980, height: 640)
            try save(view, "mac-\(tab.rawValue).png", folder, .light)
        }
        let darkHome = DeskWindow(tab: .home).environmentObject(model).frame(width: 980, height: 640)
        try save(darkHome, "mac-home-dark.png", folder, .dark)
    }

    @MainActor
    private static func save<V: View>(_ view: V, _ name: String, _ folder: URL, _ scheme: ColorScheme) throws {
        let renderer = ImageRenderer(content: view.environment(\.colorScheme, scheme))
        renderer.scale = 2
        guard let image = renderer.nsImage,
              let tiff = image.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff),
              let png = rep.representation(using: .png, properties: [:]),
              !png.isEmpty else {
            throw CocoaError(.fileWriteUnknown)
        }
        try png.write(to: folder.appendingPathComponent(name))
    }
}
