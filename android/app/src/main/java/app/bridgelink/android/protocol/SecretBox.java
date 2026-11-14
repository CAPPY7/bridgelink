package app.bridgelink.android.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM. The cipher chooses the IV. An Android Keystore key rejects an IV we pick ourselves. The 12-byte IV is stored in front of the ciphertext. */
public final class SecretBox {
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private SecretBox() {}

    public static String seal(SecretKey key, String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_LEN) throw new IllegalStateException("Unexpected IV");
        byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public static String open(SecretKey key, String sealed) throws Exception {
        byte[] raw = Base64.getDecoder().decode(sealed);
        if (raw.length < IV_LEN + 16) throw new IllegalArgumentException("sealed value is too short");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, java.util.Arrays.copyOf(raw, IV_LEN)));
        byte[] plain = cipher.doFinal(raw, IV_LEN, raw.length - IV_LEN);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
