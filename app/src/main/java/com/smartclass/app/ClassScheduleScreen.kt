package com.smartclass.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PlannerTabs = listOf("Próximas", "Horario", "Materias", "Anteriores")
private val WeekdayLabels = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val PowerableDevices = listOf(
    "light_front" to "Luces frontal",
    "light_center" to "Luces central",
    "light_back" to "Luces posterior",
    "fan_main" to "Ventilador",
    "ac_main" to "Aire acondicionado",
    "projector" to "Proyector",
)

internal data class UpcomingClass(val scheduled: ScheduledClass, val start: LocalDateTime, val end: LocalDateTime)

internal fun nextClassSummary(data: AcademicData, teacherMode: Boolean, teacherId: String): String {
    val next = data.schedule.filter { !teacherMode || it.teacherId == teacherId }.mapNotNull(::nextOccurrence).minByOrNull { it.start } ?: return "No hay clases programadas"
    val subject = data.subjects.firstOrNull { it.id == next.scheduled.subjectId }?.name ?: "Clase"
    val group = data.groups.firstOrNull { it.id == next.scheduled.groupId }?.name ?: "Grupo"
    return "$subject · $group · ${formatOccurrence(next.start)} · ${next.scheduled.room}"
}

@Composable
internal fun ClassScheduleScreen(
    wide: Boolean,
    prefs: SharedPreferences,
    data: AcademicData,
    teacherMode: Boolean,
    teacherId: String,
    bearerToken: String,
    onDataChange: (AcademicData) -> Unit,
    onDevicesPowered: (Set<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gateway = remember { SmartClassRestGateway() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var scheduleToEdit by remember { mutableStateOf<ScheduledClass?>(null) }
    var showScheduleEditor by remember { mutableStateOf(false) }
    var subjectToEdit by remember { mutableStateOf<SchoolSubject?>(null) }
    var showSubjectEditor by remember { mutableStateOf(false) }
    var deleteSubject by remember { mutableStateOf<SchoolSubject?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var liveSnapshot by remember { mutableStateOf<ClassroomSnapshot?>(null) }

    val visibleSchedule = data.schedule.filter { !teacherMode || it.teacherId == teacherId }
    val activeSession = data.sessions.firstOrNull { it.endedAt.isBlank() }
    val upcoming = visibleSchedule.mapNotNull(::nextOccurrence).sortedBy { it.start }
    val selectedTeacher = data.teachers.firstOrNull { it.id == teacherId }

    fun conflicts(candidate: ScheduledClass): List<String> {
        val start = runCatching { LocalTime.parse(candidate.startTime) }.getOrNull() ?: return emptyList()
        val end = runCatching { LocalTime.parse(candidate.endTime) }.getOrNull() ?: return emptyList()
        return data.schedule.filter { it.id != candidate.id && it.dayOfWeek == candidate.dayOfWeek }.filter { other ->
            val otherStart = runCatching { LocalTime.parse(other.startTime) }.getOrNull() ?: return@filter false
            val otherEnd = runCatching { LocalTime.parse(other.endTime) }.getOrNull() ?: return@filter false
            val overlaps = start < otherEnd && otherStart < end
            val sharedResource = candidate.groupId == other.groupId || candidate.teacherId == other.teacherId || candidate.room.equals(other.room, true)
            overlaps && sharedResource
        }.map { other ->
            val reason = when {
                candidate.groupId == other.groupId -> "el mismo grupo ${data.groups.firstOrNull { g -> g.id == other.groupId }?.name.orEmpty()}"
                candidate.teacherId == other.teacherId -> "el mismo docente ${data.teachers.firstOrNull { t -> t.id == other.teacherId }?.name.orEmpty()}"
                else -> "la misma aula ${other.room}"
            }
            "${data.subjects.firstOrNull { it.id == other.subjectId }?.name ?: "Clase"} (${other.startTime}–${other.endTime}) usa $reason"
        }
    }

    fun startClass(scheduled: ScheduledClass) {
        if (activeSession != null) {
            message = "Finaliza primero la sesión activa de ${activeSession.subjectName}."
            return
        }
        val group = data.groups.firstOrNull { it.id == scheduled.groupId }
        val subject = data.subjects.firstOrNull { it.id == scheduled.subjectId }
        val teacher = data.teachers.firstOrNull { it.id == scheduled.teacherId }
        val today = LocalDate.now().toString()
        val attendance = data.attendance.filter { it.groupId == scheduled.groupId && it.date == today }
        val rosterSize = data.students.count { it.groupId == scheduled.groupId }
        val presentCount = attendance.count { it.status == "PRESENT" || it.status == "LATE" }
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val session = ClassSession(AcademicRepository.newId("session"), scheduled.id, subject?.name ?: "Clase", group?.name ?: "Grupo", teacher?.name ?: "Docente", scheduled.room, now, presentCount = presentCount, studentCount = rosterSize)
        onDataChange(data.copy(sessions = listOf(session) + data.sessions))
        liveSnapshot = null
        message = "Sesión iniciada · ${session.subjectName} · ${session.groupName}"
        if (scheduled.powerOnDeviceIds.isEmpty()) return

        if (!prefs.getBoolean("physical_mode", false)) {
            val editor = prefs.edit()
            scheduled.powerOnDeviceIds.forEach { id ->
                editor.putBoolean("classroom_device_$id", true)
                when {
                    id.startsWith("light_") -> editor.putBoolean("lights", true)
                    id == "fan_main" || id == "ac_main" -> editor.putBoolean("fan", true)
                    id == "projector" -> editor.putBoolean("projector", true)
                }
            }
            editor.apply()
            onDevicesPowered(scheduled.powerOnDeviceIds.toSet())
            liveSnapshot = ClassroomSnapshot(devices = scheduled.powerOnDeviceIds.associateWith { true })
            message = "Sesión iniciada · ${scheduled.powerOnDeviceIds.size} equipo(s) preparados en simulación."
            return
        }

        val baseUrl = prefs.getString("gateway_url", "").orEmpty()
        val classroomId = prefs.getString("gateway_classroom", "aula-204").orEmpty()
        if (baseUrl.isBlank()) {
            message = "Sesión iniciada; configura el gateway en Aula para enviar los comandos programados."
            return
        }
        scope.launch {
            busy = true
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    scheduled.powerOnDeviceIds.forEach { gateway.setDevice(baseUrl, classroomId, it, true, bearerToken.ifBlank { null }) }
                    gateway.readSnapshot(baseUrl, classroomId, bearerToken.ifBlank { null })
                }
                liveSnapshot = snapshot
                message = "Sesión iniciada · gateway confirmó el estado del aula."
            } catch (error: Exception) {
                message = "Sesión iniciada, pero algunos equipos no confirmaron el comando: ${error.message?.take(100) ?: "error de conexión"}"
            } finally { busy = false }
        }
    }

    fun finishSession() {
        val current = activeSession ?: return
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        onDataChange(data.copy(sessions = data.sessions.map { if (it.id == current.id) it.copy(endedAt = now) else it }))
        message = "Clase finalizada · ${current.subjectName}"
    }

    ScreenFrame("Clases y horarios", "Materias, programación semanal y sesiones del aula", wide) {
        if (teacherMode) InfoCard { Text("Perfil docente · ${selectedTeacher?.name ?: "Docente"}", color = Navy, fontWeight = FontWeight.Bold); Text("Consulta tus clases y administra las sesiones de tus grupos asignados.", color = Muted, fontSize = 11.sp) }
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, containerColor = Canvas) {
            PlannerTabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
        }
        if (message.isNotBlank()) InfoCard { Text(message, color = if (message.contains("error", true) || message.contains("conflicto", true)) Color(0xFFB5473C) else Teal, fontSize = 12.sp) }

        activeSession?.let { session ->
            ActiveSessionCard(session, data, liveSnapshot, busy, onFinish = ::finishSession)
        }

        when (selectedTab) {
            0 -> {
                SectionTitle("Próximas clases", "Clases programadas para ${if (teacherMode) selectedTeacher?.name ?: "tu perfil" else "todos los grupos"}")
                upcoming.take(20).forEach { item ->
                    UpcomingClassCard(item, data, isActive = activeSession?.scheduleId == item.scheduled.id, canStart = activeSession == null && !busy, onStart = { startClass(item.scheduled) })
                }
                if (upcoming.isEmpty()) EmptyPlanner("No hay clases programadas", "Crea materias y horarios para mostrar las próximas clases.")
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle("Horario semanal", "Las coincidencias de grupo, docente o aula se señalan al guardar") }
                    if (!teacherMode) Button(onClick = { scheduleToEdit = null; showScheduleEditor = true }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Add, null); Text("Programar") }
                }
                val conflictsFound = visibleSchedule.flatMap { entry -> conflicts(entry).map { entry to it } }
                if (conflictsFound.isNotEmpty()) InfoCard { Text("Conflictos detectados", color = Color(0xFFB5473C), fontWeight = FontWeight.Bold); conflictsFound.take(4).forEach { (entry, conflict) -> Text("${weekdayName(entry.dayOfWeek)} · ${entry.startTime}: $conflict", color = Color(0xFFB5473C), fontSize = 11.sp) } }
                visibleSchedule.sortedWith(compareBy<ScheduledClass> { it.dayOfWeek }.thenBy { it.startTime }).forEach { entry ->
                    ScheduledClassCard(entry, data, conflicts(entry), canEdit = !teacherMode, onEdit = { scheduleToEdit = entry; showScheduleEditor = true }, onStart = { startClass(entry) }, canStart = activeSession == null && !busy)
                }
                if (visibleSchedule.isEmpty()) EmptyPlanner("Sin horarios", "Todavía no hay horarios asignados a este perfil.")
            }
            2 -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { SectionTitle("Materias", "Catálogo de la escuela") }; if (!teacherMode) Button(onClick = { subjectToEdit = null; showSubjectEditor = true }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Add, null); Text("Materia") } }
                data.subjects.forEach { subject ->
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(subject.name, color = Navy, fontWeight = FontWeight.Bold); Text("${data.schedule.count { it.subjectId == subject.id }} horarios", color = Muted, fontSize = 11.sp) }
                            if (!teacherMode) {
                                IconButton(onClick = { subjectToEdit = subject; showSubjectEditor = true }) { Icon(Icons.Default.Edit, "Editar materia") }
                                IconButton(onClick = { deleteSubject = subject }) { Icon(Icons.Default.DeleteOutline, "Eliminar materia", tint = Color(0xFFB5473C)) }
                            }
                        }
                    }
                }
                if (data.subjects.isEmpty()) EmptyPlanner("No hay materias", "Registra una materia antes de crear un horario.")
            }
            else -> {
                SectionTitle("Clases anteriores", "Sesiones finalizadas en este dispositivo")
                data.sessions.filter { it.endedAt.isNotBlank() && (!teacherMode || data.schedule.firstOrNull { s -> s.id == it.scheduleId }?.teacherId == teacherId) }
                    .sortedByDescending { it.endedAt }.forEach { session -> PreviousSessionCard(session) }
                if (data.sessions.none { it.endedAt.isNotBlank() }) EmptyPlanner("Aún no hay clases anteriores", "Al finalizar una sesión, aparecerá aquí el resumen del grupo y la clase.")
            }
        }
    }

    if (showScheduleEditor) ScheduleEditorDialog(scheduleToEdit, data, teacherMode, teacherId, onDismiss = { showScheduleEditor = false }, onSave = { scheduled ->
        val issues = conflicts(scheduled)
        if (issues.isNotEmpty()) message = "Conflicto: ${issues.joinToString("; ")}"
        else {
            val exists = data.schedule.any { it.id == scheduled.id }
            onDataChange(data.copy(schedule = if (exists) data.schedule.map { if (it.id == scheduled.id) scheduled else it } else data.schedule + scheduled))
            selectedTab = 0
            message = "Horario guardado sin conflictos."
            showScheduleEditor = false
        }
    })
    if (showSubjectEditor) SubjectEditorDialog(subjectToEdit, data.subjects, onDismiss = { showSubjectEditor = false }, onSave = { subject ->
        val exists = data.subjects.any { it.id == subject.id }
        onDataChange(data.copy(subjects = if (exists) data.subjects.map { if (it.id == subject.id) subject else it } else data.subjects + subject))
        showSubjectEditor = false
    })
    deleteSubject?.let { subject ->
        AlertDialog(onDismissRequest = { deleteSubject = null }, title = { Text("Eliminar materia") }, text = {
            Text(if (data.schedule.any { it.subjectId == subject.id }) "La materia tiene horarios asignados. Elimina o cambia esos horarios antes de borrarla." else "¿Eliminar ${subject.name}?")
        }, confirmButton = {
            TextButton(enabled = data.schedule.none { it.subjectId == subject.id }, onClick = { onDataChange(data.copy(subjects = data.subjects.filterNot { it.id == subject.id })); deleteSubject = null }) { Text("Eliminar") }
        }, dismissButton = { TextButton(onClick = { deleteSubject = null }) { Text("Cerrar") } })
    }
}

