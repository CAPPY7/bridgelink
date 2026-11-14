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
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "bridgelink";
    private EditText host;
    private EditText port;
    private EditText pin;
    private EditText message;
    private TextView status;
    private TextView log;
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
        message = findViewById(R.id.message);
        status = findViewById(R.id.status);
        log = findViewById(R.id.log);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        host.setText(prefs.getString("host", ""));
        port.setText(prefs.getString("port", "8787"));
        pin.setText(prefs.getString("pin", ""));

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        findViewById(R.id.connect).setOnClickListener(v -> connect());
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

    private void connect() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("host", host.getText().toString().trim())
                .putString("port", port.getText().toString().trim())
                .putString("pin", pin.getText().toString().trim())
                .apply();
        startLink(LinkService.ACTION_CONNECT, null);
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
