package app.bridgelink.android;

import android.content.Context;
import android.content.Intent;

/**
 * The phone link service. The work lives in LinkCore so this file stays short.
 * The names below stay here because the rest of the app already uses them.
 */
public class LinkService extends LinkCore {
    public static final String ACTION_CONNECT = LinkCore.ACTION_CONNECT;
    public static final String ACTION_SEND = LinkCore.ACTION_SEND;
    public static final String ACTION_STOP_RING = LinkCore.ACTION_STOP_RING;
    public static final String ACTION_CONFIRM = LinkCore.ACTION_CONFIRM;
    public static final String ACTION_FORGET = LinkCore.ACTION_FORGET;
    public static final String ACTION_FIND_MAC = LinkCore.ACTION_FIND_MAC;
    public static final String EVENT = LinkCore.EVENT;
    public static final String EXTRA_HOST = LinkCore.EXTRA_HOST;
    public static final String EXTRA_PORT = LinkCore.EXTRA_PORT;
    public static final String EXTRA_PIN = LinkCore.EXTRA_PIN;
    public static final String EXTRA_KIND = LinkCore.EXTRA_KIND;
    public static final String EXTRA_BODY = LinkCore.EXTRA_BODY;
    public static final String EXTRA_STATUS = LinkCore.EXTRA_STATUS;
    public static final String EXTRA_LINE = LinkCore.EXTRA_LINE;

    public static void post(Context context, Intent intent) {
        LinkCore.post(context, intent);
    }

    public static void onNeedKey(Runnable ask) {
        LinkCore.onNeedKey(ask);
    }

    public static void sendRaw(byte[] framed) {
        LinkCore.sendRaw(framed);
    }

    public static void postStatus(String line) {
        LinkCore.postStatus(line);
    }
}