private fun nextOccurrence(scheduled: ScheduledClass): UpcomingClass? = runCatching {
    val today = LocalDate.now()
    val delta = (scheduled.dayOfWeek - today.dayOfWeek.value + 7) % 7
    var date = today.plusDays(delta.toLong())
    var start = LocalDateTime.of(date, LocalTime.parse(scheduled.startTime))
    if (start.isBefore(LocalDateTime.now())) { date = date.plusWeeks(1); start = LocalDateTime.of(date, LocalTime.parse(scheduled.startTime)) }
    val end = LocalDateTime.of(date, LocalTime.parse(scheduled.endTime))
    UpcomingClass(scheduled, start, end)
}.getOrNull()

private fun weekdayName(day: Int): String = WeekdayLabels.getOrNull(day - 1) ?: "Día $day"
private fun formatOccurrence(dateTime: LocalDateTime): String = dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale("es", "MX")))

@Composable
private fun UpcomingClassCard(item: UpcomingClass, data: AcademicData, isActive: Boolean, canStart: Boolean, onStart: () -> Unit) {
    val entry = item.scheduled
    val name = data.subjects.firstOrNull { it.id == entry.subjectId }?.name ?: "Materia"
    val group = data.groups.firstOrNull { it.id == entry.groupId }?.name ?: "Grupo"
    val teacher = data.teachers.firstOrNull { it.id == entry.teacherId }?.name ?: "Docente"
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${formatOccurrence(item.start)} · ${entry.room}", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("$group · $teacher", color = Muted, fontSize = 11.sp)
            }
            Button(onClick = onStart, enabled = canStart || isActive, colors = ButtonDefaults.buttonColors(containerColor = if (isActive) Teal else Navy)) { Icon(if (isActive) Icons.Default.PlayCircle else Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(if (isActive) "En curso" else "Iniciar") }
        }
        Text("Al iniciar: lista de ${data.students.count { it.groupId == entry.groupId }} estudiantes y ${entry.powerOnDeviceIds.size} equipos programados.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ScheduledClassCard(entry: ScheduledClass, data: AcademicData, conflicts: List<String>, canEdit: Boolean, onEdit: () -> Unit, onStart: () -> Unit, canStart: Boolean) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(data.subjects.firstOrNull { it.id == entry.subjectId }?.name ?: "Materia", color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${weekdayName(entry.dayOfWeek)} · ${entry.startTime}–${entry.endTime} · ${entry.room}", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text("${data.groups.firstOrNull { it.id == entry.groupId }?.name ?: "Grupo"} · ${data.teachers.firstOrNull { it.id == entry.teacherId }?.name ?: "Docente"}", color = Muted, fontSize = 11.sp)
            }
            if (canEdit) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar horario") }
            Button(onClick = onStart, enabled = canStart, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Iniciar") }
        }
        if (entry.powerOnDeviceIds.isNotEmpty()) Text("Encendido al iniciar: ${entry.powerOnDeviceIds.mapNotNull { id -> PowerableDevices.firstOrNull { it.first == id }?.second }.joinToString()}", color = Muted, fontSize = 10.sp)
        conflicts.forEach { Text("Conflicto: $it", color = Color(0xFFB5473C), fontSize = 10.sp) }
    }
}

