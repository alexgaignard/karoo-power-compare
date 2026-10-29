package io.github.alexgaignard.powercompare.compare

/**
 * The most recent reading from a meter, which expires so that a silent or disconnected
 * meter reads as "no data" instead of repeating its last value forever.
 */
class LatestValue(
    private val maxAgeMillis: Long = 3_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var value: Double? = null

    @Volatile
    private var updatedAt = 0L

    fun set(newValue: Double?) {
        value = newValue
        updatedAt = clock()
    }

    fun get(): Double? {
        if (clock() - updatedAt > maxAgeMillis) {
            return null
        }
        return value
    }
}
