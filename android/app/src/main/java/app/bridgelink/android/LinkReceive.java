package app.bridgelink.android;

import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.LinkFailure;
import app.bridgelink.android.protocol.LinkState;
import app.bridgelink.android.protocol.MacList;
import app.bridgelink.android.protocol.MacTokens;
import app.bridgelink.android.protocol.Sas;
import app.bridgelink.android.protocol.SecondPhone;
import app.bridgelink.android.protocol.StageFour;

/** Incoming Mac messages. The socket stays in LinkCore. */
final class LinkReceive {
    private final LinkCore core;

    LinkReceive(LinkCore core) {
        this.core = core;
    }

    void onLine(String line) {
        try {
            Envelope message = Envelope.parse(line);
            if (app.bridgelink.android.protocol.OldMessages.ignore(message.type)) return;
            if (!message.id.isEmpty() && !core.seen.firstTime(message.id)) return;
            String body = message.body();
            switch (message.type) {
                case "paired" -> {
                    core.paired = true;
                    core.linkState.paired();
                    String issued = message.text("token");
                    if (issued.length() >= 16) {
                        SharedPreferences prefs = Prefs.open(core);
                        SharedPreferences.Editor editor = prefs.edit().putString("token", issued);
                        String fp = core.sessionFp == null || core.sessionFp.isEmpty() ? prefs.getString("fp", "") : core.sessionFp;
                        if (fp != null && !fp.isEmpty()) editor.putString("fp", fp);
                        String device = message.text("deviceId");
                        if (!device.isEmpty()) editor.putString("deviceId", device);
                        String macs = prefs.getString("macs", "[]");
                        editor.putString("macs", MacList.add(macs, "Mac", core.lastHost, core.lastPort, fp, device));
                        if (fp != null && !fp.isEmpty()) {
                            editor.putString("macTokens", MacTokens.put(prefs.getString("macTokens", ""), fp, issued));
                        }
                        editor.putString("host", core.lastHost);
                        editor.remove("pendingFp").remove("pin").commit();
                        if (prefs.getString("token", "").length() < 16) {
                            core.markUnpaired();
                            core.linkState.failFatal(Prefs.WARNING);
                            core.publish(Prefs.WARNING, null);
                            return;
                        }
                    }
                    core.publish("Paired with the Mac", "Mac accepted the phone");
                    core.main.removeCallbacks(core.heartbeat);
                    core.main.postDelayed(core.heartbeat, 20_000);
                    core.desk.sendBattery();
                    core.desk.syncCalls();
                    if (core.five != null) core.five.onPaired();
                }
                case "sas" -> {
                    String commit = message.text("commit");
                    if (commit.isEmpty() || core.sasPhoneNonce.isEmpty()) {
                        core.linkState.failFatal("The Mac code did not match. Connect again.");
                        core.publish(core.linkState.fatal, null);
                        core.generation.incrementAndGet();
                        core.closeSocket();
                    } else {
                        core.macCommit = commit;
                        JsonObject reveal = new JsonObject();
                        reveal.addProperty("nonce", core.sasPhoneNonce);
                        core.writeEnvelope(Envelope.make("sas_reveal", "android", reveal));
                        core.publish("Checking the match code…", null);
                    }
                }
                case "sas_reveal" -> {
                    String revealed = message.text("nonce");
                    if (!Sas.revealMatches(core.macCommit, revealed)) {
                        core.linkState.failFatal("The Mac code did not match. Connect again.");
                        core.publish(core.linkState.fatal, null);
                        core.generation.incrementAndGet();
                        core.closeSocket();
                    } else {
                        core.matchCode = Sas.code(core.sessionFp, core.sasPhoneNonce, revealed);
                        core.publish("Match code " + core.matchCode + ". Tap Codes match on the phone and the Mac.", null);
                    }
                }
                case "pong" -> core.lastRx = System.currentTimeMillis();
                case "ping" -> {
                    try {
                        core.writeEnvelope(Envelope.make("pong", "android", new JsonObject()));
                    } catch (Exception ignored) {
                    }
                }
                case "battery" -> {
                    if ("mac".equals(message.text("label"))) {
                        core.desk.noteMacBattery(message.text("level"), "1".equals(message.text("charging")));
                    }
                }
                case "forget" -> {
                    SharedPreferences prefs = Prefs.open(core);
                    String fp = prefs.getString("fp", "");
                    if (fp == null || fp.isEmpty()) fp = core.sessionFp == null ? "" : core.sessionFp;
                    String tokens = app.bridgelink.android.protocol.MacSwitch.forget(prefs.getString("macTokens", ""), fp);
                    String macs = MacList.removeHost(prefs.getString("macs", "[]"), core.lastHost);
                    prefs.edit().remove("token").remove("fp").remove("pendingFp").remove("deviceId")
                            .putString("macTokens", tokens)
                            .putString("macs", macs).apply();
                    core.linkState.failFatal("The Mac forgot this phone");
                    core.publish("The Mac forgot this phone", null);
                    core.generation.incrementAndGet();
                    core.closeSocket();
                }
                case "error" -> {
                    String code = message.code();
                    if ("busy".equals(code)) {
                        core.linkState.holdUntil = SecondPhone.until(System.currentTimeMillis());
                        core.publish("Another phone is connected. This phone will wait 5 minutes.", null);
                        core.closeSocket();
                    } else {
                    LinkFailure.Kind kind = LinkFailure.fromCode(code);
                    boolean fatal = LinkState.isFatalCode(code);
                    String shown = fatal ? LinkFailure.message(kind) : "The Mac rejected that message.";
                    if (fatal) {
                        core.linkState.failFatal(shown);
                        if (kind == LinkFailure.Kind.TOKEN_REJECTED || kind == LinkFailure.Kind.WRONG_CODE) {
                            Prefs.open(core).edit().remove("token").apply();
                        }
                        core.generation.incrementAndGet();
                        core.closeSocket();
                    }
                    core.publish(shown, null);
                    }
                }
                case "text" -> {
                    core.publish("Note from the Mac", body);
                    core.desk.alert("Note from the Mac", body);
                }
                case "clipboard" -> {
                    if (!core.desk.acceptClipboard()) break;
                    core.desk.copy(body);
                    core.publish("Clipboard updated from the Mac", null);
                    core.desk.alert("Clipboard from the Mac", "Clipboard updated");
                }
                case "clipboard_image" -> core.desk.acceptClipboardImage(message.text("data"));
                case "notify_reply" -> {
                    boolean sent = NotifyMirror.reply(message.text("key"), message.text("body"));
                    core.publish(sent ? "Sent a reply" : "That notification does not accept a reply", null);
                }
                case "notify_dismiss" -> {
                    NotifyMirror.dismiss(message.text("key"));
                    core.publish("Dismissed a notification", null);
                }
                case "dnd" -> core.desk.applyMacDnd("1".equals(message.text("on")));
                case "ring" -> {
                    core.publish("Mac is ringing this phone", null);
                    core.desk.startRing("1".equals(message.text("flash")), !"0".equals(message.text("vibrate")));
                }
                case "find_stop" -> {
                    core.desk.stopRing();
                    core.publish("Ring stopped", null);
                }
                case "link" -> core.desk.openLink(message.text("url"));
                case "file_begin" -> {
                    String id = message.text("id");
                    long from = longText(message, "from");
                    if ("folder".equals(message.text("dest")) && core.five != null) {
                        if (core.five.takeFolderFile(id, message.text("path"), longText(message, "size"))) {
                            JsonObject reply = new JsonObject();
                            reply.addProperty("id", id);
                            reply.addProperty("offset", "0");
                            core.writeEnvelope(Envelope.make("file_have", "android", reply));
                            core.publish("Receiving a shared file", null);
                        } else {
                            JsonObject cancel = new JsonObject();
                            cancel.addProperty("id", id);
                            core.writeEnvelope(Envelope.make("file_cancel", "android", cancel));
                        }
                    } else {
                    String status = core.files.begin(id, message.text("name"), longText(message, "size"), Math.max(0, from));
                    JsonObject reply = new JsonObject();
                    reply.addProperty("id", id);
                    reply.addProperty("offset", Long.toString(core.files.offered(id)));
                    core.writeEnvelope(Envelope.make("file_have", "android", reply));
                    if (status != null) core.publish(status, null);
                    }
                }
                case "file_have" -> core.acceptFileHave(message.text("id"), longText(message, "offset"));
                case "file_chunk" -> {
                    if (core.five != null && core.five.hasFolderFile(message.text("id"))) {
                        String status = core.five.chunkFolderFile(message.text("id"), longText(message, "offset"), message.text("data"));
                        if (status != null) core.publish(status, null);
                    } else {
                    String status = core.files.chunk(message.text("id"), longText(message, "offset"), message.text("data"));
                    if (status != null) core.publish(status, null);
                    }
                }
                case "file_end" -> {
                    if (core.five != null && core.five.hasFolderFile(message.text("id"))) {
                        core.publish(core.five.finishFolderFile(message.text("id"), message.text("sha256")), null);
                    } else {
                    String saved = core.files.finish(message.text("id"), message.text("sha256"));
                    core.publish(saved, null);
                    if (saved != null && saved.startsWith("Saved")) {
                        core.desk.askFile(core.files.lastName(), core.files.lastUri());
                        String uri = core.files.lastUri() == null ? "" : core.files.lastUri().toString();
                        HistoryDatabase.record(core, "file", "in", core.files.lastName(), uri, 0, "done");
                    }
                    }
                }
                case "file_cancel" -> {
                    if (core.five != null && core.five.hasFolderFile(message.text("id"))) core.five.dropFolderFile(message.text("id"));
                    core.files.cancel(message.text("id"));
                    core.publish("File cancelled", null);
                }
                case "media" -> {
                    MediaControl.Result result = MediaControl.fromMac(core, message.text("action"), message.text("volume"));
                    core.publish(result.status, null);
                    if (result.reply != null) core.writeEnvelope(Envelope.make("media_now", "android", result.reply));
                }
                case "media_now" -> core.publish(StageFour.nowLine(
                        message.text("title"), message.text("artist"), "1".equals(message.text("playing"))), null);
                case "call_decline" -> {
                    boolean ended = CallDecline.end(core, message.text("key"));
                    core.publish(ended ? "Declined the call" : "Could not decline the call. Use the phone.", null);
                }
                case "photo_list" -> core.desk.photos(message.text("offset"));
                case "photo_get" -> core.desk.photoGet(message.text("id"));
                case "lock_setup" -> {
                    int[] setup = StageFour.parseSetup(message.text("body"));
                    if (setup == null) {
                        core.publish("Lock settings were not understood", null);
                    } else {
                        android.content.SharedPreferences prefs = Prefs.open(core);
                        int localOn = "1".equals(prefs.getString("lockOn", "0")) ? 1 : 0;
                        int localDesk = "1".equals(prefs.getString("atDesk", "0")) ? 1 : 0;
                        int[] kept = StageFour.keepOwnSwitch(localOn, localDesk, setup);
                        LeaveWatch.saveSetup(core, kept);
                        if (core.leave != null && localOn == 1) core.leave.start();
                        core.publish("Updated the walk-away distance and wait. The switch stays as you set it.", null);
                    }
                }
                default -> {
                    if (core.five != null && core.five.handle(message)) break;
                    if (core.six != null && core.six.handle(message)) break;
                    JsonObject payload = new JsonObject();
                    payload.addProperty("code", "unsupported_type");
                    payload.addProperty("body", "This phone does not handle " + message.type);
                    core.writeEnvelope(Envelope.make("error", "android", payload));
                }
            }
        } catch (Exception error) {
            if (error instanceof Envelope.BadSender) return;
            LinkFailure.Kind kind = LinkFailure.classify(error);
            if (kind == LinkFailure.Kind.VERSION_MISMATCH) {
                core.linkState.failFatal(LinkFailure.message(kind));
                core.publish(LinkFailure.message(kind), null);
                core.generation.incrementAndGet();
                core.closeSocket();
                return;
            }
            core.publish("Bad message from the Mac", null);
        }
    }

    private static long longText(Envelope message, String key) {
        try {
            return Long.parseLong(message.text(key));
        } catch (Exception error) {
            return -1;
        }
    }
}
