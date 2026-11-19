package com.smartclass.app

import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val AcademicTabs = listOf("Lista", "Estudiantes", "Grupos", "Historial", "Estadísticas")
private val AttendanceStatuses = listOf("PRESENT", "LATE", "ABSENT", "JUSTIFIED", "UNRECORDED")

@Composable
internal fun AcademicManagementScreen(wide: Boolean, data: AcademicData, isTeacher: Boolean, selectedTeacherId: String, onDataChange: (AcademicData) -> Unit) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedGroupId by rememberSaveable { mutableStateOf(data.groups.firstOrNull()?.id.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    var studentToEdit by remember { mutableStateOf<StudentRecord?>(null) }
    var showStudentEditor by remember { mutableStateOf(false) }
    var studentToDelete by remember { mutableStateOf<StudentRecord?>(null) }
    var groupToEdit by remember { mutableStateOf<SchoolGroup?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var showTeacherEditor by remember { mutableStateOf(false) }
    var pendingJustification by remember { mutableStateOf<StudentRecord?>(null) }
    var csvToExport by remember { mutableStateOf("") }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csvToExport) }
        }
    }

    val selectedTeacher = data.teachers.firstOrNull { it.id == selectedTeacherId } ?: data.teachers.firstOrNull()
    val teacherGroups = if (isTeacher) data.groups.filter { it.teacherId == selectedTeacher?.id } else data.groups
    val availableGroupIds = teacherGroups.map { it.id }.toSet()
    val visibleStudents = data.students.filter { it.groupId in availableGroupIds }
    val activeGroupId = selectedGroupId.takeIf { it in availableGroupIds } ?: teacherGroups.firstOrNull()?.id.orEmpty()
    val activeGroup = teacherGroups.firstOrNull { it.id == activeGroupId }
    val groupStudents = visibleStudents.filter { it.groupId == activeGroupId }
    val today = LocalDate.now().toString()
    val todayEntries = data.attendance.filter { it.date == today && it.groupId == activeGroupId }
    val attendanceByStudent = todayEntries.associateBy { it.studentId }

    fun saveStatus(student: StudentRecord, status: String, note: String = "") {
        val entry = AttendanceEntry(student.id, student.groupId, today, status, note, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        onDataChange(data.copy(attendance = data.attendance.filterNot { it.studentId == student.id && it.date == today } + entry))
    }

    fun makeCsv(records: List<AttendanceEntry>): String {
        fun cell(value: String) = "\"${value.replace("\"", "\"\"")}\""
        val header = listOf("Nombre", "Matrícula", "Grupo", "Fecha", "Estado", "Observación", "Actualizado")
        val rows = records.map { record ->
            val student = data.students.firstOrNull { it.id == record.studentId }
            val group = data.groups.firstOrNull { it.id == record.groupId }
            listOf(student?.name.orEmpty(), student?.enrollment.orEmpty(), group?.name.orEmpty(), record.date, statusLabel(record.status), record.note, record.updatedAt)
        }
        return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",", transform = ::cell) }
    }

    ScreenFrame("Gestión académica", "Estudiantes, grupos y asistencia · Datos guardados en este dispositivo", wide) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isTeacher) "PERFIL DOCENTE" else "PERFIL ADMINISTRADOR", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (isTeacher) selectedTeacher?.name ?: "Docente" else "Administración escolar", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Icon(if (isTeacher) Icons.Default.School else Icons.Default.AdminPanelSettings, null, tint = Teal, modifier = Modifier.size(28.dp))
            }
            if (isTeacher) Text("Cuenta docente: ${selectedTeacher?.name ?: "sin perfil asignado"}", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(if (isTeacher) "El perfil docente administra estudiantes y asistencia de sus grupos asignados. No puede crear grupos ni cambiar docentes." else "Administra el padrón, los grupos y la asignación de docentes.", color = Muted, fontSize = 11.sp)
            Text("Perfil de demostración local: la protección real entre usuarios requiere autenticación y permisos en un servidor.", color = Muted, fontSize = 10.sp)
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, containerColor = Canvas) {
            AcademicTabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
        }

        when (selectedTab) {
            0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle("Asistencia de hoy", activeGroup?.let { "${it.name} · ${LocalDate.now()}" } ?: "Selecciona un grupo") }
                    OutlinedButton(onClick = {
                        val rows = data.attendance.filter { it.groupId in availableGroupIds }
                        csvToExport = makeCsv(rows)
                        csvLauncher.launch("asistencia_${activeGroup?.name?.replace(" ", "_") ?: "grupos"}_$today.csv")
                    }, enabled = data.attendance.any { it.groupId in availableGroupIds }) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(5.dp)); Text("Exportar CSV") }
                }
                if (teacherGroups.isNotEmpty()) GroupPicker(teacherGroups, activeGroupId, { selectedGroupId = it })
                else EmptyState("Aún no tienes grupos asignados", "Solicita a administración que asigne un grupo a tu perfil docente.")
                if (groupStudents.isNotEmpty()) {
                    val recorded = groupStudents.count { attendanceByStudent[it.id]?.status in setOf("PRESENT", "LATE", "ABSENT", "JUSTIFIED") }
                    val attended = groupStudents.count { attendanceByStudent[it.id]?.status in setOf("PRESENT", "LATE") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("ASISTIERON", "$attended/${groupStudents.size}", "Incluye retardos", Icons.Default.Groups, Modifier.weight(1f))
                        MetricCard("POR REGISTRAR", "${groupStudents.size - recorded}", "Lista de hoy", Icons.Default.PendingActions, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Lista del grupo", "Selecciona el estado de cada estudiante")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            val freshEntries = groupStudents.map { AttendanceEntry(it.id, it.groupId, today, "PRESENT", updatedAt = updatedAt) }
                            onDataChange(data.copy(attendance = data.attendance.filterNot { it.groupId == activeGroupId && it.date == today } + freshEntries))
                        }) { Text("Todos presentes") }
                    }
                    groupStudents.forEach { student ->
                        AttendanceStudentRow(student, attendanceByStudent[student.id], onStatus = { status ->
                            if (status == "JUSTIFIED") pendingJustification = student else saveStatus(student, status)
                        })
                    }
                }
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle("Estudiantes", "${visibleStudents.size} en tus grupos visibles") }
                    Button(onClick = { studentToEdit = null; showStudentEditor = true }, enabled = teacherGroups.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(5.dp)); Text("Registrar") }
                }
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar por nombre o matrícula") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                if (!isTeacher && teacherGroups.isNotEmpty()) GroupPicker(teacherGroups, activeGroupId, { selectedGroupId = it }, includeAll = true)
                val filterGroup = if (!isTeacher && selectedGroupId == "*") null else activeGroupId
                val filtered = visibleStudents.filter { student ->
                    (filterGroup == null || student.groupId == filterGroup) &&
                        (student.name.contains(search.trim(), ignoreCase = true) || student.enrollment.contains(search.trim(), ignoreCase = true))
                }
                Text("${filtered.size} resultado(s)", color = Muted, fontSize = 12.sp)
                filtered.forEach { student -> StudentCard(student, data.groups.firstOrNull { it.id == student.groupId }?.name.orEmpty(), onEdit = { studentToEdit = student; showStudentEditor = true }, onDelete = { studentToDelete = student }) }
                if (filtered.isEmpty()) EmptyState("No hay coincidencias", "Prueba con otro nombre o matrícula.")
            }
            2 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle(if (isTeacher) "Mis grupos" else "Grupos y docentes", "${teacherGroups.size} grupo(s) disponibles") }
                    if (!isTeacher) Button(onClick = { groupToEdit = null; showGroupEditor = true }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Add, null); Text("Grupo") }
                }
                if (!isTeacher) OutlinedButton(onClick = { showTeacherEditor = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Registrar docente") }
                teacherGroups.forEach { group ->
                    val teacherName = data.teachers.firstOrNull { it.id == group.teacherId }?.name ?: "Sin asignar"
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(group.name, color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Docente: $teacherName", color = Muted, fontSize = 12.sp); Text("${data.students.count { it.groupId == group.id }} estudiantes", color = Muted, fontSize = 11.sp) }
                            if (!isTeacher) IconButton(onClick = { groupToEdit = group; showGroupEditor = true }) { Icon(Icons.Default.Edit, "Editar grupo") }
                        }
                    }
                }
                if (teacherGroups.isEmpty()) EmptyState("Sin grupos asignados", "Un administrador debe asignar grupos a tu perfil.")
            }
            3 -> {
                SectionTitle("Historial de asistencia", "Registros de todos tus grupos · ${data.attendance.count { it.groupId in availableGroupIds }} eventos")
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar estudiante") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                val records = data.attendance.filter { it.groupId in availableGroupIds && it.status != "UNRECORDED" }
                    .filter { entry -> data.students.firstOrNull { it.id == entry.studentId }?.let { it.name.contains(search.trim(), true) || it.enrollment.contains(search.trim(), true) } ?: false }
                    .sortedWith(compareByDescending<AttendanceEntry> { it.date }.thenByDescending { it.updatedAt })
                records.take(200).forEach { HistoryRow(it, data) }
                if (records.isEmpty()) EmptyState("Aún no hay registros", "Las asistencias, retardos y faltas aparecerán aquí.")
            }
            else -> {
                SectionTitle("Estadísticas por estudiante", activeGroup?.let { "Grupo ${it.name} · historial completo" } ?: "Resumen de tus grupos")
                if (teacherGroups.isNotEmpty()) GroupPicker(teacherGroups, activeGroupId, { selectedGroupId = it }, includeAll = !isTeacher)
                val statsStudents = if (!isTeacher && selectedGroupId == "*") visibleStudents else visibleStudents.filter { it.groupId == activeGroupId }
                statsStudents.forEach { student ->
                    val entries = data.attendance.filter { it.studentId == student.id && it.status != "UNRECORDED" }
                    StudentStatsCard(student, data.groups.firstOrNull { it.id == student.groupId }?.name.orEmpty(), entries)
                }
            }
        }
    }

    if (showStudentEditor) {
        StudentEditorDialog(studentToEdit, teacherGroups, data, onDismiss = { showStudentEditor = false }, onSave = { student ->
            val exists = data.students.any { it.id == student.id }
            onDataChange(data.copy(students = if (exists) data.students.map { if (it.id == student.id) student else it } else data.students + student))
            showStudentEditor = false
        })
    }
    if (studentToDelete != null) {
        val candidate = studentToDelete!!
        AlertDialog(onDismissRequest = { studentToDelete = null }, title = { Text("Eliminar estudiante") }, text = { Text("¿Eliminar a ${candidate.name} y sus registros de asistencia? Esta acción no se puede deshacer.") }, confirmButton = {
            TextButton(onClick = { onDataChange(data.copy(students = data.students.filterNot { it.id == candidate.id }, attendance = data.attendance.filterNot { it.studentId == candidate.id })); studentToDelete = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        }, dismissButton = { TextButton(onClick = { studentToDelete = null }) { Text("Cancelar") } })
    }
    if (showGroupEditor) {
        GroupEditorDialog(groupToEdit, data.teachers, onDismiss = { showGroupEditor = false }, onSave = { group ->
            val exists = data.groups.any { it.id == group.id }
            onDataChange(data.copy(groups = if (exists) data.groups.map { if (it.id == group.id) group else it } else data.groups + group))
            selectedGroupId = group.id
            showGroupEditor = false
        })
    }
    if (showTeacherEditor) TeacherEditorDialog(data.teachers, onDismiss = { showTeacherEditor = false }, onSave = { teacher ->
        onDataChange(data.copy(teachers = data.teachers + teacher)); showTeacherEditor = false
    })
    if (pendingJustification != null) {
        JustificationDialog(pendingJustification!!, onDismiss = { pendingJustification = null }, onSave = { note -> saveStatus(pendingJustification!!, "JUSTIFIED", note); pendingJustification = null })
    }
}

@Composable
private fun TeacherPicker(teachers: List<TeacherProfile>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text(teachers.firstOrNull { it.id == selectedId }?.name ?: "Seleccionar docente"); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { teachers.forEach { teacher -> DropdownMenuItem(text = { Text(teacher.name) }, onClick = { onSelect(teacher.id); expanded = false }) } }
    }
}

