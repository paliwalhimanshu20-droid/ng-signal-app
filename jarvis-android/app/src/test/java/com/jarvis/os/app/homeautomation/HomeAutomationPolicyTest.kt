package com.jarvis.os.app.homeautomation

import com.jarvis.os.app.data.model.DeviceType
import com.jarvis.os.app.feature.homeautomation.HomeAutomationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 16 (Testing) requires Home Automation coverage. This is a pure
 * JUnit4 test with zero Android framework dependency — it runs as a
 * plain JVM unit test (`./gradlew test`), not an instrumented test, so
 * it's the cheapest, fastest thing in this codebase to actually verify
 * once a real build environment is available.
 */
class HomeAutomationPolicyTest {

    @Test
    fun `every explicitly supported device type is controllable`() {
        val expected = setOf(
            DeviceType.TV, DeviceType.AC, DeviceType.LIGHTS, DeviceType.FANS,
            DeviceType.SPEAKERS, DeviceType.CURTAINS, DeviceType.ROBOT_VACUUM,
        )
        for (type in expected) {
            assertTrue("$type should be supported", HomeAutomationPolicy.isSupported(type))
            assertNull("$type should have no unavailable reason", HomeAutomationPolicy.unavailableReason(type))
        }
    }

    @Test
    fun `every security-tier device type is never supported`() {
        val securityTypes = setOf(
            DeviceType.DOOR_LOCKS, DeviceType.SECURITY_CAMERAS, DeviceType.GAS_VALVE,
            DeviceType.ALARM_SYSTEM, DeviceType.FIRE_SAFETY,
        )
        for (type in securityTypes) {
            assertFalse("$type must never be supported", HomeAutomationPolicy.isSupported(type))
            assertNotNull("$type must explain why it's unavailable", HomeAutomationPolicy.unavailableReason(type))
        }
    }

    @Test
    fun `supported and never-supported sets are disjoint`() {
        val overlap = HomeAutomationPolicy.allSupportedTypes() intersect HomeAutomationPolicy.allNeverSupportedTypes()
        assertTrue("A device type must never be in both sets", overlap.isEmpty())
    }

    @Test
    fun `every DeviceType enum value is classified one way or the other`() {
        val allTypes = DeviceType.values().toSet()
        val classified = HomeAutomationPolicy.allSupportedTypes() + HomeAutomationPolicy.allNeverSupportedTypes()
        assertEquals(
            "Every DeviceType must be either supported or explicitly never-supported — " +
                "an unclassified type would silently fall through as 'not supported' today, " +
                "but this test exists so that silent fallthrough is never the ONLY thing " +
                "protecting a future new DeviceType from being wrongly exposed.",
            allTypes,
            classified,
        )
    }
}
