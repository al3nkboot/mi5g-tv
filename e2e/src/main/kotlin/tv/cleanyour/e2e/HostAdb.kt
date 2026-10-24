package tv.cleanyour.e2e

import java.io.File
import java.util.concurrent.TimeUnit

data class HostResult(val exit: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exit == 0
    fun lines(): List<String> = stdout.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
}

// The platform's own adb client, used as the independent oracle (what is really on the TV) and
// for setup the app never does (installing fixture APKs, rebooting into a known state). It runs
// beside the system under test and never replaces it: scenarios drive :adb:session and
// :core:engine, then ask HostAdb whether the TV agrees. Test code only; nothing here reaches the app.
class HostAdb(private val adb: File, private val serial: String) {

    fun run(vararg args: String, timeoutS: Long = 60): HostResult {
        val cmd = listOf(adb.path, "-s", serial) + args
        val p = ProcessBuilder(cmd).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        if (!p.waitFor(timeoutS, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return HostResult(124, out, "timed out after ${timeoutS}s: ${cmd.joinToString(" ")}")
        }
        return HostResult(p.exitValue(), out, err)
    }

    // One shell command line, as the host adb client sends it.
    fun shell(command: String, timeoutS: Long = 60): HostResult = run("shell", command, timeoutS = timeoutS)

    fun prop(name: String): String = shell("getprop $name").stdout.trim()

    fun sdk(): Int = prop("ro.build.version.sdk").toInt()

    fun bootCompleted(): Boolean = prop("sys.boot_completed") == "1"

    fun bootId(): String = shell("cat /proc/sys/kernel/random/boot_id").stdout.trim()

    private fun packages(flags: String): Set<String> =
        shell("pm list packages $flags").lines().filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toSet()

    fun allPackages(): Set<String> = packages("")
    fun disabledPackages(): Set<String> = packages("-d")
    fun enabledSystemPackages(): Set<String> = packages("-s -e")
    fun isInstalled(pkg: String): Boolean = shell("pm path $pkg").stdout.contains("package:")

    // The activity HOME resolves to, as `flat` (pkg/cls), or null.
    fun homeActivity(): String? =
        shell("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")
            .lines().lastOrNull { it.contains('/') }?.trim()

    // The resumed activity's package, read from the activity manager (Android 9 to 14 spell it
    // mResumedActivity or topResumedActivity).
    fun resumedPackage(): String? {
        val text = shell("dumpsys activity activities").stdout
        val line = text.lineSequence().firstOrNull { it.contains("mResumedActivity") || it.contains("topResumedActivity=") }
            ?: return null
        return Regex("""\s([A-Za-z0-9_.]+)/""").find(line)?.groupValues?.get(1)
    }

    fun sha256(path: String): String? =
        shell("sha256sum $path").stdout.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.length == 64 }

    fun processAlive(pattern: String): Boolean =
        shell("ps -A -o ARGS").lines().any { it.contains(pattern) }

    fun install(apk: File): HostResult = run("install", "-r", apk.path, timeoutS = 180)
    fun uninstall(pkg: String): HostResult = run("uninstall", pkg, timeoutS = 120)
    fun enable(pkg: String): HostResult = shell("pm enable $pkg")
    fun pressHome(): HostResult = shell("input keyevent KEYCODE_HOME")

    // Waits for the device to come back after a reboot the scenario caused.
    fun waitForBoot(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        run("wait-for-device", timeoutS = timeoutMs / 1000)
        while (System.currentTimeMillis() < deadline) {
            if (bootCompleted()) return true
            Thread.sleep(2_000)
        }
        return false
    }

    // Is anything accepting ADB on the forwarded TCP endpoint? Used after debug_off.
    fun devicesLine(): String? = ProcessBuilder(adb.path, "devices").start().let { p ->
        p.waitFor(20, TimeUnit.SECONDS)
        p.inputStream.bufferedReader().readLines().firstOrNull { it.startsWith(serial) }
    }
}
