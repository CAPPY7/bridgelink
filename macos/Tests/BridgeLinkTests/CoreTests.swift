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

    func testRelayCannotReuseAMatchCode() {
        XCTAssertEqual(sasCode(macFingerprint: "ab", phoneNonce: "phone", macNonce: "mac"), "001826")
        let phoneSees = sasCode(macFingerprint: "attacker", phoneNonce: "phone", macNonce: "mac")
        let macSees = sasCode(macFingerprint: "realmac", phoneNonce: "phone", macNonce: "mac")
        XCTAssertNotEqual(phoneSees, macSees)
        XCTAssertEqual(sasDecision(macConfirmed: true, phoneCode: phoneSees, expected: macSees), .reject)
        XCTAssertEqual(sasDecision(macConfirmed: false, phoneCode: macSees, expected: macSees), .waitForMac)
        XCTAssertEqual(sasDecision(macConfirmed: true, phoneCode: macSees, expected: macSees), .accept)
        XCTAssertEqual(sasDecision(macConfirmed: true, phoneCode: nil, expected: macSees), .waitForMac)
        let commit = sasCommit("mac-nonce-fixed")
        XCTAssertTrue(sasRevealMatches(commit: commit, nonce: "mac-nonce-fixed"))
        XCTAssertFalse(sasRevealMatches(commit: commit, nonce: "guess-0"))
        let target = sasCode(macFingerprint: "realmac", phoneNonce: "phone-nonce-fixed", macNonce: "mac-nonce-fixed")
        var forced = false
        for index in 0..<2000 {
            let guess = "guess-\(index)"
            if sasCode(macFingerprint: "attacker", phoneNonce: "phone-nonce-fixed", macNonce: guess) == target
                && sasCommit(guess) == commit {
                forced = true
            }
        }
        XCTAssertFalse(forced)
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

    func testFileNamesAndLinksRejectTricks() {
        XCTAssertEqual(Transfers.sanitizeName("../etc/passwd"), "passwd")
        XCTAssertEqual(Transfers.sanitizeName("..\\etc\\passwd"), "passwd")
        XCTAssertEqual(Transfers.sanitizeName(".."), "file")
        XCTAssertEqual(Transfers.sanitizeName("a\u{0}b.txt"), "a_b.txt")
        XCTAssertNil(Transfers.acceptUrl("javascript:alert(1)"))
        XCTAssertNil(Transfers.acceptUrl("file:///etc/passwd"))
        XCTAssertNil(Transfers.acceptUrl("intent://scan/#Intent;end"))
        XCTAssertEqual(Transfers.acceptUrl(" https://example.com/a "), "https://example.com/a")
    }

    func testIncomingFileChecksHashAndResumeOffset() {
        let empty = IncomingFile(id: "1", name: "a.txt", size: 0)
        XCTAssertEqual(empty?.matches("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), true)
        let one = IncomingFile(id: "2", name: "../b.txt", size: 1)
        XCTAssertEqual(one?.name, "b.txt")
        let raw = one?.accept(offset: 0, base64: Data([7]).base64EncodedString())
        XCTAssertEqual(raw?.count, 1)
        XCTAssertEqual(one?.matches(Transfers.sha256Hex(Data([7]))), true)
        let gap = IncomingFile(id: "3", name: "c.txt", size: 2)
        XCTAssertNotNil(gap?.accept(offset: 0, base64: Data([1]).base64EncodedString()))
        XCTAssertNil(gap?.accept(offset: 0, base64: Data([1]).base64EncodedString()))
        XCTAssertNotNil(gap?.accept(offset: 1, base64: Data([2]).base64EncodedString()))
        XCTAssertNil(IncomingFile(id: "4", name: "d.txt", size: Transfers.maxBytes + 1))
    }
}
