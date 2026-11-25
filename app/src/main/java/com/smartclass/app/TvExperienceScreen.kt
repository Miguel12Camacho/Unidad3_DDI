package com.smartclass.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TvSection(val label: String, val icon: ImageVector) {
    CLASS("Clase / ambiente", Icons.Default.School),
    ATTENDANCE("Asistencia", Icons.Default.Groups),
    NOTICES("Avisos", Icons.Default.Campaign),
    ROOM("Estado y controles", Icons.Default.Devices),
}

private data class TvAnnouncement(val title: String, val body: String, val priority: String = "MEDIA")

@Composable
internal fun TvClassroomExperience(database: SmartClassDatabase, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(AcademicRepository.load(prefs)) }
    var notice by remember { mutableStateOf(prefs.getString("notice", "") ?: "") }
    var notifications by remember { mutableStateOf(NotificationRepository.load(context)) }
    var snapshot by remember { mutableStateOf(tvSnapshotFromPreferences(prefs)) }
    var section by rememberSaveable { mutableStateOf(TvSection.CLASS.name) }
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var telemetryStep by remember { mutableIntStateOf(0) }
    var noticeIndex by rememberSaveable { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Datos de esta TV · importa el respaldo del teléfono para actualizarlos.") }
    var importError by remember { mutableStateOf("") }
    var gatewayConnected by remember { mutableStateOf(false) }
    val sectionValue = TvSection.valueOf(section)
    val firstNavFocus = remember { FocusRequester() }
    val gateway = remember { SmartClassRestGateway() }
    val demoGatewayUrl = "http://10.0.2.2:8765"

    LaunchedEffect(Unit) {
        runCatching { firstNavFocus.requestFocus() }
        while (true) {
            delay(1_000)
            currentTime = LocalDateTime.now()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            telemetryStep += 1
            snapshot = ClassroomDemoTelemetry.sample(snapshot, telemetryStep)
        }
    }

    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importError = ""
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("No se pudo leer el archivo seleccionado.")
                    RoomBackup.import(database.dao(), raw)
                    RoomDataMirror.restorePreferences(context, database.dao())
                }
            }
            result.onSuccess {
                data = AcademicRepository.load(prefs)
                notice = prefs.getString("notice", "") ?: ""
                notifications = NotificationRepository.load(context)
                snapshot = tvSnapshotFromPreferences(prefs).copy(
                    temperatureC = snapshot.temperatureC,
                    humidityPercent = snapshot.humidityPercent,
                    airQualityPpm = snapshot.airQualityPpm,
                    motionDetected = snapshot.motionDetected,
                    doorOpen = snapshot.doorOpen,
                    windowOpen = snapshot.windowOpen,
                )
                statusMessage = "Respaldo del teléfono importado correctamente."
                section = TvSection.CLASS.name
            }.onFailure { error ->
                importError = error.message ?: "El archivo no se pudo importar."
                statusMessage = "Revisa que sea un respaldo SmartClass en formato JSON."
            }
        }
    }

    val automaticAlerts = remember(snapshot, data.schedule, currentTime) { NotificationRules.forSnapshot(snapshot, data.schedule) }
    val announcements = buildList {
        notice.takeIf(String::isNotBlank)?.let { add(TvAnnouncement("Aviso principal", it)) }
        automaticAlerts.sortedByDescending { alertPriority(it.priority) }.forEach { add(TvAnnouncement(it.title, it.message, it.priority)) }
        notifications.filter { !it.resolved && it.message.isNotBlank() }
            .sortedByDescending { alertPriority(it.priority) }
            .forEach { add(TvAnnouncement(it.title, it.message, it.priority)) }
        if (isEmpty()) add(TvAnnouncement("Sin avisos", "No hay comunicados activos para el grupo."))
    }.distinctBy { it.title to it.body }
    val rotatingAnnouncement = announcements.getOrNull(noticeIndex % announcements.size.coerceAtLeast(1))

    LaunchedEffect(announcements.size) {
        if (announcements.isNotEmpty()) while (true) {
            delay(10_000)
            noticeIndex = (noticeIndex + 1) % announcements.size
        }
    }

    fun applyLocalDevices(states: Map<String, Boolean>) {
        val editor = prefs.edit()
        states.forEach { (id, enabled) -> editor.putBoolean("classroom_device_$id", enabled) }
        editor.putBoolean("lights", states.filterKeys { it.startsWith("light_") }.values.any { it })
            .putBoolean("fan", states["fan_main"] == true || states["ac_main"] == true)
            .putBoolean("projector", states["projector"] == true)
            .apply()
        snapshot = snapshot.copy(devices = snapshot.devices + states)
    }

    fun connectDemoGateway() {
        scope.launch {
            statusMessage = "Conectando al servidor de demostración…"
            try {
                val result = withContext(Dispatchers.IO) { gateway.readSnapshot(demoGatewayUrl, "aula-204", null) }
                snapshot = result
                gatewayConnected = true
                statusMessage = "Gateway de demostración conectado. El estado se confirma desde el servidor local."
            } catch (error: Exception) {
                gatewayConnected = false
                statusMessage = "Gateway sin conexión: ${error.message?.take(100) ?: "revisa que el servidor esté iniciado"}. La pantalla conserva la simulación local."
            }
        }
    }

    fun setTvDevice(ids: List<String>, enabled: Boolean) {
        scope.launch {
            try {
                if (gatewayConnected) {
                    val result = withContext(Dispatchers.IO) {
                        ids.forEach { gateway.setDevice(demoGatewayUrl, "aula-204", it, enabled, null) }
                        gateway.readSnapshot(demoGatewayUrl, "aula-204", null)
                    }
                    snapshot = result
                    statusMessage = "El servidor confirmó ${if (enabled) "el encendido" else "el apagado"}."
                } else {
                    applyLocalDevices(ids.associateWith { enabled })
                    statusMessage = "Simulación local de la TV · conecta el gateway para enviar comandos físicos."
                }
            } catch (error: Exception) {
                gatewayConnected = false
                statusMessage = "No se confirmó el comando: ${error.message?.take(100) ?: "error de conexión"}. Revisa el aula manualmente."
            }
        }
    }

    fun setScene(startClass: Boolean) {
        val states = mapOf(
            "light_front" to startClass, "light_center" to startClass, "light_back" to startClass,
            "fan_main" to startClass, "ac_main" to startClass, "projector" to startClass,
        )
        scope.launch {
            try {
                if (gatewayConnected) {
                    val result = withContext(Dispatchers.IO) {
                        states.forEach { (id, enabled) -> gateway.setDevice(demoGatewayUrl, "aula-204", id, enabled, null) }
                        gateway.readSnapshot(demoGatewayUrl, "aula-204", null)
                    }
                    snapshot = result
                    statusMessage = if (startClass) "Escena Iniciar clase confirmada por el gateway." else "Escena Terminar clase confirmada por el gateway."
                } else {
                    applyLocalDevices(states)
                    statusMessage = if (startClass) "Clase lista · luces, proyector y clima encendidos en simulación." else "Clase terminada · luces, proyector y clima apagados en simulación."
                }
            } catch (error: Exception) {
                gatewayConnected = false
                statusMessage = "No se confirmó toda la escena: ${error.message?.take(100) ?: "error de conexión"}. Revisa los equipos."
            }
        }
    }

    BackHandler(enabled = sectionValue != TvSection.CLASS || importError.isNotBlank()) {
        if (importError.isNotBlank()) importError = "" else section = TvSection.CLASS.name
    }

    TvMaterialTheme {
        Row(
            Modifier.fillMaxSize().background(TvBackground).padding(horizontal = 48.dp, vertical = 27.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            TvNavigation(sectionValue, firstNavFocus, statusMessage, importError, { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }, onSelect = { section = it.name })
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                TvTopBar(currentTime, sectionValue.label, gatewayConnected)
                if (importError.isNotBlank()) TvMessageBanner("No se importaron los datos", importError, error = true)
                when (sectionValue) {
                    TvSection.CLASS -> TvClassPanel(data, currentTime, notice, rotatingAnnouncement, Modifier.weight(1f), onImport = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) })
                    TvSection.ATTENDANCE -> TvAttendancePanel(data, currentTime, Modifier.weight(1f))
                    TvSection.NOTICES -> TvNoticesPanel(rotatingAnnouncement, announcements.size, noticeIndex, automaticAlerts, Modifier.weight(1f))
                    TvSection.ROOM -> TvRoomPanel(snapshot, automaticAlerts, gatewayConnected, Modifier.weight(1f), onConnectGateway = ::connectDemoGateway, onDevice = ::setTvDevice, onScene = ::setScene)
                }
                TvText(statusMessage, color = Color(0xFFBDCADB), fontSize = 18.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun TvNavigation(section: TvSection, firstFocus: FocusRequester, status: String, error: String, onImport: () -> Unit, onSelect: (TvSection) -> Unit) {
    Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvText("SMARTCLASS", color = Color(0xFF8DE4D5), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        TvText("Panel del aula", color = Color.White, fontSize = 21.sp)
        Spacer(Modifier.height(10.dp))
        TvSection.entries.forEachIndexed { index, item ->
            TvButton(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)) {
                TvIcon(item.icon, null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                TvText(item.label, fontSize = 20.sp, fontWeight = if (item == section) FontWeight.Bold else FontWeight.Medium, maxLines = 2)
            }
        }
        Spacer(Modifier.weight(1f))
        TvButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp)) {
            TvIcon(Icons.Default.FileOpen, null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            TvText("Importar respaldo", fontSize = 19.sp, maxLines = 2)
        }
        if (error.isNotBlank()) TvText("Error al importar", color = Color(0xFFFFB4AB), fontSize = 18.sp)
    }
}

@Composable
private fun TvTopBar(now: LocalDateTime, section: String, gatewayConnected: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TvText(section, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            TvText(now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX")).withLocale(Locale("es", "MX"))).replaceFirstChar { it.titlecase(Locale("es", "MX")) }, color = Color(0xFFBDCADB), fontSize = 18.sp)
        }
        TvText(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color(0xFF8DE4D5), fontSize = 42.sp, fontWeight = FontWeight.Bold)
        if (gatewayConnected) {
            Spacer(Modifier.width(24.dp))
            TvText("Gateway conectado", color = Color(0xFF8DE4D5), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvClassPanel(data: AcademicData, now: LocalDateTime, notice: String, rotating: TvAnnouncement?, modifier: Modifier, onImport: () -> Unit) {
    val current = TvDashboardData.currentLesson(data, now)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (current == null) {
            val next = TvDashboardData.nextLesson(data, now)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).background(TvCard, RoundedCornerShape(26.dp)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    TvText("MODO AMBIENTE", color = Color(0xFF8DE4D5), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    TvText(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Bold)
                    val dateLabel = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX")).withLocale(Locale("es", "MX"))).replaceFirstChar { it.titlecase(Locale("es", "MX")) }
                    TvText(dateLabel, color = Color(0xFFBDCADB), fontSize = 23.sp)
                    TvText("No hay una clase en curso", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (next != null) {
                        TvInfoCard("PRÓXIMA CLASE", next.subject, "${next.group} · ${next.teacher}", "${next.start?.format(DateTimeFormatter.ofPattern("EEEE · HH:mm", Locale("es", "MX")))} · ${next.room}")
                    } else {
                        TvInfoCard("PRÓXIMA CLASE", "Sin horario próximo", "Importa el respaldo del teléfono", "Los horarios del grupo aparecerán aquí.")
                    }
                    TvNoticeCard(rotating ?: TvAnnouncement("Aviso", notice.ifBlank { "No hay avisos publicados." }), 32.sp)
                    TvButton(onClick = onImport, modifier = Modifier.heightIn(min = 72.dp)) {
                        TvIcon(Icons.Default.FileOpen, null, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); TvText("Importar datos del teléfono", fontSize = 20.sp)
                    }
                }
            }
        } else {
            TvInfoCard("CLASE EN CURSO", current.subject, "${current.group} · ${current.teacher}", current.room)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvMetricCard("HORARIO", "${current.start?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"}–${current.end?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"}", Modifier.weight(1f))
                TvMetricCard("TIEMPO", TvDashboardData.timeLabel(current, now), Modifier.weight(1f))
                TvMetricCard("HORA ACTUAL", now.format(DateTimeFormatter.ofPattern("HH:mm")), Modifier.weight(1f))
            }
            TvNoticeCard(rotating ?: TvAnnouncement("Aviso", notice.ifBlank { "No hay avisos publicados." }), 25.sp)
        }
    }
}

@Composable
private fun TvAttendancePanel(data: AcademicData, now: LocalDateTime, modifier: Modifier) {
    val lesson = TvDashboardData.currentLesson(data, now) ?: TvDashboardData.nextLesson(data, now)
    val group = data.groups.firstOrNull { it.name == lesson?.group } ?: data.groups.firstOrNull()
    val summary = group?.let { TvDashboardData.attendance(data, it.id, now.toLocalDate()) }
        ?: TvAttendanceSummary(0, 0, 0, 0, 0)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        TvText("${group?.name ?: "Grupo sin seleccionar"} · Registro de hoy", color = Color(0xFFBDCADB), fontSize = 22.sp)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvMetricCard("PRESENTES", "${summary.present}", "de ${summary.total} estudiantes", Modifier.weight(1f), Color(0xFF13564F))
            TvMetricCard("RETARDOS", "${summary.late}", "registrados hoy", Modifier.weight(1f), Color(0xFF69490B))
            TvMetricCard("FALTAS", "${summary.absent}", "sin justificar", Modifier.weight(1f), Color(0xFF702F37))
            TvMetricCard("JUSTIFICADAS", "${summary.justified}", "con motivo", Modifier.weight(1f), TvCard)
        }
        TvText("Pantalla de consulta · las altas y correcciones se hacen desde el teléfono.", color = Color(0xFFBDCADB), fontSize = 18.sp)
    }
}

@Composable
private fun TvNoticesPanel(current: TvAnnouncement?, count: Int, index: Int, alerts: List<SmartNotification>, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TvText("Los avisos avanzan automáticamente cada 10 segundos", color = Color(0xFFBDCADB), fontSize = 20.sp)
        TvNoticeCard(current ?: TvAnnouncement("Sin avisos", "No hay comunicados activos."), 34.sp, Modifier.fillMaxWidth().weight(1f))
        TvText("${(index % (count.coerceAtLeast(1))) + 1} de ${count.coerceAtLeast(1)} avisos · ${alerts.size} alertas activas", color = Color(0xFFBDCADB), fontSize = 18.sp)
    }
}

