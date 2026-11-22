package app.bridgelink.android;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.StageFour;

/** Clipboard history. Off until the user turns it on. The file is sealed. Old copies drop after 24 hours. */
public final class ClipStore {
    private static List<StageFour.Clip> memory = List.of();

    private ClipStore() {}

    public static File file(Context context) {
        return new File(context.getFilesDir(), "clip-history.txt");
    }

    public static boolean historyOn(Context context) {
        return "1".equals(Prefs.open(context).getString("clipHistory", "0"));
    }

    public static void setHistory(Context context, boolean on) {
        Prefs.open(context).edit().putString("clipHistory", on ? "1" : "0").apply();
        if (!on) clear(context);
    }

    public static void clear(Context context) {
        memory = List.of();
        File file = file(context);
        if (file.isFile() && !file.delete()) file.deleteOnExit();
    }

    public static List<StageFour.Clip> load(Context context) {
        if (!StageFour.keepHistory(historyOn(context))) {
            clear(context);
            return List.of();
        }
        List<StageFour.Clip> fresh = StageFour.dropOlderThan(read(context), System.currentTimeMillis(), StageFour.CLIP_KEEP_MS);
        if (fresh.size() != memory.size()) saveFresh(context, fresh);
        return fresh;
    }

    public static void save(Context context, List<StageFour.Clip> clips) {
        if (!StageFour.keepHistory(historyOn(context))) {
            clear(context);
            return;
        }
        saveFresh(context, StageFour.dropOlderThan(clips, System.currentTimeMillis(), StageFour.CLIP_KEEP_MS));
    }

    public static void addText(Context context, String text) {
        if (!StageFour.keepHistory(historyOn(context))) return;
        if (text == null || text.isEmpty()) return;
        long now = System.currentTimeMillis();
        save(context, StageFour.add(read(context), text, Features.looksSensitive(text), now));
    }

    private static List<StageFour.Clip> read(Context context) {
        File file = file(context);
        if (!file.isFile()) return new ArrayList<>(memory);
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            String plain = Prefs.openText(context, new String(raw, StandardCharsets.US_ASCII).trim());
            List<StageFour.Clip> clips = StageFour.decodeClips(plain);
            memory = clips;
            return new ArrayList<>(clips);
        } catch (Exception error) {
            if (file.isFile()) file.delete();
            return new ArrayList<>(memory);
        }
    }

    private static void saveFresh(Context context, List<StageFour.Clip> fresh) {
        memory = fresh == null ? List.of() : fresh;
        File file = file(context);
        try {
            String sealed = Prefs.sealText(context, StageFour.encodeClips(memory));
            Files.write(file.toPath(), sealed.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception error) {
            if (file.isFile()) file.delete();
        }
    }
}
