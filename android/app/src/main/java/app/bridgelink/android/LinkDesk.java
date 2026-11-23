package app.bridgelink.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicInteger;

import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.MediaControl;
import app.bridgelink.android.protocol.StageRules;
import app.bridgelink.android.protocol.Transfers;

/** Clipboard, alerts, files, ringing, and battery. The socket stays in LinkCore. */
final class LinkDesk {
    private final LinkCore core;
    private volatile String lastEcho = "";
    private String lastOtp = "";
    private long lastOtpAt;
    private long lastProgressAt;
    private int lastBattery = -1;
    private boolean lastCharging;
    private boolean sawBattery;
    private volatile String lastNow = "";
    private boolean macLowAlerted;
    private boolean macFullAlerted;
    private boolean sawDnd;
    private boolean lastDnd;
    private MediaPlayer player;
    private int savedAlarmVolume = -1;
    private Runnable ringStop;
    private Runnable flashBlink;
    private boolean torchOn;
    private String torchId = "";
    private final AtomicInteger alertIds = new AtomicInteger();
    private final AtomicInteger fileNoteIds = new AtomicInteger();

    LinkDesk(LinkCore core) {
        this.core = core;
    }

    void sendNotify(Intent intent) {
        core.outbox.submit(() -> {
            if (!core.paired) return;
            if (!Features.enabled(Prefs.open(core).getString("flags", ""), "notify", false)) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("title", safe(intent.getStringExtra(LinkCore.EXTRA_BODY)));
                payload.addProperty("app", safe(intent.getStringExtra("app")));
                payload.addProperty("key", safe(intent.getStringExtra("key")));
                payload.addProperty("reply", safe(intent.getStringExtra("reply")));
                payload.addProperty("body", safe(intent.getStringExtra("detail")));
                core.writeEnvelope(Envelope.make("notify", "android", payload));
                if (core.five != null) core.five.fire("notify", safe(intent.getStringExtra("app")));
                if ("1".equals(intent.getStringExtra("otp"))
                        && Features.enabled(Prefs.open(core).getString("flags", ""), "otp", false)) {
                    String code = Features.otp(safe(intent.getStringExtra(LinkCore.EXTRA_BODY)) + "\n" + safe(intent.getStringExtra("detail")));
                    if (code != null && otpFresh(code)) {
                        JsonObject otp = new JsonObject();
                        otp.addProperty("body", code);
                        core.writeEnvelope(Envelope.make("otp", "android", otp));
                    }
                }
                core.publish("Mirrored a notification", null);
            } catch (Exception error) {
                core.publish("Could not mirror a notification", null);
            }
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    void sendFile(String source) {
        sendFiles(source);
    }

    void sendFiles(String source) {
        core.fileCancel.set(false);
        new Thread(() -> {
            if (!core.paired || core.files == null) {
                core.publish("Connect and pair before sending", null);
                return;
            }
            try {
                core.files.sendMany(source, fileOut());
                core.publish("Sent the file", null);
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not send the file" : error.getMessage();
                core.publish(message, null);
            }
        }, "bridgelink-file").start();
    }

    private synchronized boolean otpFresh(String code) {
        long now = System.currentTimeMillis();
        if (!app.bridgelink.android.protocol.OtpGate.fresh(lastOtp, lastOtpAt, code, now)) return false;
        lastOtp = code;
        lastOtpAt = now;
        return true;
    }

    private FileBridge.Out fileOut() {
        return new FileBridge.Out() {
            @Override
            public void frame(String type, JsonObject payload) throws Exception {
                core.writeEnvelope(Envelope.make(type, "android", payload));
            }

            @Override
            public long have(String id) {
                return waitHave(id);
            }

            @Override
            public void progress(String name, int percent) {
                long now = System.currentTimeMillis();
                if (!app.bridgelink.android.protocol.ProgressPace.show(lastProgressAt, now, percent)) return;
                lastProgressAt = now;
                core.publish("Sending " + name + " — " + percent + "%", null);
            }

            @Override
            public boolean cancelled() {
                return core.fileCancel.get();
            }
        };
    }

    void sendNowPlaying() {
        core.outbox.submit(() -> {
            if (!core.paired) return;
            try {
                JsonObject now = MediaControl.snapshot(core);
                String key = now.get("title").getAsString() + "|" + now.get("playing").getAsString() + "|" + now.get("volume").getAsString();
                if (key.equals(lastNow)) return;
                lastNow = key;
                core.writeEnvelope(Envelope.make("media_now", "android", now));
            } catch (Exception ignored) {
            }
        });
    }

    private boolean passwordClip(ClipboardManager clipboard) {
        if (clipboard == null || android.os.Build.VERSION.SDK_INT < 33) return false;
        ClipDescription description = clipboard.getPrimaryClipDescription();
        if (description == null || description.getExtras() == null) return false;
        boolean marked = description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
        return StageRules.skipPasswordClip(false, false, marked);
    }

    private static String clipboardText(ClipboardManager clipboard) {
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) return "";
        ClipData clip = clipboard.getPrimaryClip();
        if (clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) return "";
        return clip.getItemAt(0).getText().toString();
    }

    void sendBattery() {
        try {
            Intent sticky = core.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky == null) return;
            int level = sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            int status = sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            int percent = scale <= 0 ? level : (int) (level * 100f / scale);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            if (!Features.batteryChanged(lastBattery, lastCharging, sawBattery, percent, charging)) {
                maybeSendDnd();
                return;
            }
            sawBattery = true;
            lastBattery = percent;
            lastCharging = charging;
            if (core.leave != null) core.leave.setCharging(charging);
            if (core.five != null) core.five.noteBattery(percent, charging);
            core.outbox.submit(() -> {
                if (!core.paired) return;
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("level", Integer.toString(percent));
                    payload.addProperty("charging", charging ? "1" : "0");
                    payload.addProperty("label", "phone");
                    core.writeEnvelope(Envelope.make("battery", "android", payload));
                    maybeSendDnd();
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private long waitHave(String id) {
        long end = System.currentTimeMillis() + 800;
        while (System.currentTimeMillis() < end) {
            Long value = core.fileHave.remove(id);
            if (value != null) return Math.max(0, value);
            try {
                Thread.sleep(40);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Long value = core.fileHave.remove(id);
        return value == null ? 0 : Math.max(0, value);
    }

    void sendLink(String raw) {
        new Thread(() -> {
            if (!core.paired || core.files == null) {
                core.publish("Connect and pair before sending", null);
                return;
            }
            try {
                core.files.sendLink(raw, (type, payload) -> core.writeEnvelope(Envelope.make(type, "android", payload)));
                core.publish("Sent the link", null);
            } catch (Exception error) {
                core.publish(error.getMessage() == null ? "Could not send the link" : error.getMessage(), null);
            }
        }, "bridgelink-link").start();
    }

    void openLink(String raw) {
        String url = Transfers.acceptUrl(raw);
        if (url == null) {
            core.publish("Refused a link that is not http or https", null);
            return;
        }
        boolean always = "1".equals(Prefs.open(core).getString("linksAlways", "0"));
        PowerManager power = (PowerManager) core.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = power != null && power.isInteractive();
        if (!StageRules.openLinkNow(screenOn, always)) {
            askLink(url);
            return;
        }
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            core.startActivity(view);
            core.publish("Opened a link from the Mac", null);
        } catch (Exception error) {
            core.publish("Could not open that link", null);
        }
    }

    void noteMacBattery(String levelText, boolean charging) {
        int level;
        try {
            level = Integer.parseInt(levelText);
        } catch (Exception error) {
            core.publish("Mac battery " + levelText + "%", null);
            return;
        }
        if (StageRules.fullBattery(level, macFullAlerted)) {
            macFullAlerted = true;
            alert("Mac is fully charged", "The Mac battery is at 100%.");
        }
        if (level < 100) macFullAlerted = false;
        if (Features.lowBattery(level, charging, macLowAlerted)) {
            macLowAlerted = true;
            alert("Mac battery is low", "The Mac battery is at " + level + "%.");
        }
        if (level > 15 || charging) macLowAlerted = false;
        core.publish("Mac battery " + level + "%", null);
    }

    void maybeSendDnd() {
        boolean dnd = core.manager().getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        if (sawDnd && dnd == lastDnd) return;
        sawDnd = true;
        lastDnd = dnd;
        if (!Features.enabled(Prefs.open(core).getString("flags", ""), "dnd", false)) return;
        core.outbox.submit(() -> {
            if (!core.paired) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("on", dnd ? "1" : "0");
                payload.addProperty("label", "phone");
                core.writeEnvelope(Envelope.make("dnd", "android", payload));
            } catch (Exception ignored) {
            }
        });
    }

    void applyMacDnd(boolean on) {
        if (!Features.enabled(Prefs.open(core).getString("flags", ""), "dnd", false)) {
            core.publish(on ? "Mac is in Do Not Disturb" : "Mac left Do Not Disturb", null);
            return;
        }
        try {
            if (core.manager().isNotificationPolicyAccessGranted()) {
                core.manager().setInterruptionFilter(on
                        ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        : NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        } catch (Exception ignored) {
        }
        core.publish(on ? "Mac is in Do Not Disturb" : "Mac left Do Not Disturb", null);
    }

    boolean acceptClipboard() {
        return Features.enabled(Prefs.open(core).getString("flags", ""), "clipboard", true);
    }

    void watchClipboard() {
        ClipboardManager clipboard = (ClipboardManager) core.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.addPrimaryClipChangedListener(() -> {
            if (passwordClip(clipboard)) return;
            ClipStore.addText(core, clipboardText(clipboard));
            if (!core.paired) return;
            if (!Features.enabled(Prefs.open(core).getString("flags", ""), "clipboard", false)) return;
            sendClipboardNow(false);
        });
    }

    void sendClipboardText(String value) {
        core.outbox.submit(() -> {
            if (!core.paired) {
                core.publish("Connect and pair before sending", null);
                return;
            }
            if (value == null || value.isEmpty()) {
                core.publish("Clipboard was empty", null);
                return;
            }
            if (Features.looksSensitive(value)) {
                core.publish("Skipped sensitive clipboard", null);
                return;
            }
            if (Features.isEcho(lastEcho, value)) {
                core.publish("Skipped a clipboard echo", null);
                return;
            }
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("body", value);
                core.writeEnvelope(Envelope.make("clipboard", "android", payload));
                ClipStore.addText(core, value);
                core.publish("Sent clipboard", null);
            } catch (Exception error) {
                core.publish("Send failed. Try again.", null);
            }
        });
    }

    private void sendClipboardNow(boolean manual) {
        core.outbox.submit(() -> {
            if (!core.paired) {
                if (manual) core.publish("Connect and pair before sending", null);
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) core.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) {
                if (manual) core.publish("Clipboard was empty", null);
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (passwordClip(clipboard)) {
                if (manual) core.publish("Skipped a password-manager clipboard", null);
                return;
            }
            CharSequence text = clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
            String value = text == null ? "" : text.toString();
            boolean image = clip.getItemCount() > 0 && clip.getItemAt(0).getUri() != null && value.isEmpty();
            String kind = StageRules.clipKind(!value.isEmpty(), image, Features.looksSensitive(value));
            if ("skip".equals(kind)) {
                core.publish("Skipped sensitive clipboard", null);
                return;
            }
            if ("text".equals(kind)) {
                if (Features.isEcho(lastEcho, value)) {
                    if (manual) core.publish("Skipped a clipboard echo", null);
                    return;
                }
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("body", value);
                    core.writeEnvelope(Envelope.make("clipboard", "android", payload));
                    ClipStore.addText(core, value);
                    core.publish("Sent clipboard", null);
                } catch (Exception error) {
                    core.publish("Send failed. Try again.", null);
                }
                return;
            }
            if ("image".equals(kind)) {
                sendClipboardImage(clip.getItemAt(0).getUri());
                return;
            }
            if (manual) core.publish("Clipboard was empty", null);
        });
    }

    private static byte[] readCap(java.io.InputStream in, int cap) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int count;
        while (out.size() <= cap && (count = in.read(buffer)) >= 0) {
            if (count > 0) out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private void sendClipboardImage(Uri uri) {
        try (java.io.InputStream in = core.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                core.publish("Could not read the clipboard image", null);
                return;
            }
            byte[] raw = readCap(in, StageRules.IMAGE_CAP + 1);
            if (!StageRules.imageFits(raw.length)) {
                core.publish("That clipboard image is too big", null);
                return;
            }
            String key = Features.echoKey(java.util.Base64.getEncoder().encodeToString(raw));
            if (key.equals(lastEcho)) return;
            lastEcho = key;
            JsonObject payload = new JsonObject();
            payload.addProperty("data", java.util.Base64.getEncoder().encodeToString(raw));
            core.writeEnvelope(Envelope.make("clipboard_image", "android", payload));
            core.publish("Sent a clipboard image", null);
        } catch (Exception error) {
            core.publish("Could not send the clipboard image", null);
        }
    }

    void acceptClipboardImage(String data) {
        if (!acceptClipboard()) return;
        byte[] raw;
        try {
            raw = java.util.Base64.getDecoder().decode(data == null ? "" : data);
        } catch (IllegalArgumentException error) {
            core.publish("Skipped a clipboard image", null);
            return;
        }
        if (!StageRules.imageFits(raw.length)) {
            core.publish("Skipped a clipboard image", null);
            return;
        }
        try {
            java.io.File dir = new java.io.File(core.getCacheDir(), "clips");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            java.io.File file = new java.io.File(dir, "clip.png");
            java.nio.file.Files.write(file.toPath(), raw);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(core, "app.bridgelink.android.files", file);
            lastEcho = Features.echoKey(data);
            ClipboardManager clipboard = (ClipboardManager) core.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newUri(core.getContentResolver(), "BridgeLink", uri));
            }
            core.publish("Clipboard image updated", null);
        } catch (Exception error) {
            core.publish("Could not save the clipboard image", null);
        }
    }

    void sendOtp(String code) {
        core.outbox.submit(() -> {
            if (!core.paired || code == null || code.isEmpty()) return;
            if (!otpFresh(code)) return;
            if (!Features.enabled(Prefs.open(core).getString("flags", ""), "otp", false)) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("body", code);
                core.writeEnvelope(Envelope.make("otp", "android", payload));
                if (core.five != null) core.five.fire("sms_code", code);
                core.publish("Copied a code to the Mac", null);
            } catch (Exception error) {
                core.publish("Could not copy the code", null);
            }
        });
    }

    void sendDismiss(String key) {
        core.outbox.submit(() -> {
            if (!core.paired || key == null || key.isEmpty()) return;
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("key", key);
                core.writeEnvelope(Envelope.make("notify_dismiss", "android", payload));
            } catch (Exception ignored) {
            }
        });
    }

