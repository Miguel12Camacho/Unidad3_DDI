package com.smartclass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Navy = Color(0xFF14213D)
internal val Navy2 = Color(0xFF1D3157)
internal val Teal = Color(0xFF0B8F80)
internal val Canvas = Color(0xFFF3F6FB)
internal val Ink = Color(0xFF18243A)
internal val Muted = Color(0xFF78849A)
internal val Students = listOf("Ana López", "Carlos Ruiz", "María Torres", "Diego Hernández", "Sofía Martínez", "Luis García", "Valentina Pérez", "Jorge Sánchez", "Camila Flores", "Mateo Ramírez")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        setContent { SmartClassApp() }
    }
}

@Composable
fun SmartClassApp() {
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val prefs = remember { context.getSharedPreferences("smartclass", 0) }
    val database = remember { SmartClassDatabase.get(context) }
    val appScope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Inicio") }
    var signedInUser by remember { mutableStateOf<UserEntity?>(null) }
    var gatewayToken by remember { mutableStateOf("") }
    var serverToken by remember { mutableStateOf("") }
    var lights by rememberSaveable { mutableStateOf(prefs.getBoolean("lights", true)) }
    var fan by rememberSaveable { mutableStateOf(prefs.getBoolean("fan", false)) }
    var projector by rememberSaveable { mutableStateOf(prefs.getBoolean("projector", true)) }
    var classroomSnapshot by remember {
        val deviceDefaults = mapOf("light_front" to true, "light_center" to true, "light_back" to false, "fan_main" to false, "ac_main" to false, "projector" to true, "security_alarm" to false)
        val devices = deviceDefaults.mapValues { (id, fallback) -> prefs.getBoolean("classroom_device_$id", fallback) }
        val sensors = if (prefs.getBoolean("physical_mode", false)) emptyMap() else listOf("sensor_temperature", "sensor_humidity", "sensor_air", "sensor_motion", "sensor_door", "sensor_window").associateWith { true }
        mutableStateOf(ClassroomSnapshot(devices = devices, connected = sensors))
    }
    var notice by rememberSaveable { mutableStateOf(prefs.getString("notice", "Entrega de proyecto: viernes a las 10:00") ?: "") }
    var academicData by remember { mutableStateOf(AcademicRepository.load(prefs)) }
    var notifications by remember { mutableStateOf(NotificationRepository.load(context)) }
    var showNoticeDialog by rememberSaveable { mutableStateOf(false) }

    if (isTv) {
        TvClassroomExperience(database, prefs)
        return
    }

    if (signedInUser == null) {
        AccountGateScreen(database) { user -> signedInUser = user; tab = "Inicio" }
        return
    }
    val currentUser = signedInUser!!
    val teacherMode = currentUser.role == "TEACHER"
    val selectedTeacherId = currentUser.teacherId.orEmpty()

    fun updateAcademicData(value: AcademicData) {
        academicData = value
        AcademicRepository.save(prefs, value)
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            RoomDataMirror.syncAcademic(database.dao(), value)
            ActivityAudit.record(database.dao(), currentUser.id, "academic.updated", "school_data")
        }
    }
    fun updateDevice(key: String, value: Boolean, set: (Boolean) -> Unit) {
        set(value)
        prefs.edit().putBoolean(key, value).apply()
    }
    fun addNotification(item: SmartNotification) {
        val (updated, added) = NotificationRepository.add(context, item)
        notifications = updated
        if (added) {
            if (item.scheduledAt.isNotBlank()) NotificationScheduler.schedule(context, item)
            else NotificationPublisher.publish(context, item)
        }
    }
    fun markNotification(id: String, read: Boolean) {
        if (!read) notifications.firstOrNull { it.id == id }?.let { NotificationScheduler.cancel(context, it) }
        notifications = NotificationRepository.update(context) { current -> current.map { item -> if (item.id == id) item.copy(read = true, resolved = !read || item.resolved) else item } }
    }
    fun refreshNotifications() { notifications = NotificationRepository.load(context) }

    LaunchedEffect(academicData.schedule, teacherMode, selectedTeacherId) {
        NotificationRules.upcomingClasses(academicData, teacherMode, selectedTeacherId).forEach(::addNotification)
    }
    LaunchedEffect(Unit) { NotificationScheduler.schedulePending(context) }
    LaunchedEffect(academicData) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RoomDataMirror.syncAcademic(database.dao(), academicData) } }
    LaunchedEffect(notifications) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RoomDataMirror.syncNotifications(database.dao(), notifications) } }
    LaunchedEffect(notice) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RoomDataMirror.syncAnnouncement(database.dao(), notice) } }

    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Teal, background = Canvas, surface = Color.White)) {
        AdaptiveShell(tab = tab, teacherMode = teacherMode, isTv = isTv, onNavigate = { tab = it }) { wide ->
            when (tab) {
                "Inicio" -> {
                    val todayRecords = academicData.attendance.filter { it.date == java.time.LocalDate.now().toString() }
                    val presentCount = todayRecords.count { it.status == "PRESENT" || it.status == "LATE" }
                    val activeSession = academicData.sessions.firstOrNull { it.endedAt.isBlank() }
                    val sessionActive = activeSession != null
                    val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX"))).replaceFirstChar { it.titlecase(Locale("es", "MX")) }
                    val classSummary = activeSession?.let { "${it.subjectName} · ${it.groupName} · ${it.room}" } ?: nextClassSummary(academicData, teacherMode, selectedTeacherId)
                    val subtitle = "$dateLabel · ${activeSession?.groupName ?: "Próxima clase"}"
                    val activeDevices = classroomSnapshot.devices.values.count { it }
                    val totalDevices = classroomSnapshot.devices.size
                    val connectedSensors = listOf("sensor_temperature", "sensor_humidity", "sensor_air", "sensor_motion", "sensor_door", "sensor_window").count { classroomSnapshot.connected[it] == true }
                    if (isTv) TvHomeScreen(presentCount, academicData.students.size, activeDevices, totalDevices, connectedSensors, sessionActive, classSummary, notice) { tab = it }
                    else HomeScreen(wide, presentCount, academicData.students.size, activeDevices, totalDevices, connectedSensors, sessionActive, teacherMode, subtitle, classSummary, onNavigate = { tab = it })
                }
                "Aula" -> ClassroomScreen(wide, prefs, gatewayToken, academicData.schedule, onBearerTokenChange = { gatewayToken = it }, onNotification = ::addNotification, onConnectionRestored = {
                    notifications = NotificationRules.resolveSource(context, "live:connection:gateway")
                }, onResolveInactive = { activeKeys -> notifications = NotificationRules.resolveInactive(context, activeKeys) }, onSnapshotChanged = { snapshot ->
                    classroomSnapshot = snapshot
                    appScope.launch(kotlinx.coroutines.Dispatchers.IO) { RoomDataMirror.syncSnapshot(database.dao(), "classroom-main", snapshot) }
                }) { device, active ->
                    when (device) {
                        "lights" -> updateDevice("lights", active, { lights = it })
                        "fan" -> updateDevice("fan", active, { fan = it })
                        "projector" -> updateDevice("projector", active, { projector = it })
                    }
                }
                "Gestión" -> AcademicManagementScreen(wide, academicData, teacherMode, selectedTeacherId, ::updateAcademicData)
                "Clases" -> ClassScheduleScreen(wide, prefs, academicData, teacherMode, selectedTeacherId, gatewayToken, ::updateAcademicData, onNotification = ::addNotification, onDevicesChanged = { states ->
                    states.forEach { (id, enabled) -> prefs.edit().putBoolean("classroom_device_$id", enabled).apply() }
                    lights = states.filterKeys { it.startsWith("light_") }.values.any { it }
                    fan = states["fan_main"] == true || states["ac_main"] == true
                    projector = states["projector"] == true
                    if (states.isNotEmpty()) classroomSnapshot = classroomSnapshot.copy(devices = classroomSnapshot.devices + states)
                }, onSnapshotChanged = { snapshot ->
                    classroomSnapshot = snapshot
                    appScope.launch(kotlinx.coroutines.Dispatchers.IO) { RoomDataMirror.syncSnapshot(database.dao(), "classroom-main", snapshot) }
                })
                "Cuenta" -> AccountScreen(wide, database, currentUser, academicData.teachers, prefs, serverToken, { serverToken = it }, {
                    kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) { ActivityAudit.record(database.dao(), currentUser.id, "account.logout", "user", currentUser.id) }
                    signedInUser = null; gatewayToken = ""; serverToken = ""; tab = "Inicio"
                }, {
                    academicData = AcademicRepository.load(prefs)
                    notifications = NotificationRepository.load(context)
                    notice = prefs.getString("notice", "") ?: ""
                })
                else -> CommunicationsScreen(wide, notice, { showNoticeDialog = true }, notifications, ::refreshNotifications, ::markNotification, ::addNotification)
            }
        }
        if (showNoticeDialog) {
            var draft by remember(notice) { mutableStateOf(notice) }
            AlertDialog(onDismissRequest = { showNoticeDialog = false }, title = { Text("Editar aviso") }, text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Mensaje para el grupo") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }, confirmButton = { TextButton(onClick = { notice = draft; prefs.edit().putString("notice", draft).apply(); showNoticeDialog = false }) { Text("Guardar aviso") } }, dismissButton = { TextButton(onClick = { showNoticeDialog = false }) { Text("Cancelar") } })
        }
    }
}