@Composable
private fun ActiveSessionCard(session: ClassSession, data: AcademicData, snapshot: ClassroomSnapshot?, busy: Boolean, onFinish: () -> Unit) {
    val students = data.students.filter { it.groupId == data.schedule.firstOrNull { s -> s.id == session.scheduleId }?.groupId }
    val today = LocalDate.now().toString()
    val attendance = data.attendance.filter { it.date == today && it.groupId == data.schedule.firstOrNull { s -> s.id == session.scheduleId }?.groupId }.associateBy { it.studentId }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("SESIÓN ACTIVA", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("${session.subjectName} · ${session.groupName}", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("${session.room} · ${session.teacherName} · Inicio ${session.startedAt.take(16).replace('T', ' ')}", color = Muted, fontSize = 11.sp) }
            OutlinedButton(onClick = onFinish, enabled = !busy) { Icon(Icons.Default.StopCircle, null); Text("Finalizar") }
        }
        Text("Lista del grupo · ${students.count { attendance[it.id]?.status in setOf("PRESENT", "LATE") }}/${students.size} presentes o con retardo", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        students.take(20).forEach { student ->
            val status = when (attendance[student.id]?.status) { "PRESENT" -> "Presente"; "LATE" -> "Retardo"; "ABSENT" -> "Falta"; "JUSTIFIED" -> "Justificada"; else -> "Sin registrar" }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(student.name, Modifier.weight(1f), color = Ink, fontSize = 11.sp); Text(status, color = if (status == "Presente" || status == "Retardo") Teal else Muted, fontSize = 10.sp) }
        }
        Text("Estado del aula", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        val programmed = data.schedule.firstOrNull { it.id == session.scheduleId }?.powerOnDeviceIds.orEmpty()
        if (programmed.isEmpty()) Text("No hay equipos asociados a este horario.", color = Muted, fontSize = 11.sp)
        else programmed.forEach { id ->
            val name = PowerableDevices.firstOrNull { it.first == id }?.second ?: id
            val status = snapshot?.devices?.get(id) ?: if (snapshot == null) null else false
            Text("$name · ${when (status) { true -> "Encendido"; false -> "Apagado"; null -> "Pendiente de lectura" }}", color = if (status == true) Teal else Muted, fontSize = 11.sp)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
    }
}

@Composable
private fun PreviousSessionCard(session: ClassSession) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${session.subjectName} · ${session.groupName}", color = Navy, fontWeight = FontWeight.Bold); Text("${session.room} · ${session.teacherName}", color = Muted, fontSize = 11.sp); Text("${session.startedAt.take(16).replace('T', ' ')} a ${session.endedAt.take(16).replace('T', ' ')}", color = Muted, fontSize = 10.sp) }
            Text("${session.presentCount}/${session.studentCount}\nPresentes", color = Teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SubjectEditorDialog(subject: SchoolSubject?, subjects: List<SchoolSubject>, onDismiss: () -> Unit, onSave: (SchoolSubject) -> Unit) {
    var name by remember(subject) { mutableStateOf(subject?.name.orEmpty()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (subject == null) "Crear materia" else "Editar materia") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it; error = "" }, label = { Text("Nombre de la materia") }, singleLine = true, modifier = Modifier.fillMaxWidth()); if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
    }, confirmButton = { TextButton(onClick = { val clean = name.trim(); if (clean.isBlank()) error = "Escribe un nombre." else if (subjects.any { it.name.equals(clean, true) && it.id != subject?.id }) error = "Ya existe una materia con ese nombre." else onSave(SchoolSubject(subject?.id ?: AcademicRepository.newId("subject"), clean)) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ScheduleEditorDialog(entry: ScheduledClass?, data: AcademicData, teacherMode: Boolean, teacherId: String, onDismiss: () -> Unit, onSave: (ScheduledClass) -> Unit) {
    var subjectId by remember(entry) { mutableStateOf(entry?.subjectId ?: data.subjects.firstOrNull()?.id.orEmpty()) }
    var groupId by remember(entry) { mutableStateOf(entry?.groupId ?: data.groups.firstOrNull()?.id.orEmpty()) }
    var selectedTeacherId by remember(entry) { mutableStateOf(if (teacherMode) teacherId else entry?.teacherId ?: data.teachers.firstOrNull()?.id.orEmpty()) }
    var room by remember(entry) { mutableStateOf(entry?.room.orEmpty()) }
    var day by remember(entry) { mutableIntStateOf(entry?.dayOfWeek ?: DayOfWeek.MONDAY.value) }
    var start by remember(entry) { mutableStateOf(entry?.startTime ?: "08:00") }
    var end by remember(entry) { mutableStateOf(entry?.endTime ?: "09:00") }
    var devices by remember(entry) { mutableStateOf(entry?.powerOnDeviceIds?.toSet() ?: setOf("light_front", "projector")) }
    var menu by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val groupChoices = data.groups
    val teacherChoices = if (teacherMode) data.teachers.filter { it.id == teacherId } else data.teachers

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (entry == null) "Programar clase" else "Editar horario") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlannerSelector("Materia", data.subjects.firstOrNull { it.id == subjectId }?.name ?: "Sin materias", menu == "subject", { menu = if (menu == "subject") "" else "subject" }) { data.subjects.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { subjectId = option.id; menu = "" }) } }
            PlannerSelector("Grupo", data.groups.firstOrNull { it.id == groupId }?.name ?: "Sin grupos", menu == "group", { menu = if (menu == "group") "" else "group" }) { groupChoices.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { groupId = option.id; menu = "" }) } }
            PlannerSelector("Docente", data.teachers.firstOrNull { it.id == selectedTeacherId }?.name ?: "Sin docentes", menu == "teacher", { menu = if (menu == "teacher") "" else "teacher" }) { teacherChoices.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { selectedTeacherId = option.id; menu = "" }) } }
            OutlinedTextField(room, { room = it }, label = { Text("Aula") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PlannerSelector("Día", weekdayName(day), menu == "day", { menu = if (menu == "day") "" else "day" }) { WeekdayLabels.forEachIndexed { index, label -> DropdownMenuItem(text = { Text(label) }, onClick = { day = index + 1; menu = "" }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it }, label = { Text("Inicio HH:mm") }, singleLine = true, modifier = Modifier.weight(1f)); OutlinedTextField(end, { end = it }, label = { Text("Fin HH:mm") }, singleLine = true, modifier = Modifier.weight(1f)) }
            Text("Equipos que se encenderán al iniciar la sesión", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            PowerableDevices.forEach { (id, label) -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = id in devices, onCheckedChange = { devices = if (it) devices + id else devices - id }); Text(label, color = Ink, fontSize = 12.sp) } }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = {
        val startTime = runCatching { LocalTime.parse(start.trim()) }.getOrNull()
        val endTime = runCatching { LocalTime.parse(end.trim()) }.getOrNull()
        when {
            subjectId.isBlank() || groupId.isBlank() || selectedTeacherId.isBlank() -> error = "Registra materia, grupo y docente primero."
            room.isBlank() -> error = "Escribe el aula asignada."
            startTime == null || endTime == null -> error = "Usa la hora en formato HH:mm, por ejemplo 08:30."
            !endTime.isAfter(startTime) -> error = "La hora de fin debe ser posterior a la hora de inicio."
            else -> onSave(ScheduledClass(entry?.id ?: AcademicRepository.newId("schedule"), subjectId, groupId, selectedTeacherId, room.trim(), day, startTime.toString(), endTime.toString(), devices.toList()))
        }
    }) { Text("Guardar horario") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PlannerSelector(title: String, value: String, expanded: Boolean, onExpand: () -> Unit, menuItems: @Composable ColumnScope.() -> Unit) {
    Box {
        OutlinedButton(onClick = onExpand) { Text("$title: $value"); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = onExpand, content = menuItems)
    }
}

@Composable
private fun EmptyPlanner(title: String, message: String) { InfoCard { Icon(Icons.Default.EventBusy, null, tint = Teal); Text(title, color = Navy, fontWeight = FontWeight.Bold); Text(message, color = Muted, fontSize = 12.sp) } }