@Composable
private fun GroupPicker(groups: List<SchoolGroup>, selectedId: String, onSelect: (String) -> Unit, includeAll: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (selectedId == "*") "Todos los grupos" else groups.firstOrNull { it.id == selectedId }?.name ?: "Selecciona un grupo"
    Box {
        OutlinedButton(onClick = { expanded = true }) { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(6.dp)); Text(selectedLabel); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includeAll) DropdownMenuItem(text = { Text("Todos los grupos") }, onClick = { onSelect("*"); expanded = false })
            groups.forEach { group -> DropdownMenuItem(text = { Text(group.name) }, onClick = { onSelect(group.id); expanded = false }) }
        }
    }
}

@Composable
private fun AttendanceStudentRow(student: StudentRecord, entry: AttendanceEntry?, onStatus: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val status = entry?.status ?: "UNRECORDED"
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Color(0xFFE1F5F1), CircleShape), contentAlignment = Alignment.Center) { Text(student.name.split(" ").take(2).joinToString("") { it.first().toString() }, color = Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(student.name, color = Ink, fontWeight = FontWeight.Medium); Text("${student.enrollment}${entry?.note?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", color = Muted, fontSize = 10.sp) }
        Box {
            TextButton(onClick = { expanded = true }) { Text(statusLabel(status), color = statusColor(status)); Icon(Icons.Default.ArrowDropDown, null, tint = statusColor(status)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AttendanceStatuses.forEach { option -> DropdownMenuItem(text = { Text(statusLabel(option)) }, onClick = { expanded = false; onStatus(option) }) }
            }
        }
    }
}

