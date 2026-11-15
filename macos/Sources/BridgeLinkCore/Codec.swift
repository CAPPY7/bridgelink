import CryptoKit
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

public struct ParsedEnvelope {
    public var id: String
    public var type: String
    public var from: String
    public var ts: Int64
    public var payload: [String: Any]

    public func text(_ key: String) -> String {
        let value = payload[key]
        if let text = value as? String { return text }
        if let number = value as? NSNumber { return number.stringValue }
        return ""
    }
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
        guard let payload = object["payload"] as? [String: Any] else { throw CodecError.badJson }
        return ParsedEnvelope(id: id, type: type, from: from, ts: ts, payload: payload)
    }

    public static func json(type: String, from: String, payload: [String: Any]) -> Data {
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

public struct Incoming {
    public let json: Data?
    public let binary: Data?
}

public struct FrameBuffer {
    public static let maxBytes = 1024 * 1024
    private var pending = Data()

    public init() {}

    public mutating func push(_ chunk: Data) throws -> [Data] {
        try pushAll(chunk).compactMap { $0.json }
    }

    public mutating func pushAll(_ chunk: Data) throws -> [Incoming] {
        pending.append(chunk)
        var messages: [Incoming] = []
        while pending.count >= 5 {
            let start = pending.startIndex
            let type = pending[start]
            let length = (Int(pending[start + 1]) << 24)
                | (Int(pending[start + 2]) << 16)
                | (Int(pending[start + 3]) << 8)
                | Int(pending[start + 4])
            if length < 0 || length > FrameBuffer.maxBytes { throw CodecError.badLength }
            if pending.count < 5 + length { break }
            let payload = pending.subdata(in: start + 5..<start + 5 + length)
            if type == FrameKind.json {
                messages.append(Incoming(json: payload, binary: nil))
            } else if type == FrameKind.binary {
                messages.append(Incoming(json: nil, binary: payload))
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

    public mutating func observe(matched: Bool, now: Int64) -> Bool {
        if now > expiresAt || now < lockedUntil || used { return false }
        if matched {
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

public func pairingLink(host: String, port: Int, fingerprint: String, pin: String, hosts: [String] = []) -> String {
    func enc(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=?#")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
    let ordered = Features.addressOrder(primary: host, extra: hosts.joined(separator: ","))
    let first = ordered.first ?? host
    var text = "bridgelink://pair?v=1&host=\(enc(first))&port=\(port)&fp=\(enc(fingerprint))&pin=\(enc(pin))"
    if ordered.count > 1 {
        text += "&hosts=\(enc(ordered.joined(separator: ",")))"
    }
    return text
}

public func pairingProof(pin: String, fingerprint: String) -> String {
    let key = SymmetricKey(data: Data(pin.utf8))
    let mac = HMAC<SHA256>.authenticationCode(for: Data(fingerprint.utf8), using: key)
    return Data(mac).map { String(format: "%02x", $0) }.joined()
}

public func sha256Hex(_ text: String) -> String {
    SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
}

public struct PairedDevice: Codable, Equatable {
    public var id: String
    public var name: String
    public var tokenHash: String
    public var pairedAt: Int64
    public var lastSeen: Int64
}

public struct DeviceBook {
    public private(set) var devices: [PairedDevice] = []

    public init() {}

    public init(devices: [PairedDevice]) {
        self.devices = devices
    }

    public mutating func remember(name: String, token: String, now: Int64) -> PairedDevice {
        let device = PairedDevice(id: UUID().uuidString, name: name, tokenHash: sha256Hex(token), pairedAt: now, lastSeen: now)
        devices.append(device)
        return device
    }

    public mutating func touch(id: String, now: Int64) {
        guard let index = devices.firstIndex(where: { $0.id == id }) else { return }
        devices[index].lastSeen = now
    }

    public func match(token: String) -> PairedDevice? {
        let hash = sha256Hex(token)
        return devices.first { constantTimeEquals($0.tokenHash, hash) }
    }

    public mutating func forget(id: String) {
        devices.removeAll { $0.id == id }
    }

    public mutating func rename(id: String, name: String) {
        guard let index = devices.firstIndex(where: { $0.id == id }) else { return }
        devices[index].name = StageRules.tidyName(name, "Phone")
    }
}

public struct PendingGate {
    public static let maxPending = 4
    public static let failLimit = 3
    public static let blockMs: Int64 = 60_000
    private var blockedUntil: [String: Int64] = [:]
    private var failures: [String: Int] = [:]

    public init() {}

    public mutating func admit(ip: String, openCount: Int, now: Int64) -> Bool {
        if openCount >= PendingGate.maxPending { return false }
        if let until = blockedUntil[ip], now < until { return false }
        return true
    }

    public mutating func fail(ip: String, now: Int64) {
        let count = (failures[ip] ?? 0) + 1
        failures[ip] = count
        if count >= PendingGate.failLimit {
            blockedUntil[ip] = now + PendingGate.blockMs
            failures[ip] = 0
        }
    }
}

public func preferredLanAddress(interface name: String, address: String) -> Bool {
    let blocked = ["utun", "bridge", "vmnet", "docker", "awdl", "llw"]
    if blocked.contains(where: { name.hasPrefix($0) }) { return false }
    if address.hasPrefix("169.254.") || address == "127.0.0.1" { return false }
    if address.contains(":") { return false }
    return true
}

public func looksSensitive(_ text: String) -> Bool {
    let lower = text.lowercased()
    if lower.contains("password") || lower.contains("passcode") { return true }
    let compact = text.filter { !$0.isWhitespace }
    guard compact.count >= 6, compact.count <= 8 else { return false }
    return compact.allSatisfy { $0.isNumber }
}

public func sasCommit(_ nonce: String) -> String {
    sha256Hex(nonce)
}

public func sasRevealMatches(commit: String, nonce: String) -> Bool {
    constantTimeEquals(sasCommit(nonce), commit)
}

public func sasCode(macFingerprint: String, phoneNonce: String, macNonce: String) -> String {
    let message = Data((macFingerprint + "\n" + phoneNonce + "\n" + macNonce).utf8)
    let key = SymmetricKey(data: Data("bridgelink-sas-v1".utf8))
    let mac = HMAC<SHA256>.authenticationCode(for: message, using: key)
    let bytes = Array(mac)
    let value = (UInt32(bytes[0]) << 24) | (UInt32(bytes[1]) << 16) | (UInt32(bytes[2]) << 8) | UInt32(bytes[3])
    return String(format: "%06d", value % 1_000_000)
}

public enum SasDecision: Equatable {
    case waitForMac
    case accept
    case reject
}

/// A token is issued only after this Mac was confirmed and the phone's code
/// matches the code computed from this Mac's own certificate.
public func sasDecision(macConfirmed: Bool, phoneCode: String?, expected: String) -> SasDecision {
    guard macConfirmed, let phoneCode, !phoneCode.isEmpty else { return .waitForMac }
    if constantTimeEquals(phoneCode, expected) { return .accept }
    return .reject
}


