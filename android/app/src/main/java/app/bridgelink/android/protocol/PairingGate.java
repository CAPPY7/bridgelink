package app.bridgelink.android.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** One-time PIN: 2 minute life, 5 guesses, then a 60 second lock. */
public final class PairingGate {
    public static final int MAX_GUESSES = 5;
    public static final long TOKEN_TTL_MS = 120_000L;
    public static final long LOCK_MS = 60_000L;

    private final String token;
    private final long expiresAt;
    private int failures;
    private long lockedUntil;
    private boolean used;

    public PairingGate(String token, long now) {
        this.token = token;
        this.expiresAt = now + TOKEN_TTL_MS;
    }

    public static String newToken() {
        int value = new SecureRandom().nextInt(1_000_000);
        return String.format("%06d", value);
    }

    public boolean expired(long now) {
        return now > expiresAt;
    }

    public boolean locked(long now) {
        return now < lockedUntil;
    }

    public boolean check(String guess, long now) {
        if (expired(now) || locked(now) || used || guess == null) return false;
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        byte[] given = guess.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(expected, given)) {
            failures = 0;
            used = true;
            return true;
        }
        failures += 1;
        if (failures >= MAX_GUESSES) lockedUntil = now + LOCK_MS;
        return false;
    }

    public int failures() {
        return failures;
    }
}
