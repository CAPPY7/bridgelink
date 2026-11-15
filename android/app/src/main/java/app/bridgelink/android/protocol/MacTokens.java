package app.bridgelink.android.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** One sealed token per Mac fingerprint. The active "token" slot is separate. */
public final class MacTokens {
    private MacTokens() {}

    public static String put(String existing, String fingerprint, String token) {
        if (fingerprint == null || fingerprint.isBlank() || token == null || token.length() < 16) {
            return existing == null ? "{}" : existing;
        }
        JsonObject book = parse(existing);
        book.addProperty(fingerprint.toLowerCase(), token);
        return book.toString();
    }

    public static String tokenFor(String existing, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return "";
        JsonObject book = parse(existing);
        String key = fingerprint.toLowerCase();
        if (!book.has(key) || book.get(key).isJsonNull()) return "";
        String token = book.get(key).getAsString();
        return token.length() >= 16 ? token : "";
    }

    public static String remove(String existing, String fingerprint) {
        JsonObject book = parse(existing);
        if (fingerprint != null && !fingerprint.isBlank()) book.remove(fingerprint.toLowerCase());
        return book.toString();
    }

    private static JsonObject parse(String existing) {
        if (existing == null || existing.isBlank()) return new JsonObject();
        try {
            return JsonParser.parseString(existing).getAsJsonObject();
        } catch (Exception error) {
            return new JsonObject();
        }
    }
}
