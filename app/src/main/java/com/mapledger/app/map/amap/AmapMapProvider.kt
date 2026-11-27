package com.mapledger.app.map.amap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.animation.ScaleAnimation
import com.mapledger.app.map.*
import com.mapledger.app.place.GeoPoint

class AmapMapProvider : MapProvider {
    @Composable
    override fun Map(
        modifier: Modifier,
        initial: GeoPoint,
        darkTheme: Boolean,
        pins: List<MapPin>,
        myLocation: GeoPoint?,
        events: MapEvents,
        onReady: (MapController) -> Unit,
    ) {
        // Defer the (native, heavy) map view creation until after the first frame so the UI chrome appears instantly.
        var create by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { withFrameNanos { }; create = true }
        if (!create) { androidx.compose.foundation.layout.Box(modifier); return }
        MapContent(modifier, initial, darkTheme, pins, myLocation, events, onReady)
    }

    @Composable
    private fun MapContent(
        modifier: Modifier,
        initial: GeoPoint,
        darkTheme: Boolean,
        pins: List<MapPin>,
        myLocation: GeoPoint?,
        events: MapEvents,
        onReady: (MapController) -> Unit,
    ) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val currentEvents by rememberUpdatedState(events)
        val holder = remember { AmapHolder(context, initial) }

        DisposableEffect(lifecycle) {
            val obs = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_RESUME -> holder.view.onResume()
                    Lifecycle.Event.ON_PAUSE -> holder.view.onPause()
                    else -> {}
                }
            }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs); holder.view.onDestroy() }
        }
        LaunchedEffect(Unit) {
            holder.bind(object : MapEvents {
                override fun onCameraMoving() = currentEvents.onCameraMoving()
                override fun onCameraIdle(target: GeoPoint, byUser: Boolean) = currentEvents.onCameraIdle(target, byUser)
                override fun onPinClick(placeId: String) = currentEvents.onPinClick(placeId)
                override fun onPoiClick(poiId: String, name: String, point: GeoPoint) = currentEvents.onPoiClick(poiId, name, point)
            })
            onReady(holder)
        }
        LaunchedEffect(darkTheme) {
            holder.map.mapType = if (darkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
        }
        LaunchedEffect(pins) { holder.syncPins(pins) }
        LaunchedEffect(myLocation) { holder.setMyLocation(myLocation) }

        AndroidView(factory = { holder.view }, modifier = modifier)
    }
}

private class AmapHolder(context: Context, initial: GeoPoint) : MapController {
    val view = TextureMapView(context).apply { onCreate(Bundle()) }
    val map: AMap = view.map
    private val density = context.resources.displayMetrics.density
    private val markers = HashMap<String, Marker>()
    private val pinState = HashMap<String, MapPin>()
    private val iconCache = HashMap<Long, BitmapDescriptor>()
    private var userGesture = false
    private var moving = false

