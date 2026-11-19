package com.smartclass.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.net.ssl.HttpsURLConnection

internal data class CreatedAccount(val user: UserEntity, val recoveryCode: String)

internal object LocalAccountRepository {
    suspend fun createFirstAdmin(dao: SmartClassDao, name: String, email: String, password: String): CreatedAccount {
        require(dao.userCount() == 0) { "Ya existe una cuenta administradora." }
        return create(dao, name, email, password, "ADMIN", "")
    }

    suspend fun createUser(dao: SmartClassDao, name: String, email: String, password: String, role: String, teacherId: String): CreatedAccount {
        require(role in setOf("ADMIN", "TEACHER")) { "Rol inválido." }
        require(role != "TEACHER" || teacherId.isNotBlank()) { "Asigna un perfil docente." }
        return create(dao, name, email, password, role, teacherId)
    }

    private suspend fun create(dao: SmartClassDao, name: String, email: String, password: String, role: String, teacherId: String): CreatedAccount {
        val normalizedEmail = email.trim().lowercase()
        require(name.trim().length >= 2) { "Escribe el nombre completo." }
        require(normalizedEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Escribe un correo válido." }
        require(password.length >= 10 && password.any(Char::isDigit) && password.any(Char::isLetter)) { "Usa al menos 10 caracteres con letras y números." }
        require(dao.userByEmail(normalizedEmail) == null) { "Ese correo ya tiene una cuenta." }
        val passwordSalt = PasswordSecurity.randomBytes(16)
        val recoveryCode = (1..4).joinToString("-") { PasswordSecurity.randomCode(4) }
        val recoverySalt = PasswordSecurity.randomBytes(16)
        val user = UserEntity(
            id = AcademicRepository.newId("user"), email = normalizedEmail, displayName = name.trim(), role = role,
            passwordHash = PasswordSecurity.hash(password, passwordSalt), passwordSalt = passwordSalt,
            recoveryHash = PasswordSecurity.hash(recoveryCode, recoverySalt), recoverySalt = recoverySalt,
            teacherId = teacherId, createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )
        dao.putUser(user)
        return CreatedAccount(user, recoveryCode)
    }

    suspend fun login(dao: SmartClassDao, email: String, password: String): UserEntity {
        val user = dao.userByEmail(email.trim().lowercase()) ?: throw IllegalArgumentException("Correo o contraseña incorrectos.")
        if (!user.active || !PasswordSecurity.verify(password, user.passwordSalt, user.passwordHash)) throw IllegalArgumentException("Correo o contraseña incorrectos.")
        return user
    }

    suspend fun recoverPassword(dao: SmartClassDao, email: String, recoveryCode: String, newPassword: String): UserEntity {
        val user = dao.userByEmail(email.trim().lowercase()) ?: throw IllegalArgumentException("No se pudo verificar la cuenta y el código.")
        require(PasswordSecurity.verify(recoveryCode.trim().uppercase(), user.recoverySalt, user.recoveryHash)) { "No se pudo verificar la cuenta y el código." }
        require(newPassword.length >= 10 && newPassword.any(Char::isDigit) && newPassword.any(Char::isLetter)) { "Usa al menos 10 caracteres con letras y números." }
        val salt = PasswordSecurity.randomBytes(16)
        val updated = user.copy(passwordHash = PasswordSecurity.hash(newPassword, salt), passwordSalt = salt)
        dao.putUser(updated)
        return updated
    }

    suspend fun changePassword(dao: SmartClassDao, userId: String, currentPassword: String, newPassword: String): UserEntity {
        val user = dao.userById(userId) ?: throw IllegalArgumentException("La cuenta ya no existe.")
        require(PasswordSecurity.verify(currentPassword, user.passwordSalt, user.passwordHash)) { "La contraseña actual no coincide." }
        require(newPassword.length >= 10 && newPassword.any(Char::isDigit) && newPassword.any(Char::isLetter)) { "Usa al menos 10 caracteres con letras y números." }
        val salt = PasswordSecurity.randomBytes(16)
        return user.copy(passwordHash = PasswordSecurity.hash(newPassword, salt), passwordSalt = salt).also { dao.putUser(it) }
    }
}

private object PasswordSecurity {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()
    fun randomBytes(size: Int): String = ByteArray(size).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    fun randomCode(size: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString { repeat(size) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
    fun hash(secret: String, saltHex: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), saltHex.hexBytes(), ITERATIONS, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) } } finally { spec.clearPassword() }
    }
    fun verify(secret: String, saltHex: String, expected: String): Boolean = runCatching {
        MessageDigest.isEqual(hash(secret, saltHex).hexBytes(), expected.hexBytes())
    }.getOrDefault(false)
    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

