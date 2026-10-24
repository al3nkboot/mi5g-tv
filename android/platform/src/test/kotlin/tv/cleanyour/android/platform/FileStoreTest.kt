package tv.cleanyour.android.platform

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class FileStoreTest {
    private val root: File = Files.createTempDirectory("filestore").toFile()
    private val store = FileStore(root, kotlinx.coroutines.Dispatchers.Unconfined)

    @Test fun writesReadsListsAndDeletes() = runTest(timeout = 30.seconds) {
        store.write("tvs/a/tv.json", "one".encodeToByteArray())
        store.write("tvs/b/tv.json", "two".encodeToByteArray())
        store.write("tvs/a/tv.json", "three".encodeToByteArray())
        assertEquals("three", store.read("tvs/a/tv.json") { it.decodeToString() })
        assertEquals(listOf("a", "b"), store.list("tvs"))
        assertEquals(listOf("tv.json"), store.list("tvs/a"))
        assertFalse(File(root, "tvs/a/tv.json.tmp").exists())   // the temporary file is renamed away
        store.delete("tvs/a/tv.json")
        assertNull(store.read("tvs/a/tv.json") { it })
        assertEquals(emptyList(), store.list("tvs/a"))
    }

    @Test fun pathsCannotLeaveTheRoot() = runTest(timeout = 30.seconds) {
        for (bad in listOf("../x", "tvs/../../x", "a//b", "./a", "tvs/a/tv.json.tmp")) {
            assertFailsWith<IllegalArgumentException>(bad) { store.write(bad, ByteArray(1)) }
        }
    }

    @Test fun subnetIsTheNetworkAddress() {
        assertEquals("192.168.1.0/24", NetworkState.subnet(byteArrayOf(192.toByte(), 168.toByte(), 1, 50), 24))
        assertEquals("10.0.0.0/8", NetworkState.subnet(byteArrayOf(10, 1, 2, 3), 8))
    }
}
