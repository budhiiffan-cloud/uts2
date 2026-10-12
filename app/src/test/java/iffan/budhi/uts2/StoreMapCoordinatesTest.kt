package iffan.budhi.uts2
import org.junit.Assert.*
import org.junit.Test

class StoreMapCoordinatesTest {
    @Test fun equatorAndPrimeMeridianAreAtWorldCenter() {
        val point = StoreMapCoordinates.tilePoint(0.0, 0.0, 16)
        assertEquals(32768.0, point.first, 0.00001)
        assertEquals(32768.0, point.second, 0.00001)
    }
    @Test fun sellerIsProjectedToSouthEastWorldQuadrant() {
        val point = StoreMapCoordinates.tilePoint(StoreMapCoordinates.LATITUDE, StoreMapCoordinates.LONGITUDE, 17)
        assertTrue(point.first > 65536 && point.first < 131072)
        assertTrue(point.second > 65536 && point.second < 131072)
    }
}
