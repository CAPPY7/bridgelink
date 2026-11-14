package app.bridgelink.android.protocol;

import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Versioned control message. Same shape on the phone and the Mac. */
public final class Envelope {
    public static final int VERSION = 1;

    public int v;
    public String id;
    public String type;
    public String from;
    public long ts;
    public Map<String, String> payload;

    public Envelope() {}

    public Envelope(int v, String id, String type, String from, long ts, Map<String, String> payload) {
        this.v = v;
        this.id = id;
        this.type = type;
        this.from = from;
        this.ts = ts;
        this.payload = payload;
    }

    public static Envelope make(String type, String from, Map<String, String> payload) {
        return new Envelope(VERSION, UUID.randomUUID().toString(), type, from, System.currentTimeMillis(), payload);
    }

    public String body() {
        if (payload == null) return "";
        String value = payload.get("body");
        return value == null ? "" : value;
    }

    public String toJsonString() {
        return new Gson().toJson(this);
    }

    public static Envelope parse(String json) {
        Envelope envelope = new Gson().fromJson(json, Envelope.class);
        if (envelope == null || envelope.v != VERSION) throw new IllegalArgumentException("Unsupported version");
        if (envelope.id == null || envelope.id.isEmpty()) throw new IllegalArgumentException("Missing message id");
        if (envelope.type == null || envelope.type.isEmpty()) throw new IllegalArgumentException("Missing message type");
        if (!"android".equals(envelope.from) && !"mac".equals(envelope.from) && !"relay".equals(envelope.from)) {
            throw new IllegalArgumentException("Bad sender");
        }
        if (envelope.payload == null) envelope.payload = new LinkedHashMap<>();
        return envelope;
    }
}
