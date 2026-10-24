package android.content;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// An in-memory SharedPreferences for the test: the launcher's on-TV choices (launcher_settings).
// An Editor applies its puts and removes on commit or apply, removes first, as Android does.
public final class MemoryPreferences implements SharedPreferences {
    public final Map<String, Object> values = new HashMap<>();

    @Override public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override public Editor edit() {
        return new Editor() {
            private final Map<String, Object> puts = new HashMap<>();
            private final Set<String> removes = new HashSet<>();

            @Override public Editor putString(String key, String value) { puts.put(key, value); return this; }
            @Override public Editor putInt(String key, int value) { puts.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { puts.put(key, value); return this; }
            @Override public Editor remove(String key) { removes.add(key); return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                for (String k : removes) values.remove(k);
                values.putAll(puts);
            }
        };
    }
}
