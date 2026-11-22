package app.bridgelink.android.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Picture pieces are 24 KB. Dropping one piece breaks the frame, so each
 * lane keeps whole frames. Screen, camera, and mic each have their own lane.
 */
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

    private static final class Lane {
        final ArrayDeque<byte[][]> ready = new ArrayDeque<>();
        byte[][] building;
        int filled;
        boolean dropRest;
        int pieces;
    }

    private final Lane[] lanes = new Lane[] {null, new Lane(), new Lane(), new Lane()};

    public synchronized Result offer(byte[] framed, boolean key) {
        StageSix.Piece piece = pieceOf(framed);
        if (piece == null) return new Result(false, false);
        Lane lane = lanes[piece.stream];
        if (piece.part == 0) {
            lane.dropRest = false;
            lane.building = new byte[piece.parts][];
            lane.filled = 0;
        }
        if (lane.dropRest || lane.building == null || piece.part != lane.filled || piece.parts != lane.building.length) {
            lane.dropRest = true;
            lane.building = null;
            lane.filled = 0;
            return new Result(false, piece.stream == 1);
        }
        lane.building[lane.filled] = framed;
        lane.filled++;
        if (lane.filled < lane.building.length) return new Result(true, false);
        byte[][] done = lane.building;
        lane.building = null;
        lane.filled = 0;
        if (lane.ready.size() >= CAP) {
            if (!key) {
                return new Result(false, piece.stream == 1);
            }
            byte[][] old = lane.ready.pollFirst();
            if (old != null) lane.pieces -= old.length;
        }
        lane.ready.addLast(done);
        lane.pieces += done.length;
        return new Result(true, false);
    }

    public synchronized byte[] poll() {
        for (int stream = 1; stream <= 3; stream++) {
            Lane lane = lanes[stream];
            byte[][] frame = lane.ready.peekFirst();
            if (frame == null) continue;
            for (int i = 0; i < frame.length; i++) {
                if (frame[i] != null) {
                    byte[] next = frame[i];
                    frame[i] = null;
                    lane.pieces--;
                    boolean empty = true;
                    for (byte[] part : frame) if (part != null) empty = false;
                    if (empty) lane.ready.pollFirst();
                    return next;
                }
            }
            lane.ready.pollFirst();
        }
        return null;
    }

    public synchronized int size() {
        int n = 0;
        for (int stream = 1; stream <= 3; stream++) n += lanes[stream].pieces;
        return n;
    }

    public synchronized int size(int stream) {
        if (stream < 1 || stream > 3) return 0;
        return lanes[stream].pieces;
    }

    /** True for an H.264 key frame or parameter set inside a length-prefixed binary frame. */
    public static boolean keyFrame(byte[] framed) {
        if (framed == null || framed.length < 10 || (framed[0] & 0xff) != Frame.BINARY) return false;
        int flags = framed[9] & 0xff;
        return (flags & 1) != 0 || (flags & 2) != 0;
    }

    private static StageSix.Piece pieceOf(byte[] framed) {
        if (framed == null || framed.length < 14 || (framed[0] & 0xff) != Frame.BINARY) return null;
        int length = ByteBuffer.wrap(framed, 1, 4).getInt();
        if (length < 9 || framed.length < 5 + length) return null;
        return StageSix.unpack(Arrays.copyOfRange(framed, 5, 5 + length));
    }
}
