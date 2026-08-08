package com.relayo.core.crypto

import java.security.SecureRandom
import javax.inject.Inject

class SessionIdGenerator @Inject constructor() {

    private val random = SecureRandom()

    fun generate():String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}