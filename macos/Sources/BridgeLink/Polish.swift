import AppKit
import BridgeLinkCore
import CryptoKit
import Foundation
import Network
import ServiceManagement
import SwiftUI

extension LinkModel {
    func checkForUpdate() {
        note("Checking for an update. There is no Sparkle signature.")
        let local = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        Task.detached {
            let remote = await ReleaseCheck.fetchShortVersion()
            if remote.isEmpty {
                await MainActor.run { self.note("Could not reach the update list. Open the BridgeLink releases page.") }
                return
            }
            if ReleaseCheck.compare(local, remote) <= 0 {
                await MainActor.run { self.note("BridgeLink \(local) is the latest.") }
                return
            }
            let dmgAddress = "https://github.com/CAPPY7/bridgelink/releases/latest/download/BridgeLink.dmg"
            let sumsAddress = "https://github.com/CAPPY7/bridgelink/releases/latest/download/SHA256SUMS"
            async let dmgData = ReleaseCheck.fetchBytes(dmgAddress)
            async let sumsData = ReleaseCheck.fetchBytes(sumsAddress)
            let dmg = await dmgData
            let sums = await sumsData
            guard let dmg, let sums, let text = String(data: sums, encoding: .utf8) else {
                await MainActor.run { self.note("Could not download the update.") }
                return
            }
            let digest = SHA256.hash(data: dmg).map { String(format: "%02x", $0) }.joined()
            let verdict = ReleaseCheck.checkSum(text, "BridgeLink.dmg", digest)
            if verdict != .match {
                await MainActor.run { self.note("The update did not match its checksum. It was not kept.") }
                return
            }
            let folder = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory())
            let dest = folder.appendingPathComponent(ReleaseCheck.updateFileName(remote))
            do {
                try dmg.write(to: dest, options: .atomic)
            } catch {
                await MainActor.run { self.note("Could not save the update in Downloads.") }
                return
            }
            await MainActor.run {
                self.note("BridgeLink \(remote) matched its checksum. Opening the disk image. There is no Sparkle signature.")
                NSWorkspace.shared.open(dest)
            }
        }
    }

    func finishSetup() {
        UserDefaults.standard.set(true, forKey: "didFinishSetup")
        note("Setup finished. Scan the QR with the phone.")
        closeSetup?()
        showDesk()
    }

    func replaySetup() {
        UserDefaults.standard.set(false, forKey: "didFinishSetup")
        openSetup?()
    }

    func resetBridgeLink() {
        if SMAppService.mainApp.status == .enabled {
            try? SMAppService.mainApp.unregister()
        }
        launchAtLogin = false
        live?.cancel()
        live = nil
        liveDeviceId = ""
        book = DeviceBook()
        saveBook()
        phase = .listening
        OldMessages.dropSaved()
        let keys = [
            "clipboardMode", "flags", "showNotificationText", "linksAlways", "dndSync",
            "lockOn", "atDesk", "lockFar", "lockDelay", "notifyOff", "autoRules",
            "folderPaused", "didFinishSetup",
        ]
        for key in keys {
            UserDefaults.standard.removeObject(forKey: key)
        }
        clips = []
        ClipFile.save([])
        dropInboxStore()
        rules = []
        messages = []
        try? "".write(to: smsFile(), atomically: true, encoding: .utf8)
        try? Data("[]".utf8).write(to: historyURL(), options: .atomic)
        lines = ["BridgeLink was reset. Quit, open it again, then pair the phone."]
        MacIdentity.removeStored()
        note("BridgeLink was reset. Quit and open it again so the new Mac key is used.")
    }

    private func smsFile() -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return dir.appendingPathComponent("BridgeLink/sms.txt")
    }
}

struct FirstRunView: View {
    @EnvironmentObject private var model: LinkModel
    @State private var step = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Set up BridgeLink").font(.title2.weight(.semibold))
            Text(stepTitle).font(.headline)
            Text(stepBody).font(.body).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            if step == 1 {
                Button("Allow notifications") { model.notifications() }
            }
            if step == 3 {
                Toggle("Launch at login", isOn: Binding(get: { model.launchAtLogin }, set: { _ in model.toggleLogin() }))
            }
            HStack {
                if step > 0 {
                    Button("Back") { step -= 1 }
                }
                Spacer()
                Button(step == 4 ? "Done" : "Next") {
                    if step == 4 {
                        model.finishSetup()
                    } else {
                        step += 1
                    }
                }
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding(24)
        .frame(width: 440)
    }

    private var stepTitle: String {
        switch step {
        case 0: return "Welcome"
        case 1: return "Notifications"
        case 2: return "Local network"
        case 3: return "Launch at login"
        default: return "Pair the phone"
        }
    }

    private var stepBody: String {
        switch step {
        case 0:
            return "BridgeLink links this Mac to your Android phone. Four short steps, then a QR code. The first open can ask for the keychain. That is normal for a free sideload. If it keeps asking after every update, run scripts/make-mac-cert.sh once and keep that certificate."
        case 1:
            return "Allow notifications so a note, a text, or a low battery from the phone can appear."
        case 2:
            return "macOS asks for the local network the first time the phone connects. Allow it. The phone and the Mac need the same Wi-Fi for the first pair."
        case 3:
            return "Launch at login is optional. The app should live in Applications if you turn it on."
        default:
            return "On the next screen, scan the QR with the phone. Scan the Mac QR is the first button. The PIN lasts two minutes."
        }
    }
}
