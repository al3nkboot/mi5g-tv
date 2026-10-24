package tv.cleanyour.e2e.scenarios

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.session.InstallOutcome
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.e2e.E2eWiring
import tv.cleanyour.e2e.Timings
import kotlin.random.Random

// Transfers on a real adbd (ARCHITECTURE 3.9, D39): sync push with the remote hash, pull back,
// throughput against the 0.5 MB/s emulator row (trend), and the streaming install of the pinned
// launcher (I3), checked with the platform's package manager.
class S04PushAndInstall : E2eScenario() {

    private val dest = RemotePath(RemoteDir.TMP, listOf("e2e-push.bin"))

    @After fun tidy() {
        oracle.shell("rm -f ${dest.absolute}")
    }

    @Test fun pushVerifiesAndPullsBack() = e2e {
        val bytes = Random(28).nextBytes(8 * 1024 * 1024)
        val ref = blobs.add(bytes)
        val session = open()
        try {
            oracle.shell("mkdir -p ${RemoteDir.TMP.path}")
            val r = session.push(ref, dest)
            val mbps = bytes.size / 1_048_576.0 / (r.millis / 1000.0)
            timings.record("push.throughput", mbps, "MB/s", Timings.PUSH_MB_PER_S, higherIsBetter = true)
            assertEquals(bytes.size.toLong(), r.bytes)
            r.remoteSha256?.let { assertEquals(ref.sha256, it) }
            assertEquals(ref.sha256.hex, oracle.sha256(dest.absolute))
            assertArrayEquals(bytes, session.pull(dest, maxBytes = 16L * 1024 * 1024))
        } finally {
            session.close()
        }
    }

    @Test fun pullRespectsTheCap() = e2e {
        val ref = blobs.add(Random(1).nextBytes(64 * 1024))
        val session = open()
        try {
            oracle.shell("mkdir -p ${RemoteDir.TMP.path}")
            session.push(ref, dest)
            // The cap is a bound on memory: a pull either fails typed or returns at most maxBytes.
            val r = runCatching { session.pull(dest, maxBytes = 1024) }
            assertTrue("pull returned ${r.getOrNull()?.size} bytes over a 1024 byte cap", r.isFailure || r.getOrThrow().size <= 1024)
        } finally {
            session.close()
        }
    }

    @Test fun launcherInstallsByStream() = e2e(timeoutMs = 600_000) {
        val ref = launcherBlob()
        oracle.uninstall(LAUNCHER_PKG)
        val session = open()
        try {
            val t0 = System.nanoTime()
            val outcome = session.installStream(ref, ref.size)
            timings.record("install.launcher", (System.nanoTime() - t0) / 1e6, "ms")
            assertEquals(InstallOutcome.Installed, outcome)
            assertTrue(oracle.isInstalled(LAUNCHER_PKG))
            E2eWiring.prober(deps)?.let { p ->
                val base = oracle.shell("pm path $LAUNCHER_PKG").stdout.trim().removePrefix("package:")
                assertEquals(oracle.sha256(base), p.installedSha256(session, PackageName(LAUNCHER_PKG))?.hex)
            }
        } finally {
            session.close()
        }
    }
}
