package app.bridgelink.android.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Short code both screens show. It uses the Mac certificate the phone
 * actually saw, so a relay with a different certificate shows a different code.
 */
public final class Sas {
    private Sas() {}

    public static String code(String macFingerprint, String phoneNonce, String macNonce) {
        String message = (macFingerprint == null ? "" : macFingerprint)
                + "\n" + (phoneNonce == null ? "" : phoneNonce)
                + "\n" + (macNonce == null ? "" : macNonce);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("bridgelink-sas-v1".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            long value = ((out[0] & 0xffL) << 24) | ((out[1] & 0xffL) << 16) | ((out[2] & 0xffL) << 8) | (out[3] & 0xffL);
            return String.format(Locale.US, "%06d", value % 1_000_000L);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
