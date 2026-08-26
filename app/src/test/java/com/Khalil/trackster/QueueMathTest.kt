package com.Khalil.trackster

import com.Khalil.trackster.ui.customer.QueueMath
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueMathTest {

    @Test
    // Purpose: Verifies the people-ahead calculation before service begins.
    fun peopleAhead_beforeAnyCustomerIsCalled() {
        assertEquals(3, QueueMath.peopleAhead(queueNumber = 4, currentServingNumber = 0))
    }

    @Test
    // Purpose: Verifies that people ahead decreases as earlier numbers are served.
    fun peopleAhead_afterEarlierNumbersAreCalled() {
        assertEquals(1, QueueMath.peopleAhead(queueNumber = 4, currentServingNumber = 2))
    }

    @Test
    // Purpose: Verifies that the people-ahead calculation never returns a negative value.
    fun peopleAhead_neverBecomesNegative() {
        assertEquals(0, QueueMath.peopleAhead(queueNumber = 2, currentServingNumber = 5))
    }

    @Test
    // Purpose: Verifies that wait estimation uses the configured minutes per turn.
    fun estimatedMinutes_usesConfiguredTurnLength() {
        assertEquals(30, QueueMath.estimatedMinutes(peopleAhead = 2, minutesPerTurn = 15))
    }
}
