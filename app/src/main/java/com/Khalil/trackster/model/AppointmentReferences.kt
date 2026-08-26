package com.Khalil.trackster.model

/** Resolves mutable appointment labels from their stable Firestore references. */
object AppointmentReferences {
    fun serviceName(serviceId: String, rawCatalog: Any?, legacyNames: List<String> = emptyList()): String =
        ServiceOffering.fromFirestore(rawCatalog, legacyNames)
            .firstOrNull { it.id == serviceId }
            ?.name
            .orEmpty()

    fun customerName(rawProfile: Map<String, Any?>?): String =
        rawProfile?.get("displayName")?.toString()?.trim().orEmpty()
}
