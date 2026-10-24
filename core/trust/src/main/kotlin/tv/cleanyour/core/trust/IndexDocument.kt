package tv.cleanyour.core.trust

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PermissionStatement
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.trust.IndexJson.arr
import tv.cleanyour.core.trust.IndexJson.asObject
import tv.cleanyour.core.trust.IndexJson.instantMs
import tv.cleanyour.core.trust.IndexJson.long
import tv.cleanyour.core.trust.IndexJson.obj
import tv.cleanyour.core.trust.IndexJson.optArr
import tv.cleanyour.core.trust.IndexJson.optStr
import tv.cleanyour.core.trust.IndexJson.str

// index.json, schema 1, signed by an active key of keys.json. Shape (every listed key required
// unless marked optional; unknown keys ignored):
//
//   {"schema": 1, "sequence": 1, "generated": "<ISO-8601>", "expires": "<ISO-8601>",
//    "modules": [{"id": "retro", "name": "Retro", "one_line": "...", "description": "...",
//      "package": "io.github...", "type": "app", "tier": "community", "publisher": "...",
//      "source": "https://...", "licence": "GPL-3.0", "accent": "#E8A33D" (optional), "artwork": null (optional),
//      "versions": [{"version_code": 1, "version_name": "0.1.0", "released": "<ISO-8601>",
//        "apk": {"url": "https://github.com/...", "size": 3663466, "sha256": "<hex>", "signer_sha256": "<hex>"},
//        "permissions": [{"permission": "android.permission.INTERNET", "plain_words": "..."}],
//        "network": "...", "min_sdk": 26, "abis": ["armeabi-v7a"], "post_install": [] (optional)}]}],
//    "revoked": [{"sha256": "<hex>", "reason": "..."}] (optional)}
//
// `revoked` lists APK sha256 digests and signer certificate digests that must never be installed;
// a module whose signer is revoked is read as Tier.REVOKED, a revoked APK only rules out that
// version (see installable). `post_install` is parsed for shape
// and dropped: no 0.1 module is VERIFIED, so no post-install op may run (R0 "deliberately left open").
data class IndexDocument(val sequence: Long, val generatedAtMs: Long, val expiresAtMs: Long, val modules: List<Module>, val revocations: List<Sha256>) {
    companion object {
        const val SCHEMA = 1L
        const val MAX_BYTES = 1024 * 1024
        const val MAX_MODULES = 500
        const val MAX_VERSIONS = 20
        const val MAX_PERMISSIONS = 64
        const val MAX_REVOKED = 5000
        const val MAX_APK_BYTES = 200L * 1024 * 1024

        private val ID = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
        private val TYPE = Regex("^[a-z][a-z_]{0,19}$")
        private val LICENCE = Regex("^[A-Za-z0-9.+-]{1,40}$")
        private val VERSION_NAME = Regex("^[0-9A-Za-z][0-9A-Za-z.+_-]{0,39}$")
        private val PERMISSION = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+){1,15}$")
        private val ACCENT = Regex("^#[0-9A-Fa-f]{6}$")
        private val HEX64 = Regex("^[0-9a-f]{64}$")
        private val ABIS = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        private val TIERS = mapOf("verified" to Tier.VERIFIED, "community" to Tier.COMMUNITY, "quarantined" to Tier.QUARANTINED, "revoked" to Tier.REVOKED)

        fun parse(bytes: ByteArray): IndexDocument {
            val o = IndexJson.parseObject(bytes, MAX_BYTES)
            if (o.long("schema", 0..Long.MAX_VALUE) != SCHEMA) throw IndexFormatException("index: unsupported schema")
            val sequence = o.long("sequence", 1..Long.MAX_VALUE)
            val generated = o.instantMs("generated")
            val expires = o.instantMs("expires")
            if (expires <= generated) throw IndexFormatException("index: expires is not after generated")
            val revoked = o.optArr("revoked", MAX_REVOKED).mapIndexed { i, el ->
                val r = el.asObject("revoked[$i]")
                r.optStr("reason", 200)
                Sha256(r.str("sha256", 64, HEX64))
            }
            val revokedSet = revoked.toSet()
            val ids = mutableSetOf<String>()
            val pkgs = mutableSetOf<String>()
            val modules = o.arr("modules", MAX_MODULES).mapIndexed { i, el ->
                val m = module(el.asObject("modules[$i]"), "modules[$i]", revokedSet)
                if (!ids.add(m.id)) throw IndexFormatException("modules[$i]: duplicate id ${m.id}")
                if (!pkgs.add(m.pkg.value)) throw IndexFormatException("modules[$i]: duplicate package ${m.pkg.value}")
                m
            }
            return IndexDocument(sequence, generated, expires, modules, revoked)
        }

