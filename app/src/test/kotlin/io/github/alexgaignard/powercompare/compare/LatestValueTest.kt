package io.github.alexgaignard.powercompare.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestValueTest {
    private var now = 0L
    private val latest = LatestValue(maxAgeMillis = 3_000, clock = { now })

    @Test
    fun `is empty before the first reading`() {
        assertNull(latest.get())
    }

    @Test
    fun `returns a fresh reading`() {
        latest.set(250.0)
        now = 3_000

        assertEquals(250.0, latest.get()!!, 0.0)
    }

    @Test
    fun `expires a stale reading`() {
        latest.set(250.0)
        now = 3_001

        assertNull(latest.get())
    }
}
