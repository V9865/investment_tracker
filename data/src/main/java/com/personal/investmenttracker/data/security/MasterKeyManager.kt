package com.personal.investmenttracker.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MasterKeyInvalidatedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MasterKeyManager(
    private val context: Context,
    private val keyAlias: String = MASTER_KEY_ALIAS,
) {
    private val keyStore: KeyStore =
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
            }
        } catch (_: Exception) {
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
            }
        }

    private val wrappedKeyFile: File
        get() = File(context.filesDir, WRAPPED_DB_KEY_FILENAME)

    fun hasWrappedKey(): Boolean = wrappedKeyFile.exists()

    fun deleteMasterKey() {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
        if (wrappedKeyFile.exists()) {
            wrappedKeyFile.delete()
        }
    }

    fun getOrCreateMasterKey(userAuthenticationRequired: Boolean = true): SecretKey {
        if (keyStore.containsAlias(keyAlias)) {
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )

        val builder =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)

        if (userAuthenticationRequired) {
            builder.setUserAuthenticationRequired(true)
            builder.setInvalidatedByBiometricEnrollment(true)
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun initCipherForEncryption(): Cipher {
        val masterKey = getOrCreateMasterKey(userAuthenticationRequired = true)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        return cipher
    }

    fun initCipherForDecryption(): Cipher {
        val masterKey = getOrCreateMasterKey(userAuthenticationRequired = true)
        val iv = readIvFromFile()
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw MasterKeyInvalidatedException(
                "Keystore key was permanently invalidated (biometric enrollment changed)",
                e,
            )
        }
        return cipher
    }

    fun createBiometricCryptoObject(cipher: Cipher): BiometricPrompt.CryptoObject = BiometricPrompt.CryptoObject(cipher)

    fun generateAndWrapDbKey(encryptCipher: Cipher): ByteArray {
        val rawDbKey = ByteArray(DB_KEY_BYTE_LENGTH)
        SecureRandom().nextBytes(rawDbKey)

        try {
            val encryptedBytes = encryptCipher.doFinal(rawDbKey)
            val iv = encryptCipher.iv
            val output = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, output, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, output, iv.size, encryptedBytes.size)

            wrappedKeyFile.writeBytes(output)
            return rawDbKey
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw MasterKeyInvalidatedException("Key permanently invalidated", e)
        }
    }

    fun unwrapDbKey(decryptCipher: Cipher): ByteArray {
        check(wrappedKeyFile.exists()) { "Wrapped DB key file does not exist" }

        val allBytes = wrappedKeyFile.readBytes()
        check(allBytes.size > GCM_IV_LENGTH_BYTES) { "Malformed wrapped key file" }

        val ciphertext = ByteArray(allBytes.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(allBytes, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.size)

        try {
            return decryptCipher.doFinal(ciphertext)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw MasterKeyInvalidatedException(
                "Keystore key was invalidated by biometric change",
                e,
            )
        }
    }

    private fun readIvFromFile(): ByteArray {
        check(wrappedKeyFile.exists()) { "Wrapped DB key file does not exist" }
        val allBytes = wrappedKeyFile.readBytes()
        check(allBytes.size >= GCM_IV_LENGTH_BYTES) { "Wrapped key file corrupted" }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(allBytes, 0, iv, 0, GCM_IV_LENGTH_BYTES)
        return iv
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "investment_tracker_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val WRAPPED_DB_KEY_FILENAME = "wrapped_db_key.bin"

        const val DB_KEY_BYTE_LENGTH = 32
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }
}
