package app.bridgelink.android.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StageSix {
    public static final int CHUNK = 24 * 1024;
    public static final int NORM = 10_000;
    public static final String MIRROR_STEPS =
            "On the phone, tap Mirror, then Share screen. Android asks once. Closing the Mac window stops the picture.";
    public static final String CONTROL_STEPS =
            "Turn on BridgeLink under Accessibility. On Android 13 and newer, open App info, the menu, and Allow restricted settings, then turn the service on. Back, Home, and Recents work after that. The Mac never unlocks the phone.";
    public static final String CAM_NOTE =
            "The free app shows the phone camera inside a BridgeLink window. You can share that window in a call. A camera that appears in Zoom, Meet, or FaceTime needs a paid Apple Developer ID, so that extension stays off until those secrets exist.";

    private StageSix() {}

    public static final class Picture {
        public final int width;
        public final int height;
        public final int bitrate;
        public final int fps;

        public Picture(int width, int height, int bitrate, int fps) {
            this.width = width;
            this.height = height;
            this.bitrate = bitrate;
            this.fps = fps;
        }
    }

    public static Picture quality(String name) {
        return switch (qualityName(name)) {
            case "low" -> new Picture(640, 360, 800_000, 15);
            case "high" -> new Picture(1920, 1080, 6_000_000, 30);
            default -> new Picture(1280, 720, 2_500_000, 24);
        };
    }

    /** Fit the phone's real screen inside the quality box. Both sides stay even. */
    public static Picture fit(Picture box, int screenWidth, int screenHeight) {
        if (box == null) return new Picture(2, 2, 800_000, 15);
        if (screenWidth < 2 || screenHeight < 2) return box;
        double scale = Math.min(box.width / (double) screenWidth, box.height / (double) screenHeight);
        int width = even((int) Math.round(screenWidth * scale));
        int height = even((int) Math.round(screenHeight * scale));
        width = Math.max(2, Math.min(width, even(box.width)));
        height = Math.max(2, Math.min(height, even(box.height)));
        int fps = box.fps;
        int rate = bitrateFor(width, height, fps);
        return new Picture(width, height, rate, fps);
    }

    /** About 0.1 bit per pixel per frame, kept between 1 and 8 Mbps. */
    public static int bitrateFor(int width, int height, int fps) {
        long pixels = (long) Math.max(1, width) * Math.max(1, height);
        long frames = Math.max(1, fps);
        long bits = (long) (pixels * frames * 0.1d);
        if (bits < 1_000_000L) bits = 1_000_000L;
        if (bits > 8_000_000L) bits = 8_000_000L;
        return (int) bits;
    }

    /** Height of the Mac mirror pane so it matches the picture shape. */
    public static int paneHeight(int picW, int picH, int maxWidth) {
        if (picW < 2 || picH < 2 || maxWidth < 2) return 360;
        int height = (int) Math.round(maxWidth * (picH / (double) picW));
        if (height < 120) return 120;
        if (height > 900) return 900;
        return height;
    }

    private static int even(int value) {
        int n = Math.max(2, value);
        return n - (n % 2);
    }

    public static String qualityName(String raw) {
        if ("low".equals(raw) || "high".equals(raw)) return raw;
        return "medium";
    }

    public static List<byte[]> chunks(byte[] data) {
        List<byte[]> out = new ArrayList<>();
        if (data == null || data.length == 0) return out;
        for (int i = 0; i < data.length; i += CHUNK) {
            int n = Math.min(CHUNK, data.length - i);
            out.add(Arrays.copyOfRange(data, i, i + n));
        }
        return out;
    }

    public static byte[] join(List<byte[]> parts) {
        int size = 0;
        if (parts != null) {
            for (byte[] part : parts) if (part != null) size += part.length;
        }
        byte[] out = new byte[size];
        int at = 0;
        if (parts != null) {
            for (byte[] part : parts) {
                if (part == null) continue;
                System.arraycopy(part, 0, out, at, part.length);
                at += part.length;
            }
        }
        return out;
    }

    /** Null while a frame is incomplete or the part order is wrong. Clears the bag when finished. */
    public static byte[] assemble(List<byte[]> have, int part, int parts, byte[] data) {
        if (have == null || data == null || part < 0 || parts < 1 || part >= parts) return null;
        if (part == 0) have.clear();
        if (part != have.size()) return null;
        have.add(data);
        if (have.size() < parts) return null;
        byte[] all = join(have);
        have.clear();
        return all;
    }

    public static int nalType(byte[] data) {
        int at = nalStart(data);
        if (at < 0 || at >= data.length) return -1;
        return data[at] & 0x1f;
    }

    public static byte[][] parameterSets(byte[] data) {
        byte[] sps = null;
        byte[] pps = null;
        for (byte[] nal : splitNals(data)) {
            if (nal.length == 0) continue;
            int type = nal[0] & 0x1f;
            if (type == 7) sps = nal;
            if (type == 8) pps = nal;
        }
        if (sps == null || pps == null) return null;
        return new byte[][] {sps, pps};
    }

    public static byte[] toAvcc(byte[] data) {
        if (data == null || data.length == 0 || data.length > 256 * 1024) return null;
        List<byte[]> nals = splitNals(data);
        if (nals.isEmpty()) {
            ByteBuffer out = ByteBuffer.allocate(4 + data.length);
            out.putInt(data.length);
            out.put(data);
            return out.array();
        }
        int size = 0;
        for (byte[] nal : nals) size += 4 + nal.length;
        ByteBuffer out = ByteBuffer.allocate(size);
        for (byte[] nal : nals) {
            out.putInt(nal.length);
            out.put(nal);
        }
        return out.array();
    }

    public static final class Tap {
        public final String kind;
        public final int x;
        public final int y;
        public final int x2;
        public final int y2;
        public final String text;

        public Tap(String kind, int x, int y, int x2, int y2, String text) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.x2 = x2;
            this.y2 = y2;
            this.text = text;
        }
    }

    public static Tap control(String kind, int x, int y, int x2, int y2, String text) {
        if (!"tap".equals(kind) && !"drag".equals(kind) && !"scroll".equals(kind)
                && !"text".equals(kind) && !"back".equals(kind) && !"home".equals(kind) && !"recents".equals(kind)) {
            return null;
        }
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > 80) clean = clean.substring(0, 80);
        if ("text".equals(kind) && clean.isEmpty()) return null;
        return new Tap(kind, clamp(x), clamp(y), clamp(x2), clamp(y2), clean);
    }

    /** Window point onto the phone picture, 0 to 10000. Null means the click is in the bars. */
    public static int[] picturePoint(int viewW, int viewH, int picW, int picH, int px, int py) {
        if (viewW <= 0 || viewH <= 0 || picW <= 0 || picH <= 0) return null;
        if (px < 0 || py < 0 || px > viewW || py > viewH) return null;
        long scaleW = (long) viewW * 1000L / picW;
        long scaleH = (long) viewH * 1000L / picH;
        long scale = Math.min(scaleW, scaleH);
        if (scale <= 0) return null;
        long drawnW = (long) picW * scale / 1000L;
        long drawnH = (long) picH * scale / 1000L;
        if (drawnW <= 0 || drawnH <= 0) return null;
        long left = (viewW - drawnW) / 2;
        long top = (viewH - drawnH) / 2;
        if (px < left || py < top || px > left + drawnW || py > top + drawnH) return null;
        int nx = (int) (((px - left) * NORM + drawnW / 2) / drawnW);
        int ny = (int) (((py - top) * NORM + drawnH / 2) / drawnH);
        return new int[] {clamp(nx), clamp(ny)};
    }

    public static int[] phonePixel(int phoneW, int phoneH, int x, int y) {
        if (phoneW <= 0 || phoneH <= 0) return new int[] {0, 0};
        int px = (clamp(x) * (phoneW - 1) + NORM / 2) / NORM;
        int py = (clamp(y) * (phoneH - 1) + NORM / 2) / NORM;
        return new int[] {px, py};
    }

    public static boolean stopWhenClosed(boolean windowOpen) {
        return !windowOpen;
    }

    public static String facing(String raw) {
        return "front".equals(raw) ? "front" : "back";
    }

    public static int zoom(int raw) {
        if (raw < 1) return 1;
        if (raw > 8) return 8;
        return raw;
    }

    public static boolean flashOn(String facing, boolean want) {
        return want && !"front".equals(facing(facing));
    }

    public static int jpegQuality(String quality) {
        return switch (qualityName(quality)) {
            case "low" -> 40;
            case "high" -> 75;
            default -> 55;
        };
    }

    /** Front camera adds the display turn, then the caller mirrors. Back camera subtracts it. */
    public static int cameraAngle(int sensor, int displayDegrees, boolean front) {
        int sensorDeg = mod360(sensor);
        int turn = mod360(displayDegrees);
        if (front) return mod360(sensorDeg + turn);
        return mod360(sensorDeg - turn);
    }

    /**
     * Rotate NV21 and optionally mirror it. 90 and 270 swap the sides.
     * The result is a new buffer. A bad size is returned unchanged.
     */
    public static byte[] rotateNv21(byte[] src, int width, int height, int degrees, boolean mirror) {
        int ySize = width * height;
        if (src == null || width < 2 || height < 2 || (width % 2) != 0 || (height % 2) != 0) return src;
        if (src.length < ySize + ySize / 2) return src;
        int angle = mod360(degrees);
        if (angle != 0 && angle != 90 && angle != 180 && angle != 270) return src;
        byte[] current = src;
        int w = width;
        int h = height;
        if (angle != 0) {
            current = turnNv21(current, w, h, angle);
            if (angle == 90 || angle == 270) {
                int swap = w;
                w = h;
                h = swap;
            }
        }
        if (mirror) current = mirrorNv21(current, w, h);
        return current;
    }

    private static int mod360(int degrees) {
        int n = degrees % 360;
        return n < 0 ? n + 360 : n;
    }

    private static byte[] turnNv21(byte[] src, int width, int height, int angle) {
        int ySize = width * height;
        int outW = (angle == 90 || angle == 270) ? height : width;
        int outH = (angle == 90 || angle == 270) ? width : height;
        byte[] dst = new byte[ySize + ySize / 2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int nx;
                int ny;
                if (angle == 90) {
                    nx = height - 1 - y;
                    ny = x;
                } else if (angle == 180) {
                    nx = width - 1 - x;
                    ny = height - 1 - y;
                } else {
                    nx = y;
                    ny = width - 1 - x;
                }
                dst[ny * outW + nx] = src[y * width + x];
            }
        }
        int cW = width / 2;
        int cH = height / 2;
        for (int y = 0; y < cH; y++) {
            for (int x = 0; x < cW; x++) {
                int nx;
                int ny;
                int row;
                if (angle == 90) {
                    nx = cH - 1 - y;
                    ny = x;
                    row = cH;
                } else if (angle == 180) {
                    nx = cW - 1 - x;
                    ny = cH - 1 - y;
                    row = cW;
                } else {
                    nx = y;
                    ny = cW - 1 - x;
                    row = cH;
                }
                int from = ySize + (y * cW + x) * 2;
                int to = outW * outH + (ny * row + nx) * 2;
                dst[to] = src[from];
                dst[to + 1] = src[from + 1];
            }
        }
        return dst;
    }

    private static byte[] mirrorNv21(byte[] src, int width, int height) {
        int ySize = width * height;
        byte[] dst = new byte[src.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dst[y * width + (width - 1 - x)] = src[y * width + x];
            }
        }
        int cW = width / 2;
        int cH = height / 2;
        for (int y = 0; y < cH; y++) {
            for (int x = 0; x < cW; x++) {
                int from = ySize + (y * cW + x) * 2;
                int to = ySize + (y * cW + (cW - 1 - x)) * 2;
                dst[to] = src[from];
                dst[to + 1] = src[from + 1];
            }
        }
        return dst;
    }

    public static boolean cameraExtension(boolean appleSecrets) {
        return appleSecrets;
    }

    public static final class Piece {
        public final int stream;
        public final int flags;
        public final int part;
        public final int parts;
        public final byte[] data;

        public Piece(int stream, int flags, int part, int parts, byte[] data) {
            this.stream = stream;
            this.flags = flags;
            this.part = part;
            this.parts = parts;
            this.data = data;
        }
    }

    public static byte[] pack(int stream, int flags, int part, int parts, byte[] data) {
        if (data == null) data = new byte[0];
        if (stream < 1 || stream > 3 || part < 0 || parts < 1 || part >= parts || data.length > 256 * 1024) return null;
        ByteBuffer out = ByteBuffer.allocate(9 + data.length);
        out.putInt(stream);
        out.put((byte) (flags & 0xff));
        out.putShort((short) part);
        out.putShort((short) parts);
        out.put(data);
        return out.array();
    }

    public static Piece unpack(byte[] raw) {
        if (raw == null || raw.length < 9) return null;
        ByteBuffer in = ByteBuffer.wrap(raw);
        int stream = in.getInt();
        int flags = in.get() & 0xff;
        int part = in.getShort() & 0xffff;
        int parts = in.getShort() & 0xffff;
        if (stream < 1 || stream > 3 || parts < 1 || part >= parts) return null;
        byte[] data = new byte[in.remaining()];
        in.get(data);
        return new Piece(stream, flags, part, parts, data);
    }

    /** Client or server websocket frame. A null mask means the server form. */
    public static byte[] wsFrame(int opcode, byte[] payload, byte[] mask) {
        if (payload == null) payload = new byte[0];
        if (payload.length > 1_048_576) throw new IllegalArgumentException("too big");
        boolean masked = mask != null;
        if (masked && mask.length != 4) throw new IllegalArgumentException("mask");
        int len = payload.length;
        int len7 = len < 126 ? len : (len <= 65535 ? 126 : 127);
        int extra = len7 == 126 ? 2 : (len7 == 127 ? 8 : 0);
        byte[] out = new byte[2 + extra + (masked ? 4 : 0) + len];
        out[0] = (byte) (0x80 | (opcode & 0x0f));
        out[1] = (byte) ((masked ? 0x80 : 0) | len7);
        int at = 2;
        if (len7 == 126) {
            out[at++] = (byte) ((len >> 8) & 0xff);
            out[at++] = (byte) (len & 0xff);
        } else if (len7 == 127) {
            long n = len;
            for (int i = 7; i >= 0; i--) out[at++] = (byte) ((n >> (8 * i)) & 0xff);
        }
        if (masked) {
            System.arraycopy(mask, 0, out, at, 4);
            at += 4;
            for (int i = 0; i < len; i++) out[at + i] = (byte) (payload[i] ^ mask[i & 3]);
        } else if (len > 0) {
            System.arraycopy(payload, 0, out, at, len);
        }
        return out;
    }

    /** -1 incomplete, 0 illegal, otherwise the frame size. */
    public static int wsSize(byte[] buf, int have) {
        if (buf == null || have < 2 || have > buf.length) return have < 2 ? -1 : 0;
        int len7 = buf[1] & 0x7f;
        boolean masked = (buf[1] & 0x80) != 0;
        int header = 2;
        long len;
        if (len7 < 126) len = len7;
        else if (len7 == 126) {
            if (have < 4) return -1;
            len = ((buf[2] & 0xffL) << 8) | (buf[3] & 0xffL);
            header = 4;
        } else {
            if (have < 10) return -1;
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (buf[2 + i] & 0xffL);
            header = 10;
        }
        if (len < 0 || len > 1_048_576) return 0;
        long total = header + (masked ? 4 : 0) + len;
        if (total > Integer.MAX_VALUE) return 0;
        if (have < total) return -1;
        return (int) total;
    }

    public static byte[] wsPayload(byte[] frame) {
        if (frame == null) return null;
        int size = wsSize(frame, frame.length);
        if (size != frame.length) return null;
        int len7 = frame[1] & 0x7f;
        boolean masked = (frame[1] & 0x80) != 0;
        int header = 2;
        int len;
        if (len7 < 126) len = len7;
        else if (len7 == 126) {
            len = ((frame[2] & 0xff) << 8) | (frame[3] & 0xff);
            header = 4;
        } else {
            long n = 0;
            for (int i = 0; i < 8; i++) n = (n << 8) | (frame[2 + i] & 0xffL);
            len = (int) n;
            header = 10;
        }
        int off = header + (masked ? 4 : 0);
        byte[] payload = new byte[len];
        if (masked) {
            for (int i = 0; i < len; i++) payload[i] = (byte) (frame[off + i] ^ frame[header + (i & 3)]);
        } else if (len > 0) {
            System.arraycopy(frame, off, payload, 0, len);
        }
        return payload;
    }

    private static int clamp(int value) {
        if (value < 0) return 0;
        if (value > NORM) return NORM;
        return value;
    }

    private static int nalStart(byte[] data) {
        int code = startCode(data, 0);
        if (code < 0) return -1;
        return code + codeSize(data, code);
    }

    private static List<byte[]> splitNals(byte[] data) {
        List<byte[]> nals = new ArrayList<>();
        if (data == null) return nals;
        List<Integer> codes = new ArrayList<>();
        int at = 0;
        while (at < data.length) {
            int code = startCode(data, at);
            if (code < 0) break;
            codes.add(code);
            at = code + codeSize(data, code);
        }
        for (int n = 0; n < codes.size(); n++) {
            int from = codes.get(n) + codeSize(data, codes.get(n));
            int end = n + 1 < codes.size() ? codes.get(n + 1) : data.length;
            if (from < end) nals.add(Arrays.copyOfRange(data, from, end));
        }
        return nals;
    }

    private static int startCode(byte[] data, int from) {
        for (int i = Math.max(0, from); i + 3 < data.length; i++) {
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1) return i;
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1) return i;
        }
        return -1;
    }

    private static int codeSize(byte[] data, int code) {
        if (code + 3 < data.length && data[code + 2] == 0 && data[code + 3] == 1) return 4;
        return 3;
    }
}
