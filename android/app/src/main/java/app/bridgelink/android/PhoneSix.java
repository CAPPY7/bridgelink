package app.bridgelink.android;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.RelaySecrets;
import app.bridgelink.android.protocol.StageSix;

/** Screen mirror, camera, touches, and the away relay. The link service only forwards. */
public final class PhoneSix {
    public interface Out {
        void frame(String type, JsonObject payload) throws Exception;

        void status(String line);

        void relayUp();

        void relayFrames(java.util.List<app.bridgelink.android.protocol.Frame> frames);
    }

    private final Context context;
    private final Out out;
    private final RelayDoor relay = new RelayDoor();
    public static volatile String want = "";

    public PhoneSix(Context context, Out out) {
        this.context = context.getApplicationContext();
        this.out = out;
    }

    public boolean known(String kind) {
        return "mirror_stop".equals(kind) || "cam_stop".equals(kind) || "relay_use".equals(kind);
    }

    public void command(String kind, String body) {
        if ("mirror_stop".equals(kind)) {
            want = "stop";
            context.stopService(new android.content.Intent(context, MirrorService.class));
            reply("mirror_stop", "");
            out.status("Stopped sharing the screen.");
            return;
        }
        if ("cam_stop".equals(kind)) {
            want = "stop-cam";
            context.stopService(new android.content.Intent(context, CamService.class));
            reply("cam_stop", "");
            out.status("Camera is off.");
            return;
        }
        if ("relay_use".equals(kind)) tryRelay();
    }

    public boolean handle(Envelope message) {
        switch (message.type) {
            case "mirror_start" -> {
                want = "mirror:" + StageSix.qualityName(message.text("quality"));
                out.status("Allow screen share");
                return true;
            }
            case "mirror_stop" -> {
                want = "stop";
                context.stopService(new android.content.Intent(context, MirrorService.class));
                out.status("The Mac closed the mirror.");
                return true;
            }
            case "cam_start" -> {
                int zoom = 1;
                try {
                    zoom = Integer.parseInt(message.text("zoom"));
                } catch (NumberFormatException ignored) {
                }
                want = "cam:" + StageSix.facing(message.text("facing")) + ":" + StageSix.zoom(zoom)
                        + ":" + ("1".equals(message.text("flash")) ? "1" : "0")
                        + ":" + ("1".equals(message.text("mic")) ? "1" : "0");
                out.status("Allow the camera");
                return true;
            }
            case "cam_stop" -> {
                want = "stop-cam";
                context.stopService(new android.content.Intent(context, CamService.class));
                out.status("The Mac closed the camera.");
                return true;
            }
            case "control" -> {
                StageSix.Tap tap = StageSix.control(
                        message.text("kind"),
                        number(message.text("x")),
                        number(message.text("y")),
                        number(message.text("x2")),
                        number(message.text("y2")),
                        message.text("text")
                );
                out.status(TouchGate.perform(context, tap));
                return true;
            }
            case "relay_key" -> {
                String secret = message.text("body");
                String device = message.text("deviceId");
                SharedPreferences prefs = Prefs.open(context);
                if (device.isEmpty()) device = prefs.getString("deviceId", "");
                if (secret.length() >= 16 && device != null && !device.isEmpty()) {
                    String book = RelaySecrets.put(prefs.getString("relayBook", ""), device, secret);
                    prefs.edit().putString("relayBook", book).putString("relaySecret", secret).apply();
                    out.status("Away from home is ready after you save the relay address.");
                }
                return true;
            }
            case "relay_set" -> {
                String url = message.text("url").trim();
                if (!StageSix.relayUrl(url)) {
                    out.status("The relay address must start with wss://");
                    return true;
                }
                Prefs.open(context).edit().putString("relayUrl", url).apply();
                out.status("Saved the relay address.");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean tryRelay() {
        SharedPreferences prefs = Prefs.open(context);
        String url = prefs.getString("relayUrl", "");
        String secret = prefs.getString("relaySecret", "");
        if (!StageSix.relayUrl(url) || secret == null || secret.length() < 16) {
            out.status("Pair on Wi-Fi once, then save a wss relay address on both devices.");
            return false;
        }
        relay.start(url, secret, new RelayDoor.Sink() {
            @Override
            public void frames(java.util.List<app.bridgelink.android.protocol.Frame> frames) {
                out.relayFrames(frames);
            }

            @Override
            public void up() {
                out.relayUp();
            }

            @Override
            public void down(String reason) {
                out.status(reason);
            }
        });
        return true;
    }

    public void stopRelay() {
        relay.stop();
    }

    public boolean relaySend(byte[] framed) {
        return relay.send(framed);
    }

    public boolean relayLive() {
        return relay.up();
    }

    public void reply(String type, String body) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("body", body == null ? "" : body);
            out.frame(type, payload);
        } catch (Exception error) {
            out.status("Could not answer the Mac.");
        }
    }

    private static int number(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception error) {
            return 0;
        }
    }
}
