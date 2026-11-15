package app.bridgelink.android.protocol;

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** `bridgelink://pair?v=1&host=&port=&fp=&pin=` from the Mac QR code. Extra addresses use `hosts`. */
public final class PairingLink {
    public final String host;
    public final int port;
    public final String fingerprint;
    public final String pin;
    public final List<String> hosts;

    public PairingLink(String host, int port, String fingerprint, String pin) {
        this(host, port, fingerprint, pin, Features.addressOrder(host, ""));
    }

    public PairingLink(String host, int port, String fingerprint, String pin, List<String> hosts) {
        this.host = host;
        this.port = port;
        this.fingerprint = fingerprint;
        this.pin = pin;
        this.hosts = hosts;
    }

    public static PairingLink parse(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        int query = text.indexOf('?');
        if (!text.startsWith("bridgelink://pair") || query < 0) return null;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : text.substring(query + 1).split("&")) {
            int cut = part.indexOf('=');
            if (cut <= 0) continue;
            fields.put(decode(part.substring(0, cut)), decode(part.substring(cut + 1)));
        }
        if (!"1".equals(fields.get("v"))) return null;
        String host = fields.get("host");
        if (host == null || host.isEmpty()) return null;
        int port = 8787;
        try {
            port = Integer.parseInt(fields.getOrDefault("port", "8787"));
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (port < 1 || port > 65535) return null;
        String pin = fields.getOrDefault("pin", "");
        String fingerprint = fields.getOrDefault("fp", "");
        List<String> hosts = Features.addressOrder(host, fields.getOrDefault("hosts", ""));
        return new PairingLink(host, port, fingerprint, pin, hosts);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception error) {
            return value;
        }
    }
}
