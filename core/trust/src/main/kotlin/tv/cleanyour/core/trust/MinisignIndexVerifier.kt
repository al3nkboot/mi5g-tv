package tv.cleanyour.core.trust

import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.Tier

// The index chain of trust (SECURITY 2.4 S1 to S3, PRODUCT-SPEC D29, F9):
//
//   1. keys.json must carry a valid minisign signature by the root key compiled into the app, parse,
//      and not be older than `keysFloor` (the keys file the app shipped with)      else BAD_KEYS_FILE
//   2. index.json's signature must name a key of keys.json: a revoked one        -> REVOKED_KEY
//      one that is neither active nor revoked (the root included)                  -> UNKNOWN_KEY
//      an active one whose signature or trusted comment does not verify            -> BAD_SIGNATURE
//   3. index.json must parse within its limits                                     else MALFORMED
//   4. its sequence must not be lower than the last one accepted                   else ROLLBACK
//   5. nowMs must be before its expiry                                             else EXPIRED
//
// The order is fixed so each failure maps to one F9 copy line. Pass nowMs = VERIFY_IGNORING_EXPIRY
// to re-check a stored copy whose freshness the caller judges itself (the registry's stale flag).
class MinisignIndexVerifier(
    private val root: MinisignKey,
    private val keysFloor: Long = 0,
    private val minisign: Minisign = Ed25519Minisign,
) : IndexVerifier {

    override fun verify(indexBytes: ByteArray, sigBytes: ByteArray, keysBytes: ByteArray, keysSig: ByteArray, lastSequence: Long?, nowMs: Long): IndexVerdict {
        val keys = when (val k = verifyKeys(keysBytes, keysSig)) {
            is KeysVerdict.Ok -> k.keys
            is KeysVerdict.Bad -> return k.bad
        }

        val sig = try { Ed25519Minisign.parseSignature(sigBytes) } catch (e: IllegalArgumentException) {
            return IndexVerdict.Bad(IndexFailure.BAD_SIGNATURE, "index signature unreadable: ${e.message}")
        }
        val keyId = Ed25519Minisign.keyIdHex(sig.keyId)
        if (keys.keys.revoked.any { it.contentEquals(sig.keyId) }) return IndexVerdict.Bad(IndexFailure.REVOKED_KEY, "index signed by revoked key $keyId")
        val signer = keys.keys.active.firstOrNull { it.id.contentEquals(sig.keyId) }
            ?: return IndexVerdict.Bad(IndexFailure.UNKNOWN_KEY, "index signed by unknown key $keyId")
        when (val v = minisign.verify(indexBytes, sigBytes, listOf(signer))) {
            is VerifyOutcome.Fail -> return IndexVerdict.Bad(IndexFailure.BAD_SIGNATURE, "index: ${v.reason}")
            is VerifyOutcome.Ok -> Unit
        }

        val doc = try { IndexDocument.parse(indexBytes) } catch (e: IndexFormatException) {
            return IndexVerdict.Bad(IndexFailure.MALFORMED, e.message ?: "index malformed")
        }
        if (lastSequence != null && doc.sequence < lastSequence) {
            return IndexVerdict.Bad(IndexFailure.ROLLBACK, "index sequence ${doc.sequence} is lower than $lastSequence")
        }
        if (nowMs != VERIFY_IGNORING_EXPIRY && nowMs >= doc.expiresAtMs) {
            return IndexVerdict.Bad(IndexFailure.EXPIRED, "index expired at ${doc.expiresAtMs}")
        }
        return IndexVerdict.Ok(VerifiedIndex(doc.sequence, doc.expiresAtMs, doc.modules, doc.revocations))
    }

    sealed interface KeysVerdict { data class Ok(val keys: KeysFile) : KeysVerdict; data class Bad(val bad: IndexVerdict.Bad) : KeysVerdict }

    // Step 1 on its own, for the registry's keys high-water check and for the release checklist.
    fun verifyKeys(keysBytes: ByteArray, keysSig: ByteArray): KeysVerdict {
        fun bad(detail: String) = KeysVerdict.Bad(IndexVerdict.Bad(IndexFailure.BAD_KEYS_FILE, detail))
        if (keysBytes.size > KeysFile.MAX_BYTES) return bad("keys file too large")
        when (val v = minisign.verify(keysBytes, keysSig, listOf(root))) {
            is VerifyOutcome.Fail -> return bad("keys file not signed by the root key: ${v.reason}")
            is VerifyOutcome.Ok -> Unit
        }
        val keys = try { KeysFile.parse(keysBytes, root) } catch (e: IndexFormatException) { return bad(e.message ?: "keys file malformed") }
        if (keys.sequence < keysFloor) return bad("keys file sequence ${keys.sequence} is older than $keysFloor")
        return KeysVerdict.Ok(keys)
    }

    companion object {
        const val VERIFY_IGNORING_EXPIRY = Long.MIN_VALUE
    }
}

// What the phone may offer to install from a verified index: the newest version of a module that is
// neither revoked nor quarantined, whose APK and signer are not on the revocation list. Callers
// still refuse installs while the registry reports the index as stale (SECURITY 2.4 S2).
fun VerifiedIndex.installable(module: Module): ModuleVersion? {
    if (module.tier == Tier.REVOKED || module.tier == Tier.QUARANTINED) return null
    val revoked = revocations.toSet()
    return module.versions.sortedByDescending { it.versionCode }
        .firstOrNull { it.artifact.sha256 !in revoked && it.artifact.signer !in revoked }
}
