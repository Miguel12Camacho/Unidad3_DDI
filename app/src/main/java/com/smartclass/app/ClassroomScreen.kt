package com.smartclass.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CLASSROOM_HISTORY_KEY = "classroom_history"
private const val DEFAULT_CLASSROOM_ID = "aula-204"

private data class RoomDevice(val id: String, val name: String, val detail: String, val icon: ImageVector)
private val Devices = listOf(
    RoomDevice("light_front", "Luces · zona frontal", "Iluminación del pizarrón", Icons.Default.Lightbulb),
    RoomDevice("light_center", "Luces · zona central", "Iluminación de mesas centrales", Icons.Default.Lightbulb),
    RoomDevice("light_back", "Luces · zona posterior", "Iluminación de la parte trasera", Icons.Default.Lightbulb),
    RoomDevice("fan_main", "Ventilador", "Ventilación del aula", Icons.Default.Air),
    RoomDevice("ac_main", "Aire acondicionado", "Climatización", Icons.Default.AcUnit),
    RoomDevice("projector", "Proyector", "Equipo audiovisual", Icons.Default.Videocam),
    RoomDevice("security_alarm", "Alarma de seguridad", "Alerta del aula", Icons.Default.NotificationsActive),
)

private val DemoSnapshot = ClassroomSnapshot(
    devices = mapOf("light_front" to true, "light_center" to true, "light_back" to false, "fan_main" to false, "ac_main" to false, "projector" to true, "security_alarm" to false),
    connected = Devices.associate { it.id to true },
    temperatureC = 23.5f,
    humidityPercent = 47f,
    airQualityPpm = 620,
    motionDetected = true,
    doorOpen = false,
    windowOpen = false,
)

