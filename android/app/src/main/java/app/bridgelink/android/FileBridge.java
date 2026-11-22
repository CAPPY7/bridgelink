package app.bridgelink.android;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import app.bridgelink.android.protocol.StageRules;
import app.bridgelink.android.protocol.Transfers;

/** Reads and writes files off the UI thread. The caller sends the frames. */
public final class FileBridge {
    public interface Out {
        void frame(String type, JsonObject payload) throws Exception;

        /** How many bytes the other side already has. 0 if it does not answer. */
        default long have(String id) {
            return 0;
        }

        default void progress(String name, int percent) {
        }

        default boolean cancelled() {
            return false;
        }
    }

    private final Context context;
    private final Map<String, Slot> incoming = new HashMap<>();
    private Uri lastUri;
    private String lastName = "";

    public FileBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public String begin(String id, String name, long size) {
        return begin(id, name, size, 0);
    }

    public String begin(String id, String name, long size, long from) {
        if (!safeId(id)) return "Refused that file.";
        Transfers.Incoming transfer = Transfers.Incoming.begin(id, name, size);
        if (transfer == null) return "That file is too big.";
        closeSlot(id);
        Slot slot = new Slot();
        try {
            slot.transfer = transfer;
            slot.display = transfer.name;
            if (Build.VERSION.SDK_INT >= 29) {
                slot.uri = reopenDownload(id);
                boolean created = false;
                if (slot.uri == null) {
                    slot.uri = insertPending(transfer.name);
                    created = slot.uri != null;
                }
                if (slot.uri == null) return "Could not store the file.";
                long have = downloadSize(slot.uri);
                if (from > size) from = 0;
                if (have > size || have < 0) have = 0;
                if (have > 0) {
                    try (InputStream prior = context.getContentResolver().openInputStream(slot.uri)) {
                        if (prior == null) throw new IllegalStateException("empty");
                        seed(transfer, prior, have);
                    }
                    slot.out = context.getContentResolver().openOutputStream(slot.uri, "wa");
                } else {
                    slot.out = context.getContentResolver().openOutputStream(slot.uri, "w");
                }
                if (slot.out == null) {
                    if (created) deleteUri(slot.uri);
                    return "Could not store the file.";
                }
                rememberIncoming(id, slot.uri.toString());
            } else {
                File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return "Could not store the file.";
                String saved = incomingBook().getString(id, "");
                File dest = saved.isEmpty() ? null : new File(saved);
                if (dest == null || !dest.getCanonicalPath().startsWith(dir.getCanonicalPath())) {
                    dest = uniqueFile(dir, transfer.name);
                }
                slot.file = dest;
                slot.display = dest.getName();
                long have = dest.isFile() ? dest.length() : 0;
                if (from > size) from = 0;
                if (have > size) have = 0;
                if (have > 0) {
                    try (InputStream prior = new FileInputStream(dest)) {
                        seed(transfer, prior, have);
                    }
                    slot.out = new FileOutputStream(dest, true);
                } else {
                    if (dest.exists() && !dest.delete()) return "Could not store the file.";
                    slot.out = new FileOutputStream(dest);
                }
                rememberIncoming(id, dest.getAbsolutePath());
            }
            incoming.put(id, slot);
            return "Receiving " + slot.display;
        } catch (Exception error) {
            deleteDest(slot);
            forgetIncoming(id);
            return "Could not store the file.";
        }
    }

    public long offered(String id) {
        Slot slot = incoming.get(id);
        if (slot == null) return 0;
        return slot.transfer.written();
    }

    public String chunk(String id, long offset, String data) {
        Slot slot = incoming.get(id);
        if (slot == null) return "That file is not in progress.";
        byte[] raw = slot.transfer.accept(offset, data);
        if (raw == null) {
            cancel(id);
            return "A file chunk was rejected.";
        }
        try {
            slot.out.write(raw);
            return null;
        } catch (Exception error) {
            cancel(id);
            return "Could not store the file.";
        }
    }

    public String finish(String id, String sha256) {
        Slot slot = incoming.remove(id);
        if (slot == null) return "That file is not in progress.";
        try {
            slot.out.close();
        } catch (Exception ignored) {
        }
        if (!slot.transfer.matches(sha256)) {
            deleteDest(slot);
            forgetIncoming(id);
            return "The file hash did not match. It was not saved.";
        }
        try {
            String saved = finalizeDest(slot);
            forgetIncoming(id);
            return saved;
        } catch (Exception error) {
            deleteDest(slot);
            forgetIncoming(id);
            return "Could not save the file.";
        }
    }

