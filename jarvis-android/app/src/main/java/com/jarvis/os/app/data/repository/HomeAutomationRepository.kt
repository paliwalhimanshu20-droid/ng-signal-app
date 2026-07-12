package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.DeviceType
import com.jarvis.os.app.data.model.HomeDevice
import com.jarvis.os.app.feature.homeautomation.HomeAutomationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class HomeAutomationPolicyViolation(message: String) : Exception(message)

/**
 * Part 12: the repository is the SECOND enforcement point for
 * HomeAutomationPolicy (the UI is the first — see
 * HomeAutomationScreen.kt, which never renders a toggle for an
 * unsupported device). `toggle()` re-checks the policy itself rather
 * than trusting the caller, so a future screen or test that calls this
 * repository directly can't accidentally bypass the safety boundary
 * just because it skipped the UI layer.
 */
interface HomeAutomationRepository {
    val devices: StateFlow<List<HomeDevice>>
    fun toggle(deviceId: String)
}

@Singleton
class MockHomeAutomationRepository @Inject constructor() : HomeAutomationRepository {
    private val _devices = MutableStateFlow(
        listOf(
            HomeDevice(UUID.randomUUID().toString(), "Living Room TV", DeviceType.TV, connectionId = "conn-tv", isOn = false),
            HomeDevice(UUID.randomUUID().toString(), "Bedroom AC", DeviceType.AC, connectionId = "conn-ac", isOn = true),
            HomeDevice(UUID.randomUUID().toString(), "Hallway Lights", DeviceType.LIGHTS, connectionId = "conn-lights", isOn = true),
            HomeDevice(UUID.randomUUID().toString(), "Study Fan", DeviceType.FANS, connectionId = null, isOn = false),
            HomeDevice(UUID.randomUUID().toString(), "Living Room Speaker", DeviceType.SPEAKERS, connectionId = "conn-speaker", isOn = false),
            // Security-tier devices are still listed (so the Owner can see
            // WHY they're unavailable, per HomeAutomationPolicy's own
            // reasoning) but every entry starts disconnected and un-toggleable.
            HomeDevice(UUID.randomUUID().toString(), "Front Door Lock", DeviceType.DOOR_LOCKS, connectionId = null, isOn = false),
            HomeDevice(UUID.randomUUID().toString(), "Driveway Camera", DeviceType.SECURITY_CAMERAS, connectionId = null, isOn = false),
        ),
    )
    override val devices: StateFlow<List<HomeDevice>> = _devices.asStateFlow()

    override fun toggle(deviceId: String) {
        val device = _devices.value.firstOrNull { it.deviceId == deviceId }
            ?: throw HomeAutomationPolicyViolation("No device found with id '$deviceId'.")
        if (!HomeAutomationPolicy.isSupported(device.type)) {
            throw HomeAutomationPolicyViolation(
                HomeAutomationPolicy.unavailableReason(device.type)
                    ?: "Device type '${device.type}' is not supported.",
            )
        }
        _devices.update { list -> list.map { if (it.deviceId == deviceId) it.copy(isOn = !it.isOn) else it } }
    }
}
