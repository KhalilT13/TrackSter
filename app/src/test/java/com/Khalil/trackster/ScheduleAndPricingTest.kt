package com.Khalil.trackster

import com.Khalil.trackster.model.DaySchedule
import com.Khalil.trackster.model.RevenueEstimator
import com.Khalil.trackster.model.ServiceOffering
import com.Khalil.trackster.model.SpecialDateSchedule
import com.Khalil.trackster.model.SpecialDateSchedules
import com.Khalil.trackster.model.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAndPricingTest {
    @Test fun fixedAndRangePricesHaveCustomerFriendlyLabels() {
        assertEquals("₪50", ServiceOffering(name = "Hair & Beard", priceType = ServiceOffering.PRICE_FIXED, minimumPrice = 50.0).priceLabel())
        assertEquals("₪200–₪800", ServiceOffering(name = "Treatment", priceType = ServiceOffering.PRICE_RANGE, minimumPrice = 200.0, maximumPrice = 800.0).priceLabel())
        assertEquals("Contact for price", ServiceOffering(name = "Consultation").priceLabel())
    }

    @Test fun estimatedRevenueUsesFixedPriceAndRangeMidpoint() {
        assertEquals(
            50.0,
            RevenueEstimator.estimate(ServiceOffering.PRICE_FIXED, 50, null, null),
            0.001
        )
        assertEquals(
            575.0,
            RevenueEstimator.estimate(ServiceOffering.PRICE_RANGE, 250, 900, null),
            0.001
        )
    }

    @Test fun estimatedRevenueSupportsLegacyPriceLabelsAndUnknownPrices() {
        assertEquals(
            575.0,
            RevenueEstimator.estimate(null, null, null, "₪250–₪900"),
            0.001
        )
        assertEquals(
            0.0,
            RevenueEstimator.estimate(ServiceOffering.PRICE_CONTACT, null, null, "Contact for price"),
            0.001
        )
    }

    @Test fun breakRemovesEveryOverlappingAppointment() {
        val day = DaySchedule(true, "09:00", "17:00", true, "13:00", "14:00")
        val slots = WeeklySchedule.slots(day, durationMinutes = 60)
        assertTrue("12:00" in slots)
        assertFalse("12:30" in slots)
        assertFalse("13:00" in slots)
        assertFalse("13:30" in slots)
        assertTrue("14:00" in slots)
    }

    @Test fun closedDayAndAppointmentsPastClosingHaveNoSlot() {
        assertTrue(WeeklySchedule.slots(DaySchedule(false), 30).isEmpty())
        val slots = WeeklySchedule.slots(DaySchedule(true, "09:00", "18:00"), 60)
        assertTrue("17:00" in slots)
        assertFalse("17:30" in slots)
    }

    @Test fun invalidBreakOutsideWorkingHoursIsRejected() {
        assertFalse(WeeklySchedule.isValid(DaySchedule(true, "09:00", "17:00", true, "08:00", "10:00")))
    }

    @Test fun specialClosedDateOverridesNormallyOpenWeekday() {
        val calendar = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 16) }
        val special = mapOf("2026-08-16" to SpecialDateSchedule("2026-08-16", "Holiday", DaySchedule(false)))
        assertFalse(SpecialDateSchedules.effectiveDay(WeeklySchedule.default(), special, calendar, "2026-08-16").open)
    }

    @Test fun specialCustomHoursOverrideNormallyClosedDay() {
        val calendar = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.AUGUST, 14) }
        val custom = DaySchedule(true, "10:00", "13:00")
        val special = mapOf("2026-08-14" to SpecialDateSchedule("2026-08-14", "Special hours", custom))
        assertEquals(listOf("10:00", "10:30", "11:00", "11:30", "12:00", "12:30"),
            WeeklySchedule.slots(SpecialDateSchedules.effectiveDay(WeeklySchedule.default(), special, calendar, "2026-08-14"), 30))
    }
}
