package com.smartclass.app

/** Repeatable sensor changes for classroom demonstrations, never used by the physical gateway. */
internal object ClassroomDemoTelemetry {
    fun sample(previous: ClassroomSnapshot, step: Int): ClassroomSnapshot {
        val safeStep = step.coerceAtLeast(0)
        val temperature = if (safeStep % 12 == 9) 32.4f else 22.5f + (safeStep % 6) * 0.7f
        return previous.copy(
            temperatureC = temperature,
            humidityPercent = (42 + safeStep % 15).toFloat(),
            airQualityPpm = 560 + (safeStep * 37 % 420),
            motionDetected = safeStep % 2 == 0,
            doorOpen = safeStep % 10 == 0,
            windowOpen = safeStep % 17 == 0,
        )
    }
}
