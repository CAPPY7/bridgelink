import Foundation
import Security

public enum FrameKind {
    public static let json: UInt8 = 1
    public static let binary: UInt8 = 2
}

public enum CodecError: Error, Equatable {
    case tooLarge
    case badLength
    case badJson
}

public struct ParsedEnvelope: Equatable {
    public var id: String
    public var type: String
    public var from: String
    public var ts: Int64
    public var payload: [String: String]
}

public enum EnvelopeCodec {
    public static func parse(_ data: Data) throws -> ParsedEnvelope {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw CodecError.badJson
        }
        guard let version = (object["v"] as? NSNumber)?.intValue, version == 1,
              let id = object["id"] as? String, !id.isEmpty,
              let type = object["type"] as? String, !type.isEmpty,
              let from = object["from"] as? String,
              from == "android" || from == "mac" || from == "relay" else {
            throw CodecError.badJson
        }
        guard let ts = (object["ts"] as? NSNumber)?.int64Value, ts >= 0 else {
            throw CodecError.badJson
        }
        var payload: [String: String] = [:]
        if let raw = object["payload"] as? [String: Any] {
            for (key, value) in raw {
                guard let text = value as? String else { throw CodecError.badJson }
                payload[key] = text
            }
        } else {
            throw CodecError.badJson
        }
        return ParsedEnvelope(id: id, type: type, from: from, ts: ts, payload: payload)
    }

    public static func json(type: String, from: String, payload: [String: String]) -> Data {
        let object: [String: Any] = [
            "v": 1,
            "id": UUID().uuidString,
            "type": type,
            "from": from,
            "ts": Int64(Date().timeIntervalSince1970 * 1000),
            "payload": payload,
        ]
        return (try? JSONSerialization.data(withJSONObject: object)) ?? Data()
    }
}

public struct FrameBuffer {
    public static let maxBytes = 1024 * 1024
    private var pending = Data()

    public init() {}

    public mutating func push(_ chunk: Data) throws -> [Data] {
        pending.append(chunk)
        var messages: [Data] = []
        while pending.count >= 5 {
            let start = pending.startIndex
            let type = pending[start]
            let length = (Int(pending[start + 1]) << 24)
                | (Int(pending[start + 2]) << 16)
                | (Int(pending[start + 3]) << 8)
                | Int(pending[start + 4])
            if length < 0 || length > FrameBuffer.maxBytes { throw CodecError.badLength }
            if pending.count < 5 + length { break }
            if type == FrameKind.json {
                messages.append(pending.subdata(in: start + 5..<start + 5 + length))
            }
            pending.removeSubrange(start..<start + 5 + length)
        }
        if pending.count > FrameBuffer.maxBytes + 5 { throw CodecError.tooLarge }
        return messages
    }
}

public func encodeFrame(json: Data) -> Data {
    precondition(json.count <= FrameBuffer.maxBytes)
    var out = Data(capacity: 5 + json.count)
    out.append(FrameKind.json)
    var length = UInt32(json.count).bigEndian
    out.append(Data(bytes: &length, count: 4))
    out.append(json)
    return out
}

public struct PairingGate {
    public static let maxGuesses = 5
    public static let tokenTtlMs: Int64 = 120_000
    public static let lockMs: Int64 = 60_000

    private let token: String
    private let expiresAt: Int64
    private var failures = 0
    private var lockedUntil: Int64 = 0
    private var used = false

    public init(token: String, now: Int64) {
        self.token = token
        self.expiresAt = now + PairingGate.tokenTtlMs
    }

    public static func newToken() -> String {
        var bytes = [UInt8](repeating: 0, count: 4)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let value = UInt32(bytes[0]) << 24 | UInt32(bytes[1]) << 16 | UInt32(bytes[2]) << 8 | UInt32(bytes[3])
        return String(format: "%06d", value % 1_000_000)
    }

    public mutating func check(_ guess: String, now: Int64) -> Bool {
        if now > expiresAt || now < lockedUntil || used { return false }
        if constantTimeEquals(token, guess) {
            failures = 0
            used = true
            return true
        }
        failures += 1
        if failures >= PairingGate.maxGuesses { lockedUntil = now + PairingGate.lockMs }
        return false
    }
}

public func constantTimeEquals(_ left: String, _ right: String) -> Bool {
    let a = Array(left.utf8)
    let b = Array(right.utf8)
    if a.count != b.count { return false }
    var diff: UInt8 = 0
    for index in a.indices { diff |= a[index] ^ b[index] }
    return diff == 0
}

public func nextBackoffMs(_ attempt: Int) -> Int64 {
    let n = max(0, attempt)
    let shift = min(n, 16)
    return min(30_000, Int64(500) << shift)
}

public func pairingLink(host: String, port: Int, fingerprint: String, pin: String) -> String {
    func enc(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=?#")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
    return "bridgelink://pair?v=1&host=\(enc(host))&port=\(port)&fp=\(enc(fingerprint))&pin=\(enc(pin))"
}

public func looksSensitive(_ text: String) -> Bool {
    let lower = text.lowercased()
    if lower.contains("password") || lower.contains("passcode") { return true }
    let compact = text.filter { !$0.isWhitespace }
    guard compact.count >= 6, compact.count <= 8 else { return false }
    return compact.allSatisfy { $0.isNumber }
}