@Composable
private fun AdaptiveShell(tab: String, teacherMode: Boolean, isTv: Boolean, onNavigate: (String) -> Unit, content: @Composable (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val allDestinations = listOf(Triple("Inicio", Icons.Default.Dashboard, "Inicio"), Triple("Aula", Icons.Default.Devices, "Aula"), Triple("Gestión", Icons.Default.Groups, "Gestión"), Triple("Clases", Icons.Default.CalendarMonth, "Clases"), Triple("Avisos", Icons.Default.Campaign, "Avisos"), Triple("Cuenta", Icons.Default.AccountCircle, "Cuenta"))
        val destinations = if (teacherMode) allDestinations.filterNot { it.third == "Aula" } else allDestinations
        if (isTv) {
            Column(Modifier.fillMaxSize().background(Canvas)) {
                Row(Modifier.fillMaxWidth().background(Navy).horizontalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    destinations.forEach { (label, _, route) -> TvNavigationButton(label, selected = tab == route) { onNavigate(route) } }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content(true) }
            }
        } else if (wide) {
            Row(Modifier.fillMaxSize().background(Canvas)) {
                NavigationRail(containerColor = Color.White, header = { Icon(Icons.Default.School, "SmartClass", tint = Teal, modifier = Modifier.padding(vertical = 24.dp).size(30.dp)) }) {
                    destinations.forEach { (label, icon, route) -> NavigationRailItem(selected = tab == route, onClick = { onNavigate(route) }, icon = { Icon(icon, contentDescription = label) }, label = { Text(label) }) }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content(true) }
            }
        } else {
            Scaffold(containerColor = Canvas, bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    destinations.forEach { (label, icon, route) -> NavigationBarItem(selected = tab == route, onClick = { onNavigate(route) }, icon = { Icon(icon, contentDescription = label) }, label = { Text(label) }) }
                }
            }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content(false) } }
        }
    }
}

@Composable
private fun TvNavigationButton(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    TextButton(onClick = onClick, modifier = Modifier.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, Color(0xFF8DE4D5), RoundedCornerShape(12.dp)) else Modifier),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
        Text(if (selected) "●  $label" else label, fontSize = 18.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium)
    }
}
