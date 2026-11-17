package com.shashank.applocker.service

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStatusTest {
    private val status = ServiceStatus()

    @Test fun startsDisconnectedSoARestartedProcessNeverClaimsToBeRunning() =
        assertEquals(ServiceState.DISCONNECTED, status.state.value)

    @Test fun lifecycleTransitions() {
        status.onConnected()
        assertEquals(ServiceState.CONNECTED, status.state.value)
        status.onInterrupted()
        assertEquals(ServiceState.INTERRUPTED, status.state.value)
        status.onDisconnected()
        assertEquals(ServiceState.DISCONNECTED, status.state.value)
    }

    @Test fun eventsAfterAnInterruptionMeanItIsWorkingAgain() {
        status.onConnected()
        status.onInterrupted()
        status.onEventReceived()
        assertEquals(ServiceState.CONNECTED, status.state.value)
    }

    @Test fun anEventDoesNotReviveADisconnectedService() {
        status.onEventReceived()
        assertEquals(ServiceState.DISCONNECTED, status.state.value)
    }

    @Test fun reconnectAfterSystemRestart() {
        status.onConnected()
        status.onDisconnected()
        status.onConnected()
        assertEquals(ServiceState.CONNECTED, status.state.value)
    }

    @Test fun lockCoordinatorCancelReachesTheService() = runTest {
        val c = LockCoordinator()
        c.cancelled.test {
            c.cancel()
            awaitItem()
        }
    }
}
