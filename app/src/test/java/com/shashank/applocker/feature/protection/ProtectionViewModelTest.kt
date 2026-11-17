package com.shashank.applocker.feature.protection

import com.shashank.applocker.MainDispatcherRule
import com.shashank.applocker.service.AccessibilityStatusSource
import com.shashank.applocker.service.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProtectionViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class Source(var enabled: Boolean) : AccessibilityStatusSource {
        override fun isServiceEnabled() = enabled
    }

    private val source = Source(false)
    private val service = ServiceStatus()
    private val vm by lazy { ProtectionViewModel(source, service) }

    @Test fun offWhenNotEnabledInSettings() =
        assertEquals(ProtectionStatus.OFF, vm.state.value.status)

    @Test fun enabledButNotConnectedYetIsStarting() {
        source.enabled = true
        vm.refresh()
        assertEquals(ProtectionStatus.STARTING, vm.state.value.status)
    }

    @Test fun onOnceTheServiceConnects() {
        source.enabled = true
        vm.refresh()
        service.onConnected()
        assertEquals(ProtectionStatus.ON, vm.state.value.status)
    }

    @Test fun killedServiceIsNotReportedAsOn() {
        source.enabled = true
        service.onConnected()
        vm.refresh()
        service.onDisconnected()
        assertEquals(ProtectionStatus.STARTING, vm.state.value.status)
    }

    @Test fun turningItOffInSettingsIsNoticedOnRefresh() {
        source.enabled = true
        service.onConnected()
        vm.refresh()
        source.enabled = false
        vm.refresh()
        assertEquals(ProtectionStatus.OFF, vm.state.value.status)
    }
}
