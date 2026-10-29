package io.github.alexgaignard.powercompare.ble

/**
 * Parses the Cycling Power Measurement characteristic (0x2A63) of the BLE Cycling Power Service.
 *
 * Layout: 2 bytes of flags, then the instantaneous power as a little-endian signed 16-bit value in watts.
 * The optional fields that follow (pedal balance, crank data, ...) are not needed here.
 */
object CyclingPowerParser {
    fun instantaneousPower(data: ByteArray): Int? {
        if (data.size < 4) {
            return null
        }
        val low = data[2].toInt() and 0xFF
        val high = data[3].toInt()
        return (high shl 8) or low
    }
}
