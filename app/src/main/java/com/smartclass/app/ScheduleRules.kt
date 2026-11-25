package com.smartclass.app

import java.time.LocalTime

internal object ScheduleRules {
    fun overlaps(start: String, end: String, otherStart: String, otherEnd: String): Boolean {
        val leftStart = runCatching { LocalTime.parse(start) }.getOrNull() ?: return false
        val leftEnd = runCatching { LocalTime.parse(end) }.getOrNull() ?: return false
        val rightStart = runCatching { LocalTime.parse(otherStart) }.getOrNull() ?: return false
        val rightEnd = runCatching { LocalTime.parse(otherEnd) }.getOrNull() ?: return false
        if (!leftStart.isBefore(leftEnd) || !rightStart.isBefore(rightEnd)) return false
        return leftStart < rightEnd && rightStart < leftEnd
    }

    fun conflicts(schedule: List<ScheduledClass>, candidate: ScheduledClass): List<ScheduledClass> = schedule
        .filter { it.id != candidate.id && it.dayOfWeek == candidate.dayOfWeek }
        .filter { other ->
            overlaps(candidate.startTime, candidate.endTime, other.startTime, other.endTime) &&
                (candidate.groupId == other.groupId || candidate.teacherId == other.teacherId || candidate.room.equals(other.room, ignoreCase = true))
        }
}
