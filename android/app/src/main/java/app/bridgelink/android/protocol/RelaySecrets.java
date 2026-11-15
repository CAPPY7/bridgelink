package app.bridgelink.android.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** One away-from-home secret per phone. Forgetting a phone drops only that secret. */
public final class RelaySecrets {
    private RelaySecrets() {}

    public static String put(String book, String deviceId, String secret) {
        if (deviceId == null || deviceId.isEmpty() || secret == null || secret.length() < 16) return book == null ? "{}" : book;
        JsonObject map = parse(book);
        map.addProperty(deviceId, secret);
        return map.toString();
    }

    public static String of(String book, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return "";
        JsonObject map = parse(book);
        if (!map.has(deviceId) || map.get(deviceId).isJsonNull()) return "";
        String secret = map.get(deviceId).getAsString();
        return secret == null ? "" : secret;
    }

    public static String drop(String book, String deviceId) {
        JsonObject map = parse(book);
        if (deviceId != null) map.remove(deviceId);
        return map.toString();
    }

    public static boolean allowed(String book, String deviceId, String secret) {
        String saved = of(book, deviceId);
        return secret != null && secret.length() >= 16 && saved.length() >= 16 && saved.equals(secret);
    }

    private static JsonObject parse(String book) {
        if (book == null || book.isBlank()) return new JsonObject();
        try {
            return JsonParser.parseString(book).getAsJsonObject();
        } catch (Exception error) {
            return new JsonObject();
        }
    }
}
