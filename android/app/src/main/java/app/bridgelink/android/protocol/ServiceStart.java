package app.bridgelink.android.protocol;

/** Decide before a foreground service is promoted, so a stop or a missing grant does not crash. */
public final class ServiceStart {
    private ServiceStart() {}

    public static boolean stopFirst(String action, String stopAction) {
        return action != null && action.equals(stopAction);
    }

    public static boolean hasPayload(boolean present) {
        return present;
    }

    public static boolean cameraReady(boolean cameraGranted, boolean micNeeded, boolean micGranted) {
        return cameraGranted && (!micNeeded || micGranted);
    }
}
