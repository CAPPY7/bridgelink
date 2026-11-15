import Foundation

/// Rules for screen mirror, phone control, the camera window, and the away relay. Matches Android.
public enum StageSix {
    public static let chunk = 24 * 1024
    public static let norm = 10_000
    public static let mirrorSteps = "On the phone, tap Mirror, then Share screen. Android asks once. Closing the Mac window stops the picture."
    public static let controlSteps = "Turn on BridgeLink under Accessibility. On Android 13 and newer, open App info, the menu, and Allow restricted settings, then turn the service on. Back, Home, and Recents work after that. The Mac never unlocks the phone."
    public static let camNote = "The free app shows the phone camera inside a BridgeLink window. You can share that window in a call. A camera that appears in Zoom, Meet, or FaceTime needs a paid Apple Developer ID, so that extension stays off until those secrets exist."
    public static let relaySteps = "Away from home uses a free relay. Pair once on the same Wi-Fi so both sides share a key. The relay only forwards locked frames. It cannot read them."

    public struct Picture: Equatable {
        public let width: Int
        public let height: Int
        public let bitrate: Int
        public let fps: Int
    }

    public struct Tap: Equatable {
        public let kind: String
        public let x: Int
        public let y: Int
        public let x2: Int
        public let y2: Int
        public let text: String
    }

    public struct Piece: Equatable {
        public let stream: Int
        public let flags: Int
        public let part: Int
        public let parts: Int
        public let data: Data
    }

    public static func quality(_ name: String) -> Picture {
        switch qualityName(name) {
        case "low": return Picture(width: 640, height: 360, bitrate: 800_000, fps: 15)
        case "high": return Picture(width: 1920, height: 1080, bitrate: 6_000_000, fps: 30)
        default: return Picture(width: 1280, height: 720, bitrate: 2_500_000, fps: 30)
        }
    }

    public static func qualityName(_ raw: String) -> String {
        raw == "low" || raw == "high" ? raw : "medium"
    }

    public static func chunks(_ data: Data) -> [Data] {
        if data.isEmpty { return [] }
        var out: [Data] = []
        var index = 0
        while index < data.count {
            let end = min(data.count, index + chunk)
            out.append(data.subdata(in: index..<end))
            index = end
        }
        return out
    }

    public static func join(_ parts: [Data]) -> Data {
        var out = Data()
        for part in parts { out.append(part) }
        return out
    }

    public static func assemble(_ have: inout [Data], part: Int, parts: Int, data: Data) -> Data? {
        if part < 0 || parts < 1 || part >= parts { return nil }
        if part == 0 { have.removeAll() }
        if part != have.count { return nil }
        have.append(data)
        if have.count < parts { return nil }
        let all = join(have)
        have.removeAll()
        return all
    }

    public static func nalType(_ data: Data) -> Int {
        guard let at = nalStart(data), at < data.count else { return -1 }
        return Int(data[at] & 0x1f)
    }

    public static func parameterSets(_ data: Data) -> (Data, Data)? {
        var sps: Data?
        var pps: Data?
        for nal in splitNals(data) {
            if nal.isEmpty { continue }
            let type = Int(nal[nal.startIndex] & 0x1f)
            if type == 7 { sps = nal }
            if type == 8 { pps = nal }
        }
        guard let sps, let pps else { return nil }
        return (sps, pps)
    }

    public static func toAvcc(_ data: Data) -> Data? {
        if data.isEmpty || data.count > 256 * 1024 { return nil }
        let nals = splitNals(data)
        if nals.isEmpty {
            var out = Data()
            appendInt(data.count, 4, &out)
            out.append(data)
            return out
        }
        var out = Data()
        for nal in nals {
            appendInt(nal.count, 4, &out)
            out.append(nal)
        }
        return out
    }

    public static func control(_ kind: String, _ x: Int, _ y: Int, _ x2: Int, _ y2: Int, _ text: String) -> Tap? {
        let known = ["tap", "drag", "scroll", "text", "back", "home", "recents"]
        if !known.contains(kind) { return nil }
        var clean = text.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ")
        if clean.count > 80 { clean = String(clean.prefix(80)) }
        if kind == "text" && clean.isEmpty { return nil }
        return Tap(kind: kind, x: clamp(x), y: clamp(y), x2: clamp(x2), y2: clamp(y2), text: clean)
    }

