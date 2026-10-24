# R8 rules for the release build (SECURITY 4.6). Obfuscation is not a security control.
-keepattributes SourceFile,LineNumberTable
# Debug and verbose logging never reach a release build.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
-assumenosideeffects class tv.cleanyour.app.CytLog {
    public void d(java.lang.String);
}
