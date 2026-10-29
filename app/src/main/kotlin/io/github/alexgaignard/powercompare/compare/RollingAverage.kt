package io.github.alexgaignard.powercompare.compare

/** Average of the last [size] values added, or of all of them until [size] is reached. */
class RollingAverage(private val size: Int) {
    private val values = ArrayDeque<Double>()

    fun add(value: Double) {
        values.addLast(value)
        if (values.size > size) {
            values.removeFirst()
        }
    }

    fun clear() {
        values.clear()
    }

    fun average(): Double? {
        if (values.isEmpty()) {
            return null
        }
        return values.average()
    }
}
