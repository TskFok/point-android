package com.pointquest.android.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSessionPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = requireNotNull(cipher.iv).copyOf()
        check(iv.size == IV_SIZE_BYTES) { "Android Keystore returned an invalid GCM IV" }
        val ciphertext = cipher.doFinal(plaintext)
        check(ciphertext.size == plaintext.size + TAG_SIZE_BYTES) {
            "Android Keystore returned an invalid GCM tag"
        }
        return EncryptedSessionPayload(iv = iv, ciphertext = ciphertext)
    }

    fun decrypt(payload: EncryptedSessionPayload): ByteArray {
        require(payload.iv.size == IV_SIZE_BYTES) { "Invalid GCM IV length" }
        require(payload.ciphertext.size >= TAG_SIZE_BYTES) { "Invalid GCM ciphertext" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_SIZE_BITS, payload.iv),
        )
        return cipher.doFinal(payload.ciphertext)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val KEY_ALIAS = "point_refresh_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128

        private const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}

data class EncryptedSessionPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)
