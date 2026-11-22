package app.bridgelink.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import app.bridgelink.android.protocol.Features;

/** Reads the clipboard while this screen is in front. Android 10+ will not share it in the background. */
public class ClipSendActivity extends Activity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String text = "";
        boolean secret = false;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
            if (Build.VERSION.SDK_INT >= 33) {
                ClipDescription description = clipboard.getPrimaryClipDescription();
                secret = description != null && description.getExtras() != null
                        && description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE);
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (!secret && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                text = clip.getItemAt(0).getText().toString();
            }
        }
        if (secret || Features.looksSensitive(text)) {
            Toast.makeText(this, "Skipped a sensitive clipboard", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent send = new Intent(this, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "clipboard");
        send.putExtra(LinkService.EXTRA_BODY, text);
        ContextCompat.startForegroundService(this, send);
        finish();
    }
}
