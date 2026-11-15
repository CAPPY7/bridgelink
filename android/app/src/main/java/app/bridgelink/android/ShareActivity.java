package app.bridgelink.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import app.bridgelink.android.protocol.Transfers;

/** Share-sheet entry. The service streams the files. It does not copy them first. */
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
        ArrayList<Uri> uris = streams(incoming);
        if (uris.isEmpty()) {
            finish();
            return;
        }
        StringBuilder body = new StringBuilder();
        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            if (body.length() > 0) body.append('\n');
            body.append(uri.toString());
        }
        send(uris.size() == 1 ? "file" : "files", body.toString());
        finish();
    }

    private void send(String kind, String body) {
        Intent service = new Intent(this, LinkService.class);
        service.setAction(LinkService.ACTION_SEND);
        service.putExtra(LinkService.EXTRA_KIND, kind);
        service.putExtra(LinkService.EXTRA_BODY, body);
        ContextCompat.startForegroundService(this, service);
    }

    private static ArrayList<Uri> streams(Intent incoming) {
        ArrayList<Uri> uris = new ArrayList<>();
        if (incoming == null) return uris;
        if (Intent.ACTION_SEND.equals(incoming.getAction())) {
            Uri one = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (one != null) uris.add(one);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(incoming.getAction())) {
            ArrayList<Uri> list = incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        }
        return uris;
    }
}
