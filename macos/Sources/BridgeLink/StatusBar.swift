import AppKit
import Combine
import SwiftUI

/// The menu-bar icon is the drop target. A click opens the panel.
@MainActor
final class MenuHost: NSObject, NSWindowDelegate {
    let model: LinkModel
    private let popover = NSPopover()
    private let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
    private var bag = Set<AnyCancellable>()
    private var globalMonitor: Any?
    private var desk: NSWindow?

    init(model: LinkModel) {
        self.model = model
        super.init()
        model.openWindow = { [weak self] in self?.openDesk() }
        let host = NSHostingController(rootView: PopoverView().environmentObject(model))
        popover.contentSize = NSSize(width: 360, height: 560)
        popover.behavior = .transient
        popover.contentViewController = host
        if let button = item.button {
            let image = NSImage(systemSymbolName: "link", accessibilityDescription: "BridgeLink")
            image?.isTemplate = true
            button.image = image
            let cover = DropCover(frame: button.bounds)
            cover.autoresizingMask = [.width, .height]
            cover.onClick = { [weak self] in self?.toggle() }
            cover.onFiles = { [weak self] urls in
                self?.model.sendDropped(urls)
            }
            button.addSubview(cover)
        }
        model.$phase.receive(on: DispatchQueue.main).sink { [weak self] phase in
            let paired: Bool
            if case .paired = phase { paired = true } else { paired = false }
            self?.item.button?.appearsDisabled = !paired
        }.store(in: &bag)
        NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            if Self.quickDrop(event) {
                self?.toggle()
                return nil
            }
            return event
        }
        globalMonitor = NSEvent.addGlobalMonitorForEvents(matching: .keyDown) { [weak self] event in
            if Self.quickDrop(event) {
                Task { @MainActor in self?.toggle() }
            }
        }
    }

    func toggle() {
        guard let button = item.button else { return }
        if popover.isShown {
            popover.performClose(nil)
        } else {
            popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        }
    }

    func openDesk() {
        if desk == nil {
            let host = NSHostingController(rootView: DeskWindow().environmentObject(model))
            let window = NSWindow(contentViewController: host)
            window.title = "BridgeLink"
            window.setContentSize(NSSize(width: 980, height: 680))
            window.styleMask = [.titled, .closable, .miniaturizable, .resizable]
            window.isReleasedWhenClosed = false
            window.delegate = self
            desk = window
        }
        NSApp.setActivationPolicy(.regular)
        desk?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    func windowWillClose(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
    }

    private static func quickDrop(_ event: NSEvent) -> Bool {
        event.modifierFlags.contains(.command)
            && event.modifierFlags.contains(.shift)
            && event.charactersIgnoringModifiers?.lowercased() == "d"
    }
}

/// Covers the status icon so dropped files go to the paired phone.
final class DropCover: NSView {
    var onClick: (() -> Void)?
    var onFiles: (([URL]) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        registerForDraggedTypes([.fileURL])
    }

    required init?(coder: NSCoder) {
        return nil
    }

    override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation { .copy }

    override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
        let urls = sender.draggingPasteboard.readObjects(forClasses: [NSURL.self], options: nil) as? [URL] ?? []
        if urls.isEmpty { return false }
        onFiles?(urls)
        return true
    }

    override func mouseUp(with event: NSEvent) {
        onClick?()
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    let model = LinkModel()
    private var menu: MenuHost?

    func applicationDidFinishLaunching(_ notification: Notification) {
        if let raw = ProcessInfo.processInfo.environment["BRIDGELINK_SHOTS"] {
            do {
                try ShotBook.write(model: model, to: URL(fileURLWithPath: raw, isDirectory: true))
            } catch {
                fputs("shots \(error)\n", stderr)
                exit(1)
            }
            exit(0)
        }
        menu = MenuHost(model: model)
    }
}
