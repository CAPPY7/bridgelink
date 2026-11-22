package app.bridgelink.android.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rules for texts, the shared folder, automations, and the phone trackpad.
 * No Android APIs, so the unit tests and the Mac port can match them.
 */
public final class StageFive {
    public static final int SMS_CAP = 80;
    public static final int RULE_CAP = 8;
    public static final int FOLDER_CAP = 200;
    public static final int PART = 24 * 1024;
    public static final long FOLDER_FILE_CAP = 32L * 1024 * 1024;
    /** One wait after a folder change. A newer change replaces the old wait. */
    public static final long FOLDER_QUIET_MS = 60_000L;
    public static final String[] EVENTS = {
            "connect", "battery_low", "sms_code", "file_in", "clip", "notify", "leave"
    };
    public static final String[] ACTIONS = {
            "open_app", "alert", "start_sync", "copy_code", "ring", "pause_sync", "note", "none"
    };
    public static final String SMS_STEPS =
            "1. Open Settings.\n"
                    + "2. Tap Apps, then BridgeLink.\n"
                    + "3. Tap the three dots in the top corner.\n"
                    + "4. Tap Allow restricted settings, then confirm.\n"
                    + "5. Go back, tap Permissions, then SMS.\n"
                    + "6. Allow SMS.";
    public static final String POINTER_STEPS =
            "1. Open System Settings.\n"
                    + "2. Go to Privacy and Security, then Accessibility.\n"
                    + "3. Turn BridgeLink on.\n"
                    + "4. If BridgeLink is missing, click + and choose the app in Applications.";

    private StageFive() {}

    /** A shared file over 32 MB uses the normal file messages. Smaller files stay on folder_part. */
    public static boolean folderUsesChunks(long size) {
        return size > FOLDER_FILE_CAP && size <= Transfers.MAX_BYTES;
    }
    public static long folderPushAt(long nowMs) {
        return nowMs + FOLDER_QUIET_MS;
    }

    /** Push only after the wait, and only while the link is up, the folder is on, and it is not paused. */
    public static boolean folderWatchDue(boolean linked, boolean on, boolean paused, long dueMs, long nowMs) {
        return linked && on && !paused && dueMs > 0 && nowMs >= dueMs;
    }

    public static final class Sms {
        public final String id;
        public final String address;
        public final String body;
        public final boolean incoming;
        public final int sim;
        public final long at;

        public Sms(String id, String address, String body, boolean incoming, int sim, long at) {
            this.id = id;
            this.address = address;
            this.body = body;
            this.incoming = incoming;
            this.sim = sim;
            this.at = at;
        }
    }

    public static final class Rule {
        public final String id;
        public final String when;
        public final String thenAction;
        public final String extra;
        public final boolean enabled;

        public Rule(String id, String when, String thenAction, String extra, boolean enabled) {
            this.id = id;
            this.when = when;
            this.thenAction = thenAction;
            this.extra = extra;
            this.enabled = enabled;
        }
    }

    public static final class Fire {
        public final String thenAction;
        public final String extra;
        public final String detail;

        public Fire(String thenAction, String extra, String detail) {
            this.thenAction = thenAction;
            this.extra = extra;
            this.detail = detail;
        }
    }

    public static final class Entry {
        public final String path;
        public final long size;
        public final long mtime;
        public final String sha;
        public final boolean deleted;

        public Entry(String path, long size, long mtime, String sha, boolean deleted) {
            this.path = path;
            this.size = size;
            this.mtime = mtime;
            this.sha = sha;
            this.deleted = deleted;
        }
    }

    public enum Choice { TAKE, KEEP, CONFLICT, DELETE, SKIP }

