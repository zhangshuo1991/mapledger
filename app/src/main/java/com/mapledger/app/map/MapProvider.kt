package com.mapledger.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.mapledger.app.place.GeoPoint

/** A spending marker drawn on the map: one per place, colored by category. */
data class MapPin(
    val placeId: String,
    val point: GeoPoint,
    val color: Int,
    val visits: Int,
)

/** Imperative handle to the map, created by a [MapProvider]. */
@Stable
interface MapController {
    fun animateTo(point: GeoPoint, zoom: Float? = null, durationMs: Long = 450)
    /** Pixel (in the map view) the camera target sits at; the center pin is drawn at the same spot. */
    fun setTargetAnchor(xPx: Int, yPx: Int)
    /** Play a drop animation on the pin for [placeId] (after saving an expense). */
    fun bouncePin(placeId: String)
}

interface MapEvents {
    fun onCameraMoving()
    fun onCameraIdle(target: GeoPoint, byUser: Boolean)
    fun onPinClick(placeId: String)
    fun onPoiClick(poiId: String, name: String, point: GeoPoint)
}

/**
 * Pluggable map backend. v1 ships [com.mapledger.app.map.amap.AmapMapProvider];
 * another SDK (MapLibre, Tencent…) only needs to implement this + PlaceSearch + LocationProvider.
 */
interface MapProvider {
    @Composable
    fun Map(
        modifier: Modifier,
        initial: GeoPoint,
        darkTheme: Boolean,
        pins: List<MapPin>,
        myLocation: GeoPoint?,
        events: MapEvents,
        onReady: (MapController) -> Unit,
    )
}

interface LocationProvider {
    /** One-shot current location in the map's datum, or null on failure/timeout. */
    suspend fun current(): GeoPoint?
}
