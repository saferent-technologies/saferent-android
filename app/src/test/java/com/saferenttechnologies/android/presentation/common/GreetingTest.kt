package com.saferenttechnologies.android.presentation.common

import com.saferenttechnologies.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test fun `greeting by hour`() {
        assertEquals(R.string.greeting_morning, greetingFor(5))
        assertEquals(R.string.greeting_morning, greetingFor(11))
        assertEquals(R.string.greeting_afternoon, greetingFor(12))
        assertEquals(R.string.greeting_evening, greetingFor(17))
        assertEquals(R.string.greeting_evening, greetingFor(2))
    }
}
