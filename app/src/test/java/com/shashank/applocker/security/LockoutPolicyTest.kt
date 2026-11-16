package com.shashank.applocker.security

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutPolicyTest {
    @Test fun noLockoutBeforeThirdFailure() {
        listOf(0, 1, 2).forEach { assertEquals(LockoutAction.None, LockoutPolicy.actionFor(it)) }
    }

    @Test fun thirdAndFourthFailureWaitThirtySeconds() {
        listOf(3, 4).forEach { assertEquals(LockoutAction.Wait(30_000), LockoutPolicy.actionFor(it)) }
    }

    @Test fun fifthToSeventhWaitOneMinute() {
        listOf(5, 6, 7).forEach { assertEquals(LockoutAction.Wait(60_000), LockoutPolicy.actionFor(it)) }
    }

    @Test fun eighthAndNinthWaitFiveMinutes() {
        listOf(8, 9).forEach { assertEquals(LockoutAction.Wait(300_000), LockoutPolicy.actionFor(it)) }
    }

    @Test fun tenOrMoreRequireDeviceCredential() {
        listOf(10, 11, 50, 1000).forEach {
            assertEquals(LockoutAction.RequireDeviceCredential, LockoutPolicy.actionFor(it))
        }
    }
}
