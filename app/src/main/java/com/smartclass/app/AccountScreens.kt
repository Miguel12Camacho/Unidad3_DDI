package com.smartclass.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun AccountGateScreen(database: SmartClassDatabase, onAuthenticated: (UserEntity) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accountCount by remember { mutableStateOf<Int?>(null) }
    var creatingAdmin by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var pendingAdmin by remember { mutableStateOf<CreatedAccount?>(null) }
    var error by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { accountCount = withContext(Dispatchers.IO) { database.dao().userCount() } }

    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Teal, background = Canvas, surface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) {
            if (accountCount == null) CircularProgressIndicator(color = Teal)
            else Column(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(22.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Icon(Icons.Default.School, null, tint = Teal, modifier = Modifier.size(34.dp))
                Text("SMARTCLASS", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(when { pendingAdmin != null -> "Administrador creado"; recovering -> "Recuperar contraseña"; accountCount == 0 -> "Configurar administrador"; else -> "Iniciar sesión" }, color = Navy, fontWeight = FontWeight.Bold, fontSize = 23.sp)

                if (pendingAdmin != null) {
                    InfoCard {
                        Text("Guarda este código de recuperación ahora. Se almacena verificado y SmartClass no podrá mostrarlo otra vez.", color = Ink, fontSize = 12.sp)
                        Text(pendingAdmin!!.recoveryCode, color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { onAuthenticated(pendingAdmin!!.user) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Guardé el código y continuar") }
                    }
                } else if (recovering) {
                    OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Correo de la cuenta") }, singleLine = true)
                    OutlinedTextField(recoveryCode, { recoveryCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Código de recuperación") }, singleLine = true)
                    OutlinedTextField(newPassword, { newPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nueva contraseña · 10+ caracteres") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(enabled = !working, onClick = {
                        working = true; error = ""
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { LocalAccountRepository.recoverPassword(database.dao(), email, recoveryCode, newPassword) } }
                                .onSuccess { recovering = false; password = "" }
                                .onFailure { error = it.message ?: "No se pudo recuperar la cuenta." }
                            working = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text(if (working) "Validando…" else "Restablecer contraseña") }
                    TextButton(onClick = { recovering = false; error = "" }) { Text("Volver a iniciar sesión") }
                } else if (accountCount == 0) {
                    Text("Crea la cuenta propietaria de este dispositivo. Las cuentas y contraseñas se guardarán localmente en Room.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(displayName, { displayName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Contraseña · 10+ caracteres con letras y números") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(enabled = !working, onClick = {
                        working = true; error = ""
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    LocalAccountRepository.createFirstAdmin(database.dao(), displayName, email, password).also { ActivityAudit.record(database.dao(), it.user.id, "account.created", "user", it.user.id, "Cuenta administradora local") }
                                }
                            }.onSuccess { pendingAdmin = it; accountCount = 1 }
                                .onFailure { error = it.message ?: "No se pudo crear la cuenta." }
                            working = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Crear cuenta administradora") }
                } else {
                    OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(enabled = !working, onClick = {
                        working = true; error = ""
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { LocalAccountRepository.login(database.dao(), email, password).also { ActivityAudit.record(database.dao(), it.id, "account.login", "user", it.id) } } }
                                .onSuccess(onAuthenticated)
                                .onFailure { error = it.message ?: "No se pudo iniciar sesión." }
                            working = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text(if (working) "Verificando…" else "Iniciar sesión") }
                    TextButton(onClick = { recovering = true; error = "" }) { Text("Olvidé mi contraseña") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text("Los perfiles locales protegen esta instalación. Las cuentas compartidas y el restablecimiento por correo requieren un servidor escolar.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
internal fun AccountScreen(
    wide: Boolean,
    database: SmartClassDatabase,
    user: UserEntity,
    teachers: List<TeacherProfile>,
    prefs: android.content.SharedPreferences,
    serverToken: String,
    onServerTokenChange: (String) -> Unit,
    onLogout: () -> Unit,
    onReload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = database.dao()
    var users by remember { mutableStateOf(emptyList<UserEntity>()) }
    var audit by remember { mutableStateOf(emptyList<ActivityLogEntity>()) }
    var serverUrl by remember { mutableStateOf(prefs.getString("sync_server_url", "") ?: "") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var createUser by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var pendingAccount by remember { mutableStateOf<CreatedAccount?>(null) }
    var importPayload by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() { users = withContext(Dispatchers.IO) { dao.users() }; audit = withContext(Dispatchers.IO) { dao.activityLog() } }
    LaunchedEffect(user.id) { refresh() }

    var backupPayload by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(backupPayload) } }.onFailure { status = it.message ?: "No se pudo escribir el respaldo." }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("No se pudo leer el archivo.") }
            .onSuccess { importPayload = it }.onFailure { status = it.message ?: "No se pudo abrir el archivo." }
    }

    ScreenFrame("Cuenta y seguridad", "${user.displayName} · ${user.email} · ${if (user.role == "ADMIN") "Administrador" else "Docente"}", wide) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SESIÓN ACTUAL", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(user.displayName, color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(user.email, color = Muted, fontSize = 11.sp) }; Icon(Icons.Default.VerifiedUser, null, tint = Teal) }
            Text(if (user.role == "ADMIN") "Permisos: estudiantes, grupos, horarios, avisos, usuarios, respaldos y configuración." else "Permisos: grupos asignados, asistencia, clases y avisos. La configuración del aula y la administración de cuentas están restringidas." , color = Muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { changePassword = true }) { Icon(Icons.Default.Password, null); Text("Cambiar contraseña") }
                Button(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { ActivityAudit.record(dao, user.id, "account.logout", "user", user.id) }; onLogout() }
                }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Logout, null); Text("Cerrar sesión") }
            }
        }

        if (user.role == "ADMIN") {
            Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Usuarios y permisos", "Las cuentas se administran por rol"); Spacer(Modifier.weight(1f)); Button(onClick = { createUser = true }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.PersonAdd, null); Text("Crear usuario") } }
            users.forEach { account ->
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(account.displayName, color = Navy, fontWeight = FontWeight.Bold); Text("${account.email} · ${account.role} · ${if (account.active) "Activa" else "Desactivada"}", color = Muted, fontSize = 11.sp) }
                        if (account.id != user.id) TextButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { dao.putUser(account.copy(active = !account.active)); ActivityAudit.record(dao, user.id, if (account.active) "account.disabled" else "account.enabled", "user", account.id) }; refresh() }
                        }) { Text(if (account.active) "Desactivar" else "Activar") }
                    }
                }
            }

            InfoCard {
                SectionTitle("Respaldos locales", "Exporta o restaura los datos del sistema")
                Text("El archivo no incluye contraseñas, códigos de recuperación ni cuentas de usuario.", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        busy = true
                        scope.launch { runCatching { withContext(Dispatchers.IO) { RoomBackup.export(dao) } }.onSuccess { backupPayload = it; exportLauncher.launch("smartclass_respaldo_${LocalDateTime.now().toLocalDate()}.json") }.onFailure { status = it.message ?: "Error exportando." }; busy = false }
                    }, enabled = !busy) { Icon(Icons.Default.FileDownload, null); Text("Exportar respaldo") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json")) }, enabled = !busy) { Icon(Icons.Default.FileUpload, null); Text("Restaurar respaldo") }
                }
            }

            InfoCard {
                SectionTitle("Sincronización escolar", "Servidor compartido · HTTPS")
                OutlinedTextField(serverUrl, { serverUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("URL HTTPS del servidor") }, placeholder = { Text("https://smartclass.escuela.edu.mx") }, singleLine = true)
                OutlinedTextField(serverToken, onServerTokenChange, modifier = Modifier.fillMaxWidth(), label = { Text("Token de sincronización") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("El token se conserva solo en memoria. El servidor debe implementar el contrato de documentacion/BACKEND_API.md.", color = Muted, fontSize = 10.sp)
                Button(enabled = !busy, onClick = {
                    if (!serverUrl.startsWith("https://", true) || serverToken.isBlank()) { status = "Configura una URL HTTPS y un token del servidor."; return@Button }
                    prefs.edit().putString("sync_server_url", serverUrl.trimEnd('/')).apply()
                    busy = true; status = "Sincronizando…"
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val payload = RoomBackup.export(dao)
                                val accepted = SecureSyncClient.push(serverUrl.trimEnd('/'), serverToken, payload)
                                val acceptedJson = JSONObject(accepted)
                                require(acceptedJson.optBoolean("accepted", false)) { "El servidor no confirmó la sincronización." }
                                val remote = SecureSyncClient.pull(serverUrl.trimEnd('/'), serverToken)
                                RoomBackup.import(dao, remote)
                                RoomDataMirror.restorePreferences(context, dao)
                                ActivityAudit.record(dao, user.id, "sync.completed", "backup", detail = "Sincronización HTTPS completada")
                            }
                        }.onSuccess { status = "Sincronización completada."; refresh(); onReload() }
                            .onFailure { status = it.message ?: "No se pudo sincronizar." }
                        busy = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(5.dp)); Text(if (busy) "Trabajando…" else "Sincronizar ahora") }
            }

            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Registro de actividad", "Últimas ${audit.size} operaciones"); Spacer(Modifier.weight(1f)); IconButton(onClick = { scope.launch { refresh() } }) { Icon(Icons.Default.Refresh, "Actualizar registro") } }
                audit.take(15).forEach { item ->
                    val actor = users.firstOrNull { it.id == item.actorUserId }?.displayName ?: "Sistema"
                    Text("${item.occurredAt.take(16).replace('T', ' ')} · $actor · ${item.action} · ${item.detail}", color = Muted, fontSize = 10.sp)
                }
            }
        } else {
            InfoCard {
                SectionTitle("Datos locales", "Tus grupos y asistencia permanecen en este dispositivo hasta sincronizar con el servidor escolar.")
                Text("Solicita a administración los respaldos y la configuración de sincronización.", color = Muted, fontSize = 11.sp)
            }
        }
        if (status.isNotBlank()) Text(status, color = if (status.contains("error", true) || status.contains("no se", true)) Color(0xFFB5473C) else Teal, fontSize = 12.sp)
    }

    if (createUser) CreateUserDialog(teachers, onDismiss = { createUser = false }, onCreate = { name, email, password, role, teacherId ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LocalAccountRepository.createUser(dao, name, email, password, role, teacherId).also { ActivityAudit.record(dao, user.id, "account.created", "user", it.user.id, "Rol ${it.user.role}") } } }
                .onSuccess { pendingAccount = it; createUser = false; refresh() }
                .onFailure { status = it.message ?: "No se pudo crear la cuenta." }
        }
    })
    if (pendingAccount != null) AlertDialog(onDismissRequest = { pendingAccount = null }, title = { Text("Usuario creado") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Entrega este código al usuario y pídele que lo guarde. No volverá a mostrarse."); Text(pendingAccount!!.recoveryCode, color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
    }, confirmButton = { TextButton(onClick = { pendingAccount = null }) { Text("Guardé el código") } })
    if (changePassword) ChangePasswordDialog(onDismiss = { changePassword = false }, onSave = { current, next ->
        scope.launch { runCatching { withContext(Dispatchers.IO) { LocalAccountRepository.changePassword(dao, user.id, current, next).also { ActivityAudit.record(dao, user.id, "account.password_changed", "user", user.id) } } }.onSuccess { status = "Contraseña actualizada."; changePassword = false }.onFailure { status = it.message ?: "No se pudo cambiar la contraseña." } }
    })
    importPayload?.let { payload ->
        AlertDialog(onDismissRequest = { importPayload = null }, title = { Text("Restaurar respaldo") }, text = { Text("Esta operación reemplazará los datos locales de estudiantes, horarios, asistencias, alertas y actividad. ¿Continuar?") }, confirmButton = {
            TextButton(onClick = { busy = true; scope.launch {
                runCatching { withContext(Dispatchers.IO) { RoomBackup.import(dao, payload); RoomDataMirror.restorePreferences(context, dao); ActivityAudit.record(dao, user.id, "backup.restored", "backup") } }
                    .onSuccess { status = "Respaldo restaurado."; refresh(); onReload() }.onFailure { status = it.message ?: "Respaldo inválido." }
                busy = false; importPayload = null
            } }) { Text("Restaurar", color = MaterialTheme.colorScheme.error) }
        }, dismissButton = { TextButton(onClick = { importPayload = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun CreateUserDialog(teachers: List<TeacherProfile>, onDismiss: () -> Unit, onCreate: (String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("TEACHER") }; var teacherId by remember { mutableStateOf(teachers.firstOrNull()?.id.orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Crear cuenta") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Contraseña temporal · 10+ caracteres") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = role == "TEACHER", onClick = { role = "TEACHER" }, label = { Text("Docente") }); FilterChip(selected = role == "ADMIN", onClick = { role = "ADMIN" }, label = { Text("Administrador") }) }
            if (role == "TEACHER") Box {
                OutlinedButton(onClick = { expanded = true }, enabled = teachers.isNotEmpty()) { Text("Perfil: ${teachers.firstOrNull { it.id == teacherId }?.name ?: "Crea primero un docente"}"); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { teachers.forEach { teacher -> DropdownMenuItem(text = { Text(teacher.name) }, onClick = { teacherId = teacher.id; expanded = false }) } }
            }
            Text("La contraseña se guarda como hash PBKDF2. El código de recuperación se muestra una sola vez.", color = Muted, fontSize = 10.sp)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = {
        if (role == "TEACHER" && teachers.isEmpty()) error = "Registra el perfil docente antes de crear la cuenta."
        else onCreate(name, email, password, role, if (role == "TEACHER") teacherId else "")
    }) { Text("Crear") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cambiar contraseña") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(current, { current = it }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(next, { next = it }, label = { Text("Nueva · 10+ caracteres") }, visualTransformation = PasswordVisualTransformation()) }
    }, confirmButton = { TextButton(onClick = { onSave(current, next) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

