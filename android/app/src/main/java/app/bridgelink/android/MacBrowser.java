package app.bridgelink.android;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

/** Looks for the Mac's `_bridgelink._tcp` advertisement and reports its address. */
public final class MacBrowser {
    public interface Callback {
        void onHost(String host);
        void onStatus(String status);
    }

    private final NsdManager nsd;
    private final WifiManager.MulticastLock lock;
    private final Callback callback;
    private NsdManager.DiscoveryListener listener;
    private boolean running;

    public MacBrowser(Context context, Callback callback) {
        this.nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.lock = wifi == null ? null : wifi.createMulticastLock("bridgelink");
        if (this.lock != null) this.lock.setReferenceCounted(false);
        this.callback = callback;
    }

    public void start() {
        if (nsd == null || running) return;
        running = true;
        if (lock != null) lock.acquire();
        listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String type, int error) {
                callback.onStatus("Could not look for the Mac (" + error + ")");
                stop();
            }

            @Override
            public void onStopDiscoveryFailed(String type, int error) {
                running = false;
            }

            @Override
            public void onDiscoveryStarted(String type) {
                callback.onStatus("Looking for a Mac on this Wi-Fi…");
            }

            @Override
            public void onDiscoveryStopped(String type) {
                running = false;
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                resolve(service);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
            }
        };
        nsd.discoverServices("_bridgelink._tcp.", NsdManager.PROTOCOL_DNS_SD, listener);
    }

    public void stop() {
        if (lock != null && lock.isHeld()) lock.release();
        if (nsd != null && listener != null) {
            try {
                nsd.stopServiceDiscovery(listener);
            } catch (Exception ignored) {
            }
        }
        listener = null;
        running = false;
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
                callback.onHost(host);
                callback.onStatus("Found " + host);
                stop();
            }
        });
    }
}
