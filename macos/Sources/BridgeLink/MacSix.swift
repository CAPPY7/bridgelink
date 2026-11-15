import AppKit
import BridgeLinkCore
import Foundation
import Network
import Security

extension LinkModel {
    func ensureRelayKey() {
        var secret = UserDefaults.standard.string(forKey: "relaySecret") ?? ""
        if secret.count < 16 {
            let left = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            let right = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            secret = left + right
            UserDefaults.standard.set(secret, forKey: "relaySecret")
        }
        send(type: "relay_key", payload: ["body": secret], on: live)
    }

    func tryRelay() {
        if live != nil { return }
        let url = UserDefaults.standard.string(forKey: "relayUrl") ?? six.relayUrl
        let secret = UserDefaults.standard.string(forKey: "relaySecret") ?? ""
        if away == nil {
            away = AwayLink(
                onFrames: { [weak self] data in
                    Task { @MainActor in self?.acceptRelay(data) }
                },
                onUp: { [weak self] in
                    Task { @MainActor in
                        guard let self else { return }
                        if case .paired = self.phase {} else {
                            let name = self.pairedSummary.isEmpty ? "Phone" : self.pairedSummary
                            self.phase = .paired(name)
                        }
                        self.note("Connected through the away relay. The relay cannot read the link.")
                    }
                },
                onDown: { [weak self] reason in
                    Task { @MainActor in self?.note(reason) }
                }
            )
        }
        away?.connect(url: url, secret: secret)
    }

    func acceptRelay(_ framed: Data) {
        var buffer = FrameBuffer()
        guard let items = try? buffer.pushAll(framed) else { return }
        let sink = NWConnection(host: "127.0.0.1", port: 9, using: .tcp)
        replyOnRelay = true
        for item in items {
            if let json = item.json {
                handle(json, connection: sink, id: liveID, pending: false)
            } else if let binary = item.binary {
                takePicture(binary)
            }
        }
        replyOnRelay = false
    }

    func takePicture(_ raw: Data) {
        guard let piece = StageSix.unpack(raw) else { return }
        if piece.stream == 1 && piece.flags & 2 != 0 {
            guard piece.data.count > 4 else { return }
            let nal = piece.data.dropFirst(4)
            let type = Int(nal.first ?? 0) & 0x1f
            if type == 7 { six.sps = Data(nal) }
            if type == 8 { six.pps = Data(nal) }
            if let sps = six.sps, let pps = six.pps { six.video.setParameterSets(sps: sps, pps: pps) }
            return
        }
        if piece.stream == 1 {
            guard let whole = StageSix.assemble(&six.mirrorParts, part: piece.part, parts: piece.parts, data: piece.data) else { return }
            guard let avcc = StageSix.toAvcc(whole) else { return }
            six.video.decode(avcc: avcc, key: piece.flags & 1 != 0)
            return
        }
        if piece.stream == 2 {
            guard let whole = StageSix.assemble(&six.camParts, part: piece.part, parts: piece.parts, data: piece.data) else { return }
            if let image = NSImage(data: whole) { six.camera = image }
            return
        }
        if piece.stream == 3 { six.audio.play(piece.data) }
    }

    func startMirror() {
        let picture = StageSix.quality(six.quality)
        six.picW = picture.width
        six.picH = picture.height
        six.mirrorOn = true
        six.mirrorStatus = "Asking the phone to share its screen."
        send(type: "mirror_start", payload: ["quality": StageSix.qualityName(six.quality)], on: live)
    }

    func stopMirror() {
        guard StageSix.stopWhenClosed(false) else { return }
        six.mirrorOn = false
        six.mirrorStatus = "Mirror is off."
        send(type: "mirror_stop", payload: [:], on: live)
    }

    func startCamera() {
        six.camOn = true
        six.camStatus = "Asking the phone for the camera."
        send(type: "cam_start", payload: [
            "facing": StageSix.facing(six.facing),
            "zoom": String(StageSix.zoom(six.zoom)),
            "flash": StageSix.flashOn(six.facing, six.flash) ? "1" : "0",
            "mic": six.mic ? "1" : "0",
        ], on: live)
    }

    func stopCamera() {
        six.camOn = false
        six.camStatus = "Camera is off."
        send(type: "cam_stop", payload: [:], on: live)
    }

    func sendControl(_ kind: String, _ x: Int, _ y: Int, _ x2: Int, _ y2: Int, _ text: String) {
        guard let tap = StageSix.control(kind, x, y, x2, y2, text) else {
            note("That touch was not sent.")
            return
        }
        send(type: "control", payload: [
            "kind": tap.kind,
            "x": String(tap.x),
            "y": String(tap.y),
            "x2": String(tap.x2),
            "y2": String(tap.y2),
            "text": tap.text,
        ], on: live)
    }

