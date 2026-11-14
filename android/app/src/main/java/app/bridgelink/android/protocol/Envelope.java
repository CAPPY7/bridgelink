package app.bridgelink.android.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/** Versioned control message. Payload may contain strings, numbers, booleans, and objects. */
public final class Envelope {
    public static final int VERSION = 1;

    public int v = VERSION;
    public String id;
    public String type;
    public String from;
    public long ts;
    public JsonObject payload = new JsonObject();

    public static Envelope make(String type, String from, JsonObject payload) {
        Envelope envelope = new Envelope();
        envelope.v = VERSION;
        envelope.id = UUID.randomUUID().toString();
        envelope.type = type;
        envelope.from = from;
        envelope.ts = System.currentTimeMillis();
        envelope.payload = payload == null ? new JsonObject() : payload;
        return envelope;
    }

    public String text(String key) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) return "";
        JsonElement value = payload.get(key);
        if (value.isJsonPrimitive()) return value.getAsString();
        return value.toString();
    }

    public String code() {
        return text("code");
    }

    public String body() {
        return text("body");
    }

    public String toJsonString() {
        JsonObject root = new JsonObject();
        root.addProperty("v", v);
        root.addProperty("id", id);
        root.addProperty("type", type);
        root.addProperty("from", from);
        root.addProperty("ts", ts);
        root.add("payload", payload == null ? new JsonObject() : payload);
        return root.toString();
    }

    public static Envelope parse(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (parsed == null || !parsed.isJsonObject()) throw new IllegalArgumentException("Bad JSON");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("v") || root.get("v").getAsInt() != VERSION) throw new IllegalArgumentException("Unsupported version");
        Envelope envelope = new Envelope();
        envelope.v = VERSION;
        envelope.id = required(root, "id");
        envelope.type = required(root, "type");
        envelope.from = required(root, "from");
        if (!"android".equals(envelope.from) && !"mac".equals(envelope.from) && !"relay".equals(envelope.from)) {
            throw new IllegalArgumentException("Bad sender");
        }
        if (!root.has("ts")) throw new IllegalArgumentException("Missing time");
        envelope.ts = root.get("ts").getAsLong();
        if (!root.has("payload") || !root.get("payload").isJsonObject()) throw new IllegalArgumentException("Missing payload");
        envelope.payload = root.getAsJsonObject("payload");
        return envelope;
    }

    private static String required(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing " + key);
        String value = root.get(key).getAsString();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
