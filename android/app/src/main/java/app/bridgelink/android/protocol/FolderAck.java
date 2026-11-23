package app.bridgelink.android.protocol;

/** Decides whether a stored file offset belongs to the send that is running now. */
public final class FolderAck {
    private FolderAck() {}

    /**
     * A stale ack from an older send is not used.
     * Returns the offset only when it was stored for this send.
     */
    public static long resume(Long offset, boolean fromThisSend) {
        if (!fromThisSend || offset == null || offset < 0) return -1L;
        return offset;
    }
}
