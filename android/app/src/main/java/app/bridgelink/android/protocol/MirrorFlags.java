package app.bridgelink.android.protocol;

/** Notification mirroring changes only notify and otp. Other switches stay. */
public final class MirrorFlags {
    private MirrorFlags() {
    }

    public static String apply(String settings, boolean on) {
        return StageRules.setFlag(StageRules.setFlag(settings, "notify", on), "otp", on);
    }
}
