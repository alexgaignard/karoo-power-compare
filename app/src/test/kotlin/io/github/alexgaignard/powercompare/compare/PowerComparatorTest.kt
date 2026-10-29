package io.github.alexgaignard.powercompare.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerComparatorTest {
    private val delta = 0.001

    @Test
    fun `difference is B minus A over the rolling window`() {
        val comparator = PowerComparator(diffWindowSeconds = 3)

        comparator.addSample(200.0, 210.0, recording = false)
        comparator.addSample(200.0, 210.0, recording = false)
        val result = comparator.addSample(200.0, 210.0, recording = false)

        assertEquals(10.0, result.diffWatts!!, delta)
        assertEquals(5.0, result.diffPercent!!, delta)
    }

    @Test
    fun `window drops samples older than its size`() {
        val comparator = PowerComparator(diffWindowSeconds = 2)

        comparator.addSample(100.0, 500.0, recording = false)
        comparator.addSample(200.0, 200.0, recording = false)
        val result = comparator.addSample(200.0, 200.0, recording = false)

        assertEquals(0.0, result.diffWatts!!, delta)
    }

    @Test
    fun `percent is unavailable at low power`() {
        val comparator = PowerComparator(minPowerForPercent = 30.0)

        val result = comparator.addSample(10.0, 15.0, recording = false)

        assertEquals(5.0, result.diffWatts!!, delta)
        assertNull(result.diffPercent)
    }

    @Test
    fun `smooths meter B over 3, 10 and 30 seconds`() {
        val comparator = PowerComparator()
        repeat(27) { comparator.addSample(200.0, 100.0, recording = false) }

        lateinit var result: Comparison
        repeat(3) { result = comparator.addSample(200.0, 400.0, recording = false) }

        assertEquals(400.0, result.powerB3s!!, delta)
        // last 10 s: 7 × 100 W + 3 × 400 W
        assertEquals(190.0, result.powerB10s!!, delta)
        // last 30 s: 27 × 100 W + 3 × 400 W
        assertEquals(130.0, result.powerB30s!!, delta)
    }

    @Test
    fun `meter B smoothing keeps running when meter A drops out`() {
        val comparator = PowerComparator()
        comparator.addSample(200.0, 100.0, recording = false)

        val result = comparator.addSample(null, 300.0, recording = false)

        assertEquals(200.0, result.powerB3s!!, delta)
        assertNull(result.diffWatts)
    }

    @Test
    fun `meter B dropout clears its smoothing`() {
        val comparator = PowerComparator()
        comparator.addSample(200.0, 100.0, recording = false)

        val result = comparator.addSample(200.0, null, recording = false)

        assertNull(result.powerB3s)
        assertNull(result.powerB30s)
    }

    @Test
    fun `missing meter clears the window and the live difference`() {
        val comparator = PowerComparator(diffWindowSeconds = 3)
        comparator.addSample(100.0, 500.0, recording = false)

        val missing = comparator.addSample(200.0, null, recording = false)
        val afterReconnect = comparator.addSample(200.0, 200.0, recording = false)

        assertNull(missing.diffWatts)
        assertEquals(0.0, afterReconnect.diffWatts!!, delta)
    }

    @Test
    fun `ride average only counts recorded samples`() {
        val comparator = PowerComparator()

        comparator.addSample(100.0, 900.0, recording = false)
        comparator.addSample(200.0, 196.0, recording = true)
        val result = comparator.addSample(200.0, 196.0, recording = true)

        assertEquals(-2.0, result.rideDiffPercent!!, delta)
        assertEquals(196.0, result.rideAveragePowerB!!, delta)
    }

    @Test
    fun `ride average survives a dropout and resets on demand`() {
        val comparator = PowerComparator()
        comparator.addSample(200.0, 220.0, recording = true)

        val duringDropout = comparator.addSample(200.0, null, recording = true)
        comparator.resetRide()
        val afterReset = comparator.addSample(200.0, null, recording = true)

        assertEquals(10.0, duringDropout.rideDiffPercent!!, delta)
        assertNull(afterReset.rideDiffPercent)
    }
}
