package com.relayo.core.crypto

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class EcdhKeyAgreement @Inject constructor() {

    /**
     * Derives a shared AES-256 key from my private key and peer's public key via ECDH P-256.
     * Uses SHA-256 of the raw ECDH secret as the AES key material (32 bytes = 256 bits).
     */
    fun deriveSharedKey(
        myPrivateKeyBytes:ByteArray,
        peerPublicKeyBytes:ByteArray
    ):SecretKey {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(myPrivateKeyBytes))
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val digest = MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = digest.digest(sharedSecret)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    fun isValidPublicKey(bytes:ByteArray):Boolean = try {
        val kf = KeyFactory.getInstance("EC")
        kf.generatePublic(X509EncodedKeySpec(bytes))
        true
    } catch(e:Exception) {
        false
    }
}
