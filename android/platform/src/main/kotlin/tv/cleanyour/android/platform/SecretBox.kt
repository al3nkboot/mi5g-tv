package tv.cleanyour.android.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// AES-256-GCM under a Keystore key (SECURITY 4.1: alias cleanyourtv.wrap.v1, StrongBox when
// available). Seals the software ADB key when the Keystore cannot sign the adbd way. The sealed
// blob is: one version byte, the 12-byte IV, the ciphertext with its 16-byte tag.
class SecretBox(private val alias: String = ALIAS) {

    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "unexpected GCM IV length" }
        val ct = cipher.doFinal(plain)
        return byteArrayOf(VERSION) + iv + ct
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > 1 + IV_BYTES + TAG_BYTES) { "sealed blob too short" }
        require(sealed[0] == VERSION) { "sealed blob version ${sealed[0]}" }
        val iv = sealed.copyOfRange(1, 1 + IV_BYTES)
        val ct = sealed.copyOfRange(1 + IV_BYTES, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BYTES * 8, iv))
        return cipher.doFinal(ct)
    }

    fun delete() {
        val ks = keyStore()
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    val exists: Boolean get() = keyStore().containsAlias(alias)

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey {
        val ks = keyStore()
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        for (strongBox in listOf(true, false)) {
            try {
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                if (strongBox) spec.setIsStrongBoxBacked(true)
                kg.init(spec.build())
                return kg.generateKey()
            } catch (e: StrongBoxUnavailableException) {
                continue
            }
        }
        throw IllegalStateException("could not create the wrapping key")
    }

    companion object {
        const val ALIAS = "cleanyourtv.wrap.v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val VERSION: Byte = 1
    }
}
