package com.jarvis.os.app.data

import com.jarvis.os.app.data.model.DeviceType
import com.jarvis.os.app.data.repository.HomeAutomationPolicyViolation
import com.jarvis.os.app.data.repository.MockHomeAutomationRepository
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAutomationRepositoryTest {

    @Test
    fun `toggling a supported device succeeds`() {
        val repo = MockHomeAutomationRepository()
        val tv = repo.devices.value.first { it.type == DeviceType.TV }
        val before = tv.isOn

        repo.toggle(tv.deviceId)

        val after = repo.devices.value.first { it.deviceId == tv.deviceId }
        assertTrue(after.isOn != before)
    }

    @Test
    fun `toggling a security-tier device is rejected even calling the repository directly`() {
        val repo = MockHomeAutomationRepository()
        val lock = repo.devices.value.first { it.type == DeviceType.DOOR_LOCKS }

        assertThrows(HomeAutomationPolicyViolation::class.java) {
            repo.toggle(lock.deviceId)
        }

        // State must be unchanged after the rejected attempt.
        val unchanged = repo.devices.value.first { it.deviceId == lock.deviceId }
        assertTrue(unchanged.isOn == lock.isOn)
    }

    @Test
    fun `toggling a camera is also rejected`() {
        val repo = MockHomeAutomationRepository()
        val camera = repo.devices.value.first { it.type == DeviceType.SECURITY_CAMERAS }
        assertThrows(HomeAutomationPolicyViolation::class.java) {
            repo.toggle(camera.deviceId)
        }
    }
}
