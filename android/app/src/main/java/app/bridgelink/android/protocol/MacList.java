package app.bridgelink.android.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Saved Macs. Tokens are not stored in this list. */
public final class MacList {
    private MacList() {}

    public static final class Saved {
        public final String name;
        public final String host;
        public final int port;
        public final String fp;
        public final String deviceId;

        public Saved(String name, String host, int port, String fp, String deviceId) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.fp = fp;
            this.deviceId = deviceId;
        }
    }

    public static String add(String existing, String name, String host, int port, String fingerprint) {
        return add(existing, name, host, port, fingerprint, "");
    }

    public static String add(String existing, String name, String host, int port, String fingerprint, String deviceId) {
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
        row.addProperty("deviceId", deviceId == null ? "" : deviceId);
        next.add(row);
        return next.toString();
    }

    public static Saved findByName(String existing, String name) {
        if (name == null || name.isBlank()) return null;
        JsonArray array = parse(existing);
        for (int i = 0; i < array.size(); i++) {
            JsonObject row = array.get(i).getAsJsonObject();
            if (name.trim().equalsIgnoreCase(text(row, "name"))) {
                int port = 8787;
                try {
                    port = row.has("port") ? row.get("port").getAsInt() : 8787;
                } catch (Exception ignored) {
                }
                return new Saved(text(row, "name"), text(row, "host"), port, text(row, "fp"), text(row, "deviceId"));
            }
        }
        return null;
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
