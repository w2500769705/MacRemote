package com.macremote.app

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun parsePong() {
        assertEquals("客厅的 MacBook", RemoteClient.parsePong("pong 客厅的 MacBook"))
        assertEquals("Mac", RemoteClient.parsePong("pong"))
        assertNull(RemoteClient.parsePong("hello"))
    }

    @Test fun accelKeepsSignAndGrowsFaster() {
        assertTrue(accel(-2f, 1f) < 0)
        assertTrue(accel(20f, 1f) / 20f > accel(2f, 1f) / 2f)
    }

    @Test fun profilesHaveNextKeys() {
        assertEquals("bracketright", profileById("bilibili").next)
        assertEquals("jellyfin", profileById("nope").id)
    }
}
