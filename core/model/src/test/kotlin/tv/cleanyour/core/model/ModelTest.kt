package tv.cleanyour.core.model

import tv.cleanyour.core.store.SnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModelTest {
    private val facts = DeviceFacts("Xiaomi", "Xiaomi", "MiTV-AXSO0", "dangal", "0001", 28, "9",
        "Xiaomi/dangal/dangal:9/PI/6036:user/release-keys", listOf("armeabi-v7a"), 1024, true, true,
        setOf("shell_v2", "cmd"), false, "en-IN")
    private val stock = Component(PackageName("com.google.android.tvlauncher"), ".MainActivity")

    private fun snapshot(packages: Map<PackageName, PackageState>): Snapshot {
        val s = Snapshot(TvId("0123456789abcdef"), 5, SnapshotReason.BASELINE, facts, packages,
            HomeResolution(stock, listOf(HomeClaimant(stock, 2))),
            mapOf(SettingKey.SCREENSAVER_ENABLED to "1", SettingKey.DEVICE_NAME to null), stock, listOf(stock),
            byteArrayOf(1, 2, 3), listOf(HdmiSource(1, "PlayStation 5", null, false)), Sha256("0".repeat(64)))
        return s.copy(contentHash = SnapshotCodec.hash(s))
    }

    private fun pkg(enabled: EnabledState) = PackageState(enabled, true, 3, null, false, false, "Label", false)

    @Test fun grammarsAcceptAndReject() {
        PackageName("com.gathin.tvhome")
        assertFailsWith<IllegalArgumentException> { PackageName("nodots") }
        assertFailsWith<IllegalArgumentException> { PackageName("com.x;rm") }
        assertFailsWith<IllegalArgumentException> { TvId("0123") }
        assertFailsWith<IllegalArgumentException> { RunToken("0123456789ABCDEF") }
        assertFailsWith<IllegalArgumentException> { Component(PackageName("com.a.b"), "Main'Activity") }
        assertEquals("com.a.b/.Main", Component(PackageName("com.a.b"), ".Main").flat)
        assertFailsWith<IllegalArgumentException> { RemotePath(RemoteDir.TMP, listOf("a..b")) }
        assertFailsWith<IllegalArgumentException> { RemotePath(RemoteDir.TMP, listOf("-x")) }
        assertEquals("/data/local/tmp/cleanyourtv/home-ok-0123456789abcdef", RemotePath(RemoteDir.TMP, listOf("home-ok-0123456789abcdef")).absolute)
        assertTrue(SettingKey.ADB_ENABLED.valueShape.matches("0"))
        assertTrue(!SettingKey.ADB_ENABLED.valueShape.matches("1"))
        assertTrue(SettingKey.SCREENSAVER_COMPONENTS.valueShape.matches("com.google.android.backdrop/.Backdrop"))
    }

    // RFC-W1-2: the framework package is admitted by name; nothing else without a dot is.
    @Test fun theFrameworkPackageIsTheOneDotlessName() {
        assertEquals("android", PackageName("android").value)
        assertFailsWith<IllegalArgumentException> { PackageName("android.") }
        assertFailsWith<IllegalArgumentException> { PackageName("androidx") }
        assertFailsWith<IllegalArgumentException> { PackageName("Android") }
        assertFailsWith<IllegalArgumentException> { PackageName(" android") }
        assertEquals("android.x", PackageName("android.x").value)
        assertEquals("androidx.core", PackageName("androidx.core").value)
    }

    @Test fun snapshotCodecIsCanonicalAndRoundTrips() {
        val a = PackageName("com.a.app") to pkg(EnabledState.ENABLED)
        val b = PackageName("com.b.app") to pkg(EnabledState.DISABLED_USER)
        val s1 = snapshot(linkedMapOf(a, b))
        val s2 = snapshot(linkedMapOf(b, a))
        assertEquals(s1.contentHash, s2.contentHash, "map order does not change the hash")
        assertTrue(SnapshotCodec.canonical(s1).contentEquals(SnapshotCodec.canonical(s2)))
        val back = SnapshotCodec.decode(SnapshotCodec.canonical(s1))
        assertEquals(s1.copy(launcherOverlay = null), back.copy(launcherOverlay = null))
        assertTrue(back.launcherOverlay!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertNotEquals(s1.contentHash, snapshot(mapOf(a)).contentHash)
    }

    @Test fun snapshotCodecRejectsTamperingAndOtherSchemas() {
        val text = SnapshotCodec.canonical(snapshot(mapOf(PackageName("com.a.app") to pkg(EnabledState.ENABLED)))).decodeToString()
        assertFailsWith<IllegalArgumentException> { SnapshotCodec.decode(text.replace("\"ENABLED\"", "\"DISABLED\"").encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { SnapshotCodec.decode(text.replace("\"schema\":1", "\"schema\":2").encodeToByteArray()) }
        assertTrue(text.startsWith("{\"schema\":1,\"tvId\":\"0123456789abcdef\""))
    }
}
