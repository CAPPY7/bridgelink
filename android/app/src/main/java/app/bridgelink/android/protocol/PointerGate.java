package app.bridgelink.android.protocol;

/** One Accessibility explanation per session. Later pointer moves stay quiet. */
public final class PointerGate {
    private PointerGate() {
    }

    public static boolean shouldAlert(boolean alreadyShown, boolean trusted) {
        return !alreadyShown && !trusted;
    }
}
