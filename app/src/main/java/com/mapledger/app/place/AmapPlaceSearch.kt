package com.mapledger.app.place

import android.content.Context
import android.util.LruCache
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.mapledger.app.data.Category
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

class PlaceSearchException(val code: Int) : Exception("Amap search error $code")

/** Nearby POIs from Amap Web services via the Android search SDK. Results are cached per ~100m grid cell. */
class AmapPlaceSearch(context: Context) : PlaceSearch {
    companion object { var SEARCH_TIMEOUT_MS = 25_000L }
    private val ctx = context.applicationContext
    private val cache = LruCache<String, List<PoiItem>>(32)

    // Consumer-relevant classes only: food, shopping, life, leisure, health, hotel, scenic, edu/culture, transport.
    private val types = "050000|060000|070000|080000|090000|100000|110000|140000|150000"

    override suspend fun nearby(center: GeoPoint, radiusM: Int): List<Place> {
        val key = "%.3f,%.3f,%d".format(center.lat, center.lng, radiusM)
        val items = cache.get(key) ?: search(center, radiusM).also { cache.put(key, it) }
        return items.map { it.toPlace(center) }.sortedBy { it.distanceM }
    }

    private suspend fun search(center: GeoPoint, radiusM: Int): List<PoiItem> = withTimeout(SEARCH_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val query = PoiSearch.Query("", types, "").apply {
                pageSize = 40
                pageNum = 1
                isDistanceSort = true
                extensions = PoiSearch.EXTENSIONS_BASE
            }
            val t0 = System.currentTimeMillis()
            val search = PoiSearch(ctx, query)
            search.bound = PoiSearch.SearchBound(LatLonPoint(center.lat, center.lng), radiusM, true)
            search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(result: PoiResult?, code: Int) {
                    android.util.Log.i("MapLedger", "poi callback code=$code n=${result?.pois?.size} after ${System.currentTimeMillis() - t0}ms")
                    if (!cont.isActive) return
                    if (code == 1000) cont.resume(result?.pois?.toList().orEmpty())
                    else cont.resumeWithException(PlaceSearchException(code))
                }
                override fun onPoiItemSearched(item: PoiItem?, code: Int) {}
            })
            search.searchPOIAsyn()
        }
    }

    override suspend fun describe(point: GeoPoint): Place {
        val label = runCatching {
            withTimeout(12_000) {
                suspendCancellableCoroutine<String> { cont ->
                    val gs = GeocodeSearch(ctx)
                    gs.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onRegeocodeSearched(r: RegeocodeResult?, code: Int) {
                            if (!cont.isActive) return
                            android.util.Log.i("MapLedger", "regeo callback code=$code")
                            val a = r?.regeocodeAddress
                            val road = a?.streetNumber?.street?.takeIf { it.isNotBlank() }
                                ?: a?.roads?.firstOrNull()?.name
                            val name = when {
                                !a?.aois.isNullOrEmpty() -> a!!.aois.first().aoiName
                                road != null -> "${road}附近"
                                !a?.neighborhood.isNullOrBlank() -> a!!.neighborhood
                                else -> a?.township ?: ""
                            }
                            cont.resume(name.orEmpty())
                        }
                        override fun onGeocodeSearched(r: GeocodeResult?, code: Int) {}
                    })
                    gs.getFromLocationAsyn(RegeocodeQuery(LatLonPoint(point.lat, point.lng), 200f, GeocodeSearch.AMAP))
                }
            }
        }.getOrNull().orEmpty()
        return Place(
            id = "pin:%.5f,%.5f".format(point.lat, point.lng),
            name = label.ifBlank { "地图上的位置" },
            typeName = "自选位置",
            typeCode = "",
            category = Category.OTHER,
            point = point,
            distanceM = 0,
            isCustom = true,
        )
    }

    override suspend fun byId(id: String, fallbackName: String, point: GeoPoint): Place {
        val item = runCatching {
            withTimeout(8_000) {
                suspendCancellableCoroutine<PoiItem?> { cont ->
                    val s = PoiSearch(ctx, PoiSearch.Query("", "", ""))
                    s.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                        override fun onPoiSearched(result: PoiResult?, code: Int) {}
                        override fun onPoiItemSearched(item: PoiItem?, code: Int) {
                            if (cont.isActive) cont.resume(if (code == 1000) item else null)
                        }
                    })
                    s.searchPOIIdAsyn(id)
                }
            }
        }.getOrNull()
        return item?.toPlace(point) ?: Place(
            id = "amap:$id", name = fallbackName, typeName = "", typeCode = "",
            category = Category.OTHER, point = point, distanceM = 0,
        )
    }

    private fun PoiItem.toPlace(from: GeoPoint): Place {
        val p = GeoPoint(latLonPoint.latitude, latLonPoint.longitude)
        val typeLeaf = typeDes?.split(';')?.lastOrNull().orEmpty()
        return Place(
            id = "amap:$poiId",
            name = title.orEmpty(),
            typeName = typeLeaf,
            typeCode = typeCode.orEmpty(),
            category = AmapCategoryMapper.map(typeCode),
            point = p,
            distanceM = if (distance > 0) distance else from.distanceTo(p).roundToInt(),
            address = snippet.orEmpty(),
        )
    }
}
