package app.bridgelink.android.protocol;

import com.google.gson.JsonObject;

/**
 * First message. A PIN proof is sent only when the certificate matches the
 * fingerprint from the QR link. A typed PIN never becomes a proof, because
 * that proof can be guessed offline. An unpinned certificate starts a
 * match-code exchange and sends no secret.
 */
public final class HelloPlan {
    public final LinkFailure.Kind kind;
    public final String fatal;
    public final JsonObject payload;

    private HelloPlan(LinkFailure.Kind kind, String fatal, JsonObject payload) {
        this.kind = kind;
        this.fatal = fatal;
        this.payload = payload;
    }

    public static HelloPlan decide(String name, String pin, String token, String pinnedFingerprint, String seenFingerprint, String phoneNonce) {
        if (seenFingerprint == null || seenFingerprint.isEmpty()) {
            return fail(LinkFailure.Kind.REFUSED, "The Mac did not present a certificate.");
        }
        boolean pinned = pinnedFingerprint != null && !pinnedFingerprint.isEmpty();
        if (pinned && !pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            return fail(LinkFailure.Kind.KEY_CHANGED, LinkFailure.message(LinkFailure.Kind.KEY_CHANGED));
        }
        if (token != null && token.length() >= 16 && pinned && pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            JsonObject payload = base(name);
            payload.addProperty("token", token);
            return new HelloPlan(null, null, payload);
        }
        if (pin != null && pin.length() == 6 && pinned && pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            JsonObject payload = base(name);
            payload.addProperty("proof", Proof.hmacHex(pin, seenFingerprint));
            return new HelloPlan(null, null, payload);
        }
        if (!pinned) {
            JsonObject payload = base(name);
            payload.addProperty("commit", Sas.commit(phoneNonce));
            return new HelloPlan(null, null, payload);
        }
        return fail(LinkFailure.Kind.WRONG_CODE, "Scan the Mac code again. The saved key does not have a PIN.");
    }

    private static HelloPlan fail(LinkFailure.Kind kind, String message) {
        return new HelloPlan(kind, message, null);
    }

    private static JsonObject base(String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name == null || name.isEmpty() ? "Phone" : name);
        return payload;
    }
}
