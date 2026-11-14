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
        XCTAssertEqual(envelope.payload["body"], "hello")
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
        XCTAssertFalse(gate.check("000000", now: 1))
        XCTAssertFalse(gate.check("000000", now: 2))
        XCTAssertFalse(gate.check("000000", now: 3))
        XCTAssertFalse(gate.check("000000", now: 4))
        XCTAssertFalse(gate.check("000000", now: 5))
        XCTAssertFalse(gate.check("123456", now: 6))
        var fresh = PairingGate(token: "123456", now: 0)
        XCTAssertTrue(fresh.check("123456", now: 10))
        XCTAssertFalse(fresh.check("123456", now: 11))
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
}
