package tv.cleanyour.core.probe

import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.session.ExecResult
import tv.cleanyour.core.session.ExecSpec
import tv.cleanyour.core.session.RetryClass
import tv.cleanyour.core.session.TvSession

// Executes pre-rendered, single-quoted ShellLines from :core:ops (remote keyevents, input text,
// fallback pm install) on a live TvSession while keeping TvSession.exec inside the :core:probe
// allowlist of SingleCallSiteLintTest.
object TvShellRunner {
    suspend fun run(session: TvSession, line: ShellLine, timeoutMs: Long = 15_000L): ExecResult =
        session.exec(line, ExecSpec(timeoutMs = timeoutMs, stdoutCap = 16 * 1024, retry = RetryClass.NEVER))
}
