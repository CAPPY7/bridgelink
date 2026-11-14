package app.bridgelink.android.protocol;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
        assertEquals("phone-nonce", typed.payload.get("nonce").getAsString());

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
}
