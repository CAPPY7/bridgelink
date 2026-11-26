import CryptoKit
import Darwin
import Foundation

/// Pure rules shared with the Android Features class. No AppKit, so tests can run them.
public enum Features {
    public static let names = [
        "files", "links", "text", "clipboard", "find", "battery", "media",
        "notify", "otp", "sms", "clips", "scan", "folder",
        "input", "mirror", "control", "camera", "lock", "calls", "photos",
    ]

    private static let keywords = [
        "one-time", "one time", "onetime", "verification", "passcode", "password", "otp", "pin", "code",
    ]

    public static func shared(_ local: [String], _ remote: [String]) -> [String] {
        let other = Set(remote)
        var both: [String] = []
        for name in local where other.contains(name) && !both.contains(name) {
            both.append(name)
        }
        return both
    }

    public static func enabled(_ settings: String, _ feature: String, fallback: Bool) -> Bool {
        if feature.isEmpty { return false }
        if settings.isEmpty { return fallback }
        for part in settings.split(separator: ",") {
            let bits = part.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            if bits.count == 2 && bits[0] == feature {
                if bits[1] == "1" { return true }
                if bits[1] == "0" { return false }
            }
        }
        return fallback
    }

    public static func privateByDefault(_ feature: String) -> Bool {
        ["clipboard", "notify", "otp", "sms", "clips", "folder", "input", "mirror", "control", "camera", "lock", "calls", "photos"].contains(feature)
    }

    public static func resumeFrom(have: Int64, size: Int64) -> Int64 {
        if size < 0 || size > Int64(Transfers.maxBytes) || have < 0 || have > size { return -1 }
        return have
    }

    public static func sensitiveText(_ text: String) -> Bool {
        if text.isEmpty { return false }
        let lower = text.lowercased()
        if lower.contains("password") || lower.contains("passcode") { return true }
        let compact = text.filter { !$0.isWhitespace }
        if compact.count >= 6 && compact.count <= 8 && compact.allSatisfy({ $0.isNumber }) { return true }
        return card(text) || otp(text) != nil
    }

    public static func echoKey(_ text: String) -> String {
        sha256Hex(text)
    }

    public static func isEcho(_ lastKey: String, _ text: String) -> Bool {
        !lastKey.isEmpty && lastKey == echoKey(text)
    }

    public static func otp(_ message: String) -> String? {
        if message.isEmpty || message.count > 4000 { return nil }
        let lower = message.lowercased()
        var keys: [Int] = []
        for word in keywords {
            var from = lower.startIndex
            while let range = lower.range(of: word, range: from..<lower.endIndex) {
                if wordEdge(lower, range) { keys.append(lower.distance(from: lower.startIndex, to: range.lowerBound)) }
                from = range.upperBound
            }
        }
        if keys.isEmpty { return nil }
        let chars = Array(message)
        var best: String?
        var bestDist = Int.max
        var index = 0
        while index < chars.count {
            if chars[index].isNumber {
                let start = index
                while index < chars.count && chars[index].isNumber { index += 1 }
                let length = index - start
                if length >= 4 && length <= 8 && !isPrice(lower, start) && !isDate(chars, start, index) {
                    var dist = Int.max
                    for key in keys { dist = min(dist, abs(key - start)) }
                    if dist <= 40 && dist < bestDist {
                        bestDist = dist
                        best = String(chars[start..<index])
                    }
                }
            } else {
                index += 1
            }
        }
        return best
    }

    public static func batteryChanged(previousLevel: Int, previousCharging: Bool, hadSample: Bool, level: Int, charging: Bool) -> Bool {
        if level < 0 || level > 100 { return false }
        if !hadSample { return true }
        return level != previousLevel || charging != previousCharging
    }

    public static func addressOrder(primary: String, extra: String) -> [String] {
        var ordered: [String] = []
        func add(_ raw: String) {
            let item = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if item.isEmpty || item.contains(" ") || item.contains("/") || ordered.contains(item) { return }
            ordered.append(item)
        }
        add(primary)
        for part in extra.split(separator: ",") { add(String(part)) }
        return ordered
    }

    public static func rememberLine(existing: [String], line: String, cap: Int) -> [String] {
        let limit = max(1, cap)
        var next: [String] = []
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { next.append(trimmed) }
        for item in existing where next.count < limit { next.append(item) }
        return next
    }