    void copy(String text) {
        lastEcho = Features.echoKey(text == null ? "" : text);
        ClipboardManager clipboard = (ClipboardManager) core.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BridgeLink", text));
        }
        if (core.five != null) core.five.fire("clip", "");
    }

    void startRing(boolean flash, boolean vibrate) {
        stopRing();
        AudioManager audio = (AudioManager) core.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null) {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            } catch (RuntimeException ignored) {
            }
        }
        boolean buzz = vibrate;
        try {
            Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            MediaPlayer next = new MediaPlayer();
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            next.setDataSource(core, alarm);
            next.setLooping(true);
            next.prepare();
            next.start();
            player = next;
        } catch (Exception ignored) {
            buzz = true;
        }
        if (buzz) {
            Vibrator vibrator = (Vibrator) core.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 400}, 0));
        }
        if (flash) blinkTorch();
        Intent stop = new Intent(core, LinkService.class);
        stop.setAction(LinkCore.ACTION_STOP_RING);
        PendingIntent pending = PendingIntent.getService(core, 2, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification ring = new Notification.Builder(core, LinkCore.RING_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("BridgeLink is ringing")
                .setContentText("The Mac asked this phone to ring")
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", pending).build())
                .build();
        core.manager().notify(LinkCore.RING_ID, ring);
        ringStop = this::stopRing;
        core.main.postDelayed(ringStop, 120_000);
    }

    void stopRing() {
        if (ringStop != null) core.main.removeCallbacks(ringStop);
        ringStop = null;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        AudioManager audio = (AudioManager) core.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null && savedAlarmVolume >= 0) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
            } catch (RuntimeException ignored) {
            }
            savedAlarmVolume = -1;
        }
        Vibrator vibrator = (Vibrator) core.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.cancel();
        core.manager().cancel(LinkCore.RING_ID);
        if (flashBlink != null) core.main.removeCallbacks(flashBlink);
        flashBlink = null;
        setTorch(false);
    }

    private void blinkTorch() {
        flashBlink = new Runnable() {
            @Override
            public void run() {
                torchOn = !torchOn;
                setTorch(torchOn);
                core.main.postDelayed(this, 400);
            }
        };
        core.main.post(flashBlink);
    }

    private void setTorch(boolean on) {
        try {
            CameraManager cameras = (CameraManager) core.getSystemService(Context.CAMERA_SERVICE);
            if (cameras == null) return;
            if (torchId.isEmpty()) {
                String[] ids = cameras.getCameraIdList();
                if (ids.length == 0) return;
                torchId = ids[0];
            }
            cameras.setTorchMode(torchId, on);
            torchOn = on;
        } catch (Exception ignored) {
            torchOn = false;
        }
    }

    void askFile(String name, android.net.Uri uri) {
        String title = StageRules.fileTitle(name);
        int id = StageRules.fileNoteId(fileNoteIds.incrementAndGet());
        Notification.Builder builder = new Notification.Builder(core, LinkCore.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText("Saved in Downloads. It was not opened.")
                .setAutoCancel(true);
        Intent show = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.addAction(new Notification.Action.Builder(null, "Show in Downloads",
                PendingIntent.getActivity(core, id, show, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        if (uri != null && StageRules.canOfferOpen(name, fileMime(uri))) {
            Intent open = new Intent(Intent.ACTION_VIEW, uri);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pending = PendingIntent.getActivity(core, id + 50_000, open, flags);
            builder.setContentIntent(pending);
            builder.addAction(new Notification.Action.Builder(null, "Open", pending).build());
        }
        core.manager().notify(id, builder.build());
        core.publish(title, null);
    }

    /** The file type stored with the download, when MediaStore has one. */
    private String fileMime(android.net.Uri uri) {
        try (android.database.Cursor cursor = core.getContentResolver().query(
                uri,
                new String[]{android.provider.MediaStore.MediaColumns.MIME_TYPE},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                String mime = cursor.getString(0);
                if (mime != null && !mime.isEmpty()) return mime;
            }
        } catch (Exception ignored) {
        }
        try {
            String mime = core.getContentResolver().getType(uri);
            return mime == null ? "" : mime;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void askLink(String url) {
        core.publish("Link from the Mac is waiting", null);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification note = new Notification.Builder(core, LinkCore.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Link from the Mac")
                .setContentText("Tap Open. The link was not opened by itself.")
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Open",
                        PendingIntent.getActivity(core, 43, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build())
                .build();
        core.manager().notify(43, note);
    }

    void alert(String title, String body) {
        Notification note = new Notification.Builder(core, LinkCore.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(core, LinkCore.CHANNEL)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle(title)
                        .setContentText("Open BridgeLink")
                        .build())
                .setAutoCancel(true)
                .build();
        core.manager().notify(StageRules.alertId(alertIds.incrementAndGet()), note);
    }
}
