package app.bridgelink.android;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import app.bridgelink.android.protocol.PairingLink;

public class MainActivity extends AppCompatActivity {
    private EditText host;
    private EditText port;
    private EditText pin;
    private EditText pair;
    private EditText message;
    private TextView status;
    private TextView log;
    private MacBrowser browser;
    private final StringBuilder history = new StringBuilder();

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String next = intent.getStringExtra(LinkService.EXTRA_STATUS);
            String line = intent.getStringExtra(LinkService.EXTRA_LINE);
            if (next != null) status.setText(next);
            if (line != null && !line.isEmpty()) append(line);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        host = findViewById(R.id.host);
        port = findViewById(R.id.port);
        pin = findViewById(R.id.pin);
        pair = findViewById(R.id.pair);
        message = findViewById(R.id.message);
        status = findViewById(R.id.status);
        log = findViewById(R.id.log);

        SharedPreferences prefs = Prefs.open(this);
        host.setText(prefs.getString("host", ""));
        port.setText(prefs.getString("port", "8787"));
        pin.setText("");

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.applyLink).setOnClickListener(v -> applyLink());
        findViewById(R.id.find).setOnClickListener(v -> findMac());
        findViewById(R.id.send).setOnClickListener(v -> {
            startLink(LinkService.ACTION_SEND, message.getText().toString());
            message.setText("");
        });
        findViewById(R.id.clipboard).setOnClickListener(v -> sendClipboard());
        findViewById(R.id.stopRing).setOnClickListener(v -> {
            Intent stop = new Intent(this, LinkService.class);
            stop.setAction(LinkService.ACTION_STOP_RING);
            ContextCompat.startForegroundService(this, stop);
        });
        findViewById(R.id.forget).setOnClickListener(v -> {
            Prefs.open(this).edit().remove("token").remove("fp").remove("pendingFp").remove("pin").apply();
            status.setText("Forgot this Mac. Paste a new link or enter a PIN.");
        });
        findViewById(R.id.battery).setOnClickListener(v -> {
            Intent settings = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(settings);
        });
        applyLinkUri(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLinkUri(intent);
    }

    private void applyLinkUri(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (data == null || !"bridgelink".equals(data.getScheme())) return;
        pair.setText(data.toString());
        applyLink();
        status.setText("Pairing link opened. Tap Connect within 2 minutes.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(LinkService.EVENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(events, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(events, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(events);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (browser != null) browser.stop();
        super.onDestroy();
    }

    private void connect() {
        Prefs.open(this).edit()
                .putString("host", host.getText().toString().trim())
                .putString("port", port.getText().toString().trim())
                .remove("pin")
                .apply();
        startLink(LinkService.ACTION_CONNECT, null);
    }

    private void applyLink() {
        PairingLink link = PairingLink.parse(pair.getText().toString());
        if (link == null) {
            status.setText("That is not a BridgeLink pairing link");
            return;
        }
        host.setText(link.host);
        port.setText(Integer.toString(link.port));
        pin.setText(link.pin);
        SharedPreferences.Editor editor = Prefs.open(this).edit()
                .putString("host", link.host)
                .putString("port", Integer.toString(link.port))
                .remove("pin");
        if (link.fingerprint != null && !link.fingerprint.isEmpty()) {
            editor.putString("fp", link.fingerprint).remove("pendingFp");
        }
        editor.apply();
        status.setText("Link saved. Tap Connect before the PIN expires.");
    }

    private void findMac() {
        if (browser == null) {
            browser = new MacBrowser(this, new MacBrowser.Callback() {
                @Override
                public void onHost(String found) {
                    runOnUiThread(() -> host.setText(found));
                }

                @Override
                public void onStatus(String text) {
                    runOnUiThread(() -> status.setText(text));
                }
            });
        }
        browser.start();
    }

    private void startLink(String action, String body) {
        int number = 8787;
        try {
            number = Integer.parseInt(port.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }
        Intent intent = new Intent(this, LinkService.class);
        intent.setAction(action);
        intent.putExtra(LinkService.EXTRA_HOST, host.getText().toString().trim());
        intent.putExtra(LinkService.EXTRA_PORT, number);
        intent.putExtra(LinkService.EXTRA_PIN, pin.getText().toString().trim());
        intent.putExtra(LinkService.EXTRA_KIND, "text");
        intent.putExtra(LinkService.EXTRA_BODY, body);
        ContextCompat.startForegroundService(this, intent);
    }

    private void sendClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String text = "";
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                text = clip.getItemAt(0).getText().toString();
            }
        }
        Intent intent = new Intent(this, LinkService.class);
        intent.setAction(LinkService.ACTION_SEND);
        intent.putExtra(LinkService.EXTRA_KIND, "clipboard");
        intent.putExtra(LinkService.EXTRA_BODY, text);
        ContextCompat.startForegroundService(this, intent);
    }

    private void append(String line) {
        if (history.length() > 0) history.insert(0, "\n");
        history.insert(0, line);
        if (history.length() > 2000) history.setLength(2000);
        log.setText(history.toString());
    }
}
