package tv.cleanyour.testing.fixtures

import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.session.TvIdentity
import java.io.File

// Finds the repo's fixtures/ folder and the upstream submodule. The convention plugins pass the
// repo root to every test JVM as the system property "cleanyourtv.repoRoot"; without it (an IDE
// run) the loader walks up from the working directory.
object Fixtures {
    const val REPO_ROOT_PROPERTY = "cleanyourtv.repoRoot"

    val repoRoot: File by lazy {
        System.getProperty(REPO_ROOT_PROPERTY)?.let(::File)
            ?: generateSequence(File("").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "fixtures").isDirectory }
            ?: error("cannot find the repo root; set -D$REPO_ROOT_PROPERTY")
    }

    val fixturesDir: File get() = File(repoRoot, "fixtures")
    val conformanceDir: File get() = File(fixturesDir, "conformance")
    val upstreamDir: File get() = File(repoRoot, "third_party/cleanyourtv")

    fun file(relativeToFixtures: String): File =
        File(fixturesDir, relativeToFixtures).also { require(it.isFile) { "missing fixture $it" } }

    fun text(relativeToFixtures: String): String = file(relativeToFixtures).readText()

    // The reference TV capture (redacted), see fixtures/conformance/mitv-axso0-2026-10-18/README.md.
    fun miTv(): MiTvCapture = MiTvCapture(File(conformanceDir, "mitv-axso0-2026-10-18"))

    // The forge emulator session's TV (redacted), see fixtures/conformance/emu-atv28-2026-10-20/README.md.
    fun emulatorTv(): EmulatorCapture = EmulatorCapture(File(conformanceDir, "emu-atv28-2026-10-20"))

    private fun upstream(): File {
        val examples = File(upstreamDir, "examples")
        check(examples.isDirectory) {
            "upstream submodule missing at $upstreamDir; run: git submodule update --init third_party/cleanyourtv"
        }
        return upstreamDir
    }

    // Every upstream examples/<folder>/debloat.txt at the pinned commit.
    fun upstreamExamples(): List<UpstreamExample> =
        File(upstream(), "examples").listFiles().orEmpty()
            .filter { File(it, "debloat.txt").isFile }
            .sortedBy { it.name }
            .map { UpstreamExample(it.name, File(it, "debloat.txt").readText()) }

    // Every upstream themes/<name>/config.json, with the wallpaper bytes when the theme has one.
    fun upstreamThemes(): List<UpstreamTheme> =
        File(upstream(), "themes").listFiles().orEmpty()
            .filter { File(it, "config.json").isFile }
            .sortedBy { it.name }
            .map { UpstreamTheme(it.name, File(it, "config.json").readBytes(), File(it, "wallpaper.jpg").takeIf(File::isFile)?.readBytes()) }

    // Upstream launcher Config.java, for W4's differential test.
    fun upstreamConfigJava(): File =
        upstream().walkTopDown().first { it.name == "Config.java" && it.path.contains("launcher") }

    // Module card fixture APK digests (fixtures/module-cards/digests.txt): apk name to signer and apk sha256.
    fun moduleCardDigests(): Map<String, ModuleCardDigest> {
        val out = linkedMapOf<String, ModuleCardDigest>()
        var name: String? = null
        var signer: String? = null
        for (line in text("module-cards/digests.txt").lines()) {
            when {
                line.startsWith("== ") -> { name = line.removePrefix("== ").trim(); signer = null }
                line.startsWith("Signer #1 certificate SHA-256 digest:") -> signer = line.substringAfter(":").trim()
                line.startsWith("APK SHA-256:") && name != null && signer != null ->
                    out[name] = ModuleCardDigest(Sha256(signer), Sha256(line.substringAfter(":").trim()))
            }
        }
        return out
    }
}

data class UpstreamExample(val folder: String, val debloatTxt: String)
data class UpstreamTheme(val name: String, val configJson: ByteArray, val wallpaperJpg: ByteArray?)
data class ModuleCardDigest(val signer: Sha256, val apk: Sha256)

// Raw command outputs from the reference TV plus a small getprop reader. The real parsers are W4's
// (:core:probe); this only exists so fakes and tests have facts to start from.
class MiTvCapture(val dir: File) {
    private fun read(name: String) = File(dir, name).readText()

    val getpropText: String get() = read("getprop.txt")
    val packagesAllText: String get() = read("packages-all.txt")
    val packagesThirdPartyText: String get() = read("packages-3rdparty.txt")
    val packagesDisabledBeforeText: String get() = read("packages-disabled.txt")
    val packagesDisabledAfterText: String get() = read("packages-disabled-after.txt")
    val settingsGlobalText: String get() = read("settings-global.txt")
    val settingsSecureText: String get() = read("settings-secure.txt")
    val settingsSystemText: String get() = read("settings-system.txt")
    val homeActivityText: String get() = read("home-activity.txt")
    val debloatTxt: String get() = read("debloat.txt")

    val props: Map<String, String> by lazy {
        getpropText.lines().mapNotNull { PROP_LINE.matchEntire(it) }.associate { it.groupValues[1] to it.groupValues[2] }
    }

    // "package:/path/base.apk=com.example" lines to package names, in file order.
    val packages: List<String> by lazy { packagesAllText.lines().filter { it.startsWith("package:") }.map { it.substringAfterLast('=') } }
    val disabledAfter: List<String> by lazy { packagesDisabledAfterText.lines().filter { it.startsWith("package:") }.map { it.removePrefix("package:") } }

    fun settings(ns: String): Map<String, String> = read("settings-$ns.txt").lines()
        .filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    val serial: String get() = props.getValue("ro.serialno")

    // D37: first 16 hex of sha256(ro.serialno + ro.product.device).
    fun tvId(): TvId = TvId(sha256Of((serial + props.getValue("ro.product.device")).encodeToByteArray()).hex.take(16))

    fun facts(): DeviceFacts = DeviceFacts(
        brand = props.getValue("ro.product.brand"),
        manufacturer = props.getValue("ro.product.manufacturer"),
        model = props.getValue("ro.product.model"),
        device = props.getValue("ro.product.device"),
        serialLast4 = serial.takeLast(4),
        sdk = props.getValue("ro.build.version.sdk").toInt(),
        release = props.getValue("ro.build.version.release"),
        fingerprint = props.getValue("ro.build.fingerprint"),
        abis = props.getValue("ro.product.cpu.abilist").split(','),
        ramMb = MI_TV_RAM_MB,
        hasGms = "com.google.android.gms" in packages,
        adbSecure = props["ro.adb.secure"] == "1",
        adbFeatures = ANDROID_9_ADB_FEATURES,
        installRefusal = false,
        locale = props["persist.sys.locale"] ?: props.getValue("ro.product.locale"),
    )

    fun identity(): TvIdentity = facts().let {
        TvIdentity(tvId(), it.serialLast4, it.device, it.model, it.sdk, it.adbSecure, it.adbFeatures, MAX_PAYLOAD)
    }

    companion object {
        private val PROP_LINE = Regex("^\\[([^\\]]+)\\]: \\[(.*)\\]$")
        // Not in the capture: the spec's reference numbers (1 GB TV) and the CNXN banner features an
        // Android 9 adbd advertises. W2 replaces these with the fake adbd persona's banner.
        const val MI_TV_RAM_MB = 1024
        const val MAX_PAYLOAD = 1024 * 1024
        val ANDROID_9_ADB_FEATURES = setOf("shell_v2", "cmd", "stat_v2", "ls_v2", "fixed_push_mkdir", "fixed_push_symlink_timestamp")
    }
}
