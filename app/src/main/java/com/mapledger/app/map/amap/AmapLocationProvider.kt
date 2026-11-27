package com.mapledger.app.map.amap

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng
import com.mapledger.app.map.LocationProvider
import com.mapledger.app.place.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Amap location SDK returns GCJ-02 directly. Falls back to the platform GPS fix converted to GCJ-02. */
class AmapLocationProvider(context: Context) : LocationProvider {
    private val ctx = context.applicationContext

    override suspend fun current(): GeoPoint? =
        withTimeoutOrNull(10_000) { amap() } ?: platformFallback()

    private suspend fun amap(): GeoPoint? = suspendCancellableCoroutine { cont ->
        val client = try { AMapLocationClient(ctx) } catch (e: Exception) {
            cont.resume(null); return@suspendCancellableCoroutine
        }
        client.setLocationOption(AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
            isOnceLocationLatest = true
            isNeedAddress = false
            isLocationCacheEnable = false
            httpTimeOut = 8000
        })
        client.setLocationListener { loc ->
            client.stopLocation(); client.onDestroy()
            if (!cont.isActive) return@setLocationListener
            if (loc != null && loc.errorCode == 0) cont.resume(GeoPoint(loc.latitude, loc.longitude))
            else {
                Log.w("MapLedger", "Amap location failed: ${loc?.errorCode} ${loc?.errorInfo}")
                cont.resume(null)
            }
        }
        cont.invokeOnCancellation { runCatching { client.stopLocation(); client.onDestroy() } }
        client.startLocation()
    }

    @SuppressLint("MissingPermission")
    private fun platformFallback(): GeoPoint? = runCatching {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        val gcj = CoordinateConverter(ctx).from(CoordinateConverter.CoordType.GPS)
            .coord(LatLng(loc.latitude, loc.longitude)).convert()
        GeoPoint(gcj.latitude, gcj.longitude)
    }.getOrNull()
}
