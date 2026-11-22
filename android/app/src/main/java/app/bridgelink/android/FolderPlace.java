package app.bridgelink.android;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;

/** Where the shared folder lives. A chosen folder is remembered. No broad storage permission. */
public final class FolderPlace {
    private FolderPlace() {}

    public static Uri grantedTree(Context context) {
        String saved = Prefs.open(context).getString("folderTree", "");
        if (saved == null || !saved.startsWith("content:")) return null;
        Uri uri = Uri.parse(saved);
        for (UriPermission grant : context.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(grant.getUri()) && grant.isReadPermission() && grant.isWritePermission()) return uri;
        }
        return null;
    }

    public static File defaultDir(Context context) {
        if (Build.VERSION.SDK_INT < 29) {
            File docs = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BridgeLink Shared");
            if (docs.isDirectory() || docs.mkdirs()) return docs;
        }
        if (Build.VERSION.SDK_INT >= 29 && !Prefs.open(context).getBoolean("folderMarked", false)) {
            markDocuments(context);
        }
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "BridgeLink Shared");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    public static String label(Context context) {
        Uri tree = grantedTree(context);
        if (tree != null) {
            androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree);
            if (doc != null && doc.getName() != null && !doc.getName().isEmpty()) return doc.getName();
            return "Chosen folder";
        }
        return defaultDir(context).getAbsolutePath();
    }

    private static void markDocuments(Context context) {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "BridgeLink Shared");
            values.put(MediaStore.MediaColumns.MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);
            resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            Prefs.open(context).edit().putBoolean("folderMarked", true).apply();
        } catch (Exception ignored) {
        }
    }
}
