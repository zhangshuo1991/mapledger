package com.mapledger.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.data.*
import com.mapledger.app.map.LocationProvider
import com.mapledger.app.map.amap.AmapLocationProvider
import com.mapledger.app.place.AmapPlaceSearch
import com.mapledger.app.place.GeoPoint
import com.mapledger.app.place.Place
import com.mapledger.app.place.PlaceSearch
import com.mapledger.app.place.PlaceSearchException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Immutable
data class NearbyState(
    val loading: Boolean = true,
    val places: List<Place> = emptyList(),
    val error: String? = null,
    val center: GeoPoint? = null,
)

@Immutable
data class SavedEvent(val placeId: String, val amountCents: Long, val category: Category, val nonce: Long)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        /** Fallback when location is denied/unavailable: 上海人民广场 (GCJ-02). */
        val DEFAULT_CITY = GeoPoint(31.2304, 121.4737)
    }

    private val dao = LedgerDb.get(app).dao()
    private val search: PlaceSearch = AmapPlaceSearch(app)
    private val location: LocationProvider = AmapLocationProvider(app)

    val expenses = dao.expenses().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val summaries = dao.placeSummaries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val overrides = dao.overrides()
        .map { list -> list.associate { it.placeId to Category.of(it.category) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val monthTotal = dao.totalSince(monthStart()).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _nearby = MutableStateFlow(NearbyState())
    val nearby: StateFlow<NearbyState> = _nearby

    private val _myLocation = MutableStateFlow<GeoPoint?>(null)
    val myLocation: StateFlow<GeoPoint?> = _myLocation

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating

    private val _saved = MutableSharedFlow<SavedEvent>(extraBufferCapacity = 1)
    val saved: SharedFlow<SavedEvent> = _saved

    private var searchJob: Job? = null
    private var lastSearch: GeoPoint? = null

    /** Returns the location to center on (real fix, or default city). */
    suspend fun locate(hasPermission: Boolean): GeoPoint {
        if (!hasPermission) return DEFAULT_CITY
        _locating.value = true
        // The first fix after a cold start (or right after the permission grant) can take longer than
        // one attempt allows; try once more before falling back to the default city.
        val p = location.current() ?: run { Log.w("MapLedger", "locate timed out, retrying once"); location.current() }
        Log.i("MapLedger", "locate -> ${p ?: "fallback"}")
        _locating.value = false
        if (p != null) _myLocation.value = p
        return p ?: DEFAULT_CITY
    }

    fun searchAround(center: GeoPoint, force: Boolean = false) {
        val last = lastSearch
        if (!force && last != null && last.distanceTo(center) < 120 && _nearby.value.error == null) return
        lastSearch = center
        searchJob?.cancel()
        _nearby.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            // The very first Amap request warms up its crypto/network stack and can be slow on weak
            // devices; one quiet retry on timeout avoids showing an error for that cold start.
            runCatching {
                try { search.nearby(center) }
                catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w("MapLedger", "nearby timeout, retrying once"); search.nearby(center)
                } catch (e: PlaceSearchException) {
                    // Transient network / IO errors (18xx, 1902): retry once before showing an error.
                    if (e.code !in 1800..1902) throw e
                    Log.w("MapLedger", "nearby io error ${e.code}, retrying once"); delay(800); search.nearby(center)
                }
            }
                .onSuccess { list -> Log.i("MapLedger", "nearby ${list.size} @ $center"); _nearby.value = NearbyState(false, list, null, center) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                    Log.w("MapLedger", "nearby failed: $e")
                    val msg = when ((e as? PlaceSearchException)?.code ?: -1) {
                        in 1800..1902 -> "网络不太稳定，点重试"
                        1008, 1001, 1002 -> "地图服务鉴权失败"
                        -1 -> "加载超时，点重试"
                        else -> "附近地点加载失败 (${(e as PlaceSearchException).code})"
                    }
                    _nearby.value = NearbyState(false, emptyList(), msg, center)
                }
        }
    }

    suspend fun describe(point: GeoPoint): Place {
        val p = search.describe(point)
        if (p.name != "地图上的位置") return p
        // Reverse geocode unavailable/slow: name the pin after the closest known POI instead.
        val near = _nearby.value.places.minByOrNull { it.point.distanceTo(point) }
            ?.takeIf { it.point.distanceTo(point) < 150 }
        return if (near != null) p.copy(name = "${near.name}附近") else p
    }
    suspend fun placeById(id: String, name: String, point: GeoPoint): Place = search.byId(id, name, point)

    /** Category for a place: the user's earlier choice wins over the POI type. */
    fun categoryFor(place: Place): Category = overrides.value[place.id] ?: place.category

    /** True when the category comes from the user's earlier choice at this place, not the POI type. */
    fun hasOverride(place: Place): Boolean = overrides.value.containsKey(place.id)

    fun visitsAt(placeId: String): Int = summaries.value.firstOrNull { it.placeId == placeId }?.visits ?: 0

    fun save(place: Place, amountCents: Long, category: Category) {
        viewModelScope.launch {
            if (category != place.category || overrides.value.containsKey(place.id)) {
                dao.upsertOverride(PlaceOverride(place.id, category.name))
            }
            dao.insert(
                Expense(
                    amountCents = amountCents, category = category.name, placeId = place.id,
                    placeName = place.name, placeType = place.typeName,
                    lat = place.point.lat, lng = place.point.lng, createdAt = System.currentTimeMillis(),
                )
            )
            _saved.tryEmit(SavedEvent(place.id, amountCents, category, System.nanoTime()))
        }
    }

    fun delete(e: Expense) = viewModelScope.launch { dao.delete(e); Log.i("MapLedger", "deleted expense ${e.id}") }

    /** Puts a just-deleted expense back (same id, so it lands in the same place in the list). */
    fun restore(e: Expense) = viewModelScope.launch { dao.insert(e); Log.i("MapLedger", "restored expense ${e.id}") }

    fun expensesAt(placeId: String) = dao.expensesAt(placeId)

    private fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
