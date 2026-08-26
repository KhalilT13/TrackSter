package com.Khalil.trackster.model

import java.util.Calendar

data class DaySchedule(
    val open: Boolean,
    val openTime: String = "09:00",
    val closeTime: String = "17:00",
    val breakEnabled: Boolean = false,
    val breakStart: String = "13:00",
    val breakEnd: String = "14:00"
) {
    // Purpose: Converts this model into a map suitable for Firestore storage.
    fun toMap(): Map<String, Any> = mapOf(
        "open" to open,
        "openTime" to openTime,
        "closeTime" to closeTime,
        "breakEnabled" to breakEnabled,
        "breakStart" to breakStart,
        "breakEnd" to breakEnd
    )

    companion object {
        // Purpose: Converts a Firestore map into a validated app model.
        fun fromMap(raw: Map<*, *>?): DaySchedule? {
            if (raw == null) return null
            return DaySchedule(
                open = raw["open"] as? Boolean ?: false,
                openTime = raw["openTime"]?.toString() ?: "09:00",
                closeTime = raw["closeTime"]?.toString() ?: "17:00",
                breakEnabled = raw["breakEnabled"] as? Boolean ?: false,
                breakStart = raw["breakStart"]?.toString() ?: "13:00",
                breakEnd = raw["breakEnd"]?.toString() ?: "14:00"
            )
        }
    }
}

object WeeklySchedule {
    val dayKeys = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    val shortNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    // Purpose: Creates the default weekly schedule with weekdays open and Friday and Saturday closed.
    fun default(): LinkedHashMap<String, DaySchedule> = linkedMapOf(
        "sunday" to DaySchedule(true),
        "monday" to DaySchedule(true),
        "tuesday" to DaySchedule(true),
        "wednesday" to DaySchedule(true),
        "thursday" to DaySchedule(true),
        "friday" to DaySchedule(false),
        "saturday" to DaySchedule(false)
    )

    // Purpose: Converts raw Firestore data into the structured model used by the app.
    fun fromFirestore(raw: Any?): LinkedHashMap<String, DaySchedule> {
        val map = raw as? Map<*, *> ?: return default()
        return LinkedHashMap<String, DaySchedule>().apply {
            dayKeys.forEach { key ->
                put(key, DaySchedule.fromMap(map[key] as? Map<*, *>) ?: default().getValue(key))
            }
        }
    }

    // Purpose: Converts app data into the map structure stored in Firestore.
    fun toFirestore(schedule: Map<String, DaySchedule>): Map<String, Any> =
        dayKeys.associateWith { schedule[it]?.toMap() ?: DaySchedule(false).toMap() }

    // Purpose: Maps a Calendar day of week to the key used by the weekly schedule.
    fun keyFor(calendar: Calendar): String = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "sunday"
        Calendar.MONDAY -> "monday"
        Calendar.TUESDAY -> "tuesday"
        Calendar.WEDNESDAY -> "wednesday"
        Calendar.THURSDAY -> "thursday"
        Calendar.FRIDAY -> "friday"
        else -> "saturday"
    }

    // Purpose: Converts an HH:mm time string into minutes from the start of the day.
    fun minutes(time: String): Int? {
        val parts = time.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    // Purpose: Formats minutes from the start of the day as an HH:mm time label.
    fun timeLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    // Purpose: Validates opening, closing, and optional break times for one working day.
    fun isValid(day: DaySchedule): Boolean {
        if (!day.open) return true
        val start = minutes(day.openTime) ?: return false
        val end = minutes(day.closeTime) ?: return false
        if (start >= end) return false
        if (!day.breakEnabled) return true
        val breakStart = minutes(day.breakStart) ?: return false
        val breakEnd = minutes(day.breakEnd) ?: return false
        return breakStart < breakEnd && breakStart >= start && breakEnd <= end
    }

    // Purpose: Generates valid appointment starts while respecting hours, breaks, duration, and earliest start.
    fun slots(day: DaySchedule, durationMinutes: Int, earliestStart: Int? = null): List<String> {
        if (!day.open || !isValid(day)) return emptyList()
        val opening = minutes(day.openTime) ?: return emptyList()
        val closing = minutes(day.closeTime) ?: return emptyList()
        val breakStart = if (day.breakEnabled) minutes(day.breakStart) else null
        val breakEnd = if (day.breakEnabled) minutes(day.breakEnd) else null
        val first = maxOf(opening, earliestStart ?: opening)
        var cursor = ((first + 29) / 30) * 30
        val result = mutableListOf<String>()
        while (cursor + durationMinutes <= closing) {
            val overlapsBreak = breakStart != null && breakEnd != null && cursor < breakEnd && cursor + durationMinutes > breakStart
            if (!overlapsBreak) result += timeLabel(cursor)
            cursor += 30
        }
        return result
    }

    // Purpose: Builds a readable summary of the weekly working schedule.
    fun summary(schedule: Map<String, DaySchedule>): String {
        val groups = mutableListOf<Pair<IntRange, DaySchedule>>()
        var start = 0
        for (index in 1..dayKeys.size) {
            val changed = index == dayKeys.size || schedule[dayKeys[index]] != schedule[dayKeys[start]]
            if (changed) {
                groups += (start until index) to (schedule[dayKeys[start]] ?: DaySchedule(false))
                start = index
            }
        }
        return groups.joinToString(" · ") { (range, day) ->
            val days = if (range.first == range.last) shortNames[range.first] else "${shortNames[range.first]}–${shortNames[range.last]}"
            if (!day.open) "$days Closed" else buildString {
                append("$days ${day.openTime}–${day.closeTime}")
                if (day.breakEnabled) append(" (break ${day.breakStart}–${day.breakEnd})")
            }
        }
    }
}
