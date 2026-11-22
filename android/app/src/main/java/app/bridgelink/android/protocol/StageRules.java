package app.bridgelink.android.protocol;

import java.util.Locale;

/** Decisions for the half-done features. No Android APIs, so tests can run them. */
public final class StageRules {
    public static final long OTP_CLEAR_MS = 60_000L;
    /** A clipboard image must fit in one frame after Base64. */
    public static final int IMAGE_CAP = 700_000;

    private StageRules() {}

    public static boolean fullBattery(int level, boolean alreadyAlerted) {
        return level == 100 && !alreadyAlerted;
    }

    /** 0 to 100. A size of zero stays at 0 so a bad offer does not look finished. */
    public static int progressPercent(long written, long size) {
        if (size <= 0 || written <= 0) return 0;
        if (written >= size) return 100;
        long percent = (written * 100L) / size;
        if (percent > 99) return 99;
        return (int) percent;
    }

    public static boolean appBlocked(String list, String app) {
        if (app == null || app.isEmpty() || list == null || list.isEmpty()) return false;
        for (String part : list.split(",")) {
            if (app.equals(part.trim())) return true;
        }
        return false;
    }

    /** A link opens by itself only when Always open links is on. The screen does not matter. */
    public static boolean openLinkNow(boolean screenOn, boolean alwaysAllow) {
        if (screenOn && !alwaysAllow) return false;
        return alwaysAllow;
    }

    /** A received file is never opened by itself. An apk has no Open button. */
    public static boolean canOfferOpen(String name) {
        if (name == null) return false;
        return !name.toLowerCase(Locale.US).endsWith(".apk");
    }

    public static String fileTitle(String name) {
        String shown = name == null || name.isBlank() ? "file" : name.trim();
        return "File from the Mac: " + shown;
    }

    /** text, image, skip, or empty. Sensitive text is never sent. */
    public static String clipKind(boolean hasText, boolean hasImage, boolean sensitive) {
        if (hasText && sensitive) return "skip";
        if (hasText) return "text";
        if (hasImage) return "image";
        return "empty";
    }

    /** Password-manager clips are never synced or stored. */
    public static boolean skipPasswordClip(boolean concealed, boolean transientType, boolean androidSensitive) {
        return concealed || transientType || androidSensitive;
    }

    /** Reconnect only after a real boot or an app update. */
    public static boolean bootReconnect(String action) {
        return "android.intent.action.BOOT_COMPLETED".equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action);
    }

    public static boolean imageFits(int bytes) {
        return bytes > 0 && bytes <= IMAGE_CAP;
    }

    public static String tidyName(String raw, String fallback) {
        if (raw == null) return fallback;
        String name = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (name.length() > 40) name = name.substring(0, 40).trim();
        if (name.isEmpty()) return fallback;
        return name;
    }

    /** Find-my-phone plays on the alarm stream, so the mute switch does not hide it. */
    public static boolean findUsesAlarmStream() {
        return true;
    }

    public static String bundleName(String folder) {
        String base = Transfers.sanitizeName(folder);
        if (base.toLowerCase(Locale.US).endsWith(".zip")) return base;
        return base + ".zip";
    }

    /** Replace one feature flag. Other flags stay. */
    public static String setFlag(String settings, String feature, boolean on) {
        StringBuilder out = new StringBuilder();
        if (settings != null) {
            for (String part : settings.split(",")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                if (feature.equals(part.substring(0, eq).trim())) continue;
                if (out.length() > 0) out.append(',');
                out.append(part.trim());
            }
        }
        if (out.length() > 0) out.append(',');
        out.append(feature).append(on ? "=1" : "=0");
        return out.toString();
    }
}
