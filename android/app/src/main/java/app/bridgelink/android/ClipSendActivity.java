package app.bridgelink.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/** Reads the clipboard after this screen is in front. Android 10+ will not share it before then. */
public class ClipSendActivity extends Activity {
    private boolean sent;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || sent) return;
        sent = true;
        ClipRead.Text clip = ClipRead.read(this);
        if (clip.sensitive) {
            Toast.makeText(this, "Skipped a sensitive clipboard", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (clip.text.isEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent send = new Intent(this, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "clipboard");
        send.putExtra(LinkService.EXTRA_BODY, clip.text);
        ContextCompat.startForegroundService(this, send);
        finish();
    }
}
