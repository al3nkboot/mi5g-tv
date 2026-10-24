package tv.cleanyour.core.probe

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.testing.fixtures.FakeTvSession
import tv.cleanyour.testing.fixtures.Fixtures
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Launcher v2.4.0 plan finding 4: the snapshot carries the installed launcher's (and the TV
// curtain's) signer, read from the APK Signing Block of a copy of the installed APK. D3: the
// owners of the saved Wi-Fi networks, cname words only.
class SignerAndWifiTest {
    private fun p(s: String) = PackageName(s)
    private val prober = AdbProber(ProbeFixtures.clock())
    private val bundled = File(Fixtures.repoRoot, "android/platform/src/main/assets/bundled")
    // SHA-256 of the launcher key K1's certificate, as PinnedLauncher.SIGNER_SHA256 holds it.
    private val k1 = Sha256("576418154bec6505bfc709ccaa6ae85b6e09d98eb89f4a25147ca1b4b0505132")

    @Test fun signerOfTheBundledApks() {
        assertEquals(k1, ApkSigners.signerSha256(File(bundled, "cleanyourtv-launcher.apk").readBytes()))
        assertEquals(k1, ApkSigners.signerSha256(File(bundled, "cleanyourtv-curtain.apk").readBytes()))
    }

    // RF-LAUNCHER-240 F2: Android 8 ignores a v3 block, and the APK Signing Block is not covered by
    // v2's signature, so a v3 block alone, or one naming another certificate than v2, could carry K1
    // into an APK signed with any key. Only a certificate the TV verified counts: v2 required, v3
    // (when present) the same.
    @Test fun onlyACertificateTheTvVerifiedCounts() {
        val apk = File(bundled, "cleanyourtv-launcher.apk").readBytes()
        val cert = ApkSigners.certificate(apk)!!
        assertEquals(k1, ApkSigners.signerSha256(withPairs(apk) { it == V2 }), "v2 alone is what Android 8 verifies")
        assertNull(ApkSigners.signerSha256(withPairs(apk) { it == V3 }), "a v3 block alone (K1 planted for Android 8)")
        assertNull(ApkSigners.signerSha256(withPairs(apk) { false }), "no scheme block")
        assertEquals(k1, ApkSigners.signerSha256(withPairs(apk) { true }), "the rebuilt block reads as before")
        // The certificate appears once per scheme; another certificate in either one means v2 and v3
        // disagree (an APK signed v2 with another key, K1 planted in v3), so the signer is unknown.
        val at = occurrences(apk, cert)
        assertEquals(2, at.size)
        for (i in at) {
            val other = apk.copyOf().also { it[i + cert.size / 2] = (it[i + cert.size / 2].toInt() xor 0x55).toByte() }
            assertNull(ApkSigners.signerSha256(other), "certificate at $i changed")
        }
    }

    private val V2 = 0x7109871a
    private val V3 = 0xf05368c0.toInt()

    private fun occurrences(hay: ByteArray, needle: ByteArray): List<Int> =
        (0..hay.size - needle.size).filter { i -> (needle.indices).all { hay[i + it] == needle[it] } }

    // The APK with its signing block rebuilt from the ID-value pairs `keep` accepts, the central
    // directory moved after it and the EOCD's offset fixed; nothing is signed again.
    private fun withPairs(apk: ByteArray, keep: (Int) -> Boolean): ByteArray {
        fun le(b: ByteArray) = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val b = le(apk)
        val eocd = (apk.size - 22 downTo 0).first { b.getInt(it) == 0x06054b50 }
        val cd = b.getInt(eocd + 16)
        val magicAt = cd - 16
        val size = b.getLong(magicAt - 8)
        val start = (cd - size - 8).toInt()
        val out = java.io.ByteArrayOutputStream()
        var p = start + 8
        while (p < magicAt - 8) {
            val len = b.getLong(p).toInt()
            if (keep(b.getInt(p + 8))) out.write(apk, p, 8 + len)
            p += 8 + len
        }
        val pairs = out.toByteArray()
        val newSize = pairs.size + 8L + 16
        fun long(v: Long) = ByteArray(8).also { le(it).putLong(v) }
        val block = long(newSize) + pairs + long(newSize) + apk.copyOfRange(magicAt, cd)
        val head = apk.copyOfRange(0, start)
        val central = apk.copyOfRange(cd, eocd)
        val end = apk.copyOfRange(eocd, apk.size).also { le(it).putInt(16, head.size + block.size) }
        return head + block + central + end
    }

