package app.bridgelink.android.protocol;

/** Android 13+ blocks notification access on a sideloaded app until this is allowed. */
public final class RestrictedHelp {
    public static final String[] STEPS = {
            "Open Settings.",
            "Tap Apps, then BridgeLink.",
            "Tap the three dots in the top corner.",
            "Tap Allow restricted settings, then confirm.",
            "Go back to Settings, then Notifications, then Notification access.",
            "Turn BridgeLink on."
    };

    private RestrictedHelp() {}

    /** AppOpsManager.MODE_ALLOWED is 0. Any other mode means the setting is still blocked. */
    public static boolean blocked(int sdk, int appOpsMode) {
        if (sdk < 33) return false;
        return appOpsMode != 0;
    }

    public static String stepsText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < STEPS.length; i++) {
            if (i > 0) builder.append('\n');
            builder.append(i + 1).append(". ").append(STEPS[i]);
        }
        return builder.toString();
    }
}
