package app.bridgelink.android;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.FolderFiles;
import app.bridgelink.android.protocol.StageFive;
import app.bridgelink.android.protocol.StageRules;
import app.bridgelink.android.protocol.Transfers;

/** Texts, shared folder, automations, and trackpad messages. The socket stays in LinkService. */
public final class PhoneFive {
    public interface Out {
        void frame(String type, JsonObject payload) throws Exception;

        void status(String line);

        /** Bytes the Mac already has for this file, or -1 if it has not said. */
        default long have(String id) {
            return -1;
        }

        default boolean linkedNow() {
            return true;
        }
    }

    private final Context context;
    private final Out out;
    private FolderFiles folder;
    private FolderTree tree;
    private boolean paused;
    private boolean linked;
    private boolean batteryFired;
    private List<StageFive.Rule> rules = new ArrayList<>();
    private final Map<String, FolderIn> folderIn = new HashMap<>();
    private final List<FileObserver> folderWatches = new ArrayList<>();
    private ContentObserver treeWatch;
    private long watchDue;
    private final Handler watchHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService folderSend = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bridgelink-folder-send");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable watchTick = () -> {
        if (!StageFive.folderWatchDue(linked, flag("folder"), paused, watchDue, System.currentTimeMillis())) return;
        watchDue = 0;
        pushIndex();
    };

    public PhoneFive(Context context, Out out) {
        this.context = context.getApplicationContext();
        this.out = out;
        folder = new FolderFiles(FolderPlace.defaultDir(this.context));
        android.net.Uri granted = FolderPlace.grantedTree(this.context);
        tree = granted == null ? null : new FolderTree(this.context, granted);
        paused = Prefs.open(this.context).getBoolean("folderPaused", false);
        rules = StageFive.decodeRules(Prefs.open(this.context).getString("autoRules", ""));
    }

    public void onPaired() {
        onLinked();
        pushSms();
        pushRules(false);
        pushIndex();
        fire("connect", "");
    }

    /** The folder watch follows the link, including an away link. */
    public void onLinked() {
        linked = true;
        armWatch();
    }

    /** Stop the folder watch when the link drops, the phone forgets the Mac, or the service stops. */
    public void onUnlinked() {
        linked = false;
        watchDue = 0;
        watchHandler.removeCallbacks(watchTick);
        stopWatch();
    }

    public boolean handle(Envelope message) throws Exception {
        switch (message.type) {
            case "sms_send" -> sendLocalSms(message.text("address"), message.text("body"), parseInt(message.text("sim")));
            case "sms_ask" -> pushSms();
            case "folder_index" -> onIndex(message.text("body"));
            case "folder_want" -> sendShared(message.text("path"));
            case "folder_part" -> onPart(message);
            case "folder_delete" -> onDelete(message.text("path"), message.text("sha"));
            case "folder_pause" -> setPaused("1".equals(message.text("on")), true);
            case "auto_rules" -> replaceRules(message.text("body"), true);
            case "input" -> setFlag("input", "1".equals(message.text("on")));
            case "folder" -> {
                boolean on = "1".equals(message.text("on"));
                setFlag("folder", on);
                if (on && !paused) armWatch();
                else disarmWatch();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public void command(String kind, String body) {
        if (kind == null) return;
        try {
            switch (kind) {
                case "pointer" -> sendPointer(body);
                case "sms_push" -> {
                    setFlag("sms", true);
                    pushSms();
                }
                case "sms_in" -> smsIn(body);
                case "folder_sync" -> {
                    setFlag("folder", true);
                    paused = false;
                    Prefs.open(context).edit().putBoolean("folderPaused", false).apply();
                    armWatch();
                    pushIndex();
                }
                case "folder_pause" -> setPaused("1".equals(body), false);
                case "folder_root" -> reloadFolder();
                case "auto" -> edit(body);
                case "input_on" -> {
                    setFlag("input", !"0".equals(body));
                    JsonObject payload = new JsonObject();
                    payload.addProperty("on", "0".equals(body) ? "0" : "1");
                    out.frame("input", payload);
                    out.status("1".equals(payload.get("on").getAsString()) ? "Phone trackpad is on" : "Phone trackpad is off");
                }
                default -> {
                }
            }
        } catch (Exception error) {
            out.status("Could not do that. Try again.");
        }
    }

    public void smsIn(String packed) {
        if (!flag("sms")) return;
        String address = "";
        String text = packed == null ? "" : packed;
        int split = text.indexOf('\n');
        if (split >= 0) {
            address = text.substring(0, split);
            text = text.substring(split + 1);
        }
        if (!StageFive.addressOk(address)) address = "Unknown";
        long now = System.currentTimeMillis();
        StageFive.Sms sms = new StageFive.Sms("in-" + now, address, text, true, 0, now);
        remember(sms);
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("address", address);
            payload.addProperty("body", text);
            payload.addProperty("sim", "0");
            payload.addProperty("incoming", "1");
            payload.addProperty("at", Long.toString(now));
            payload.addProperty("id", sms.id);
            out.frame("sms_new", payload);
            out.status("New text message");
        } catch (Exception error) {
            out.status("Could not show the new text on the Mac");
        }
    }

    public void noteBattery(int level, boolean charging) {
        if (StageFive.batteryLow(level, charging)) {
            if (!batteryFired) {
                batteryFired = true;
                fire("battery_low", Integer.toString(level));
            }
        } else {
            batteryFired = false;
        }
    }

    public void fire(String event, String detail) {
        if (!flag("auto")) return;
        for (StageFive.Fire item : StageFive.fire(rules, event, detail)) apply(item);
    }

    private void apply(StageFive.Fire item) {
        switch (item.thenAction) {
            case "start_sync" -> setPaused(false, false);
            case "pause_sync" -> setPaused(true, false);
            case "copy_code" -> copyCode(item.detail.isEmpty() ? item.extra : item.detail);
            case "alert", "note" -> showAuto(item.extra.isEmpty() ? item.detail : item.extra, item.thenAction);
            case "open_app" -> {
                String name = StageFive.appName(item.extra);
                if (name == null) {
                    out.status("That app name was not allowed");
                } else {
                    try {
                        JsonObject payload = new JsonObject();
                        payload.addProperty("name", name);
                        out.frame("open_app", payload);
                    } catch (Exception error) {
                        out.status("Could not ask the Mac to open the app");
                    }
                }
            }
            case "ring" -> ringMac();
            default -> {
            }
        }
    }

    public void sendLocalSms(String address, String body, int sim) {
        if (!flag("sms")) {
            out.status("Texts are off. Turn them on from Messages.");
            return;
        }
        if (!StageFive.addressOk(address)) {
            out.status("That number looks wrong.");
            return;
        }
        String text = body == null ? "" : body;
        List<String> parts = Features.smsParts(text);
        if (parts.isEmpty()) {
            out.status("Write a message first.");
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            out.status("Allow SMS for BridgeLink. " + StageFive.SMS_STEPS.replace('\n', ' '));
            return;
        }
        try {
            SmsManager manager = manager(sim);
            if (parts.size() == 1) manager.sendTextMessage(address.trim(), null, parts.get(0), null, null);
            else manager.sendMultipartTextMessage(address.trim(), null, new ArrayList<>(parts), null, null);
            long now = System.currentTimeMillis();
            remember(new StageFive.Sms("out-" + now, address.trim(), text, false, StageFive.simSlot(sim, simCount(context)), now));
            pushSms();
            out.status("Sent a text");
        } catch (SecurityException error) {
            out.status("Allow SMS for BridgeLink, including restricted settings if Android asks.");
        } catch (Exception error) {
            out.status("Could not send the text.");
        }
    }

    public void pushSms() {
        if (!flag("sms")) return;
        try {
            String encoded = readDeviceSms();
            if (encoded == null) {
                out.status("Allow SMS for BridgeLink so the Mac can show texts.");
                return;
            }
            writeText(smsFile(), encoded);
            JsonObject payload = new JsonObject();
            payload.addProperty("body", encoded);
            payload.addProperty("sims", Integer.toString(simCount(context)));
            out.frame("sms_list", payload);
            out.status("Texts updated");
        } catch (Exception error) {
            out.status("Could not read texts.");
        }
    }

    private void remember(StageFive.Sms sms) {
        try {
            String prior = readText(smsFile());
            String encoded = StageFive.encodeSms(StageFive.addSms(StageFive.decodeSms(prior), sms));
            writeText(smsFile(), encoded);
        } catch (Exception ignored) {
        }
    }

    private String readDeviceSms() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        List<StageFive.Sms> items = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[] {
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE
                },
                null,
                null,
                Telephony.Sms.DATE + " DESC"
        )) {
            if (cursor == null) return StageFive.encodeSms(items);
            while (cursor.moveToNext() && items.size() < StageFive.SMS_CAP) {
                String id = cursor.getString(0);
                String address = cursor.getString(1);
                String text = cursor.getString(2);
                long at = cursor.getLong(3);
                boolean incoming = cursor.getInt(4) != Telephony.Sms.MESSAGE_TYPE_SENT;
                if (address == null) address = "Unknown";
                if (text == null) text = "";
                items.add(new StageFive.Sms(id == null ? Long.toString(at) : id, address, text, incoming, 0, at));
            }
        } catch (SecurityException error) {
            return null;
        }
        return StageFive.encodeSms(items);
    }

    private void onIndex(String body) {
        if (paused || !flag("folder")) return;
        Map<String, StageFive.Entry> local = new HashMap<>();
        for (StageFive.Entry item : scanFolder()) local.put(item.path, item);
        for (StageFive.Entry remote : StageFive.decodeIndex(body)) {
            StageFive.Entry mine = local.get(remote.path);
            StageFive.Choice choice = StageFive.decide(
                    false,
                    mine != null,
                    mine == null ? "" : mine.sha,
                    remote.deleted,
                    remote.sha
            );
            if (remote.deleted) {
                if (choice == StageFive.Choice.DELETE && removeFolder(remote.path, remote.sha)) {
                    out.status("Removed a shared file");
                }
                continue;
            }
            if (choice == StageFive.Choice.TAKE || choice == StageFive.Choice.CONFLICT) {
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("path", remote.path);
                    out.frame("folder_want", payload);
                } catch (Exception error) {
                    out.status("Could not ask for a shared file.");
                }
            }
        }
    }

    private void onPart(Envelope message) {
        if (paused || !flag("folder")) return;
        String status = putFolder(
                message.text("path"),
                parseLong(message.text("offset")),
                parseLong(message.text("size")),
                parseLong(message.text("mtime")),
                message.text("sha"),
                message.text("data")
        );
        if (status != null) out.status(status);
        else if (parseLong(message.text("offset")) + decodeLen(message.text("data")) >= parseLong(message.text("size"))) {
            out.status("Shared folder updated");
            fire("file_in", message.text("path"));
        }
    }

    private void onDelete(String path, String sha) {
        if (paused || !flag("folder")) return;
        if (removeFolder(path, sha)) out.status("Removed a shared file");
    }

    public void pushIndex() {
        if (!flag("folder")) return;
        if (paused) {
            out.status("Shared folder is paused");
            return;
        }
        try {
            List<StageFive.Entry> now = scanFolder();
            String seen = readText(seenFile());
            for (StageFive.Entry gone : StageFive.removed(StageFive.decodeIndex(seen), now)) {
                JsonObject payload = new JsonObject();
                payload.addProperty("path", gone.path);
                payload.addProperty("sha", gone.sha);
                out.frame("folder_delete", payload);
            }
            writeText(seenFile(), StageFive.encodeIndex(now));
            JsonObject index = new JsonObject();
            index.addProperty("body", StageFive.encodeIndex(now));
            index.addProperty("paused", "0");
            out.frame("folder_index", index);
            out.status("Shared folder checked");
        } catch (Exception error) {
            out.status("Could not sync the shared folder.");
        }
    }

    private void sendShared(String path) throws Exception {
        if (paused || !flag("folder")) return;
        long size = tree != null ? tree.sizeOf(path) : folder.sizeOf(path);
        if (StageFive.folderUsesChunks(size)) {
            folderSend.execute(() -> {
                try {
                    sendFolderChunks(path, size);
                } catch (Exception error) {
                    out.status("Could not send the shared file.");
                }
            });
            return;
        }
        String problem = sendFolder(path, (offset, fileSize, mtime, sha, data) -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("path", Features.safeRelative(path));
            payload.addProperty("offset", Long.toString(offset));
            payload.addProperty("size", Long.toString(fileSize));
            payload.addProperty("mtime", Long.toString(mtime));
            payload.addProperty("sha", sha);
            payload.addProperty("data", data);
            out.frame("folder_part", payload);
        });
        if (problem != null) out.status(problem);
    }

    private void sendFolderChunks(String path, long size) throws Exception {
        String safe = Features.safeRelative(path);
        if (safe == null) {
            out.status("Refused a shared file.");
            return;
        }
        String name = safe.contains("/") ? safe.substring(safe.lastIndexOf('/') + 1) : safe;
        String id = Transfers.sha256Hex((safe + "\n" + size).getBytes(StandardCharsets.UTF_8));
        if (id.length() > 32) id = id.substring(0, 32);
        JsonObject begin = new JsonObject();
        begin.addProperty("id", id);
        begin.addProperty("name", name);
        begin.addProperty("path", safe);
        begin.addProperty("dest", "folder");
        begin.addProperty("size", Long.toString(size));
        begin.addProperty("from", "0");
        out.frame("file_begin", begin);
        long skip = waitHave(id);
        if (skip < 0 || skip > size) skip = 0;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = openShared(safe)) {
            if (in == null) {
                cancelSend(id);
                out.status("That shared file is gone.");
                return;
            }
            byte[] buf = new byte[Transfers.CHUNK];
            long offset = 0;
            while (offset < skip) {
                if (!out.linkedNow()) {
                    cancelSend(id);
                    return;
                }
                int want = (int) Math.min(buf.length, skip - offset);
                int count = in.read(buf, 0, want);
                if (count < 0) {
                    cancelSend(id);
                    out.status("The file changed while it was sending.");
                    return;
                }
                if (count == 0) continue;
                digest.update(buf, 0, count);
                offset += count;
            }
            int count;
            while ((count = in.read(buf)) >= 0) {
                if (!out.linkedNow()) {
                    cancelSend(id);
                    return;
                }
                if (count == 0) continue;
                waitWindow(id, offset);
                if (!out.linkedNow()) {
                    cancelSend(id);
                    return;
                }
                digest.update(buf, 0, count);
                JsonObject chunk = new JsonObject();
                chunk.addProperty("id", id);
                chunk.addProperty("offset", Long.toString(offset));
                chunk.addProperty("data", java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buf, count)));
                out.frame("file_chunk", chunk);
                offset += count;
            }
            if (offset != size) {
                cancelSend(id);
                out.status("The file changed while it was sending.");
                return;
            }
        }
        JsonObject end = new JsonObject();
        end.addProperty("id", id);
        end.addProperty("sha256", hex(digest.digest()));
        out.frame("file_end", end);
        out.status("Sent " + name);
    }

    private long waitHave(String id) {
        long end = System.currentTimeMillis() + 800;
        while (System.currentTimeMillis() < end) {
            if (!out.linkedNow()) return 0;
            long value = out.have(id);
            if (value >= 0) return value;
            try {
                Thread.sleep(40);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return 0;
            }
        }
        long value = out.have(id);
        return value < 0 ? 0 : value;
    }

    /** Keep at most 6 chunks ahead of the Mac, the same limit as a normal file send. */
    private void waitWindow(String id, long sent) {
        long cap = 6L * Transfers.CHUNK;
        long end = System.currentTimeMillis() + 30_000L;
        while (out.linkedNow() && System.currentTimeMillis() < end) {
            long ack = out.have(id);
            if (ack < 0) ack = 0;
            if (sent - ack < cap) return;
            try {
                Thread.sleep(40);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void cancelSend(String id) {
        try {
            JsonObject cancel = new JsonObject();
            cancel.addProperty("id", id);
            out.frame("file_cancel", cancel);
        } catch (Exception ignored) {
        }
    }

    private InputStream openShared(String safe) throws Exception {
        if (tree != null) {
            androidx.documentfile.provider.DocumentFile file = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree.tree());
            if (file == null) return null;
            for (String part : safe.split("/")) {
                file = file.findFile(part);
                if (file == null) return null;
            }
            return context.getContentResolver().openInputStream(file.getUri());
        }
        File file = new File(folder.root(), safe);
        if (!file.isFile()) return null;
        return new FileInputStream(file);
    }

    public boolean takeFolderFile(String id, String path, long size) {
        if (id == null || id.isEmpty() || !StageFive.folderUsesChunks(size)) return false;
        Transfers.Incoming incoming = Transfers.Incoming.begin(id, path, size);
        if (incoming == null) return false;
        try {
            File dir = new File(context.getCacheDir(), "folder-in");
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File tmp = new File(dir, id);
            if (tmp.exists() && !tmp.delete()) return false;
            folderIn.put(id, new FolderIn(incoming, path, tmp, new FileOutputStream(tmp)));
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public boolean hasFolderFile(String id) {
        return id != null && folderIn.containsKey(id);
    }

    public String chunkFolderFile(String id, long offset, String data) {
        FolderIn slot = folderIn.get(id);
        if (slot == null) return null;
        byte[] raw = slot.incoming.accept(offset, data);
        if (raw == null) {
            dropFolderFile(id);
            return "A file chunk was rejected.";
        }
        try {
            slot.out.write(raw);
            return null;
        } catch (Exception error) {
            dropFolderFile(id);
            return "Could not store the shared file.";
        }
    }

    public String finishFolderFile(String id, String sha) {
        FolderIn slot = folderIn.remove(id);
        if (slot == null) return null;
        try {
            slot.out.close();
        } catch (Exception ignored) {
        }
        if (!slot.incoming.matches(sha)) {
            slot.tmp.delete();
            return "A shared file did not match.";
        }
        String problem = tree != null ? tree.storeFile(slot.path, slot.tmp) : folder.storeFile(slot.path, slot.tmp);
        if (problem != null) return problem;
        return "Shared folder updated";
    }

    public void dropFolderFile(String id) {
        FolderIn slot = folderIn.remove(id);
        if (slot == null) return;
        try {
            slot.out.close();
        } catch (Exception ignored) {
        }
        slot.tmp.delete();
    }

    private static String hex(byte[] raw) {
        StringBuilder out = new StringBuilder(raw.length * 2);
        for (byte value : raw) out.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
        return out.toString();
    }

    private static final class FolderIn {
        final Transfers.Incoming incoming;
        final String path;
        final File tmp;
        final OutputStream out;

        FolderIn(Transfers.Incoming incoming, String path, File tmp, OutputStream out) {
            this.incoming = incoming;
            this.path = path;
            this.tmp = tmp;
            this.out = out;
        }
    }

    public void setPaused(boolean on, boolean fromPeer) {
        paused = on;
        Prefs.open(context).edit().putBoolean("folderPaused", on).apply();
        out.status(on ? "Shared folder is paused" : "Shared folder sync is on");
        if (!fromPeer) {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("on", on ? "1" : "0");
                out.frame("folder_pause", payload);
            } catch (Exception ignored) {
            }
        }
        if (!on) {
            armWatch();
            pushIndex();
        } else {
            disarmWatch();
        }
    }

    public void reloadFolder() {
        disarmWatch();
        folder = new FolderFiles(FolderPlace.defaultDir(context));
        android.net.Uri granted = FolderPlace.grantedTree(context);
        tree = granted == null ? null : new FolderTree(context, granted);
        if (linked) armWatch();
    }

    private java.util.List<StageFive.Entry> scanFolder() {
        return tree != null ? tree.scan(remembered()) : folder.scan(remembered());
    }

    private String putFolder(String path, long offset, long size, long mtime, String sha, String data) {
        return tree != null ? tree.part(path, offset, size, mtime, sha, data) : folder.part(path, offset, size, mtime, sha, data);
    }

    private String sendFolder(String path, app.bridgelink.android.protocol.FolderFiles.ChunkOut out) throws Exception {
        return tree != null ? tree.send(path, out) : folder.send(path, out);
    }

    private boolean removeFolder(String path, String sha) {
        return tree != null ? tree.remove(path, sha) : folder.remove(path, sha);
    }

    private void armWatch() {
        stopWatch();
        if (!linked || paused || !flag("folder")) return;
        if (tree != null) {
            treeWatch = new ContentObserver(watchHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    bumpWatch(null);
                }
            };
            context.getContentResolver().registerContentObserver(tree.tree(), true, treeWatch);
            return;
        }
        File root = folder.root();
        if (root == null || (!root.isDirectory() && !root.mkdirs())) return;
        int mask = FileObserver.CLOSE_WRITE | FileObserver.DELETE | FileObserver.CREATE
                | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.MODIFY;
        watchFolder(root, mask);
    }

    private void disarmWatch() {
        watchDue = 0;
        watchHandler.removeCallbacks(watchTick);
        stopWatch();
    }

    private void stopWatch() {
        if (!folderWatches.isEmpty()) {
            for (FileObserver observer : folderWatches) observer.stopWatching();
            folderWatches.clear();
        }
        if (treeWatch != null) {
            context.getContentResolver().unregisterContentObserver(treeWatch);
            treeWatch = null;
        }
    }

    private void bumpWatch(String path) {
        if (path != null && (path.startsWith(".") || path.contains("/."))) return;
        watchHandler.post(() -> {
            if (!linked || paused || !flag("folder")) return;
            watchDue = StageFive.folderPushAt(System.currentTimeMillis());
            watchHandler.removeCallbacks(watchTick);
            watchHandler.postDelayed(watchTick, StageFive.FOLDER_QUIET_MS);
        });
    }

    private void watchFolder(File dir, int mask) {
        if (dir == null || !dir.isDirectory() || dir.getName().startsWith(".")) return;
        if (folderWatches.size() >= 200) return;
        FileObserver observer = Build.VERSION.SDK_INT >= 29 ? watchTree(dir, mask) : watchPath(dir.getAbsolutePath(), mask);
        observer.startWatching();
        folderWatches.add(observer);
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) watchFolder(child, mask);
        }
    }

    @androidx.annotation.RequiresApi(29)
    private FileObserver watchTree(File root, int mask) {
        return new FileObserver(root, mask) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && (event & FileObserver.CREATE) != 0) {
                    File created = new File(root, path);
                    if (created.isDirectory()) watchHandler.post(() -> watchFolder(created, mask));
                }
                bumpWatch(path);
            }
        };
    }

    @SuppressWarnings("deprecation")
    private FileObserver watchPath(String directory, int mask) {
        return new FileObserver(directory, mask) {
            @Override
            public void onEvent(int event, String relative) {
                if (relative != null && (event & FileObserver.CREATE) != 0) {
                    File created = new File(directory, relative);
                    if (created.isDirectory()) watchHandler.post(() -> watchFolder(created, mask));
                }
                bumpWatch(relative);
            }
        };
    }

    private void edit(String body) {
        String text = body == null ? "" : body;
        String[] parts = text.split("\t", 4);
        String op = parts.length == 0 ? "" : parts[0];
        if ("add".equals(op) && parts.length >= 3) {
            String extra = parts.length >= 4 ? parts[3] : "";
            rules = StageFive.addRule(rules, parts[1], parts[2], extra);
        } else if ("toggle".equals(op) && parts.length >= 2) {
            boolean on = true;
            for (StageFive.Rule rule : rules) if (rule.id.equals(parts[1])) on = !rule.enabled;
            rules = StageFive.toggle(rules, parts[1], on);
        } else if ("delete".equals(op) && parts.length >= 2) {
            rules = StageFive.delete(rules, parts[1]);
        } else {
            out.status("That automation was not understood.");
            return;
        }
        setFlag("auto", true);
        saveRules();
        pushRules(false);
        out.status(rules.isEmpty() ? "No automations yet" : "Saved an automation");
    }

    private void replaceRules(String body, boolean fromPeer) {
        List<StageFive.Rule> next = StageFive.decodeRules(body);
        if (StageFive.encodeRules(next).equals(StageFive.encodeRules(rules))) return;
        rules = next;
        saveRules();
        if (!rules.isEmpty()) setFlag("auto", true);
        out.status("Automations updated");
        if (!fromPeer) pushRules(false);
    }

    private void pushRules(boolean force) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("body", StageFive.encodeRules(rules));
            out.frame("auto_rules", payload);
            if (force) out.status("Automations updated");
        } catch (Exception ignored) {
        }
    }

    private void saveRules() {
        Prefs.open(context).edit().putString("autoRules", StageFive.encodeRules(rules)).apply();
    }

    private void copyCode(String text) {
        if (text == null || text.isEmpty()) {
            out.status("No code to copy");
            return;
        }
        ClipboardManager board = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (board != null) board.setPrimaryClip(ClipData.newPlainText("BridgeLink", text));
        out.status("Copied a code");
    }

    private void showAuto(String text, String action) {
        String line = text == null || text.isEmpty() ? StageFive.actionLabel(action) : text;
        out.status(line);
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(new NotificationChannel("bridgelink.auto", "Automations", NotificationManager.IMPORTANCE_DEFAULT));
            }
            Notification note = new Notification.Builder(context, "bridgelink.auto")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("BridgeLink")
                    .setContentText(line)
                    .build();
            manager.notify(70, note);
        } catch (Exception ignored) {
        }
    }

    private void ringMac() {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("label", "automation");
            out.frame("find_mac", payload);
            out.status("Asked the Mac to ring");
        } catch (Exception error) {
            out.status("Could not ask the Mac to ring");
        }
    }

    private void sendPointer(String body) throws Exception {
        if (!flag("input")) {
            out.status("Turn the trackpad on first.");
            return;
        }
        String[] parts = (body == null ? "" : body).split("\t", 4);
        if (parts.length < 3) {
            out.status("That pointer move was ignored.");
            return;
        }
        int x;
        int y;
        try {
            x = Integer.parseInt(parts[1].trim());
            y = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException error) {
            out.status("That pointer move was ignored.");
            return;
        }
        if (StageFive.pointer(parts[0], x, y) == null) {
            out.status("That pointer move was ignored.");
            return;
        }
        String text = parts.length >= 4 ? parts[3] : "";
        if ("key".equals(parts[0]) && StageFive.keyName(text) == null) {
            out.status("That key was ignored.");
            return;
        }
        if ("slide".equals(parts[0]) && StageFive.slideKey(text) == null) {
            out.status("That slide button was ignored.");
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", parts[0]);
        payload.addProperty("x", Integer.toString(x));
        payload.addProperty("y", Integer.toString(y));
        payload.addProperty("text", text);
        out.frame("pointer", payload);
    }

    private void setFlag(String name, boolean on) {
        String flags = Prefs.open(context).getString("flags", "");
        Prefs.open(context).edit().putString("flags", StageRules.setFlag(flags, name, on)).apply();
    }

    private boolean flag(String name) {
        return Features.enabled(Prefs.open(context).getString("flags", ""), name, false);
    }

    private Map<String, StageFive.Entry> remembered() {
        Map<String, StageFive.Entry> prior = new HashMap<>();
        try {
            for (StageFive.Entry old : StageFive.decodeIndex(readText(seenFile()))) {
                if (!old.deleted) prior.put(old.path, old);
            }
        } catch (Exception ignored) {
        }
        return prior;
    }

    private File smsFile() {
        return new File(context.getFilesDir(), "sms.txt");
    }

    private File seenFile() {
        return new File(context.getFilesDir(), "folder-seen.txt");
    }

    private SmsManager manager(int sim) {
        int subscription = subscriptionId(sim);
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager manager = context.getSystemService(SmsManager.class);
            if (manager == null) manager = SmsManager.getDefault();
            if (subscription >= 0) return manager.createForSubscriptionId(subscription);
            return manager;
        }
        if (subscription >= 0) return SmsManager.getSmsManagerForSubscriptionId(subscription);
        return SmsManager.getDefault();
    }

    private int subscriptionId(int slot) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return -1;
        }
        try {
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            if (manager == null) return -1;
            List<SubscriptionInfo> list = manager.getActiveSubscriptionInfoList();
            if (list == null || list.isEmpty()) return -1;
            return list.get(StageFive.simSlot(slot, list.size())).getSubscriptionId();
        } catch (SecurityException error) {
            return -1;
        }
    }

    public static int simCount(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return 1;
        }
        try {
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            if (manager == null) return 1;
            List<SubscriptionInfo> list = manager.getActiveSubscriptionInfoList();
            if (list == null || list.isEmpty()) return 1;
            return Math.min(4, list.size());
        } catch (SecurityException error) {
            return 1;
        }
    }

    private static String readText(File file) throws Exception {
        if (file == null || !file.isFile()) return "";
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void writeText(File file, String text) throws Exception {
        Files.write(file.toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception error) {
            return 0;
        }
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception error) {
            return -1;
        }
    }

    private static int decodeLen(String data) {
        if (data == null || data.isEmpty()) return 0;
        try {
            return java.util.Base64.getDecoder().decode(data).length;
        } catch (IllegalArgumentException error) {
            return 0;
        }
    }
}
