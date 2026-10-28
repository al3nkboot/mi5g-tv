package tv.cleanyour.core.ops

import tv.cleanyour.core.model.PACKAGE_RE
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.ShellLine

// Extra shell lines for the Tellyscope-style tools. Every token is quoted. Folders are the two
// shared ones only. None of these lines is `pm uninstall --user 0`.
@OptIn(tv.cleanyour.core.model.RawShell::class)
object TellyShell {
    private const val PM = "pm"
    private const val USER = "--user"

    fun dumpsysPackageLine(pkg: PackageName): ShellLine {
        require(PACKAGE_RE.matches(pkg.value)) { "invalid package" }
        return line("dumpsys", "package", pkg.value)
    }

    fun dumpsysAdbLine(): ShellLine = line("dumpsys", "adb")
    fun dumpsysCpuLine(): ShellLine = line("dumpsys", "cpuinfo")
    fun dumpsysMemLine(): ShellLine = line("dumpsys", "meminfo")
    fun dumpsysThermalLine(): ShellLine = line("dumpsys", "thermalservice")
    fun dumpsysUsbLine(): ShellLine = line("dumpsys", "usb")
    fun dumpsysBluetoothLine(): ShellLine = line("dumpsys", "bluetooth_manager")
    fun procNetDevLine(): ShellLine = line("cat", "/proc/net/dev")

    fun listSharedFolderLine(folder: String): ShellLine {
        require(known(folder))
        return line("ls", "-1", folder)
    }

    fun mkdirSharedFolderLine(folder: String): ShellLine {
        require(known(folder))
        return line("mkdir", "-p", folder)
    }

    fun copyToSharedFolderLine(src: RemotePath, folder: String, name: String): ShellLine {
        require(src.dir == RemoteDir.TMP && known(folder) && plain(name))
        return line("cp", src.absolute, "$folder/$name")
    }

    fun copyFromSharedFolderLine(folder: String, name: String, dest: RemotePath): ShellLine {
        require(dest.dir == RemoteDir.TMP && known(folder) && plain(name))
        return line("cp", "$folder/$name", dest.absolute)
    }

    fun pmInstallReplaceLine(apk: RemotePath, downgrade: Boolean): ShellLine {
        require(apk.dir == RemoteDir.TMP && apk.segments.last().endsWith(".apk"))
        val argv = mutableListOf(PM, "install", "-r")
        if (downgrade) argv += "-d"
        argv += listOf(USER, "0", apk.absolute)
        return line(*argv.toTypedArray())
    }

    fun installCreateLine(totalBytes: Long, downgrade: Boolean): ShellLine {
        require(totalBytes in 1..ShellRenderer.MAX_APK_BYTES)
        val argv = mutableListOf(PM, "install-create", "-r")
        if (downgrade) argv += "-d"
        argv += listOf("-S", totalBytes.toString())
        return line(*argv.toTypedArray())
    }

    fun installWriteLine(sessionId: String, size: Long, splitName: String, apk: RemotePath): ShellLine {
        require(Regex("^[0-9]{1,12}$").matches(sessionId))
        require(size in 1..ShellRenderer.MAX_APK_BYTES)
        require(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,40}$").matches(splitName))
        require(apk.dir == RemoteDir.TMP)
        return line(PM, "install-write", "-S", size.toString(), sessionId, splitName, apk.absolute)
    }

    fun installCommitLine(sessionId: String): ShellLine {
        require(Regex("^[0-9]{1,12}$").matches(sessionId))
        return line(PM, "install-commit", sessionId)
    }

    fun installAbandonLine(sessionId: String): ShellLine {
        require(Regex("^[0-9]{1,12}$").matches(sessionId))
        return line(PM, "install-abandon", sessionId)
    }

    fun parseInstallSession(output: String): String? =
        Regex("\\[([0-9]{1,12})\\]").find(output)?.groupValues?.get(1)

    fun pictureOffLine(jar: RemotePath, on: Boolean): ShellLine {
        require(jar.dir == RemoteDir.TMP && jar.segments == listOf("picture-off.jar"))
        return line("env", "CLASSPATH=" + jar.absolute, "app_process", "/", "PictureOff", if (on) "2" else "0")
    }

    fun svcRebootLine(): ShellLine = line("svc", "power", "reboot")

    fun copyKeptKeysLine(src: RemotePath): ShellLine {
        require(src.dir == RemoteDir.TMP && src.segments == listOf("kept-keys.txt"))
        return line("cp", src.absolute, "/data/misc/adb/adb_keys")
    }

    private fun known(folder: String) = folder == TellyFeatures.DOWNLOAD || folder == TellyFeatures.MOVIES
    private fun plain(name: String) = ShellTokens.isSafeToken(name) && "/" !in name && ".." !in name
    private fun line(vararg argv: String) = ShellLine(ShellTokens.line(argv.toList()))
}
