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

    func testFeaturesMatchThePhoneRules() throws {
        XCTAssertEqual(Features.shared(["files", "sms", "clipboard"], ["clipboard", "files"]), ["files", "clipboard"])
        XCTAssertFalse(Features.enabled("", "sms", fallback: false))
        XCTAssertTrue(Features.enabled("sms=1,notify=0", "sms", fallback: false))
        XCTAssertFalse(Features.enabled("sms=1,notify=0", "notify", fallback: true))
        XCTAssertEqual(Features.resumeFrom(have: 4, size: 10), 4)
        XCTAssertEqual(Features.resumeFrom(have: 11, size: 10), -1)
        XCTAssertTrue(Features.isEcho(Features.echoKey("same"), "same"))
        XCTAssertEqual(Features.otp("HDFC Bank: 482193 is the OTP for UPI payment of Rs.500"), "482193")
        XCTAssertEqual(Features.otp("G-998877 is your Google verification code"), "998877")
        XCTAssertEqual(Features.otp("OTP 4821 for A/c XX3291"), "4821")
        XCTAssertNil(Features.otp("Debited Rs.5000 on 12/11/2026"))
        XCTAssertNil(Features.otp("Your code is 123456789"))
        XCTAssertNil(Features.otp("barcode 112233 is on the box"))
        XCTAssertTrue(Features.sensitiveText("4111111111111111"))
        XCTAssertFalse(Features.sensitiveText("hello from the phone"))
        XCTAssertEqual(Features.mediaAction("pause"), "pause")
        XCTAssertNil(Features.mediaAction("shutdown"))
        XCTAssertGreaterThan(Features.smsParts(String(repeating: "a", count: 161)).count, 1)
        XCTAssertEqual(Features.smsParts(String(repeating: "ह", count: 71)).count, 2)
        XCTAssertEqual(Features.scanName(1_763_150_400_000), "scan-20251114-200000.pdf")
        XCTAssertEqual(Features.safeRelative("photos/cat.jpg"), "photos/cat.jpg")
        XCTAssertNil(Features.safeRelative("../etc/passwd"))
        XCTAssertEqual(Features.unseen(already: ["a"], current: ["a", "b"]), ["b"])
        let lock = Features.LeaveLock(weakRssi: -70, graceMs: 30_000)
        XCTAssertFalse(lock.observe(connected: true, rssi: -60, now: 0))
        XCTAssertFalse(lock.observe(connected: true, rssi: -80, now: 1_000))
        XCTAssertFalse(lock.observe(connected: false, rssi: -80, now: 20_000))
        XCTAssertTrue(lock.observe(connected: false, rssi: -90, now: 31_000))
        let rate = Features.RateLimit(max: 2, windowMs: 1_000)
        XCTAssertTrue(rate.allow(0))
        XCTAssertTrue(rate.allow(10))
        XCTAssertFalse(rate.allow(20))
        XCTAssertTrue(rate.allow(1_000))
        let history = Features.ClipHistory()
        XCTAssertFalse(history.add(id: "1", text: "123456"))
        XCTAssertTrue(history.add(id: "2", text: "grocery list"))
        XCTAssertEqual(history.search("groc"), ["grocery list"])
        XCTAssertEqual(Features.roomId("pairing-secret-value"), "f507aed2ed2149bbb78e8dccbe166a53")
        let key = Features.deriveKey("pairing-secret-value")
        XCTAssertEqual(key.map { String(format: "%02x", $0) }.joined(), "530b21bed696739ee321edc2b49c0dc8a0c443df9799e6ca8601f74f2fa5f959")
        var nonce = Data(count: 12)
        nonce[0] = 7
        let sealed = try Features.seal(key: key, counter: 1, nonce: nonce, plaintext: Data("hello".utf8))
        XCTAssertEqual(sealed.map { String(format: "%02x", $0) }.joined(), "0700000000000000000000000000000000000001ba56ed7d29da1f0d090e38babf509b3f45c3284483")
        XCTAssertEqual(try Features.open(key: key, sealed: sealed), Data("hello".utf8))
        var flipped = sealed
        flipped[flipped.count - 1] ^= 1
        XCTAssertThrowsError(try Features.open(key: key, sealed: flipped))
        let replay = Features.Replay()
        XCTAssertTrue(replay.accept(1))
        XCTAssertFalse(replay.accept(1))
        let header = try XCTUnwrap(Features.streamHeader(streamId: 3, offset: 100, flags: 1, length: 20))
        XCTAssertEqual(header.count, 17)
        XCTAssertEqual(Features.streamLength(header), 20)
        XCTAssertEqual(otpRows.filter { Features.otp($0.1) == $0.0 }.count, otpRows.count)
    }
}

private let otpRows: [(String?, String)] = [
    ("482193", "HDFC Bank: 482193 is the OTP for UPI payment of Rs.500"),
    ("123456", "123456 is your OTP"),
    ("998877", "G-998877 is your Google verification code"),
    ("1234", "PIN is 1234"),
    ("12345678", "Your code is 12345678"),
    (nil, "Your code is 123456789"),
    (nil, "Debited Rs.5000 on 12/11/2026"),
    (nil, "Call 9876543210 about the order"),
    (nil, "hello from the phone"),
    ("492813", "OTP: 492813. Valid for 10 min"),
    ("556677", "SBI: one time password is 556677"),
    ("111222", "Use verification code 111222 to continue"),
    ("909090", "Paytm OTP 909090. Do not share this code"),
    ("445566", "ICICI Bank OTP is 445566 for credit card txn"),
    (nil, "Your balance is Rs 25000"),
    (nil, "Meeting on 15-11-2026 at noon"),
    ("2468", "passcode 2468"),
    (nil, "Order #12 is ready"),
    ("135790", "WhatsApp code: 135790"),
    ("102938", "Your Amazon OTP is 102938"),
    (nil, "Price $19.99 only today"),
    ("657483", "Airtel: OTP 657483 to login"),
    ("2222", "ATM PIN 2222 was requested"),
    (nil, "Reference 123 is not a code"),
    ("314159", "code 314159 expires soon"),
    (nil, "The year 2026 was busy"),
    ("808080", "PhonePe OTP: 808080 for UPI"),
    (nil, "UPI ref 123456789012"),
    ("191919", "password code 191919"),
    (nil, "See you at 6"),
    ("454545", "Gpay verification 454545"),
    (nil, "Invoice 44 for milk"),
    ("787878", "one-time code 787878"),
    (nil, "Rs.123456 debited"),
    ("363636", "OTP 363636"),
    (nil, "barcode 112233 is on the box"),
    ("575757", "Your login code is 575757"),
    (nil, "Date of birth 01/02/1990"),
    ("646464", "Bank of Baroda OTP 646464"),
    ("818181", "Use 818181 as the code"),
    (nil, "Call center 1800123456"),
    ("929292", "Axis Bank: 929292 is the OTP"),
]
