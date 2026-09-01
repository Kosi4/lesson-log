package com.kosi.lessonlog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogRow(
    @SerialName("log_date") val logDate: String,
    @SerialName("morning_status") val morningStatus: String = "pending",
    @SerialName("evening_status") val eveningStatus: String = "pending",
    @SerialName("morning_note") val morningNote: String? = null,
    @SerialName("evening_note") val eveningNote: String? = null,
    @SerialName("morning_snooze_count") val morningSnoozeCount: Int = 0,
    @SerialName("evening_snooze_count") val eveningSnoozeCount: Int = 0,
) {
    fun statusOf(session: Session): String =
        if (session == Session.MORNING) morningStatus else eveningStatus

    fun noteOf(session: Session): String? =
        if (session == Session.MORNING) morningNote else eveningNote

    fun snoozesUsed(session: Session): Int =
        if (session == Session.MORNING) morningSnoozeCount else eveningSnoozeCount
}

@Serializable
data class ActionResult(
    val ok: Boolean = false,
    val status: String? = null,
    val snoozesLeft: Int? = null,
    val error: String? = null,
    val message: String? = null,
)