@Composable
private fun TvRoomPanel(
    snapshot: ClassroomSnapshot,
    alerts: List<SmartNotification>,
    gatewayConnected: Boolean,
    modifier: Modifier,
    onConnectGateway: () -> Unit,
    onDevice: (List<String>, Boolean) -> Unit,
    onScene: (Boolean) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        alerts.firstOrNull()?.let { TvMessageBanner(it.title, it.message, error = it.priority == "CRÍTICA" || it.priority == "ALTA") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvMetricCard("TEMPERATURA", snapshot.temperatureC?.let { "%.1f °C".format(Locale.getDefault(), it) } ?: "Sin lectura", "Sensor de aula", Modifier.weight(1f))
            TvMetricCard("HUMEDAD", snapshot.humidityPercent?.let { "%.0f%%".format(Locale.getDefault(), it) } ?: "Sin lectura", "Humedad relativa", Modifier.weight(1f))
            TvMetricCard("PUERTA", when (snapshot.doorOpen) { true -> "ABIERTA"; false -> "CERRADA"; else -> "SIN LECTURA" }, "${snapshot.motionDetected?.let { if (it) "Movimiento" else "Sin movimiento" } ?: "Movimiento desconocido"}", Modifier.weight(1f), if (snapshot.doorOpen == true) Color(0xFF702F37) else TvCard)
        }
        TvText("Controles del aula", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val lightsOn = snapshot.devices.filterKeys { it.startsWith("light_") }.values.let { it.isNotEmpty() && it.all { state -> state } }
            TvDeviceTile("Luces", "3 zonas · ${if (lightsOn) "Encendidas" else "Apagadas"}", Icons.Default.Lightbulb, Modifier.weight(1f)) {
                val ids = listOf("light_front", "light_center", "light_back")
                onDevice(ids, !ids.all { snapshot.devices[it] == true })
            }
            TvDeviceTile("Aire y ventilación", if (snapshot.devices["ac_main"] == true || snapshot.devices["fan_main"] == true) "Encendidos" else "Apagados", Icons.Default.AcUnit, Modifier.weight(1f)) {
                val active = snapshot.devices["ac_main"] == true || snapshot.devices["fan_main"] == true
                onDevice(listOf("ac_main", "fan_main"), !active)
            }
            TvDeviceTile("Proyector", if (snapshot.devices["projector"] == true) "Encendido" else "Apagado", Icons.Default.Videocam, Modifier.weight(1f)) { onDevice(listOf("projector"), snapshot.devices["projector"] != true) }
            TvDeviceTile("Alarma", if (snapshot.devices["security_alarm"] == true) "Activa" else "Desactivada", Icons.Default.NotificationsActive, Modifier.weight(1f)) { onDevice(listOf("security_alarm"), snapshot.devices["security_alarm"] != true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvDeviceTile("Iniciar clase", "Luces, proyector y clima", Icons.Default.PlayCircle, Modifier.weight(1f), emphasized = true) { onScene(true) }
            TvDeviceTile("Terminar clase", "Apagar equipos de clase", Icons.Default.StopCircle, Modifier.weight(1f), emphasized = true) { onScene(false) }
            if (BuildConfig.DEBUG) TvDeviceTile("${if (gatewayConnected) "Gateway conectado" else "Conectar demo"}", "Servidor local de prueba", Icons.Default.Router, Modifier.weight(1f)) { onConnectGateway() }
        }
    }
}

@Composable
private fun TvInfoCard(label: String, title: String, detail: String, extra: String) {
    Column(Modifier.fillMaxWidth().background(TvCard, RoundedCornerShape(24.dp)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvText(label, color = Color(0xFF8DE4D5), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        TvText(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        TvText(detail, color = Color.White, fontSize = 23.sp, maxLines = 2)
        TvText(extra, color = Color(0xFFBDCADB), fontSize = 20.sp, maxLines = 2)
    }
}

@Composable
private fun TvMetricCard(title: String, value: String, modifier: Modifier = Modifier, color: Color = TvCard) = TvMetricCard(title, value, "", modifier, color)

@Composable
private fun TvMetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier, color: Color = TvCard) {
    Column(modifier.fillMaxHeight().background(color, RoundedCornerShape(22.dp)).padding(23.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.Start) {
        TvText(title, color = Color(0xFF8DE4D5), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        TvText(value, color = Color.White, fontSize = if (value.length > 16) 25.sp else 42.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (detail.isNotBlank()) TvText(detail, color = Color(0xFFBDCADB), fontSize = 19.sp, maxLines = 2)
    }
}

@Composable
private fun TvNoticeCard(announcement: TvAnnouncement, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Column(modifier.background(if (announcement.priority == "CRÍTICA" || announcement.priority == "ALTA") Color(0xFF702F37) else TvCard, RoundedCornerShape(24.dp)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvText(announcement.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        TvText(announcement.body, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 5)
    }
}

@Composable
private fun TvMessageBanner(title: String, message: String, error: Boolean) {
    Column(Modifier.fillMaxWidth().background(if (error) Color(0xFF702F37) else Color(0xFF13564F), RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TvText(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        TvText(message, color = Color.White, fontSize = 19.sp, maxLines = 2)
    }
}

@Composable
private fun TvDeviceTile(title: String, status: String, icon: ImageVector, modifier: Modifier, emphasized: Boolean = false, onClick: () -> Unit) {
    TvButton(onClick = onClick, modifier = modifier.heightIn(min = if (emphasized) 94.dp else 116.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvIcon(icon, null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                TvText(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            }
            TvText(status, fontSize = 18.sp, maxLines = 2)
        }
    }
}

private fun tvSnapshotFromPreferences(prefs: android.content.SharedPreferences): ClassroomSnapshot {
    val defaults = mapOf("light_front" to true, "light_center" to true, "light_back" to false, "fan_main" to false, "ac_main" to false, "projector" to true, "security_alarm" to false)
    val devices = defaults.mapValues { (id, default) -> prefs.getBoolean("classroom_device_$id", default) }
    val sensors = listOf("sensor_temperature", "sensor_humidity", "sensor_air", "sensor_motion", "sensor_door", "sensor_window").associateWith { true }
    return ClassroomSnapshot(devices, sensors, temperatureC = 23.5f, humidityPercent = 47f, airQualityPpm = 620, motionDetected = false, doorOpen = false, windowOpen = false)
}

private fun alertPriority(priority: String): Int = when (priority) { "CRÍTICA" -> 4; "ALTA" -> 3; "MEDIA" -> 2; else -> 1 }

private val TvBackground = Color(0xFF08162D)
private val TvCard = Color(0xFF18304F)
