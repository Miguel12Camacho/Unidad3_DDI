package com.smartclass.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal object NoticeCategory {
    const val ROOM = "Aula"
    const val DEVICES = "Dispositivos"
    const val SECURITY = "Seguridad"
    const val CLASSES = "Clases"
    const val DELIVERIES = "Entregas"
    const val MAINTENANCE = "Mantenimiento"
    const val CONNECTION = "Conexión"
    val all = listOf(ROOM, DEVICES, SECURITY, CLASSES, DELIVERIES, MAINTENANCE, CONNECTION)
}

internal data class SmartNotification(
    val id: String,
    val dedupeKey: String,
    val category: String,
    val title: String,
    val message: String,
    val priority: String,
    val createdAt: String,
    val scheduledAt: String = "",
    val read: Boolean = false,
    val resolved: Boolean = false,
    val triggered: Boolean = false,
)

internal object NotificationRepository {
    private const val KEY = "smartclass_notifications_v1"

    fun load(context: Context): List<SmartNotification> = runCatching {
        val raw = context.getSharedPreferences("smartclass", Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(SmartNotification(item.getString("id"), item.optString("dedupeKey"), item.optString("category"), item.optString("title"), item.optString("message"), item.optString("priority", "MEDIA"), item.optString("createdAt"), item.optString("scheduledAt"), item.optBoolean("read"), item.optBoolean("resolved"), item.optBoolean("triggered")))
            }
        }
    }.getOrDefault(emptyList())

    fun persist(context: Context, items: List<SmartNotification>) {
        val array = JSONArray()
        items.take(400).forEach { item ->
            array.put(JSONObject().put("id", item.id).put("dedupeKey", item.dedupeKey).put("category", item.category).put("title", item.title).put("message", item.message).put("priority", item.priority).put("createdAt", item.createdAt).put("scheduledAt", item.scheduledAt).put("read", item.read).put("resolved", item.resolved).put("triggered", item.triggered))
        }
        context.getSharedPreferences("smartclass", Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun add(context: Context, item: SmartNotification): Pair<List<SmartNotification>, Boolean> {
        val current = load(context)
        if (item.dedupeKey.isNotBlank() && current.any { it.dedupeKey == item.dedupeKey && !it.resolved }) return current to false
        val updated = listOf(item) + current
        persist(context, updated)
        return updated.take(400) to true
    }

    fun update(context: Context, transform: (List<SmartNotification>) -> List<SmartNotification>): List<SmartNotification> {
        val updated = transform(load(context)).take(400)
        persist(context, updated)
        return updated
    }

    fun fresh(category: String, title: String, message: String, priority: String, dedupeKey: String = "", scheduledAt: String = ""): SmartNotification = SmartNotification(
        id = UUID.randomUUID().toString(), dedupeKey = dedupeKey, category = category, title = title, message = message, priority = priority,
        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), scheduledAt = scheduledAt,
    )
}

internal object NotificationRules {
    fun forSnapshot(snapshot: ClassroomSnapshot, schedule: List<ScheduledClass>): List<SmartNotification> {
        val alerts = mutableListOf<SmartNotification>()
        snapshot.temperatureC?.takeIf { it >= 30f }?.let {
            alerts += NotificationRepository.fresh(NoticeCategory.ROOM, "Temperatura demasiado elevada", "El sensor reporta ${it} °C en el aula.", "ALTA", "live:temperature-high")
        }
        snapshot.connected.filterValues { !it }.keys.forEach { deviceId ->
            val deviceName = deviceLabel(deviceId)
            alerts += NotificationRepository.fresh(NoticeCategory.DEVICES, "Dispositivo desconectado", "$deviceName no responde al gateway.", "ALTA", "live:device:$deviceId")
        }
        val now = LocalDateTime.now()
        val inClass = schedule.any { entry ->
            entry.dayOfWeek == now.dayOfWeek.value && runCatching { val start = LocalTime.parse(entry.startTime); val end = LocalTime.parse(entry.endTime); !now.toLocalTime().isBefore(start) && now.toLocalTime().isBefore(end) }.getOrDefault(false)
        }
        if (snapshot.doorOpen == true && !inClass) alerts += NotificationRepository.fresh(NoticeCategory.SECURITY, "Puerta abierta fuera del horario", "El sensor detecta una puerta abierta y no hay una clase programada ahora.", "CRÍTICA", "live:door-after-hours")
        if (snapshot.devices["security_alarm"] == true) alerts += NotificationRepository.fresh(NoticeCategory.SECURITY, "Alarma activada", "La alarma del aula aparece activa.", "CRÍTICA", "live:security-alarm")
        if ((snapshot.airQualityPpm ?: 0) >= 1000) alerts += NotificationRepository.fresh(NoticeCategory.ROOM, "Calidad del aire baja", "La lectura de calidad del aire es ${snapshot.airQualityPpm} ppm.", "ALTA", "live:air-quality")
        return alerts
    }

