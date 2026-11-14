package app.bridgelink.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import app.bridgelink.android.protocol.Transfers;

/** Share-sheet entry. Copies a file into the app cache, then the service sends it. */
public class ShareActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText(R.string.sending);
        view.setPadding(48, 48, 48, 48);
        setContentView(view);
        Intent incoming = getIntent();
        String action = incoming == null ? "" : incoming.getAction();
        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(incoming.getType())) {
            String text = incoming.getStringExtra(Intent.EXTRA_TEXT);
            if (Transfers.acceptUrl(text) != null) send("link", text);
            else send("text", text == null ? "" : text);
            finish();
            return;
        }
        Uri uri = stream(incoming);
        if (uri == null) {
            finish();
            return;
        }
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "outgoing");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cache");
                File out = new File(dir, UUID.randomUUID().toString());
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(out)) {
                    if (in == null) throw new IllegalStateException("empty");
                    byte[] buffer = new byte[Transfers.CHUNK];
                    long total = 0;
                    int count;
                    while ((count = in.read(buffer)) >= 0) {
                        total += Math.max(count, 0);
                        if (total > Transfers.MAX_BYTES) {
                            out.delete();
                            throw new IllegalStateException("big");
                        }
                        if (count > 0) output.write(buffer, 0, count);
                    }
                }
                runOnUiThread(() -> {
                    send("file", out.getAbsolutePath());
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(this::finish);
            }
        }, "bridgelink-share").start();
    }

    private void send(String kind, String body) {
        Intent service = new Intent(this, LinkService.class);
        service.setAction(LinkService.ACTION_SEND);
        service.putExtra(LinkService.EXTRA_KIND, kind);
        service.putExtra(LinkService.EXTRA_BODY, body);
        ContextCompat.startForegroundService(this, service);
    }

    private static Uri stream(Intent incoming) {
        if (incoming == null) return null;
        if (Intent.ACTION_SEND.equals(incoming.getAction())) {
            return incoming.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(incoming.getAction())) {
            ArrayList<Uri> list = incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        }
        return null;
    }
}
