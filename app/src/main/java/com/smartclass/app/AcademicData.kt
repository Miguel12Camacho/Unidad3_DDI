package com.smartclass.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class StudentRecord(
    val id: String,
    val name: String,
    val enrollment: String,
    val groupId: String,
)

internal data class SchoolGroup(
    val id: String,
    val name: String,
    val teacherId: String,
)

internal data class TeacherProfile(val id: String, val name: String)

internal data class AttendanceEntry(
    val studentId: String,
    val groupId: String,
    val date: String,
    val status: String,
    val note: String = "",
    val updatedAt: String = "",
)

internal data class SchoolSubject(val id: String, val name: String)

internal data class ScheduledClass(
    val id: String,
    val subjectId: String,
    val groupId: String,
    val teacherId: String,
    val room: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val powerOnDeviceIds: List<String>,
)

internal data class ClassSession(
    val id: String,
    val scheduleId: String,
    val subjectName: String,
    val groupName: String,
    val teacherName: String,
    val room: String,
    val startedAt: String,
    val endedAt: String = "",
    val presentCount: Int = 0,
    val studentCount: Int = 0,
)

internal data class AcademicData(
    val students: List<StudentRecord>,
    val groups: List<SchoolGroup>,
    val teachers: List<TeacherProfile>,
    val attendance: List<AttendanceEntry>,
    val subjects: List<SchoolSubject>,
    val schedule: List<ScheduledClass>,
    val sessions: List<ClassSession>,
)

internal object AcademicRepository {
    private const val DATA_KEY = "academic_data_v1"
    private val validStatuses = setOf("PRESENT", "LATE", "ABSENT", "JUSTIFIED", "UNRECORDED")

    fun load(prefs: SharedPreferences): AcademicData {
        val raw = prefs.getString(DATA_KEY, null) ?: return initialData().also { save(prefs, it) }
        return runCatching {
            val root = JSONObject(raw)
            AcademicData(
                students = root.getJSONArray("students").toRecords { o -> StudentRecord(o.getString("id"), o.getString("name"), o.getString("enrollment"), o.getString("groupId")) },
                groups = root.getJSONArray("groups").toRecords { o -> SchoolGroup(o.getString("id"), o.getString("name"), o.optString("teacherId")) },
                teachers = root.getJSONArray("teachers").toRecords { o -> TeacherProfile(o.getString("id"), o.getString("name")) },
                attendance = root.getJSONArray("attendance").toRecords { o ->
                    val status = o.optString("status", "UNRECORDED").takeIf { it in validStatuses } ?: "UNRECORDED"
                    AttendanceEntry(o.getString("studentId"), o.optString("groupId"), o.getString("date"), status, o.optString("note"), o.optString("updatedAt"))
                },
                subjects = root.optJSONArray("subjects")?.toRecords { o -> SchoolSubject(o.getString("id"), o.getString("name")) }.orEmpty(),
                schedule = root.optJSONArray("schedule")?.toRecords { o ->
                    val devices = o.optJSONArray("powerOnDeviceIds")?.toRecords { value -> value.getString("id") }.orEmpty()
                    ScheduledClass(o.getString("id"), o.getString("subjectId"), o.getString("groupId"), o.getString("teacherId"), o.optString("room"), o.optInt("dayOfWeek", 1), o.optString("startTime", "08:00"), o.optString("endTime", "09:00"), devices)
                }.orEmpty(),
                sessions = root.optJSONArray("sessions")?.toRecords { o -> ClassSession(o.getString("id"), o.optString("scheduleId"), o.optString("subjectName"), o.optString("groupName"), o.optString("teacherName"), o.optString("room"), o.getString("startedAt"), o.optString("endedAt"), o.optInt("presentCount"), o.optInt("studentCount")) }.orEmpty(),
            ).let { loaded ->
                if (root.optInt("schemaVersion", 0) < 1) {
                    val seed = initialData()
                    loaded.copy(subjects = seed.subjects, schedule = seed.schedule.map { it.copy(groupId = loaded.groups.firstOrNull()?.id ?: it.groupId, teacherId = loaded.teachers.firstOrNull()?.id ?: it.teacherId) }).validated().also { save(prefs, it) }
                } else loaded.validated()
            }
        }.getOrElse { initialData().also { save(prefs, it) } }
    }

