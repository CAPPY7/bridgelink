package app.bridgelink.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (!app.bridgelink.android.protocol.StageRules.bootReconnect(action)) return;
        SharedPreferences prefs = Prefs.open(context);
        String host = prefs.getString("host", "");
        String token = prefs.getString("token", "");
        if (host == null || host.isEmpty() || token == null || token.length() < 16) return;
        int port = 8787;
        try {
            port = Integer.parseInt(prefs.getString("port", "8787"));
        } catch (NumberFormatException ignored) {
        }
        Intent service = new Intent(context, LinkService.class);
        service.setAction(LinkService.ACTION_CONNECT);
        service.putExtra(LinkService.EXTRA_HOST, host);
        service.putExtra(LinkService.EXTRA_PORT, port);
        service.putExtra(LinkService.EXTRA_PIN, "");
        LinkService.post(context, service);
    }
}
