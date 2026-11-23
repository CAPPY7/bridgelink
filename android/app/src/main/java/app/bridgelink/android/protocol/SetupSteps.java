package app.bridgelink.android.protocol;

import java.util.ArrayList;
import java.util.List;

/** Which setup pages apply on a given Android version. */
public final class SetupSteps {
    private SetupSteps() {}

    public static List<String> forSdk(int sdk) {
        List<String> pages = new ArrayList<>();
        pages.add("pair");
        if (sdk >= 33) pages.add("notifications");
        pages.add("alerts");
        pages.add("battery");
        pages.add("texts");
        if (sdk >= 28) pages.add("decline");
        pages.add("photos");
        pages.add("dnd");
        pages.add("bluetooth");
        pages.add("accessibility");
        if (sdk >= 34) pages.add("fullscreen");
        return pages;
    }

    public static boolean applies(String step, int sdk) {
        return forSdk(sdk).contains(step);
    }

    public static String title(String step) {
        return switch (step == null ? "" : step) {
            case "pair" -> "Pair with the Mac";
            case "notifications" -> "Notifications";
            case "alerts" -> "Notification access";
            case "battery" -> "Keep the link";
            case "texts" -> "Texts and phone state";
            case "decline" -> "Decline calls from the Mac";
            case "photos" -> "Phone photos";
            case "dnd" -> "Do Not Disturb";
            case "bluetooth" -> "Leave-lock Bluetooth";
            case "accessibility" -> "Phone control";
            case "fullscreen" -> "Full-screen alerts";
            default -> "Setup";
        };
    }

    public static String why(String step) {
        return switch (step == null ? "" : step) {
            case "pair" -> "Scan the Mac QR so the two apps trust each other. Without this, nothing is sent.";
            case "notifications" -> "Android 13 and newer hide the connection notice until you allow notifications. The link can still run, but you will not see it.";
            case "alerts" -> "Mirroring, call banners, and Do Not Disturb need notification access. Without it, the Mac stays quiet.";
            case "battery" -> "Some phones kill BridgeLink to save battery. Allowing this keeps the Mac link up. You can skip it.";
            case "texts" -> "Texts and two SIMs need SMS and phone state. Call alerts also use phone state. Nothing is read until you turn those features on.";
            case "decline" -> "Decline from the Mac needs permission to end a call, and only after you turn call alerts on. You can still see the banner without it.";
            case "photos" -> "The Mac Photos page needs access to pictures you allow. BridgeLink does not scan photos in the background.";
            case "dnd" -> "Pausing phone notifications from the Mac needs Do Not Disturb access. Without it, the Mac says so instead of pretending it worked.";
            case "bluetooth" -> "Leave-lock is off until you turn it on. Bluetooth is used only while that switch is on.";
            case "accessibility" -> "Clicks in the Mac mirror window need the accessibility service. The phone is never unlocked by the Mac.";
            case "fullscreen" -> "Android 14 can hide the screen-share question unless full-screen alerts are allowed. The notification still waits if you skip this.";
            default -> "You can change this later in Settings.";
        };
    }
}