        private fun module(m: JsonObject, at: String, revoked: Set<Sha256>): Module {
            val pkg = try { PackageName(m.str("package", 255)) } catch (e: IllegalArgumentException) { throw IndexFormatException("$at.package: not a package name") }
            val tier = TIERS[m.str("tier", 20)] ?: throw IndexFormatException("$at.tier: unknown tier")
            val source = m.str("source", DownloadHosts.MAX_URL_LENGTH)
            if (!source.startsWith("https://")) throw IndexFormatException("$at.source: https link expected")
            val artwork = m.optStr("artwork", DownloadHosts.MAX_URL_LENGTH)
            if (artwork != null && !DownloadHosts.isAllowed(artwork)) throw IndexFormatException("$at.artwork: host not allowed")
            val codes = mutableSetOf<Long>()
            val versions = m.arr("versions", MAX_VERSIONS).mapIndexed { j, el ->
                val v = version(el.asObject("$at.versions[$j]"), "$at.versions[$j]")
                if (!codes.add(v.versionCode)) throw IndexFormatException("$at.versions[$j]: duplicate version_code")
                v
            }
            if (versions.isEmpty()) throw IndexFormatException("$at.versions: at least one version expected")
            val isRevoked = versions.any { it.artifact.signer in revoked }
            return Module(
                id = m.str("id", 40, ID),
                name = m.str("name", 40).also { if (it.isBlank()) throw IndexFormatException("$at.name: empty") },
                oneLine = m.str("one_line", 80),
                description = m.str("description", 2000),
                pkg = pkg,
                type = m.str("type", 20, TYPE),
                tier = if (isRevoked) Tier.REVOKED else tier,
                publisher = m.str("publisher", 60),
                sourceUrl = source,
                licence = m.str("licence", 40, LICENCE),
                accent = m.optStr("accent", 7, ACCENT),
                artwork = artwork,
                versions = versions.sortedByDescending { it.versionCode },
            )
        }

        private fun version(v: JsonObject, at: String): ModuleVersion {
            val apk = v.obj("apk")
            val url = apk.str("url", DownloadHosts.MAX_URL_LENGTH)
            if (!DownloadHosts.isAllowed(url)) throw IndexFormatException("$at.apk.url: host not allowed")
            val artifact = ArtifactRef(
                url = url,
                size = apk.long("size", 1..MAX_APK_BYTES),
                sha256 = Sha256(apk.str("sha256", 64, HEX64)),
                signer = Sha256(apk.str("signer_sha256", 64, HEX64)),
            )
            val permissions = v.arr("permissions", MAX_PERMISSIONS).mapIndexed { k, el ->
                val p = el.asObject("$at.permissions[$k]")
                PermissionStatement(p.str("permission", 160, PERMISSION), p.str("plain_words", 200))
            }
            val abis = v.arr("abis", ABIS.size).mapIndexed { k, el ->
                val s = (el as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IndexFormatException("$at.abis[$k]: string expected")
                if (s !in ABIS) throw IndexFormatException("$at.abis[$k]: unknown ABI")
                s
            }
            v.optArr("post_install", 20)
            return ModuleVersion(
                versionCode = v.long("version_code", 1..Int.MAX_VALUE.toLong()),
                versionName = v.str("version_name", 40, VERSION_NAME),
                releasedAt = v.str("released", 40).also { v.instantMs("released") },
                artifact = artifact,
                permissions = permissions,
                networkStatement = v.str("network", 300),
                minSdk = v.long("min_sdk", 1L..100L).toInt(),
                abis = abis,
                postInstall = emptyList(),
            )
        }
    }
}