    func saveRelay() {
        let url = six.relayUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard StageSix.relayUrl(url) else {
            note("The relay address must start with wss://")
            return
        }
        UserDefaults.standard.set(url, forKey: "relayUrl")
        send(type: "relay_set", payload: ["url": url], on: live)
        note("Saved the relay address.")
    }

    func handleSix(_ envelope: ParsedEnvelope, _ connection: NWConnection) -> Bool {
        switch envelope.type {
        case "mirror_stop":
            six.mirrorOn = false
            six.mirrorStatus = "The phone stopped sharing."
            return true
        case "cam_stop":
            six.camOn = false
            six.camStatus = "The phone stopped the camera."
            return true
        case "relay_set":
            let url = envelope.text("url").isEmpty ? envelope.text("body") : envelope.text("url")
            guard StageSix.relayUrl(url) else {
                note("The relay address must start with wss://")
                return true
            }
            UserDefaults.standard.set(url, forKey: "relayUrl")
            six.relayUrl = url
            note("Saved the relay address.")
            return true
        default:
            return false
        }
    }
}

final class AwayLink {
    var isLive = false
    private let onFrames: (Data) -> Void
    private let onUp: () -> Void
    private let onDown: (String) -> Void
    private var task: URLSessionWebSocketTask?
    private var session: URLSession?
    private var sendKey = Data()
    private var recvKey = Data()
    private var sessionId: Int64 = 1
    private var counter: Int64 = 0
    private var seen: Int64 = 0
    private var replaySession: Int64 = 0
    private var replayArmed = false

    init(onFrames: @escaping (Data) -> Void, onUp: @escaping () -> Void, onDown: @escaping (String) -> Void) {
        self.onFrames = onFrames
        self.onUp = onUp
        self.onDown = onDown
    }

    func connect(url: String, secret: String) {
        stop()
        guard StageSix.relayUrl(url), secret.count >= 16, let parts = StageSix.relayParts(url) else {
            onDown("Pair on the same Wi-Fi once, and save a wss relay address.")
            return
        }
        sendKey = Features.directionKey(secret, "mac-to-phone")
        recvKey = Features.directionKey(secret, "phone-to-mac")
        sessionId = Int64.random(in: 1...Int64.max)
        counter = 0
        seen = 0
        replayArmed = false
        var path = parts.path
        if path.hasSuffix("/") { path.removeLast() }
        guard let target = URL(string: "wss://\(parts.host):\(parts.port)\(path)/room/\(StageSix.room(secret))") else {
            onDown("That relay address is not valid.")
            return
        }
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: target)
        self.session = session
        self.task = task
        task.resume()
        let ticket = StageSix.relayHello(secret, "mac", Int64(Date().timeIntervalSince1970))
        task.send(.string(ticket)) { [weak self] error in
            guard let self else { return }
            if error != nil {
                self.onDown("The away relay could not be reached.")
                return
            }
            self.isLive = true
            self.onUp()
            self.listen()
        }
    }

    func sendJson(_ json: Data) {
        sendFramed(encodeFrame(json: json))
    }

    func stop() {
        isLive = false
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        session?.invalidateAndCancel()
        session = nil
    }

    private func sendFramed(_ framed: Data) {
        guard isLive, sendKey.count == 32 else { return }
        var nonce = Data(count: 12)
        nonce.withUnsafeMutableBytes { raw in
            _ = SecRandomCopyBytes(kSecRandomDefault, 12, raw.baseAddress!)
        }
        counter += 1
        guard let sealed = try? Features.seal(key: sendKey, counter: counter, nonce: nonce, plaintext: framed) else { return }
        var packet = Data()
        var sid = sessionId.bigEndian
        withUnsafeBytes(of: &sid) { packet.append(contentsOf: $0) }
        packet.append(sealed)
        if packet.count > 512 * 1024 { return }
        task?.send(.data(packet)) { _ in }
    }

    private func listen() {
        task?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(.data(let data)):
                if data.count >= 8 + 12 + 8 + 16 {
                    let sid = data.prefix(8).reduce(Int64(0)) { ($0 << 8) | Int64($1) }
                    let sealed = data.dropFirst(8)
                    if let opened = try? Features.open(key: self.recvKey, sealed: Data(sealed)) {
                        if !self.replayArmed || sid != self.replaySession {
                            self.replayArmed = true
                            self.replaySession = sid
                            self.seen = 0
                        }
                        let count = Features.counterOf(Data(sealed))
                        if count > self.seen {
                            self.seen = count
                            self.onFrames(opened)
                        }
                    }
                }
                self.listen()
            case .success(.string):
                self.stop()
                self.onDown("The relay sent plain text. The link was closed.")
            case .failure:
                self.isLive = false
                self.onDown("The away relay dropped.")
            @unknown default:
                self.listen()
            }
        }
    }
}
