package com.relayo.core.crypto

import org.junit.Assert.*
import org.junit.Test

class CryptoTest {

    @Test
    fun aesGcmEncryptDecryptRoundTrip() {
        val cipher = AesGcmCipher()
        val key = cipher.generateKey()
        val plaintext = "Hello Relayo mesh"
        val encrypted = cipher.encrypt(key, plaintext)
        val decrypted = cipher.decrypt(key, encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun aesGcmDifferentKeysFail() {
        val cipher = AesGcmCipher()
        val key1 = cipher.generateKey()
        val key2 = cipher.generateKey()
        val enc = cipher.encrypt(key1, "secret")
        try {
            cipher.decrypt(key2, enc)
            fail("Should have thrown")
        } catch(_:Exception) {
            // expected
        }
    }

    @Test
    fun ecdhDerivesSameKeyBothWays() {
        val gen = RelayoKeyPairGenerator()
        val kpA = gen.generate()
        val kpB = gen.generate()
        val ecdh = EcdhKeyAgreement()
        val keyA = ecdh.deriveSharedKey(kpA.private.encoded, kpB.public.encoded)
        val keyB = ecdh.deriveSharedKey(kpB.private.encoded, kpA.public.encoded)
        assertArrayEquals(keyA.encoded, keyB.encoded)
    }

    @Test
    fun ecdhDifferentPeersDeriveDifferentKeys() {
        val gen = RelayoKeyPairGenerator()
        val kpA = gen.generate()
        val kpB = gen.generate()
        val kpC = gen.generate()
        val ecdh = EcdhKeyAgreement()
        val keyAB = ecdh.deriveSharedKey(kpA.private.encoded, kpB.public.encoded)
        val keyAC = ecdh.deriveSharedKey(kpA.private.encoded, kpC.public.encoded)
        assertFalse(keyAB.encoded.contentEquals(keyAC.encoded))
    }

    @Test
    fun ecdhEncryptWithDerivedKey() {
        val gen = RelayoKeyPairGenerator()
        val kpA = gen.generate()
        val kpB = gen.generate()
        val ecdh = EcdhKeyAgreement()
        val cipher = AesGcmCipher()
        val keyA = ecdh.deriveSharedKey(kpA.private.encoded, kpB.public.encoded)
        val keyB = ecdh.deriveSharedKey(kpB.private.encoded, kpA.public.encoded)
        val msg = "Per-peer secret"
        val enc = cipher.encrypt(keyA, msg)
        val dec = cipher.decrypt(keyB, enc)
        assertEquals(msg, dec)
    }
}
