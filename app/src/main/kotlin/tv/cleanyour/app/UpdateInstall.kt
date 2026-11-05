package tv.cleanyour.app

import android.content.Context
import android.content.Intent
import android.net.Uri

// 2.0.2: whether this device can install a downloaded update at all. The app holds no install
// permission (REQUEST_INSTALL_PACKAGES stays absent, SECURITY 4.2): the browser downloads the APK and
// the system package installer installs it. Many Android TV boxes ship neither a screen that opens
// an APK nor a browser, and the update then did nothing.
object UpdateInstall {
    private const val APK = "application/vnd.android.package-archive"

    fun possible(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://downloads/update.apk"), APK)
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"))
        pm.resolveActivity(installer, 0) != null && pm.resolveActivity(browser, 0) != null
    }.getOrDefault(false)
}
