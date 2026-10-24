package tv.cleanyour.core.ops

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.ProbeKind
import tv.cleanyour.core.model.ReadArg
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// The RFC-W1-2 ruling: `android` is a valid PackageName but protected.
class FrameworkPackageTest {
    private val android = PackageName("android")
    private val r = ShellRenderer()
    private val sha = Sha256("a".repeat(64))

    @Test fun theValueIsTheFrameworkPackage() = assertEquals(Denylist.FRAMEWORK, android.value)

    @Test fun protectedNamesIt() {
        assertNotNull(Protected.isProtected(android, null, null))
        assertNotNull(Denylist.staticReason(android.value))
    }

    @Test fun theRendererRefusesEveryOpThatTargetsIt() {
        val comp = Component(android, ".ResolverActivity")
        val ops = listOf(
            Op.Disable(android), Op.Enable(android), Op.DefaultState(android), Op.Uninstall(android),
            Op.Install(RemotePath(RemoteDir.TMP, listOf("a.apk")), sha, android, 1, sha, setOf(InstallFlag.REPLACE)),
            Op.SetHome(comp), Op.VerifyHome(comp), Op.StartActivity(comp, wait = true), Op.RestartHome(comp),
            Op.ArmHomeWatchdog(comp, 120, tv.cleanyour.core.model.RunToken("0123456789abcdef")),
        )
        for (op in ops) {
            val e = assertFailsWith<RenderRefused>(op.toString()) { r.render(op) }
            assertEquals(op, e.op)
        }
    }

    @Test fun itsPackageDumpCanStillBeRead() {
        // The executor's live denylist check reads the package at hand; reading is not a write.
        val line = r.render(Op.Read(ProbeKind.PACKAGE_DUMP, ReadArg.Pkg(android))).lines.single().text
        assertEquals("'dumpsys' 'package' 'android'", line)
    }
}
