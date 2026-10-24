package tv.cleanyour.core.probe

import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.session.ExecResult
import tv.cleanyour.core.session.ExecSpec
import tv.cleanyour.core.session.RetryClass
import tv.cleanyour.core.session.TvSession

// Executes pre-rendered, single-quoted ShellLines from :core:ops (remote keyevents, input text,
// fallback pm install, speed boost, media cast, and app management) on a live TvSession while
// keeping TvSession.exec inside the :core:probe allowlist of SingleCallSiteLintTest.
object TvShellRunner {
    suspend fun run(
        session: TvSession,
        line: ShellLine,
        timeoutMs: Long = 15_000L,
        stdoutCap: Int = 64 * 1024,
    ): ExecResult =
        session.exec(line, ExecSpec(timeoutMs = timeoutMs, stdoutCap = stdoutCap, retry = RetryClass.NEVER))
}
