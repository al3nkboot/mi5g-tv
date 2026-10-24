package tv.cleanyour.adb.session

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.session.ExecSpec
import tv.cleanyour.core.session.RetryClass
import tv.cleanyour.testing.fakeadbd.ExecOutcome
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fixtures.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionExecTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    @Test fun execReturnsOutputAndExit() = runTest {
        val s = Rig(this).open()
        val r = s.exec(line("echo hi"))
        assertEquals(0, r.exit)
        assertEquals("hi\n", r.stdout)
        assertEquals("", r.stderr)
        assertTrue(!r.truncated)
        val bad = s.exec(line("nosuchbinary"))
        assertEquals(127, bad.exit)
        assertTrue(bad.stderr.contains("not found"))
    }

    @Test fun stdoutCapTruncatesAndSaysSo() = runTest {
        val s = Rig(this).open()
        val r = s.exec(line("getprop"), ExecSpec(stdoutCap = 100))
        assertEquals(100, r.stdout.toByteArray().size)
        assertTrue(r.truncated)
        assertEquals(0, r.exit)
    }

    @Test fun totalAndIdleTimeoutsAreSeparateClocks() = runTest {
        val s = Rig(this).open()
        var t = currentTime
        val total = assertFailsWith<AdbException> { s.exec(line("sleep 100"), ExecSpec(timeoutMs = 1_000, idleTimeoutMs = 5_000)) }
        assertEquals(AdbError.Transport.Timeout("exec-total"), total.error)
        assertEquals(1_000, currentTime - t)
        t = currentTime
        val idle = assertFailsWith<AdbException> { s.exec(line("sleep 100"), ExecSpec(timeoutMs = 30_000, idleTimeoutMs = 2_000)) }
        assertEquals(AdbError.Transport.Timeout("exec-idle"), idle.error)
        assertEquals(2_000, currentTime - t)
        // The connection survives a timed-out stream (5.3: CLSE for that stream, connection up).
        assertIs<ConnectionState.Connected>(s.state.value)
        assertEquals("still here\n", s.exec(line("echo still here")).stdout)
    }

    @Test fun linesStreamsStdoutLineByLine() = runTest {
        val s = Rig(this).open()
        val lines = s.lines(line("pm list packages -f")).toList()
        assertEquals(Fixtures.miTv().packagesAllText.lines().filter { it.isNotEmpty() }, lines)
        val capped = assertFailsWith<AdbException> { s.lines(line("getprop"), ExecSpec(stdoutCap = 50)).toList() }
        assertTrue(assertIs<AdbError.Transport.Protocol>(capped.error).detail.contains("cap"))
        val nonZero = assertFailsWith<AdbException> { s.lines(line("nosuchbinary")).toList() }
        assertEquals(127, assertIs<AdbError.NonZeroExit>(nonZero.error).exit)
    }

    @Test fun linesAreCutAtEightKiB() = runTest {
        val rig = Rig(this)
        rig.tv.handlers += { argv, _ -> if (argv.firstOrNull() == "longline") ExecOutcome("x".repeat(20_000) + "\nshort\n") else null }
        val s = rig.open()
        val lines = s.lines(line("longline")).toList()
        assertEquals(2, lines.size)
        assertEquals(SessionConfig.LINE_CAP_BYTES, lines[0].length)
        assertEquals("short", lines[1])
    }

    @Test fun pmIsNeverChained() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        for (text in listOf(
            "pm disable-user --user 0 com.a; pm disable-user --user 0 com.b",
            "'pm' 'enable' 'com.a' && 'pm' 'enable' 'com.b'",
            "pm install -r /x.apk | cat",
            "cmd package uninstall com.a; echo done",
            "echo hi; pm disable-user --user 0 com.a",
            "pm disable-user --user 0 com.a\npm enable com.b",
            "pm clear com.a & true",
        )) {
            val e = assertFailsWith<AdbException>(text) { s.exec(line(text)) }
            assertIs<AdbError.Policy.GuardFailed>(e.error)
            val e2 = assertFailsWith<AdbException>(text) { s.lines(line(text)).toList() }
            assertIs<AdbError.Policy.GuardFailed>(e2.error)
        }
        settle()
        assertTrue(rig.adbd.connections.single().openedServices.none { it.contains("pm ") && it.contains(";") })
        assertTrue(rig.tv.violations.isEmpty())
        // Single pm writes and chained reads pass.
        assertEquals(0, s.exec(line("'pm' 'disable-user' '--user' '0' 'com.droidlogic.tvinput'")).exit)
        assertEquals(0, s.exec(line("pm list packages -d; echo ---; getprop ro.product.model")).exit)
        assertTrue(rig.tv.violations.isEmpty())
    }

    @Test fun hostileOutputsDoNotCrash() = runTest {
        val rig = Rig(this)
        val tenMb = ByteArray(10 * 1024 * 1024) { 'A'.code.toByte() }
        val garbage = ByteArray(70_000) { (it * 31 + 7).toByte() }
        rig.tv.handlers += { argv, _ ->
            when (argv.firstOrNull()) {
                "tenmb" -> ExecOutcome(tenMb)
                "garbage" -> ExecOutcome(garbage, garbage, 0)
                "ansi" -> ExecOutcome("\u001b[31mred\u001b[0m\u0000nul\r\n")
                "badprops" -> ExecOutcome("[ro.serialno]: [X]\n]]]]: [[[[\n[a]: [b\n[ro.product.device]: [dev]\n" + "[k]: [" + "v".repeat(10_000) + "]\n")
                else -> null
            }
        }
        val s = rig.open()
        val big = s.exec(line("tenmb"), ExecSpec.Stream)
        assertTrue(big.truncated)
        assertEquals(ExecSpec.Stream.stdoutCap, big.stdout.toByteArray().size)
        val capped = assertFailsWith<AdbException> { s.lines(line("tenmb")).toList() }
        assertIs<AdbError.Transport.Protocol>(capped.error)
        val g = s.exec(line("garbage"), ExecSpec(stdoutCap = 1 shl 20, stderrCap = 1 shl 20))
        assertEquals(0, g.exit)
        assertTrue(g.stdout.isNotEmpty())
        val a = s.exec(line("ansi"))
        assertEquals("\u001b[31mred\u001b[0m\u0000nul\r\n", a.stdout)
        val props = Identity.parseProps(s.exec(line("badprops")).stdout)
        assertEquals("X", props["ro.serialno"])
        assertEquals("dev", props["ro.product.device"])
        assertNull(props["a"])
        assertIs<ConnectionState.Connected>(s.state.value)
    }

    @Test fun idempotentReadsAreRetriedOnceAfterTheSocketDies() = runTest {
        val rig = Rig(this)
        var drops = 0
        rig.tv.handlers += { argv, _ ->
            if (argv.firstOrNull() == "flaky" && drops == 0) { drops++; rig.adbd.closeConnections(); ExecOutcome("never\n") } else null
        }
        val s = rig.open()
        val hold = s.hold()
        val e = assertFailsWith<AdbException> { s.exec(line("flaky")) }
        assertEquals(AdbError.Transport.Closed, e.error)
        settle()
        assertIs<ConnectionState.Connected>(s.state.value)
        drops = 0
        val r = s.exec(line("flaky"), ExecSpec(retry = RetryClass.IDEMPOTENT_READ))
        assertEquals(127, r.exit)
        assertEquals("flaky: inaccessible", r.stderr.substringAfter("sh: ").substringBefore(" or"))
        hold.close()
    }
}