    public static func diagnostics(version: String, os: String, paired: Bool, hostSaved: Bool, port: Int, status: String) -> String {
        var safe = status.replacingOccurrences(of: "\n", with: " ")
        if safe.count > 160 { safe = String(safe.prefix(160)) }
        return """
        BridgeLink diagnostics
        version=\(version)
        os=\(os)
        paired=\(paired ? "yes" : "no")
        hostSaved=\(hostSaved ? "yes" : "no")
        port=\(port)
        status=\(safe)
        secrets=none
        """
    }

    public static func lowBattery(level: Int, charging: Bool, alreadyAlerted: Bool) -> Bool {
        level >= 0 && level <= 15 && !charging && !alreadyAlerted
    }

    public static func mediaAction(_ raw: String) -> String? {
        switch raw {
        case "play", "pause", "next", "previous", "seek", "volume": return raw
        default: return nil
        }
    }

    public static func pointerKind(_ kind: String) -> Bool {
        ["move", "tap", "right", "scroll", "drag", "drag_end", "key"].contains(kind)
    }

    public static func smsParts(_ text: String) -> [String] {
        if text.isEmpty { return [] }
        let units = Array(text.utf16)
        let gsm = isGsm(units)
        let single = gsm ? 160 : 70
        if units.count <= single { return [text] }
        let chunk = gsm ? 153 : 67
        var parts: [String] = []
        var index = 0
        while index < units.count {
            let end = min(units.count, index + chunk)
            parts.append(decodeUtf16(units[index..<end]))
            index = end
        }
        return parts
    }

