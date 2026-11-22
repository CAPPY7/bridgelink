package app.bridgelink.android;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import app.bridgelink.android.protocol.Frame;
import app.bridgelink.android.protocol.StageSix;

/** Phone camera as JPEGs, plus optional mic audio. The Mac shows them in a window. */
public final class CamCast {
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private AudioRecord mic;
    private HandlerThread thread;
    private volatile boolean running;
    private long lastFrame;
    private int jpegRotation;
    private boolean jpegMirror;

    public void start(Context context, String facing, int zoom, boolean flash, boolean withMic) {
        stop();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            LinkService.postStatus("This phone has no camera.");
            return;
        }
        try {
            String id = choose(manager, StageSix.facing(facing));
            if (id == null) {
                LinkService.postStatus("That camera is not on this phone.");
                return;
            }
            CameraCharacteristics info = manager.getCameraCharacteristics(id);
            Integer sensor = info.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Integer lens = info.get(CameraCharacteristics.LENS_FACING);
            jpegMirror = lens != null && lens == CameraCharacteristics.LENS_FACING_FRONT;
            int degrees = sensor == null ? 0 : sensor;
            if (jpegMirror) degrees = (360 - degrees) % 360;
            jpegRotation = degrees;
            thread = new HandlerThread("bridgelink-cam");
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            reader = ImageReader.newInstance(960, 540, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(imageReader -> sendJpeg(imageReader, StageSix.jpegQuality("medium")), handler);
            running = true;
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    camera = device;
                    begin(device, info, StageSix.zoom(zoom), StageSix.flashOn(facing, flash), handler);
                }

                @Override
                public void onDisconnected(CameraDevice device) {
                    stop();
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    LinkService.postStatus("The camera stopped.");
                    stop();
                }
            }, handler);
            if (withMic) startMic();
        } catch (SecurityException error) {
            LinkService.postStatus("Camera was not allowed.");
            stop();
        } catch (Exception error) {
            LinkService.postStatus("The camera could not start.");
            stop();
        }
    }

    public void stop() {
        running = false;
        if (session != null) {
            session.close();
            session = null;
        }
        if (camera != null) {
            camera.close();
            camera = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (mic != null) {
            try {
                mic.stop();
            } catch (Exception ignored) {
            }
            mic.release();
            mic = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
    }

    private void begin(CameraDevice device, CameraCharacteristics info, int zoom, boolean flash, Handler handler) {
        try {
            Rect crop = crop(info, zoom);
            device.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession next) {
                    session = next;
                    try {
                        CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(reader.getSurface());
                        if (crop != null) request.set(CaptureRequest.SCALER_CROP_REGION, crop);
                        request.set(CaptureRequest.FLASH_MODE, flash
                                ? CaptureRequest.FLASH_MODE_TORCH
                                : CaptureRequest.FLASH_MODE_OFF);
                        next.setRepeatingRequest(request.build(), null, handler);
                    } catch (Exception error) {
                        LinkService.postStatus("The camera preview stopped.");
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession next) {
                    LinkService.postStatus("The camera preview was refused.");
                }
            }, handler);
        } catch (Exception error) {
            LinkService.postStatus("The camera preview stopped.");
        }
    }

    private void sendJpeg(ImageReader imageReader, int quality) {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null || !running) return;
            long now = System.currentTimeMillis();
            if (now - lastFrame < 100) return;
            lastFrame = now;
            byte[] nv21 = nv21(image);
            if (nv21 == null) return;
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), quality, out);
            byte[] jpeg = upright(out.toByteArray(), jpegRotation, jpegMirror, quality);
            if (jpeg.length > 400_000) return;
            List<byte[]> parts = StageSix.chunks(jpeg);
            for (int i = 0; i < parts.size(); i++) {
                byte[] packed = StageSix.pack(2, 0, i, parts.size(), parts.get(i));
                if (packed != null) LinkService.sendRaw(Frame.encode(Frame.BINARY, packed));
            }
        } catch (Exception ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private void startMic() {
        int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return;
        try {
            mic = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) return;
            mic.startRecording();
            Thread worker = new Thread(() -> {
                byte[] buf = new byte[640];
                while (running && mic != null) {
                    int n = mic.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    byte[] packed = StageSix.pack(3, 0, 0, 1, n == buf.length ? buf : java.util.Arrays.copyOf(buf, n));
                    if (packed != null) LinkService.sendRaw(Frame.encode(Frame.BINARY, packed));
                }
            }, "bridgelink-mic");
            worker.start();
        } catch (SecurityException error) {
            LinkService.postStatus("The microphone was not allowed. The picture still works.");
        }
    }

    private static String choose(CameraManager manager, String facing) throws Exception {
        int want = "front".equals(facing) ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            Integer lens = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (fallback == null) fallback = id;
            if (lens != null && lens == want) return id;
        }
        return fallback;
    }

    private static Rect crop(CameraCharacteristics info, int zoom) {
        Rect full = info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (full == null || zoom <= 1) return full;
        int width = full.width() / zoom;
        int height = full.height() / zoom;
        int x = full.centerX() - width / 2;
        int y = full.centerY() - height / 2;
        return new Rect(x, y, x + width, y + height);
    }

    private static byte[] upright(byte[] jpeg, int degrees, boolean mirror, int quality) {
        if (jpeg == null) return null;
        if ((degrees % 360) == 0 && !mirror) return jpeg;
        android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        if (src == null) return jpeg;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        if (mirror) matrix.postScale(-1, 1);
        if ((degrees % 360) != 0) matrix.postRotate(degrees);
        android.graphics.Bitmap turned = android.graphics.Bitmap.createBitmap(
                src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        turned.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out);
        if (turned != src) turned.recycle();
        src.recycle();
        return out.toByteArray();
    }

    private static byte[] nv21(Image image) {
        Image.Plane y = image.getPlanes()[0];
        Image.Plane u = image.getPlanes()[1];
        Image.Plane v = image.getPlanes()[2];
        ByteBuffer yb = y.getBuffer();
        ByteBuffer ub = u.getBuffer();
        ByteBuffer vb = v.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] out = new byte[width * height * 3 / 2];
        int pos = 0;
        int yRow = y.getRowStride();
        for (int row = 0; row < height; row++) {
            yb.position(row * yRow);
            yb.get(out, pos, width);
            pos += width;
        }
        int cRow = v.getRowStride();
        int cPix = v.getPixelStride();
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int offset = row * cRow + col * cPix;
                out[pos++] = vb.get(offset);
                out[pos++] = ub.get(offset);
            }
        }
        return out;
    }
}
