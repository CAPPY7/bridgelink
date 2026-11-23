package app.bridgelink.android.protocol;

/** The Mac switch shows what the phone reported, not what the Mac guessed. */
public final class DndFollow {
    private DndFollow() {}

    public static boolean shown(boolean guess, boolean phoneReply) {
        return phoneReply;
    }

    public static String needAccess() {
        return "Allow Do Not Disturb access on the phone. Open Settings, then Special app access, then Do Not Disturb access, and turn BridgeLink on.";
    }
}
