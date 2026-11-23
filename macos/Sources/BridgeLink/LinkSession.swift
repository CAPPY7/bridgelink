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
    func handle(_ data: Data, connection: NWConnection, id: UUID, pending: Bool) {
        guard let envelope = try? EnvelopeCodec.parse(data) else { return }
        // Ignore old relay_* messages. They are never sent.
        if OldMessages.ignore(envelope.type) { return }
        lastRx = Date()
        if envelope.type == "hello" {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let token = envelope.text("token")
            let proof = envelope.text("proof")
            let name = envelope.text("name").isEmpty ? "Phone" : envelope.text("name")
            if !token.isEmpty, let device = book.match(token: token) {
                book.touch(id: device.id, now: now)
                saveBook()
                promote(connection, id: id, name: device.name, token: token, deviceId: device.id)
                return
            }
            if !token.isEmpty {
                pendingGate.fail(ip: Self.endpointHost(connection), now: now)
                send(type: "error", payload: ["code": "bad_token", "body": "This phone is not paired"], on: connection)
                connection.cancel()
                finishPending(id)
                note("Rejected an unknown phone. Paired phones stay linked.")
                return
            }
            let phoneCommit = envelope.text("commit")
            if proof.isEmpty && !phoneCommit.isEmpty {
                let macNonce = UUID().uuidString.replacingOccurrences(of: "-", with: "")
                sasConnectionID = id
                sasPhoneCommit = phoneCommit
                sasMacNonce = macNonce
                sasPhoneNonce = ""
                sasName = name
                sasExpected = ""
                sasMacConfirmed = false
                sasPhoneCode = nil
                sasFails = 0
                matchCode = ""
                send(type: "sas", payload: ["commit": sasCommit(macNonce)], on: connection)
                note("Phone started a match. The code appears after both sides commit.")
                return
            }
            let expected = pairingProof(pin: pin, fingerprint: fingerprint)
            if !proof.isEmpty && gate.observe(matched: constantTimeEquals(expected, proof), now: now) {
                let issued = UUID().uuidString.replacingOccurrences(of: "-", with: "")
                let device = book.remember(name: name, token: issued, now: now)
                saveBook()
                promote(connection, id: id, name: name, token: issued, deviceId: device.id)
            } else {
                pendingGate.fail(ip: Self.endpointHost(connection), now: now)
                send(type: "error", payload: ["code": "bad_proof", "body": "Wrong or expired PIN"], on: connection)
                connection.cancel()
                finishPending(id)
                note("Rejected a PIN. Paired phones stay linked.")
            }
            return
        }
        if envelope.type == "sas_reveal" && pending {
            guard id == sasConnectionID else { return }
            let revealed = envelope.text("nonce")
            if !sasRevealMatches(commit: sasPhoneCommit, nonce: revealed) {
                send(type: "error", payload: ["code": "bad_code", "body": "The other side changed its code"], on: connection)
                connection.cancel()
                clearSas()
                finishPending(id)
                note("The phone changed its code. Paired phones stay linked.")
                return
            }
            sasPhoneNonce = revealed
            send(type: "sas_reveal", payload: ["nonce": sasMacNonce], on: connection)
            sasExpected = sasCode(macFingerprint: fingerprint, phoneNonce: revealed, macNonce: sasMacNonce)
            matchCode = sasExpected
            note("Match code \(sasExpected). Tap Codes match on both screens.")
            return
        }
        if envelope.type == "sas_confirm" && pending {
            guard id == sasConnectionID else { return }
            sasPhoneCode = envelope.text("code")
            finishSas(connection: connection, id: id)
            return
        }
        guard !pending, liveID == id else {
            send(type: "error", payload: ["code": "pair_first", "body": "Pair first"], on: connection)
            return
        }
        switch envelope.type {
        case "text":
            note(envelope.text("body"))
            notify("Note from the phone", envelope.text("body"))
        case "clipboard":
            let text = envelope.text("body")
            if clipboardMode == "off" {
                note("Ignored clipboard from the phone")
                return
            }
            if clipboardMode == "ask" || looksSensitive(text) {
                pendingClipboard = text
                note(looksSensitive(text) ? "Sensitive clipboard is waiting" : "Clipboard is waiting")
                notify("Clipboard from the phone", "Open BridgeLink to accept it")
                return
            }
            applyClipboard(text)
            fireAuto("clip", "")
        case "ping":
            send(type: "pong", payload: [:], on: connection)
        case "link":
            if let url = Transfers.acceptUrl(envelope.text("url")) {
                if linksAlways, let open = URL(string: url) {
                    NSWorkspace.shared.open(open)
                    note("Opened a link from the phone")
                } else {
                    pendingLink = url
                    note("Link from the phone. Open it only if you trust it.")
                    notify("Link from the phone", "Open BridgeLink to open it")
                }
            } else {
                note("Refused a link that is not http or https")
            }
        case "clipboard_image":
            if clipboardMode == "off" {
                note("Ignored clipboard from the phone")
                break
            }
            guard let data = Data(base64Encoded: envelope.text("data")), StageRules.imageFits(data.count) else {
                note("Skipped a clipboard image")
                break
            }
            let board = NSPasteboard.general
            board.clearContents()
            board.setData(data, forType: .png)
            note("Clipboard image updated")
        case "notify_dismiss":
            removeNote(key: envelope.text("key"))
            if lastNotifyKey == envelope.text("key") { lastNotifyKey = "" }
            note("The phone dismissed a notification")
        case "dnd":
            if envelope.text("label") == "mac" { break }
            phoneQuiet = envelope.text("on") == "1"
            if dndSync {
                note(phoneQuiet ? "Phone is in Do Not Disturb. Banners stay quiet." : "Phone left Do Not Disturb")
            }
        case "file_begin":
            let fileId = envelope.text("id")
            if envelope.text("dest") == "folder",
               folderSink.begin(fileId, envelope.text("path"), Int(envelope.text("size")) ?? -1) {
                send(type: "file_have", payload: ["id": fileId, "offset": "0"], on: live)
                note("Receiving a shared file")
                break
            }
            let from = Int(envelope.text("from")) ?? 0
            files.begin(id: fileId, name: envelope.text("name"), size: Int(envelope.text("size")) ?? -1, from: from) { [weak self] line, have in
                Task { @MainActor in
                    self?.send(type: "file_have", payload: ["id": fileId, "offset": String(have)], on: self?.live)
                    self?.note(line)
                }
            }
        case "file_chunk":
            if folderSink.has(envelope.text("id")) {
                let fileId = envelope.text("id")
                let offset = Int(envelope.text("offset")) ?? -1
                let raw = envelope.text("data")
                if let line = folderSink.chunk(fileId, offset, raw) {
                    note(line)
                } else {
                    let added = Data(base64Encoded: raw)?.count ?? 0
                    if offset >= 0 {
                        send(type: "file_have", payload: ["id": fileId, "offset": String(offset + added)], on: live)
                    }
                }
                break
            }
            files.chunk(id: envelope.text("id"), offset: Int(envelope.text("offset")) ?? -1, base64: envelope.text("data")) { [weak self] line in
                if let line { Task { @MainActor in self?.note(line) } }
            }
        case "file_end":
            if folderSink.has(envelope.text("id")) {
                note(folderSink.finish(envelope.text("id"), envelope.text("sha256"), sharedBox))
                break
            }
            files.finish(id: envelope.text("id"), sha256: envelope.text("sha256")) { [weak self] line, url, opened in
                Task { @MainActor in
                    self?.note(line)
                    if let url {
                        self?.notifySaved(url.lastPathComponent, url, opened: opened)
                    } else {
                        self?.notify("File from the phone", line)
                    }
                    self?.fireAuto("file_in", line)
                }
            }
        case "file_cancel":
            if folderSink.has(envelope.text("id")) { folderSink.drop(envelope.text("id")) }
            files.cancel(id: envelope.text("id"))
            note("File cancelled")
        case "file_have":
            noteHave(id: envelope.text("id"), offset: Int(envelope.text("offset")) ?? 0)
        case "find_mac":
            startFindMac()
        case "find_stop":
            stopFindMac()
            note("Find stopped")
        case "notify":
            let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
            guard Features.enabled(flags, "notify", fallback: false) else { return }
            if phoneQuiet && dndSync {
                note("Notification held while the phone is in Do Not Disturb")
                return
            }
            let app = envelope.text("app")
            if StageRules.appBlocked(UserDefaults.standard.string(forKey: "notifyOff") ?? "", app) { return }
            lastNotifyKey = envelope.text("key")
            canReply = envelope.text("reply") == "1"
            let shown = Features.notificationShown(
                app: envelope.text("app"),
                title: envelope.text("title"),
                body: envelope.text("body"),
                unlocked: macUnlocked(),
                showText: showNotificationText
            )
            postNote(key: envelope.text("key"), title: shown.title, body: shown.body, reply: canReply)
            note("Notification from the phone")
            fireAuto("notify", app)
        case "otp":
            let otpFlags = UserDefaults.standard.string(forKey: "flags") ?? ""
            guard Features.enabled(otpFlags, "otp", fallback: false) else { return }
            copyConcealed(envelope.text("body"))
            note("Copied a code. It will clear in a minute.")
            fireAuto("sms_code", envelope.text("body"))
        case "battery":
            if envelope.text("label") == "mac" { break }
            let level = envelope.text("level")
            let levelInt = Int(level) ?? -1
            let charging = envelope.text("charging") == "1"
            if StageRules.fullBattery(level: levelInt, alreadyAlerted: phoneFullAlerted) {
                phoneFullAlerted = true
                notify("Phone is fully charged", "The phone battery is at 100%.")
            }
            if levelInt < 100 { phoneFullAlerted = false }
            if Features.lowBattery(level: levelInt, charging: charging, alreadyAlerted: phoneLowAlerted) {
                phoneLowAlerted = true
                notify("Phone battery is low", "The phone battery is at \(level)%.")
            }
            if levelInt > 15 || charging { phoneLowAlerted = false }
            if StageFive.batteryLow(levelInt, charging) {
                if !batteryAuto {
                    batteryAuto = true
                    fireAuto("battery_low", level)
                }
            } else {
                batteryAuto = false
            }
            if level.isEmpty {
                phoneBattery = ""
            } else {
                let chargingText = charging ? ", charging" : ""
                phoneBattery = "Phone battery \(level)%\(chargingText)"
            }
        case "forget":
            let device = envelope.text("deviceId")
            let forgotten = device.isEmpty ? liveDeviceId : device
            if !forgotten.isEmpty {
                book.forget(id: forgotten)
            }
            saveBook()
            live?.cancel()
            live = nil
            liveDeviceId = ""
            phase = .listening
            MacInput.releaseHeld()
            note("The phone forgot this Mac")
        default:
            if handleSix(envelope, connection) { break }
            if handleFive(envelope, connection) { break }
            if handleEasy(envelope, connection) { break }
            note(envelope.type)
            send(type: "error", payload: ["code": "unsupported_type", "body": "Unsupported"], on: connection)
        }
    }

    func promote(_ connection: NWConnection, id: UUID, name: String, token: String, deviceId: String) {
        guard pendingID == id || liveID == id else { return }
        if !SecondPhone.allow(liveDeviceId, deviceId, live != nil && liveID != id) {
            send(type: "error", payload: ["code": "busy", "body": "Another phone is connected"], on: connection)
            connection.cancel()
            finishPending(id)
            note("Another phone tried to connect. This one stays paired.")
            return
        }
        live?.cancel()
        liveID = id
        live = connection
        liveDeviceId = deviceId
        pending = nil
        pendingCount = max(0, pendingCount - 1)
        phase = .paired(name)
        clearLeaveLock()
        send(type: "paired", payload: ["token": token, "deviceId": deviceId], on: connection)
        lastRx = Date()
        note("Paired with \(name)")
        sendMacBattery()
        startFive()
    }

    func finishSas(connection: NWConnection, id: UUID) {
        guard id == sasConnectionID else { return }
        let decision = sasDecision(macConfirmed: sasMacConfirmed, phoneCode: sasPhoneCode, expected: sasExpected)
        switch decision {
        case .waitForMac:
            note("The phone confirmed. Tap Codes match on this Mac if the codes are the same.")
        case .accept:
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let issued = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            let device = book.remember(name: sasName, token: issued, now: now)
            saveBook()
            clearSas()
            promote(connection, id: id, name: sasName, token: issued, deviceId: device.id)
        case .reject:
            sasFails += 1
            if sasFails >= 3 {
                pendingGate.fail(ip: Self.endpointHost(connection), now: Int64(Date().timeIntervalSince1970 * 1000))
                send(type: "error", payload: ["code": "bad_code", "body": "The match codes did not agree"], on: connection)
                connection.cancel()
                clearSas()
                finishPending(id)
                note("Match codes did not agree. Paired phones stay linked.")
            } else {
                sasPhoneCode = nil
                note("Those codes do not match. Look at both screens.")
            }
        }
    }

    func clearSas() {
        sasConnectionID = nil
        sasPhoneCode = nil
        sasMacConfirmed = false
        matchCode = ""
    }

    func loadBook() {
        guard let data = UserDefaults.standard.data(forKey: "devices"),
              let decoded = try? JSONDecoder().decode([PairedDevice].self, from: data) else { return }
        book = DeviceBook(devices: decoded)
        publishPairs()
    }

    func saveBook() {
        if let data = try? JSONEncoder().encode(book.devices) {
            UserDefaults.standard.set(data, forKey: "devices")
        }
        publishPairs()
    }

    func publishPairs() {
        pairedSummary = book.devices.map(\.name).joined(separator: ", ")
        phoneList = book.devices
    }

    func applyClipboard(_ text: String) {
        let board = NSPasteboard.general
        board.clearContents()
        board.setString(text, forType: .string)
        lastEcho = Features.echoKey(text)
        rememberClip(text)
        note("Clipboard updated")
        notify("Clipboard from the phone", "Clipboard updated")
    }

    func copyConcealed(_ text: String) {
        guard !text.isEmpty else { return }
        let board = NSPasteboard.general
        board.clearContents()
        board.setString(text, forType: .string)
        board.setString("", forType: NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType"))
        lastEcho = Features.echoKey(text)
        DispatchQueue.main.asyncAfter(deadline: .now() + 60) {
            if NSPasteboard.general.string(forType: .string) == text {
                NSPasteboard.general.clearContents()
            }
        }
    }

}
