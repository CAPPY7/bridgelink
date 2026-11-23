package app.bridgelink.android.protocol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Paging for the phone photo shelf. Newest first, no duplicates. */
public final class PhotoRules {
    public static final int PAGE = 30;

    public static final class Shot {
        public final String id;
        public final long at;

        public Shot(String id, long at) {
            this.id = id == null ? "" : id;
            this.at = at;
        }
    }

    private PhotoRules() {}

    public static List<Shot> newestFirst(List<Shot> items) {
        List<Shot> copy = new ArrayList<>();
        if (items != null) {
            for (Shot shot : items) {
                if (shot != null && !shot.id.isEmpty()) copy.add(shot);
            }
        }
        copy.sort(Comparator.comparingLong((Shot shot) -> shot.at).reversed().thenComparing(shot -> shot.id));
        List<Shot> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Shot shot : copy) {
            if (seen.add(shot.id)) unique.add(shot);
        }
        return unique;
    }

    public static List<Shot> page(List<Shot> newest, int offset, int limit) {
        List<Shot> ordered = newest == null ? List.of() : newest;
        int start = Math.max(0, offset);
        int size = limit < 1 ? PAGE : Math.min(PAGE, limit);
        if (start >= ordered.size()) return List.of();
        int end = Math.min(ordered.size(), start + size);
        List<Shot> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = start; i < end; i++) {
            Shot shot = ordered.get(i);
            if (shot == null || shot.id.isEmpty() || !seen.add(shot.id)) continue;
            out.add(shot);
        }
        return out;
    }

    public static boolean noDuplicates(List<Shot> rows) {
        Set<String> seen = new HashSet<>();
        if (rows == null) return true;
        for (Shot shot : rows) {
            if (shot == null || !seen.add(shot.id)) return false;
        }
        return true;
    }
}
