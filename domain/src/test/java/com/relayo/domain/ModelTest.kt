package com.relayo.domain

import com.relayo.domain.model.BridgeRequestType
import com.relayo.domain.model.EphemeralIdentity
import com.relayo.domain.model.MeshDevice
import org.junit.Assert.*
import org.junit.Test

class ModelTest {

    @Test
    fun ephemeralIdentityEqualityIsBySessionId() {
        val a = EphemeralIdentity("sess1", ByteArray(10) { 1 }, ByteArray(10) { 2 })
        val b = EphemeralIdentity("sess1", ByteArray(10) { 9 }, ByteArray(10) { 9 })
        val c = EphemeralIdentity("sess2", ByteArray(10) { 1 }, ByteArray(10) { 2 })
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun meshDeviceFingerprintIsOptional() {
        val d1 = MeshDevice("id1", "Alice", 1, -50, 123L, true, fingerprint = "abc123")
        val d2 = MeshDevice("id2", "Bob", 1, -60, 123L, true)
        assertEquals("abc123", d1.fingerprint)
        assertNull(d2.fingerprint)
    }

    @Test
    fun bridgeRequestTypeValues() {
        assertEquals(3, BridgeRequestType.values().size)
        assertNotNull(BridgeRequestType.valueOf("WEATHER"))
    }
}
