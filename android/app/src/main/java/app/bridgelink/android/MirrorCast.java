package app.bridgelink.android;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.List;

import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.StageSix;

/** Hardware H.264 of the phone screen. The Mac decodes it. Closing the Mac window stops this. */
public final class MirrorCast {
    private MediaCodec codec;
    private Surface surface;
    private MediaProjection projection;
    private android.hardware.display.VirtualDisplay display;
    private Thread drain;
    private volatile boolean running;
    private volatile boolean stopping;

    public void start(Context context, MediaProjection mediaProjection, String quality) {
        stop();
        stopping = false;
        StageSix.Picture picture = StageSix.quality(quality);
        projection = mediaProjection;
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stop();
            }
        }, new Handler(Looper.getMainLooper()));
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", picture.width, picture.height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, picture.bitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, picture.fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec = MediaCodec.createEncoderByType("video/avc");
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = codec.createInputSurface();
            codec.start();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            display = projection.createVirtualDisplay(
                    "BridgeLink",
                    picture.width,
                    picture.height,
                    metrics.densityDpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
            );
            running = true;
            drain = new Thread(this::drain, "bridgelink-mirror");
            drain.start();
        } catch (Exception error) {
            LinkService.postStatus("This phone could not start the screen encoder.");
            stop();
        }
    }

    public void stop() {
        if (stopping) return;
        stopping = true;
        running = false;
        Thread thread = drain;
        drain = null;
        if (thread != null) thread.interrupt();
        if (display != null) {
            display.release();
            display = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            codec.release();
            codec = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }

    private void drain() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sentSets = false;
        while (running && codec != null) {
            int index;
            try {
                index = codec.dequeueOutputBuffer(info, 20_000);
            } catch (Exception error) {
                break;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !sentSets) {
                sentSets = sendSets(codec.getOutputFormat());
            }
            if (index < 0) continue;
            ByteBuffer buffer = codec.getOutputBuffer(index);
            if (buffer != null && info.size > 0) {
                byte[] data = new byte[info.size];
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                buffer.get(data);
                boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (config) {
                    sentSets = sendSets(data) || sentSets;
                } else {
                    int flags = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ? 1 : 0;
                    sendPieces(1, flags, data);
                }
            }
            codec.releaseOutputBuffer(index, false);
        }
    }

    private boolean sendSets(MediaFormat format) {
        ByteBuffer csd = format.getByteBuffer("csd-0");
        if (csd == null) return false;
        byte[] data = new byte[csd.remaining()];
        csd.get(data);
        return sendSets(data);
    }

    private boolean sendSets(byte[] data) {
        byte[][] sets = StageSix.parameterSets(data);
        if (sets == null) return false;
        sendPieces(1, 2, StageSix.toAvcc(sets[0]));
        sendPieces(1, 2, StageSix.toAvcc(sets[1]));
        return true;
    }

    private void sendPieces(int stream, int flags, byte[] data) {
        if (data == null || data.length == 0) return;
        List<byte[]> parts = StageSix.chunks(data);
        for (int i = 0; i < parts.size(); i++) {
            byte[] packed = StageSix.pack(stream, flags, i, parts.size(), parts.get(i));
            if (packed != null) LinkService.sendRaw(Frame.encode(Frame.BINARY, packed));
        }
    }
}
