package app.bridgelink.android.protocol;

/** When an older phone may append to a half-saved download. */
public final class FileResume {
    private FileResume() {}

    public static boolean allow(long have, long from, long size) {
        return have > 0 && have == from && have <= size;
    }
}
