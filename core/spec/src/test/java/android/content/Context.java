package android.content;

import android.content.res.Resources;
import java.io.File;

// Test stub for upstream Config.java: the Context calls it makes. The preferences stand in for the
// launcher's private launcher_settings file; null gives a fresh empty store on each call.
public class Context {
    public static final int MODE_PRIVATE = 0;

    private final File externalFilesDir;
    private final Resources resources;
    private final SharedPreferences preferences;

    public Context(File externalFilesDir, Resources resources, SharedPreferences preferences) {
        this.externalFilesDir = externalFilesDir;
        this.resources = resources;
        this.preferences = preferences;
    }

    public File getExternalFilesDir(String type) { return externalFilesDir; }

    public Resources getResources() { return resources; }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences != null ? preferences : new MemoryPreferences();
    }
}
