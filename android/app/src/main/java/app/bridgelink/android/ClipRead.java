package app.bridgelink.android;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import app.bridgelink.android.protocol.Features;

/** One clipboard read. Android 10 and newer only return text while a screen is in front. */
public final class ClipRead {
    public static final class Text {
        public final String text;
        public final boolean sensitive;

        public Text(String text, boolean sensitive) {
            this.text = text == null ? "" : text;
            this.sensitive = sensitive;
        }
    }

    private ClipRead() {}

    public static Text read(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) {
            return new Text("", false);
        }
        boolean secret = false;
        if (Build.VERSION.SDK_INT >= 33) {
            ClipDescription description = clipboard.getPrimaryClipDescription();
            secret = description != null && description.getExtras() != null
                    && description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
        }
        ClipData clip = clipboard.getPrimaryClip();
        String text = "";
        if (!secret && clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
            text = clip.getItemAt(0).getText().toString();
        }
        if (Features.looksSensitive(text)) secret = true;
        return new Text(secret ? "" : text, secret);
    }
}
