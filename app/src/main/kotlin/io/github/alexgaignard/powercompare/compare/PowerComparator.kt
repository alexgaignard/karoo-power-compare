package io.github.alexgaignard.powercompare.compare

/**
 * Compares two power meters from samples taken once per second.
 *
 * Differences are "B relative to A": a positive value means meter B reads higher.
 * The live difference uses a short rolling average because single 1 Hz samples from two
 * meters are never taken at exactly the same pedal stroke.
 */
class PowerComparator(
    private val windowSeconds: Int = 3,
    // Below this, a few watts of difference become a huge, meaningless percentage.
    private val minPowerForPercent: Double = 30.0,
) {
    private val windowA = ArrayDeque<Double>()
    private val windowB = ArrayDeque<Double>()

    private var rideTotalA = 0.0
    private var rideTotalB = 0.0
    private var rideSamples = 0

    fun addSample(
        powerA: Double?,
        powerB: Double?,
        recording: Boolean,
    ): Comparison {
        if (powerA == null || powerB == null) {
            windowA.clear()
            windowB.clear()
            return Comparison(
                powerA = powerA,
                powerB = powerB,
                rideDiffPercent = rideDiffPercent(),
                rideAveragePowerB = rideAveragePowerB(),
            )
        }

        addToWindow(windowA, powerA)
        addToWindow(windowB, powerB)

        if (recording) {
            rideTotalA += powerA
            rideTotalB += powerB
            rideSamples++
        }

        val averageA = windowA.average()
        val averageB = windowB.average()

        return Comparison(
            powerA = powerA,
            powerB = powerB,
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

    private fun addToWindow(
        window: ArrayDeque<Double>,
        power: Double,
    ) {
        window.addLast(power)
        if (window.size > windowSeconds) {
            window.removeFirst()
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
