package tv.cleanyour.adb.wire

import kotlinx.coroutines.test.TestScope
import tv.cleanyour.core.model.RawShell
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.testing.fakeadbd.FakeAdbd
import tv.cleanyour.testing.fakeadbd.FakeTv
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.Personas

object TestKeys {
    // RSA key generation costs a few hundred ms; share one across tests.
    val signer: SoftwareRsaAdbSigner by lazy { SoftwareRsaAdbSigner(SoftwareRsaAdbSigner.generateKeyPair()) }
    fun fresh(): SoftwareRsaAdbSigner = SoftwareRsaAdbSigner(SoftwareRsaAdbSigner.generateKeyPair())
}

fun TestScope.adbd(tv: FakeTv = Personas.miTv(), faults: Faults = Faults(), trusted: Boolean = true): FakeAdbd =
    FakeAdbd(tv, faults, backgroundScope).also { if (trusted) it.trust(TestKeys.signer) }

suspend fun FakeAdbd.open(signer: AdbSigner = TestKeys.signer, tls: TlsUpgrader? = null, onAllow: suspend (String) -> Unit = {}): AdbConnection =
    AdbConnection.open(connect(), signer, tls, onAllow)

@OptIn(RawShell::class)
fun line(text: String): ShellLine = ShellLine(text)

class ShellOutput(val stdout: String, val stderr: String, val exit: Int)

// A whole shell v2 exchange, unbounded (tests of the bounds live in :adb:session).
suspend fun AdbConnection.shell(text: String): ShellOutput {
    val stream = openStream(AdbService.ShellV2(line(text)))
    try {
        val reader = AdbStreamReader(stream)
        val out = StringBuilder()
        val err = StringBuilder()
        while (true) {
            when (val p = ShellV2.next(reader) ?: throw protocolError("no exit packet")) {
                is ShellPacket.Stdout -> out.append(String(p.bytes, Charsets.UTF_8))
                is ShellPacket.Stderr -> err.append(String(p.bytes, Charsets.UTF_8))
                is ShellPacket.Exit -> return ShellOutput(out.toString(), err.toString(), p.code)
            }
        }
    } finally {
        stream.close()
    }
}

// An AdbStream that hands out fixed chunks and then end of stream; writes are kept.
class ScriptedStream(chunks: List<ByteArray>) : AdbStream {
    private val pending = ArrayDeque(chunks)
    val written = ArrayList<ByteArray>()
    override suspend fun read(): ByteArray? = pending.removeFirstOrNull()
    override suspend fun write(bytes: ByteArray) { written += bytes }
    override fun close() {}
}

fun ByteArray.chunked(size: Int): List<ByteArray> {
    val out = ArrayList<ByteArray>()
    var off = 0
    while (off < this.size) { val n = minOf(size, this.size - off); out += copyOfRange(off, off + n); off += n }
    return out
}
