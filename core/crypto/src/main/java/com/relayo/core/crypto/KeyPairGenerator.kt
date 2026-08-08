package com.relayo.core.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject

class RelayoKeyPairGenerator @Inject constructor() {

    fun generate():KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }
}