    public static func picturePoint(viewW: Int, viewH: Int, picW: Int, picH: Int, px: Int, py: Int) -> (Int, Int)? {
        if viewW <= 0 || viewH <= 0 || picW <= 0 || picH <= 0 { return nil }
        if px < 0 || py < 0 || px > viewW || py > viewH { return nil }
        let scaleW = viewW * 1000 / picW
        let scaleH = viewH * 1000 / picH
        let scale = min(scaleW, scaleH)
        if scale <= 0 { return nil }
        let drawnW = picW * scale / 1000
        let drawnH = picH * scale / 1000
        if drawnW <= 0 || drawnH <= 0 { return nil }
        let left = (viewW - drawnW) / 2
        let top = (viewH - drawnH) / 2
        if px < left || py < top || px > left + drawnW || py > top + drawnH { return nil }
        let nx = ((px - left) * norm + drawnW / 2) / drawnW
        let ny = ((py - top) * norm + drawnH / 2) / drawnH
        return (clamp(nx), clamp(ny))
    }

    public static func phonePixel(phoneW: Int, phoneH: Int, x: Int, y: Int) -> (Int, Int) {
        if phoneW <= 0 || phoneH <= 0 { return (0, 0) }
        let px = (clamp(x) * (phoneW - 1) + norm / 2) / norm
        let py = (clamp(y) * (phoneH - 1) + norm / 2) / norm
        return (px, py)
    }

    public static func stopWhenClosed(_ windowOpen: Bool) -> Bool { !windowOpen }

    public static func facing(_ raw: String) -> String { raw == "front" ? "front" : "back" }

    public static func zoom(_ raw: Int) -> Int { min(8, max(1, raw)) }

    public static func flashOn(_ facing: String, _ want: Bool) -> Bool { want && StageSix.facing(facing) != "front" }

    public static func jpegQuality(_ quality: String) -> Int {
        switch qualityName(quality) {
        case "low": return 40
        case "high": return 75
        default: return 55
        }
    }

    public static func relayUrl(_ url: String) -> Bool { relayParts(url) != nil }

    public static func relayParts(_ url: String) -> (host: String, port: String, path: String)? {
        let raw = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if !raw.hasPrefix("wss://") || raw.count > 200 { return nil }
        if raw.contains(" ") || raw.contains("@") || raw.contains("\\") { return nil }
        let rest = String(raw.dropFirst(6))
        if rest.isEmpty || rest.hasPrefix("/") || rest.hasPrefix(".") { return nil }
        let hostport: String
        var path = "/"
        if let slash = rest.firstIndex(of: "/") {
            hostport = String(rest[..<slash])
            path = String(rest[slash...])
            if path.isEmpty { path = "/" }
        } else {
            hostport = rest
        }
        if hostport.isEmpty || hostport.hasPrefix("[") { return nil }
        var host = hostport
        var port = 443
        if let colon = hostport.lastIndex(of: ":"), hostport.firstIndex(of: ":") == colon, colon > hostport.startIndex {
            host = String(hostport[..<colon])
            guard let parsed = Int(hostport[hostport.index(after: colon)...]), parsed >= 1, parsed <= 65535 else { return nil }
            port = parsed
        }
        if host.isEmpty || !host.contains(".") { return nil }
        let lower = host.lowercased()
        if lower == "localhost" || lower.hasPrefix("127.") { return nil }
        return (host, String(port), path)
    }

    public static func room(_ secret: String) -> String { Features.roomId(secret) }

    public static func ticket(_ secret: String, _ role: String) -> String {
        if secret.count < 16 { return "" }
        let who = role == "mac" ? "mac" : "phone"
        return String(Features.echoKey(secret + "\n" + who).prefix(32))
    }

    public static func sealedLooksOpaque(plain: Data, sealed: Data) -> Bool {
        if plain.isEmpty || sealed.count < plain.count + 16 { return false }
        let a = [UInt8](plain)
        let b = [UInt8](sealed)
        if a.count > b.count { return false }
        for i in 0...(b.count - a.count) {
            if Array(b[i..<(i + a.count)]) == a { return false }
        }
        return true
    }

    public static func cameraExtension(_ appleSecrets: Bool) -> Bool { appleSecrets }

    public static func pack(stream: Int, flags: Int, part: Int, parts: Int, data: Data) -> Data? {
        if stream < 1 || stream > 3 || part < 0 || parts < 1 || part >= parts || data.count > 256 * 1024 { return nil }
        var out = Data()
        appendInt(stream, 4, &out)
        out.append(UInt8(flags & 0xff))
        appendInt(part, 2, &out)
        appendInt(parts, 2, &out)
        out.append(data)
        return out
    }

    public static func unpack(_ raw: Data) -> Piece? {
        if raw.count < 9 { return nil }
        let bytes = [UInt8](raw)
        let stream = intOf(bytes[0..<4])
        let flags = Int(bytes[4])
        let part = intOf(bytes[5..<7])
        let parts = intOf(bytes[7..<9])
        if stream < 1 || stream > 3 || parts < 1 || part >= parts { return nil }
        return Piece(stream: stream, flags: flags, part: part, parts: parts, data: raw.subdata(in: 9..<raw.count))
    }

