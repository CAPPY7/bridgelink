package app.bridgelink.android.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared-folder bytes on disk. Decisions stay in StageFive. */
public final class FolderFiles {
    public interface ChunkOut {
        void chunk(long offset, long size, long mtime, String sha, String data) throws Exception;
    }

    private final File root;
    private final Map<String, Partial> incoming = new HashMap<>();

    public FolderFiles(File root) {
        this.root = root;
        if (!root.isDirectory() && !root.mkdirs()) {
            root.mkdirs();
        }
    }

    public File root() {
        return root;
    }

    public List<StageFive.Entry> scan() {
        return scan(java.util.Collections.emptyMap());
    }

    public List<StageFive.Entry> scan(Map<String, StageFive.Entry> prior) {
        ArrayList<StageFive.Entry> items = new ArrayList<>();
        walk(root, "", items, prior == null ? java.util.Collections.emptyMap() : prior);
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
                File dir = new File(root, ".incoming");
                if (!dir.isDirectory() && !dir.mkdirs()) return "Could not store the shared file.";
                File tmp = new File(dir, safe.replace('/', '_'));
                if (tmp.exists() && !tmp.delete()) return "Could not store the shared file.";
                slot = new Partial(tmp, size, mtime, sha, safe, MessageDigest.getInstance("SHA-256"));
                incoming.put(safe, slot);
            }
            if (offset != slot.written || size != slot.size || !sha.equalsIgnoreCase(slot.sha)) {
                close(slot);
                incoming.remove(safe);
                return "Refused a shared file.";
            }
            if (raw.length > 0) {
                slot.out.write(raw);
                slot.digest.update(raw);
            }
            slot.written += raw.length;
            if (slot.written < size) return null;
            slot.out.close();
            slot.out = null;
            String got = hex(slot.digest.digest());
            incoming.remove(safe);
            if (!got.equalsIgnoreCase(sha)) {
                slot.tmp.delete();
                return "A shared file did not match.";
            }
            File existing = resolve(safe);
            if (existing != null && existing.isFile() && sha.equalsIgnoreCase(sha(existing))) {
                slot.tmp.delete();
                return null;
            }
            File dest = place(safe, sha);
            if (dest == null) {
                slot.tmp.delete();
                return "Refused a shared file.";
            }
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return "Could not store the shared file.";
            if (dest.exists() && !dest.delete()) return "Could not store the shared file.";
            if (!slot.tmp.renameTo(dest)) {
                Files.copy(slot.tmp.toPath(), dest.toPath());
                slot.tmp.delete();
            }
            if (mtime > 0) dest.setLastModified(mtime);
            return null;
        } catch (Exception error) {
            incoming.remove(safe);
            return "Could not store the shared file.";
        }
    }

    public String send(String path, ChunkOut out) throws Exception {
        String safe = Features.safeRelative(path);
        File file = resolve(safe);
        if (safe == null || file == null || !file.isFile()) return "That shared file is gone.";
        if (file.length() > StageFive.FOLDER_FILE_CAP) return "Skipped a shared file over 32 MB.";
        String sha = sha(file);
        long size = file.length();
        long mtime = file.lastModified();
        if (size == 0) {
            out.chunk(0, 0, mtime, sha, "");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[StageFive.PART];
            long offset = 0;
            while (offset < size) {
                int n = in.read(buf);
                if (n < 0) break;
                out.chunk(offset, size, mtime, sha, Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buf, n)));
                offset += n;
            }
        }
        return null;
    }

    public boolean remove(String path, String sha) {
        File file = resolve(Features.safeRelative(path));
        if (file == null || !file.isFile() || sha == null) return false;
        if (!sha.equalsIgnoreCase(sha(file))) return false;
        return file.delete();
    }

    private File place(String safe, String sha) {
        File dest = resolve(safe);
        if (dest == null) return null;
        if (!dest.isFile()) return dest;
        if (sha.equalsIgnoreCase(sha(dest))) return dest;
        String conflict = StageFive.conflictName(safe);
        File copy = resolve(conflict);
        if (copy == null) return null;
        if (!copy.exists()) return copy;
        return copy;
    }

    private void walk(File dir, String prefix, List<StageFive.Entry> items, Map<String, StageFive.Entry> prior) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File kid : kids) {
            if (items.size() >= StageFive.FOLDER_CAP) return;
            String name = kid.getName();
            if (name.startsWith(".")) continue;
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

    private String sha(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[StageFive.PART];
                int n;
                while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
            }
            return hex(digest.digest());
        } catch (Exception error) {
            return "";
        }
    }

    private File resolve(String safe) {
        if (safe == null) return null;
        File file = new File(root, safe);
        try {
            String rootPath = root.getCanonicalPath();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            String filePath = file.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) return null;
            return file;
        } catch (Exception error) {
            return null;
        }
    }

    private static void close(Partial slot) {
        if (slot == null || slot.out == null) return;
        try {
            slot.out.close();
        } catch (Exception ignored) {
        }
        slot.out = null;
    }

    private static String hex(byte[] raw) {
        StringBuilder out = new StringBuilder();
        for (byte value : raw) out.append(String.format(Locale.US, "%02x", value & 0xff));
        return out.toString();
    }

    private static final class Partial {
        final File tmp;
        final long size;
        final long mtime;
        final String sha;
        final String path;
        final MessageDigest digest;
        FileOutputStream out;
        long written;

        Partial(File tmp, long size, long mtime, String sha, String path, MessageDigest digest) throws Exception {
            this.tmp = tmp;
            this.size = size;
            this.mtime = mtime;
            this.sha = sha;
            this.path = path;
            this.digest = digest;
            this.out = new FileOutputStream(tmp);
        }
    }
}
