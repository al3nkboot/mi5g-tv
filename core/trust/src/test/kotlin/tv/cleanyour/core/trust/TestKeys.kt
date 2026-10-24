package tv.cleanyour.core.trust

import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.io.File
import java.security.SecureRandom
import java.util.Base64

// Test-only minisign signer: fresh Ed25519 keys and signatures in minisign's file format, so the
// fixture matrix can sign anything (good and deliberately broken). Interop with the real tool is
// covered separately by the spike vectors and by the seed index signed with minisign 0.12.
class TestKey private constructor(val id: ByteArray, private val sk: ByteArray, val pk: ByteArray) {
    val key: MinisignKey get() = MinisignKey(id, pk)
    val idHex: String get() = Ed25519Minisign.keyIdHex(id)
    val publicLine: String get() = Base64.getEncoder().encodeToString(byteArrayOf('E'.code.toByte(), 'd'.code.toByte()) + id + pk)

    fun sign(data: ByteArray, trusted: String = "timestamp:1760000000\tfile:test", prehashed: Boolean = true): ByteArray {
        val msg = if (prehashed) Ed25519Minisign.blake2b512(data) else data
        val sig = ByteArray(64).also { Ed25519.sign(sk, 0, msg, 0, msg.size, it, 0) }
        val global = ByteArray(64).also { val m = sig + trusted.toByteArray(); Ed25519.sign(sk, 0, m, 0, m.size, it, 0) }
        val alg = if (prehashed) "ED" else "Ed"
        val b64 = Base64.getEncoder()
        return ("untrusted comment: test signature\n" + b64.encodeToString(alg.toByteArray() + id + sig) + "\n" +
            "trusted comment: $trusted\n" + b64.encodeToString(global) + "\n").toByteArray()
    }

    companion object {
        private val rng = SecureRandom()
        fun generate(): TestKey {
            val sk = ByteArray(32).also { rng.nextBytes(it) }
            val pk = ByteArray(32).also { Ed25519.generatePublicKey(sk, 0, it, 0) }
            val id = ByteArray(8).also { rng.nextBytes(it) }
            return TestKey(id, sk, pk)
        }
    }
}

object Repo {
    val root: File get() = File(System.getProperty("cleanyourtv.repoRoot") ?: error("cleanyourtv.repoRoot not set"))
    val bundled: File get() = File(root, "android/platform/src/main/assets/bundled")
    fun bundled(name: String): ByteArray = File(bundled, name).readBytes()
}

// Builders for keys.json and index.json with every field overridable.
object Docs {
    fun keys(sequence: Long = 1, active: List<TestKey>, revoked: List<TestKey> = emptyList()): ByteArray {
        val entries = active.map { """{"id":"${it.idHex}","public_key":"${it.publicLine}","status":"active"}""" } +
            revoked.map { """{"id":"${it.idHex}","public_key":"${it.publicLine}","status":"revoked"}""" }
        return """{"schema":1,"sequence":$sequence,"generated":"2026-10-19T00:00:00Z","keys":[${entries.joinToString(",")}]}""".toByteArray()
    }

    const val RETRO_SHA = "a3bcf445e1062d9e905b1d21b347236a6831e9ace6bfa1e76f9d154acf2d9db8"
    const val RETRO_SIGNER = "0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405"

    fun version(
        code: Int = 1,
        url: String = "https://github.com/udhaykumarbala/tv-vibehack-retro/releases/download/v0.1.0/retro-release.apk",
        sha: String = RETRO_SHA,
        signer: String = RETRO_SIGNER,
        size: Long = 3663466,
    ) = """{"version_code":$code,"version_name":"0.1.$code","released":"2026-10-19T09:20:32Z",
        "apk":{"url":"$url","size":$size,"sha256":"$sha","signer_sha256":"$signer"},
        "permissions":[{"permission":"android.permission.INTERNET","plain_words":"Downloads games."}],
        "network":"Game downloads only.","min_sdk":26,"abis":["armeabi-v7a","arm64-v8a"],"post_install":[]}"""

    fun module(id: String = "retro", name: String = "Retro", pkg: String = "io.github.udhaykumarbala.retro", tier: String = "community",
               versions: List<String> = listOf(version())) =
        """{"id":"$id","name":"$name","one_line":"NES games","description":"A game player.","package":"$pkg","type":"app",
        "tier":"$tier","publisher":"udhaykumarbala","source":"https://github.com/udhaykumarbala/tv-vibehack-retro","licence":"GPL-3.0",
        "accent":"#E8A33D","artwork":null,"versions":[${versions.joinToString(",")}]}"""

    fun index(
        sequence: Long = 7,
        generated: String = "2026-10-19T00:00:00Z",
        expires: String = "2026-11-02T00:00:00Z",
        modules: List<String> = listOf(module()),
        revoked: List<String> = emptyList(),
    ): ByteArray = """{"schema":1,"sequence":$sequence,"generated":"$generated","expires":"$expires",
        "modules":[${modules.joinToString(",")}],"revoked":[${revoked.joinToString(",") { """{"sha256":"$it","reason":"test"}""" }}]}""".toByteArray()

    // 2026-10-20T00:00:00Z, inside the default validity window.
    const val NOW = 1_792_454_400_000L
}
