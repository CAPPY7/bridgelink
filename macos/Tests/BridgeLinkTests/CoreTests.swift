import BridgeLinkCore
import Darwin
import XCTest

final class CoreTests: XCTestCase {
    func testPointerAlertShowsOnce() {
        XCTAssertTrue(PointerGate.shouldAlert(false, false))
        XCTAssertFalse(PointerGate.shouldAlert(true, false))
        XCTAssertFalse(PointerGate.shouldAlert(false, true))
    }

    func testPictureLaneSendsBytesToTheDecoderNotTheUi() {
        let items = [
            Incoming(json: Data([1]), binary: nil),
            Incoming(json: nil, binary: Data([2, 3])),
        ]
        let lanes = PictureLane.route(items)
        XCTAssertEqual(lanes.json, [Data([1])])
        XCTAssertEqual(lanes.pictures, [Data([2, 3])])
    }

    func testMediaWatchPollsOnlyWhileTheRemotePageIsOpen() {
        XCTAssertFalse(MediaWatch.shouldPoll(false, true, false))
        XCTAssertFalse(MediaWatch.shouldPoll(true, false, false))
        XCTAssertFalse(MediaWatch.shouldPoll(true, true, true))
        XCTAssertTrue(MediaWatch.shouldPoll(true, true, false))
        XCTAssertEqual(MediaWatch.command("watch")?.action, "watch")
        XCTAssertNil(MediaWatch.command("play"))
    }

