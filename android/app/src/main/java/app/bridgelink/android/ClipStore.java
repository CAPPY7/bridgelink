package app.bridgelink.android;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.StageFour;

/** Clipboard history on disk. Sensitive text is never written. */
public final class ClipStore {
    private ClipStore() {}

    public static File file(Context context) {
        return new File(context.getFilesDir(), "clip-history.txt");
    }

    public static List<StageFour.Clip> load(Context context) {
        File file = file(context);
        if (!file.isFile()) return List.of();
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            return StageFour.decodeClips(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception error) {
            return List.of();
        }
    }

    public static void save(Context context, List<StageFour.Clip> clips) {
        try {
            Files.write(file(context).toPath(), StageFour.encodeClips(clips).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static void addText(Context context, String text) {
        if (text == null || text.isEmpty()) return;
        save(context, StageFour.add(load(context), text, Features.looksSensitive(text), System.currentTimeMillis()));
    }
}
