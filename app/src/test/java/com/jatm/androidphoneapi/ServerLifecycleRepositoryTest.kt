package com.jatm.androidphoneapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerLifecycleRepositoryTest {
    @Before
    fun resetState() {
        ServerLifecycleRepository.markStopped()
    }

    @Test
    fun defaultStoppedStateCanStartButCannotStop() {
        val state = ServerLifecycleRepository.state.value

        assertEquals(ServerStatus.STOPPED, state.status)
        assertTrue(state.canStart)
        assertFalse(state.canStop)
        assertFalse(state.isRunning)
    }

    @Test
    fun runningStateCanStopButCannotStart() {
        ServerLifecycleRepository.markRunning()

        val state = ServerLifecycleRepository.state.value

        assertEquals(ServerStatus.RUNNING, state.status)
        assertFalse(state.canStart)
        assertTrue(state.canStop)
        assertTrue(state.isRunning)
    }

    @Test
    fun stoppingStateBlocksDuplicateStartAndStopActions() {
        ServerLifecycleRepository.markStopping()

        val state = ServerLifecycleRepository.state.value

        assertEquals(ServerStatus.STOPPING, state.status)
        assertFalse(state.canStart)
        assertFalse(state.canStop)
        assertFalse(state.isRunning)
    }
}
