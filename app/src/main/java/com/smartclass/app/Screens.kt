package com.smartclass.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged

@Composable
internal fun TvHomeScreen(attendanceCount: Int, studentCount: Int, activeDevices: Int, totalDevices: Int, connectedSensors: Int, sessionActive: Boolean, nextClass: String, notice: String, onNavigate: (String) -> Unit) {
    ScreenFrame("SmartClass en tu aula", "Resumen en tiempo real · Usa las flechas y OK del control remoto", true) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TvMetricCard("ASISTENCIA", "$attendanceCount / $studentCount", "Presentes hoy", Modifier.weight(1f))
            TvMetricCard("CLASE", if (sessionActive) "En curso" else "Sin iniciar", nextClass, Modifier.weight(1f))
            TvMetricCard("AULA", "$activeDevices / $totalDevices equipos", "$connectedSensors sensores conectados", Modifier.weight(1f))
        }
        SectionTitle("Acceso rápido", "Selecciona una sección con el control remoto")
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvActionCard("Aula", "Estado y controles del salón", Modifier.weight(1f)) { onNavigate("Aula") }
            TvActionCard("Clases", "Horario y sesiones", Modifier.weight(1f)) { onNavigate("Clases") }
            TvActionCard("Asistencia", "Grupos y seguimiento", Modifier.weight(1f)) { onNavigate("Gestión") }
            TvActionCard("Avisos", "Comunicados recientes", Modifier.weight(1f)) { onNavigate("Avisos") }
        }
        InfoCard {
            Text("AVISO", color = Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(notice.ifBlank { "No hay avisos publicados." }, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TvMetricCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Teal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Navy, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = Muted, fontSize = 17.sp, maxLines = 2)
    }
}

@Composable
private fun TvActionCard(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .then(if (focused) Modifier.border(4.dp, Teal, RoundedCornerShape(20.dp)) else Modifier)
        .background(if (focused) Navy2 else Navy, RoundedCornerShape(20.dp)).padding(22.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color(0xFFCBD5E1), fontSize = 16.sp)
    }
}