internal object RoomDataMirror {
    suspend fun syncAcademic(dao: SmartClassDao, data: AcademicData) {
        val rooms = data.schedule.map { it.room }.filter(String::isNotBlank).distinct().associateWith { room -> "room-${room.hashCode()}" }
        dao.clearStudents(); dao.putStudents(data.students.map { StudentEntity(it.id, it.name, it.enrollment, it.groupId) })
        dao.clearTeachers(); dao.putTeachers(data.teachers.map { TeacherEntity(it.id, it.name) })
        dao.clearGroups(); dao.putGroups(data.groups.map { GroupEntity(it.id, it.name, it.teacherId) })
        dao.clearClassrooms(); dao.putClassrooms(rooms.map { (name, id) -> ClassroomEntity(id, name) })
        dao.clearSubjects(); dao.putSubjects(data.subjects.map { SubjectEntity(it.id, it.name) })
        dao.clearSchedules(); dao.putSchedules(data.schedule.map { ScheduleEntity(it.id, it.subjectId, it.groupId, it.teacherId, rooms[it.room].orEmpty(), it.dayOfWeek, it.startTime, it.endTime, JSONArray(it.powerOnDeviceIds).toString()) })
        dao.clearSessions(); dao.putSessions(data.sessions.map { ClassSessionEntity(it.id, it.scheduleId, it.subjectName, it.groupName, it.teacherName, it.room, it.startedAt, it.endedAt, it.presentCount, it.studentCount) })
        dao.clearAttendance(); dao.putAttendance(data.attendance.map { AttendanceEntity("${it.studentId}_${it.date}", it.studentId, it.groupId, it.date, it.status, it.note, it.updatedAt) })
        val sessions = data.sessions.map { it.id to it }
        dao.putActivity(sessions.map { (id, s) -> ActivityLogEntity("session:$id", "", if (s.endedAt.isBlank()) "session.started" else "session.finished", "class_session", id, "${s.subjectName} · ${s.groupName} · ${s.room}", s.endedAt.ifBlank { s.startedAt }) })
    }

    suspend fun syncNotifications(dao: SmartClassDao, items: List<SmartNotification>) {
        dao.clearAlerts()
        dao.putAlerts(items.map { AlertEntity(it.id, it.category, it.title, it.message, it.priority, it.createdAt, it.scheduledAt, it.read, it.resolved) })
    }

