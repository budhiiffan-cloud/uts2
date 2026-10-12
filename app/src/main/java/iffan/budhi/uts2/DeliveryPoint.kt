package iffan.budhi.uts2

import java.util.Locale

data class DeliveryPoint(val latitude: Double, val longitude: Double) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun display() = String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
    fun mapsUrl() = "https://www.google.com/maps/search/?api=1&query=$latitude%2C$longitude"
    companion object {
        fun parse(latitude: String, longitude: String): DeliveryPoint? {
            val lat = latitude.trim().replace(',', '.').toDoubleOrNull() ?: return null
            val lng = longitude.trim().replace(',', '.').toDoubleOrNull() ?: return null
            return DeliveryPoint(lat, lng).takeIf { it.valid() }
        }
    }
}