@Composable
internal fun ClassroomScreen(wide: Boolean, prefs: SharedPreferences, bearerToken: String, schedule: List<ScheduledClass>, onBearerTokenChange: (String) -> Unit, onNotification: (SmartNotification) -> Unit, onResolveInactive: (Set<String>) -> Unit, onConnectionRestored: () -> Unit, onSnapshotChanged: (ClassroomSnapshot) -> Unit = {}, onDashboardDeviceChange: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val gateway = remember { SmartClassRestGateway() }
    var physicalMode by remember { mutableStateOf(prefs.getBoolean("physical_mode", false)) }
    var baseUrl by remember { mutableStateOf(prefs.getString("gateway_url", "") ?: "") }
    var classroomId by remember { mutableStateOf(prefs.getString("gateway_classroom", DEFAULT_CLASSROOM_ID) ?: DEFAULT_CLASSROOM_ID) }
    var snapshot by remember {
        val savedDevices = Devices.associate { device -> device.id to prefs.getBoolean("classroom_device_${device.id}", DemoSnapshot.devices[device.id] ?: false) }
        mutableStateOf(if (physicalMode) ClassroomSnapshot() else DemoSnapshot.copy(devices = savedDevices))
    }
    var telemetryStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(snapshot) { onSnapshotChanged(snapshot) }
    LaunchedEffect(physicalMode) {
        if (!physicalMode) while (true) {
            delay(4_000)
            telemetryStep += 1
            snapshot = ClassroomDemoTelemetry.sample(snapshot, telemetryStep)
        }
    }
    var history by remember { mutableStateOf(prefs.getString("$CLASSROOM_HISTORY_KEY:$classroomId", null)?.lines()?.filter(String::isNotBlank).orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf(if (physicalMode) "Modo físico seleccionado · configura el gateway para leer equipos." else "Simulación activa · estados demostrativos") }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot, physicalMode, schedule) {
        val alerts = NotificationRules.forSnapshot(snapshot, schedule)
        onResolveInactive(alerts.map { it.dedupeKey }.toSet())
        alerts.forEach(onNotification)
    }

    fun addHistory(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val updated = listOf("$time · $message") + history
        history = updated.take(50)
        prefs.edit().putString("$CLASSROOM_HISTORY_KEY:$classroomId", history.joinToString("\n")).apply()
    }

    fun readGateway(tokenOverride: String? = null) {
        if (!GatewayUrlPolicy.isAllowed(baseUrl, BuildConfig.DEBUG)) {
            connectionMessage = if (BuildConfig.DEBUG) "Usa HTTPS o http://10.0.2.2 para el gateway de demostración." else "Configura una dirección HTTPS segura del gateway escolar."
            return
        }
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) { gateway.readSnapshot(baseUrl, classroomId, tokenOverride ?: bearerToken.ifBlank { null }) }
                snapshot = result
                connectionMessage = "Gateway conectado · Aula $classroomId"
                onConnectionRestored()
                addHistory("Lectura de estado del aula")
            } catch (error: Exception) {
                connectionMessage = error.message?.take(150) ?: "No fue posible conectar con el gateway."
                onNotification(NotificationRepository.fresh(NoticeCategory.CONNECTION, "Problemas de conexión con los aparatos", connectionMessage, "ALTA", "live:connection:gateway"))
            } finally { busy = false }
        }
    }

    fun setDevice(device: RoomDevice, enabled: Boolean) {
        if (!physicalMode) {
            val updated = snapshot.copy(devices = snapshot.devices + (device.id to enabled))
            snapshot = updated
            prefs.edit().putBoolean("classroom_device_${device.id}", enabled).apply()
            when {
                device.id.startsWith("light_") -> onDashboardDeviceChange("lights", updated.devices.filterKeys { it.startsWith("light_") }.values.any { it })
                device.id == "fan_main" || device.id == "ac_main" -> onDashboardDeviceChange("fan", updated.devices["fan_main"] == true || updated.devices["ac_main"] == true)
                device.id == "projector" -> onDashboardDeviceChange("projector", enabled)
            }
            addHistory("${device.name}: ${if (enabled) "encendido" else "apagado"} · simulación")
            connectionMessage = "Simulación actualizada · ${device.name} ${if (enabled) "encendido" else "apagado"}"
            return
        }
        if (baseUrl.isBlank()) {
            connectionMessage = "Configura primero la dirección HTTPS del gateway escolar."
            showSettings = true
            return
        }
        scope.launch {
            busy = true
            connectionMessage = "Enviando comando a ${device.name}…"
            try {
                val result = withContext(Dispatchers.IO) { gateway.setDevice(baseUrl, classroomId, device.id, enabled, bearerToken.ifBlank { null }) }
                snapshot = result
                connectionMessage = "Comando confirmado por el gateway · ${device.name}"
                onConnectionRestored()
                addHistory("${device.name}: ${if (enabled) "encendido" else "apagado"} · gateway")
            } catch (error: Exception) {
                connectionMessage = error.message?.take(150) ?: "No se pudo enviar el comando."
                onNotification(NotificationRepository.fresh(NoticeCategory.CONNECTION, "Problemas de conexión con los aparatos", connectionMessage, "ALTA", "live:connection:gateway"))
                addHistory("Error de comando · ${device.name}")
            } finally { busy = false }
        }
    }

    fun applyClassScene(startClass: Boolean) {
        val scene = mapOf(
            "light_front" to startClass, "light_center" to startClass, "light_back" to startClass,
            "fan_main" to startClass, "ac_main" to startClass, "projector" to startClass,
        )
        if (!physicalMode) {
            snapshot = snapshot.copy(devices = snapshot.devices + scene)
            val editor = prefs.edit()
            scene.forEach { (id, enabled) -> editor.putBoolean("classroom_device_$id", enabled) }
            editor.putBoolean("lights", startClass).putBoolean("fan", startClass).putBoolean("projector", startClass).apply()
            onDashboardDeviceChange("lights", startClass)
            onDashboardDeviceChange("fan", startClass)
            onDashboardDeviceChange("projector", startClass)
            connectionMessage = if (startClass) "Escena lista · luces, proyector y clima encendidos." else "Escena finalizada · luces, proyector y clima apagados."
            addHistory(if (startClass) "Escena Iniciar clase" else "Escena Terminar clase")
            return
        }
        if (baseUrl.isBlank()) {
            connectionMessage = "Configura el gateway para ejecutar esta escena."
            showSettings = true
            return
        }
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    scene.forEach { (id, enabled) -> gateway.setDevice(baseUrl, classroomId, id, enabled, bearerToken.ifBlank { null }) }
                    gateway.readSnapshot(baseUrl, classroomId, bearerToken.ifBlank { null })
                }
                snapshot = result
                connectionMessage = "Escena confirmada por el gateway · ${if (startClass) "clase iniciada" else "clase terminada"}."
                onConnectionRestored()
                addHistory(if (startClass) "Escena Iniciar clase · gateway" else "Escena Terminar clase · gateway")
            } catch (error: Exception) {
                connectionMessage = "La escena no se completó: ${error.message?.take(120) ?: "error de conexión"}."
                onNotification(NotificationRepository.fresh(NoticeCategory.CONNECTION, "No se pudo completar la escena del aula", connectionMessage, "ALTA", "live:connection:gateway"))
            } finally { busy = false }
        }
    }

    ScreenFrame("Control del aula", "Dispositivos, sensores, conectividad e historial · Aula $classroomId", wide) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MODO DE OPERACIÓN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (physicalMode) "Conexión física" else "Simulación demostrativa", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Configurar conexión") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !physicalMode, onClick = {
                    physicalMode = false; snapshot = DemoSnapshot; prefs.edit().putBoolean("physical_mode", false).apply()
                    connectionMessage = "Simulación activa · los valores no provienen de sensores físicos."
                }, label = { Text("Simular") }, leadingIcon = { Icon(Icons.Default.Science, null) })
                FilterChip(selected = physicalMode, onClick = {
                    physicalMode = true; snapshot = ClassroomSnapshot(); prefs.edit().putBoolean("physical_mode", true).apply()
                    connectionMessage = "Modo físico · conecta un gateway SmartClass compatible."
                }, label = { Text("Físico") }, leadingIcon = { Icon(Icons.Default.Router, null) })
                Spacer(Modifier.weight(1f))
                if (physicalMode) TextButton(onClick = ::readGateway, enabled = !busy) { Icon(Icons.Default.Refresh, null); Text("Leer") }
            }
            Text(connectionMessage, color = if (connectionMessage.contains("conectado", true) || connectionMessage.contains("simulación activa", true)) Teal else Muted, fontSize = 12.sp)
            if (physicalMode) Text("Los interruptores y sensores muestran datos físicos solo después de conectar el gateway.", color = Muted, fontSize = 11.sp)
        }

        SectionTitle("Escenas de clase", "Acciones rápidas para preparar o cerrar el aula")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SceneTile("Iniciar clase", "Luces · proyector · clima", Icons.Default.PlayCircle, Modifier.weight(1f), enabled = !busy) { applyClassScene(true) }
            SceneTile("Terminar clase", "Apaga luz, proyección y clima", Icons.Default.StopCircle, Modifier.weight(1f), enabled = !busy) { applyClassScene(false) }
        }
        Text("Las escenas no modifican la alarma de seguridad.", color = Muted, fontSize = 11.sp)

        SectionTitle("Iluminación por zonas", "Cada zona se controla de forma independiente")
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Devices.take(3).forEach { device -> DeviceControlCard(device, snapshot, physicalMode, busy, Modifier.weight(1f)) { setDevice(device, it) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Devices.drop(3).forEach { device -> DeviceControlCard(device, snapshot, physicalMode, busy, Modifier.weight(1f)) { setDevice(device, it) } }
            }
        } else {
            Devices.forEach { device -> DeviceControlCard(device, snapshot, physicalMode, busy, Modifier.fillMaxWidth()) { setDevice(device, it) } }
        }

        SectionTitle("Sensores ambientales y seguridad", "Lecturas con su estado de conexión")
        val sensors = listOf(
            SensorReading("Temperatura", snapshot.temperatureC?.let { "%.1f °C".format(Locale.getDefault(), it) } ?: "—", Icons.Default.Thermostat, if (physicalMode) snapshot.connected["sensor_temperature"] else true),
            SensorReading("Humedad", snapshot.humidityPercent?.let { "%.0f%%".format(Locale.getDefault(), it) } ?: "—", Icons.Default.WaterDrop, if (physicalMode) snapshot.connected["sensor_humidity"] else true),
            SensorReading("Calidad del aire", snapshot.airQualityPpm?.let { "$it ppm" } ?: "—", Icons.Default.Air, if (physicalMode) snapshot.connected["sensor_air"] else true),
            SensorReading("Movimiento", snapshot.motionDetected?.let { if (it) "Detectado" else "Sin movimiento" } ?: "—", Icons.Default.DirectionsRun, if (physicalMode) snapshot.connected["sensor_motion"] else true),
            SensorReading("Puerta", snapshot.doorOpen?.let { if (it) "Abierta" else "Cerrada" } ?: "—", Icons.Default.DoorFront, if (physicalMode) snapshot.connected["sensor_door"] else true),
            SensorReading("Ventana", snapshot.windowOpen?.let { if (it) "Abierta" else "Cerrada" } ?: "—", Icons.Default.Window, if (physicalMode) snapshot.connected["sensor_window"] else true),
        )
        if (wide) sensors.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { SensorCard(it, Modifier.weight(1f), physicalMode) } } }
        else sensors.forEach { SensorCard(it, Modifier.fillMaxWidth(), physicalMode) }

        if (!physicalMode) InfoCard {
            SectionTitle("Probar notificaciones", "Simula eventos para la demostración")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { snapshot = snapshot.copy(temperatureC = 34f) }) { Text("Temperatura alta") }
                OutlinedButton(onClick = { snapshot = snapshot.copy(connected = snapshot.connected + ("projector" to false)) }) { Text("Desconectar equipo") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { snapshot = snapshot.copy(doorOpen = true) }) { Text("Abrir puerta") }
                OutlinedButton(onClick = { snapshot = snapshot.copy(devices = snapshot.devices + ("security_alarm" to true)) }) { Text("Activar alarma") }
            }
            TextButton(onClick = { snapshot = DemoSnapshot }) { Text("Restablecer simulación") }
        }

        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = Teal); Spacer(Modifier.width(8.dp)); SectionTitle("Historial", "Últimos ${history.size} eventos de esta aula"); Spacer(Modifier.weight(1f)); TextButton(onClick = { history = emptyList(); prefs.edit().remove("$CLASSROOM_HISTORY_KEY:$classroomId").apply() }) { Text("Limpiar") } }
            if (history.isEmpty()) Text("Todavía no hay eventos registrados.", color = Muted, fontSize = 12.sp)
            else history.take(12).forEach { Text(it, color = Ink, fontSize = 12.sp) }
        }
        if (!physicalMode) Text("La simulación permite mostrar las funciones sin sensores ni actuadores conectados. Cambia a Físico y configura un gateway cuando la escuela lo tenga instalado.", color = Muted, fontSize = 11.sp)
    }

    if (showSettings) {
        GatewaySettingsDialog(baseUrl, classroomId, bearerToken, allowDebugHttp = BuildConfig.DEBUG, onDismiss = { showSettings = false }, onSave = { url, id, token ->
            baseUrl = url.trimEnd('/'); classroomId = id
            prefs.edit().putString("gateway_url", baseUrl).putString("gateway_classroom", classroomId).putBoolean("physical_mode", physicalMode).apply()
            history = prefs.getString("$CLASSROOM_HISTORY_KEY:$classroomId", null)?.lines()?.filter(String::isNotBlank).orEmpty()
            showSettings = false
            onBearerTokenChange(token)
            if (physicalMode) readGateway(token.ifBlank { null })
        })
    }
}

