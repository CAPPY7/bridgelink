package app.bridgelink.android.protocol;

import java.util.LinkedHashSet;

/** A typed address is added. The other saved addresses stay. */
public final class HostMerge {
    private HostMerge() {
    }

    public static String merge(String saved, String extra) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        String all = (saved == null ? "" : saved) + "," + (extra == null ? "" : extra);
        for (String part : all.split(",")) {
            String host = part.trim();
            if (!host.isEmpty()) ordered.add(host);
        }
        return String.join(",", ordered);
    }
}
