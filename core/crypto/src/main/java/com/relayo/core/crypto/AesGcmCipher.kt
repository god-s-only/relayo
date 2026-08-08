package com.relayo.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class EncryptedPayload(
    val ivBytes:ByteArray,
    val cipherBytes:ByteArray
)

class AesGcmCipher @Inject constructor() {

    private val random = SecureRandom()

    fun generateKey():SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        return generator.generateKey()
    }

    fun encrypt(key:SecretKey, plaintext:String):EncryptedPayload {
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(ivBytes = iv, cipherBytes = cipherBytes)
    }

    fun decrypt(key:SecretKey, payload:EncryptedPayload):String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.ivBytes))
        val plainBytes = cipher.doFinal(payload.cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun keyFromBytes(bytes:ByteArray):SecretKey = SecretKeySpec(bytes, "AES")
}