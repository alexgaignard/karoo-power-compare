package io.github.alexgaignard.powercompare.compare

/**
 * One second's snapshot of the two meters. A null field means it cannot be computed right now,
 * usually because one of the meters is not sending data.
 */
data class Comparison(
    val powerA: Double? = null,
    val powerB: Double? = null,
    val diffWatts: Double? = null,
    val diffPercent: Double? = null,
    val rideDiffPercent: Double? = null,
    val rideAveragePowerB: Double? = null,
)
