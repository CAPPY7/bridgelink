package app.bridgelink.android;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.FolderFiles;
import app.bridgelink.android.protocol.StageFive;

/** Shared-folder bytes in a folder the user picked. The rules stay in StageFive. */
public final class FolderTree {
    private final Context context;
    private final Uri tree;
    private final Map<String, Partial> incoming = new HashMap<>();

    public FolderTree(Context context, Uri tree) {
        this.context = context.getApplicationContext();
        this.tree = tree;
    }

    public Uri tree() {
        return tree;
    }

    public List<StageFive.Entry> scan(Map<String, StageFive.Entry> prior) {
        ArrayList<StageFive.Entry> items = new ArrayList<>();
        DocumentFile root = DocumentFile.fromTreeUri(context, tree);
        if (root != null) walk(root, "", items, prior == null ? java.util.Collections.emptyMap() : prior);
        return items;
    }

    public String part(String path, long offset, long size, long mtime, String sha, String data) {
        byte[] raw;
        try {
            raw = data == null || data.isEmpty() ? new byte[0] : Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException error) {
            return "Refused a shared file.";
        }
        if (!StageFive.partOk(path, offset, raw.length, size)) return "Refused a shared file.";
        String safe = Features.safeRelative(path);
        if (safe == null || sha == null || sha.isEmpty()) return "Refused a shared file.";
        try {
            Partial slot = incoming.get(safe);
            if (slot == null || offset == 0) {
                close(slot);
                slot = new Partial(size, mtime, sha, safe, MessageDigest.getInstance("SHA-256"));
                incoming.put(safe, slot);
            }
            if (offset != slot.written || size != slot.size || !sha.equalsIgnoreCase(slot.sha)) {
                incoming.remove(safe);
                return "Refused a shared file.";
            }
            slot.digest.update(raw);
            slot.pending.write(raw);
            slot.written += raw.length;
            if (slot.written < size) return null;
            slot.pending.close();
            String got = hex(slot.digest.digest());
            incoming.remove(safe);
            if (!got.equalsIgnoreCase(sha)) return "A shared file did not match.";
            DocumentFile dest = place(safe, sha);
            if (dest == null) return "Refused a shared file.";
            try (OutputStream out = context.getContentResolver().openOutputStream(dest.getUri(), "wt")) {
                if (out == null) return "Could not store the shared file.";
                slot.pending.writeTo(out);
            }
            return null;
        } catch (Exception error) {
            incoming.remove(safe);
            return "Could not store the shared file.";
        }
    }

    public String send(String path, FolderFiles.ChunkOut out) throws Exception {
        String safe = Features.safeRelative(path);
        DocumentFile file = find(safe);
        if (safe == null || file == null || !file.isFile()) return "That shared file is gone.";
        if (file.length() > StageFive.FOLDER_FILE_CAP) return "Skipped a shared file over 32 MB.";
        String sha = sha(file);
        long size = file.length();
        long mtime = file.lastModified();
        if (size == 0) {
            out.chunk(0, 0, mtime, sha, "");
            return null;
        }
        try (InputStream in = context.getContentResolver().openInputStream(file.getUri())) {
            if (in == null) return "Could not read the shared file.";
            byte[] buf = new byte[StageFive.PART];
            long offset = 0;
            int n;
            while (offset < size && (n = in.read(buf)) >= 0) {
                if (n == 0) continue;
                out.chunk(offset, size, mtime, sha, Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buf, n)));
                offset += n;
            }
        }
        return null;
    }

    public boolean remove(String path, String sha) {
        DocumentFile file = find(Features.safeRelative(path));
        if (file == null || !file.isFile() || sha == null) return false;
        if (!sha.equalsIgnoreCase(sha(file))) return false;
        return file.delete();
    }

    private DocumentFile place(String safe, String sha) throws Exception {
        DocumentFile existing = find(safe);
        if (existing != null && existing.isFile() && sha.equalsIgnoreCase(sha(existing))) return existing;
        if (existing != null && existing.isFile()) {
            String conflict = StageFive.conflictName(safe);
            if (conflict == null) return null;
            DocumentFile copy = find(conflict);
            if (copy != null) return copy;
            return create(conflict);
        }
        return create(safe);
    }

    private DocumentFile create(String safe) {
        int slash = safe.lastIndexOf('/');
        String dir = slash < 0 ? "" : safe.substring(0, slash);
        String name = slash < 0 ? safe : safe.substring(slash + 1);
        DocumentFile parent = dir.isEmpty() ? DocumentFile.fromTreeUri(context, tree) : mkdir(dir);
        if (parent == null || name.startsWith(".")) return null;
        return parent.createFile("application/octet-stream", name);
    }

    private DocumentFile mkdir(String dir) {
        DocumentFile current = DocumentFile.fromTreeUri(context, tree);
        if (current == null) return null;
        for (String part : dir.split("/")) {
            if (part.isEmpty() || part.startsWith(".")) return null;
            DocumentFile next = current.findFile(part);
            if (next == null) next = current.createDirectory(part);
            if (next == null || !next.isDirectory()) return null;
            current = next;
        }
        return current;
    }

    private DocumentFile find(String safe) {
        if (safe == null) return null;
        DocumentFile current = DocumentFile.fromTreeUri(context, tree);
        if (current == null) return null;
        for (String part : safe.split("/")) {
            if (part.isEmpty() || part.startsWith(".")) return null;
            current = current.findFile(part);
            if (current == null) return null;
        }
        return current;
    }

    private void walk(DocumentFile dir, String prefix, List<StageFive.Entry> items, Map<String, StageFive.Entry> prior) {
        DocumentFile[] kids = dir.listFiles();
        for (DocumentFile kid : kids) {
            if (items.size() >= StageFive.FOLDER_CAP) return;
            String name = kid.getName();
            if (name == null || name.startsWith(".")) continue;
            String path = prefix.isEmpty() ? name : prefix + "/" + name;
            if (kid.isDirectory()) walk(kid, path, items, prior);
            else if (kid.isFile()) {
                long size = kid.length();
                long mtime = kid.lastModified();
                StageFive.Entry old = prior.get(path);
                String hash = old != null && old.size == size && old.mtime == mtime && old.sha != null && !old.sha.isEmpty()
                        ? old.sha
                        : sha(kid);
                items.add(new StageFive.Entry(path, size, mtime, hash, false));
            }
        }
    }

    private String sha(DocumentFile file) {
        try (InputStream in = context.getContentResolver().openInputStream(file.getUri())) {
            if (in == null) return "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[StageFive.PART];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
            return hex(digest.digest());
        } catch (Exception error) {
            return "";
        }
    }

    private static void close(Partial slot) {
        if (slot == null) return;
        try {
            slot.pending.close();
        } catch (Exception ignored) {
        }
    }

    private static String hex(byte[] raw) {
        StringBuilder out = new StringBuilder();
        for (byte value : raw) out.append(String.format(Locale.US, "%02x", value & 0xff));
        return out.toString();
    }

    private static final class Partial {
        final long size;
        final long mtime;
        final String sha;
        final String safe;
        final MessageDigest digest;
        final java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();
        long written;

        Partial(long size, long mtime, String sha, String safe, MessageDigest digest) {
            this.size = size;
            this.mtime = mtime;
            this.sha = sha;
            this.safe = safe;
            this.digest = digest;
        }
    }
}
