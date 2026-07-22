package dev.switchnat.app.ss

import java.security.MessageDigest
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class AeadMethod(
    val wireName: String,
    val keySize: Int,
    val saltSize: Int,
    val tagSize: Int
) {
    AES_128_GCM("aes-128-gcm", 16, 16, 16),
    AES_256_GCM("aes-256-gcm", 32, 32, 16),
    CHACHA20_IETF_POLY1305("chacha20-ietf-poly1305", 32, 32, 16);

    companion object {
        fun fromWireName(name: String): AeadMethod {
            return entries.firstOrNull { it.wireName == name }
                ?: throw IllegalArgumentException("Unsupported AEAD method: $name")
        }
    }
}

object ShadowsocksKeys {
    fun evpBytesToKey(password: String, keySize: Int): ByteArray {
        require(keySize > 0) { "Key size must be positive" }
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("MD5")
        val result = ByteArray(keySize)
        var generated = 0
        var previous = ByteArray(0)
        while (generated < keySize) {
            digest.reset()
            digest.update(previous)
            digest.update(passwordBytes)
            previous = digest.digest()
            val copyLength = minOf(previous.size, keySize - generated)
            previous.copyInto(result, destinationOffset = generated, endIndex = copyLength)
            generated += copyLength
        }
        return result
    }

    fun hkdfSha1(masterKey: ByteArray, salt: ByteArray, length: Int): ByteArray {
        require(length in 0..MAX_HKDF_SHA1_LENGTH) { "Invalid HKDF output length: $length" }
        val extract = Mac.getInstance(HMAC_SHA1)
        extract.init(SecretKeySpec(salt, HMAC_SHA1))
        val pseudorandomKey = extract.doFinal(masterKey)

        val output = ByteArray(length)
        val expand = Mac.getInstance(HMAC_SHA1)
        expand.init(SecretKeySpec(pseudorandomKey, HMAC_SHA1))
        var previous = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < length) {
            expand.update(previous)
            expand.update(SUBKEY_INFO)
            expand.update(counter.toByte())
            previous = expand.doFinal()
            val copyLength = minOf(previous.size, length - generated)
            previous.copyInto(output, destinationOffset = generated, endIndex = copyLength)
            generated += copyLength
            counter++
        }
        return output
    }

    private const val HMAC_SHA1 = "HmacSHA1"
    private const val SHA1_OUTPUT_SIZE = 20
    private const val MAX_HKDF_SHA1_LENGTH = 255 * SHA1_OUTPUT_SIZE
    private val SUBKEY_INFO = "ss-subkey".toByteArray(Charsets.UTF_8)
}

class AeadCipher(private val method: AeadMethod, private val subkey: ByteArray) {
    init {
        require(subkey.size == method.keySize) {
            "Subkey size ${subkey.size} did not match ${method.keySize}"
        }
    }

    fun encryptOnce(plain: ByteArray): ByteArray = encrypt(plain, ByteArray(NONCE_SIZE))

    fun decryptOnce(cipherText: ByteArray): ByteArray = decrypt(cipherText, ByteArray(NONCE_SIZE))

    fun encrypt(plain: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherName())
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(subkey, keyAlgorithm()), parameterSpec(nonce))
        return cipher.doFinal(plain)
    }

    fun decrypt(cipherText: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherName())
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(subkey, keyAlgorithm()), parameterSpec(nonce))
        return cipher.doFinal(cipherText)
    }

    private fun cipherName(): String = when (method) {
        AeadMethod.AES_128_GCM, AeadMethod.AES_256_GCM -> "AES/GCM/NoPadding"
        AeadMethod.CHACHA20_IETF_POLY1305 -> "ChaCha20-Poly1305"
    }

    private fun keyAlgorithm(): String = when (method) {
        AeadMethod.AES_128_GCM, AeadMethod.AES_256_GCM -> "AES"
        AeadMethod.CHACHA20_IETF_POLY1305 -> "ChaCha20"
    }

    private fun parameterSpec(nonce: ByteArray): AlgorithmParameterSpec {
        require(nonce.size == NONCE_SIZE) { "AEAD nonce must be $NONCE_SIZE bytes" }
        return when (method) {
            AeadMethod.AES_128_GCM, AeadMethod.AES_256_GCM -> GCMParameterSpec(128, nonce)
            AeadMethod.CHACHA20_IETF_POLY1305 -> IvParameterSpec(nonce)
        }
    }

    private companion object {
        const val NONCE_SIZE = 12
    }
}

class NonceCounter(private val size: Int = 12) {
    private val nonce = ByteArray(size)

    init {
        require(size > 0) { "Nonce size must be positive" }
    }

    fun next(): ByteArray {
        val current = nonce.copyOf()
        increment()
        return current
    }

    private fun increment() {
        for (index in nonce.indices) {
            val value = (nonce[index].toInt() and 0xff) + 1
            nonce[index] = value.toByte()
            if (value <= 0xff) return
        }
    }
}
