package tv.cleanyour.core.registry

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.testing.fixtures.sha256Of
import java.io.File
import java.io.IOException

class DiskBlobStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private val assets = mutableMapOf<String, ByteArray>()
    private fun store(cap: Long = 1_000) = DiskBlobStore(File(tmp.root, "artifacts"), File(tmp.root, "bundled"), { assets[it]?.inputStream() }, cap, nowMs = { now })

    private fun blob(n: Int, seed: Int) = ByteArray(n) { (it + seed).toByte() }

    @Test fun putVerifiesSizeAndHash() = runBlocking {
        val s = store()
        val b = blob(100, 1)
        val ref = s.put(sha256Of(b), b.inputStream(), 100)
        assertArrayEquals(b, File(ref.path).readBytes())
        assertEquals(ref, s.get(ref.sha256))
        assertThrows(IOException::class.java) { runBlocking { s.put(sha256Of(b), b.inputStream(), 99) } }
        assertThrows(IOException::class.java) { runBlocking { s.put(Sha256("0".repeat(64)), b.inputStream(), 100) } }
        assertNull(s.get(Sha256("0".repeat(64))))
        assertEquals(listOf("${ref.sha256.hex}.blob"), File(tmp.root, "artifacts").list()!!.toList())
    }

    @Test fun leastRecentlyUsedIsEvictedFirst() = runBlocking {
        val s = store(cap = 250)
        val a = blob(100, 1); val b = blob(100, 2); val c = blob(100, 3)
        s.put(sha256Of(a), a.inputStream(), 100); now += 10_000
        s.put(sha256Of(b), b.inputStream(), 100); now += 10_000
        s.get(sha256Of(a)); now += 10_000   // a is now more recent than b
        s.put(sha256Of(c), c.inputStream(), 100)
        assertNotNull(s.get(sha256Of(a)))
        assertNull(s.get(sha256Of(b)))
        assertNotNull(s.get(sha256Of(c)))
        assertEquals(200, s.cachedBytes())
    }

    @Test fun theNewestBlobIsKeptEvenAboveTheCap() = runBlocking {
        val s = store(cap = 50)
        val big = blob(100, 4)
        assertNotNull(s.get(s.put(sha256Of(big), big.inputStream(), 100).sha256))
    }

    @Test fun bundledAssetsAreCopiedOnceToAFile() {
        assets["cleanyourtv-launcher.apk"] = blob(500, 5)
        val s = store()
        val ref = s.bundled("cleanyourtv-launcher.apk")
        assertEquals(sha256Of(assets.getValue("cleanyourtv-launcher.apk")), ref.sha256)
        assertTrue(File(ref.path).isFile)
        assertEquals(ref, s.bundled("cleanyourtv-launcher.apk"))
        assertEquals(ref, store().bundled("cleanyourtv-launcher.apk"))   // a new process reuses the copy
        assertThrows(IllegalStateException::class.java) { s.bundled("missing.apk") }
        assertThrows(IllegalArgumentException::class.java) { s.bundled("../etc/passwd") }
        assertEquals(0, s.cachedBytes())
    }

    @Test fun partialLifecycle() = runBlocking {
        val s = store()
        val b = blob(300, 6)
        val sha = sha256Of(b)
        s.startPartial(sha, "\"e1\"")
        s.appendPartial(sha).use { it.write(b, 0, 120) }
        assertEquals(120, s.partialLength(sha))
        assertEquals("\"e1\"", s.partialEtag(sha))
        assertThrows(IOException::class.java) { runBlocking { s.commitPartial(sha, 300) } }
        s.appendPartial(sha).use { it.write(b, 120, 180) }
        val ref = s.commitPartial(sha, 300)
        assertArrayEquals(b, File(ref.path).readBytes())
        assertEquals(0, s.partialLength(sha))
        assertNull(s.partialEtag(sha))
    }

    @Test fun commitRefusesAPartialWithTheWrongDigest() = runBlocking {
        val s = store()
        val sha = sha256Of("x")
        s.startPartial(sha, null)
        s.appendPartial(sha).use { it.write(byteArrayOf(1, 2, 3)) }
        assertThrows(IOException::class.java) { runBlocking { s.commitPartial(sha, 3) } }
        assertNull(s.get(sha))
    }

    @Test fun stalePartialsAreDroppedOnTheNextWrite() = runBlocking {
        val s = store()
        val old = sha256Of("old")
        s.startPartial(old, "\"x\"")
        s.appendPartial(old).use { it.write(1) }
        File(tmp.root, "artifacts/${old.hex}.part").setLastModified(now)
        now += 8L * 24 * 3600 * 1000
        val b = blob(10, 7)
        s.put(sha256Of(b), b.inputStream(), 10)
        assertEquals(0, s.partialLength(old))
        assertNull(s.partialEtag(old))
    }

    @Test fun deleteAndClear() = runBlocking {
        val s = store()
        val b = blob(10, 8)
        val ref = s.put(sha256Of(b), b.inputStream(), 10)
        s.delete(ref.sha256)
        assertNull(s.get(ref.sha256))
        s.put(sha256Of(b), b.inputStream(), 10)
        s.clear()
        assertEquals(0, s.cachedBytes())
    }
}
