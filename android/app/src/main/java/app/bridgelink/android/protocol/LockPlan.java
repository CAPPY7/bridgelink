package app.bridgelink.android.protocol;

/** Sleep the display. Do not write Mac system settings. */
public final class LockPlan {
    private LockPlan() {
    }

    public static String[] command() {
        return new String[]{"/usr/bin/pmset", "displaysleepnow"};
    }

    public static boolean writesSettings() {
        return false;
    }

    public static String note(boolean passwordNow) {
        if (passwordNow) return "Locked the screen";
        return "Locked the screen. Turn on Require password immediately in Lock Screen settings if the Mac did not ask.";
    }
}
