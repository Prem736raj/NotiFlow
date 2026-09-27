package com.premraj.notiflow.data

enum class FocusProfileType(val label: String) {
    WORK("Work"),
    STUDY("Study"),
    SLEEP("Sleep"),
    QUIET_HOURS("Quiet Hours")
}

data class FocusStatus(
    val isActive: Boolean = false,
    val activeProfile: FocusProfileType? = null,
    val isScheduled: Boolean = false,
    val blockedCount: Int = 0,
    val activatedAt: Long = 0L
)
