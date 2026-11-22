package app.bridgelink.android;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import java.nio.charset.StandardCharsets;

import app.bridgelink.android.protocol.BrowseAgain;

/** Looks for the Mac's `_bridgelink._tcp` advertisement and reports its address. */
public final class MacBrowser {
    public interface Callback {
        void onHost(String host);
        void onStatus(String status);
        default void onStopped() {}
    }

    private final NsdManager nsd;
    private final WifiManager.MulticastLock lock;
    private final Callback callback;
    private NsdManager.DiscoveryListener listener;
    private boolean running;
    private boolean toldStop;

    private final String expectedFp;

    public MacBrowser(Context context, Callback callback) {
        this(context, callback, "");
    }

    public MacBrowser(Context context, Callback callback, String expectedFp) {
        this.nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.lock = wifi == null ? null : wifi.createMulticastLock("bridgelink");
        if (this.lock != null) this.lock.setReferenceCounted(false);
        this.callback = callback;
        this.expectedFp = expectedFp == null ? "" : expectedFp;
    }

    public void start() {
        if (nsd == null || running) return;
        running = true;
        toldStop = false;
        if (lock != null) lock.acquire();
        listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String type, int error) {
                callback.onStatus("Could not look for the Mac (" + error + ")");
                stop();
            }

            @Override
            public void onStopDiscoveryFailed(String type, int error) {
                finish();
            }

            @Override
            public void onDiscoveryStarted(String type) {
                callback.onStatus("Looking for a Mac on this Wi-Fi…");
            }

            @Override
            public void onDiscoveryStopped(String type) {
                finish();
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                resolve(service);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
            }
        };
        try {
            nsd.discoverServices("_bridgelink._tcp.", NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (RuntimeException error) {
            callback.onStatus("Could not look for the Mac");
            stop();
        }
    }

    public void stop() {
        releaseLock();
        if (nsd != null && listener != null) {
            try {
                nsd.stopServiceDiscovery(listener);
            } catch (Exception ignored) {
            }
        }
        listener = null;
        finish();
    }

    private void finish() {
        running = false;
        releaseLock();
        if (toldStop) return;
        toldStop = true;
        if (callback != null) callback.onStopped();
    }

    private void releaseLock() {
        if (lock == null) return;
        try {
            if (lock.isHeld()) lock.release();
        } catch (RuntimeException ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void resolve(NsdServiceInfo service) {
        nsd.resolveService(service, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo info, int error) {
                callback.onStatus("Found a Mac but could not read its address");
            }

            @Override
            public void onServiceResolved(NsdServiceInfo info) {
                if (info.getHost() == null) return;
                String host = info.getHost().getHostAddress();
                if (host == null || host.isEmpty()) return;
                byte[] raw = info.getAttributes() == null ? null : info.getAttributes().get("fp");
                String seen = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
                if (!expectedFp.isEmpty() && !BrowseAgain.sameMac(expectedFp, seen)) return;
                callback.onHost(host);
                callback.onStatus("Found " + host);
                stop();
            }
        });
    }
}
