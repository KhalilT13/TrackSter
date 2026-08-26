package com.Khalil.trackster

import com.Khalil.trackster.model.AppointmentReferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentReferencesTest {
    @Test fun serviceNameComesFromCurrentCatalogUsingStableId() {
        val catalog = listOf(mapOf("id" to "haircut", "name" to "Updated haircut", "active" to true))
        assertEquals("Updated haircut", AppointmentReferences.serviceName("haircut", catalog))
    }

    @Test fun renamedCustomerComesFromCurrentPublicProfile() {
        assertEquals("New name", AppointmentReferences.customerName(mapOf("displayName" to " New name ")))
    }

    @Test fun missingReferenceDoesNotReuseStaleAppointmentText() {
        assertEquals("", AppointmentReferences.serviceName("removed", emptyList<Any>()))
        assertEquals("", AppointmentReferences.customerName(null))
    }
}
