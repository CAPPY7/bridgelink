package app.bridgelink.android.protocol;

/** Search text for the transfer history. The percent signs are escaped. */
public final class HistoryQuery {
    public static final int PAGE = 50;
    public static final int CAP = 2000;
    public static final String SEARCH =
            "SELECT id, line, kind, direction, title, body, size, at, status FROM lines "
                    + "WHERE title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' OR line LIKE ? ESCAPE '\\' "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?";

    private HistoryQuery() {}

    public static String like(String raw) {
        String text = raw == null ? "" : raw;
        StringBuilder out = new StringBuilder("%");
        int cap = Math.min(text.length(), 80);
        for (int i = 0; i < cap; i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '%' || c == '_') out.append('\\');
            out.append(c);
        }
        return out.append('%').toString();
    }

    public static boolean keep(String text, boolean markedSensitive) {
        if (markedSensitive) return false;
        return !Features.looksSensitive(text);
    }

    /** file -> view, link -> browse, anything else -> copy. */
    public static String openPlan(String kind) {
        if ("file".equals(kind)) return "view";
        if ("link".equals(kind)) return "browse";
        return "copy";
    }
}
