// Picture off with audio still playing. Runs on the TV as the shell user via app_process.
// SurfaceControl.setDisplayPowerMode keeps the TV awake, so ADB stays up. Android 14 moved
// the method to a class the shell VM cannot load, so this answers 0 there and the app does
// not offer it above Android 13.
public final class PictureOff {
    public static void main(String[] args) {
        int mode = 0;
        if (args.length > 0) {
            try {
                mode = Integer.parseInt(args[0]);
            } catch (Throwable ignored) {
                mode = 0;
            }
        }
        System.out.print(power(mode) ? "1\n" : "0\n");
        System.out.flush();
    }

    /** 0 is picture off, 2 is picture on. */
    private static boolean power(int mode) {
        try {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            Object display;
            try {
                display = sc.getMethod("getInternalDisplayToken").invoke(null);
            } catch (NoSuchMethodException e) {
                display = sc.getMethod("getBuiltInDisplay", int.class).invoke(null, 0);
            }
            if (display == null) return false;
            sc.getMethod("setDisplayPowerMode", Class.forName("android.os.IBinder"), int.class)
                .invoke(null, display, mode);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
