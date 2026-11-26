package app.bridgelink.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import app.bridgelink.android.protocol.Features;
import app.bridgelink.android.protocol.SmsBatch;

/** Forwards a new text, and copies a code when that is turned on. The activity list does not store the text. */
public class OtpSms extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        StringBuilder body = new StringBuilder();
        String address = "";
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (SmsBatch.missing(messages)) return;
        for (SmsMessage message : messages) {
            if (message == null) continue;
            if (address.isEmpty() && message.getOriginatingAddress() != null) address = message.getOriginatingAddress();
            if (message.getMessageBody() != null) body.append(message.getMessageBody());
        }
        String text = body.toString();
        String flags = Prefs.open(context).getString("flags", "");
        if (Features.enabled(flags, "sms", false)) {
            Intent send = new Intent(context, LinkService.class);
            send.setAction(LinkService.ACTION_SEND);
            send.putExtra(LinkService.EXTRA_KIND, "sms_in");
            send.putExtra("address", address);
            send.putExtra(LinkService.EXTRA_BODY, text);
            LinkService.post(context, send);
        }
        if (!Features.enabled(flags, "otp", false)) return;
        String code = Features.otp(text);
        if (code == null) return;
        Intent send = new Intent(context, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "otp");
        send.putExtra(LinkService.EXTRA_BODY, code);
        LinkService.post(context, send);
    }
}
