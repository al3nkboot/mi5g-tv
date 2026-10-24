package tv.cleanyour.adb.wire

import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.Sha256

// 0.1 ships plain 5555 only (D9). A TV that sends STLS gets this refusal and the S2x dead end.
object NotSupportedTlsUpgrader : TlsUpgrader {
    override suspend fun upgrade(socket: AdbSocket, pin: Sha256?): AdbSocket =
        throw AdbException(AdbError.Transport.Protocol("TLS debugging is not supported in this version"))
}
