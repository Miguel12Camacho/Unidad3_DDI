package com.smartclass.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleRulesTest {
    @Test
    fun adjacentClassesDoNotOverlap() {
        assertFalse(ScheduleRules.overlaps("08:00", "09:00", "09:00", "10:00"))
    }

    @Test
    fun invalidOrReversedRangesAreRejected() {
        assertFalse(ScheduleRules.overlaps("09:00", "08:00", "08:30", "10:00"))
        assertFalse(ScheduleRules.overlaps("invalid", "10:00", "08:30", "10:00"))
    }

    @Test
    fun onlyOverlappingSharedResourcesAreConflicts() {
        val first = ScheduledClass("a", "math", "5a", "teacher-1", "Room A", 2, "08:00", "09:00", emptyList())
        val overlapDifferentResources = ScheduledClass("b", "art", "6b", "teacher-2", "Room B", 2, "08:30", "09:30", emptyList())
        val overlapSameRoom = overlapDifferentResources.copy(id = "c", room = "room a")
        val adjacentSameGroup = overlapDifferentResources.copy(id = "d", groupId = "5a", startTime = "09:00", endTime = "10:00")

        assertEquals(listOf(overlapSameRoom), ScheduleRules.conflicts(listOf(first, overlapDifferentResources, overlapSameRoom, adjacentSameGroup), first))
    }

    @Test
    fun debugGatewayAllowsOnlyEmulatorHttpHost() {
        assertTrue(GatewayUrlPolicy.isAllowed("http://10.0.2.2:8765", allowDebugHttp = true))
        assertFalse(GatewayUrlPolicy.isAllowed("http://192.168.1.20:8765", allowDebugHttp = true))
        assertFalse(GatewayUrlPolicy.isAllowed("http://10.0.2.2:8765", allowDebugHttp = false))
        assertTrue(GatewayUrlPolicy.isAllowed("https://gateway.school.example", allowDebugHttp = false))
    }

    @Test
    fun demonstrationTelemetryVariesAndReachesTheAlertThreshold() {
        val first = ClassroomDemoTelemetry.sample(ClassroomSnapshot(), 2)
        val alert = ClassroomDemoTelemetry.sample(first, 9)
        assertTrue(first.humidityPercent != alert.humidityPercent)
        assertTrue(first.motionDetected != alert.motionDetected)
        assertTrue(alert.temperatureC!! >= 30f)
    }

    @Test
    fun tvAttendanceUsesOnlyTheSelectedGroupsDate() {
        val data = tvTestData().copy(attendance = listOf(
            AttendanceEntry("s1", "5a", "2026-11-24", "PRESENT"),
            AttendanceEntry("s2", "5a", "2026-11-24", "LATE"),
            AttendanceEntry("s3", "5a", "2026-11-24", "ABSENT"),
            AttendanceEntry("s4", "6b", "2026-11-24", "PRESENT"),
        ))
        val result = TvDashboardData.attendance(data, "5a", LocalDate.parse("2026-11-24"))
        assertEquals(TvAttendanceSummary(present = 1, late = 1, absent = 1, justified = 0, total = 3), result)
    }

    @Test
    fun tvClassPanelFindsCurrentLessonAndRemainingTime() {
        val now = LocalDateTime.parse("2026-11-24T09:45:00")
        val lesson = TvDashboardData.currentLesson(tvTestData(), now)
        assertEquals("Matemáticas", lesson?.subject)
        assertTrue(lesson?.active == true)
        assertEquals("Faltan 45 min para terminar", TvDashboardData.timeLabel(lesson!!, now))
    }

    @Test
    fun tvAmbientPanelCanFindTheNextScheduledClass() {
        val now = LocalDateTime.parse("2026-11-24T11:00:00")
        val next = TvDashboardData.nextLesson(tvTestData(), now)
        assertEquals("Matemáticas", next?.subject)
        assertEquals(LocalDateTime.parse("2026-12-01T09:00:00"), next?.start)
        assertTrue(next?.active == false)
    }

    private fun tvTestData() = AcademicData(
        students = listOf(
            StudentRecord("s1", "Ana", "01", "5a"),
            StudentRecord("s2", "Beto", "02", "5a"),
            StudentRecord("s3", "Caro", "03", "5a"),
        ),
        groups = listOf(SchoolGroup("5a", "5°A", "t1")),
        teachers = listOf(TeacherProfile("t1", "Laura")),
        attendance = emptyList(),
        subjects = listOf(SchoolSubject("math", "Matemáticas")),
        schedule = listOf(ScheduledClass("today-class", "math", "5a", "t1", "Aula 204", 2, "09:00", "10:30", emptyList())),
        sessions = emptyList(),
    )
}
