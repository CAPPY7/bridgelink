package app.bridgelink.android;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import app.bridgelink.android.protocol.StageRules;
import app.bridgelink.android.protocol.Transfers;

/** Buttons on a file or link notice. Nothing opens until the user taps. */
public final class FileActions extends BroadcastReceiver {
    public static final String OPEN_FILE = "app.bridgelink.android.OPEN_FILE";
    public static final String SHOW_DOWNLOADS = "app.bridgelink.android.SHOW_DOWNLOADS";
    public static final String OPEN_LINK = "app.bridgelink.android.OPEN_LINK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        try {
            if (OPEN_FILE.equals(intent.getAction())) {
                String name = intent.getStringExtra("name");
                String raw = intent.getStringExtra("uri");
                if (!StageRules.canOfferOpen(name) || raw == null || raw.isEmpty()) return;
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(raw));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(view);
            } else if (SHOW_DOWNLOADS.equals(intent.getAction())) {
                Intent view = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
            } else if (OPEN_LINK.equals(intent.getAction())) {
                String url = Transfers.acceptUrl(intent.getStringExtra("url"));
                if (url == null) return;
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
            }
        } catch (Exception ignored) {
        }
    }
}
