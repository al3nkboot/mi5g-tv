package tv.cleanyour.core.registry

import org.bouncycastle.math.ec.rfc8032.Ed25519
import tv.cleanyour.core.trust.Ed25519Minisign
import tv.cleanyour.core.trust.MinisignIndexVerifier
import tv.cleanyour.core.trust.MinisignKey
import java.io.File
import java.security.SecureRandom
import java.util.Base64

// Test-side minisign signer (the same as :core:trust's test helper; test sources do not cross modules).
class Signer {
    private val sk = ByteArray(32).also { rng.nextBytes(it) }
    private val pk = ByteArray(32).also { Ed25519.generatePublicKey(sk, 0, it, 0) }
    val id = ByteArray(8).also { rng.nextBytes(it) }
    val key get() = MinisignKey(id, pk)
    val idHex get() = Ed25519Minisign.keyIdHex(id)
    val line: String get() = Base64.getEncoder().encodeToString("Ed".toByteArray() + id + pk)

    fun sign(data: ByteArray, trusted: String = "timestamp:1\tfile:test"): ByteArray {
        val msg = Ed25519Minisign.blake2b512(data)
        val sig = ByteArray(64).also { Ed25519.sign(sk, 0, msg, 0, msg.size, it, 0) }
        val g = sig + trusted.toByteArray()
        val global = ByteArray(64).also { Ed25519.sign(sk, 0, g, 0, g.size, it, 0) }
        val b = Base64.getEncoder()
        return "untrusted comment: t\n${b.encodeToString("ED".toByteArray() + id + sig)}\ntrusted comment: $trusted\n${b.encodeToString(global)}\n".toByteArray()
    }

    companion object { private val rng = SecureRandom() }
}

// A small publisher: a root, signing keys, and generations of keys.json and index.json.
class Publisher {
    val root = Signer()
    var ci = Signer()
    val verifier = MinisignIndexVerifier(root.key)

    fun keys(sequence: Long, active: List<Signer> = listOf(ci), revoked: List<Signer> = emptyList()): Pair<ByteArray, ByteArray> {
        val entries = active.map { """{"id":"${it.idHex}","public_key":"${it.line}","status":"active"}""" } +
            revoked.map { """{"id":"${it.idHex}","public_key":"${it.line}","status":"revoked"}""" }
        val bytes = """{"schema":1,"sequence":$sequence,"generated":"2026-10-19T00:00:00Z","keys":[${entries.joinToString(",")}]}""".toByteArray()
        return bytes to root.sign(bytes)
    }

    fun index(sequence: Long, expires: String = "2026-11-02T00:00:00Z", moduleId: String = "retro", by: Signer = ci): Pair<ByteArray, ByteArray> {
        val bytes = """{"schema":1,"sequence":$sequence,"generated":"2026-10-19T00:00:00Z","expires":"$expires","modules":[
            {"id":"$moduleId","name":"Retro","one_line":"NES games","description":"d","package":"io.github.udhaykumarbala.retro","type":"app",
             "tier":"community","publisher":"p","source":"https://github.com/udhaykumarbala/tv-vibehack-retro","licence":"GPL-3.0",
             "versions":[{"version_code":1,"version_name":"0.1.0","released":"2026-10-19T09:20:32Z",
               "apk":{"url":"https://github.com/udhaykumarbala/tv-vibehack-retro/releases/download/v0.1.0/retro-release.apk","size":3663466,
                      "sha256":"a3bcf445e1062d9e905b1d21b347236a6831e9ace6bfa1e76f9d154acf2d9db8","signer_sha256":"0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405"},
               "permissions":[],"network":"n","min_sdk":26,"abis":[]}]}]}""".toByteArray()
        return bytes to by.sign(bytes)
    }

    fun bundle(indexSeq: Long, keysSeq: Long = 1, expires: String = "2026-11-02T00:00:00Z"): IndexBundle {
        val (k, ks) = keys(keysSeq)
        val (i, s) = index(indexSeq, expires)
        return IndexBundle(i, s, k, ks)
    }
}

object Times {
    const val NOW = 1_792_454_400_000L        // 2026-10-20T00:00:00Z
    const val EXPIRY = 1_793_577_600_000L     // 2026-11-02T00:00:00Z
}

object RepoFiles {
    val root: File get() = File(System.getProperty("cleanyourtv.repoRoot") ?: error("cleanyourtv.repoRoot not set"))
    fun bundled(name: String): ByteArray = File(root, "android/platform/src/main/assets/bundled/$name").readBytes()
}
