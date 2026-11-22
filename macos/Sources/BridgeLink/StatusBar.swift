import AppKit
import Combine
import SwiftUI
import UserNotifications

/// The menu-bar icon is the drop target. A click opens the panel.
@MainActor
final class MenuHost: NSObject, NSWindowDelegate {
    let model: LinkModel
    private let popover = NSPopover()
    private let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
    private var bag = Set<AnyCancellable>()
    private var globalMonitor: Any?
    private var desk: NSWindow?
    private var setup: NSWindow?

    init(model: LinkModel) {
        self.model = model
        super.init()
        model.openWindow = { [weak self] in self?.openDesk() }
        model.openSetup = { [weak self] in self?.presentSetup() }
        model.closeSetup = { [weak self] in self?.setup?.close() }
        let host = NSHostingController(rootView: PopoverView().environmentObject(model))
        popover.contentSize = NSSize(width: 360, height: 560)
        popover.behavior = .transient
        popover.contentViewController = host
        if let button = item.button {
            let image = NSImage(systemSymbolName: "link", accessibilityDescription: "BridgeLink")
            image?.isTemplate = true
            button.image = image
            button.target = self
            button.action = #selector(toggleFromButton)
            let cover = DropCover(frame: button.bounds)
            cover.autoresizingMask = [.width, .height]
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

    @objc func toggleFromButton() {
        toggle()
    }

    func toggle() {
        guard let button = item.button else { return }
        if popover.isShown {
            popover.performClose(nil)
        } else {
            popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        }
    }

    func presentSetupIfNeeded() {
        if UserDefaults.standard.bool(forKey: "didFinishSetup") { return }
        presentSetup()
    }

    func presentSetup() {
        if setup == nil {
            let host = NSHostingController(rootView: FirstRunView().environmentObject(model))
            let window = NSWindow(contentViewController: host)
            window.title = "Set up BridgeLink"
            window.setContentSize(NSSize(width: 480, height: 340))
            window.styleMask = [.titled, .closable]
            window.isReleasedWhenClosed = false
            window.level = .floating
            window.center()
            setup = window
        }
        NSApp.setActivationPolicy(.regular)
        setup?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
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
    var onFiles: (([URL]) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        registerForDraggedTypes([.fileURL])
    }

    required init?(coder: NSCoder) {
        return nil
    }

    override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation { .copy }

    override func hitTest(_ point: NSPoint) -> NSView? {
        nil
    }

    override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
        let urls = sender.draggingPasteboard.readObjects(forClasses: [NSURL.self], options: nil) as? [URL] ?? []
        if urls.isEmpty { return false }
        onFiles?(urls)
        return true
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
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
        menu?.presentSetupIfNeeded()
        UNUserNotificationCenter.current().delegate = self
        NSApp.servicesProvider = self
        NSUpdateDynamicServices()
        model.startClipboardWatch()
        model.drainShareInbox()
    }

    func applicationDidBecomeActive(_ notification: Notification) {
        model.drainShareInbox()
    }

    @objc func sendToPhone(_ pboard: NSPasteboard, userData: String, error: AutoreleasingUnsafeMutablePointer<NSString?>) {
        let urls = pboard.readObjects(forClasses: [NSURL.self], options: [.urlReadingFileURLsOnly: true]) as? [URL] ?? []
        if urls.isEmpty {
            error.pointee = "Choose a file" as NSString
            return
        }
        model.fileCancel.on = false
        for url in urls { model.sendFile(url) }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let path = response.notification.request.content.userInfo["path"] as? String ?? ""
        let url = URL(fileURLWithPath: path)
        if response.actionIdentifier == "reply",
           let typed = response as? UNTextInputNotificationResponse {
            let key = response.notification.request.content.userInfo["key"] as? String ?? ""
            model.replyFromBanner(key, typed.userText)
        } else if response.actionIdentifier == "open" || response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            if !path.isEmpty { NSWorkspace.shared.open(url) }
        } else if response.actionIdentifier == "finder", !path.isEmpty {
            NSWorkspace.shared.activateFileViewerSelecting([url])
        }
        completionHandler()
    }
}
