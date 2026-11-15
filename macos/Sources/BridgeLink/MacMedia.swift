import AppKit
import BridgeLinkCore
import Foundation

/// Media keys and the Music or Spotify song title. Volume uses a system script.
enum MacMedia {
    struct Now {
        var title = ""
        var artist = ""
        var playing = "0"
        var volume = "0"
    }

    static func perform(_ action: String, _ volume: String) -> String {
        guard let verb = Features.mediaAction(action) else { return "That media button is not supported" }
        if verb == "volume" {
            guard let number = Int(volume) else { return "Volume needs a number from 0 to 100" }
            let level = StageFour.clampVolume(number)
            if MacShell.ok("/usr/bin/osascript", ["-e", "set volume output volume \(level)"]) {
                return "Mac volume is \(level)"
            }
            return "Could not set the volume. Allow BridgeLink under Automation."
        }
        let before = now()
        let key: Int
        switch verb {
        case "next": key = 17
        case "previous": key = 18
        case "play":
            if before.playing == "1" { return "Already playing" }
            key = 16
        case "pause":
            if before.playing != "1" { return "Already paused" }
            key = 16
        default:
            return "That media button is not supported"
        }
        postMediaKey(key)
        return "Sent \(verb) to the Mac"
    }

    static func now() -> Now {
        let apps = Set(NSWorkspace.shared.runningApplications.compactMap(\.bundleIdentifier))
        if apps.contains("com.apple.Music"), let parsed = ask(musicScript) { return parsed }
        if apps.contains("com.spotify.client"), let parsed = ask(spotifyScript) { return parsed }
        return Now(volume: readVolume())
    }

    static func postMediaKey(_ keyCode: Int) {
        func post(_ down: Bool) {
            let flags = down ? 0xA00 : 0xB00
            let data1 = (keyCode << 16) | (flags << 8)
            guard let event = NSEvent.otherEvent(
                with: .systemDefined,
                location: .zero,
                modifierFlags: NSEvent.ModifierFlags(rawValue: UInt(flags)),
                timestamp: 0,
                windowNumber: 0,
                context: nil,
                subtype: Int16(8),
                data1: data1,
                data2: -1
            ) else { return }
            event.cgEvent?.post(tap: .cghidEventTap)
        }
        post(true)
        post(false)
    }

    private static let musicScript = """
    tell application "Music"
      if player state is playing or player state is paused then
        set flag to "0"
        if player state is playing then set flag to "1"
        return (name of current track) & tab & (artist of current track) & tab & flag
      end if
    end tell
    return tab & tab & "0"
    """

    private static let spotifyScript = """
    tell application "Spotify"
      set flag to "0"
      if player state is playing then set flag to "1"
      return (name of current track) & tab & (artist of current track) & tab & flag
    end tell
    """

    private static func ask(_ source: String) -> Now? {
        guard let text = MacShell.text("/usr/bin/osascript", ["-e", source]) else { return nil }
        let parts = text.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: "\t")
        let title = parts.first ?? ""
        let artist = parts.count > 1 ? parts[1] : ""
        let playing = parts.count > 2 && parts[2].contains("1") ? "1" : "0"
        if title.isEmpty && artist.isEmpty { return nil }
        return Now(title: title, artist: artist, playing: playing, volume: readVolume())
    }

    private static func readVolume() -> String {
        guard let text = MacShell.text("/usr/bin/osascript", ["-e", "output volume of (get volume settings)"]) else { return "0" }
        return String(StageFour.clampVolume(Int(text.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0))
    }
}

enum MacShell {
    static func text(_ path: String, _ args: [String]) -> String? {
        let task = Process()
        task.executableURL = URL(fileURLWithPath: path)
        task.arguments = args
        let pipe = Pipe()
        task.standardOutput = pipe
        task.standardError = Pipe()
        do {
            try task.run()
            task.waitUntilExit()
        } catch {
            return nil
        }
        if task.terminationStatus != 0 { return nil }
        return String(data: pipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8)
    }

    static func ok(_ path: String, _ args: [String]) -> Bool {
        text(path, args) != nil
    }
}

/// Locks the screen. There is no unlock here.
enum MacLock {
    static func lock() -> String {
        _ = MacShell.ok("/usr/bin/defaults", ["write", "com.apple.screensaver", "askForPassword", "-int", "1"])
        _ = MacShell.ok("/usr/bin/defaults", ["write", "com.apple.screensaver", "askForPasswordDelay", "-int", "0"])
        if MacShell.ok("/usr/bin/osascript", ["-e", "tell application \"System Events\" to keystroke \"q\" using {control down, command down}"]) {
            return "Locked the screen"
        }
        if MacShell.ok("/usr/bin/open", ["-a", "ScreenSaverEngine"]) {
            return "Started the screen saver. Ask for a password immediately in Lock Screen settings."
        }
        return "Could not lock. Allow Accessibility for BridgeLink, or press Control-Command-Q."
    }
}
