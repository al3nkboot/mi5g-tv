package tv.cleanyour.e2e

import tv.cleanyour.core.model.Endpoint
import java.io.File

// Where the emulator under test is, as scripts/emulator-session.sh exports it. Every value can
// also come from a system property of the same name in lower case with dots
// (CYT_E2E_ENDPOINT or -Dcyt.e2e.endpoint), so a scenario can be run from an IDE.
data class E2eConfig(
    val adb: File,                 // the platform-tools adb binary, used only as the test oracle
    val serial: String,            // the emulator as the host adb server names it (emulator-5554)
    val endpoint: Endpoint.PlainTcp, // adbd on TCP, forwarded: what the app's session connects to
    val adbKey: File,              // the host's adbkey (PKCS#8 PEM), which the emulator trusts
    val api: Int,
    val outDir: File,              // timings and logs (trend data, never a gate in 0.1)
    val launcherApk: File?,        // the pinned launcher APK, when W5's bundled copy is available
    val crashingLauncherApk: File?, // the S10 fixture: a HOME activity that crashes on start
) {
    companion object {
        private fun value(name: String): String? =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: System.getProperty(name.lowercase().replace('_', '.'))?.takeIf { it.isNotBlank() }

        // Null when the harness is not inside an emulator session; scenarios then fail with a
        // message that names the script to run, rather than skipping silently.
        fun fromEnvironment(): E2eConfig? {
            val endpoint = value("CYT_E2E_ENDPOINT") ?: return null
            val (host, port) = endpoint.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "5555" }.toInt() }
            return E2eConfig(
                adb = File(value("CYT_E2E_ADB") ?: "adb"),
                serial = value("CYT_E2E_SERIAL") ?: "emulator-5554",
                endpoint = Endpoint.PlainTcp(host, port),
                adbKey = File(value("CYT_E2E_ADBKEY") ?: (System.getProperty("user.home") + "/.android/adbkey")),
                api = value("CYT_E2E_API")?.toInt() ?: 28,
                outDir = File(value("CYT_E2E_OUT") ?: "build/e2e"),
                launcherApk = value("CYT_E2E_LAUNCHER_APK")?.let(::File)?.takeIf { it.isFile },
                crashingLauncherApk = value("CYT_E2E_CRASHING_LAUNCHER_APK")?.let(::File)?.takeIf { it.isFile },
            )
        }

        const val NOT_IN_SESSION =
            "No emulator session: run scripts/emulator-on-forge.sh run 28 (forge) or scripts/emulator-session.sh 28 " +
                "./gradlew :e2e:emulatorTest (a host with KVM), which export CYT_E2E_ENDPOINT and friends."
    }
}