    public void cancel(String id) {
        Slot slot = incoming.remove(id);
        if (slot == null) return;
        try {
            slot.out.close();
        } catch (Exception ignored) {
        }
        deleteDest(slot);
        forgetIncoming(id);
    }

    public void sendMany(String joined, Out out) throws Exception {
        if (joined == null || joined.isBlank()) throw new IllegalArgumentException("No file");
        int sent = 0;
        for (String part : joined.split("\n")) {
            if (part == null || part.isBlank()) continue;
            if (out.cancelled()) throw new IllegalArgumentException("File send cancelled.");
            send(part.trim(), out);
            sent++;
        }
        if (sent == 0) throw new IllegalArgumentException("No file");
    }

    public void send(String source, Out out) throws Exception {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("No file");
        Stream opened = open(source);
        if (opened.size > Transfers.MAX_BYTES) {
            opened.close();
            throw new IllegalArgumentException("That file is over 4 GB.");
        }
        String id = stableId(source, opened.size);
        try (InputStream in = opened.stream) {
            JsonObject begin = new JsonObject();
            begin.addProperty("id", id);
            begin.addProperty("name", opened.name);
            begin.addProperty("size", Long.toString(opened.size));
            begin.addProperty("from", "0");
            out.frame("file_begin", begin);
            long skip = out.have(id);
            if (skip < 0 || skip > opened.size) skip = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[Transfers.CHUNK];
            long offset = 0;
            while (offset < skip) {
                int want = (int) Math.min(buffer.length, skip - offset);
                int count = in.read(buffer, 0, want);
                if (count < 0) throw new IllegalArgumentException("The file changed while it was sending.");
                if (count == 0) continue;
                digest.update(buffer, 0, count);
                offset += count;
            }
            int lastPercent = -1;
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (out.cancelled()) {
                    JsonObject cancel = new JsonObject();
                    cancel.addProperty("id", id);
                    out.frame("file_cancel", cancel);
                    throw new IllegalArgumentException("File send cancelled.");
                }
                if (count == 0) continue;
                digest.update(buffer, 0, count);
                JsonObject chunk = new JsonObject();
                chunk.addProperty("id", id);
                chunk.addProperty("offset", Long.toString(offset));
                chunk.addProperty("data", java.util.Base64.getEncoder().encodeToString(
                        count == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, count)));
                out.frame("file_chunk", chunk);
                offset += count;
                int percent = StageRules.progressPercent(offset, opened.size);
                if (percent == 100 || percent >= lastPercent + 10) {
                    lastPercent = percent;
                    out.progress(opened.name, percent);
                }
            }
            if (offset != opened.size) throw new IllegalArgumentException("The file changed while it was sending.");
            JsonObject end = new JsonObject();
            end.addProperty("id", id);
            end.addProperty("sha256", hex(digest.digest()));
            out.frame("file_end", end);
        } finally {
            opened.deleteTemp();
        }
    }

    public void sendLink(String raw, Out out) throws Exception {
        String url = Transfers.acceptUrl(raw);
        if (url == null) throw new IllegalArgumentException("Only http and https links can be sent.");
        JsonObject payload = new JsonObject();
        payload.addProperty("url", url);
        out.frame("link", payload);
    }

    private Stream open(String source) throws Exception {
        if (source.startsWith("content:")) {
            Uri uri = Uri.parse(source);
            String name = "file";
            long size = -1;
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIndex >= 0) name = cursor.getString(nameIndex);
                    if (sizeIndex >= 0) size = cursor.getLong(sizeIndex);
                }
            }
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) throw new IllegalArgumentException("Could not read that file.");
            if (size < 0) {
                File spool = spool(in);
                in.close();
                return new Stream(Transfers.sanitizeName(name), spool.length(), new FileInputStream(spool), spool);
            }
            return new Stream(Transfers.sanitizeName(name), size, in, null);
        }
        File file = new File(source);
        String cache = context.getCacheDir().getCanonicalPath();
        if (!file.getCanonicalPath().startsWith(cache + File.separator)) {
            throw new IllegalArgumentException("Refused a file outside the app cache.");
        }
        return new Stream(Transfers.sanitizeName(file.getName()), file.length(), new FileInputStream(file), null);
    }

    private File spool(InputStream in) throws Exception {
        File dir = new File(context.getCacheDir(), "outgoing");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalArgumentException("Could not store the file.");
        File out = new File(dir, UUID.randomUUID().toString());
        try (OutputStream output = new FileOutputStream(out)) {
            byte[] buffer = new byte[Transfers.CHUNK];
            long total = 0;
            int count;
            while ((count = in.read(buffer)) >= 0) {
                total += count;
                if (total > Transfers.MAX_BYTES) {
                    out.delete();
                    throw new IllegalArgumentException("That file is over 4 GB.");
                }
                if (count > 0) output.write(buffer, 0, count);
            }
        }
        return out;
    }

    private String finalizeDest(Slot slot) throws Exception {
        if (Build.VERSION.SDK_INT >= 29 && slot.uri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            int updated = context.getContentResolver().update(slot.uri, values, null, null);
            if (updated < 1) {
                deleteUri(slot.uri);
                throw new IllegalStateException("Downloads rejected the file");
            }
            lastUri = slot.uri;
            lastName = slot.display;
            return "Saved " + slot.display + " in Downloads/BridgeLink";
        }
        if (slot.file == null) throw new IllegalStateException("No downloads folder");
        lastName = slot.display;
        return "Saved " + slot.display;
    }

    private void deleteDest(Slot slot) {
        if (slot == null) return;
        if (slot.uri != null) deleteUri(slot.uri);
        if (slot.file != null) slot.file.delete();
    }

    @androidx.annotation.RequiresApi(29)
    private Uri insertPending(String name) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BridgeLink");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        return context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    }

    @androidx.annotation.RequiresApi(29)
    private Uri reopenDownload(String id) {
        String saved = incomingBook().getString(id, "");
        if (saved == null || !saved.startsWith("content:")) return null;
        Uri uri = Uri.parse(saved);
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Downloads._ID}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return uri;
        } catch (Exception ignored) {
        }
        return null;
    }

    private long downloadSize(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void deleteUri(Uri uri) {
        try {
            context.getContentResolver().delete(uri, null, null);
        } catch (Exception ignored) {
        }
    }

    private static void seed(Transfers.Incoming transfer, InputStream prior, long have) throws Exception {
        byte[] buf = new byte[Transfers.CHUNK];
        long left = have;
        while (left > 0) {
            int n = prior.read(buf, 0, (int) Math.min(buf.length, left));
            if (n <= 0) break;
            transfer.seed(java.util.Arrays.copyOf(buf, n));
            left -= n;
        }
    }

    static File uniqueFile(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int n = 1; n < 1000; n++) {
            File next = new File(dir, base + " (" + n + ")" + ext);
            if (!next.exists()) return next;
        }
        return new File(dir, base + " (" + System.currentTimeMillis() + ")" + ext);
    }

    private SharedPreferences incomingBook() {
        return context.getSharedPreferences("bridgelink_incoming", Context.MODE_PRIVATE);
    }

    private void rememberIncoming(String id, String where) {
        incomingBook().edit().putString(id, where).apply();
    }

    private void forgetIncoming(String id) {
        incomingBook().edit().remove(id).apply();
    }

    public String lastName() {
        return lastName == null ? "" : lastName;
    }

    public Uri lastUri() {
        return lastUri;
    }

    public void openLast() {
        if (lastUri == null) return;
        try {
            android.content.Intent view = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            view.setData(lastUri);
            view.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(view);
        } catch (Exception ignored) {
        }
    }

    private static String stableId(String source, long size) {
        String raw = source + "\n" + size;
        String hex = Transfers.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return hex.substring(0, 32);
    }

    private void closeSlot(String id) {
        Slot slot = incoming.remove(id);
        if (slot == null) return;
        try {
            slot.out.close();
        } catch (Exception ignored) {
        }
    }

    private static boolean safeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 80) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') && c != '-') return false;
        }
        return true;
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) builder.append(String.format(Locale.US, "%02x", value));
        return builder.toString();
    }

    private static final class Slot {
        Transfers.Incoming transfer;
        File file;
        Uri uri;
        OutputStream out;
        String display = "";
    }

    private static final class Stream {
        final String name;
        final long size;
        final InputStream stream;
        final File temp;

        Stream(String name, long size, InputStream stream, File temp) {
            this.name = name;
            this.size = size;
            this.stream = stream;
            this.temp = temp;
        }

        void close() {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
            deleteTemp();
        }

        void deleteTemp() {
            if (temp != null) temp.delete();
        }
    }
}
