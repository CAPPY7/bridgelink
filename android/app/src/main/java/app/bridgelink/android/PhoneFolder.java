package app.bridgelink.android;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

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
import app.bridgelink.android.protocol.Transfers;

/** Shared folder and big-file send and receive. PhoneFive keeps the public names. */
final class PhoneFolder {
    interface Host {
        boolean flag(String name);
        void setFlag(String name, boolean on);
        void fire(String event, String detail);
    }

    private final Context context;
    private final PhoneFive.Out out;
    private final Host host;
    private FolderFiles folder;
    private FolderTree tree;
    private boolean paused;
    private boolean linked;
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
        if (!StageFive.folderWatchDue(linked, host.flag("folder"), paused, watchDue, System.currentTimeMillis())) return;
        watchDue = 0;
        pushIndex();
    };

    PhoneFolder(Context context, PhoneFive.Out out, Host host) {
        this.context = context;
        this.out = out;
        this.host = host;
        folder = new FolderFiles(FolderPlace.defaultDir(context));
        Uri granted = FolderPlace.grantedTree(context);
        tree = granted == null ? null : new FolderTree(context, granted);
        paused = Prefs.open(context).getBoolean("folderPaused", false);
    }

    void onLinked() {
        linked = true;
        armWatch();
    }

    void onUnlinked() {
        linked = false;
        watchDue = 0;
        watchHandler.removeCallbacks(watchTick);
        stopWatch();
    }

    /** The phone asked to turn the shared folder on or off. */
    void setSwitch(boolean on) {
        host.setFlag("folder", on);
        if (on && !paused) armWatch();
        else disarmWatch();
    }

    /** The phone asked to sync now. */
    void syncNow() {
        host.setFlag("folder", true);
        paused = false;
        Prefs.open(context).edit().putBoolean("folderPaused", false).apply();
        armWatch();
        pushIndex();
    }

    boolean paused() {
        return paused;
    }

    void onIndex(String body) {
        if (paused || !host.flag("folder")) return;
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

    void onPart(Envelope message) {
        if (paused || !host.flag("folder")) return;
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
            host.fire("file_in", message.text("path"));
        }
    }

    void onDelete(String path, String sha) {
        if (paused || !host.flag("folder")) return;
        if (removeFolder(path, sha)) out.status("Removed a shared file");
    }

    public void pushIndex() {
        if (!host.flag("folder")) return;
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

    void sendShared(String path) throws Exception {
        if (paused || !host.flag("folder")) return;
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
        out.dropAck(id);
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
        out.dropAck(id);
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
            out.dropAck(id);
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
        if (!StageFive.folderAccept(linked, host.flag("folder"), paused)) return false;
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

    void armWatch() {
        stopWatch();
        if (!linked || paused || !host.flag("folder")) return;
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

    void disarmWatch() {
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
            if (!linked || paused || !host.flag("folder")) return;
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

    private File seenFile() {
        return new File(context.getFilesDir(), "folder-seen.txt");
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

    private static String readText(File file) throws Exception {
        if (file == null || !file.isFile()) return "";
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void writeText(File file, String text) throws Exception {
        Files.write(file.toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }
}
