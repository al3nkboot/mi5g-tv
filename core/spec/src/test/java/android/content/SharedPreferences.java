package android.content;

// Test stub for upstream Config.java (v2.4.0): the SharedPreferences calls it makes.
public interface SharedPreferences {
    String getString(String key, String defValue);
    int getInt(String key, int defValue);
    boolean getBoolean(String key, boolean defValue);
    Editor edit();

    interface Editor {
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        boolean commit();
        void apply();
    }
}
