package iffan.budhi.uts2

import kotlin.math.*

object StoreMapCoordinates {
    const val LATITUDE = -7.3646631
    const val LONGITUDE = 112.7390932
    fun tilePoint(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
        require(latitude.isFinite() && longitude.isFinite() && latitude in -85.0..85.0 && longitude in -180.0..180.0 && zoom in 0..19)
        val count = 2.0.pow(zoom)
        val radians = Math.toRadians(latitude)
        return (longitude + 180.0) / 360.0 * count to (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0 * count
    }
}