    suspend fun syncAnnouncement(dao: SmartClassDao, notice: String) {
        dao.putAnnouncements(listOf(AnnouncementEntity("main-notice", notice, "Aviso", "MEDIA", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))))
    }

    suspend fun syncSnapshot(dao: SmartClassDao, classroomId: String, snapshot: ClassroomSnapshot) {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dao.putDevices(snapshot.devices.map { (id, active) -> DeviceEntity(id, classroomId, NotificationRules.deviceLabel(id), id.substringBefore('_'), snapshot.connected[id] ?: true, if (active) "on" else "off", now) })
        val readings = listOfNotNull(
            snapshot.temperatureC?.let { SensorReadingEntity(AcademicRepository.newId("reading"), classroomId, "sensor_temperature", it.toDouble(), "°C", snapshot.connected["sensor_temperature"] ?: true, now) },
            snapshot.humidityPercent?.let { SensorReadingEntity(AcademicRepository.newId("reading"), classroomId, "sensor_humidity", it.toDouble(), "%", snapshot.connected["sensor_humidity"] ?: true, now) },
            snapshot.airQualityPpm?.let { SensorReadingEntity(AcademicRepository.newId("reading"), classroomId, "sensor_air", it.toDouble(), "ppm", snapshot.connected["sensor_air"] ?: true, now) },
            snapshot.motionDetected?.let { SensorReadingEntity(AcademicRepository.newId("reading"), classroomId, "sensor_motion", if (it) 1.0 else 0.0, "boolean", snapshot.connected["sensor_motion"] ?: true, now) },
            snapshot.doorOpen?.let { SensorReadingEntity(AcademicRepository.newId("reading"), classroomId, "sensor_door", if (it) 1.0 else 0.0, "boolean", snapshot.connected["sensor_door"] ?: true, now) },
            snapshot.windowOpen?.let { SensorReadingEntity(AcademicRepository.newId("reading"), classroomId, "sensor_window", if (it) 1.0 else 0.0, "boolean", snapshot.connected["sensor_window"] ?: true, now) },
        )
        if (readings.isNotEmpty()) { dao.putSensorReadings(readings); dao.trimSensorReadings() }
    }

    suspend fun restorePreferences(context: Context, dao: SmartClassDao) {
        val rooms = dao.classrooms().associateBy { it.id }
        val attendance = dao.attendance().map { AttendanceEntry(it.studentId, it.groupId, it.date, it.status, it.note, it.updatedAt) }
        val schedule = dao.schedules().map { entry ->
            ScheduledClass(entry.id, entry.subjectId, entry.groupId, entry.teacherId, rooms[entry.classroomId]?.name.orEmpty(), entry.dayOfWeek, entry.startTime, entry.endTime, runCatching { JSONArray(entry.devicesJson).let { array -> (0 until array.length()).map { index -> array.optString(index) }.filter(String::isNotBlank) } }.getOrDefault(emptyList()))
        }
        val academic = AcademicData(
            students = dao.students().map { StudentRecord(it.id, it.name, it.enrollment, it.groupId) },
            groups = dao.groups().map { SchoolGroup(it.id, it.name, it.teacherId) },
            teachers = dao.teachers().map { TeacherProfile(it.id, it.name) },
            attendance = attendance,
            subjects = dao.subjects().map { SchoolSubject(it.id, it.name) },
            schedule = schedule,
            sessions = dao.sessions().map { ClassSession(it.id, it.scheduleId, it.subjectName, it.groupName, it.teacherName, rooms[it.classroomName]?.name ?: it.classroomName, it.startedAt, it.endedAt, it.presentCount, it.studentCount) },
        )
        AcademicRepository.save(context.getSharedPreferences("smartclass", Context.MODE_PRIVATE), academic)
        dao.announcements().firstOrNull { it.id == "main-notice" }?.let { context.getSharedPreferences("smartclass", Context.MODE_PRIVATE).edit().putString("notice", it.message).apply() }
        val notifications = dao.alerts().map { SmartNotification(it.id, "", it.category, it.title, it.message, it.priority, it.createdAt, it.scheduledAt, it.read, it.resolved) }
        NotificationRepository.persist(context, notifications)
        dao.devices().forEach { device ->
            when {
                device.id.startsWith("light_") -> context.getSharedPreferences("smartclass", Context.MODE_PRIVATE).edit().putBoolean("classroom_device_${device.id}", device.state == "on").putBoolean("lights", device.state == "on").apply()
                device.id == "fan_main" || device.id == "ac_main" -> context.getSharedPreferences("smartclass", Context.MODE_PRIVATE).edit().putBoolean("classroom_device_${device.id}", device.state == "on").putBoolean("fan", device.state == "on").apply()
                device.id == "projector" -> context.getSharedPreferences("smartclass", Context.MODE_PRIVATE).edit().putBoolean("classroom_device_projector", device.state == "on").putBoolean("projector", device.state == "on").apply()
            }
        }
    }

    suspend fun syncDevice(dao: SmartClassDao, classroomId: String, id: String, active: Boolean, connected: Boolean = true) {
        dao.putDevices(listOf(DeviceEntity(id, classroomId, NotificationRules.deviceLabel(id), id.substringBefore('_'), connected, if (active) "on" else "off", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))))
    }
}

