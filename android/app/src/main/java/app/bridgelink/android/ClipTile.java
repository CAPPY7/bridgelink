package app.bridgelink.android;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** Quick Settings. Opens a short screen so Android will allow the clipboard to be read. */
public class ClipTile extends TileService {
    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        Intent open = new Intent(this, ClipSendActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        PendingIntent pending = PendingIntent.getActivity(this, 5, open, PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(pending);
        else startActivityAndCollapse(open);
    }
}