    public static func wsFrame(opcode: Int, payload: Data, mask: [UInt8]?) -> Data? {
        if payload.count > 1_048_576 { return nil }
        let masked = mask != nil
        if masked && mask?.count != 4 { return nil }
        let len = payload.count
        let len7 = len < 126 ? len : (len <= 65535 ? 126 : 127)
        var out = Data()
        out.append(UInt8(0x80 | (opcode & 0x0f)))
        out.append(UInt8((masked ? 0x80 : 0) | len7))
        if len7 == 126 {
            out.append(UInt8((len >> 8) & 0xff))
            out.append(UInt8(len & 0xff))
        } else if len7 == 127 {
            var n = len
            var wide = Data()
            for _ in 0..<8 {
                wide.insert(UInt8(n & 0xff), at: 0)
                n >>= 8
            }
            out.append(wide)
        }
        let raw = [UInt8](payload)
        if let mask {
            out.append(contentsOf: mask)
            for i in 0..<raw.count { out.append(raw[i] ^ mask[i & 3]) }
        } else {
            out.append(payload)
        }
        return out
    }

    public static func wsSize(_ buf: Data, _ have: Int) -> Int {
        if have < 2 || have > buf.count { return have < 2 ? -1 : 0 }
        let bytes = [UInt8](buf.prefix(have))
        return wsSize(bytes, have)
    }

    public static func wsPayload(_ frame: Data) -> Data? {
        let bytes = [UInt8](frame)
        let size = wsSize(bytes, bytes.count)
        if size != bytes.count { return nil }
        let len7 = Int(bytes[1] & 0x7f)
        let masked = (bytes[1] & 0x80) != 0
        var header = 2
        var len = 0
        if len7 < 126 {
            len = len7
        } else if len7 == 126 {
            len = intOf(bytes[2..<4])
            header = 4
        } else {
            len = intOf(bytes[2..<10])
            header = 10
        }
        let off = header + (masked ? 4 : 0)
        if off + len > bytes.count { return nil }
        var payload = [UInt8](repeating: 0, count: len)
        if masked {
            for i in 0..<len { payload[i] = bytes[off + i] ^ bytes[header + (i & 3)] }
        } else if len > 0 {
            payload = Array(bytes[off..<(off + len)])
        }
        return Data(payload)
    }

    private static func wsSize(_ bytes: [UInt8], _ have: Int) -> Int {
        if have < 2 || have > bytes.count { return have < 2 ? -1 : 0 }
        let len7 = Int(bytes[1] & 0x7f)
        let masked = (bytes[1] & 0x80) != 0
        var header = 2
        var len = 0
        if len7 < 126 {
            len = len7
        } else if len7 == 126 {
            if have < 4 { return -1 }
            len = intOf(bytes[2..<4])
            header = 4
        } else {
            if have < 10 { return -1 }
            len = intOf(bytes[2..<10])
            header = 10
        }
        if len < 0 || len > 1_048_576 { return 0 }
        let total = header + (masked ? 4 : 0) + len
        if have < total { return -1 }
        return total
    }

    private static func clamp(_ value: Int) -> Int { min(norm, max(0, value)) }

    private static func nalStart(_ data: Data) -> Int? {
        let bytes = [UInt8](data)
        guard let code = startCode(bytes, 0) else { return nil }
        return code + codeSize(bytes, code)
    }

    private static func splitNals(_ data: Data) -> [Data] {
        let bytes = [UInt8](data)
        var codes: [Int] = []
        var at = 0
        while at < bytes.count {
            guard let code = startCode(bytes, at) else { break }
            codes.append(code)
            at = code + codeSize(bytes, code)
        }
        var nals: [Data] = []
        for n in 0..<codes.count {
            let from = codes[n] + codeSize(bytes, codes[n])
            let end = n + 1 < codes.count ? codes[n + 1] : bytes.count
            if from < end { nals.append(Data(bytes[from..<end])) }
        }
        return nals
    }

    private static func startCode(_ data: [UInt8], _ from: Int) -> Int? {
        var i = max(0, from)
        while i + 3 < data.count {
            if data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1 { return i }
            if data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1 { return i }
            i += 1
        }
        return nil
    }

    private static func codeSize(_ data: [UInt8], _ code: Int) -> Int {
        if code + 3 < data.count && data[code + 2] == 0 && data[code + 3] == 1 { return 4 }
        return 3
    }

    private static func appendInt(_ value: Int, _ bytes: Int, _ out: inout Data) {
        var shift = (bytes - 1) * 8
        for _ in 0..<bytes {
            out.append(UInt8((value >> shift) & 0xff))
            shift -= 8
        }
    }

    private static func intOf(_ data: ArraySlice<UInt8>) -> Int {
        var value = 0
        for byte in data { value = (value << 8) | Int(byte) }
        return value
    }
}
