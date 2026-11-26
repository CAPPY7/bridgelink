package app.bridgelink.android.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Length-prefixed frames. 1 byte type, 4 byte big-endian length, then payload. */
public final class Frame {
    public static final int JSON = 1;
    public static final int BINARY = 2;
    public static final int MAX_BYTES = 1024 * 1024;

    public final int type;
    public final byte[] payload;

    public Frame(int type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public static byte[] encode(int type, byte[] payload) {
        if (payload.length > MAX_BYTES) throw new IllegalArgumentException("Frame too large");
        ByteBuffer buffer = ByteBuffer.allocate(5 + payload.length);
        buffer.put((byte) type);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    public static final class Parser {
        private byte[] buf = new byte[64];
        private int read;
        private int write;

        public List<Frame> push(byte[] chunk) throws Exception {
            if (chunk != null && chunk.length > 0) {
                grow(chunk.length);
                System.arraycopy(chunk, 0, buf, write, chunk.length);
                write += chunk.length;
            }
            List<Frame> frames = new ArrayList<>();
            boolean completed = false;
            while (write - read >= 5) {
                int type = buf[read] & 0xff;
                int length = ByteBuffer.wrap(buf, read + 1, 4).getInt();
                if (length < 0 || length > MAX_BYTES) {
                    throw new IllegalArgumentException("Bad frame length");
                }
                if (write - read < 5 + length) break;
                byte[] payload = new byte[length];
                System.arraycopy(buf, read + 5, payload, 0, length);
                frames.add(new Frame(type, payload));
                read += 5 + length;
                completed = true;
            }
            if (completed) compact();
            if (write - read > MAX_BYTES + 5) throw new IllegalArgumentException("Buffer cap");
            return frames;
        }

        private void grow(int extra) {
            if (write + extra <= buf.length) return;
            int used = write - read;
            int need = used + extra;
            byte[] next = new byte[Math.max(need, buf.length * 2)];
            if (used > 0) System.arraycopy(buf, read, next, 0, used);
            buf = next;
            read = 0;
            write = used;
        }

        private void compact() {
            if (read == 0) return;
            int used = write - read;
            if (used > 0) System.arraycopy(buf, read, buf, 0, used);
            read = 0;
            write = used;
        }
    }
}
