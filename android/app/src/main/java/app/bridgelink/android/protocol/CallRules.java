package app.bridgelink.android.protocol;

/** Turns a phone call notification into the Mac banner text. No Android APIs. */
public final class CallRules {
    public static final class Call {
        public final String name;
        public final String number;
        public final String key;

        public Call(String name, String number, String key) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
            this.key = key == null ? "" : key;
        }
    }

    private CallRules() {}

    public static boolean isCall(String category) {
        return "call".equals(category);
    }

    /** Null when the notification is not a call. */
    public static Call fromNotice(String category, String title, String text, String key) {
        if (!isCall(category)) return null;
        String name = title == null ? "" : title.trim();
        String number = text == null ? "" : text.trim();
        if (!name.isEmpty() && name.equals(number)) number = "";
        return new Call(name, number, key);
    }

    public static String label(Call call) {
        if (call == null) return "Incoming call";
        if (!call.name.isEmpty()) return "Call from " + call.name;
        if (!call.number.isEmpty()) return "Call from " + call.number;
        return "Incoming call";
    }

    /** The ringing listener runs only while the feature is on and the link is up. */
    public static boolean listen(boolean featureOn, boolean linked) {
        return featureOn && linked;
    }
}
