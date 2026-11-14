package app.bridgelink.android.protocol;

import java.io.ByteArrayOutputStream;
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
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        public List<Frame> push(byte[] chunk) throws Exception {
            pending.write(chunk);
            byte[] data = pending.toByteArray();
            List<Frame> frames = new ArrayList<>();
            int offset = 0;
            while (data.length - offset >= 5) {
                int type = data[offset] & 0xff;
                int length = ByteBuffer.wrap(data, offset + 1, 4).getInt();
                if (length < 0 || length > MAX_BYTES) {
                    throw new IllegalArgumentException("Bad frame length");
                }
                if (data.length - offset < 5 + length) break;
                byte[] payload = new byte[length];
                System.arraycopy(data, offset + 5, payload, 0, length);
                frames.add(new Frame(type, payload));
                offset += 5 + length;
            }
            pending.reset();
            if (offset < data.length) pending.write(data, offset, data.length - offset);
            if (pending.size() > MAX_BYTES + 5) throw new IllegalArgumentException("Buffer cap");
            return frames;
        }
    }
}
