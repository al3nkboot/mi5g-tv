package tv.cleanyour.core.backup

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.ProbeKind
import tv.cleanyour.core.model.ReadArg
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.SettingsNs
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChangeJsonTest {
    private val pkg = PackageName("com.example.app")
    private val comp = Component(pkg, ".Main")
    private val token = RunToken("0123456789abcdef")
    private val blob = BlobRef(sha256Of("x"), 1, "mem:x")
    private val path = RemotePath(RemoteDir.TMP, listOf("a.apk"))

    // One of every Op, so a new variant without a codec entry fails to compile (exhaustive when) or here.
    private val everyOp: List<Op> = listOf(
        Op.Disable(pkg), Op.Enable(pkg), Op.DefaultState(pkg),
        Op.Install(path, blob.sha256, pkg, 7, Samples.zeroHash, setOf(InstallFlag.REPLACE)),
        Op.InstallStream(blob, blob.sha256, pkg, 8, Samples.zeroHash), Op.Uninstall(pkg),
        Op.SettingsPut(SettingKey.SCREENSAVER_ENABLED, "1"), Op.SettingsPut(SettingKey.DEVICE_NAME, null),
        Op.SetHome(comp), Op.VerifyHome(comp), Op.ArmHomeWatchdog(comp, 120, token), Op.ConfirmHome(token),
        Op.KillWatchdog(token), Op.PressHome, Op.Mkdir(RemoteDir.OVERLAY),
        Op.PushAsset(blob, RemotePath(RemoteDir.OVERLAY, listOf("config.json")), blob.sha256),
        Op.RemoveFile(RemotePath(RemoteDir.RESTORE, listOf("restore.txt"))), Op.RestartHome(comp),
        Op.StartActivity(comp, true), Op.Read(ProbeKind.PACKAGES), Op.Read(ProbeKind.PACKAGE_DUMP, ReadArg.Pkg(pkg)),
        Op.Read(ProbeKind.SHA256_OF_FILE, ReadArg.Path(path)), Op.Read(ProbeKind.WATCHDOG_ALIVE, ReadArg.Token(token)),
        Op.Read(ProbeKind.SETTINGS, ReadArg.Ns(SettingsNs.GLOBAL)), Op.Settle(5), Op.Reboot, Op.DebugOff,
        Op.CurtainCue(comp, token, tv.cleanyour.core.model.CurtainStage.GROUP_DONE, 4, 9, tv.cleanyour.core.model.CurtainGroup.SHOP_DEMO, 2, 2),
    )

    @Test fun everyOpRoundTrips() {
        val changes = everyOp.mapIndexed { i, op ->
            ChangeRecord(PlanId("01PLAN"), i, op, if (i % 3 == 0) null else Prior(EnabledState.DISABLED_USER, "v", comp, byteArrayOf(1, 2, 3), i % 2 == 0), 1000L + i)
        }
        val bytes = ChangeJson.encode(changes)
        val back = ChangeJson.decode(bytes)
        assertEquals(changes.map { it.op }, back.map { it.op })
        assertEquals(changes.map { it.copy(prior = it.prior?.copy(overlay = null)) }, back.map { it.copy(prior = it.prior?.copy(overlay = null)) })
        changes.zip(back).forEach { (a, b) -> assertContentEquals(a.prior?.overlay, b.prior?.overlay) }
        assertContentEquals(bytes, ChangeJson.encode(back))
    }

    @Test fun theScenarioRoundTrips() {
        val changes = Scenario.changes()
        assertContentEquals(ChangeJson.encode(changes), ChangeJson.encode(ChangeJson.decode(ChangeJson.encode(changes))))
    }

    @Test fun grammarsAreCheckedOnDecode() {
        val good = ChangeJson.encode(listOf(ChangeRecord(PlanId("p"), 0, Op.Enable(pkg), null, 1))).decodeToString()
        assertFailsWith<IllegalArgumentException> { ChangeJson.decode(good.replace("com.example.app", "com.example.app';reboot").encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { ChangeJson.decode(good.replace("\"enable\"", "\"format_disk\"").encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { ChangeJson.decode(good.replace("\"schema\":1", "\"schema\":2").encodeToByteArray()) }
    }
}