    func testSendWindowStopsTheSeventhChunk() {
        let window = SendWindow()
        for _ in 0..<SendWindow.maxInFlight {
            XCTAssertTrue(window.tryAcquire())
        }
        XCTAssertEqual(window.inFlight(), SendWindow.maxInFlight)
        XCTAssertFalse(window.tryAcquire())
        window.release()
        XCTAssertTrue(window.tryAcquire())
        XCTAssertEqual(window.inFlight(), SendWindow.maxInFlight)
    }

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
        let many = pairingLink(host: "10.0.0.2", port: 8787, fingerprint: "ab", pin: "123456", hosts: ["10.0.0.8", "10.0.0.2"])
        XCTAssertTrue(many.contains("host=10.0.0.2"))
        XCTAssertTrue(many.contains("hosts=10.0.0.2,10.0.0.8"))
        XCTAssertEqual(Features.addressOrder(primary: "10.0.0.2", extra: "10.0.0.8,10.0.0.2"), ["10.0.0.2", "10.0.0.8"])
        XCTAssertEqual(Features.rememberLine(existing: ["a"], line: "b", cap: 2), ["b", "a"])
        let report = Features.diagnostics(version: "0.8.0", os: "macOS", paired: true, hostSaved: true, port: 8787, status: "Paired")
        XCTAssertTrue(report.contains("secrets=none"))
        XCTAssertFalse(report.contains("token"))
        let folder = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("bridgelink-history-test.json")
        try? FileManager.default.removeItem(at: folder)
        XCTAssertEqual(HistoryFile.append(line: "one", url: folder), ["one"])
        XCTAssertEqual(HistoryFile.load(from: folder), ["one"])
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
        XCTAssertNotNil(IncomingFile(id: "cap", name: "big.bin", size: Transfers.maxBytes))
    }

    func testStageThreeRules() {
        XCTAssertEqual(Transfers.maxBytes, 4 * 1024 * 1024 * 1024)
        XCTAssertEqual(StageRules.progressPercent(written: 5, size: 10), 50)
        XCTAssertEqual(StageRules.progressPercent(written: 10, size: 10), 100)
        XCTAssertTrue(StageRules.fullBattery(level: 100, alreadyAlerted: false))
        XCTAssertFalse(StageRules.fullBattery(level: 99, alreadyAlerted: false))
        XCTAssertTrue(StageRules.appBlocked("com.a, com.b", "com.b"))
        XCTAssertFalse(StageRules.openLinkNow(screenOn: false, alwaysAllow: false))
        XCTAssertTrue(StageRules.openLinkNow(screenOn: false, alwaysAllow: true))
        XCTAssertEqual(StageRules.clipKind(hasText: true, hasImage: false, sensitive: true), "skip")
        XCTAssertEqual(StageRules.clipKind(hasText: false, hasImage: true, sensitive: false), "image")
        XCTAssertFalse(StageRules.imageFits(StageRules.imageCap + 1))
        XCTAssertEqual(StageRules.bundleName("Photos"), "Photos.zip")
        XCTAssertTrue(StageRules.findUsesAlarmStream())
        XCTAssertEqual(StageRules.setFlag("notify=1,clipboard=0", "clipboard", true), "notify=1,clipboard=1")
        var book = DeviceBook()
        let device = book.remember(name: "Pixel", token: "token-1", now: 10)
        book.rename(id: device.id, name: "Kitchen phone")
        XCTAssertEqual(book.devices.first?.name, "Kitchen phone")
    }

    func testStageFourRules() {
        XCTAssertEqual(StageFour.clipId("ab", 5), "5-c21")
        XCTAssertEqual(StageFour.clampVolume(140), 100)
        XCTAssertEqual(StageFour.threshold(70), -70)
        XCTAssertEqual(StageFour.threshold(10), -40)
        XCTAssertFalse(StageFour.lockRequest("unlock"))
        XCTAssertTrue(StageFour.lockRequest("away"))
        XCTAssertFalse(StageFour.shouldLock(enabled: true, rssi: -40, heard: true, threshold: -70, awayMs: 60_000, delayMs: 30_000, charging: false, atDesk: false, alreadyLocked: false))
        XCTAssertFalse(StageFour.shouldLock(enabled: true, rssi: 0, heard: true, threshold: -70, awayMs: 30_000, delayMs: 30_000, charging: true, atDesk: false, alreadyLocked: false))
        XCTAssertTrue(StageFour.shouldLock(enabled: true, rssi: 0, heard: true, threshold: -70, awayMs: 30_000, delayMs: 30_000, charging: false, atDesk: false, alreadyLocked: false))
        XCTAssertFalse(StageFour.shouldLock(enabled: true, rssi: -90, heard: true, threshold: -70, awayMs: 30_000, delayMs: 30_000, charging: false, atDesk: false, alreadyLocked: true))
        XCTAssertEqual(StageFour.awayFor(previous: 5_000, rssi: -40, threshold: -70, elapsed: 1_000, heard: true), 0)
        XCTAssertEqual(StageFour.awayFor(previous: 5_000, rssi: -80, threshold: -70, elapsed: 1_000, heard: true), 6_000)
        XCTAssertNil(StageFour.splitCommand("shutdown"))
        XCTAssertEqual(StageFour.splitCommand("pause")?.action, "pause")
        XCTAssertEqual(StageFour.splitCommand("volume:140")?.volume, "100")
        XCTAssertEqual(StageFour.formatSetup(on: true, far: 70, delay: 30, desk: false), "1,70,30,0")
        XCTAssertEqual(StageFour.parseSetup("1,200,1,2")?.far, 95)
        XCTAssertEqual(StageFour.nowLine("Sunny", "Ada", true), "Playing Sunny - Ada")
        XCTAssertEqual(StageFour.nowLine(" ", "", false), "Nothing is playing")
        XCTAssertEqual(StageFour.fileTitle("Saved scan.pdf in Downloads/BridgeLink"), "scan.pdf")
        var clips = StageFour.add([], "hello", false, 5)
        clips = StageFour.add(clips, "password: x", true, 6)
        clips = StageFour.add(clips, "123456", false, 7)
        XCTAssertEqual(clips.count, 1)
        clips = StageFour.add(clips, "line one\nline two", false, 9)
        XCTAssertEqual(StageFour.decodeClips(StageFour.encodeClips(clips)).first?.text, "line one\nline two")
        let pinned = clips[1].id
        clips = StageFour.pin(clips, pinned, true)
        for index in 0..<120 {
            clips = StageFour.add(clips, "item \(index)", false, Int64(20 + index))
        }
        XCTAssertEqual(clips.count, StageFour.clipCap)
        XCTAssertTrue(clips.contains { $0.id == pinned && $0.pinned })
        clips = StageFour.delete(clips, pinned)
        XCTAssertFalse(clips.contains { $0.id == pinned })
        var inbox = StageFour.addInbox([], "notify", "Mail", "hi", false, 1)
        inbox = StageFour.addInbox(inbox, "file", "a.pdf", "Saved", false, 2)
        inbox = StageFour.addInbox(inbox, "sms", "Ada", "password: secret", true, 3)
        inbox = StageFour.addInbox(inbox, "notify", "Bank", "482193", false, 4)
        XCTAssertEqual(inbox[0].detail, "Hidden")
        XCTAssertEqual(inbox[1].detail, "Hidden")
        XCTAssertEqual(StageFour.filter(inbox, "file", "").count, 1)
        XCTAssertEqual(StageFour.filter(inbox, "all", "pdf").count, 1)
        XCTAssertEqual(StageFour.decodeInbox(StageFour.encodeInbox(inbox))[2].title, "a.pdf")
    }

    func testFeaturesMatchThePhoneRules() throws {
        XCTAssertEqual(Features.shared(["files", "sms", "clipboard"], ["clipboard", "files"]), ["files", "clipboard"])
        XCTAssertFalse(Features.enabled("", "sms", fallback: false))
        XCTAssertTrue(Features.enabled("sms=1,notify=0", "sms", fallback: false))
        XCTAssertFalse(Features.enabled("sms=1,notify=0", "notify", fallback: true))
        XCTAssertEqual(Features.resumeFrom(have: 4, size: 10), 4)
        XCTAssertEqual(Features.resumeFrom(have: 11, size: 10), -1)
        XCTAssertTrue(Features.isEcho(Features.echoKey("same"), "same"))
        XCTAssertEqual(Features.otp("HDFC Bank: 482193 is the OTP for UPI payment of Rs.500"), "482193" as String?)
        XCTAssertEqual(Features.otp("G-998877 is your Google verification code"), "998877" as String?)
        XCTAssertEqual(Features.otp("OTP 4821 for A/c XX3291"), "4821" as String?)
        XCTAssertNil(Features.otp("Debited Rs.5000 on 12/11/2026"))
        XCTAssertNil(Features.otp("Your code is 123456789"))
        XCTAssertNil(Features.otp("barcode 112233 is on the box"))
        XCTAssertTrue(Features.sensitiveText("4111111111111111"))
        XCTAssertFalse(Features.sensitiveText("hello from the phone"))
        XCTAssertEqual(Features.mediaAction("pause"), "pause" as String?)
        XCTAssertNil(Features.mediaAction("shutdown"))
        XCTAssertGreaterThan(Features.smsParts(String(repeating: "a", count: 161)).count, 1)
        XCTAssertEqual(Features.smsParts(String(repeating: "ह", count: 71)).count, 2)
        XCTAssertEqual(Features.scanName(1_763_150_400_000), "scan-20251114-200000.pdf")
        XCTAssertTrue(Features.autoOpenScan("scan-20251114-200000.pdf", true))
        XCTAssertTrue(Features.autoOpenScan("scan-20251114-200000.jpg", true))
        XCTAssertFalse(Features.autoOpenScan("scan-20251114-200000.pdf", false))
        XCTAssertFalse(Features.autoOpenScan("photo.jpg", true))
        XCTAssertFalse(Features.autoOpenScan("run.command", true))
        XCTAssertEqual(Features.safeRelative("photos/cat.jpg"), "photos/cat.jpg" as String?)
        XCTAssertNil(Features.safeRelative("../etc/passwd"))
        XCTAssertEqual(Features.unseen(already: ["a"], current: ["a", "b"]), ["b"])
        let hidden = Features.notificationShown(app: "Mail", title: "Salary", body: "secret body", unlocked: false, showText: true)
        XCTAssertEqual(hidden.title, "Mail")
        XCTAssertEqual(hidden.body, "Notification")
        XCTAssertFalse(hidden.body.contains("secret"))
        let shown = Features.notificationShown(app: "Mail", title: "Salary", body: "secret body", unlocked: true, showText: true)
        XCTAssertEqual(shown.body, "secret body")
        let opted = Features.notificationShown(app: "Mail", title: "Salary", body: "secret body", unlocked: true, showText: false)
        XCTAssertEqual(opted.body, "Notification")
        XCTAssertEqual(LinkKind.from(code: "bad_token"), .tokenRejected)
        XCTAssertTrue(LinkKind.tokenRejected.fatal)
        XCTAssertEqual(LinkKind.fromErrno(ECONNREFUSED), .refused)
        XCTAssertEqual(LinkKind.fromErrno(EPERM), .firewall)
        XCTAssertTrue(LinkKind.firewall.message.contains("Firewall"))
        XCTAssertFalse(LinkKind.refused.fatal)
        XCTAssertFalse(LinkKind.timeout.message.contains("ETIMEDOUT"))
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
        let header = try XCTUnwrap(Features.streamHeader(streamId: 3, offset: 100, flags: 1, length: 20))
        XCTAssertEqual(header.count, 17)
        XCTAssertEqual(Features.streamLength(header), 20)
        XCTAssertEqual(otpRows.filter { Features.otp($0.1) == $0.0 }.count, otpRows.count)
    }

    func testStageFiveRules() {
        XCTAssertEqual(StageFive.simSlot(-1, 2), 0)
        XCTAssertEqual(StageFive.simSlot(5, 2), 1)
        XCTAssertTrue(StageFive.addressOk("+1 415 555 0100"))
        XCTAssertFalse(StageFive.addressOk("hello"))
        XCTAssertNil(StageFive.pointer("shutdown", 1, 1))
        XCTAssertEqual(StageFive.pointer("move", 10, -3)?.x, 10)
        XCTAssertEqual(StageFive.pointer("move", 10, -3)?.y, -3)
        XCTAssertEqual(StageFive.pointer("laser", 5, 2)?.x, 1)
        XCTAssertEqual(StageFive.slideKey("next"), "right")
        XCTAssertEqual(StageFive.slideKey("prev"), "left")
        XCTAssertEqual(StageFive.keyName("Return"), "return")
        XCTAssertNil(StageFive.keyName("a\nb"))
        XCTAssertEqual(StageFive.keyName(String(repeating: "a", count: 50))?.count, 40)
        let sms = StageFive.Sms(id: "1", address: "+1555", body: "hello\nthere", incoming: true, sim: 0, at: 5)
        XCTAssertEqual(StageFive.decodeSms(StageFive.encodeSms([sms])).first?.body, "hello\nthere")
        XCTAssertEqual(StageFive.decide(paused: false, localExists: false, localSha: "", remoteDeleted: false, remoteSha: "abc"), .take)
        XCTAssertEqual(StageFive.decide(paused: false, localExists: true, localSha: "old", remoteDeleted: false, remoteSha: "new"), .conflict)
        XCTAssertEqual(StageFive.decide(paused: false, localExists: true, localSha: "abc", remoteDeleted: true, remoteSha: "abc"), .delete)
        XCTAssertEqual(StageFive.decide(paused: false, localExists: true, localSha: "changed", remoteDeleted: true, remoteSha: "abc"), .keep)
        XCTAssertEqual(StageFive.decide(paused: true, localExists: false, localSha: "", remoteDeleted: false, remoteSha: "abc"), .skip)
        XCTAssertEqual(StageFive.conflictName("notes/a.txt"), "notes/a (BridgeLink conflict).txt")
        XCTAssertFalse(StageFive.partOk("../x", 0, 1, 1))
        XCTAssertTrue(StageFive.partOk("a.txt", 0, 4, 4))
        let rules = StageFive.addRule([], "connect", "alert", "Hi")
        XCTAssertEqual(StageFive.fire(rules, "connect", "").first?.thenAction, "alert")
        XCTAssertTrue(StageFive.fire(StageFive.toggle(rules, rules[0].id, false), "connect", "").isEmpty)
        var many: [StageFive.Rule] = []
        for index in 0..<10 { many = StageFive.addRule(many, "connect", "note", "n\(index)") }
        XCTAssertEqual(many.count, 8)
        XCTAssertTrue(StageFive.addRule([], "explode", "alert", "").isEmpty)
        XCTAssertTrue(StageFive.batteryLow(19, false))
        XCTAssertFalse(StageFive.batteryLow(19, true))
        XCTAssertFalse(StageFive.batteryLow(20, false))
        XCTAssertTrue(StageFive.homeWifi("Home", ""))
        XCTAssertTrue(StageFive.homeWifi("Home", "home"))
        XCTAssertFalse(StageFive.homeWifi("<unknown ssid>", ""))
        XCTAssertEqual(StageFive.appName("Notes.app"), "Notes")
        XCTAssertNil(StageFive.appName("../Apps"))
        XCTAssertTrue(StageFive.smsSteps.contains("Allow restricted settings"))
        XCTAssertTrue(StageFive.smsSteps.contains("SMS"))
        XCTAssertTrue(StageFive.pointerSteps.contains("Accessibility"))
        let kept = StageFive.Entry(path: "a.txt", size: 1, mtime: 1, sha: "aa", deleted: false)
        let gone = StageFive.Entry(path: "b.txt", size: 1, mtime: 1, sha: "bb", deleted: false)
        XCTAssertEqual(StageFive.removed([kept, gone], [kept]).first?.path, "b.txt")
    }

    func testStageSixRules() throws {
        XCTAssertEqual(StageSix.quality("medium").width, 1280)
        XCTAssertEqual(StageSix.quality("medium").height, 720)
        XCTAssertEqual(StageSix.quality("medium").fps, 30)
        XCTAssertEqual(StageSix.quality("medium").bitrate, 2_500_000)
        XCTAssertEqual(StageSix.quality("low").fps, 15)
        XCTAssertEqual(StageSix.quality("high").width, 1920)
        XCTAssertEqual(StageSix.quality("nope").width, 1280)
        XCTAssertEqual(StageSix.qualityName("nope"), "medium")
        XCTAssertEqual(StageSix.qualityName("high"), "high")
        var blob = Data(count: 50_000)
        for i in 0..<blob.count { blob[i] = UInt8(i & 0xff) }
        let parts = StageSix.chunks(blob)
        XCTAssertEqual(parts.count, 3)
        XCTAssertEqual(parts[0].count, 24 * 1024)
        XCTAssertEqual(StageSix.join(parts), blob)
        XCTAssertNil(StageSix.picturePoint(viewW: 0, viewH: 100, picW: 100, picH: 100, px: 1, py: 1))
        XCTAssertNil(StageSix.picturePoint(viewW: 200, viewH: 100, picW: 100, picH: 100, px: 10, py: 50))
        let origin = try XCTUnwrap(StageSix.picturePoint(viewW: 200, viewH: 100, picW: 100, picH: 100, px: 50, py: 0))
        XCTAssertEqual(origin.0, 0)
        XCTAssertEqual(origin.1, 0)
        let mid = try XCTUnwrap(StageSix.picturePoint(viewW: 200, viewH: 100, picW: 100, picH: 100, px: 100, py: 50))
        XCTAssertEqual(mid.0, 5000)
        XCTAssertEqual(mid.1, 5000)
        XCTAssertNil(StageSix.control("shutdown", 1, 1, 1, 1, ""))
        XCTAssertNil(StageSix.control("text", 1, 1, 1, 1, ""))
        let typed = try XCTUnwrap(StageSix.control("text", -5, 20, 0, 0, String(repeating: "a", count: 90)))
        XCTAssertEqual(typed.text.count, 80)
        XCTAssertEqual(typed.x, 0)
        XCTAssertEqual(StageSix.control("back", 1, 1, 2, 2, "")?.kind, "back")
        XCTAssertEqual(StageSix.phonePixel(phoneW: 1080, phoneH: 2400, x: 10000, y: 10000).0, 1079)
        XCTAssertEqual(StageSix.phonePixel(phoneW: 1080, phoneH: 2400, x: 10000, y: 10000).1, 2399)
        XCTAssertFalse(StageSix.flashOn("front", true))
        XCTAssertTrue(StageSix.flashOn("back", true))
        XCTAssertEqual(StageSix.zoom(0), 1)
        XCTAssertEqual(StageSix.zoom(99), 8)
        XCTAssertEqual(StageSix.facing("front"), "front")
        XCTAssertEqual(StageSix.facing("side"), "back")
        XCTAssertEqual(StageSix.jpegQuality("low"), 40)
        XCTAssertFalse(StageSix.relayUrl("ws://relay.example.com"))
        XCTAssertFalse(StageSix.relayUrl("wss://user@relay.example.com/x"))
        XCTAssertFalse(StageSix.relayUrl("wss://localhost/room"))
        XCTAssertFalse(StageSix.relayUrl("wss://127.0.0.1/room"))
        XCTAssertTrue(StageSix.relayUrl("wss://relay.example.com"))
        XCTAssertTrue(StageSix.relayUrl("wss://relay.example.com:8443/bridge"))
        let whereTo = try XCTUnwrap(StageSix.relayParts("wss://relay.example.com:8443/bridge"))
        XCTAssertEqual(whereTo.host, "relay.example.com")
        XCTAssertEqual(whereTo.port, "8443")
        XCTAssertEqual(whereTo.path, "/bridge")
        let secret = "0123456789abcdefRELAY"
        let ticket = StageSix.ticket(secret, "phone")
        XCTAssertEqual(ticket.count, 32)
        XCTAssertFalse(ticket.contains("RELAY"))
        XCTAssertEqual(ticket, StageSix.ticket(secret, "other"))
        XCTAssertNotEqual(ticket, StageSix.ticket(secret, "mac"))
        XCTAssertEqual(StageSix.ticket("short", "phone"), "")
        XCTAssertEqual(StageSix.room(secret), Features.roomId(secret))
        let key = Features.deriveKey(secret)
        var nonce = Data(count: 12)
        nonce[0] = 9
        let plain = Data("hello-frame-unique".utf8)
        let sealed = try Features.seal(key: key, counter: 4, nonce: nonce, plaintext: plain)
        XCTAssertTrue(StageSix.sealedLooksOpaque(plain: plain, sealed: sealed))
        XCTAssertFalse(StageSix.sealedLooksOpaque(plain: plain, sealed: plain))
        XCTAssertFalse(StageSix.cameraExtension(false))
        XCTAssertTrue(StageSix.cameraExtension(true))
        XCTAssertTrue(StageSix.stopWhenClosed(false))
        XCTAssertFalse(StageSix.stopWhenClosed(true))
        XCTAssertTrue(StageSix.controlSteps.contains("Accessibility"))
        XCTAssertTrue(StageSix.controlSteps.contains("restricted"))
        XCTAssertTrue(StageSix.camNote.contains("Zoom"))
        XCTAssertTrue(StageSix.mirrorSteps.contains("Closing"))
        let nal = Data([0, 0, 0, 1, 0x65, 9, 9])
        XCTAssertEqual(StageSix.nalType(nal), 5)
        let sets = Data([0, 0, 0, 1, 0x67, 1, 0, 0, 0, 1, 0x68, 2])
        let found = try XCTUnwrap(StageSix.parameterSets(sets))
        XCTAssertEqual(found.0.first, 0x67)
        XCTAssertEqual(found.1.first, 0x68)
        let packed = try XCTUnwrap(StageSix.pack(stream: 1, flags: 1, part: 0, parts: 1, data: Data([7, 8])))
        let piece = try XCTUnwrap(StageSix.unpack(packed))
        XCTAssertEqual(piece.stream, 1)
        XCTAssertEqual(piece.data.first, 7)
        XCTAssertNil(StageSix.pack(stream: 9, flags: 0, part: 0, parts: 1, data: Data([1])))
        let hi = Data("hi".utf8)
        let mask: [UInt8] = [1, 2, 3, 4]
        XCTAssertEqual(StageSix.wsPayload(try XCTUnwrap(StageSix.wsFrame(opcode: 2, payload: hi, mask: mask))), hi)
        XCTAssertEqual(StageSix.wsPayload(try XCTUnwrap(StageSix.wsFrame(opcode: 1, payload: hi, mask: nil))), hi)
        let big = Data(repeating: 5, count: 200)
        XCTAssertEqual(StageSix.wsPayload(try XCTUnwrap(StageSix.wsFrame(opcode: 2, payload: big, mask: mask))), big)
        XCTAssertEqual(StageSix.wsSize(Data([0x82, 0x02]), 1), -1)
        var bag: [Data] = []
        XCTAssertNil(StageSix.assemble(&bag, part: 0, parts: 2, data: Data([1])))
        XCTAssertEqual(bag.count, 1)
        XCTAssertEqual(StageSix.assemble(&bag, part: 1, parts: 2, data: Data([2])), Data([1, 2]))
        XCTAssertEqual(bag.count, 0)
        let avcc = try XCTUnwrap(StageSix.toAvcc(nal))
        XCTAssertEqual(avcc[avcc.startIndex], 0)
        XCTAssertEqual(avcc[avcc.index(avcc.startIndex, offsetBy: 3)], 3)
    }

    func testReleaseCompare() {
        XCTAssertEqual(ReleaseCheck.compare("0.13.0", "1.0.0"), 1)
        XCTAssertEqual(ReleaseCheck.compare("1.0.0", "1.0.0"), 0)
        XCTAssertEqual(ReleaseCheck.compare("1.1.0", "1.0.0"), -1)
        XCTAssertEqual(ReleaseCheck.compare("1.0", "1.0.0"), 0)
        XCTAssertEqual(ReleaseCheck.shortVersion("<item sparkle:shortVersionString=\"1.0.0\" sparkle:version=\"13\">"), "1.0.0")
        XCTAssertEqual(ReleaseCheck.shortVersion("nope"), "")
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
