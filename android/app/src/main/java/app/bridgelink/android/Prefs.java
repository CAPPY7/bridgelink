package app.bridgelink.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import app.bridgelink.android.protocol.SecretBox;

/**
 * Pairing settings. The token is AES-GCM sealed with a key that never leaves
 * the Android Keystore. If the keystore cannot be used, the token is not saved.
 */
public final class Prefs {
    public static final String TOKEN = "token";
    public static final String TOKENS = "macTokens";
    public static final String WARNING =
            "Android Keystore is unavailable. The pairing token will not be saved on this phone.";

    private static SharedPreferences cached;
    private static boolean keystoreFailed;

    private Prefs() {}

    public static synchronized boolean keystoreFailed() {
        return keystoreFailed;
    }

    public static synchronized SharedPreferences open(Context context) {
        if (cached != null) return cached;
        Context app = context.getApplicationContext();
        SharedPreferences raw = app.getSharedPreferences("bridgelink_box", Context.MODE_PRIVATE);
        migrateLegacy(app, raw);
        SecretKey key = null;
        try {
            key = keystoreKey();
        } catch (Exception error) {
            keystoreFailed = true;
        }
        cached = new SealedPrefs(raw, key);
        return cached;
    }

    private static void migrateLegacy(Context app, SharedPreferences raw) {
        if (raw.getBoolean("migrated", false)) return;
        SharedPreferences legacy = app.getSharedPreferences("bridgelink", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = raw.edit();
        for (String key : new String[]{"host", "port", "fp", "pendingFp", "deviceId", "macs"}) {
            String value = legacy.getString(key, null);
            if (value != null) editor.putString(key, value);
        }
        editor.putBoolean("migrated", true).apply();
        legacy.edit().remove(TOKEN).apply();
    }

    private static SecretKey keystoreKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias("bridgelink.aes")) {
            return (SecretKey) store.getKey("bridgelink.aes", null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                "bridgelink.aes",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static final class SealedPrefs implements SharedPreferences {
        private final SharedPreferences raw;
        private final SecretKey key;

        SealedPrefs(SharedPreferences raw, SecretKey key) {
            this.raw = raw;
            this.key = key;
        }

        @Override
        public Map<String, ?> getAll() {
            return raw.getAll();
        }

        @Override
        public String getString(String name, String def) {
            if (!secret(name)) return raw.getString(name, def);
            if (key == null) return def;
            String stored = raw.getString(name, null);
            if (stored == null || stored.isEmpty()) return def;
            try {
                return SecretBox.open(key, stored);
            } catch (Exception error) {
                return def;
            }
        }

        @Override
        public Set<String> getStringSet(String name, Set<String> def) {
            return raw.getStringSet(name, def);
        }

        @Override
        public int getInt(String name, int def) {
            return raw.getInt(name, def);
        }

        @Override
        public long getLong(String name, long def) {
            return raw.getLong(name, def);
        }

        @Override
        public float getFloat(String name, float def) {
            return raw.getFloat(name, def);
        }

        @Override
        public boolean getBoolean(String name, boolean def) {
            return raw.getBoolean(name, def);
        }

        @Override
        public boolean contains(String name) {
            return raw.contains(name);
        }

        @Override
        public Editor edit() {
            return new SealedEditor(raw.edit(), key);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            raw.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            raw.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private static final class SealedEditor implements SharedPreferences.Editor {
        private final SharedPreferences.Editor raw;
        private final SecretKey key;
        private final Map<String, String> pending = new HashMap<>();

        SealedEditor(SharedPreferences.Editor raw, SecretKey key) {
            this.raw = raw;
            this.key = key;
        }

        @Override
        public SharedPreferences.Editor putString(String name, String value) {
            if (secret(name)) {
                pending.put(name, value);
                return this;
            }
            raw.putString(name, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String name, Set<String> values) {
            raw.putStringSet(name, values);
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String name, int value) {
            raw.putInt(name, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String name, long value) {
            raw.putLong(name, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String name, float value) {
            raw.putFloat(name, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String name, boolean value) {
            raw.putBoolean(name, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String name) {
            pending.remove(name);
            raw.remove(name);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            pending.clear();
            raw.clear();
            return this;
        }

        @Override
        public boolean commit() {
            flushSecrets();
            return raw.commit();
        }

        @Override
        public void apply() {
            flushSecrets();
            raw.apply();
        }

        private void flushSecrets() {
            for (String name : new String[]{TOKEN, TOKENS}) {
                if (!pending.containsKey(name)) continue;
                String value = pending.remove(name);
                if (value == null || value.isEmpty() || key == null) {
                    raw.remove(name);
                    continue;
                }
                try {
                    raw.putString(name, SecretBox.seal(key, value));
                } catch (Exception error) {
                    raw.remove(name);
                }
            }
        }
    }

    private static boolean secret(String name) {
        return TOKEN.equals(name) || TOKENS.equals(name);
    }
}
