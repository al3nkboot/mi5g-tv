package tv.mi5g.adb.pairing

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec as MacKey

interface PairingTls : AutoCloseable {
    fun exportKeyingMaterial(label: ByteArray, length: Int): ByteArray
    val input: java.io.InputStream
    val output: java.io.OutputStream
}

fun interface PairingTlsFactory { fun connect(host: String, port: Int): PairingTls }

class PairingClient(private val tlsFactory: PairingTlsFactory) {
    fun pair(host: String, port: Int, code: String, peerInfoKeyLine: String): PairingResult = try {
        tlsFactory.connect(host, port).use { tls ->
            val password = code.toByteArray(Charsets.UTF_8) + tls.exportKeyingMaterial("adb-label\u0000".toByteArray(), 64)
            val auth = Spake2(true, password)
            val input = DataInputStream(tls.input); val output = DataOutputStream(tls.output)
            writePacket(output, TYPE_SPAKE, auth.message)
            val (type, theirs) = readPacket(input)
            require(type == TYPE_SPAKE && theirs.size == 32) { "Invalid SPAKE2 packet" }
            val key = auth.process(theirs) ?: return PairingResult.Protocol("Invalid SPAKE2 message")
            val cipher = PairCipher(key)
            val own = ByteArray(8192); own[0] = 0
            val keyLine = (peerInfoKeyLine + "\u0000").toByteArray(Charsets.UTF_8)
            require(keyLine.size <= own.size - 1) { "ADB public key is too long" }
            keyLine.copyInto(own, 1)
            writePacket(output, TYPE_PEER_INFO, cipher.encrypt(own))
            val (peerType, encrypted) = readPacket(input)
            require(peerType == TYPE_PEER_INFO) { "Expected peer info" }
            val remote = cipher.decrypt(encrypted) ?: return PairingResult.WrongCode
            // adbd answers with its device GUID (type 1); a host-style peer sends its RSA key (type 0).
            require(remote.size == 8192 && remote[0].toInt() in 0..1) { "Unsupported peer info" }
            val line = remote.copyOfRange(1, remote.size).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
            PairingResult.Paired(line)
        }
    } catch (e: java.net.ConnectException) { PairingResult.Unreachable }
      catch (e: java.net.SocketTimeoutException) { PairingResult.Unreachable }
      catch (e: Exception) { PairingResult.Protocol(e.message ?: "Pairing failed") }

    private fun writePacket(out: DataOutputStream, type: Int, payload: ByteArray) {
        out.writeByte(1); out.writeByte(type); out.writeInt(payload.size); out.write(payload); out.flush()
    }
    private fun readPacket(input: DataInputStream): Pair<Int, ByteArray> {
        val version = input.readUnsignedByte(); val type = input.readUnsignedByte(); val size = input.readInt()
        require(version == 1 && size in 1..16384) { "Invalid pairing packet header" }
        return type to ByteArray(size).also(input::readFully)
    }
    private companion object { const val TYPE_SPAKE = 0; const val TYPE_PEER_INFO = 1 }
}

sealed interface PairingResult {
    data class Paired(val peerInfoOfTv: String) : PairingResult
    data object WrongCode : PairingResult
    data object Unreachable : PairingResult
    data class Protocol(val msg: String) : PairingResult
}

internal class PairCipher(keyMaterial: ByteArray) {
    private val key = hkdf(keyMaterial, "adb pairing_auth aes-128-gcm key".toByteArray(), 16)
    private var enc = 0L; private var dec = 0L
    fun encrypt(data: ByteArray): ByteArray = crypt(Cipher.ENCRYPT_MODE, data, enc++)
    fun decrypt(data: ByteArray): ByteArray? = try { crypt(Cipher.DECRYPT_MODE, data, dec++) } catch (_: Exception) { null }
    private fun crypt(mode: Int, bytes: ByteArray, seq: Long): ByteArray {
        val nonce = ByteArray(12); for (i in 0..7) nonce[i] = (seq ushr (8 * i)).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").run { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); doFinal(bytes) }
    }
    private fun hkdf(ikm: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256"); mac.init(MacKey(ByteArray(32), "HmacSHA256")); val prk = mac.doFinal(ikm)
        mac.init(MacKey(prk, "HmacSHA256")); val t = mac.doFinal(info + byteArrayOf(1)); return t.copyOf(len)
    }
}

/** Convenience socket adapter for the later Android TLS implementation. */
class SocketPairingTls(private val socket: Socket, private val exporter: (ByteArray, Int) -> ByteArray) : PairingTls {
    override val input get() = socket.getInputStream()
    override val output get() = socket.getOutputStream()
    override fun exportKeyingMaterial(label: ByteArray, length: Int) = exporter(label, length)
    override fun close() = socket.close()
}
