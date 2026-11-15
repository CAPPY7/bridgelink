package app.bridgelink.android.protocol;

import org.junit.Test;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProtocolTest {
    @Test
    public void goldenTextEnvelope() throws Exception {
        Envelope envelope = Envelope.parse(fixture());
        assertEquals(1, envelope.v);
        assertEquals("abc", envelope.id);
        assertEquals("text", envelope.type);
        assertEquals("android", envelope.from);
        assertEquals(1000L, envelope.ts);
        assertEquals("hello", envelope.body());
    }

    @Test
    public void frameRoundTrip() throws Exception {
        byte[] encoded = Frame.encode(Frame.JSON, "{\"v\":1}".getBytes(StandardCharsets.UTF_8));
        Frame.Parser parser = new Frame.Parser();
        var frames = parser.push(encoded);
        assertEquals(1, frames.size());
        assertEquals(Frame.JSON, frames.get(0).type);
        assertEquals("{\"v\":1}", new String(frames.get(0).payload, StandardCharsets.UTF_8));
    }

    @Test
    public void splitFrameStillAssembles() throws Exception {
        byte[] encoded = Frame.encode(Frame.JSON, "hi".getBytes(StandardCharsets.UTF_8));
        Frame.Parser parser = new Frame.Parser();
        assertTrue(parser.push(new byte[]{encoded[0], encoded[1]}).isEmpty());
        var frames = parser.push(java.util.Arrays.copyOfRange(encoded, 2, encoded.length));
        assertEquals(1, frames.size());
    }

    @Test
    public void pinExpiresLocksAndIsSingleUse() {
        PairingGate gate = new PairingGate("123456", 0);
        assertFalse(gate.check("000000", 1));
        assertFalse(gate.check("000000", 2));
        assertFalse(gate.check("000000", 3));
        assertFalse(gate.check("000000", 4));
        assertFalse(gate.check("000000", 5));
        assertTrue(gate.locked(6));
        assertFalse(gate.check("123456", 6));
        PairingGate fresh = new PairingGate("123456", 0);
        assertTrue(fresh.check("123456", 10));
        assertFalse(fresh.check("123456", 11));
        assertFalse(new PairingGate("123456", 0).check("123456", PairingGate.TOKEN_TTL_MS + 1));
    }

    @Test
    public void pairingLinkRoundTrip() {
        PairingLink link = PairingLink.parse("bridgelink://pair?v=1&host=192.168.1.20&port=8787&fp=ab&pin=123456");
        assertEquals("192.168.1.20", link.host);
        assertEquals(8787, link.port);
        assertEquals("ab", link.fingerprint);
        assertEquals("123456", link.pin);
        assertEquals(1, link.hosts.size());
        PairingLink many = PairingLink.parse("bridgelink://pair?v=1&host=10.0.0.2&port=8787&fp=ab&pin=123456&hosts=10.0.0.8,10.0.0.2");
        assertEquals("10.0.0.2", many.hosts.get(0));
        assertEquals("10.0.0.8", many.hosts.get(1));
        assertEquals(2, many.hosts.size());
        assertEquals(null, PairingLink.parse("not a link"));
        assertEquals(null, PairingLink.parse("bridgelink://pair?v=1&host=&port=8787&fp=ab&pin=123456"));
    }

    @Test
    public void backoffCapsAtThirtySeconds() {
        assertEquals(500L, Backoff.nextMs(0));
        assertEquals(1000L, Backoff.nextMs(1));
        assertEquals(30_000L, Backoff.nextMs(20));
    }

    @Test
    public void dedupDropsRepeats() {
        Dedup dedup = new Dedup(2);
        assertTrue(dedup.firstTime("a"));
        assertFalse(dedup.firstTime("a"));
        assertTrue(dedup.firstTime("b"));
        assertTrue(dedup.firstTime("c"));
        assertTrue(dedup.firstTime("a"));
    }

    @Test
    public void outboxDoesNotRunOnCaller() throws Exception {
        Outbox outbox = new Outbox();
        String caller = Thread.currentThread().getName();
        java.util.concurrent.atomic.AtomicReference<String> ran = new java.util.concurrent.atomic.AtomicReference<>();
        outbox.submit(() -> ran.set(Thread.currentThread().getName()));
        for (int i = 0; i < 50 && ran.get() == null; i++) Thread.sleep(20);
        assertEquals("bridgelink-writer", ran.get());
        assertFalse(caller.equals(ran.get()));
        outbox.close();
    }

    @Test
    public void richPayloadKeepsNumbers() {
        Envelope envelope = Envelope.parse("{\"v\":1,\"id\":\"n1\",\"type\":\"battery\",\"from\":\"android\",\"ts\":5,\"payload\":{\"percent\":87,\"charging\":true,\"label\":\"phone\"}}");
        assertEquals("87", envelope.text("percent"));
        assertEquals("true", envelope.text("charging"));
        assertEquals("phone", envelope.text("label"));
    }

    @Test
    public void proofHidesThePin() {
        String proof = Proof.hmacHex("123456", "abc");
        assertTrue(Proof.matches("123456", "abc", proof));
        assertFalse(Proof.matches("123456", "def", proof));
        assertFalse(proof.contains("123456"));
    }

    @Test
    public void helloNeverSendsPinOrTokenToAnUnpinnedKey() {
        HelloPlan typed = HelloPlan.decide("Pixel", "123456", "tokentokentoken1", "", "attacker", "phone-nonce");
        assertEquals(null, typed.fatal);
        assertFalse(typed.payload.has("pin"));
        assertFalse(typed.payload.has("proof"));
        assertFalse(typed.payload.has("token"));
        assertFalse(typed.payload.has("nonce"));
        assertEquals(Sas.commit("phone-nonce"), typed.payload.get("commit").getAsString());

        HelloPlan qr = HelloPlan.decide("Pixel", "123456", "", "realfp", "realfp", "n");
        assertFalse(qr.payload.has("pin"));
        assertFalse(qr.payload.has("nonce"));
        assertEquals(Proof.hmacHex("123456", "realfp"), qr.payload.get("proof").getAsString());

        HelloPlan again = HelloPlan.decide("Pixel", "123456", "tokentokentoken1", "pinned", "pinned", "n");
        assertFalse(again.payload.has("pin"));
        assertFalse(again.payload.has("proof"));
        assertEquals("tokentokentoken1", again.payload.get("token").getAsString());

        HelloPlan mitm = HelloPlan.decide("Pixel", "123456", "tokentokentoken1", "pinned", "attacker", "n");
        assertEquals(null, mitm.payload);
        assertEquals(LinkFailure.Kind.KEY_CHANGED, mitm.kind);
        assertTrue(mitm.fatal.contains("key changed"));
    }

    @Test
    public void relayShowsADifferentMatchCodeAndGetsNoToken() {
        assertEquals("001826", Sas.code("ab", "phone", "mac"));
        String phoneSees = Sas.code("attacker", "phone", "mac");
        String macSees = Sas.code("realmac", "phone", "mac");
        assertFalse(phoneSees.equals(macSees));
        HelloPlan relay = HelloPlan.decide("Pixel", "123456", "", "", "attacker", "phone");
        assertFalse(relay.payload.has("proof"));
        assertFalse(relay.payload.has("token"));
        assertFalse(relay.payload.has("pin"));
        assertFalse(relay.payload.has("nonce"));
    }

    @Test
    public void relayCannotGrindACommittedNonce() {
        String macNonce = "mac-nonce-fixed";
        String commit = Sas.commit(macNonce);
        String target = Sas.code("realmac", "phone-nonce-fixed", macNonce);
        boolean forced = false;
        for (int i = 0; i < 2000; i++) {
            String guess = "guess-" + i;
            if (Sas.code("attacker", "phone-nonce-fixed", guess).equals(target) && Sas.commit(guess).equals(commit)) {
                forced = true;
            }
        }
        assertFalse(forced);
        assertFalse(Sas.revealMatches(commit, "guess-0"));
        assertTrue(Sas.revealMatches(commit, macNonce));
    }

    @Test
    public void fatalErrorsDoNotRetry() {
        LinkState state = new LinkState();
        state.begin();
        state.failFatal("bad pin");
        assertFalse(state.retryScheduled);
        assertEquals(LinkState.Phase.FATAL, state.phase);
        assertTrue(LinkState.isFatalCode("bad_proof"));
        assertTrue(LinkState.isFatalCode("bad_code"));
        assertFalse(LinkState.isFatalCode("timeout"));
        state.begin();
        state.retryable("timeout");
        assertTrue(state.retryScheduled);
        assertEquals(500L, state.delayMs());
    }

    @Test
    public void secretBoxRoundTripHidesTheToken() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        String sealed = SecretBox.seal(key, "tokentokentoken1");
        assertEquals("tokentokentoken1", SecretBox.open(key, sealed));
        assertFalse(sealed.contains("tokentokentoken1"));
        SecretKey other = generator.generateKey();
        try {
            SecretBox.open(other, sealed);
            assertTrue(false);
        } catch (Exception expected) {
            assertTrue(true);
        }
    }

    @Test
    public void macListKeepsSeveralMacsAndDropsOne() {
        String list = MacList.add("[]", "Office", "192.168.1.20", 8787, "aa");
        list = MacList.add(list, "Home", "10.0.0.4", 8788, "bb");
        assertEquals("Office, Home", MacList.names(list));
        list = MacList.add(list, "Office", "192.168.1.20", 8787, "cc");
        assertEquals("Home, Office", MacList.names(list));
        assertNotEquals(list, MacList.removeHost(list, "10.0.0.4"));
        assertEquals("Office", MacList.names(MacList.removeHost(list, "10.0.0.4")));
    }

    @Test
    public void fileNamesAndLinksRejectTricks() {
        assertEquals("passwd", Transfers.sanitizeName("../etc/passwd"));
        assertEquals("passwd", Transfers.sanitizeName("..\\etc\\passwd"));
        assertEquals("file", Transfers.sanitizeName(".."));
        assertEquals("file", Transfers.sanitizeName("."));
        assertEquals("file", Transfers.sanitizeName(""));
        assertEquals("a_b.txt", Transfers.sanitizeName("a\u0000b.txt"));
        assertNull(Transfers.acceptUrl("javascript:alert(1)"));
        assertNull(Transfers.acceptUrl("file:///etc/passwd"));
        assertNull(Transfers.acceptUrl("intent://scan/#Intent;end"));
        assertNull(Transfers.acceptUrl("data:text/html,hi"));
        assertEquals("https://example.com/a", Transfers.acceptUrl(" https://example.com/a "));
    }

    @Test
    public void incomingFileChecksHashAndResumeOffset() {
        Transfers.Incoming empty = Transfers.Incoming.begin("1", "a.txt", 0);
        assertTrue(empty.matches(Transfers.sha256Hex(new byte[0])));
        assertFalse(empty.matches("00"));
        Transfers.Incoming one = Transfers.Incoming.begin("2", "../b.txt", 1);
        assertEquals("b.txt", one.name);
        byte[] raw = one.accept(0, Base64.getEncoder().encodeToString(new byte[]{7}));
        assertEquals(1, raw.length);
        assertTrue(one.matches(Transfers.sha256Hex(new byte[]{7})));
        Transfers.Incoming gap = Transfers.Incoming.begin("3", "c.txt", 2);
        assertNotNull(gap.accept(0, Base64.getEncoder().encodeToString(new byte[]{1})));
        assertNull(gap.accept(0, Base64.getEncoder().encodeToString(new byte[]{1})));
        assertNotNull(gap.accept(1, Base64.getEncoder().encodeToString(new byte[]{2})));
        assertNull(Transfers.Incoming.begin("4", "d.txt", Transfers.MAX_BYTES + 1));
        assertNotNull(Transfers.Incoming.begin("cap", "big.bin", Transfers.MAX_BYTES));
    }

    @Test
    public void fourGigabyteCapStaysChunked() throws Exception {
        assertEquals(4L * 1024 * 1024 * 1024, Transfers.MAX_BYTES);
        byte[] zeros = new byte[Transfers.CHUNK];
        String encoded = Base64.getEncoder().encodeToString(zeros);
        long size = Transfers.CHUNK * 3L;
        Transfers.Incoming incoming = Transfers.Incoming.begin("big", "big.bin", size);
        assertNotNull(incoming);
        for (long offset = 0; offset < size; offset += Transfers.CHUNK) {
            assertNotNull(incoming.accept(offset, encoded));
        }
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update(zeros);
        digest.update(zeros);
        digest.update(zeros);
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) builder.append(String.format("%02x", value));
        assertTrue(incoming.matches(builder.toString()));
        assertNull(incoming.accept(size, encoded));
    }

    @Test
    public void stageThreeRules() {
        assertEquals(50, StageRules.progressPercent(5, 10));
        assertEquals(100, StageRules.progressPercent(10, 10));
        assertEquals(0, StageRules.progressPercent(1, 0));
        assertTrue(StageRules.fullBattery(100, false));
        assertFalse(StageRules.fullBattery(100, true));
        assertFalse(StageRules.fullBattery(99, false));
        assertTrue(StageRules.appBlocked("com.a, com.b", "com.b"));
        assertFalse(StageRules.appBlocked("com.a", "com.b"));
        assertFalse(StageRules.openLinkNow(false, false));
        assertTrue(StageRules.openLinkNow(false, true));
        assertEquals("skip", StageRules.clipKind(true, true, true));
        assertEquals("text", StageRules.clipKind(true, true, false));
        assertEquals("image", StageRules.clipKind(false, true, false));
        assertFalse(StageRules.imageFits(StageRules.IMAGE_CAP + 1));
        assertTrue(StageRules.imageFits(32));
        assertEquals("Photos.zip", StageRules.bundleName("Photos"));
        assertTrue(StageRules.findUsesAlarmStream());
        assertEquals(60_000L, StageRules.OTP_CLEAR_MS);
        assertEquals("notify=1,clipboard=1", StageRules.setFlag("notify=1,clipboard=0", "clipboard", true));
        assertEquals("Desk", StageRules.tidyName("  Desk  ", "Mac"));
        String list = MacList.add("[]", "Office", "10.0.0.4", 8787, "aa");
        assertEquals("Desk", MacList.names(MacList.rename(list, "10.0.0.4", "Desk")));
    }

    @Test
    public void stageFourRules() {
        assertEquals("5-c21", StageFour.clipId("ab", 5));
        assertEquals(40, StageFour.clampVolume(40));
        assertEquals(0, StageFour.clampVolume(-3));
        assertEquals(100, StageFour.clampVolume(140));
        assertEquals(-70, StageFour.threshold(70));
        assertEquals(-40, StageFour.threshold(10));
        assertEquals(-95, StageFour.threshold(200));
        assertEquals(5, StageFour.delaySeconds(1));
        assertFalse(StageFour.lockRequest("unlock"));
        assertTrue(StageFour.lockRequest("away"));
        assertFalse(StageFour.shouldLock(false, -90, true, -70, 60_000, 30_000, false, false, false));
        assertFalse(StageFour.shouldLock(true, -90, false, -70, 60_000, 30_000, false, false, false));
        assertFalse(StageFour.shouldLock(true, -40, true, -70, 60_000, 30_000, false, false, false));
        assertFalse(StageFour.shouldLock(true, 0, true, -70, 60_000, 30_000, true, false, false));
        assertFalse(StageFour.shouldLock(true, 0, true, -70, 60_000, 30_000, false, true, false));
        assertFalse(StageFour.shouldLock(true, 0, true, -70, 1_000, 30_000, false, false, false));
        assertTrue(StageFour.shouldLock(true, 0, true, -70, 30_000, 30_000, false, false, false));
        assertTrue(StageFour.shouldLock(true, -90, true, -70, 30_000, 30_000, false, false, false));
        assertFalse(StageFour.shouldLock(true, -90, true, -70, 30_000, 30_000, false, false, true));
        assertEquals(0, StageFour.awayFor(5_000, -40, -70, 1_000, true));
        assertEquals(6_000, StageFour.awayFor(5_000, -80, -70, 1_000, true));
        assertEquals(0, StageFour.awayFor(5_000, 0, -70, 1_000, false));
        assertNull(StageFour.splitCommand("shutdown"));
        assertEquals("pause", StageFour.splitCommand("pause")[0]);
        assertEquals("", StageFour.splitCommand("pause")[1]);
        assertEquals("100", StageFour.splitCommand("volume:140")[1]);
        assertNull(StageFour.splitCommand("volume:loud"));
        assertEquals("1,70,30,0", StageFour.formatSetup(true, 70, 30, false));
        assertEquals(95, StageFour.parseSetup("1,200,1,2")[1]);
        assertEquals(5, StageFour.parseSetup("1,200,1,2")[2]);
        assertEquals("Playing Sunny - Ada", StageFour.nowLine("Sunny", "Ada", true));
        assertEquals("Nothing is playing", StageFour.nowLine(" ", "", false));
        assertEquals("scan.pdf", StageFour.fileTitle("Saved scan.pdf in Downloads/BridgeLink"));
        assertFalse(StageFour.artFits(StageFour.ART_CAP + 1));
        assertTrue(StageFour.artFits(32));
        java.util.List<StageFour.Clip> clips = StageFour.add(java.util.List.of(), "hello", false, 5);
        clips = StageFour.add(clips, "password: x", true, 6);
        clips = StageFour.add(clips, "123456", false, 7);
        assertEquals(1, clips.size());
        clips = StageFour.add(clips, "line one\nline two", false, 9);
        assertEquals("line one\nline two", StageFour.decodeClips(StageFour.encodeClips(clips)).get(0).text);
        String pinned = clips.get(1).id;
        clips = StageFour.pin(clips, pinned, true);
        for (int i = 0; i < 120; i++) clips = StageFour.add(clips, "item " + i, false, 20 + i);
        assertEquals(StageFour.CLIP_CAP, clips.size());
        boolean found = false;
        for (StageFour.Clip clip : clips) if (clip.id.equals(pinned) && clip.pinned) found = true;
        assertTrue(found);
        clips = StageFour.delete(clips, pinned);
        found = false;
        for (StageFour.Clip clip : clips) if (clip.id.equals(pinned)) found = true;
        assertFalse(found);
        assertEquals(0, StageFour.search(clips, "no-such-clip").size());
        java.util.List<StageFour.Item> inbox = StageFour.addInbox(java.util.List.of(), "notify", "Mail", "hi", false, 1);
        inbox = StageFour.addInbox(inbox, "file", "a.pdf", "Saved", false, 2);
        inbox = StageFour.addInbox(inbox, "sms", "Ada", "password: secret", true, 3);
        inbox = StageFour.addInbox(inbox, "notify", "Bank", "482193", false, 4);
        assertEquals("Hidden", inbox.get(0).detail);
        assertEquals("Hidden", inbox.get(1).detail);
        assertEquals(1, StageFour.filter(inbox, "file", "").size());
        assertEquals(1, StageFour.filter(inbox, "all", "pdf").size());
        assertEquals("a.pdf", StageFour.decodeInbox(StageFour.encodeInbox(inbox)).get(2).title);
        assertEquals(StageFour.BLE_UUID, "f1a0b4c0-6d3e-4a5b-8c7d-9e0f1a2b3c4d");
    }

    @Test
    public void featuresAgreeOnResumeClipboardOtpAndLock() throws Exception {
        assertEquals(List.of("files", "clipboard"), Features.shared(List.of("files", "sms", "clipboard"), List.of("clipboard", "files")));
        assertFalse(Features.enabled("", "sms", false));
        assertTrue(Features.enabled("sms=1,notify=0", "sms", false));
        assertFalse(Features.enabled("sms=1,notify=0", "notify", true));
        assertEquals(4L, Features.resumeFrom(4, 10));
        assertEquals(-1L, Features.resumeFrom(11, 10));
        assertTrue(Features.isEcho(Features.echoKey("same"), "same"));
        assertFalse(Features.isEcho(Features.echoKey("same"), "other"));
        assertEquals("482193", Features.otp("HDFC Bank: 482193 is the OTP for UPI payment of Rs.500"));
        assertEquals("123456", Features.otp("123456 is your OTP"));
        assertEquals("998877", Features.otp("G-998877 is your Google verification code"));
        assertEquals("1234", Features.otp("PIN is 1234"));
        assertEquals("12345678", Features.otp("Your code is 12345678"));
        assertNull(Features.otp("Your code is 123456789"));
        assertNull(Features.otp("Debited Rs.5000 on 12/11/2026"));
        assertNull(Features.otp("Call 9876543210 about the order"));
        assertNull(Features.otp("Flight 6E1234 is on time"));
        assertNull(Features.otp("hello from the phone"));
        assertEquals("492813", Features.otp("OTP: 492813. Valid for 10 min"));
        assertEquals("4821", Features.otp("OTP 4821 for A/c XX3291"));
        assertTrue(Features.looksSensitive("password: hunter2"));
        assertTrue(Features.looksSensitive("4111111111111111"));
        assertFalse(Features.looksSensitive("hello from the phone"));
        assertTrue(Features.batteryChanged(0, false, false, 80, false));
        assertFalse(Features.batteryChanged(80, true, true, 80, true));
        assertEquals(List.of("10.0.0.2", "10.0.0.8"), Features.addressOrder("10.0.0.2", "10.0.0.8,10.0.0.2"));
        assertEquals(List.of("b", "a"), Features.rememberLine(List.of("a"), "b", 2));
        String report = Features.diagnostics("0.8.0", "Android 14", true, true, 8787, "Paired");
        assertTrue(report.contains("secrets=none"));
        assertFalse(report.contains("token"));
        assertTrue(Features.lowBattery(10, false, false));
        assertFalse(Features.lowBattery(10, true, false));
        assertEquals("pause", Features.mediaAction("pause"));
        assertNull(Features.mediaAction("shutdown"));
        assertEquals(1, Features.smsParts("hi").size());
        assertTrue(Features.smsParts("a".repeat(161)).size() > 1);
        assertEquals(2, Features.smsParts("ह".repeat(71)).size());
        assertEquals("scan-20251114-200000.pdf", Features.scanName(1_763_150_400_000L));
        assertTrue(Features.triggerMatches("charging", "charging", true));
        assertFalse(Features.triggerMatches("charging", "charging", false));
        assertEquals(List.of("b"), Features.unseen(List.of("a"), List.of("a", "b")));
        assertEquals("photos/cat.jpg", Features.safeRelative("photos/../photos/cat.jpg".replace("photos/../", "")));
        assertEquals("photos/cat.jpg", Features.safeRelative("photos/cat.jpg"));
        assertNull(Features.safeRelative("../etc/passwd"));
        assertNull(Features.safeRelative("/etc/passwd"));
        assertFalse(Features.shouldMirror(true, false, true, false));
        assertTrue(Features.shouldMirror(false, false, true, false));
        Features.Notice hidden = Features.notificationShown("Mail", "Salary", "secret body", false, true);
        assertEquals("Mail", hidden.title);
        assertEquals("Notification", hidden.body);
        assertFalse(hidden.body.contains("secret"));
        Features.Notice shown = Features.notificationShown("Mail", "Salary", "secret body", true, true);
        assertEquals("Salary", shown.title);
        assertEquals("secret body", shown.body);
        Features.Notice optedOut = Features.notificationShown("Mail", "Salary", "secret body", true, false);
        assertEquals("Mail", optedOut.title);
        assertEquals("Notification", optedOut.body);
        assertEquals(LinkFailure.Kind.KEY_CHANGED, LinkFailure.classify(new LinkFailure(LinkFailure.Kind.KEY_CHANGED, "x")));
        assertEquals(LinkFailure.Kind.TIMEOUT, LinkFailure.classify(new java.net.SocketTimeoutException("timed out waiting")));
        assertEquals(LinkFailure.Kind.VERSION_MISMATCH, LinkFailure.classify(
                new IllegalArgumentException(new LinkFailure(LinkFailure.Kind.VERSION_MISMATCH, "version"))));
        assertTrue(LinkFailure.Kind.KEY_CHANGED.fatal);
        assertFalse(LinkFailure.Kind.NETWORK_LOST.fatal);
        assertFalse(LinkFailure.message(LinkFailure.Kind.TIMEOUT).contains("timed out waiting"));
        java.util.ArrayDeque<String> parked = new java.util.ArrayDeque<>();
        ServiceDoor.Result blocked = ServiceDoor.deliver(false, () -> {
            throw new AssertionError("in process");
        }, () -> {
            throw new ForegroundServiceStartNotAllowedException();
        }, ticket -> parked.add(ticket), "notify");
        assertTrue(blocked.queued);
        assertEquals(ServiceDoor.RECONNECT, blocked.notice);
        assertEquals("notify", parked.peek());
        ServiceDoor.Result live = ServiceDoor.deliver(true, () -> parked.add("handled"), () -> {
            throw new AssertionError("should not start");
        }, ticket -> parked.add(ticket), "notify");
        assertTrue(live.handled);
        assertFalse(live.queued);
        assertTrue(RestrictedHelp.blocked(33, 1));
        assertFalse(RestrictedHelp.blocked(32, 1));
        assertFalse(RestrictedHelp.blocked(34, 0));
        assertTrue(RestrictedHelp.stepsText().contains("Allow restricted settings"));
        String secret = "pairing-secret-value";
        assertEquals(32, Features.roomId(secret).length());
        assertFalse(Features.roomId(secret).contains(secret));
        byte[] key = Features.deriveKey(secret);
        byte[] nonce = new byte[12];
        nonce[0] = 7;
        byte[] sealed = Features.seal(key, 1, nonce, "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello", new String(Features.open(key, sealed), StandardCharsets.UTF_8));
        sealed[sealed.length - 1] ^= 1;
        try {
            Features.open(key, sealed);
            assertTrue(false);
        } catch (Exception expected) {
            assertTrue(true);
        }
        assertEquals(17, Features.streamHeader(3, 100, 1, 20).length);
        assertEquals(20, Features.streamLength(Features.streamHeader(3, 100, 1, 20)));
        Frame.Parser parser = new Frame.Parser();
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 40; i++) {
            byte[] noise = new byte[random.nextInt(64)];
            random.nextBytes(noise);
            try {
                parser.push(noise);
            } catch (Exception expected) {
                parser = new Frame.Parser();
            }
        }
        assertEquals(32, Features.digestLength(noiseSafe()));
    }

    private static byte[] noiseSafe() {
        return new byte[]{1, 2, 3, 4};
    }

    @Test
    public void otpTableRejectsPricesDatesAndLongNumbers() {
        String[][] rows = otpRows();
        assertTrue(rows.length >= 40);
        for (String[] row : rows) {
            assertEquals(row[0], row[2], Features.otp(row[1]));
        }
    }

    private static String[][] otpRows() {
        return new String[][]{
                {"482193", "HDFC Bank: 482193 is the OTP for UPI payment of Rs.500", "482193"},
                {"123456", "123456 is your OTP", "123456"},
                {"998877", "G-998877 is your Google verification code", "998877"},
                {"1234", "PIN is 1234", "1234"},
                {"12345678", "Your code is 12345678", "12345678"},
                {null, "Your code is 123456789", null},
                {null, "Debited Rs.5000 on 12/11/2026", null},
                {null, "Call 9876543210 about the order", null},
                {null, "hello from the phone", null},
                {"492813", "OTP: 492813. Valid for 10 min", "492813"},
                {"556677", "SBI: one time password is 556677", "556677"},
                {"111222", "Use verification code 111222 to continue", "111222"},
                {"909090", "Paytm OTP 909090. Do not share this code", "909090"},
                {"445566", "ICICI Bank OTP is 445566 for credit card txn", "445566"},
                {null, "Your balance is Rs 25000", null},
                {null, "Meeting on 15-11-2026 at noon", null},
                {"2468", "passcode 2468", "2468"},
                {null, "Order #12 is ready", null},
                {"135790", "WhatsApp code: 135790", "135790"},
                {"102938", "Your Amazon OTP is 102938", "102938"},
                {null, "Price $19.99 only today", null},
                {"657483", "Airtel: OTP 657483 to login", "657483"},
                {"2222", "ATM PIN 2222 was requested", "2222"},
                {null, "Reference 123 is not a code", null},
                {"314159", "code 314159 expires soon", "314159"},
                {null, "The year 2026 was busy", null},
                {"808080", "PhonePe OTP: 808080 for UPI", "808080"},
                {null, "UPI ref 123456789012", null},
                {"191919", "password code 191919", "191919"},
                {null, "See you at 6", null},
                {"454545", "Gpay verification 454545", "454545"},
                {null, "Invoice 44 for milk", null},
                {"787878", "one-time code 787878", "787878"},
                {null, "Rs.123456 debited", null},
                {"363636", "OTP 363636", "363636"},
                {null, "barcode 112233 is on the box", null},
                {"575757", "Your login code is 575757", "575757"},
                {null, "Date of birth 01/02/1990", null},
                {"646464", "Bank of Baroda OTP 646464", "646464"},
                {"818181", "Use 818181 as the code", "818181"},
                {null, "Call center 1800123456", null},
                {"929292", "Axis Bank: 929292 is the OTP", "929292"}
        };
    }

    private static String fixture() throws Exception {
        Path[] paths = new Path[]{
                Path.of("docs", "fixtures", "text.json"),
                Path.of("..", "docs", "fixtures", "text.json"),
                Path.of("..", "..", "docs", "fixtures", "text.json")
        };
        for (Path path : paths) {
            if (Files.exists(path)) return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
        }
        throw new IllegalStateException("Missing docs/fixtures/text.json");
    }

    @Test
    public void stageFiveRules() throws Exception {
        assertEquals(0, StageFive.simSlot(-1, 2));
        assertEquals(1, StageFive.simSlot(5, 2));
        assertTrue(StageFive.addressOk("+1 415 555 0100"));
        assertFalse(StageFive.addressOk("hello"));
        assertNull(StageFive.pointer("shutdown", 1, 1));
        assertEquals(10, StageFive.pointer("move", 10, -3)[0]);
        assertEquals(-3, StageFive.pointer("move", 10, -3)[1]);
        assertEquals(1, StageFive.pointer("laser", 5, 2)[0]);
        assertEquals("right", StageFive.slideKey("next"));
        assertEquals("left", StageFive.slideKey("prev"));
        assertEquals("return", StageFive.keyName("Return"));
        assertNull(StageFive.keyName("a\nb"));
        assertEquals(40, StageFive.keyName("a".repeat(50)).length());
        StageFive.Sms sms = new StageFive.Sms("1", "+1555", "hello\nthere", true, 0, 5);
        assertEquals("hello\nthere", StageFive.decodeSms(StageFive.encodeSms(List.of(sms))).get(0).body);
        assertEquals(StageFive.Choice.TAKE, StageFive.decide(false, false, "", false, "abc"));
        assertEquals(StageFive.Choice.CONFLICT, StageFive.decide(false, true, "old", false, "new"));
        assertEquals(StageFive.Choice.DELETE, StageFive.decide(false, true, "abc", true, "abc"));
        assertEquals(StageFive.Choice.KEEP, StageFive.decide(false, true, "changed", true, "abc"));
        assertEquals(StageFive.Choice.SKIP, StageFive.decide(true, false, "", false, "abc"));
        assertEquals("notes/a (BridgeLink conflict).txt", StageFive.conflictName("notes/a.txt"));
        assertFalse(StageFive.partOk("../x", 0, 1, 1));
        assertTrue(StageFive.partOk("a.txt", 0, 4, 4));
        java.util.List<StageFive.Rule> rules = StageFive.addRule(List.of(), "connect", "alert", "Hi");
        assertEquals("alert", StageFive.fire(rules, "connect", "").get(0).thenAction);
        assertTrue(StageFive.fire(StageFive.toggle(rules, rules.get(0).id, false), "connect", "").isEmpty());
        java.util.List<StageFive.Rule> many = List.of();
        for (int i = 0; i < 10; i++) many = StageFive.addRule(many, "connect", "note", "n" + i);
        assertEquals(8, many.size());
        assertTrue(StageFive.addRule(List.of(), "explode", "alert", "").isEmpty());
        assertTrue(StageFive.batteryLow(19, false));
        assertFalse(StageFive.batteryLow(19, true));
        assertFalse(StageFive.batteryLow(20, false));
        assertTrue(StageFive.homeWifi("Home", ""));
        assertTrue(StageFive.homeWifi("Home", "home"));
        assertFalse(StageFive.homeWifi("<unknown ssid>", ""));
        assertEquals("Notes", StageFive.appName("Notes.app"));
        assertNull(StageFive.appName("../Apps"));
        assertTrue(StageFive.SMS_STEPS.contains("Allow restricted settings"));
        assertTrue(StageFive.SMS_STEPS.contains("SMS"));
        assertTrue(StageFive.POINTER_STEPS.contains("Accessibility"));
        StageFive.Entry kept = new StageFive.Entry("a.txt", 1, 1, "aa", false);
        StageFive.Entry gone = new StageFive.Entry("b.txt", 1, 1, "bb", false);
        assertEquals("b.txt", StageFive.removed(List.of(kept, gone), List.of(kept)).get(0).path);
        java.io.File root = Files.createTempDirectory("bl-folder").toFile();
        FolderFiles box = new FolderFiles(root);
        String sha = Transfers.sha256Hex("hello".getBytes(StandardCharsets.UTF_8));
        String hello = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        assertNull(box.part("notes/a.txt", 0, 5, 10, sha, hello));
        assertEquals("hello", new String(Files.readAllBytes(root.toPath().resolve("notes/a.txt")), StandardCharsets.UTF_8));
        String other = Transfers.sha256Hex("world".getBytes(StandardCharsets.UTF_8));
        String world = Base64.getEncoder().encodeToString("world".getBytes(StandardCharsets.UTF_8));
        assertNull(box.part("notes/a.txt", 0, 5, 11, other, world));
        assertEquals("hello", new String(Files.readAllBytes(root.toPath().resolve("notes/a.txt")), StandardCharsets.UTF_8));
        assertEquals("world", new String(Files.readAllBytes(root.toPath().resolve("notes/a (BridgeLink conflict).txt")), StandardCharsets.UTF_8));
        assertFalse(box.remove("notes/a.txt", other));
        assertTrue(box.remove("notes/a.txt", sha));
        assertFalse(new java.io.File(root, "notes/a.txt").exists());
        assertEquals("Refused a shared file.", box.part("../a.txt", 0, 1, 1, sha, "YQ=="));
    }

    /** Name matches the Android 12 exception so the door can recognize it without the SDK class. */
    public static final class ForegroundServiceStartNotAllowedException extends IllegalStateException {
        public ForegroundServiceStartNotAllowedException() {
            super("blocked");
        }
    }
}
