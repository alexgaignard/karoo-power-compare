package io.github.alexgaignard.powercompare.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CyclingPowerParserTest {
    @Test
    fun `reads little-endian instantaneous power`() {
        // flags 0x0000, power 0x012C = 300 W
        val data = byteArrayOf(0x00, 0x00, 0x2C, 0x01)

        assertEquals(300, CyclingPowerParser.instantaneousPower(data))
    }

    @Test
    fun `ignores optional fields after the power value`() {
        // flags with pedal balance present, power 250 W, balance byte
        val data = byteArrayOf(0x01, 0x00, 0xFA.toByte(), 0x00, 0x64)

        assertEquals(250, CyclingPowerParser.instantaneousPower(data))
    }

    @Test
    fun `reads power above 255 W with the low byte sign bit set`() {
        // 0x03E8 = 1000 W, low byte 0xE8 is negative as a signed byte
        val data = byteArrayOf(0x00, 0x00, 0xE8.toByte(), 0x03)

        assertEquals(1000, CyclingPowerParser.instantaneousPower(data))
    }

    @Test
    fun `returns null for a truncated packet`() {
        assertNull(CyclingPowerParser.instantaneousPower(byteArrayOf(0x00, 0x00, 0x2C)))
    }
}
