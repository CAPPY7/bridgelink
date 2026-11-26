package app.bridgelink.android.protocol;

import java.io.File;

/** A file is inside a folder only when the whole path stays under that folder. */
public final class SafePath {
    private SafePath() {}

    public static boolean inside(File root, File file) {
        if (root == null || file == null) return false;
        try {
            String base = root.getCanonicalPath();
            String path = file.getCanonicalPath();
            return path.equals(base) || path.startsWith(base + File.separator);
        } catch (Exception error) {
            return false;
        }
    }
}
