package app.bridgelink.android.protocol;

/** Ignore old relay_* messages. They are never sent. */
public final class OldMessages {
    private OldMessages() {}

    public static final String[] SAVED = {"relaySecret", "relayBook", "relayUrl"};

    public static boolean ignore(String type) {
        return "relay_key".equals(type)
                || "relay_set".equals(type)
                || "relay_use".equals(type)
                || "relay_auth".equals(type)
                || "relay_ok".equals(type);
    }
}