@Composable
internal fun HomeScreen(wide: Boolean, attendanceCount: Int, studentCount: Int, activeDevices: Int, totalDevices: Int, connectedSensors: Int, sessionActive: Boolean, teacherMode: Boolean, subtitle: String, nextClass: String, onNavigate: (String) -> Unit) {
    ScreenFrame("Panel del aula", subtitle, wide) {
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricCard("ASISTENCIA", "$attendanceCount/$studentCount", "Estudiantes presentes", Icons.Default.Groups, Modifier.weight(1f))
            MetricCard("EQUIPOS", "$activeDevices/$totalDevices", "$connectedSensors sensores conectados", Icons.Default.Devices, Modifier.weight(1f))
            MetricCard("SESIÓN", if (sessionActive) "En curso" else "Finalizada", "Clase de hoy", Icons.Default.EventAvailable, Modifier.weight(1f))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("ASISTENCIA", "$attendanceCount/$studentCount", "Presentes", Icons.Default.Groups, Modifier.weight(1f))
                MetricCard("EQUIPOS", "$activeDevices/$totalDevices", "Sensores conectados: $connectedSensors", Icons.Default.Devices, Modifier.weight(1f))
            }
            MetricCard("SESIÓN DE CLASE", if (sessionActive) "En curso" else "Finalizada", subtitle, Icons.Default.EventAvailable)
        }
        SectionTitle("Acceso rápido", "Las tareas frecuentes del aula, en un solo lugar")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!teacherMode) QuickAction("Control del aula", "Luces, clima y equipo", Icons.Default.Tune, Modifier.weight(1f)) { onNavigate("Aula") }
            QuickAction(if (teacherMode) "Mis grupos" else "Asistencia", if (teacherMode) "Lista y seguimiento" else "Lista y grupos", Icons.Default.FactCheck, Modifier.weight(1f)) { onNavigate("Gestión") }
            QuickAction("Clases", "Horario y sesiones", Icons.Default.CalendarMonth, Modifier.weight(1f)) { onNavigate("Clases") }
            if (wide) QuickAction("Avisos", "Comunicados", Icons.Default.Campaign, Modifier.weight(1f)) { onNavigate("Avisos") }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(Modifier.weight(1f)) {
                SectionTitle("Próxima clase", nextClass)
                Button(onClick = { onNavigate("Clases") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Ver horario e iniciar sesión") }
            }
            InfoCard(Modifier.weight(1f)) {
                SectionTitle("Estado del aula", "Resumen del equipamiento")
                StatusPill("Equipos activos", activeDevices > 0); StatusPill("Sensores", connectedSensors > 0)
                Button(onClick = { onNavigate("Clases") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(if (sessionActive) Icons.Default.StopCircle else Icons.Default.PlayCircle, null); Spacer(Modifier.width(8.dp)); Text(if (sessionActive) "Administrar sesión" else "Iniciar clase") }
                TextButton(onClick = { onNavigate("Cuenta") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(5.dp)); Text("Cuenta, seguridad y respaldos") }
            }
        } else {
            InfoCard {
                SectionTitle("Próxima clase", nextClass)
                Button(onClick = { onNavigate("Clases") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Ver horario e iniciar sesión") }
            }
            InfoCard {
                SectionTitle("Estado del aula")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { StatusPill("Equipos activos", activeDevices > 0); StatusPill("Sensores", connectedSensors > 0) }
                Button(onClick = { onNavigate("Clases") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text(if (sessionActive) "Administrar sesión" else "Iniciar clase") }
                TextButton(onClick = { onNavigate("Cuenta") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(5.dp)); Text("Cuenta y respaldos") }
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).background(Navy2, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Icon(icon, null, tint = Color(0xFF8DE4D5), modifier = Modifier.size(23.dp)); Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(subtitle, color = Color(0xFFCBD5E1), fontSize = 10.sp)
    }
}

@Composable
private fun ScheduleRow(time: String, subject: String, state: String, current: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
        Column(Modifier.weight(1f)) { Text(subject, color = Ink, fontWeight = FontWeight.SemiBold); Text("Aula 204 · Grupo 5°A", color = Muted, fontSize = 11.sp) }
        Text(state, color = if (current) Teal else Muted, fontSize = 11.sp)
    }
}

@Composable
internal fun AttendanceScreen(wide: Boolean, attendance: Set<String>, onChange: (Set<String>) -> Unit) {
    ScreenFrame("Asistencia", "Grupo 5°A · Registro de la clase de hoy", wide) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("PRESENTES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("${attendance.size} de ${Students.size} estudiantes", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Text("${attendance.size * 100 / Students.size}%", color = Teal, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { attendance.size.toFloat() / Students.size }, modifier = Modifier.fillMaxWidth(), color = Teal)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Lista del grupo", "Toca un estudiante para actualizar su estado")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onChange(if (attendance.size == Students.size) emptySet() else Students.toSet()) }) { Text(if (attendance.size == Students.size) "Limpiar" else "Marcar todos") }
        }
        Students.forEach { student ->
            val present = student in attendance
            Row(Modifier.fillMaxWidth().clickable { onChange(if (present) attendance - student else attendance + student) }.background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(if (present) Color(0xFFE1F5F1) else Canvas, CircleShape), contentAlignment = Alignment.Center) { Text(student.split(" ").take(2).joinToString("") { it.first().toString() }, color = Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(student, color = Ink, fontWeight = FontWeight.Medium); Text(if (present) "Presente" else "Sin registrar", color = if (present) Teal else Muted, fontSize = 11.sp) }
                Checkbox(checked = present, onCheckedChange = { onChange(if (it) attendance + student else attendance - student) }, colors = CheckboxDefaults.colors(checkedColor = Teal))
            }
        }
    }
}

@Composable
internal fun NoticesScreen(wide: Boolean, notice: String, onEdit: () -> Unit) {
    ScreenFrame("Avisos del grupo", "Comunicados y recordatorios para estudiantes", wide) {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Comunicados", "Información para el grupo 5°A"); Spacer(Modifier.weight(1f)); Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar") } }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Campaign, null, tint = Teal); Spacer(Modifier.width(8.dp)); Text("AVISO IMPORTANTE", color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            Text(notice.ifBlank { "Escribe un aviso para compartirlo con el grupo." }, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Publicado por coordinación · Hoy", color = Muted, fontSize = 11.sp)
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReminderCard("Preparación de clase", "Consulta tu horario y revisa los materiales antes de cada clase.", Modifier.weight(1f))
            ReminderCard("Proyecto", "Recuerda entregar el proyecto el viernes durante la clase.", Modifier.weight(1f))
        } else {
            ReminderCard("Preparación de clase", "Consulta tu horario y revisa los materiales antes de cada clase.")
            ReminderCard("Proyecto", "Recuerda entregar el proyecto el viernes durante la clase.")
        }
    }
}

@Composable
private fun ReminderCard(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Color(0xFFE8F5FF), RoundedCornerShape(16.dp)).padding(17.dp)) {
        Text(title, color = Navy, fontWeight = FontWeight.Bold); Text(message, color = Navy2, fontSize = 13.sp)
    }
}
