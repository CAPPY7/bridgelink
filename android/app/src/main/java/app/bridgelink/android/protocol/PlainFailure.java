package app.bridgelink.android.protocol;

/** A sentence we wrote. The screen shows line, never a raw exception. */
public final class PlainFailure extends Exception {
    public final String line;

    public PlainFailure(String line) {
        super("plain");
        this.line = line == null ? "Something failed. Try again." : line;
    }
}
