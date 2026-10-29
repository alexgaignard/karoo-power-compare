package io.github.alexgaignard.powercompare.compare

/**
 * Compares two power meters from samples taken once per second.
 *
 * Differences are "B relative to A": a positive value means meter B reads higher.
 * The live difference uses a short rolling average because single 1 Hz samples from two
 * meters are never taken at exactly the same pedal stroke.
 */
class PowerComparator(
    diffWindowSeconds: Int = 3,
    // Below this, a few watts of difference become a huge, meaningless percentage.
    private val minPowerForPercent: Double = 30.0,
) {
    private val diffWindowA = RollingAverage(diffWindowSeconds)
    private val diffWindowB = RollingAverage(diffWindowSeconds)

    // Meter B's own smoothed power only depends on meter B, so a dropout of meter A leaves it running.
    private val powerB3s = RollingAverage(3)
    private val powerB10s = RollingAverage(10)
    private val powerB30s = RollingAverage(30)

    private var rideTotalA = 0.0
    private var rideTotalB = 0.0
    private var rideSamples = 0

    fun addSample(
        powerA: Double?,
        powerB: Double?,
        recording: Boolean,
    ): Comparison {
        addPowerB(powerB)

        val smoothedB = Comparison(
            powerA = powerA,
            powerB = powerB,
            powerB3s = powerB3s.average(),
            powerB10s = powerB10s.average(),
            powerB30s = powerB30s.average(),
        )

        if (powerA == null || powerB == null) {
            diffWindowA.clear()
            diffWindowB.clear()
            return smoothedB.copy(
                rideDiffPercent = rideDiffPercent(),
                rideAveragePowerB = rideAveragePowerB(),
            )
        }

        diffWindowA.add(powerA)
        diffWindowB.add(powerB)

        if (recording) {
            rideTotalA += powerA
            rideTotalB += powerB
            rideSamples++
        }

        val averageA = diffWindowA.average()!!
        val averageB = diffWindowB.average()!!

        return smoothedB.copy(
            diffWatts = averageB - averageA,
            diffPercent = percentDifference(averageA, averageB),
            rideDiffPercent = rideDiffPercent(),
            rideAveragePowerB = rideAveragePowerB(),
        )
    }

    fun resetRide() {
        rideTotalA = 0.0
        rideTotalB = 0.0
        rideSamples = 0
    }

    private fun addPowerB(powerB: Double?) {
        val windows = listOf(powerB3s, powerB10s, powerB30s)
        if (powerB == null) {
            windows.forEach { it.clear() }
        } else {
            windows.forEach { it.add(powerB) }
        }
    }

    private fun percentDifference(
        powerA: Double,
        powerB: Double,
    ): Double? {
        if (powerA < minPowerForPercent) {
            return null
        }
        return (powerB - powerA) / powerA * 100.0
    }

    private fun rideDiffPercent(): Double? {
        if (rideSamples == 0) {
            return null
        }
        return percentDifference(
            rideTotalA / rideSamples,
            rideTotalB / rideSamples,
        )
    }

    private fun rideAveragePowerB(): Double? {
        if (rideSamples == 0) {
            return null
        }
        return rideTotalB / rideSamples
    }
}