    public static func scanName(_ epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(max(0, epochMs)) / 1000)
        let format = DateFormatter()
        format.locale = Locale(identifier: "en_US_POSIX")
        format.timeZone = TimeZone(identifier: "UTC")
        format.dateFormat = "yyyyMMdd-HHmmss"
        return "scan-\(format.string(from: date)).pdf"
    }

    /// Only a scan PDF or JPG may open by itself, and only when that setting is on and this file was marked as a scan.
    public static func autoOpenScan(_ name: String, _ openScans: Bool, _ markedScan: Bool) -> Bool {
        if !openScans || !markedScan { return false }
        let lower = name.lowercased()
        return lower.hasPrefix("scan-") && (lower.hasSuffix(".pdf") || lower.hasSuffix(".jpg"))
    }

    public static func triggerMatches(rule: String, event: String, enabled: Bool) -> Bool {
        enabled && rule == event
    }

    public static func unseen(already: [String], current: [String]) -> [String] {
        let seen = Set(already)
        return current.filter { !$0.isEmpty && !seen.contains($0) }
    }

    public static func safeRelative(_ raw: String) -> String? {
        let path = raw.replacingOccurrences(of: "\\", with: "/").trimmingCharacters(in: .whitespacesAndNewlines)
        if path.isEmpty || path.count > 240 || path.contains("\u{0}") || path.hasPrefix("/") || path.contains(":") { return nil }
        var keep: [String] = []
        for part in path.split(separator: "/", omittingEmptySubsequences: false) {
            if part.isEmpty || part.hasPrefix(".") || part == ".." { return nil }
            keep.append(Transfers.sanitizeName(String(part)))
        }
        if keep.isEmpty { return nil }
        return keep.joined(separator: "/")
    }

    public static func shouldMirror(ongoing: Bool, silent: Bool, appAllowed: Bool, duplicate: Bool) -> Bool {
        !ongoing && !silent && appAllowed && !duplicate
    }

    /// One Mac banner per phone notification. An empty key does not share a banner.
    public static func notifyId(_ key: String) -> String {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.count > 180 { return "" }
        return "notify-" + trimmed
    }

    public static func roomId(_ pairingSecret: String) -> String {
        if pairingSecret.count < 16 { return "" }
        return String(sha256Hex(pairingSecret).prefix(32))
    }

    public static func deriveKey(_ pairingSecret: String) -> Data {
        hkdf(ikm: Data(pairingSecret.utf8), salt: Data("bridgelink-e2e-v1".utf8), info: Data("key".utf8), length: 32)
    }

    public static func directionKey(_ secret: String, _ info: String) -> Data {
        hkdf(ikm: Data(secret.utf8), salt: Data("bridgelink-e2e-v1".utf8), info: Data(info.utf8), length: 32)
    }

    public static func seal(key: Data, counter: Int64, nonce: Data, plaintext: Data) throws -> Data {
        if key.count != 32 || nonce.count != 12 || counter < 0 { throw CodecError.badJson }
        let box = try AES.GCM.seal(plaintext, using: SymmetricKey(data: key), nonce: AES.GCM.Nonce(data: nonce), authenticating: counterData(counter))
        var out = Data()
        out.append(nonce)
        out.append(counterData(counter))
        out.append(box.ciphertext)
        out.append(box.tag)
        return out
    }

    public static func open(key: Data, sealed: Data) throws -> Data {
        if key.count != 32 || sealed.count < 12 + 8 + 16 { throw CodecError.badJson }
        let nonce = sealed.prefix(12)
        let counter = sealed.subdata(in: 12..<20)
        let body = sealed.suffix(from: 20)
        let tag = body.suffix(16)
        let cipher = body.dropLast(16)
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: nonce), ciphertext: cipher, tag: tag)
        return try AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: counter)
    }

    public static func counterOf(_ sealed: Data) -> Int64 {
        if sealed.count < 20 { return -1 }
        var value: Int64 = 0
        for byte in sealed.subdata(in: 12..<20) { value = (value << 8) | Int64(byte) }
        return value
    }

    public struct Notice: Equatable {
        public let title: String
        public let body: String
    }

    /// Full text only when the Mac is unlocked and the user turned show-text on.
    public static func notificationShown(app: String, title: String, body: String, unlocked: Bool, showText: Bool) -> Notice {
        let name = app.isEmpty ? "Notification" : app
        if !unlocked || !showText { return Notice(title: name, body: "Notification") }
        return Notice(title: title.isEmpty ? name : title, body: body)
    }

    public static func streamHeader(streamId: Int, offset: Int64, flags: Int, length: Int) -> Data? {
        if streamId < 0 || offset < 0 || length < 0 || length > 64 * 1024 { return nil }
        var out = Data(capacity: 17)
        appendInt(streamId, 4, &out)
        appendInt(Int(offset), 8, &out)
        out.append(UInt8(flags & 0xff))
        appendInt(length, 4, &out)
        return out
    }

    public static func streamLength(_ header: Data) -> Int {
        if header.count != 17 { return -1 }
        let length = intOf(header.suffix(4))
        if length < 0 || length > 64 * 1024 { return -1 }
        return length
    }

    private static func wordEdge(_ text: String, _ range: Range<String.Index>) -> Bool {
        if range.lowerBound > text.startIndex {
            let previous = text[text.index(before: range.lowerBound)]
            if previous.isLetter { return false }
        }
        if range.upperBound < text.endIndex && text[range.upperBound].isLetter { return false }
        return true
    }

    private static func isPrice(_ lower: String, _ start: Int) -> Bool {
        let chars = Array(lower)
        var i = start - 1
        while i >= 0 && (chars[i] == Character(" ") || chars[i] == Character(",") || chars[i] == Character(".")) { i -= 1 }
        if i >= 0 {
            let c = chars[i]
            if c == Character("$") || c == Character("₹") || c == Character("€") || c == Character("£") { return true }
        }
        let from = max(0, start - 4)
        let pre = String(chars[from..<max(from, start)])
        return pre.contains("rs") || pre.contains("inr") || pre.contains("usd")
    }

    private static func isDate(_ chars: [Character], _ start: Int, _ end: Int) -> Bool {
        separatedNumber(chars, start, -1) || separatedNumber(chars, end, 1)
    }

    private static func separatedNumber(_ chars: [Character], _ edge: Int, _ direction: Int) -> Bool {
        let mark = direction < 0 ? edge - 1 : edge
        if mark < 0 || mark >= chars.count { return false }
        let sep = chars[mark]
        if sep != Character("/") && sep != Character("-") { return false }
        var i = mark + direction
        while i >= 0 && i < chars.count && chars[i] == Character(" ") { i += direction }
        return i >= 0 && i < chars.count && chars[i].isNumber
    }

    private static func decodeUtf16(_ units: ArraySlice<UInt16>) -> String {
        let array = Array(units)
        if array.isEmpty { return "" }
        return array.withUnsafeBufferPointer { buffer in
            String(utf16CodeUnits: buffer.baseAddress!, count: buffer.count)
        }
    }

    private static func isGsm(_ units: [UInt16]) -> Bool {
        for unit in units {
            if unit == 10 || unit == 13 { continue }
            if unit < 32 || unit > 126 { return false }
            if let scalar = UnicodeScalar(unit), "{}[]~|^\\".contains(Character(scalar)) { return false }
        }
        return true
    }

    private static func card(_ text: String) -> Bool {
        let chars = Array(text)
        var index = 0
        while index < chars.count {
            if chars[index].isNumber {
                var digits = ""
                while index < chars.count {
                    let c = chars[index]
                    if c.isNumber {
                        digits.append(c)
                        index += 1
                    } else if (c == Character(" ") || c == Character("-")) && !digits.isEmpty {
                        index += 1
                    } else { break }
                }
                if digits.count >= 13 && digits.count <= 19 && luhn(digits) { return true }
            } else {
                index += 1
            }
        }
        return false
    }

    private static func luhn(_ digits: String) -> Bool {
        var sum = 0
        var alt = false
        for char in digits.reversed() {
            var n = Int(String(char)) ?? 0
            if alt {
                n *= 2
                if n > 9 { n -= 9 }
            }
            sum += n
            alt.toggle()
        }
        return sum % 10 == 0
    }

    private static func hkdf(ikm: Data, salt: Data, info: Data, length: Int) -> Data {
        let prk = Data(HMAC<SHA256>.authenticationCode(for: ikm, using: SymmetricKey(data: salt)))
        var okm = Data()
        var block = Data()
        var counter = 1
        while okm.count < length {
            var message = block
            message.append(info)
            message.append(UInt8(counter))
            block = Data(HMAC<SHA256>.authenticationCode(for: message, using: SymmetricKey(data: prk)))
            okm.append(block)
            counter += 1
        }
        return okm.prefix(length)
    }

    private static func counterData(_ counter: Int64) -> Data {
        var out = Data(count: 8)
        var value = counter
        for index in (0..<8).reversed() {
            out[index] = UInt8(value & 0xff)
            value >>= 8
        }
        return out
    }

    private static func appendInt(_ value: Int, _ bytes: Int, _ out: inout Data) {
        var shift = (bytes - 1) * 8
        for _ in 0..<bytes {
            out.append(UInt8((value >> shift) & 0xff))
            shift -= 8
        }
    }

    private static func intOf(_ data: Data) -> Int {
        var value = 0
        for byte in data { value = (value << 8) | Int(byte) }
        return value
    }
}

