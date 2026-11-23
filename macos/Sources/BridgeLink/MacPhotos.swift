import AppKit
import BridgeLinkCore
import Foundation
import UserNotifications

struct PhonePhoto: Identifiable, Equatable {
    var id: String
    var name: String
    var thumb: Data?
}

extension LinkModel {
    func postCall(key: String, title: String) {
        ensureNoteCategories()
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = "Decline ends the call on the phone when that is allowed."
        content.categoryIdentifier = "bridgelink.call"
        content.userInfo = ["key": key]
        let stable = Features.notifyId(key)
        let request = UNNotificationRequest(identifier: stable.isEmpty ? "call" : stable, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func declineCall(_ key: String) {
        send(type: "call_decline", payload: ["key": key], on: live)
        removeNote(key: key)
        note("Asked the phone to decline the call")
    }

    func pausePhone(_ on: Bool) {
        phonePause = on
        send(type: "dnd", payload: ["on": on ? "1" : "0", "label": "mac", "ask": "1"], on: live)
    }

    func setCalls(_ on: Bool) {
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        UserDefaults.standard.set(StageRules.setFlag(flags, "calls", on), forKey: "flags")
        callsOn = on
        note(on ? "Call alerts are on." : "Call alerts are off.")
    }

    func setPhotos(_ on: Bool) {
        let flags = UserDefaults.standard.string(forKey: "flags") ?? ""
        UserDefaults.standard.set(StageRules.setFlag(flags, "photos", on), forKey: "flags")
        photosOn = on
        if !on { phonePhotos = []; photoMore = false }
        note(on ? "Phone photos are on." : "Phone photos are off.")
    }

    func setMenuBattery(_ on: Bool) {
        UserDefaults.standard.set(on, forKey: "menuBattery")
        menuBattery = on
        onStatus?()
    }

    func askPhotos(_ offset: Int) {
        guard photosOn else {
            note("Turn on phone photos in Settings, then open this page again.")
            return
        }
        guard case .paired = phase else {
            note("Pair the phone, then try the photos again.")
            return
        }
        if offset == 0 { phonePhotos = [] }
        send(type: "photo_list", payload: ["offset": String(max(0, offset))], on: live)
    }

    func saveSelectedPhotos() {
        let chosen = phonePhotos.filter { photoPicked.contains($0.id) }
        if chosen.isEmpty {
            note("Select one or more photos first.")
            return
        }
        for photo in chosen where photo.id.allSatisfy({ $0.isNumber }) {
            send(type: "photo_get", payload: ["id": photo.id], on: live)
        }
        note("Asking the phone for \(chosen.count) photo\(chosen.count == 1 ? "" : "s"). They are checked before they are saved.")
    }

    func applyPhotoPage(_ offset: String, _ ids: String, _ more: Bool) {
        photoMore = more
        if offset == "0" { phonePhotos = [] }
        for id in ids.split(separator: ",") {
            let text = String(id)
            guard text.allSatisfy({ $0.isNumber }), !phonePhotos.contains(where: { $0.id == text }) else { continue }
            phonePhotos.append(PhonePhoto(id: text, name: "Photo", thumb: nil))
        }
        if phonePhotos.count > 400 { phonePhotos.removeFirst(phonePhotos.count - 400) }
    }

    func applyPhotoThumb(_ id: String, _ name: String, _ data: String) {
        guard id.allSatisfy({ $0.isNumber }), let raw = Data(base64Encoded: data), raw.count <= 80_000 else { return }
        if let index = phonePhotos.firstIndex(where: { $0.id == id }) {
            phonePhotos[index].name = name.isEmpty ? phonePhotos[index].name : name
            phonePhotos[index].thumb = raw
        }
    }

    func openFinderServices() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.keyboard?Shortcuts") {
            NSWorkspace.shared.open(url)
        }
        note("Right-click a file in Finder, then Quick Actions or Services, then Send to phone. The app must be in Applications.")
    }
}
