package io.github.alexgaignard.powercompare.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RollingAverageTest {
    @Test
    fun `is empty until a value is added`() {
        assertNull(RollingAverage(3).average())
    }

    @Test
    fun `averages what it has before the window is full`() {
        val average = RollingAverage(3)
        average.add(100.0)
        average.add(200.0)

        assertEquals(150.0, average.average()!!, 0.0)
    }

    @Test
    fun `keeps only the last values`() {
        val average = RollingAverage(2)
        average.add(1000.0)
        average.add(100.0)
        average.add(200.0)

        assertEquals(150.0, average.average()!!, 0.0)
    }
}
