package app.bridgelink.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.bridgelink.android.protocol.Envelope;
import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.PhotoRules;

/** Phone photos for the Mac. Queries and thumbnails stay off the main thread. */
public final class PhotoShelf {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bridgelink-photos");
        thread.setDaemon(true);
        return thread;
    });

    private PhotoShelf() {}

    public static boolean safeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 20) return false;
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) return false;
        }
        return true;
    }

    public static void list(LinkCore core, String offsetText) {
        IO.execute(() -> listNow(core, offsetText));
    }

    public static void sendFull(LinkCore core, String id) {
        IO.execute(() -> sendNow(core, id));
    }

    private static void listNow(LinkCore core, String offsetText) {
        if (!allowed(core)) {
            sendPage(core, 0, List.of(), false);
            core.publish("Turn on phone photos, then try again.", null);
            return;
        }
        int offset = 0;
        try {
            offset = Integer.parseInt(offsetText == null ? "0" : offsetText.trim());
        } catch (Exception ignored) {
        }
        if (offset < 0 || offset > 100_000) offset = 0;
        List<PhotoRules.Shot> shots = new ArrayList<>();
        List<String> names = new ArrayList<>();
        ContentResolver resolver = core.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] columns = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
        };
        int keep = offset + PhotoRules.PAGE + 1;
        int seen = 0;
        try (Cursor cursor = resolver.query(collection, columns, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int atCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                int nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    if (seen >= keep) break;
                    String id = Long.toString(cursor.getLong(idCol));
                    long at = cursor.getLong(atCol);
                    shots.add(new PhotoRules.Shot(id, at));
                    String name = nameCol < 0 ? "" : cursor.getString(nameCol);
                    names.add(name == null ? "" : name.replace('\n', ' '));
                    seen++;
                }
            }
        } catch (Exception ignored) {
            core.publish("Could not read photos. Allow photo access, then try again.", null);
            return;
        }
        List<PhotoRules.Shot> ordered = PhotoRules.newestFirst(shots);
        List<PhotoRules.Shot> page = PhotoRules.page(ordered, offset, PhotoRules.PAGE);
        boolean more = ordered.size() > offset + page.size();
        sendPage(core, offset, page, more);
        for (PhotoRules.Shot shot : page) {
            sendThumb(core, resolver, shot.id, nameFor(shots, names, shot.id));
        }
    }

    private static String nameFor(List<PhotoRules.Shot> shots, List<String> names, String id) {
        for (int i = 0; i < shots.size() && i < names.size(); i++) {
            if (id.equals(shots.get(i).id)) return names.get(i);
        }
        return "photo.jpg";
    }

    private static void sendPage(LinkCore core, int offset, List<PhotoRules.Shot> page, boolean more) {
        if (!core.paired) return;
        StringBuilder ids = new StringBuilder();
        for (PhotoRules.Shot shot : page) {
            if (ids.length() > 0) ids.append(',');
            ids.append(shot.id);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("offset", Integer.toString(offset));
        payload.addProperty("count", Integer.toString(page.size()));
        payload.addProperty("ids", ids.toString());
        payload.addProperty("more", more ? "1" : "0");
        try {
            core.writeEnvelope(Envelope.make("photo_page", "android", payload));
        } catch (Exception ignored) {
            core.publish("Could not send the photo list. Stay on the same Wi-Fi and try again.", null);
        }
    }

    private static void sendThumb(LinkCore core, ContentResolver resolver, String id, String name) {
        if (!core.paired || !safeId(id)) return;
        Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        Bitmap bitmap = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                bitmap = resolver.loadThumbnail(uri, new Size(256, 256), null);
            } else {
                bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                        resolver, Long.parseLong(id), MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            if (bitmap == null) return;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out);
            bitmap.recycle();
            bitmap = null;
            if (out.size() > 80_000) return;
            JsonObject payload = new JsonObject();
            payload.addProperty("id", id);
            payload.addProperty("name", name == null || name.isEmpty() ? "photo.jpg" : name);
            payload.addProperty("data", java.util.Base64.getEncoder().encodeToString(out.toByteArray()));
            core.writeEnvelope(Envelope.make("photo_thumb", "android", payload));
        } catch (Exception ignored) {
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static void sendNow(LinkCore core, String id) {
        if (!allowed(core)) {
            core.publish("Turn on phone photos, then try again.", null);
            return;
        }
        if (!safeId(id)) {
            core.publish("That photo was refused.", null);
            return;
        }
        Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        core.desk.sendPhoto(uri.toString());
    }

    private static boolean allowed(LinkCore core) {
        return core.paired && Features.enabled(Prefs.open(core).getString("flags", ""), "photos", false);
    }

    public static void chooseMore(Context context) {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            android.content.Intent intent = new android.content.Intent("android.provider.action.USER_SELECT_IMAGES_FOR_APP");
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            coreSettings(context);
        }
    }

    private static void coreSettings(Context context) {
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
