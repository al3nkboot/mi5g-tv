package tv.cleanyour.core.ops

import org.junit.Assert.*
import org.junit.Test

class RemoteShellTest {
    @Test fun packageListAndLeanbackLaunch() {
        assertEquals(listOf("pm", "list", "packages", "-3"), ShellTokens.tokenize(ShellRenderer.listThirdPartyPackagesLine().text))
        assertEquals(listOf("com.one.app", "net.two.app"), ShellRenderer.parsePackageList("package:com.one.app\npackage:net.two.app\nnot a package"))
        assertEquals(listOf("monkey", "-p", "com.one.app", "-c", "android.intent.category.LEANBACK_LAUNCHER", "1"), ShellTokens.tokenize(ShellRenderer.launchPackageLine("com.one.app").text))
        assertThrows(IllegalArgumentException::class.java) { ShellRenderer.launchPackageLine("-x;reboot") }
    }
}
