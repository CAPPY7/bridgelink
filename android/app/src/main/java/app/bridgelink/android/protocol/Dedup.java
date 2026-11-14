package app.bridgelink.android.protocol;

import java.util.ArrayDeque;

public final class Dedup {
    private final int cap;
    private final ArrayDeque<String> ids = new ArrayDeque<>();

    public Dedup(int cap) {
        this.cap = cap;
    }

    public boolean firstTime(String id) {
        if (ids.contains(id)) return false;
        ids.addLast(id);
        while (ids.size() > cap) ids.removeFirst();
        return true;
    }
}