    init {
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isScaleControlsEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initial.lat, initial.lng), 16.5f))
    }

    fun bind(events: MapEvents) {
        map.setOnMapLoadedListener { android.util.Log.i("MapLedger", "amap map loaded") }
        map.setOnMapTouchListener { e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) userGesture = true }
        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(p: CameraPosition?) {
                if (!moving) { moving = true; events.onCameraMoving() }
            }
            override fun onCameraChangeFinish(p: CameraPosition?) {
                moving = false
                val t = p?.target ?: return
                android.util.Log.d("MapLedger", "camera idle ${t.latitude},${t.longitude} z=${p.zoom} user=$userGesture")
                events.onCameraIdle(GeoPoint(t.latitude, t.longitude), userGesture)
                userGesture = false
            }
        })
        map.setOnMarkerClickListener { m -> (m.`object` as? String)?.let(events::onPinClick); true }
        map.setOnPOIClickListener { poi ->
            val c = poi.coordinate
            events.onPoiClick(poi.poiId, poi.name, GeoPoint(c.latitude, c.longitude))
        }
    }

    override fun animateTo(point: GeoPoint, zoom: Float?, durationMs: Long) {
        userGesture = false
        val target = LatLng(point.lat, point.lng)
        val update = if (zoom != null) CameraUpdateFactory.newLatLngZoom(target, zoom)
        else CameraUpdateFactory.changeLatLng(target)
        map.animateCamera(update, durationMs, null)
    }

    override fun setTargetAnchor(xPx: Int, yPx: Int) {
        map.setPointToCenter(xPx, yPx)
    }

    override fun bouncePin(placeId: String) {
        val m = markers[placeId] ?: return
        m.setAnimation(ScaleAnimation(0.2f, 1f, 0.2f, 1f).apply {
            setDuration(520)
            setInterpolator(OvershootInterpolator(3f))
        })
        m.startAnimation()
    }

    fun syncPins(pins: List<MapPin>) {
        val incoming = pins.associateBy { it.placeId }
        (markers.keys - incoming.keys).forEach { markers.remove(it)?.remove(); pinState.remove(it) }
        for (pin in pins) {
            val old = pinState[pin.placeId]
            if (old == pin) continue
            val icon = icon(pin.color, sizeFor(pin.visits))
            val existing = markers[pin.placeId]
            if (existing != null) {
                existing.setIcon(icon); existing.position = LatLng(pin.point.lat, pin.point.lng)
            } else {
                val m = map.addMarker(
                    MarkerOptions().position(LatLng(pin.point.lat, pin.point.lng)).icon(icon).anchor(0.5f, 0.5f)
                )
                m.`object` = pin.placeId
                markers[pin.placeId] = m
            }
            pinState[pin.placeId] = pin
        }
    }

    private var meMarker: Marker? = null
    fun setMyLocation(p: GeoPoint?) {
        if (p == null) { meMarker?.remove(); meMarker = null; return }
        val ll = LatLng(p.lat, p.lng)
        val m = meMarker
        if (m != null) { m.position = ll; return }
        meMarker = map.addMarker(MarkerOptions().position(ll).icon(meIcon()).anchor(0.5f, 0.5f).zIndex(-1f))
    }

    /** Classic "you are here": accent dot, white ring, soft halo. */
    private fun meIcon(): BitmapDescriptor {
        val r = 7 * density; val halo = 18 * density
        val dim = (2 * halo).toInt()
        val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val cx = dim / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x261F6FEB; c.drawCircle(cx, cx, halo, p)
        p.color = 0xFFFFFFFF.toInt(); p.setShadowLayer(2 * density, 0f, 0.6f * density, 0x40000000); c.drawCircle(cx, cx, r + 2.5f * density, p)
        p.clearShadowLayer(); p.color = 0xFF1F6FEB.toInt(); c.drawCircle(cx, cx, r, p)
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun sizeFor(visits: Int) = when { visits >= 8 -> 30; visits >= 3 -> 26; else -> 22 }

    /** A soft dot: category color, white ring, faint shadow. Cached per (color,size). */
    private fun icon(color: Int, sizeDp: Int): BitmapDescriptor {
        val key = (color.toLong() shl 8) or sizeDp.toLong()
        return iconCache.getOrPut(key) {
            val pad = 4 * density
            val r = sizeDp * density / 2
            val dim = (2 * (r + pad)).toInt()
            val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = dim / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = 0x33000000; p.setShadowLayer(3 * density, 0f, 1.2f * density, 0x40000000)
            c.drawCircle(cx, cx, r, p)
            p.clearShadowLayer(); p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(cx, cx, r, p)
            p.color = color
            c.drawCircle(cx, cx, r - 2.4f * density, p)
            // White ¥ glyph so ledger pins read clearly against Amap's own POI icons.
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            p.textSize = r * 0.95f
            val fm = p.fontMetrics
            c.drawText("¥", cx, cx - (fm.ascent + fm.descent) / 2f, p)
            BitmapDescriptorFactory.fromBitmap(bmp)
        }
    }
}
