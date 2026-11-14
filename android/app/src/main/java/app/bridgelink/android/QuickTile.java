package app.bridgelink.android;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** Quick Settings shortcut. Opens the app so a file or link can be sent. */
public class QuickTile extends TileService {
    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 4, open, PendingIntent.FLAG_IMMUTABLE);
        // The PendingIntent overload exists only on API 34. Older phones still need the Intent one.
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(pending);
        else startActivityAndCollapse(open);
    }
}