    @Test fun anythingElseIsUnknown() {
        val apk = File(bundled, "cleanyourtv-launcher.apk").readBytes()
        assertNull(ApkSigners.signerSha256(ByteArray(0)))
        assertNull(ApkSigners.signerSha256("not an apk".toByteArray()))
        assertNull(ApkSigners.signerSha256(apk.copyOf(apk.size / 2)))
        // A zip without a signing block (v1 or unsigned).
        val zip = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("a")); z.write(1); z.closeEntry() }
        }.toByteArray()
        assertNull(ApkSigners.signerSha256(zip))
        // Every single-byte change in the signing block either reads the same certificate, another
        // one, or nothing; it never throws.
        val r = kotlin.random.Random(7)
        repeat(2_000) {
            val bad = apk.copyOf()
            val at = bad.size - 1 - r.nextInt(minOf(bad.size, 12_000))
            bad[at] = (bad[at].toInt() xor (1 shl r.nextInt(8))).toByte()
            ApkSigners.signerSha256(bad)
        }
    }

    // The TV: the copy line (any per-copy name) answers with copyExit and, on success, puts `apk` at
    // its destination; `installedSha` answers the installed APK's hash read.
    private fun session(installed: Boolean, apk: ByteArray?, copyExit: Int = 0, installedSha: Sha256? = null): FakeTvSession {
        val packages = Fixtures.miTv().packagesAllText + if (installed) "package:/data/app/com.gathin.tvhome-1/base.apk=com.gathin.tvhome\n" else ""
        lateinit var s: FakeTvSession
        val rec = ProbeFixtures.miTvRecording()
            .on(ProbeScripts.PACKAGES.text, packages)
            .on(Regex(".* cp .* (/data/local/tmp/cleanyourtv/signer-[0-9a-f]{16}\\.apk)$")) { m ->
                if (copyExit == 0) apk?.let { s.files[m.groupValues[1]] = it }
                tv.cleanyour.core.session.ExecResult(copyExit, "", "", false, 1)
            }
            .on(Regex("^rm -f (/data/local/tmp/cleanyourtv/signer-[0-9a-f]{16}\\.apk)$")) { m -> s.files.remove(m.groupValues[1]); tv.cleanyour.core.session.ExecResult(0, "", "", false, 1) }
            .on(ProbeScripts.installedSha256(p("com.gathin.tvhome")).text, installedSha?.let { "${it.hex}  /data/app/com.gathin.tvhome-1/base.apk\n" } ?: "", exit = if (installedSha == null) 1 else 0)
        s = FakeTvSession(recording = rec)
        return s
    }

    private fun copies(s: FakeTvSession) = s.execLines.filter { " cp " in it }

    @Test fun theSnapshotCarriesTheInstalledLaunchersSigner() = runBlocking<Unit> {
        val apk = File(bundled, "cleanyourtv-launcher.apk").readBytes()
        val s = session(installed = true, apk = apk)
        val snap = prober.snapshot(s, SnapshotReason.BASELINE)
        assertEquals(k1, snap.packages.getValue(p("com.gathin.tvhome")).signer)
        assertTrue(snap.packages.filterKeys { it != p("com.gathin.tvhome") }.values.all { it.signer == null })
        // Copy (size checked first), pull, delete; the copy never stays on the TV.
        val copy = copies(s).single()
        assertTrue("stat -c %s" in copy && "-le 16777216" in copy, copy)
        val name = Regex("signer-[0-9a-f]{16}\\.apk").find(copy)!!.value
        assertTrue(s.execLines.last().startsWith("rm -f") && name in s.execLines.last())
        assertTrue(s.files.keys.none { "signer-" in it })
        assertEquals(2, s.calls.count { it is FakeTvSession.Call.Pull })
        // Each snapshot copies under a name of its own (RF-LAUNCHER-240 F11).
        val again = session(installed = true, apk = apk)
        prober.snapshot(again, SnapshotReason.BASELINE)
        assertTrue(name !in copies(again).single())
    }

    // An installed APK that hashes to a pinned one has that pin's signer: nothing is copied.
    @Test fun aPinnedApkIsNotCopied() = runBlocking<Unit> {
        val pinned = Sha256("ab".repeat(32))
        val withPins = AdbProber(ProbeFixtures.clock(), known = mapOf(pinned to k1))
        val s = session(installed = true, apk = null, installedSha = pinned)
        assertEquals(k1, withPins.snapshot(s, SnapshotReason.BASELINE).packages.getValue(p("com.gathin.tvhome")).signer)
        assertTrue(copies(s).isEmpty())
        // Another APK: copied and read as before.
        val other = session(installed = true, apk = File(bundled, "cleanyourtv-launcher.apk").readBytes(), installedSha = Sha256("cd".repeat(32)))
        assertEquals(k1, withPins.snapshot(other, SnapshotReason.BASELINE).packages.getValue(p("com.gathin.tvhome")).signer)
        assertEquals(1, copies(other).size)
    }

    @Test fun anUnreadableSignerIsUnknownAndTheSnapshotStillWorks() = runBlocking<Unit> {
        val failed = prober.snapshot(session(installed = true, apk = null, copyExit = 1), SnapshotReason.BASELINE)
        assertNull(failed.packages.getValue(p("com.gathin.tvhome")).signer)
        val garbage = prober.snapshot(session(installed = true, apk = "junk".toByteArray()), SnapshotReason.BASELINE)
        assertNull(garbage.packages.getValue(p("com.gathin.tvhome")).signer)
        // Not installed: nothing is copied.
        val none = session(installed = false, apk = null)
        prober.snapshot(none, SnapshotReason.BASELINE)
        assertTrue(copies(none).isEmpty())
    }

    // tests/test_setupwraith_guard.py of v2.4.0, on the phone's side, plus RF-LAUNCHER-240 F14:
    // nothing saved (a cable) is told apart from a TV that could not say. The answers are what the
    // probe line prints on the API 28 TV emulator (its AndroidWifi network, saved by the system).
    @Test fun savedWifi() = runBlocking<Unit> {
        val wizard = "com.google.android.tungsten.setupwraith"
        fun read(out: String, exit: Int = 0) = runBlocking {
            prober.savedWifi(FakeTvSession(recording = ProbeFixtures.miTvRecording().on(ProbeScripts.WIFI_OWNERS.text, out, exit = exit)))
        }
        fun list(vararg owners: String) = "Configured networks Begin\n" + owners.mapIndexed { i, o -> "ID: $i SSID\ncname=$o\n" }.joinToString("") + "Configured networks End\n"
        val bySystem = read(list("android.uid.system:1000"))
        assertEquals(SavedWifi(true, 1, setOf("android.uid.system:1000")), bySystem)
        assertEquals(SavedWifi.Verdict.OTHERS_OWN, bySystem.verdict(wizard))
        assertEquals(SavedWifi.Verdict.WIZARD_OWNS, read(list("$wizard:10031")).verdict(wizard))
        assertEquals(SavedWifi.Verdict.WIZARD_OWNS, read(list("android.uid.system:1000", "$wizard:10031")).verdict(wizard), "one network each")
        assertEquals(SavedWifi.Verdict.WIZARD_OWNS, SavedWifi(true, 1, setOf("com.example.oobe:10050")).verdict("com.example.oobe"), "a wizard a profile marks")
        // Nothing saved: the list is there and empty, so the wizard owns nothing.
        val none = read(list())
        assertEquals(SavedWifi(true, 0, emptySet()), none)
        assertEquals(SavedWifi.Verdict.NOTHING_SAVED, none.verdict(wizard))
        // Could not tell: dumpsys failed, printed no list (another format), or a saved network names no owner.
        assertEquals(SavedWifi.Verdict.CANNOT_TELL, read("", exit = 1).verdict(wizard))
        assertEquals(SavedWifi.Verdict.CANNOT_TELL, read("Wi-Fi is off\n").verdict(wizard))
        assertEquals(SavedWifi.Verdict.CANNOT_TELL, read("cname=android.uid.system:1000\n").verdict(wizard), "owners but no list")
        assertEquals(SavedWifi.Verdict.CANNOT_TELL, read("Configured networks Begin\nID: 0 SSID\nConfigured networks End\n").verdict(wizard))
        assertEquals(SavedWifi.Verdict.CANNOT_TELL, read("Configured networks Begin\n").verdict(wizard), "cut off before the end")
        // ...but a wizard owner seen anywhere keeps the wizard on.
        assertEquals(SavedWifi.Verdict.WIZARD_OWNS, read("cname=$wizard:10031\n").verdict(wizard))
        // Only owners come through: a word that is not one is dropped.
        assertEquals(setOf("android.uid.system:1000"), WifiOwnersParser.parse(sequenceOf("cname=android.uid.system:1000", "cname=\"My home\"", "SSID=x", "cname=;rm")).owners)
    }
}
