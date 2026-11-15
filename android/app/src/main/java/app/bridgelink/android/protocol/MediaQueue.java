package app.bridgelink.android.protocol;

import java.util.ArrayDeque;

/** A short line for picture frames. When it is full, extra non-key frames are dropped. */
public final class MediaQueue {
    public static final int CAP = 3;

    public static final class Result {
        public final boolean kept;
        public final boolean askKey;

        public Result(boolean kept, boolean askKey) {
            this.kept = kept;
            this.askKey = askKey;
        }
    }

    private final ArrayDeque<byte[]> frames = new ArrayDeque<>();
    private boolean waitingForKey;

    public synchronized Result offer(byte[] frame, boolean key) {
        if (frame == null) return new Result(false, waitingForKey);
        if (frames.size() >= CAP) {
            if (!key) {
                waitingForKey = true;
                return new Result(false, true);
            }
            while (frames.size() >= CAP) frames.pollFirst();
        }
        frames.addLast(frame);
        if (key) waitingForKey = false;
        return new Result(true, false);
    }

    public synchronized byte[] poll() {
        return frames.pollFirst();
    }

    public synchronized int size() {
        return frames.size();
    }

    /** True for an H.264 key frame or parameter set inside a length-prefixed binary frame. */
    public static boolean keyFrame(byte[] framed) {
        if (framed == null || framed.length < 10 || (framed[0] & 0xff) != Frame.BINARY) return false;
        int flags = framed[9] & 0xff;
        return (flags & 1) != 0 || (flags & 2) != 0;
    }
}
