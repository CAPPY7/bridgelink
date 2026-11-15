package app.bridgelink.android;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Base64;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.StageFour;

/** Phone music, through the notification listener. Art is a small picture when it fits. */
public final class MediaControl {
    public static final class Result {
        public final String status;
        public final JsonObject reply;

        public Result(String status, JsonObject reply) {
            this.status = status;
            this.reply = reply;
        }
    }

    private MediaControl() {}

    public static Result fromMac(Context context, String action, String volumeText) {
        String verb = Features.mediaAction(action);
        if (verb == null) return new Result("That media button is not supported", null);
        if ("volume".equals(verb)) {
            int vol;
            try {
                vol = StageFour.clampVolume(Integer.parseInt(volumeText == null ? "" : volumeText.trim()));
            } catch (NumberFormatException error) {
                return new Result("Volume needs a number from 0 to 100", null);
            }
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return new Result("This phone has no volume control", null);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, max <= 0 ? 0 : (vol * max) / 100, 0);
            return new Result(StageFour.nowLine(title(context), artist(context), playing(context)), snapshot(context));
        }
        MediaController controller = session(context);
        if (controller == null) {
            return new Result("No music app is open. Turn on notification access, then start music.", null);
        }
        MediaController.TransportControls controls = controller.getTransportControls();
        switch (verb) {
            case "play" -> controls.play();
            case "pause" -> controls.pause();
            case "next" -> controls.skipToNext();
            case "previous" -> controls.skipToPrevious();
            case "seek" -> controls.seekTo(0);
            default -> {
                return new Result("That media button is not supported", null);
            }
        }
        return new Result("Media command from the Mac", snapshot(context));
    }

    public static JsonObject snapshot(Context context) {
        MediaController controller = session(context);
        JsonObject payload = new JsonObject();
        String title = "";
        String artist = "";
        boolean playing = false;
        if (controller != null) {
            MediaMetadata meta = controller.getMetadata();
            if (meta != null) {
                title = text(meta, MediaMetadata.METADATA_KEY_TITLE);
                artist = text(meta, MediaMetadata.METADATA_KEY_ARTIST);
                putArt(payload, meta);
            }
            PlaybackState state = controller.getPlaybackState();
            playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }
        payload.addProperty("title", title);
        payload.addProperty("artist", artist);
        payload.addProperty("playing", playing ? "1" : "0");
        payload.addProperty("volume", Integer.toString(volume(context)));
        return payload;
    }

    private static String title(Context context) {
        MediaController controller = session(context);
        if (controller == null || controller.getMetadata() == null) return "";
        return text(controller.getMetadata(), MediaMetadata.METADATA_KEY_TITLE);
    }

    private static String artist(Context context) {
        MediaController controller = session(context);
        if (controller == null || controller.getMetadata() == null) return "";
        return text(controller.getMetadata(), MediaMetadata.METADATA_KEY_ARTIST);
    }

    private static boolean playing(Context context) {
        MediaController controller = session(context);
        if (controller == null || controller.getPlaybackState() == null) return false;
        return controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
    }

    private static int volume(Context context) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return 0;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (max <= 0) return 0;
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / max;
    }

    private static MediaController session(Context context) {
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return null;
            List<MediaController> sessions = manager.getActiveSessions(new ComponentName(context, NotifyMirror.class));
            if (sessions == null || sessions.isEmpty()) return null;
            return sessions.get(0);
        } catch (SecurityException error) {
            return null;
        }
    }

    private static void putArt(JsonObject payload, MediaMetadata meta) {
        Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) return;
        Bitmap small = Bitmap.createScaledBitmap(art, 64, 64, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 50, out);
        if (!StageFour.artFits(out.size())) return;
        payload.addProperty("art", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
    }

    private static String text(MediaMetadata meta, String key) {
        CharSequence value = meta.getText(key);
        return value == null ? "" : value.toString();
    }
}