internal object ActivityAudit {
    suspend fun record(dao: SmartClassDao, actorId: String, action: String, targetType: String, targetId: String = "", detail: String = "") {
        dao.putActivity(listOf(ActivityLogEntity(AcademicRepository.newId("activity"), actorId, action, targetType, targetId, detail, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))))
    }
}

internal object RoomBackup {
    suspend fun export(dao: SmartClassDao): String {
        val root = JSONObject().put("format", "smartclass-backup-v1").put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        root.put("students", JSONArray().apply { dao.students().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("enrollment", it.enrollment).put("groupId", it.groupId)) } })
        root.put("teachers", JSONArray().apply { dao.teachers().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("email", it.email)) } })
        root.put("groups", JSONArray().apply { dao.groups().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("teacherId", it.teacherId)) } })
        root.put("classrooms", JSONArray().apply { dao.classrooms().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("location", it.location).put("gatewayId", it.gatewayId)) } })
        root.put("subjects", JSONArray().apply { dao.subjects().forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        root.put("schedules", JSONArray().apply { dao.schedules().forEach { put(JSONObject().put("id", it.id).put("subjectId", it.subjectId).put("groupId", it.groupId).put("teacherId", it.teacherId).put("classroomId", it.classroomId).put("dayOfWeek", it.dayOfWeek).put("startTime", it.startTime).put("endTime", it.endTime).put("devicesJson", it.devicesJson)) } })
        root.put("sessions", JSONArray().apply { dao.sessions().forEach { put(JSONObject().put("id", it.id).put("scheduleId", it.scheduleId).put("subjectName", it.subjectName).put("groupName", it.groupName).put("teacherName", it.teacherName).put("classroomName", it.classroomName).put("startedAt", it.startedAt).put("endedAt", it.endedAt).put("presentCount", it.presentCount).put("studentCount", it.studentCount)) } })
        root.put("attendance", JSONArray().apply { dao.attendance().forEach { put(JSONObject().put("id", it.id).put("studentId", it.studentId).put("groupId", it.groupId).put("date", it.date).put("status", it.status).put("note", it.note).put("updatedAt", it.updatedAt)) } })
        root.put("devices", JSONArray().apply { dao.devices().forEach { put(JSONObject().put("id", it.id).put("classroomId", it.classroomId).put("name", it.name).put("kind", it.kind).put("connected", it.connected).put("state", it.state).put("updatedAt", it.updatedAt)) } })
        root.put("sensorReadings", JSONArray().apply { dao.sensorReadings().forEach { put(JSONObject().put("id", it.id).put("classroomId", it.classroomId).put("sensorId", it.sensorId).put("value", it.value).put("unit", it.unit).put("connected", it.connected).put("recordedAt", it.recordedAt)) } })
        root.put("alerts", JSONArray().apply { dao.alerts().forEach { put(JSONObject().put("id", it.id).put("category", it.category).put("title", it.title).put("message", it.message).put("priority", it.priority).put("createdAt", it.createdAt).put("scheduledAt", it.scheduledAt).put("read", it.read).put("resolved", it.resolved)) } })
        root.put("announcements", JSONArray().apply { dao.announcements().forEach { put(JSONObject().put("id", it.id).put("message", it.message).put("category", it.category).put("priority", it.priority).put("createdAt", it.createdAt).put("dueAt", it.dueAt)) } })
        root.put("activityLog", JSONArray().apply { dao.activityLog().forEach { put(JSONObject().put("id", it.id).put("actorUserId", it.actorUserId).put("action", it.action).put("targetType", it.targetType).put("targetId", it.targetId).put("detail", it.detail).put("occurredAt", it.occurredAt)) } })
        return root.toString(2)
    }

    suspend fun import(dao: SmartClassDao, raw: String) {
        val root = JSONObject(raw)
        require(root.optString("format") == "smartclass-backup-v1") { "El archivo no es un respaldo SmartClass compatible." }
        fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
        dao.clearStudents(); dao.putStudents(root.array("students").objects { StudentEntity(getString("id"), getString("name"), getString("enrollment"), getString("groupId")) })
        dao.clearTeachers(); dao.putTeachers(root.array("teachers").objects { TeacherEntity(getString("id"), getString("name"), optString("email")) })
        dao.clearGroups(); dao.putGroups(root.array("groups").objects { GroupEntity(getString("id"), getString("name"), optString("teacherId")) })
        dao.clearClassrooms(); dao.putClassrooms(root.array("classrooms").objects { ClassroomEntity(getString("id"), getString("name"), optString("location"), optString("gatewayId")) })
        dao.clearSubjects(); dao.putSubjects(root.array("subjects").objects { SubjectEntity(getString("id"), getString("name")) })
        dao.clearSchedules(); dao.putSchedules(root.array("schedules").objects { ScheduleEntity(getString("id"), getString("subjectId"), getString("groupId"), getString("teacherId"), optString("classroomId"), optInt("dayOfWeek"), getString("startTime"), getString("endTime"), optString("devicesJson", "[]")) })
        dao.clearSessions(); dao.putSessions(root.array("sessions").objects { ClassSessionEntity(getString("id"), optString("scheduleId"), getString("subjectName"), getString("groupName"), getString("teacherName"), optString("classroomName"), getString("startedAt"), optString("endedAt"), optInt("presentCount"), optInt("studentCount")) })
        dao.clearAttendance(); dao.putAttendance(root.array("attendance").objects { AttendanceEntity(getString("id"), getString("studentId"), getString("groupId"), getString("date"), getString("status"), optString("note"), optString("updatedAt")) })
        dao.clearDevices(); dao.putDevices(root.array("devices").objects { DeviceEntity(getString("id"), optString("classroomId"), getString("name"), getString("kind"), optBoolean("connected"), getString("state"), optString("updatedAt")) })
        dao.clearSensorReadings(); dao.putSensorReadings(root.array("sensorReadings").objects { SensorReadingEntity(getString("id"), optString("classroomId"), getString("sensorId"), optDouble("value"), optString("unit"), optBoolean("connected"), getString("recordedAt")) })
        dao.clearAlerts(); dao.putAlerts(root.array("alerts").objects { AlertEntity(getString("id"), getString("category"), getString("title"), getString("message"), getString("priority"), getString("createdAt"), optString("scheduledAt"), optBoolean("read"), optBoolean("resolved")) })
        dao.clearAnnouncements(); dao.putAnnouncements(root.array("announcements").objects { AnnouncementEntity(getString("id"), getString("message"), getString("category"), getString("priority"), getString("createdAt"), optString("dueAt")) })
        dao.clearActivity(); dao.putActivity(root.array("activityLog").objects { ActivityLogEntity(getString("id"), optString("actorUserId"), getString("action"), getString("targetType"), optString("targetId"), optString("detail"), getString("occurredAt")) })
    }

    private inline fun <T> JSONArray.objects(transform: JSONObject.() -> T): List<T> = buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it.transform()) } }
}

internal object SecureSyncClient {
    fun push(baseUrl: String, token: String, backup: String): String {
        require(baseUrl.startsWith("https://", true)) { "La sincronización requiere HTTPS." }
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/sync/push").openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 20_000; doOutput = true
            setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Content-Type", "application/json; charset=utf-8"); setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(backup) }
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) throw IllegalStateException("El servidor respondió HTTP $responseCode.")
            return body
        } finally { connection.disconnect() }
    }

    fun pull(baseUrl: String, token: String): String {
        require(baseUrl.startsWith("https://", true)) { "La sincronización requiere HTTPS." }
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/sync/pull").openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 20_000; setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Accept", "application/json")
        }
        try {
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) throw IllegalStateException("El servidor respondió HTTP $responseCode.")
            return body
        } finally { connection.disconnect() }
    }
}
