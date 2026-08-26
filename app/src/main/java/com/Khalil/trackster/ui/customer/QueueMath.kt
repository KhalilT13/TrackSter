package com.Khalil.trackster.ui.customer

/** Pure queue calculations kept separate so edge cases stay easy to test. */
internal object QueueMath {
    // Purpose: Calculates how many queue numbers remain ahead of the customer.
    fun peopleAhead(queueNumber: Int, currentServingNumber: Int): Int {
        return (queueNumber - currentServingNumber - 1).coerceAtLeast(0)
    }

    // Purpose: Calculates estimated wait time from people ahead and configured turn length.
    fun estimatedMinutes(peopleAhead: Int, minutesPerTurn: Int = 15): Int {
        return peopleAhead.coerceAtLeast(0) * minutesPerTurn.coerceAtLeast(0)
    }
}
