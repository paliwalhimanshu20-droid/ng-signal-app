package com.jarvis.os.app.feature.homeautomation

import com.jarvis.os.app.data.model.DeviceType

/**
 * Part 12's safety-critical rule, made structural rather than a UI
 * convention: "These devices shall remain unavailable by default" for
 * DOOR_LOCKS, SECURITY_CAMERAS, GAS_VALVE, ALARM_SYSTEM, FIRE_SAFETY,
 * and (generically) anything else this app doesn't explicitly know is
 * safe. This is a pure function over an enum — no Android framework
 * dependency — specifically so it's directly unit-testable without
 * instrumentation, and so no screen can accidentally bypass it by
 * constructing a HomeDevice UI element for an unsupported type: every
 * device-control affordance in HomeAutomationScreen.kt calls
 * `isControllable()` before rendering an on/off toggle, never renders a
 * toggle "optimistically" first.
 *
 * "By default" in Part 12's wording is honored literally: this object
 * has no mutable state and no owner-facing override anywhere in this
 * sprint's code — enabling any SECURITY-tier device is out of scope
 * here, deliberately, and would need its own explicit, reviewed
 * decision in a future sprint, not a settings toggle a distracted owner
 * could flip by accident.
 */
object HomeAutomationPolicy {

    private val supported: Set<DeviceType> = setOf(
        DeviceType.TV,
        DeviceType.AC,
        DeviceType.LIGHTS,
        DeviceType.FANS,
        DeviceType.SPEAKERS,
        DeviceType.CURTAINS,
        DeviceType.ROBOT_VACUUM,
    )

    private val neverSupported: Set<DeviceType> = setOf(
        DeviceType.DOOR_LOCKS,
        DeviceType.SECURITY_CAMERAS,
        DeviceType.GAS_VALVE,
        DeviceType.ALARM_SYSTEM,
        DeviceType.FIRE_SAFETY,
    )

    fun isSupported(type: DeviceType): Boolean = type in supported

    /**
     * A short, owner-facing reason a security-tier device is
     * unavailable — used by the UI to explain the disabled state rather
     * than just hiding it silently, per Article III's non-fabrication
     * spirit applied to UI: the Owner should be told WHY, not left to
     * guess whether it's a bug.
     */
    fun unavailableReason(type: DeviceType): String? {
        if (type !in neverSupported) return null
        return "Security-critical devices are not controllable from JARVIS by default. " +
            "This is a deliberate safety boundary, not a missing feature."
    }

    fun allSupportedTypes(): Set<DeviceType> = supported
    fun allNeverSupportedTypes(): Set<DeviceType> = neverSupported
}
