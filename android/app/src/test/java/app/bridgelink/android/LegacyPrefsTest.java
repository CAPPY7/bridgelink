package app.bridgelink.android;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/** Old phone settings are copied once, even when the keystore cannot seal the token. */
public class LegacyPrefsTest {
    @Test
    public void aSecondCopyDoesNotOverwriteHost() {
        MemoryPrefs raw = new MemoryPrefs();
        MemoryPrefs legacy = new MemoryPrefs();
        legacy.values.put("host", "old-mac");
        legacy.values.put("token", "legacy-token-value");
        Prefs.migrateLegacy(raw, legacy, null);
        raw.values.put("host", "new-mac");
        legacy.values.put("host", "older-mac");
        Prefs.migrateLegacy(raw, legacy, null);
        assertEquals("new-mac", raw.getString("host", ""));
        assertEquals("legacy-token-value", legacy.getString("token", ""));
    }

    private static final class MemoryPrefs implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return values;
        }

        @Override
        public String getString(String key, String def) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : def;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> def) {
            return def;
        }

        @Override
        public int getInt(String key, int def) {
            return def;
        }

        @Override
        public long getLong(String key, long def) {
            return def;
        }

        @Override
        public float getFloat(String key, float def) {
            return def;
        }

        @Override
        public boolean getBoolean(String key, boolean def) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : def;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> next = new HashMap<>();
                private final Set<String> gone = new HashSet<>();

                @Override
                public Editor putString(String key, String value) {
                    next.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    next.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    gone.add(key);
                    return this;
                }

                @Override
                public Editor clear() {
                    return this;
                }

                @Override
                public boolean commit() {
                    values.keySet().removeAll(gone);
                    values.putAll(next);
                    return true;
                }

                @Override
                public void apply() {
                    commit();
                }
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }
    }
}
