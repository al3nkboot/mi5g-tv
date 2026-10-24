package android.util;

// Test stub for upstream Config.java: android.util.Log is native on a device; here it is silent.
public final class Log {
    private Log() {}
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
}
