package com.smartclass.app

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal data class TvLesson(
    val subject: String,
    val teacher: String,
    val group: String,
    val room: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val active: Boolean,
)

internal data class TvAttendanceSummary(val present: Int, val late: Int, val absent: Int, val justified: Int, val total: Int)

internal object TvDashboardData {
    fun attendance(data: AcademicData, groupId: String, date: LocalDate): TvAttendanceSummary {
        val roster = data.students.filter { it.groupId == groupId }
        val entries = data.attendance.filter { it.groupId == groupId && it.date == date.toString() }
        return TvAttendanceSummary(
            present = entries.count { it.status == "PRESENT" },
            late = entries.count { it.status == "LATE" },
            absent = entries.count { it.status == "ABSENT" },
            justified = entries.count { it.status == "JUSTIFIED" },
            total = roster.size,
        )
    }

    fun currentLesson(data: AcademicData, now: LocalDateTime): TvLesson? {
        val session = data.sessions.firstOrNull { it.endedAt.isBlank() }
        if (session != null) {
            val scheduled = data.schedule.firstOrNull { it.id == session.scheduleId }
            val start = runCatching { LocalDateTime.parse(session.startedAt) }.getOrNull()
            val end = scheduled?.let { lessonEnd(it, start?.toLocalDate() ?: now.toLocalDate(), start) }
            return TvLesson(session.subjectName, session.teacherName, session.groupName, session.room, start, end, active = true)
        }
        val scheduled = data.schedule.firstOrNull { item ->
            if (item.dayOfWeek != now.dayOfWeek.value) false else {
                val start = runCatching { LocalTime.parse(item.startTime) }.getOrNull()
                val end = runCatching { LocalTime.parse(item.endTime) }.getOrNull()
                start != null && end != null && !now.toLocalTime().isBefore(start) && now.toLocalTime().isBefore(end)
            }
        } ?: return null
        return lessonFor(data, scheduled, now.toLocalDate(), active = true)
    }

    fun nextLesson(data: AcademicData, now: LocalDateTime): TvLesson? = data.schedule.mapNotNull { scheduled ->
        (0..7).firstNotNullOfOrNull { offset ->
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (scheduled.dayOfWeek != date.dayOfWeek.value) return@firstNotNullOfOrNull null
            val start = runCatching { LocalDateTime.of(date, LocalTime.parse(scheduled.startTime)) }.getOrNull() ?: return@firstNotNullOfOrNull null
            if (!start.isAfter(now)) return@firstNotNullOfOrNull null
            lessonFor(data, scheduled, date, active = false)
        }
    }.minByOrNull { it.start ?: LocalDateTime.MAX }

    fun timeLabel(lesson: TvLesson, now: LocalDateTime): String {
        val target = (if (lesson.active) lesson.end else lesson.start)
            ?: return if (lesson.active) "En curso · horario de fin no disponible" else "Horario pendiente"
        val minutes = Duration.between(now, target).toMinutes()
        if (lesson.active && minutes < 0) return "Horario superado por ${-minutes} min"
        if (!lesson.active) return if (minutes < 60) "Comienza en $minutes min" else "Comienza en ${minutes / 60} h ${minutes % 60} min"
        return if (minutes < 60) "Faltan $minutes min para terminar" else "Faltan ${minutes / 60} h ${minutes % 60} min para terminar"
    }

    private fun lessonFor(data: AcademicData, scheduled: ScheduledClass, date: LocalDate, active: Boolean): TvLesson {
        val start = runCatching { LocalDateTime.of(date, LocalTime.parse(scheduled.startTime)) }.getOrNull()
        val subject = data.subjects.firstOrNull { it.id == scheduled.subjectId }?.name ?: "Clase"
        val group = data.groups.firstOrNull { it.id == scheduled.groupId }?.name ?: "Grupo"
        val teacher = data.teachers.firstOrNull { it.id == scheduled.teacherId }?.name ?: "Docente"
        return TvLesson(subject, teacher, group, scheduled.room.ifBlank { "Aula sin asignar" }, start, lessonEnd(scheduled, date, start), active)
    }

    private fun lessonEnd(scheduled: ScheduledClass, date: LocalDate, start: LocalDateTime?): LocalDateTime? {
        val endTime = runCatching { LocalTime.parse(scheduled.endTime) }.getOrNull() ?: return null
        var end = LocalDateTime.of(date, endTime)
        if (start != null && !end.isAfter(start)) end = end.plusDays(1)
        return end
    }
}
