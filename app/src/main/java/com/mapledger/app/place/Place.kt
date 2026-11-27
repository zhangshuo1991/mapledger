package com.mapledger.app.place

import com.mapledger.app.data.Category
import kotlin.math.*

/** Coordinates in the provider's native datum (GCJ-02 for Amap). */
data class GeoPoint(val lat: Double, val lng: Double) {
    fun distanceTo(o: GeoPoint): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(o.lat - lat)
        val dLng = Math.toRadians(o.lng - lng)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(o.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}

data class Place(
    val id: String,
    val name: String,
    val typeName: String,      // human readable sub-type, e.g. "火锅店"
    val typeCode: String,      // provider type code, e.g. Amap "050117"
    val category: Category,    // auto category derived from typeCode
    val point: GeoPoint,
    val distanceM: Int,
    val address: String = "",
    val isCustom: Boolean = false,
)

/** Backend-agnostic place search. Implementations: [AmapPlaceSearch]. */
interface PlaceSearch {
    suspend fun nearby(center: GeoPoint, radiusM: Int = 800): List<Place>
    /** Describe an arbitrary point (pin dropped by the user). */
    suspend fun describe(point: GeoPoint): Place
    /** Resolve a place tapped on the map's own POI layer. */
    suspend fun byId(id: String, fallbackName: String, point: GeoPoint): Place
}

fun formatDistance(m: Int): String = when {
    m < 10 -> "就在这"
    m < 1000 -> "${m}m"
    else -> String.format("%.1fkm", m / 1000.0)
}
