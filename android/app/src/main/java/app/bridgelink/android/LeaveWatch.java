package app.bridgelink.android;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.List;

import app.bridgelink.android.protocol.StageFour;

/**
 * Watches the Mac Bluetooth signal. A weak signal for long enough asks the Mac
 * to lock. This class never asks the Mac to unlock.
 */
public final class LeaveWatch {
    public interface Sink {
        void lock();

        void status(String text);
    }

    private final Context context;
    private final Sink sink;
    private final ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result != null) hear(result.getRssi());
        }
    };
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private boolean heard;
    private boolean locked;
    private boolean charging;
    private boolean told;
    private long awayMs;
    private long lastAt;

    public LeaveWatch(Context context, Sink sink) {
        this.context = context.getApplicationContext();
        this.sink = sink;
    }

    public void setCharging(boolean charging) {
        this.charging = charging;
        if (charging) awayMs = 0;
    }

    public void start() {
        stop();
        if (!"1".equals(Prefs.open(context).getString("lockOn", "0"))) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            say("Turn on Bluetooth so the Mac can lock when you leave.");
            return;
        }
        if (!allowed()) {
            say("Allow Bluetooth so the Mac can lock when you leave.");
            return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                say("This phone has no Bluetooth scan.");
                return;
            }
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(StageFour.BLE_UUID))
                    .build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build();
            scanner.startScan(List.of(filter), settings, callback);
            scanning = true;
        } catch (SecurityException error) {
            say("Allow Bluetooth so the Mac can lock when you leave.");
        }
    }

    public void stop() {
        if (scanner != null && scanning && allowed()) {
            try {
                scanner.stopScan(callback);
            } catch (SecurityException ignored) {
            }
        }
        scanning = false;
    }

    /** Called from the slow heartbeat. A missing signal counts as away. */
    public void tick() {
        if (!scanning || lastAt == 0) return;
        if (System.currentTimeMillis() - lastAt >= 5_000) hear(0);
    }

    public static void saveSetup(Context context, int[] setup) {
        Prefs.open(context).edit()
                .putString("lockOn", setup[0] == 1 ? "1" : "0")
                .putString("lockFar", Integer.toString(setup[1]))
                .putString("lockDelay", Integer.toString(setup[2]))
                .putString("atDesk", setup[3] == 1 ? "1" : "0")
                .apply();
    }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
    }

    private void hear(int rssi) {
        long now = System.currentTimeMillis();
        long elapsed = lastAt == 0 ? 0 : now - lastAt;
        lastAt = now;
        if (rssi != 0) heard = true;
        boolean enabled = "1".equals(Prefs.open(context).getString("lockOn", "0"));
        int threshold = StageFour.threshold(parse(Prefs.open(context).getString("lockFar", "70"), 70));
        long delay = StageFour.delaySeconds(parse(Prefs.open(context).getString("lockDelay", "30"), 30)) * 1000L;
        boolean atDesk = "1".equals(Prefs.open(context).getString("atDesk", "0"));
        awayMs = StageFour.awayFor(awayMs, rssi, threshold, elapsed, heard);
        if (StageFour.shouldLock(enabled, rssi, heard, threshold, awayMs, delay, charging, atDesk, locked)) {
            locked = true;
            sink.lock();
            sink.status("The phone left. Asked the Mac to lock.");
        }
        if (rssi != 0 && rssi > threshold) {
            locked = false;
            awayMs = 0;
        }
    }

    private void say(String text) {
        if (told) return;
        told = true;
        sink.status(text);
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
