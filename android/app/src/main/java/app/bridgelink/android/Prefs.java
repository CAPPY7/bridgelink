package app.bridgelink.android;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

/** Pairing secrets. The Android Keystore wraps this file. A private file is the fallback. */
public final class Prefs {
    private static SharedPreferences cached;

    private Prefs() {}

    public static synchronized SharedPreferences open(Context context) {
        if (cached != null) return cached;
        Context app = context.getApplicationContext();
        try {
            String master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            cached = EncryptedSharedPreferences.create(
                    "bridgelink_secure",
                    master,
                    app,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception error) {
            cached = app.getSharedPreferences("bridgelink", Context.MODE_PRIVATE);
        }
        return cached;
    }
}
