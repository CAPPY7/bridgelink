package app.bridgelink.android.protocol;

import com.google.gson.JsonObject;

/**
 * First message. A PIN proof is sent only when the certificate matches the
 * fingerprint from the QR link. A typed PIN never becomes a proof, because
 * that proof can be guessed offline. An unpinned certificate starts a
 * match-code exchange and sends no secret.
 */
public final class HelloPlan {
    public final String fatal;
    public final JsonObject payload;

    private HelloPlan(String fatal, JsonObject payload) {
        this.fatal = fatal;
        this.payload = payload;
    }

    public static HelloPlan decide(String name, String pin, String token, String pinnedFingerprint, String seenFingerprint, String phoneNonce) {
        if (seenFingerprint == null || seenFingerprint.isEmpty()) {
            return new HelloPlan("The Mac did not present a certificate.", null);
        }
        boolean pinned = pinnedFingerprint != null && !pinnedFingerprint.isEmpty();
        if (pinned && !pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            return new HelloPlan("This Mac key changed. Forget the Mac, then pair again.", null);
        }
        if (token != null && token.length() >= 16 && pinned && pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            JsonObject payload = base(name);
            payload.addProperty("token", token);
            return new HelloPlan(null, payload);
        }
        if (pin != null && pin.length() == 6 && pinned && pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            JsonObject payload = base(name);
            payload.addProperty("proof", Proof.hmacHex(pin, seenFingerprint));
            return new HelloPlan(null, payload);
        }
        if (!pinned) {
            JsonObject payload = base(name);
            payload.addProperty("nonce", phoneNonce == null ? "" : phoneNonce);
            return new HelloPlan(null, payload);
        }
        return new HelloPlan("Scan the Mac code again. The saved key does not have a PIN.", null);
    }

    private static JsonObject base(String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name == null || name.isEmpty() ? "Phone" : name);
        return payload;
    }
}