    fun upcomingClasses(data: AcademicData, teacherMode: Boolean, teacherId: String): List<SmartNotification> {
        val now = LocalDateTime.now()
        return data.schedule.filter { !teacherMode || it.teacherId == teacherId }.mapNotNull { scheduled ->
            val date = now.toLocalDate().plusDays(((scheduled.dayOfWeek - now.dayOfWeek.value + 7) % 7).toLong())
            var start = runCatching { LocalDateTime.of(date, LocalTime.parse(scheduled.startTime)) }.getOrNull() ?: return@mapNotNull null
            if (start.isBefore(now)) start = start.plusWeeks(1)
            val alarmTime = start.minusMinutes(30).let { if (it.isBefore(now)) now.plusSeconds(2) else it }
            val subject = data.subjects.firstOrNull { it.id == scheduled.subjectId }?.name ?: "Clase"
            val group = data.groups.firstOrNull { it.id == scheduled.groupId }?.name ?: "Grupo"
            val key = "scheduled:class:${scheduled.id}:${start.toLocalDate()}"
            NotificationRepository.fresh(NoticeCategory.CLASSES, "Clase próxima: $subject", "$group · ${scheduled.room} · ${start.format(DateTimeFormatter.ofPattern("HH:mm"))}", "MEDIA", key, alarmTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }
    }

    fun resolveInactive(context: Context, activeKeys: Set<String>): List<SmartNotification> = NotificationRepository.update(context) { items ->
        items.map { item ->
            if (item.dedupeKey.startsWith("live:") && !item.resolved && item.dedupeKey !in activeKeys) item.copy(resolved = true) else item
        }
    }

    fun resolveSource(context: Context, sourceKey: String): List<SmartNotification> = NotificationRepository.update(context) { items ->
        items.map { item -> if (item.dedupeKey == sourceKey && !item.resolved) item.copy(resolved = true) else item }
    }

    fun deviceLabel(id: String): String = when (id) {
        "light_front" -> "Luces zona frontal"; "light_center" -> "Luces zona central"; "light_back" -> "Luces zona posterior"
        "fan_main" -> "Ventilador"; "ac_main" -> "Aire acondicionado"; "projector" -> "Proyector"; "security_alarm" -> "Alarma de seguridad"
        "sensor_temperature" -> "Sensor de temperatura"; "sensor_humidity" -> "Sensor de humedad"; "sensor_air" -> "Sensor de calidad del aire"
        "sensor_motion" -> "Sensor de movimiento"; "sensor_door" -> "Sensor de puerta"; "sensor_window" -> "Sensor de ventana"
        else -> id
    }
}

internal object NotificationScheduler {
    private const val ACTION = "com.smartclass.app.NOTIFICATION_ALARM"
    fun schedule(context: Context, notification: SmartNotification) {
        if (notification.scheduledAt.isBlank()) return
        val alarmTime = runCatching { LocalDateTime.parse(notification.scheduledAt).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() ?: return
        val intent = Intent(context, NotificationAlarmReceiver::class.java).setAction(ACTION).putExtra("notification_id", notification.id)
        val pendingIntent = PendingIntent.getBroadcast(context, notification.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.coerceAtLeast(System.currentTimeMillis() + 1_000), pendingIntent)
    }

    fun cancel(context: Context, notification: SmartNotification) {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).setAction(ACTION).putExtra("notification_id", notification.id)
        val pendingIntent = PendingIntent.getBroadcast(context, notification.id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun schedulePending(context: Context) {
        NotificationRepository.load(context).filter { it.scheduledAt.isNotBlank() && !it.triggered && !it.resolved }.forEach { schedule(context, it) }
    }

    internal const val BOOT_ACTION = Intent.ACTION_BOOT_COMPLETED
    internal const val ALARM_ACTION = ACTION
}

internal object NotificationPublisher {
    private const val CHANNEL_ID = "smartclass_alerts"
    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun publish(context: Context, notification: SmartNotification) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Alertas de SmartClass", NotificationManager.IMPORTANCE_HIGH).apply { description = "Avisos del aula, clases y recordatorios" })
        }
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
        manager.notify(notification.id.hashCode(), builder.setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(notification.title).setContentText(notification.message).setStyle(Notification.BigTextStyle().bigText(notification.message)).setContentIntent(openApp).setAutoCancel(true).build())
    }
}

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationScheduler.BOOT_ACTION) {
            NotificationScheduler.schedulePending(context)
            return
        }
        val id = intent.getStringExtra("notification_id") ?: return
        val fired = NotificationRepository.update(context) { notifications ->
            notifications.map { item -> if (item.id == id) item.copy(triggered = true, read = false) else item }
        }.firstOrNull { it.id == id } ?: return
        NotificationPublisher.publish(context, fired)
    }
}