    public static boolean addressOk(String raw) {
        if (raw == null) return false;
        String text = raw.trim();
        if (text.length() < 3 || text.length() > 20) return false;
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') digits++;
            else if (c != '+' && c != ' ' && c != '-' && c != '(' && c != ')') return false;
        }
        return digits >= 3;
    }

    public static int simSlot(int raw, int count) {
        int slots = count < 1 ? 1 : Math.min(count, 4);
        if (raw < 0) return 0;
        if (raw >= slots) return slots - 1;
        return raw;
    }

    public static List<Sms> addSms(List<Sms> existing, Sms item) {
        ArrayList<Sms> next = new ArrayList<>();
        if (item != null && item.id != null && !item.id.isEmpty()) next.add(item);
        if (existing != null) {
            for (Sms sms : existing) {
                if (next.size() >= SMS_CAP) break;
                if (item != null && item.id != null && item.id.equals(sms.id)) continue;
                next.add(sms);
            }
        }
        return next;
    }

    public static String encodeSms(List<Sms> items) {
        StringBuilder out = new StringBuilder();
        if (items == null) return "";
        for (Sms sms : items) {
            if (out.length() > 0) out.append('\n');
            out.append(escape(sms.id)).append('\t')
                    .append(escape(sms.address)).append('\t')
                    .append(sms.incoming ? '1' : '0').append('\t')
                    .append(sms.sim).append('\t')
                    .append(sms.at).append('\t')
                    .append(escape(sms.body));
        }
        return out.toString();
    }

    public static List<Sms> decodeSms(String raw) {
        ArrayList<Sms> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return items;
        for (String line : raw.split("\n", -1)) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 6);
            if (parts.length < 6) continue;
            long at;
            int sim;
            try {
                sim = Integer.parseInt(parts[3]);
                at = Long.parseLong(parts[4]);
            } catch (NumberFormatException error) {
                continue;
            }
            items.add(new Sms(unescape(parts[0]), unescape(parts[1]), unescape(parts[5]), "1".equals(parts[2]), sim, at));
        }
        return items;
    }

    public static boolean knownEvent(String event) {
        if (event == null) return false;
        for (String item : EVENTS) if (item.equals(event)) return true;
        return false;
    }

    public static boolean knownAction(String action) {
        if (action == null) return false;
        for (String item : ACTIONS) if (item.equals(action)) return true;
        return false;
    }

    public static String eventLabel(String event) {
        if (event == null) return "";
        return switch (event) {
            case "connect" -> "Phone connects";
            case "battery_low" -> "Phone battery under 20%";
            case "home_wifi" -> "Phone joins home Wi-Fi";
            case "sms_code" -> "Text has a code";
            case "file_in" -> "A file arrives";
            case "clip" -> "Clipboard arrives";
            case "notify" -> "A notification arrives";
            case "leave" -> "Phone leaves the Mac";
            default -> event;
        };
    }

    public static String actionLabel(String action) {
        if (action == null) return "";
        return switch (action) {
            case "open_app" -> "Open an app";
            case "alert" -> "Show an alert";
            case "start_sync" -> "Start shared folder sync";
            case "copy_code" -> "Copy the code";
            case "ring" -> "Ring the phone";
            case "pause_sync" -> "Pause shared folder sync";
            case "note" -> "Show a note";
            case "none" -> "Do nothing";
            default -> action;
        };
    }

    public static List<Rule> addRule(List<Rule> existing, String when, String thenAction, String extra) {
        ArrayList<Rule> next = copyRules(existing);
        if (!knownEvent(when) || !knownAction(thenAction) || next.size() >= RULE_CAP) return next;
        String clean = extra == null ? "" : extra.trim();
        if (clean.length() > 80) clean = clean.substring(0, 80);
        int n = next.size() + 1;
        String id = when + "-" + thenAction + "-" + n;
        while (hasRule(next, id)) {
            n++;
            id = when + "-" + thenAction + "-" + n;
        }
        next.add(new Rule(id, when, thenAction, clean, true));
        return next;
    }

    public static List<Rule> toggle(List<Rule> existing, String id, boolean on) {
        ArrayList<Rule> next = new ArrayList<>();
        if (existing != null) {
            for (Rule rule : existing) {
                if (id != null && id.equals(rule.id)) {
                    next.add(new Rule(rule.id, rule.when, rule.thenAction, rule.extra, on));
                } else {
                    next.add(rule);
                }
            }
        }
        return next;
    }

    public static List<Rule> delete(List<Rule> existing, String id) {
        ArrayList<Rule> next = new ArrayList<>();
        if (existing != null) {
            for (Rule rule : existing) {
                if (id == null || !id.equals(rule.id)) next.add(rule);
            }
        }
        return next;
    }

    public static List<Fire> fire(List<Rule> rules, String event, String detail) {
        ArrayList<Fire> next = new ArrayList<>();
        if (rules == null || !knownEvent(event)) return next;
        String shown = detail == null ? "" : detail;
        for (Rule rule : rules) {
            if (!rule.enabled || !event.equals(rule.when)) continue;
            if ("home_wifi".equals(event) && !homeWifi(shown, rule.extra)) continue;
            if ("sms_code".equals(event) && shown.isEmpty()) continue;
            next.add(new Fire(rule.thenAction, rule.extra, shown));
        }
        return next;
    }

    public static String encodeRules(List<Rule> rules) {
        StringBuilder out = new StringBuilder();
        if (rules == null) return "";
        for (Rule rule : rules) {
            if (out.length() > 0) out.append('\n');
            out.append(escape(rule.id)).append('\t')
                    .append(rule.enabled ? '1' : '0').append('\t')
                    .append(rule.when).append('\t')
                    .append(rule.thenAction).append('\t')
                    .append(escape(rule.extra));
        }
        return out.toString();
    }

    public static List<Rule> decodeRules(String raw) {
        ArrayList<Rule> rules = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return rules;
        for (String line : raw.split("\n", -1)) {
            if (line.isEmpty() || rules.size() >= RULE_CAP) continue;
            String[] parts = line.split("\t", 5);
            if (parts.length < 5) continue;
            if (!knownEvent(parts[2]) || !knownAction(parts[3])) continue;
            rules.add(new Rule(unescape(parts[0]), parts[2], parts[3], unescape(parts[4]), "1".equals(parts[1])));
        }
        return rules;
    }

    public static boolean batteryLow(int level, boolean charging) {
        return level >= 0 && level < 20 && !charging;
    }

    public static boolean homeWifi(String ssid, String wanted) {
        if (ssid == null) return false;
        String name = ssid.trim();
        if (name.isEmpty() || "<unknown ssid>".equalsIgnoreCase(name) || "unknown".equalsIgnoreCase(name)) return false;
        if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) name = name.substring(1, name.length() - 1);
        if (name.isEmpty()) return false;
        if (wanted == null || wanted.trim().isEmpty()) return false;
        return name.equalsIgnoreCase(wanted.trim());
    }

    public static String appName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.endsWith(".app")) name = name.substring(0, name.length() - 4).trim();
        if (name.isEmpty() || name.length() > 80) return null;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..") || name.indexOf(':') >= 0) return null;
        return name;
    }

    public static Choice decide(boolean paused, boolean localExists, String localSha, boolean remoteDeleted, String remoteSha) {
        if (paused) return Choice.SKIP;
        if (remoteDeleted) {
            if (localExists && localSha != null && localSha.equalsIgnoreCase(remoteSha)) return Choice.DELETE;
            return Choice.KEEP;
        }
        if (remoteSha == null || remoteSha.isEmpty()) return Choice.SKIP;
        if (!localExists) return Choice.TAKE;
        if (localSha != null && localSha.equalsIgnoreCase(remoteSha)) return Choice.SKIP;
        return Choice.CONFLICT;
    }

    public static String conflictName(String path) {
        String safe = Features.safeRelative(path);
        if (safe == null) return null;
        int slash = safe.lastIndexOf('/');
        String dir = slash < 0 ? "" : safe.substring(0, slash + 1);
        String name = slash < 0 ? safe : safe.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return dir + name + " (BridgeLink conflict)";
        return dir + name.substring(0, dot) + " (BridgeLink conflict)" + name.substring(dot);
    }

    public static boolean partOk(String path, long offset, int length, long size) {
        if (Features.safeRelative(path) == null) return false;
        if (offset < 0 || length < 0 || size < 0 || size > FOLDER_FILE_CAP) return false;
        if (length > PART) return false;
        return offset <= size && offset + length <= size;
    }

    public static List<Entry> removed(List<Entry> before, List<Entry> now) {
        ArrayList<String> paths = new ArrayList<>();
        if (now != null) {
            for (Entry item : now) {
                if (item != null && !item.deleted) paths.add(item.path);
            }
        }
        ArrayList<Entry> gone = new ArrayList<>();
        if (before == null) return gone;
        for (Entry item : before) {
            if (item == null || item.deleted) continue;
            if (!paths.contains(item.path)) gone.add(item);
        }
        return gone;
    }

    public static String encodeIndex(List<Entry> items) {
        StringBuilder out = new StringBuilder();
        if (items == null) return "";
        int count = 0;
        for (Entry item : items) {
            if (item == null || item.deleted || item.path == null) continue;
            if (count >= FOLDER_CAP) break;
            if (out.length() > 0) out.append('\n');
            out.append(escape(item.path)).append('\t').append(item.size).append('\t')
                    .append(item.mtime).append('\t').append(item.sha == null ? "" : item.sha);
            count++;
        }
        return out.toString();
    }

    public static List<Entry> decodeIndex(String raw) {
        ArrayList<Entry> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return items;
        for (String line : raw.split("\n", -1)) {
            if (line.isEmpty() || items.size() >= FOLDER_CAP) continue;
            String[] parts = line.split("\t", 4);
            if (parts.length < 4) continue;
            String path = Features.safeRelative(unescape(parts[0]));
            if (path == null) continue;
            long size;
            long mtime;
            try {
                size = Long.parseLong(parts[1]);
                mtime = Long.parseLong(parts[2]);
            } catch (NumberFormatException error) {
                continue;
            }
            boolean deleted = parts[3].startsWith("deleted:");
            String sha = deleted ? parts[3].substring("deleted:".length()) : parts[3];
            items.add(new Entry(path, size, mtime, sha, deleted));
        }
        return items;
    }

    public static int[] pointer(String kind, int x, int y) {
        if (!knownPointer(kind)) return null;
        if ("laser".equals(kind)) return new int[] {x == 0 ? 0 : 1, 0};
        return new int[] {clamp(x), clamp(y)};
    }

    public static boolean knownPointer(String kind) {
        return "move".equals(kind) || "tap".equals(kind) || "right".equals(kind)
                || "scroll".equals(kind) || "drag".equals(kind) || "drag_end".equals(kind) || "key".equals(kind)
                || "slide".equals(kind) || "laser".equals(kind);
    }

    public static String keyName(String raw) {
        if (raw == null) return null;
        String text = raw;
        if (text.length() > 40) text = text.substring(0, 40);
        String lower = text.toLowerCase(Locale.US);
        switch (lower) {
            case "return", "delete", "escape", "tab", "left", "right", "up", "down", "space" -> {
                return lower;
            }
            default -> {
            }
        }
        if (text.isEmpty()) return null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c == 127) return null;
        }
        return text;
    }

    public static String slideKey(String which) {
        if ("next".equals(which)) return "right";
        if ("prev".equals(which) || "previous".equals(which)) return "left";
        return null;
    }

    private static boolean hasRule(List<Rule> rules, String id) {
        for (Rule rule : rules) if (id.equals(rule.id)) return true;
        return false;
    }

    private static ArrayList<Rule> copyRules(List<Rule> existing) {
        ArrayList<Rule> next = new ArrayList<>();
        if (existing != null) next.addAll(existing);
        return next;
    }

    private static int clamp(int value) {
        if (value < -4000) return -4000;
        if (value > 4000) return 4000;
        return value;
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", " ");
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                }
                if (next == '\\') {
                    out.append('\\');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
