import AppKit
import Combine
import SwiftUI

/// The menu-bar icon is the drop target. A click opens the panel.
@MainActor
final class MenuHost: NSObject {
    let model: LinkModel
    private let popover = NSPopover()
    private let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
    private var bag = Set<AnyCancellable>()
    private var globalMonitor: Any?

    init(model: LinkModel) {
        self.model = model
        super.init()
        let host = NSHostingController(rootView: DeskView().environmentObject(model))
        popover.contentSize = NSSize(width: 380, height: 560)
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
                guard let url = urls.first else { return }
                self?.model.sendFile(url)
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
        menu = MenuHost(model: model)
    }
}