@Composable
private fun StudentCard(student: StudentRecord, groupName: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(0xFFE8F5FF), CircleShape), contentAlignment = Alignment.Center) { Text(student.name.split(" ").take(2).joinToString("") { it.first().toString() }, color = Navy, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(student.name, color = Ink, fontWeight = FontWeight.Bold); Text("Matrícula: ${student.enrollment} · Grupo: $groupName", color = Muted, fontSize = 11.sp) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar estudiante") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Eliminar estudiante", tint = Color(0xFFB5473C)) }
        }
    }
}

@Composable
private fun HistoryRow(entry: AttendanceEntry, data: AcademicData) {
    val student = data.students.firstOrNull { it.id == entry.studentId }
    val group = data.groups.firstOrNull { it.id == entry.groupId }
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(13.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(student?.name ?: "Estudiante eliminado", color = Ink, fontWeight = FontWeight.SemiBold); Text("${student?.enrollment.orEmpty()} · ${group?.name.orEmpty()} · ${entry.date}", color = Muted, fontSize = 10.sp); entry.note.takeIf { it.isNotBlank() }?.let { Text("Nota: $it", color = Muted, fontSize = 10.sp) } }
        Text(statusLabel(entry.status), color = statusColor(entry.status), fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun StudentStatsCard(student: StudentRecord, groupName: String, entries: List<AttendanceEntry>) {
    val attended = entries.count { it.status == "PRESENT" || it.status == "LATE" }
    val late = entries.count { it.status == "LATE" }
    val absent = entries.count { it.status == "ABSENT" }
    val justified = entries.count { it.status == "JUSTIFIED" }
    val percent = if (entries.isEmpty()) 0 else attended * 100 / entries.size
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(student.name, color = Navy, fontWeight = FontWeight.Bold); Text("${student.enrollment} · $groupName · ${entries.size} clases registradas", color = Muted, fontSize = 10.sp) }; Text("$percent%", color = Teal, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Teal)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallCount("Asistencias", attended, Modifier.weight(1f)); SmallCount("Retardos", late, Modifier.weight(1f)); SmallCount("Faltas", absent, Modifier.weight(1f)); SmallCount("Justificadas", justified, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallCount(label: String, count: Int, modifier: Modifier) { Column(modifier) { Text(count.toString(), color = Navy, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 9.sp) } }

@Composable
private fun StudentEditorDialog(student: StudentRecord?, groups: List<SchoolGroup>, data: AcademicData, onDismiss: () -> Unit, onSave: (StudentRecord) -> Unit) {
    var name by remember(student) { mutableStateOf(student?.name.orEmpty()) }
    var enrollment by remember(student) { mutableStateOf(student?.enrollment.orEmpty()) }
    var groupId by remember(student, groups) { mutableStateOf(student?.groupId?.takeIf { id -> groups.any { it.id == id } } ?: groups.firstOrNull()?.id.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var groupMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (student == null) "Registrar estudiante" else "Editar estudiante") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Nombre completo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(enrollment, { enrollment = it; error = null }, label = { Text("Matrícula") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick = { groupMenu = true }) { Text("Grupo: ${groups.firstOrNull { it.id == groupId }?.name ?: "Sin grupo"}"); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) { groups.forEach { group -> DropdownMenuItem(text = { Text(group.name) }, onClick = { groupId = group.id; groupMenu = false }) } }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        val cleanName = name.trim(); val cleanEnrollment = enrollment.trim()
        if (cleanName.isBlank() || cleanEnrollment.isBlank() || groupId.isBlank()) error = "Completa nombre, matrícula y grupo."
        else if (data.students.any { it.enrollment.equals(cleanEnrollment, true) && it.id != student?.id }) error = "Esa matrícula ya está registrada."
        else onSave(StudentRecord(student?.id ?: AcademicRepository.newId("student"), cleanName, cleanEnrollment, groupId))
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun GroupEditorDialog(group: SchoolGroup?, teachers: List<TeacherProfile>, onDismiss: () -> Unit, onSave: (SchoolGroup) -> Unit) {
    var name by remember(group) { mutableStateOf(group?.name.orEmpty()) }
    var teacherId by remember(group, teachers) { mutableStateOf(group?.teacherId?.takeIf { id -> teachers.any { it.id == id } } ?: teachers.firstOrNull()?.id.orEmpty()) }
    var teacherMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (group == null) "Crear grupo" else "Editar grupo") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Nombre del grupo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick = { teacherMenu = true }) { Text("Docente: ${teachers.firstOrNull { it.id == teacherId }?.name ?: "Sin asignar"}"); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(expanded = teacherMenu, onDismissRequest = { teacherMenu = false }) { teachers.forEach { teacher -> DropdownMenuItem(text = { Text(teacher.name) }, onClick = { teacherId = teacher.id; teacherMenu = false }) } }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        val cleanName = name.trim()
        if (cleanName.isBlank()) error = "Escribe el nombre del grupo." else onSave(SchoolGroup(group?.id ?: AcademicRepository.newId("group"), cleanName, teacherId))
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun TeacherEditorDialog(teachers: List<TeacherProfile>, onDismiss: () -> Unit, onSave: (TeacherProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Registrar docente") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre del docente") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (name.isNotBlank() && teachers.none { it.name.equals(name.trim(), true) }) onSave(TeacherProfile(AcademicRepository.newId("teacher"), name.trim())) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun JustificationDialog(student: StudentRecord, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember(student) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Falta justificada") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Agrega el motivo de la falta de ${student.name}.", fontSize = 12.sp); OutlinedTextField(note, { note = it }, label = { Text("Motivo") }, minLines = 2) }
    }, confirmButton = { TextButton(onClick = { if (note.isNotBlank()) onSave(note.trim()) }) { Text("Guardar justificación") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun EmptyState(title: String, message: String) { InfoCard { Icon(Icons.Default.Info, null, tint = Teal); Text(title, color = Navy, fontWeight = FontWeight.Bold); Text(message, color = Muted, fontSize = 12.sp) } }

private fun statusLabel(status: String): String = when (status) { "PRESENT" -> "Presente"; "LATE" -> "Retardo"; "ABSENT" -> "Falta"; "JUSTIFIED" -> "Justificada"; else -> "Sin registrar" }
private fun statusColor(status: String): Color = when (status) { "PRESENT" -> Teal; "LATE" -> Color(0xFFB7791F); "ABSENT" -> Color(0xFFB5473C); "JUSTIFIED" -> Color(0xFF526A9C); else -> Muted }
