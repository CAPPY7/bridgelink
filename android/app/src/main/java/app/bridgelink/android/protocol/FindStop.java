package app.bridgelink.android.protocol;

/** Stopping the ring tells the Mac as well as this phone. */
public final class FindStop {
    private FindStop() {
    }

    public static boolean tellsMac() {
        return true;
    }
}
