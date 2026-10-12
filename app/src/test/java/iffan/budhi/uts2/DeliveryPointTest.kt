package iffan.budhi.uts2

import org.junit.Assert.*
import org.junit.Test

class DeliveryPointTest {
    @Test fun acceptsGpsValuesAndIndonesianDecimalInput() {
        assertEquals(DeliveryPoint(-6.917464, 107.619123), DeliveryPoint.parse("-6,917464", "107,619123"))
        assertEquals(DeliveryPoint(0.0, 0.0), DeliveryPoint.parse("0", "0"))
    }
    @Test fun rejectsMissingNonFiniteAndOutOfRangeCoordinates() {
        listOf("" to "107", "-6" to "", "91" to "107", "-6" to "181", "NaN" to "107", "-6" to "Infinity").forEach {
            assertNull(DeliveryPoint.parse(it.first, it.second))
        }
    }
    @Test fun mapsLinkUsesBothCoordinates() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=-6.0%2C107.0", DeliveryPoint(-6.0, 107.0).mapsUrl())
    }
}
