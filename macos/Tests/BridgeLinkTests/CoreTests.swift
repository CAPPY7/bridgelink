import BridgeLinkCore
import XCTest

final class CoreTests: XCTestCase {
    func testGoldenEnvelope() throws {
        let url = Bundle.module.url(forResource: "text", withExtension: "json", subdirectory: "Fixtures")
            ?? Bundle.module.url(forResource: "text", withExtension: "json")
        let data = try Data(contentsOf: try XCTUnwrap(url))
        let envelope = try EnvelopeCodec.parse(data)
        XCTAssertEqual(envelope.id, "abc")
        XCTAssertEqual(envelope.type, "text")
        XCTAssertEqual(envelope.from, "android")
        XCTAssertEqual(envelope.ts, 1000)
        XCTAssertEqual(envelope.text("body"), "hello")
    }

    func testFrameRoundTrip() throws {
        let json = Data("{\"v\":1}".utf8)
        let encoded = encodeFrame(json: json)
        var buffer = FrameBuffer()
        XCTAssertEqual(try buffer.push(encoded.prefix(2)), [])
        let messages = try buffer.push(encoded.dropFirst(2))
        XCTAssertEqual(messages, [json])
    }

    func testPinLockout() {
        var gate = PairingGate(token: "123456", now: 0)
        XCTAssertFalse(gate.observe(matched: false, now: 1))
        XCTAssertFalse(gate.observe(matched: false, now: 2))
        XCTAssertFalse(gate.observe(matched: false, now: 3))
        XCTAssertFalse(gate.observe(matched: false, now: 4))
        XCTAssertFalse(gate.observe(matched: false, now: 5))
        XCTAssertFalse(gate.observe(matched: true, now: 6))
        var fresh = PairingGate(token: "123456", now: 0)
        XCTAssertTrue(fresh.observe(matched: true, now: 10))
        XCTAssertFalse(fresh.observe(matched: true, now: 11))
    }

    func testBackoff() {
        XCTAssertEqual(nextBackoffMs(0), 500)
        XCTAssertEqual(nextBackoffMs(1), 1000)
        XCTAssertEqual(nextBackoffMs(20), 30_000)
    }

    func testPairingLink() {
        let link = pairingLink(host: "192.168.1.20", port: 8787, fingerprint: "ab", pin: "123456")
        XCTAssertEqual(link, "bridgelink://pair?v=1&host=192.168.1.20&port=8787&fp=ab&pin=123456")
    }

    func testSensitiveClipboard() {
        XCTAssertTrue(looksSensitive("123456"))
        XCTAssertTrue(looksSensitive("password: hunter2"))
        XCTAssertFalse(looksSensitive("hello from the phone"))
    }

    func testProofDevicesAndPendingCap() {
        let proof = pairingProof(pin: "123456", fingerprint: "abc")
        XCTAssertFalse(proof.contains("123456"))
        XCTAssertNotEqual(proof, pairingProof(pin: "123456", fingerprint: "other"))
        var book = DeviceBook()
        let device = book.remember(name: "Pixel", token: "tokentokentoken1", now: 10)
        XCTAssertNotNil(book.match(token: "tokentokentoken1"))
        XCTAssertNil(book.match(token: "other-token-value"))
        XCTAssertEqual(device.name, "Pixel")
        var gate = PendingGate()
        XCTAssertFalse(gate.admit(ip: "1.1.1.1", openCount: 4, now: 0))
        XCTAssertTrue(gate.admit(ip: "1.1.1.1", openCount: 1, now: 0))
        gate.fail(ip: "9.9.9.9", now: 0)
        gate.fail(ip: "9.9.9.9", now: 1)
        gate.fail(ip: "9.9.9.9", now: 2)
        XCTAssertFalse(gate.admit(ip: "9.9.9.9", openCount: 0, now: 3))
        XCTAssertFalse(preferredLanAddress(interface: "utun0", address: "10.0.0.1"))
        XCTAssertFalse(preferredLanAddress(interface: "en0", address: "169.254.1.1"))
        XCTAssertTrue(preferredLanAddress(interface: "en0", address: "192.168.1.20"))
    }

    func testIdentityFingerprintStaysAndKeychainDoesNotGrow() throws {
        let savedService = MacIdentity.service
        let savedLabel = MacIdentity.label
        MacIdentity.service = "app.bridgelink.test.\(UUID().uuidString)"
        MacIdentity.label = "BridgeLinkTest-\(UUID().uuidString)"
        defer {
            MacIdentity.removeStored()
            MacIdentity.service = savedService
            MacIdentity.label = savedLabel
        }
        let first = try MacIdentity.load()
        let afterFirst = MacIdentity.identityCount()
        let second = try MacIdentity.load()
        XCTAssertFalse(first.fingerprint.isEmpty)
        XCTAssertEqual(first.fingerprint, second.fingerprint)
        XCTAssertEqual(MacIdentity.identityCount(), afterFirst)
        XCTAssertLessThanOrEqual(afterFirst, 1)
    }
}
