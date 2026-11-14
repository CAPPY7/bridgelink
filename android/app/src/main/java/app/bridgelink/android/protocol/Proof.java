package app.bridgelink.android.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Pairing proof. The raw PIN never goes on the wire. */
public final class Proof {
    private Proof() {}

    public static String hmacHex(String pin, String fingerprint) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pin.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(fingerprint.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(out.length * 2);
            for (byte value : out) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static boolean matches(String pin, String fingerprint, String proof) {
        if (pin == null || fingerprint == null || proof == null) return false;
        byte[] expected = hmacHex(pin, fingerprint).getBytes(StandardCharsets.UTF_8);
        byte[] given = proof.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, given);
    }
}
