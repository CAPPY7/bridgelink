import AppKit
import BridgeLinkCore
import CoreImage
import Darwin
import Foundation
import Network
import Security
import ServiceManagement
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications


extension LinkModel {
    func send(type: String, payload: [String: String], on connection: NWConnection?, done: (() -> Void)? = nil) {
        let json = EnvelopeCodec.json(type: type, from: "mac", payload: payload)
        if replyOnRelay, let away, away.isLive {
            away.sendJson(json, done: done)
            return
        }
        if connection != nil {
            connection?.send(content: encodeFrame(json: json), completion: .contentProcessed { _ in done?() })
            return
        }
        if let away, away.isLive {
            away.sendJson(json, done: done)
            return
        }
        done?()
    }

    func note(_ line: String) {
        lines = HistoryFile.append(line: line, url: historyURL())
    }

    func historyURL() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return base.appendingPathComponent("BridgeLink/history.json")
    }

    func sendMacBattery() {
        refreshMacBatteryLabel()
        guard case .paired = phase, let sample = MacPower.sample() else { return }
        if !Features.batteryChanged(
            previousLevel: macLevel,
            previousCharging: macCharging,
            hadSample: sawMacBattery,
            level: sample.level,
            charging: sample.charging
        ) { return }
        sawMacBattery = true
        macLevel = sample.level
        macCharging = sample.charging
        send(type: "battery", payload: [
            "level": String(sample.level),
            "charging": sample.charging ? "1" : "0",
            "label": "mac",
        ], on: live)
    }

    func saveDiagnostics() {
        let paired: Bool
        if case .paired = phase { paired = true } else { paired = false }
        let status: String
        switch phase {
        case .idle: status = "Starting"
        case .listening: status = "Listening"
        case .paired: status = "Paired"
        case .failed(let reason): status = reason
        }
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let text = Features.diagnostics(
            version: version,
            os: ProcessInfo.processInfo.operatingSystemVersionString,
            paired: paired,
            hostSaved: !chosenAddress.isEmpty,
            port: Int(listenPort),
            status: status
        )
        let folder = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let dir = folder.appendingPathComponent("BridgeLink")
        let url = dir.appendingPathComponent("bridgelink-diagnostics.txt")
        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try text.write(to: url, atomically: true, encoding: .utf8)
            NSWorkspace.shared.activateFileViewerSelecting([url])
            note("Saved a diagnostics file in Downloads. It has no messages and no keys.")
        } catch {
            note("Could not save the diagnostics file.")
        }
    }

    func startFindMac() {
        stopFindMac()
        note("The phone is ringing this Mac")
        let clock = DispatchSource.makeTimerSource(queue: .main)
        clock.schedule(deadline: .now(), repeating: .seconds(1))
        clock.setEventHandler { NSSound.beep() }
        clock.resume()
        macFinder = clock
        DispatchQueue.main.asyncAfter(deadline: .now() + 30) { [weak self] in self?.stopFindMac() }
    }

    func stopFindMac() {
        macFinder?.cancel()
        macFinder = nil
    }

    func macUnlocked() -> Bool {
        guard let handle = dlopen("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", RTLD_LAZY) else {
            return false
        }
        defer { dlclose(handle) }
        guard let symbol = dlsym(handle, "CGSSessionCopyCurrentDictionary") else { return false }
        typealias SessionFn = @convention(c) () -> Unmanaged<CFDictionary>?
        let copy = unsafeBitCast(symbol, to: SessionFn.self)
        guard let session = copy()?.takeRetainedValue() as? [String: Any] else { return false }
        if let locked = session["CGSSessionScreenIsLocked"] as? Bool, locked { return false }
        return true
    }

    func notify(_ title: String, _ body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func notifySaved(_ name: String, _ url: URL, opened: Bool) {
        let open = UNNotificationAction(identifier: "open", title: "Open", options: [])
        let finder = UNNotificationAction(identifier: "finder", title: "Show in Finder", options: [])
        let category = UNNotificationCategory(identifier: "bridgelink.file", actions: [open, finder], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
        let content = UNMutableNotificationContent()
        content.title = "\(name) saved"
        content.body = opened ? "Opened because Open scans automatically is on." : "Open or show it in Finder."
        content.categoryIdentifier = "bridgelink.file"
        content.userInfo = ["path": url.path]
        let request = UNNotificationRequest(identifier: "file-" + UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    static func lanAddresses() -> [String] {
        var result: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return result }
        defer { freeifaddrs(ifaddr) }
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let item = cursor {
            let flags = Int32(item.pointee.ifa_flags)
            if (flags & IFF_UP) != 0, (flags & IFF_LOOPBACK) == 0,
               let address = item.pointee.ifa_addr, address.pointee.sa_family == UInt8(AF_INET) {
                var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                let interfaceName = String(cString: item.pointee.ifa_name)
                if getnameinfo(address, socklen_t(address.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                    let value = String(cString: host)
                    if preferredLanAddress(interface: interfaceName, address: value) { result.append(value) }
                }
            }
            cursor = item.pointee.ifa_next
        }
        return result
    }

    static func endpointHost(_ connection: NWConnection) -> String {
        switch connection.endpoint {
        case .hostPort(let host, _):
            return "\(host)"
        default:
            return "unknown"
        }
    }
}
