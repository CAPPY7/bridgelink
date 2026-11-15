package app.bridgelink.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import app.bridgelink.android.protocol.Features;

/** Copies a code from an SMS. The message text is not stored. */
public class OtpSms extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (!Features.enabled(Prefs.open(context).getString("flags", ""), "otp", false)) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage message : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            if (message != null && message.getMessageBody() != null) body.append(message.getMessageBody());
        }
        String code = Features.otp(body.toString());
        if (code == null) return;
        Intent send = new Intent(context, LinkService.class);
        send.setAction(LinkService.ACTION_SEND);
        send.putExtra(LinkService.EXTRA_KIND, "otp");
        send.putExtra(LinkService.EXTRA_BODY, code);
        LinkService.post(context, send);
    }
}
