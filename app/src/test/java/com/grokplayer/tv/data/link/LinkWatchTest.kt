package com.grokplayer.tv.data.link

import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
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
    fun oneDisconnectedStateDoesNotDrop() {
        assertFalse(LinkWatch.shouldDropDisconnected(1))
        assertFalse(LinkWatch.shouldDropDisconnected(2))
        assertTrue(LinkWatch.shouldDropDisconnected(3))
    }

    @Test
    fun deadHelloDropsAfterTwoMisses() {
        assertFalse(LinkWatch.shouldDropAfterMiss(1, helloOk = false))
        assertTrue(LinkWatch.shouldDropAfterMiss(2, helloOk = false))
    }

    @Test
    fun helloOkStillDropsIfStateKeepsFailing() {
        assertFalse(LinkWatch.shouldDropAfterMiss(3, helloOk = true))
        assertTrue(LinkWatch.shouldDropAfterMiss(4, helloOk = true))
    }

    @Test
    fun connectExceptionIsDeadPc() {
        assertTrue(LinkWatch.isUnreachable(ConnectException("Failed to connect to /10.0.2.2:17422")))
        assertTrue(LinkWatch.isUnreachable(SocketTimeoutException("timeout")))
    }

    @Test
    fun bucketsKeepNearbyPairedAndConnectedApart() {
        val nearby = listOf(
            NearbyPc("live", "Open PC", "10.0.0.8", 17422, 1),
            NearbyPc("paired-on", "Known PC", "10.0.0.9", 17422, 1),
        )
        val paired = listOf(
            PairedPc("paired-on", "Known PC", "10.0.0.9", 17422, "t1"),
            PairedPc("off", "Old PC", "10.0.0.10", 17422, "t2"),
            PairedPc("now", "Active PC", "10.0.0.11", 17422, "t3"),
        )
        val buckets = LinkWatch.buckets(nearby, paired, "now")
        assertEquals(listOf("now"), buckets.connected.map { it.id })
        assertEquals(listOf("paired-on", "off"), buckets.paired.map { it.id })
        assertEquals(listOf("live"), buckets.nearby.map { it.id })
    }

    @Test
    fun nearbyOnlyGetsPairAction() {
        val actions = LinkWatch.actions(nearby = true, connected = false)
        assertTrue(actions.pair)
        assertFalse(actions.connect)
        assertFalse(actions.manage)
        assertFalse(actions.disconnect)
        assertFalse(actions.forget)
    }

    @Test
    fun pairedOfflineGetsConnectAndForget() {
        val actions = LinkWatch.actions(nearby = false, connected = false)
        assertFalse(actions.pair)
        assertTrue(actions.connect)
        assertFalse(actions.manage)
        assertFalse(actions.disconnect)
        assertTrue(actions.forget)
    }

    @Test
    fun connectedGetsManageDisconnectAndForget() {
        val actions = LinkWatch.actions(nearby = false, connected = true)
        assertFalse(actions.pair)
        assertFalse(actions.connect)
        assertTrue(actions.manage)
        assertTrue(actions.disconnect)
        assertTrue(actions.forget)
    }
}
