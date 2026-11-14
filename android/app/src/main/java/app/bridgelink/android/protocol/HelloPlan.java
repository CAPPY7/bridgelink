package app.bridgelink.android.protocol;

import com.google.gson.JsonObject;

/**
 * Decides the first message. A token is sent only when the certificate matches
 * the one we already pinned. A PIN is never sent; a proof over the seen
 * certificate is sent instead.
 */
public final class HelloPlan {
    public final String fatal;
    public final JsonObject payload;

    private HelloPlan(String fatal, JsonObject payload) {
        this.fatal = fatal;
        this.payload = payload;
    }

    public static HelloPlan decide(String name, String pin, String token, String pinnedFingerprint, String seenFingerprint) {
        if (seenFingerprint == null || seenFingerprint.isEmpty()) {
            return new HelloPlan("The Mac did not present a certificate.", null);
        }
        if (pinnedFingerprint != null && !pinnedFingerprint.isEmpty()
                && !pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            return new HelloPlan("This Mac key changed. Forget the Mac, then pair again.", null);
        }
        if (token != null && token.length() >= 16
                && pinnedFingerprint != null && pinnedFingerprint.equalsIgnoreCase(seenFingerprint)) {
            JsonObject payload = base(name);
            payload.addProperty("token", token);
            return new HelloPlan(null, payload);
        }
        if (pin != null && pin.length() == 6) {
            JsonObject payload = base(name);
            payload.addProperty("proof", Proof.hmacHex(pin, seenFingerprint));
            return new HelloPlan(null, payload);
        }
        return new HelloPlan("Enter the 6-digit PIN from the Mac.", null);
    }

    private static JsonObject base(String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name == null || name.isEmpty() ? "Phone" : name);
        return payload;
    }
}
