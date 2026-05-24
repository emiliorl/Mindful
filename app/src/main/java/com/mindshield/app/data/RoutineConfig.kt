package com.mindshield.app.data

import java.time.LocalTime

enum class RoutinePhase { MORNING, WIND_DOWN, SLEEP }

data class ChecklistItem(
    val id: String,
    val text: String,
    val unlocksPackage: String? = null
)

data class MorningConfig(
    val enabled: Boolean = false,
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val phoneAvailableTime: LocalTime = LocalTime.of(7, 30),
    val checklist: List<ChecklistItem> = emptyList()
)

data class WindDownConfig(
    val enabled: Boolean = false,
    val startTime: LocalTime = LocalTime.of(22, 0),
    val sleepTime: LocalTime = LocalTime.of(23, 0),
    val extendedDelaySeconds: Int = 30,
    val checklist: List<ChecklistItem> = emptyList()
)
