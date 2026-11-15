package app.bridgelink.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import app.bridgelink.android.protocol.Transfers;

/** Share-sheet entry. Nothing is sent until the user taps Send. */
public class ShareActivity extends AppCompatActivity {
    private String kind = "";
    private String body = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent incoming = getIntent();
        String action = incoming == null ? "" : incoming.getAction();
        if (Intent.ACTION_SEND.equals(action) && incoming != null && "text/plain".equals(incoming.getType())) {
            String text = incoming.getStringExtra(Intent.EXTRA_TEXT);
            if (Transfers.acceptUrl(text) != null) {
                kind = "link";
                body = text;
            } else {
                kind = "text";
                body = text == null ? "" : text;
            }
            showConfirm(body.isEmpty() ? "Nothing to send" : "Send this to the Mac?", body.isEmpty());
            return;
        }
        ArrayList<Uri> uris = streams(incoming);
        if (uris.isEmpty()) {
            finish();
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (Uri uri : uris) {
            if (joined.length() > 0) joined.append('\n');
            joined.append(uri.toString());
        }
        kind = uris.size() == 1 ? "file" : "files";
        body = joined.toString();
        String title = uris.size() == 1 ? "Send 1 file to the Mac?" : "Send " + uris.size() + " files to the Mac?";
        showConfirm(title, false);
    }

    private void showConfirm(String title, boolean empty) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(48, 48, 48, 48);
        TextView view = new TextView(this);
        view.setText(title);
        view.setPadding(0, 0, 0, 24);
        column.addView(view);
        if (!empty) {
            Button send = new Button(this);
            send.setText("Send");
            send.setOnClickListener(this::onSend);
            column.addView(send);
        }
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> finish());
        column.addView(cancel);
        setContentView(column);
    }

    private void onSend(View ignored) {
        Intent incoming = getIntent();
        if (incoming != null) {
            ArrayList<Uri> uris = streams(incoming);
            for (Uri uri : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignoredError) {
                }
            }
        }
        Intent service = new Intent(this, LinkService.class);
        service.setAction(LinkService.ACTION_SEND);
        service.putExtra(LinkService.EXTRA_KIND, kind);
        service.putExtra(LinkService.EXTRA_BODY, body);
        if (incomingClip() != null) {
            service.setClipData(incomingClip());
            service.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        ContextCompat.startForegroundService(this, service);
        finish();
    }

    private android.content.ClipData incomingClip() {
        Intent incoming = getIntent();
        if (incoming == null) return null;
        return incoming.getClipData();
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