    fun save(prefs: SharedPreferences, data: AcademicData) {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("students", JSONArray().apply { data.students.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("enrollment", it.enrollment).put("groupId", it.groupId)) } })
        root.put("groups", JSONArray().apply { data.groups.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("teacherId", it.teacherId)) } })
        root.put("teachers", JSONArray().apply { data.teachers.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        root.put("attendance", JSONArray().apply { data.attendance.forEach { put(JSONObject().put("studentId", it.studentId).put("groupId", it.groupId).put("date", it.date).put("status", it.status).put("note", it.note).put("updatedAt", it.updatedAt)) } })
        root.put("subjects", JSONArray().apply { data.subjects.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        root.put("schedule", JSONArray().apply { data.schedule.forEach { scheduled ->
            val devices = JSONArray().apply { scheduled.powerOnDeviceIds.forEach { put(JSONObject().put("id", it)) } }
            put(JSONObject().put("id", scheduled.id).put("subjectId", scheduled.subjectId).put("groupId", scheduled.groupId).put("teacherId", scheduled.teacherId).put("room", scheduled.room).put("dayOfWeek", scheduled.dayOfWeek).put("startTime", scheduled.startTime).put("endTime", scheduled.endTime).put("powerOnDeviceIds", devices))
        } })
        root.put("sessions", JSONArray().apply { data.sessions.forEach { put(JSONObject().put("id", it.id).put("scheduleId", it.scheduleId).put("subjectName", it.subjectName).put("groupName", it.groupName).put("teacherName", it.teacherName).put("room", it.room).put("startedAt", it.startedAt).put("endedAt", it.endedAt).put("presentCount", it.presentCount).put("studentCount", it.studentCount)) } })
        prefs.edit().putString(DATA_KEY, root.toString()).apply()
    }

    fun initialData(): AcademicData {
        val teacher = TeacherProfile("teacher-default", "Laura Mendoza")
        val group = SchoolGroup("group-5a", "5°A", teacher.id)
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val today = LocalDate.now().toString()
        val roster = Students.mapIndexed { index, name -> StudentRecord("student-${index + 1}", name, "SC-${(1001 + index)}", group.id) }
        val attendance = roster.mapIndexed { index, student ->
            AttendanceEntry(student.id, group.id, today, if (index < 3) "PRESENT" else "UNRECORDED", updatedAt = now)
        }
        val subjects = listOf(SchoolSubject("subject-math", "Matemáticas"), SchoolSubject("subject-mobile", "Desarrollo móvil"), SchoolSubject("subject-database", "Bases de datos"))
        val schedule = listOf(
            ScheduledClass("schedule-math-mon", "subject-math", group.id, teacher.id, "Aula 204", 1, "08:00", "09:00", listOf("light_front", "light_center", "projector")),
            ScheduledClass("schedule-mobile-tue", "subject-mobile", group.id, teacher.id, "Laboratorio 2", 2, "09:00", "10:30", listOf("light_front", "projector")),
            ScheduledClass("schedule-math-wed", "subject-math", group.id, teacher.id, "Aula 204", 3, "08:00", "09:00", listOf("light_front", "light_center", "projector")),
            ScheduledClass("schedule-database-thu", "subject-database", group.id, teacher.id, "Aula 204", 4, "10:00", "11:00", listOf("light_front", "projector")),
            ScheduledClass("schedule-math-fri", "subject-math", group.id, teacher.id, "Aula 204", 5, "08:00", "09:00", listOf("light_front", "light_center", "projector")),
        )
        return AcademicData(roster, listOf(group), listOf(teacher), attendance, subjects, schedule, emptyList())
    }

    fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun AcademicData.validated(): AcademicData {
        val groupIds = groups.map { it.id }.toSet()
        val studentIds = students.map { it.id }.toSet()
        val teacherIds = teachers.map { it.id }.toSet()
        val safeGroups = groups.map { if (it.teacherId in teacherIds || it.teacherId.isBlank()) it else it.copy(teacherId = "") }
        val safeStudents = students.map { if (it.groupId in groupIds) it else it.copy(groupId = safeGroups.firstOrNull()?.id.orEmpty()) }
        val subjectIds = subjects.map { it.id }.toSet()
        val safeSchedule = schedule.filter { it.groupId in groupIds && it.subjectId in subjectIds && it.dayOfWeek in 1..7 && it.startTime.matches(Regex("\\d{2}:\\d{2}")) && it.endTime.matches(Regex("\\d{2}:\\d{2}")) }
        return copy(students = safeStudents, groups = safeGroups, attendance = attendance.filter { it.studentId in studentIds && (it.groupId.isBlank() || it.groupId in groupIds) }, schedule = safeSchedule, sessions = sessions.filter { it.scheduleId.isBlank() || schedule.any { scheduled -> scheduled.id == it.scheduleId } })
    }

    private inline fun <T> JSONArray.toRecords(transform: (JSONObject) -> T): List<T> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) }
    }
}
