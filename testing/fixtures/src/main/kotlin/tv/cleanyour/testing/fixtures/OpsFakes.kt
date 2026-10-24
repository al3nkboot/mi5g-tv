package tv.cleanyour.testing.fixtures

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.RawShell
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.ops.ConfigTransport
import tv.cleanyour.core.ops.ConfigTransportKind
import tv.cleanyour.core.ops.OpRenderer
import tv.cleanyour.core.ops.Privilege
import tv.cleanyour.core.ops.RenderRefused
import tv.cleanyour.core.ops.Rendered
import tv.cleanyour.core.ops.Transfer
import tv.cleanyour.core.ops.VerifyResult
import tv.cleanyour.core.session.TvSession
import java.util.concurrent.CopyOnWriteArrayList

// NOT the renderer. Renders every op as one readable "fake ..." line (RestartHome as two, Settle as
// none, PushAsset and InstallStream as transfers) so planner, engine and flow tests can run before
// W3's renderer exists. Never use it for golden files or anything that reaches a TV.
@OptIn(RawShell::class)
class FakeOpRenderer(
    var privilegeOf: (Op) -> Privilege = ::defaultPrivilege,
    var refuse: (Op) -> String? = { null },
    var verifyMismatch: (Op, String) -> VerifyResult.Mismatch? = { _, _ -> null },
) : OpRenderer {
    val rendered = CopyOnWriteArrayList<Op>()

    override fun render(op: Op): Rendered {
        refuse(op)?.let { throw RenderRefused(op, it) }
        rendered += op
        val (lines, transfer) = when (op) {
            is Op.Settle -> emptyList<String>() to null
            is Op.PushAsset -> emptyList<String>() to Transfer.Push(op.src, op.dest)
            is Op.InstallStream -> emptyList<String>() to Transfer.StreamInstall(op.blob, op.blob.size)
            is Op.RestartHome -> listOf("fake force-stop ${op.component.pkg.value}", "fake start ${op.component.flat}") to null
            else -> listOf("fake $op") to null
        }
        return Rendered(op, lines.map { ShellLine(it) }, transfer, privilegeOf(op), verify = null, timeoutMs = 30_000, stdoutCap = 256 * 1024)
    }

    override fun inverse(op: Op, prior: Prior): Op? = when (op) {
        is Op.Disable -> Op.Enable(op.pkg)
        is Op.Enable -> Op.Disable(op.pkg)
        is Op.SettingsPut -> Op.SettingsPut(op.key, prior.settingValue)
        is Op.SetHome -> prior.home?.let { Op.SetHome(it) }
        is Op.Install -> if (prior.present == false) Op.Uninstall(op.pkg) else null
        is Op.InstallStream -> if (prior.present == false) Op.Uninstall(op.pkg) else null
        is Op.PushAsset -> if (prior.present == false) Op.RemoveFile(op.dest) else null
        is Op.ArmHomeWatchdog -> Op.KillWatchdog(op.token)
        else -> null
    }

    override fun check(op: Op, verifyOutput: String, prior: Prior?): VerifyResult = verifyMismatch(op, verifyOutput) ?: VerifyResult.Ok

    // The 5.2 definition (R0: 5): every rendered line then "\n", a transfer as one sync line.
    override fun planHash(rendered: List<Rendered>): Sha256 {
        val sb = StringBuilder()
        for (r in rendered) {
            r.lines.forEach { sb.append(it.text).append('\n') }
            when (val t = r.transfer) {
                is Transfer.Push -> sb.append("sync:${t.dest.absolute} ${t.src.sha256.hex}\n")
                is Transfer.Pull -> sb.append("sync:${t.src.absolute} ${t.maxBytes}\n")
                is Transfer.StreamInstall -> sb.append("sync:${t.src.path} ${t.size}\n")
                null -> {}
            }
        }
        return sha256Of(sb.toString())
    }

    companion object {
        fun defaultPrivilege(op: Op): Privilege = when (op) {
            is Op.Read, is Op.VerifyHome, is Op.Settle -> Privilege.READ
            is Op.DebugOff -> Privilege.SESSION_END
            is Op.SetHome, is Op.ArmHomeWatchdog, is Op.ConfirmHome, is Op.KillWatchdog, is Op.PressHome -> Privilege.TAKEOVER
            else -> Privilege.PROFILE_STEP
        }
    }
}

// Overlay transport stand-in: returns fixed step lists and a settable digest.
class FakeConfigTransport(
    override val kind: ConfigTransportKind = ConfigTransportKind.FILE_OVERLAY,
    @Volatile var digest: Sha256? = null,
    private val launcher: Component = Component(PackageName("com.gathin.tvhome"), ".MainActivity"),
) : ConfigTransport {
    val writes = CopyOnWriteArrayList<Pair<BlobRef, BlobRef?>>()
    @Volatile var resets = 0
        private set

    override suspend fun readDigest(session: TvSession): Sha256? = digest

    override fun writePlanSteps(config: BlobRef, wallpaper: BlobRef?): List<Op> {
        writes += config to wallpaper
        return listOf(Op.Mkdir(RemoteDir.OVERLAY), Op.RestartHome(launcher))
    }

    override fun resetPlanSteps(): List<Op> {
        resets++
        return listOf(Op.RestartHome(launcher))
    }
}
