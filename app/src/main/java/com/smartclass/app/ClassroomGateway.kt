package com.smartclass.app

import android.net.Uri
import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL

internal object GatewayUrlPolicy {
    fun isAllowed(baseUrl: String, allowDebugHttp: Boolean): Boolean {
        val uri = runCatching { URI(baseUrl.trim().trimEnd('/')) }.getOrNull() ?: return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return false
        return uri.scheme.equals("https", ignoreCase = true) ||
            (allowDebugHttp && uri.scheme.equals("http", ignoreCase = true) && uri.host.equals("10.0.2.2", ignoreCase = true))
    }
}

/** Neutral classroom API contract implemented by the app and a school-side IoT gateway. */
internal data class ClassroomSnapshot(
    val devices: Map<String, Boolean> = emptyMap(),
    val connected: Map<String, Boolean> = emptyMap(),
    val temperatureC: Float? = null,
    val humidityPercent: Float? = null,
    val airQualityPpm: Int? = null,
    val motionDetected: Boolean? = null,
    val doorOpen: Boolean? = null,
    val windowOpen: Boolean? = null,
)

internal interface ClassroomGateway {
    fun readSnapshot(baseUrl: String, classroomId: String, bearerToken: String?): ClassroomSnapshot
    fun setDevice(baseUrl: String, classroomId: String, deviceId: String, enabled: Boolean, bearerToken: String?): ClassroomSnapshot
}

/** REST adapter for gateways that implement the documented SmartClass classroom API. */
internal class SmartClassRestGateway : ClassroomGateway {
    override fun readSnapshot(baseUrl: String, classroomId: String, bearerToken: String?): ClassroomSnapshot {
        val path = "/api/v1/classrooms/${Uri.encode(classroomId)}/state"
        return parse(request(baseUrl, path, "GET", null, bearerToken))
    }

    override fun setDevice(baseUrl: String, classroomId: String, deviceId: String, enabled: Boolean, bearerToken: String?): ClassroomSnapshot {
        val path = "/api/v1/classrooms/${Uri.encode(classroomId)}/devices/${Uri.encode(deviceId)}/command"
        request(baseUrl, path, "POST", JSONObject().put("state", if (enabled) "on" else "off"), bearerToken)
        return readSnapshot(baseUrl, classroomId, bearerToken)
    }

    private fun request(baseUrl: String, path: String, method: String, body: JSONObject?, bearerToken: String?): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/json")
            bearerToken?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("El gateway respondió HTTP $code${response.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}")
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: JSONObject): ClassroomSnapshot {
        val devicesJson = json.optJSONObject("devices") ?: JSONObject()
        val devices = mutableMapOf<String, Boolean>()
        val connected = mutableMapOf<String, Boolean>()
        val keys = devicesJson.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val device = devicesJson.optJSONObject(id) ?: continue
            devices[id] = device.optString("state").equals("on", ignoreCase = true)
            connected[id] = device.optBoolean("connected", false)
        }
        val sensors = json.optJSONObject("sensors") ?: JSONObject()
        val sensorConnections = sensors.optJSONObject("connected") ?: JSONObject()
        val sensorKeys = sensorConnections.keys()
        while (sensorKeys.hasNext()) {
            val id = sensorKeys.next()
            connected[id] = sensorConnections.optBoolean(id, false)
        }
        return ClassroomSnapshot(
            devices = devices,
            connected = connected,
            temperatureC = sensors.numberOrNull("temperatureC")?.toFloat(),
            humidityPercent = sensors.numberOrNull("humidityPercent")?.toFloat(),
            airQualityPpm = sensors.numberOrNull("airQualityPpm")?.toInt(),
            motionDetected = sensors.booleanOrNull("motionDetected"),
            doorOpen = sensors.booleanOrNull("doorOpen"),
            windowOpen = sensors.booleanOrNull("windowOpen"),
        )
    }

    private fun JSONObject.numberOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf(Double::isFinite) else null
    private fun JSONObject.booleanOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null
}
