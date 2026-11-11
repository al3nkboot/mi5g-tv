package tv.cleanyour.core.flows

// 2.3: when the TV ended the launcher's install, whether it is clearly low on storage. Ported from
// cleanyourtv-app InstallStorage (0.4.8, commit 73cbee1, issues #3221, #3101, #2851), MIT, Copyright
// (c) 2026 udhaykumarbala.
object InstallStorage {
    // Below this the TV is clearly low on storage, whatever the APK.
    const val FLOOR_MB = 200L

    // "Clearly low" only, so an install the TV ended for another reason is not blamed on storage: an
    // install needs the APK on /data and about as much again for its compiled code, and Android keeps
    // a reserve of its own (5 % of /data, at most 500 MB).
    fun low(freeMb: Int, apkBytes: Long): Boolean {
        val apkMb = (apkBytes + MB - 1) / MB
        return freeMb.toLong() < maxOf(FLOOR_MB, 2 * apkMb)
    }

    private const val MB = 1024L * 1024
}