@Composable
private fun SceneTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    ElevatedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 112.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.elevatedButtonColors(containerColor = Navy, contentColor = Color.White), contentPadding = PaddingValues(18.dp)) {
        Icon(icon, null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFFCBD5E1))
        }
    }
}

private data class SensorReading(val label: String, val value: String, val icon: ImageVector, val connected: Boolean?)

@Composable
private fun DeviceControlCard(device: RoomDevice, snapshot: ClassroomSnapshot, physical: Boolean, busy: Boolean, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    val enabled = snapshot.devices[device.id] ?: false
    val connected = if (physical) snapshot.connected[device.id] else true
    Row(modifier.background(Color.White, RoundedCornerShape(17.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(device.icon, null, tint = if (enabled) Teal else Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(device.detail, color = Muted, fontSize = 10.sp)
            ConnectionLabel(if (!physical) true else connected)
        }
        Switch(checked = enabled, enabled = !busy && (!physical || connected == true), onCheckedChange = onToggle)
    }
}

@Composable
private fun SensorCard(reading: SensorReading, modifier: Modifier, physical: Boolean) {
    InfoCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(reading.icon, null, tint = Teal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) { Text(reading.label, color = Muted, fontSize = 11.sp); Text(reading.value, color = Navy, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
            ConnectionLabel(if (!physical) true else reading.connected)
        }
    }
}

@Composable
private fun ConnectionLabel(connected: Boolean?) {
    val label = when (connected) { true -> "Conectado"; false -> "Sin conexión"; null -> "Desconocido" }
    val color = when (connected) { true -> Teal; false -> Color(0xFFB5473C); null -> Muted }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape)); Spacer(Modifier.width(5.dp)); Text(label, color = color, fontSize = 9.sp)
    }
}

