package com.mapledger.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapledger.app.MainViewModel
import com.mapledger.app.SavedEvent
import com.mapledger.app.data.Category
import com.mapledger.app.map.MapController
import com.mapledger.app.map.MapEvents
import com.mapledger.app.map.MapPin
import com.mapledger.app.map.MapProvider
import com.mapledger.app.map.amap.AmapMapProvider
import com.mapledger.app.place.GeoPoint
import com.mapledger.app.place.Place
import com.mapledger.app.ui.components.formatMoney
import com.mapledger.app.ui.screens.*
import com.mapledger.app.ui.theme.LocalPalette
import com.mapledger.app.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val mapProvider: MapProvider = AmapMapProvider()

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()

    val nearby by vm.nearby.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val monthTotal by vm.monthTotal.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val myLocation by vm.myLocation.collectAsStateWithLifecycle()

    var controller by remember { mutableStateOf<MapController?>(null) }
    var cameraMoving by remember { mutableStateOf(false) }
    // Saved across process death so a restored session reopens where the user was looking.
    var center by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<GeoPoint, DoubleArray>(
            save = { doubleArrayOf(it.lat, it.lng) }, restore = { GeoPoint(it[0], it[1]) },
        )
    ) { mutableStateOf(MainViewModel.DEFAULT_CITY) }
    var located by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var suppressIdle by remember { mutableStateOf(false) }
    var pinBusy by remember { mutableStateOf(false) }
    var pinAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Map view is created after the first frame; if we located before it existed, move there now.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (located) c.animateTo(myLocation ?: center, 16.5f, 1L)
    }
    LaunchedEffect(controller, pinAnchor) {
        val c = controller; val a = pinAnchor
        if (c != null && a != null) c.setTargetAnchor(a.first, a.second)
    }

    var amountPlace by remember { mutableStateOf<Place?>(null) }
    var showLedger by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<SavedEvent?>(null) }
    var deleted by remember { mutableStateOf<com.mapledger.app.data.Expense?>(null) }
    var undoGen by remember { mutableIntStateOf(0) }
    LaunchedEffect(deleted) { if (deleted != null) { delay(6000); deleted = null } }

    fun hasPerm() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun locateAndSearch() {
        scope.launch {
            val granted = hasPerm()
            denied = !granted
            val pt = vm.locate(granted)
            located = true
            center = pt
            controller?.animateTo(pt, 16.5f, 600)
            vm.searchAround(pt, force = true)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locateAndSearch() }
    LaunchedEffect(Unit) {
        when {
            // Fresh start, or restored after process death: get a current fix (the user may have moved).
            hasPerm() -> locateAndSearch()
            !located -> permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else -> vm.searchAround(center)
        }
    }

    LaunchedEffect(Unit) {
        vm.saved.collect { ev ->
            toast = ev
            delay(260)
            controller?.bouncePin(ev.placeId)
            delay(1900)
            if (toast == ev) toast = null
        }
    }

    val pins = remember(summaries) {
        summaries.map { MapPin(it.placeId, GeoPoint(it.lat, it.lng), Category.of(it.category).color.toArgb(), it.visits) }
    }

    fun openAmount(place: Place) {
        suppressIdle = true
        controller?.animateTo(place.point, null, 380)
        amountPlace = place
    }

    val events = remember {
        object : MapEvents {
            override fun onCameraMoving() { cameraMoving = true }
            override fun onCameraIdle(target: GeoPoint, byUser: Boolean) {
                cameraMoving = false
                if (suppressIdle) { suppressIdle = false; return }
                if (!byUser && located && target.distanceTo(center) > 150) {
                    // Stale idle from the map's initial camera (map created before the first fix
                    // arrived): move to where we actually are instead of searching the old spot.
                    controller?.animateTo(center, 16.5f, 1L)
                    return
                }
                center = target
                if (located) vm.searchAround(target)
            }
            override fun onPinClick(placeId: String) { detailId = placeId }
            override fun onPoiClick(poiId: String, name: String, point: GeoPoint) {
                scope.launch { openAmount(vm.placeById(poiId, name, point)) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
        mapProvider.Map(
            modifier = Modifier.fillMaxSize(),
            initial = center,
            darkTheme = dark,
            pins = pins,
            myLocation = myLocation,
            events = events,
            onReady = { controller = it },
        )

        MapChrome(
            nearby = nearby,
            monthTotalCents = monthTotal,
            cameraMoving = cameraMoving,
            locating = locating,
            locationDenied = denied,
            visits = { id -> summaries.firstOrNull { it.placeId == id }?.visits ?: 0 },
            categoryOf = vm::categoryFor,
            pinBusy = pinBusy,
            onPlaceClick = { openAmount(it) },
            onPinHere = {
                if (!pinBusy) scope.launch {
                    pinBusy = true
                    val place = vm.describe(center)
                    pinBusy = false
                    amountPlace = place
                }
            },
            onLocate = {
                if (hasPerm()) locateAndSearch()
                else if (denied && located) {
                    // Second ask goes to system settings if the dialog is suppressed.
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    }
                } else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onRetry = { vm.searchAround(center, force = true) },
            onOpenLedger = { showLedger = true },
            onPinAnchor = { x, y -> pinAnchor = x to y },
        )

        val slideSpec = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
        AnimatedVisibility(
            showLedger,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(tween(200, delayMillis = 80)),
        ) {
            LedgerScreen(expenses, monthTotal, onBack = { showLedger = false }, onOpenPlace = { detailId = it }, onDelete = { vm.delete(it); deleted = it }, undoGeneration = undoGen)
        }

        var lastDetail by remember { mutableStateOf<String?>(null) }
        if (detailId != null) lastDetail = detailId
        AnimatedVisibility(
            detailId != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(tween(200, delayMillis = 80)),
        ) {
            val id = lastDetail
            val summary = summaries.firstOrNull { it.placeId == id }
            val visits by remember(id) { vm.expensesAt(id ?: "") }.collectAsStateWithLifecycle(emptyList())
            PlaceDetailScreen(
                summary = summary, visits = visits,
                onBack = { detailId = null },
                onRecord = {
                    summary?.let {
                        val place = Place(
                            id = it.placeId, name = it.placeName, typeName = it.placeType, typeCode = "",
                            category = Category.of(it.category), point = GeoPoint(it.lat, it.lng), distanceM = 0,
                            isCustom = it.placeId.startsWith("pin:"),
                        )
                        amountPlace = place
                    }
                },
                onShowOnMap = {
                    summary?.let {
                        detailId = null; showLedger = false
                        suppressIdle = false
                        center = GeoPoint(it.lat, it.lng)
                        controller?.animateTo(center, 17f, 600)
                    }
                },
            )
        }

        val ap = amountPlace
        AmountSheet(
            place = ap,
            initialCategory = ap?.let { vm.categoryFor(it) } ?: Category.OTHER,
            autoCategory = ap?.category ?: Category.OTHER,
            remembered = ap != null && vm.hasOverride(ap),
            onDismiss = { amountPlace = null },
            onSave = { cents, cat ->
                ap?.let { vm.save(it, cents, cat) }
                amountPlace = null
            },
        )

        SavedToast(toast, Modifier.align(Alignment.TopCenter))
        UndoPill(deleted, Modifier.align(Alignment.BottomCenter)) {
            deleted?.let { vm.restore(it); undoGen++ }; deleted = null
        }
    }

    BackHandler(amountPlace != null || detailId != null || showLedger) {
        when {
            amountPlace != null -> amountPlace = null
            detailId != null -> detailId = null
            else -> showLedger = false
        }
    }
}

@Composable
private fun SavedToast(ev: SavedEvent?, modifier: Modifier) {
    val p = LocalPalette.current
    var last by remember { mutableStateOf(ev) }
    if (ev != null) last = ev
    AnimatedVisibility(
        visible = ev != null, modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.62f, stiffness = 420f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 500f)) { -it * 2 } + fadeOut(),
    ) {
        val e = last ?: return@AnimatedVisibility
        Row(
            Modifier.statusBarsPadding().padding(top = 66.dp) // below the top bar, never over the month pill
                .shadow(16.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(CircleShape).background(p.surface).padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(e.category.color), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("已记录 ¥${formatMoney(e.amountCents)} · ${e.category.label}", style = Type.bodyStrong, color = p.text)
        }
    }
}

@Composable
private fun UndoPill(e: com.mapledger.app.data.Expense?, modifier: Modifier, onUndo: () -> Unit) {
    val p = LocalPalette.current
    var last by remember { mutableStateOf(e) }
    if (e != null) last = e
    AnimatedVisibility(
        visible = e != null, modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { it * 2 } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 500f)) { it * 2 } + fadeOut(),
    ) {
        val d = last ?: return@AnimatedVisibility
        Row(
            Modifier.navigationBarsPadding().padding(bottom = 20.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(CircleShape).background(p.text).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已删除 ${if (d.placeName.length > 9) d.placeName.take(8) + "…" else d.placeName} ¥${formatMoney(d.amountCents)}",
                style = Type.body, color = p.bg, maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.clip(CircleShape).background(p.bg.copy(alpha = 0.14f))
                    .clickable(onClick = onUndo).padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("撤销", style = Type.bodyStrong, color = p.bg) }
        }
    }
}

