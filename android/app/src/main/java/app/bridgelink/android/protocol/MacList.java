package app.bridgelink.android.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Saved Macs. Tokens are not stored in this list. */
public final class MacList {
    private MacList() {}

    public static String add(String existing, String name, String host, int port, String fingerprint) {
        JsonArray array = parse(existing);
        JsonArray next = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonObject row = array.get(i).getAsJsonObject();
            if (!host.equals(text(row, "host"))) next.add(row);
        }
        JsonObject row = new JsonObject();
        row.addProperty("name", name == null || name.isEmpty() ? "Mac" : name);
        row.addProperty("host", host);
        row.addProperty("port", port);
        row.addProperty("fp", fingerprint == null ? "" : fingerprint);
        next.add(row);
        return next.toString();
    }

    public static String rename(String existing, String host, String name) {
        JsonArray array = parse(existing);
        String clean = StageRules.tidyName(name, "Mac");
        for (int i = 0; i < array.size(); i++) {
            JsonObject row = array.get(i).getAsJsonObject();
            if (host.equals(text(row, "host"))) row.addProperty("name", clean);
        }
        return array.toString();
    }

    public static String removeHost(String existing, String host) {
        JsonArray array = parse(existing);
        JsonArray next = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonObject row = array.get(i).getAsJsonObject();
            if (!host.equals(text(row, "host"))) next.add(row);
        }
        return next.toString();
    }

    public static String names(String existing) {
        JsonArray array = parse(existing);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (out.length() > 0) out.append(", ");
            out.append(text(array.get(i).getAsJsonObject(), "name"));
        }
        return out.toString();
    }

    private static JsonArray parse(String existing) {
        if (existing == null || existing.isBlank()) return new JsonArray();
        try {
            return JsonParser.parseString(existing).getAsJsonArray();
        } catch (Exception error) {
            return new JsonArray();
        }
    }

    private static String text(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : "";
    }
}