public enum FinderSend {
    public static let fileTypes = ["public.file-url", "public.plain-text", "public.url"]
    public static let itemType = "public.item"
}

public enum LinkKind: Equatable {
    case keyChanged
    case wrongCode
    case tokenRejected
    case versionMismatch
    case timeout
    case refused
    case networkLost
    case firewall

    public var fatal: Bool {
        switch self {
        case .keyChanged, .wrongCode, .tokenRejected, .versionMismatch:
            return true
        case .timeout, .refused, .networkLost, .firewall:
            return false
        }
    }

    public var message: String {
        switch self {
        case .keyChanged: return "This Mac key changed. Forget the phone, then pair again."
        case .wrongCode: return "The match code was wrong. Connect again from the phone."
        case .tokenRejected: return "This Mac rejected the phone. Scan the QR to pair again."
        case .versionMismatch: return "The phone and the Mac are different versions. Update both copies, then pair again."
        case .timeout: return "The phone did not answer in time. Check that both are on the same Wi-Fi."
        case .refused: return "The phone refused the connection. Leave BridgeLink open on the phone and try again."
        case .networkLost: return "The Wi-Fi link dropped. Stay on the same Wi-Fi. BridgeLink will try again."
        case .firewall: return "BridgeLink cannot accept connections. Open System Settings, Network, Firewall, and allow incoming connections for BridgeLink. Then open BridgeLink again."
        }
    }

    public static func from(code: String) -> LinkKind? {
        switch code {
        case "key_changed": return .keyChanged
        case "bad_code", "bad_pin", "bad_proof": return .wrongCode
        case "bad_token": return .tokenRejected
        case "bad_version": return .versionMismatch
        default: return nil
        }
    }

    public static func fromErrno(_ errno: Int32) -> LinkKind {
        if errno == EPERM || errno == EACCES { return .firewall }
        if errno == ECONNREFUSED || errno == ECONNRESET { return .refused }
        if errno == ETIMEDOUT || errno == EADDRINUSE { return .timeout }
        return .networkLost
    }
}
