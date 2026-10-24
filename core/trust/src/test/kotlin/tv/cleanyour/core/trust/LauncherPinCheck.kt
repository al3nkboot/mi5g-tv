package tv.cleanyour.core.trust

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

// The build-time pin assertion for a bundled APK, run by the verifyLauncherPin and verifyCurtainPin
// Gradle tasks (JavaExec on this source set, so apksig stays out of the app) and by LauncherPinTest
// and CurtainPinTest. The sha256 pins the bytes and so package, versionCode and versionName as well
// (read once with aapt2 by the build script); the signer is checked with apksig, the library
// apksigner is built on.
data class ApkPin(val name: String, val size: Long, val sha256: String, val signerSha256: String)

object PinCheck {
    val launcher = ApkPin("launcher", PinnedLauncher.SIZE, PinnedLauncher.SHA256, PinnedLauncher.SIGNER_SHA256)
    val curtain = ApkPin("curtain", PinnedCurtain.SIZE, PinnedCurtain.SHA256, PinnedCurtain.SIGNER_SHA256)
    val byName = listOf(launcher, curtain).associateBy { it.name }

    fun problems(pin: ApkPin, apk: File): List<String> {
        if (!apk.isFile) return listOf("missing $apk")
        val out = mutableListOf<String>()
        val bytes = apk.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (bytes.size.toLong() != pin.size) out += "size ${bytes.size} is not the pinned ${pin.size}"
        if (sha != pin.sha256) out += "sha256 $sha is not the pinned ${pin.sha256}"
        val result = ApkVerifier.Builder(apk).build().verify()
        if (!result.isVerified) out += "APK signature does not verify: ${result.errors}"
        if (!result.isVerifiedUsingV2Scheme) out += "not signed with APK Signature Scheme v2"
        val certs = result.signerCertificates
        if (certs.size != 1) out += "expected one signer, found ${certs.size}"
        val signer = certs.firstOrNull()?.let { c -> MessageDigest.getInstance("SHA-256").digest(c.encoded).joinToString("") { "%02x".format(it) } }
        if (signer != pin.signerSha256) out += "signer $signer is not the pinned ${pin.signerSha256}"
        return out
    }
}

object LauncherPinCheck {
    fun problems(apk: File): List<String> = PinCheck.problems(PinCheck.launcher, apk)
}

// args: the pin's name ("launcher" or "curtain") and the APK; one argument alone is the launcher.
fun main(args: Array<String>) {
    val pin = if (args.size == 1) PinCheck.launcher else PinCheck.byName[args[0]] ?: error("unknown pin ${args[0]}")
    val apk = File(args.last())
    val problems = PinCheck.problems(pin, apk)
    if (problems.isNotEmpty()) {
        System.err.println("bundled ${pin.name} does not match its pin (${apk.name}):")
        problems.forEach { System.err.println("  $it") }
        exitProcess(1)
    }
    println("bundled ${pin.name} matches its pin: ${pin.sha256}, signer ${pin.signerSha256}")
}
