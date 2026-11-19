package com.smartclass.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PriorityOptions = listOf("Todas", "CRÍTICA", "ALTA", "MEDIA", "BAJA")

@Composable
internal fun CommunicationsScreen(
    wide: Boolean,
    notice: String,
    onEditNotice: () -> Unit,
    notifications: List<SmartNotification>,
    onRefresh: () -> Unit,
    onNotificationChange: (String, Boolean) -> Unit,
    onCreateNotification: (SmartNotification) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    ScreenFrame("Avisos y notificaciones", "Comunicados del grupo y alertas del aula", wide) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, containerColor = Canvas) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Avisos") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Centro (${notifications.count { !it.read }})") })
        }
        if (selectedTab == 0) NoticesContent(notice, onEditNotice)
        else NotificationCenterContent(notifications, onRefresh, onNotificationChange, onCreateNotification)
    }
}

@Composable
private fun NoticesContent(notice: String, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Comunicados", "Información para el grupo 5°A"); Spacer(Modifier.weight(1f)); Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar") } }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Campaign, null, tint = Teal); Spacer(Modifier.width(8.dp)); Text("AVISO IMPORTANTE", color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        Text(notice.ifBlank { "Escribe un aviso para compartirlo con el grupo." }, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Publicado por coordinación · Hoy", color = Muted, fontSize = 11.sp)
    }
    ReminderPanel("Preparación de clase", "Consulta tu horario y revisa los materiales antes de cada clase.")
    ReminderPanel("Proyecto", "Recuerda entregar el proyecto en la fecha indicada por tu docente.")
}

@Composable
private fun NotificationCenterContent(items: List<SmartNotification>, onRefresh: () -> Unit, onChange: (String, Boolean) -> Unit, onCreate: (SmartNotification) -> Unit) {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf("Todas") }
    var priority by rememberSaveable { mutableStateOf("Todas") }
    var date by rememberSaveable { mutableStateOf("Todas") }
    var showCreate by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val unread = items.count { !it.read && !it.resolved }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SectionTitle("Centro de notificaciones", "$unread sin leer · ${items.size} registradas") }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar") }
        Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.AddAlert, null); Spacer(Modifier.width(4.dp)); Text("Aviso") }
    }
    if (NotificationPublisher.hasPermission(context)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.NotificationsActive, null, tint = Teal); Spacer(Modifier.width(6.dp)); Text("Notificaciones del dispositivo activas", color = Teal, fontSize = 11.sp) }
    } else {
        OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(6.dp)); Text("Activar notificaciones del dispositivo")
        }
    }

    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("FILTRAR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            NotificationFilter("Categoría", category, listOf("Todas") + NoticeCategory.all) { category = it }
            NotificationFilter("Prioridad", priority, PriorityOptions) { priority = it }
            val dates = listOf("Todas") + items.map { notificationDate(it) }.distinct().sortedDescending()
            NotificationFilter("Fecha", date, dates) { date = it }
            TextButton(onClick = { category = "Todas"; priority = "Todas"; date = "Todas" }) { Text("Limpiar") }
        }
    }

    val filtered = items.filter { item ->
        (category == "Todas" || item.category == category) &&
            (priority == "Todas" || item.priority == priority) &&
            (date == "Todas" || notificationDate(item) == date)
    }.sortedWith(compareBy<SmartNotification> { it.resolved }.thenBy { it.read }.thenByDescending { if (it.scheduledAt.isNotBlank() && !it.triggered) it.scheduledAt else it.createdAt })

    if (filtered.isEmpty()) EmptyNotificationState("No hay notificaciones con esos filtros", "Las alertas de sensores, clases y recordatorios aparecerán aquí.")
    filtered.forEach { notification -> NotificationCard(notification, onClick = { onChange(notification.id, true) }, onDismiss = { onChange(notification.id, false) }) }
    Text("Los avisos de temperatura, desconexión, puerta y alarma requieren lecturas del gateway o acciones de simulación.", color = Muted, fontSize = 10.sp)

    if (showCreate) NotificationComposer(onDismiss = { showCreate = false }, onSave = { onCreate(it); showCreate = false })
}

@Composable
private fun NotificationFilter(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected", fontSize = 11.sp); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
private fun NotificationCard(item: SmartNotification, onClick: () -> Unit, onDismiss: () -> Unit) {
    val color = priorityColor(item.priority)
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (item.read || item.resolved) Muted else color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(item.category.uppercase(Locale.getDefault()), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.weight(1f))
            Text(item.priority, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            IconButton(onClick = onDismiss, modifier = Modifier.size(35.dp)) { Icon(if (item.resolved) Icons.Default.CheckCircle else Icons.Default.Close, if (item.resolved) "Resuelta" else "Resolver", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
        Text(item.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(item.message, color = Ink, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(notificationDate(item), color = Muted, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text(notificationStatus(item), color = if (item.resolved) Muted else Teal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (!item.read) TextButton(onClick = onClick) { Text("Marcar leída") }
        }
    }
}

@Composable
private fun NotificationComposer(onDismiss: () -> Unit, onSave: (SmartNotification) -> Unit) {
    var category by remember { mutableStateOf(NoticeCategory.DELIVERIES) }
    var priority by remember { mutableStateOf("MEDIA") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("09:00") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Programar recordatorio") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationFilter("Categoría", category, listOf(NoticeCategory.DELIVERIES, NoticeCategory.MAINTENANCE), { category = it })
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(message, { message = it }, label = { Text("Detalle") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Fecha AAAA-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(time, { time = it }, label = { Text("Hora HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            NotificationFilter("Prioridad", priority, PriorityOptions.drop(1), { priority = it })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = {
        val dateTime = runCatching { LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim())) }.getOrNull()
        when {
            title.isBlank() || message.isBlank() -> error = "Escribe el título y el detalle del aviso."
            dateTime == null -> error = "Usa una fecha y hora válidas."
            dateTime.isBefore(LocalDateTime.now()) -> error = "La fecha y hora deben estar en el futuro."
            else -> onSave(NotificationRepository.fresh(category, title.trim(), message.trim(), priority, "user:${java.util.UUID.randomUUID()}", dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
        }
    }) { Text("Programar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ReminderPanel(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFE8F5FF), RoundedCornerShape(16.dp)).padding(17.dp)) { Text(title, color = Navy, fontWeight = FontWeight.Bold); Text(detail, color = Navy2, fontSize = 13.sp) }
}

@Composable
private fun EmptyNotificationState(title: String, message: String) { InfoCard { Icon(Icons.Default.NotificationsNone, null, tint = Teal); Text(title, color = Navy, fontWeight = FontWeight.Bold); Text(message, color = Muted, fontSize = 12.sp) } }

private fun notificationDate(item: SmartNotification): String = (if (item.triggered && item.scheduledAt.isNotBlank()) item.scheduledAt else item.scheduledAt.ifBlank { item.createdAt }).take(10)
private fun notificationStatus(item: SmartNotification): String = when { item.resolved -> "Resuelta"; item.scheduledAt.isNotBlank() && !item.triggered -> "Programada · ${item.scheduledAt.take(16).replace('T', ' ')}"; !item.read -> "Nueva"; else -> "Leída" }
private fun priorityColor(priority: String): Color = when (priority) { "CRÍTICA" -> Color(0xFFB5473C); "ALTA" -> Color(0xFFD47727); "MEDIA" -> Color(0xFF526A9C); else -> Muted }
