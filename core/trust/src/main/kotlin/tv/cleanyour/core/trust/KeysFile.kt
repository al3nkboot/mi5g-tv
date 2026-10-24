package tv.cleanyour.core.trust

import tv.cleanyour.core.trust.IndexJson.arr
import tv.cleanyour.core.trust.IndexJson.asObject
import tv.cleanyour.core.trust.IndexJson.instantMs
import tv.cleanyour.core.trust.IndexJson.long
import tv.cleanyour.core.trust.IndexJson.str

// keys.json, schema 1, signed by the root key (SECURITY 2.4 S3). It names the keys that may sign
// index.json and the keys that no longer may:
//
//   {"schema": 1, "sequence": 1, "generated": "2026-10-19T12:00:00Z",
//    "keys": [{"id": "0190B42669E69FB0", "public_key": "RWSwn+Zp...", "status": "active"},
//             {"id": "...", "public_key": "...", "status": "revoked"}]}
//
// `id` is the minisign key id as minisign prints it and must equal the id inside `public_key`.
// `sequence` only grows; the registry refuses a keys file older than one it has already accepted,
// so an old file that still lists a since-revoked key as active cannot be replayed.
data class KeysFile(val sequence: Long, val generatedAtMs: Long, val keys: KeySet) {
    companion object {
        const val SCHEMA = 1L
        const val MAX_BYTES = 64 * 1024
        const val MAX_KEYS = 32
        private val KEY_ID = Regex("^[0-9A-F]{16}$")
        private val KEY_LINE = Regex("^[A-Za-z0-9+/]{56}$")

        // The sequence of a keys file the verifier has already accepted, without the root key.
        fun peekSequence(bytes: ByteArray): Long? = try {
            IndexJson.parseObject(bytes, MAX_BYTES).long("sequence", 1..Long.MAX_VALUE)
        } catch (e: IndexFormatException) {
            null
        }

        fun parse(bytes: ByteArray, root: MinisignKey): KeysFile {
            val o = IndexJson.parseObject(bytes, MAX_BYTES)
            if (o.long("schema", 0..Long.MAX_VALUE) != SCHEMA) throw IndexFormatException("keys: unsupported schema")
            val sequence = o.long("sequence", 1..Long.MAX_VALUE)
            val generated = o.instantMs("generated")
            val active = mutableListOf<MinisignKey>()
            val revoked = mutableListOf<ByteArray>()
            val seen = mutableSetOf<String>()
            for ((i, el) in o.arr("keys", MAX_KEYS).withIndex()) {
                val k = el.asObject("keys[$i]")
                val id = k.str("id", 16, KEY_ID)
                val key = try { Ed25519Minisign.parsePublicKey(k.str("public_key", 56, KEY_LINE)) } catch (e: IllegalArgumentException) {
                    throw IndexFormatException("keys[$i]: ${e.message}")
                }
                if (Ed25519Minisign.keyIdHex(key.id) != id) throw IndexFormatException("keys[$i]: id does not match the key")
                if (!seen.add(id)) throw IndexFormatException("keys[$i]: duplicate id")
                if (key.id.contentEquals(root.id)) throw IndexFormatException("keys[$i]: the root key cannot sign the index")
                when (k.str("status", 10)) {
                    "active" -> active += key
                    "revoked" -> revoked += key.id
                    else -> throw IndexFormatException("keys[$i]: status must be active or revoked")
                }
            }
            if (active.isEmpty()) throw IndexFormatException("keys: no active key")
            return KeysFile(sequence, generated, KeySet(root, active, revoked))
        }
    }
}
