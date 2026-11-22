package app.bridgelink.android.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rules for media, leave-lock, the inbox, and clipboard history.
 * No Android APIs, so the unit tests and the Mac port can match them.
 */
public final class StageFour {
    public static final int CLIP_CAP = 100;
    public static final int INBOX_CAP = 200;
    public static final int ART_CAP = 48_000;
    /** Advertised by the Mac. The phone measures how near this signal is. */
    public static final String BLE_UUID = "f1a0b4c0-6d3e-4a5b-8c7d-9e0f1a2b3c4d";

    private StageFour() {}

    public static final class Clip {
        public final String id;
        public final String text;
        public final boolean pinned;
        public final long at;

        public Clip(String id, String text, boolean pinned, long at) {
            this.id = id;
            this.text = text;
            this.pinned = pinned;
            this.at = at;
        }
    }

    public static final class Item {
        public final String id;
        public final String kind;
        public final String title;
        public final String detail;
        public final long at;

        public Item(String id, String kind, String title, String detail, long at) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.detail = detail;
            this.at = at;
        }
    }

    public static String clipId(String text, long now) {
        int hash = 0;
        String value = text == null ? "" : text;
        for (int i = 0; i < value.length(); i++) {
            hash = 31 * hash + value.charAt(i);
        }
        return now + "-" + Integer.toHexString(hash);
    }

    public static int clampVolume(int raw) {
        if (raw < 0) return 0;
        if (raw > 100) return 100;
        return raw;
    }

    public static int farSteps(int raw) {
        if (raw < 40) return 40;
        if (raw > 95) return 95;
        return raw;
    }

    public static int delaySeconds(int raw) {
        if (raw < 5) return 5;
        if (raw > 180) return 180;
        return raw;
    }

    /** Negative RSSI. A higher far number means the phone can be farther away. */
    public static int threshold(int far) {
        return -farSteps(far);
    }

    public static boolean artFits(int bytes) {
        return bytes > 0 && bytes <= ART_CAP;
    }

    /** Only "away" may lock the Mac. Unlock is never a real request. */
    public static boolean lockRequest(String reason) {
        return "away".equals(reason);
    }

    /**
     * rssi 0 means the Mac signal disappeared. heard is false until the phone
     * has seen the Mac once, so a missing radio does not lock the desk.
     */
    public static boolean shouldLock(boolean enabled, int rssi, boolean heard, int threshold,
                                      long awayMs, long delayMs, boolean charging, boolean atDesk,
                                      boolean alreadyLocked) {
        if (!enabled || !heard || alreadyLocked || charging || atDesk) return false;
        if (delayMs < 0 || awayMs < delayMs) return false;
        if (rssi == 0) return true;
        return rssi <= threshold;
    }

    public static long awayFor(long previous, int rssi, int threshold, long elapsed, boolean heard) {
        if (!heard) return 0;
        long step = elapsed;
        if (step < 0) step = 0;
        if (step > 60_000) step = 60_000;
        boolean away = rssi == 0 || rssi <= threshold;
        if (!away) return 0;
        long next = previous + step;
        return next < 0 ? Long.MAX_VALUE : next;
    }

    /** action, volume. Null when the button is not a real media command. */
    public static String[] splitCommand(String raw) {
        if (raw == null) return null;
        String action = raw.trim();
        String volume = "";
        int colon = action.indexOf(':');
        if (colon >= 0) {
            volume = action.substring(colon + 1).trim();
            action = action.substring(0, colon).trim();
        }
        if (Features.mediaAction(action) == null) return null;
        if (!volume.isEmpty()) {
            try {
                volume = Integer.toString(clampVolume(Integer.parseInt(volume)));
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return new String[] {action, volume};
    }

    /** on, far, delay seconds, at desk. Null when the text is not four numbers. */
    public static int[] parseSetup(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length != 4) return null;
        try {
            int on = Integer.parseInt(parts[0].trim()) == 0 ? 0 : 1;
            int far = farSteps(Integer.parseInt(parts[1].trim()));
            int delay = delaySeconds(Integer.parseInt(parts[2].trim()));
            int desk = Integer.parseInt(parts[3].trim()) == 0 ? 0 : 1;
            return new int[] {on, far, delay, desk};
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static String formatSetup(boolean on, int far, int delay, boolean desk) {
        return (on ? "1" : "0") + "," + farSteps(far) + "," + delaySeconds(delay) + "," + (desk ? "1" : "0");
    }

    public static String nowLine(String title, String artist, boolean playing) {
        String name = title == null ? "" : title.trim();
        String who = artist == null ? "" : artist.trim();
        if (name.isEmpty() && who.isEmpty()) return "Nothing is playing";
        if (name.isEmpty()) name = "Unknown song";
        String line = who.isEmpty() ? name : name + " - " + who;
        return (playing ? "Playing " : "Paused ") + line;
    }

    public static String fileTitle(String line) {
        if (line == null) return "File";
        String text = line.trim();
        if (text.startsWith("Saved ")) text = text.substring(6);
        int in = text.indexOf(" in ");
        if (in > 0) text = text.substring(0, in);
        return text.isEmpty() ? "File" : text;
    }

    public static final long CLIP_KEEP_MS = 24L * 60L * 60L * 1000L;

    /** History is stored only after the user turns it on. */
    public static boolean keepHistory(boolean turnedOn) {
        return turnedOn;
    }

    /** Drop copies older than the keep window. */
    public static List<Clip> dropOlderThan(List<Clip> existing, long now, long keepMs) {
        ArrayList<Clip> next = new ArrayList<>();
        if (existing == null) return next;
        for (Clip clip : existing) {
            if (keepMs > 0 && now - clip.at > keepMs) continue;
            next.add(clip);
        }
        return next;
    }

    public static List<Clip> add(List<Clip> existing, String text, boolean sensitive, long now) {
        List<Clip> base = existing == null ? List.of() : existing;
        if (text == null) return cap(base);
        String clean = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (clean.length() > 4000) clean = clean.substring(0, 4000);
        if (sensitive || !storable(clean)) return cap(base);
        boolean wasPin = false;
        ArrayList<Clip> rest = new ArrayList<>();
        for (Clip clip : base) {
            if (clip.text.equals(clean)) wasPin = clip.pinned;
            else rest.add(clip);
        }
        rest.add(0, new Clip(clipId(clean, now), clean, wasPin, now));
        return cap(rest);
    }

    public static List<Clip> pin(List<Clip> existing, String id, boolean on) {
        ArrayList<Clip> next = new ArrayList<>();
        if (existing != null) {
            for (Clip clip : existing) {
                if (id != null && id.equals(clip.id)) next.add(new Clip(clip.id, clip.text, on, clip.at));
                else next.add(clip);
            }
        }
        return cap(next);
    }

    public static List<Clip> delete(List<Clip> existing, String id) {
        ArrayList<Clip> next = new ArrayList<>();
        if (existing != null) {
            for (Clip clip : existing) {
                if (id == null || !id.equals(clip.id)) next.add(clip);
            }
        }
        return next;
    }

    public static List<Clip> search(List<Clip> existing, String query) {
        ArrayList<Clip> next = new ArrayList<>();
        if (existing == null) return next;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (Clip clip : existing) {
            if (q.isEmpty() || clip.text.toLowerCase(Locale.US).contains(q)) next.add(clip);
        }
        return next;
    }

    public static String encodeClips(List<Clip> clips) {
        StringBuilder out = new StringBuilder();
        if (clips == null) return "";
        for (Clip clip : clips) {
            if (out.length() > 0) out.append('\n');
            out.append(clip.id).append('\t')
                    .append(clip.pinned ? '1' : '0').append('\t')
                    .append(clip.at).append('\t')
                    .append(escape(clip.text));
        }
        return out.toString();
    }

    public static List<Clip> decodeClips(String raw) {
        ArrayList<Clip> clips = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return clips;
        for (String line : raw.split("\n", -1)) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 4);
            if (parts.length < 4) continue;
            long at;
            try {
                at = Long.parseLong(parts[2]);
            } catch (NumberFormatException error) {
                continue;
            }
            clips.add(new Clip(parts[0], unescape(parts[3]), "1".equals(parts[1]), at));
        }
        return clips;
    }

    public static List<Item> addInbox(List<Item> existing, String kind, String title, String detail,
                                       boolean sensitive, long now) {
        if (!"notify".equals(kind) && !"sms".equals(kind) && !"file".equals(kind)) {
            return existing == null ? List.of() : new ArrayList<>(existing);
        }
        String shownTitle = title == null || title.trim().isEmpty() ? "Item" : title.trim();
        if (shownTitle.length() > 120) shownTitle = shownTitle.substring(0, 120);
        String shown = detail == null ? "" : detail.trim();
        if (!shown.isEmpty() && (sensitive || !storable(shown) || looksSecret(shown))) shown = "Hidden";
        if (shown.length() > 240) shown = shown.substring(0, 240);
        ArrayList<Item> next = new ArrayList<>();
        next.add(new Item(clipId(kind + " " + shownTitle, now), kind, shownTitle, shown, now));
        if (existing != null) {
            for (Item item : existing) {
                if (next.size() >= INBOX_CAP) break;
                next.add(item);
            }
        }
        return next;
    }

    public static List<Item> filter(List<Item> items, String kind, String query) {
        ArrayList<Item> next = new ArrayList<>();
        if (items == null) return next;
        String want = kind == null || kind.isEmpty() ? "all" : kind;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (Item item : items) {
            if (!"all".equals(want) && !want.equals(item.kind)) continue;
            if (!q.isEmpty() && !(item.title + " " + item.detail).toLowerCase(Locale.US).contains(q)) continue;
            next.add(item);
        }
        return next;
    }

    public static String encodeInbox(List<Item> items) {
        StringBuilder out = new StringBuilder();
        if (items == null) return "";
        for (Item item : items) {
            if (out.length() > 0) out.append('\n');
            out.append(item.id).append('\t').append(item.kind).append('\t').append(item.at).append('\t')
                    .append(escape(item.title)).append('\t').append(escape(item.detail));
        }
        return out.toString();
    }

    public static List<Item> decodeInbox(String raw) {
        ArrayList<Item> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return items;
        for (String line : raw.split("\n", -1)) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 5);
            if (parts.length < 5) continue;
            long at;
            try {
                at = Long.parseLong(parts[2]);
            } catch (NumberFormatException error) {
                continue;
            }
            items.add(new Item(parts[0], parts[1], unescape(parts[3]), unescape(parts[4]), at));
        }
        return items;
    }

    private static boolean storable(String clean) {
        if (clean == null || clean.isEmpty()) return false;
        String compact = clean.replace(" ", "");
        if (compact.length() >= 4 && compact.length() <= 8) {
            boolean digits = true;
            for (int i = 0; i < compact.length(); i++) {
                if (!Character.isDigit(compact.charAt(i))) digits = false;
            }
            if (digits) return false;
        }
        return true;
    }

    private static boolean looksSecret(String text) {
        return text != null && text.toLowerCase(Locale.US).contains("password");
    }

    private static List<Clip> cap(List<Clip> items) {
        ArrayList<Clip> pinned = new ArrayList<>();
        ArrayList<Clip> rest = new ArrayList<>();
        for (Clip clip : items) {
            if (clip.pinned) pinned.add(clip);
            else rest.add(clip);
        }
        ArrayList<Clip> ordered = new ArrayList<>();
        ordered.addAll(pinned);
        ordered.addAll(rest);
        while (ordered.size() > CLIP_CAP) {
            int drop = -1;
            for (int i = ordered.size() - 1; i >= 0; i--) {
                if (!ordered.get(i).pinned) {
                    drop = i;
                    break;
                }
            }
            if (drop < 0) drop = ordered.size() - 1;
            ordered.remove(drop);
        }
        return ordered;
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", " ");
    }

    static String unescape(String text) {
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
