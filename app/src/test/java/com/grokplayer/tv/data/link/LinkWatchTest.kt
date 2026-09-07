package com.grokplayer.tv.data.link

import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkWatchTest {
    @Test
    fun refusedDropsAfterThree() {
        assertFalse(LinkWatch.shouldEndSession(2, 2))
        assertTrue(LinkWatch.shouldEndSession(3, 3))
    }

    @Test
    fun emptyHttpNeverCountsAsUnreachable() {
        assertFalse(LinkWatch.shouldEndSession(0, 0))
        assertFalse(LinkWatch.shouldEndSession(1, 0))
        assertFalse(LinkWatch.shouldEndSession(3, 0))
    }

    @Test
    fun timeoutNeedsEightMisses() {
        assertFalse(LinkWatch.shouldEndSession(7, 0))
        assertTrue(LinkWatch.shouldEndSession(8, 0))
    }

    @Test
    fun connectExceptionIsDeadPc() {
        assertTrue(LinkWatch.isUnreachable(ConnectException("Failed to connect to /10.0.2.2:17422")))
        assertFalse(LinkWatch.isUnreachable(SocketTimeoutException("timeout")))
    }
}
