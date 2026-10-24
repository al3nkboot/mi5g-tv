package tv.cleanyour.core.backup

import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.Snapshot
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

// The TV state a laptop restore can change, as plain values: package states (absent = not
// installed), HOME, settings ("ns/key" to value, absent = not set) and the files we wrote.
data class TvState(val packages: Map<String, String>, val home: String?, val settings: Map<String, String>, val files: Set<String>) {

    // What the app did, replayed forward on the fake TV (the effect each op has on a real TV).
    fun apply(changes: List<ChangeRecord>): TvState {
        val p = packages.toMutableMap(); var h = home; val s = settings.toMutableMap(); val f = files.toMutableSet()
        for (c in changes.sortedBy { it.atMs }) {
            when (val op = c.op) {
                is Op.Disable -> p[op.pkg.value] = "DISABLED_USER"
                is Op.Enable -> p[op.pkg.value] = "ENABLED"
                is Op.DefaultState -> p[op.pkg.value] = "DEFAULT"
                is Op.Install -> p[op.pkg.value] = "ENABLED"
                is Op.InstallStream -> p[op.pkg.value] = "ENABLED"
                is Op.Uninstall -> { p.remove(op.pkg.value); f.removeAll { it.startsWith("/sdcard/Android/data/${op.pkg.value}/") } }   // its folder goes with it
                is Op.SettingsPut -> {
                    val k = op.key.ns.name.lowercase(Locale.ROOT) + "/" + op.key.key
                    if (op.value == null) s.remove(k) else s[k] = op.value!!
                }
                is Op.SetHome -> h = op.component.flat
                // our own restore point files stay on the TV by design and are not part of a restore
                is Op.PushAsset -> if (op.dest.dir != RemoteDir.RESTORE) f += op.dest.absolute
                is Op.RemoveFile -> f -= op.path.absolute
                else -> {}
            }
        }
        return TvState(p, h, s, f)
    }

    companion object {
        fun of(s: Snapshot) = TvState(
            packages = s.packages.entries.associate { (k, v) -> k.value to v.enabled.name },
            home = s.home.resolved?.flat,
            settings = s.settings.entries.filter { it.value != null }
                .associate { (k, v) -> k.ns.name.lowercase(Locale.ROOT) + "/" + k.key to v!! },
            files = emptySet(),
        )
    }
}

// A fake TV on disk for the stub adb (src/test/resources/stub/adb), plus a runner for scripts.
class FakeTvDir(val root: File, val adbSerial: String, val props: Map<String, String>) {
    val tv = File(root, "tv")
    val bin = File(root, "bin")

    fun write(state: TvState) {
        tv.deleteRecursively()
        File(tv, "props").mkdirs(); File(tv, "pkg").mkdirs(); File(tv, "settings").mkdirs(); File(tv, "files").mkdirs()
        File(tv, "adb-serial").writeText(adbSerial)
        File(tv, "calls").writeText("")
        File(tv, "lines").writeText("")
        props.forEach { (k, v) -> File(tv, "props/$k").writeText(v) }
        state.packages.forEach { (k, v) -> File(tv, "pkg/$k").writeText(v) }
        state.home?.let { File(tv, "home").writeText(it) }
        state.settings.forEach { (k, v) -> File(tv, "settings/$k").also { it.parentFile.mkdirs() }.writeText(v) }
        state.files.forEach { File(tv, "files/" + it.replace('/', '%')).writeText("") }
        bin.mkdirs()
        val adb = File(bin, "adb")
        adb.writeBytes(checkNotNull(FakeTvDir::class.java.getResourceAsStream("/stub/adb")).use { it.readBytes() })
        check(adb.setExecutable(true))
    }

    fun read(): TvState = TvState(
        packages = File(tv, "pkg").listFiles().orEmpty().associate { it.name to it.readText() },
        home = File(tv, "home").takeIf { it.isFile }?.readText(),
        settings = File(tv, "settings").listFiles().orEmpty().flatMap { ns -> ns.listFiles().orEmpty().map { "${ns.name}/${it.name}" to it.readText() } }.toMap(),
        files = File(tv, "files").listFiles().orEmpty().map { it.name.replace('%', '/') }.toSet(),
    )

    fun calls(): List<String> = File(tv, "calls").readLines().filter { it.isNotEmpty() }
    fun lines(): List<String> = File(tv, "lines").readLines().filter { it.isNotEmpty() }

    // Runs `shell script args` with the stub adb first on PATH (or PATH replaced by pathOverride).
    fun run(shell: List<String>, script: File, args: List<String> = emptyList(), pathOverride: String? = null): ShellRun {
        val pb = ProcessBuilder(shell + script.absolutePath + args).directory(root)
        pb.environment()["PATH"] = pathOverride ?: (bin.absolutePath + File.pathSeparator + System.getenv("PATH").orEmpty())
        pb.environment()["STUB_TV"] = tv.absolutePath
        pb.environment().remove("ADB")
        val out = File(root, "out.txt"); val err = File(root, "err.txt")
        pb.redirectOutput(out).redirectError(err).redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        val p = pb.start()
        check(p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); "script timed out" }
        return ShellRun(p.exitValue(), out.readText(), err.readText())
    }
}

data class ShellRun(val exit: Int, val out: String, val err: String) {
    override fun toString() = "exit $exit\n--- stdout\n$out--- stderr\n$err"
}

// POSIX shells found on this machine. /bin/sh is required (forge's build container has dash
// there); the others run when installed.
object Shells {
    fun available(): List<List<String>> {
        val candidates = listOf(
            listOf("/bin/sh"), listOf("dash"), listOf("bash", "--posix"), listOf("busybox", "sh"),
            listOf("mksh"), listOf("yash"), listOf("zsh", "--emulate", "sh"),
        )
        return candidates.filter { c -> c[0].startsWith("/") && File(c[0]).canExecute() || which(c[0]) != null }
            .map { c -> if (c[0].startsWith("/")) c else listOf(which(c[0])!!.absolutePath) + c.drop(1) }
            .distinctBy { it.joinToString(" ") }
    }

    fun which(name: String): File? = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }
}
