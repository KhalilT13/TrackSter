package com.Khalil.trackster.model

import java.util.Calendar

data class SpecialDateSchedule(
    val dateKey: String,
    val title: String,
    val schedule: DaySchedule
) {
    // Purpose: Converts this model into a map suitable for Firestore storage.
    fun toMap(): Map<String, Any> = mapOf(
        "dateKey" to dateKey,
        "title" to title,
        "schedule" to schedule.toMap()
    )

    companion object {
        // Purpose: Converts a Firestore map into a validated app model.
        fun fromMap(dateKey: String, raw: Map<*, *>?): SpecialDateSchedule? {
            if (raw == null) return null
            val schedule = DaySchedule.fromMap(raw["schedule"] as? Map<*, *>) ?: return null
            return SpecialDateSchedule(
                dateKey = raw["dateKey"]?.toString()?.ifBlank { dateKey } ?: dateKey,
                title = raw["title"]?.toString().orEmpty(),
                schedule = schedule
            )
        }
    }
}

object SpecialDateSchedules {
    // Purpose: Converts raw Firestore data into the structured model used by the app.
    fun fromFirestore(raw: Any?): LinkedHashMap<String, SpecialDateSchedule> {
        val source = raw as? Map<*, *> ?: return linkedMapOf()
        return source.entries.mapNotNull { (key, value) ->
            val dateKey = key?.toString() ?: return@mapNotNull null
            SpecialDateSchedule.fromMap(dateKey, value as? Map<*, *>)
        }.sortedBy { it.dateKey }.associateByTo(linkedMapOf()) { it.dateKey }
    }

    // Purpose: Converts app data into the map structure stored in Firestore.
    fun toFirestore(items: Map<String, SpecialDateSchedule>): Map<String, Any> =
        items.toSortedMap().mapValues { it.value.toMap() }

    // Purpose: Returns the effective hours for a date, applying a special-date override when one exists.
    fun effectiveDay(
        weekly: Map<String, DaySchedule>,
        specialDates: Map<String, SpecialDateSchedule>,
        calendar: Calendar,
        dateKey: String
    ): DaySchedule = specialDates[dateKey]?.schedule
        ?: weekly[WeeklySchedule.keyFor(calendar)]
        ?: DaySchedule(false)
}
