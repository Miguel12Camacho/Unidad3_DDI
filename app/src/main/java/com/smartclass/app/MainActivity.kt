package com.smartclass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

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
        setContent { SmartClassApp() }
    }
}

@Composable
fun SmartClassApp() {
    val context = LocalContext.current
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
    var notice by rememberSaveable { mutableStateOf(prefs.getString("notice", "Entrega de proyecto: viernes a las 10:00") ?: "") }
    var academicData by remember { mutableStateOf(AcademicRepository.load(prefs)) }
    var notifications by remember { mutableStateOf(NotificationRepository.load(context)) }
    var showNoticeDialog by rememberSaveable { mutableStateOf(false) }

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
        AdaptiveShell(tab = tab, teacherMode = teacherMode, onNavigate = { tab = it }) { wide ->
            when (tab) {
                "Inicio" -> {
                    val todayRecords = academicData.attendance.filter { it.date == java.time.LocalDate.now().toString() }
                    val presentCount = todayRecords.count { it.status == "PRESENT" || it.status == "LATE" }
                    val sessionActive = academicData.sessions.any { it.endedAt.isBlank() }
                    HomeScreen(wide, presentCount, academicData.students.size, lights, fan, projector, sessionActive, teacherMode, nextClassSummary(academicData, teacherMode, selectedTeacherId), onNavigate = { tab = it })
                }
                "Aula" -> ClassroomScreen(wide, prefs, gatewayToken, academicData.schedule, onBearerTokenChange = { gatewayToken = it }, onNotification = ::addNotification, onConnectionRestored = {
                    notifications = NotificationRules.resolveSource(context, "live:connection:gateway")
                }, onResolveInactive = { activeKeys -> notifications = NotificationRules.resolveInactive(context, activeKeys) }, onSnapshotChanged = { snapshot ->
                    appScope.launch(kotlinx.coroutines.Dispatchers.IO) { RoomDataMirror.syncSnapshot(database.dao(), "classroom-main", snapshot) }
                }) { device, active ->
                    when (device) {
                        "lights" -> updateDevice("lights", active, { lights = it })
                        "fan" -> updateDevice("fan", active, { fan = it })
                        "projector" -> updateDevice("projector", active, { projector = it })
                    }
                }
                "Gestión" -> AcademicManagementScreen(wide, academicData, teacherMode, selectedTeacherId, ::updateAcademicData)
                "Clases" -> ClassScheduleScreen(wide, prefs, academicData, teacherMode, selectedTeacherId, gatewayToken, ::updateAcademicData, onDevicesPowered = { ids ->
                    if (ids.any { it.startsWith("light_") }) lights = true
                    if (ids.any { it == "fan_main" || it == "ac_main" }) fan = true
                    if ("projector" in ids) projector = true
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
private fun AdaptiveShell(tab: String, teacherMode: Boolean, onNavigate: (String) -> Unit, content: @Composable (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val allDestinations = listOf(Triple("Inicio", Icons.Default.Dashboard, "Inicio"), Triple("Aula", Icons.Default.Devices, "Aula"), Triple("Gestión", Icons.Default.Groups, "Gestión"), Triple("Clases", Icons.Default.CalendarMonth, "Clases"), Triple("Avisos", Icons.Default.Campaign, "Avisos"), Triple("Cuenta", Icons.Default.AccountCircle, "Cuenta"))
        val destinations = if (teacherMode) allDestinations.filterNot { it.third == "Aula" } else allDestinations
        if (wide) {
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
