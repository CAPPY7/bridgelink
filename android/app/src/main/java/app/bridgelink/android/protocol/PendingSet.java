package app.bridgelink.android.protocol;

import java.util.LinkedHashSet;

/** Every connection that is still pairing stays on the list. */
public final class PendingSet {
    private final LinkedHashSet<String> ids = new LinkedHashSet<>();

    public void add(String id) {
        if (id != null && !id.isEmpty()) ids.add(id);
    }

    public boolean remove(String id) {
        return ids.remove(id);
    }

    public boolean contains(String id) {
        return ids.contains(id);
    }

    public int count() {
        return ids.size();
    }
}
