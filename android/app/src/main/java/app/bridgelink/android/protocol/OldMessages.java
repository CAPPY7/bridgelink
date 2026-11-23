package app.bridgelink.android.protocol;

/** Ignore old relay messages and old rule messages. They are never sent. */
public final class OldMessages {
    private OldMessages() {}

    public static final String[] SAVED = {"relaySecret", "relayBook", "relayUrl"};

    public static boolean ignoreSender(String from) {
        return "relay".equals(from);
    }

    public static boolean ignore(String type) {
        return "relay_key".equals(type)
                || "relay_set".equals(type)
                || "relay_use".equals(type)
                || "relay_auth".equals(type)
                || "relay_ok".equals(type)
                || "auto".equals(type)
                || "auto_rules".equals(type)
                || "open_app".equals(type);
    }
}
