package com.jarvis.os.app.data.model

enum class SystemHealthLevel { HEALTHY, DEGRADED, CRITICAL }

/** Sprint 11 "Production Readiness" -- a single computed rollup across connection, tool and workflow health, read by the Executive Dashboard and available for a future background health-check job to publish CoreEvents from. */
data class SystemHealthSnapshot(
    val level: SystemHealthLevel,
    val connectionsHealthy: Int,
    val connectionsTotal: Int,
    val toolsHealthy: Int,
    val toolsTotal: Int,
    val recentWorkflowFailureRate: Double,
    val reasons: List<String>,
)