@Composable
private fun GatewaySettingsDialog(initialUrl: String, initialClassroom: String, initialToken: String, allowDebugHttp: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var classroom by remember(initialClassroom) { mutableStateOf(initialClassroom) }
    var token by remember { mutableStateOf(initialToken) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conexión de la escuela") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Conecta SmartClass a un gateway compatible que traduzca los protocolos y equipos instalados en el aula.", fontSize = 12.sp)
                OutlinedTextField(url, { url = it; error = null }, label = { Text("URL HTTPS del gateway") }, placeholder = { Text("https://iot.escuela.edu.mx") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(classroom, { classroom = it }, label = { Text("Identificador del aula") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("Token de acceso (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                Text("El token solo se conserva mientras la app permanece abierta. Usa HTTPS para proteger los comandos y lecturas.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            val cleanUrl = url.trim().trimEnd('/')
            if (!GatewayUrlPolicy.isAllowed(cleanUrl, allowDebugHttp)) error = if (allowDebugHttp) "Usa https:// o el host de emulador http://10.0.2.2." else "La dirección del gateway debe comenzar con https://"
            else if (classroom.isBlank()) error = "Escribe el identificador del aula."
            else onSave(cleanUrl, classroom.trim(), token.trim())
        }) { Text("Guardar y conectar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
