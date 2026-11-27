package com.mapledger.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mapledger.app.NearbyState
import com.mapledger.app.data.Category
import com.mapledger.app.place.Place
import com.mapledger.app.place.formatDistance
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.LocalPalette
import com.mapledger.app.ui.theme.Type
import kotlin.math.roundToInt

/** Floating chrome over the map: top bar, center pin, locate button and the nearby sheet. */
@Composable
fun MapChrome(
    nearby: NearbyState,
    monthTotalCents: Long,
    cameraMoving: Boolean,
    locating: Boolean,
    locationDenied: Boolean,
    visits: (String) -> Int,
    categoryOf: (Place) -> Category,
    pinBusy: Boolean,
    onPlaceClick: (Place) -> Unit,
    onPinHere: () -> Unit,
    onLocate: () -> Unit,
    onRetry: () -> Unit,
    onOpenLedger: () -> Unit,
    onPinAnchor: (x: Int, y: Int) -> Unit,
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val sheet = rememberSheetState()
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val insets = WindowInsets.systemBars
    val topInset = insets.getTop(density)
    val peekPx = with(density) { 300.dp.toPx() }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        if (size.height > 0) {
            val h = size.height.toFloat()
            LaunchedEffect(size) {
                val expanded = topInset + with(density) { 84.dp.toPx() }
                sheet.updateAnchors(listOf(expanded, h * 0.42f, h - peekPx), initialIndex = 2)
                // Camera target sits in the middle of the map area left visible above the peeking sheet.
                val ay = ((topInset + (h - peekPx)) / 2f).roundToInt()
                onPinAnchor(size.width / 2, ay)
            }
            val anchorY = (topInset + (h - peekPx)) / 2f
            CenterPin(
                lifted = cameraMoving,
                modifier = Modifier.offset { IntOffset(size.width / 2, anchorY.roundToInt()) },
            )
        }

        // Soft top fade so the status bar stays legible over busy map tiles.
        Box(
            Modifier.fillMaxWidth().height(with(density) { topInset.toDp() } + 28.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(p.bg.copy(alpha = 0.72f), p.bg.copy(alpha = 0f))
                    )
                ),
        )

        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                    .clip(RoundedCornerShape(22.dp)).background(p.surface)
                    .clickable(onClick = onOpenLedger).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("本月", style = Type.caption, color = p.text2)
                Spacer(Modifier.width(8.dp))
                Text("¥", style = Type.bodyStrong, color = p.text)
                RollingText(formatMoney(monthTotalCents), Type.bodyStrong.copy(fontFeatureSettings = "tnum"), p.text)
            }
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.AutoMirrored.Rounded.ReceiptLong, "账本", onOpenLedger)
        }

        // Locate button rides on top of the sheet.
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 16.dp)
                .offset { val o = sheet.offset; IntOffset(0, if (sheet.anchors.isEmpty()) 10_000 else (o - 64.dp.toPx()).roundToInt().coerceAtLeast(0)) }
                .graphicsLayer {
                    // Fade away as the sheet climbs over the map.
                    val a = sheet.anchors
                    alpha = if (a.size < 3) 1f else ((sheet.offset - a[1]) / (a[2] - a[1])).coerceIn(0f, 1f)
                },
        ) {
            RoundButton(if (locationDenied) Icons.Rounded.LocationDisabled else Icons.Rounded.MyLocation, "定位", onLocate, spinning = locating)
        }

        NearbySheet(
            state = sheet, nearby = nearby, visits = visits, categoryOf = categoryOf, pinBusy = pinBusy,
            locationDenied = locationDenied,
            onPlaceClick = onPlaceClick, onPinHere = onPinHere, onRetry = onRetry,
        )
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, spinning: Boolean = false) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val view = LocalView.current
    Box(
        Modifier.size(46.dp).pressScale(src, 0.9f)
            .shadow(10.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(CircleShape).background(p.surface)
            .clickable(src, null) { view.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (spinning) CircularProgressIndicator(Modifier.size(20.dp), color = p.accent, strokeWidth = 2.dp)
        else Icon(icon, desc, tint = p.text, modifier = Modifier.size(22.dp))
    }
}

/** The "where" pin. Lifts while the map moves, drops back with a bouncy spring when it settles. */
@Composable
private fun CenterPin(lifted: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val lift by animateFloatAsState(
        if (lifted) 1f else 0f,
        if (lifted) spring(dampingRatio = 0.9f, stiffness = 900f) else spring(dampingRatio = 0.42f, stiffness = 520f),
        label = "lift",
    )
    Canvas(modifier.size(1.dp)) {
        val headR = 11.dp.toPx()
        val stem = 14.dp.toPx()
        val up = lift * 14.dp.toPx()
        // Ground shadow shrinks as the pin rises.
        val sw = (10.dp.toPx()) * (1f - 0.4f * lift)
        drawOval(Color.Black.copy(alpha = 0.22f - 0.1f * lift), topLeft = Offset(-sw / 2, -sw * 0.18f), size = Size(sw, sw * 0.36f))
        val headCy = -stem - headR - up
        drawLine(p.accent, Offset(0f, -up), Offset(0f, headCy), strokeWidth = 2.6.dp.toPx())
        drawCircle(Color.Black.copy(alpha = 0.12f), headR + 1.5.dp.toPx(), Offset(0f, headCy + 1.5.dp.toPx()))
        drawCircle(p.accent, headR, Offset(0f, headCy))
        drawCircle(Color.White, headR * 0.38f, Offset(0f, headCy))
    }
}

@Composable
private fun NearbySheet(
    state: SheetState,
    nearby: NearbyState,
    visits: (String) -> Int,
    categoryOf: (Place) -> Category,
    pinBusy: Boolean,
    locationDenied: Boolean,
    onPlaceClick: (Place) -> Unit,
    onPinHere: () -> Unit,
    onRetry: () -> Unit,
) {
    val p = LocalPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(nearby.center) { listState.scrollToItem(0) }

    Column(
        Modifier.fillMaxSize()
            .offset { val o = state.offset; IntOffset(0, if (state.anchors.isEmpty()) 10_000 else o.roundToInt()) }
            .shadow(24.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), ambientColor = Color(0x30000000), spotColor = Color(0x30000000))
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(p.surface)
            .nestedScroll(state.nestedScroll),
    ) {
        // Header: drag handle + title (drag anywhere here).
        Column(Modifier.fillMaxWidth().sheetDrag(state).padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(p.text3.copy(alpha = 0.45f)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("这笔花在哪里？", style = Type.title, color = p.text)
                    Spacer(Modifier.height(2.dp))
                    val sub = when {
                        nearby.loading -> "正在查找附近的地点…"
                        nearby.error != null -> nearby.error
                        locationDenied -> "未开启定位 · 拖动地图选择位置"
                        else -> "附近 ${nearby.places.size} 个地点 · 拖动地图换个位置"
                    }
                    AnimatedContent(sub, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "sub") {
                        Text(it, style = Type.caption, color = p.text2)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PinHereRow(busy = pinBusy, onClick = onPinHere)
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.hairline))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            when {
                nearby.loading && nearby.places.isEmpty() -> items(6, key = { "sk$it" }) { SkeletonRow(it) }
                nearby.error != null && nearby.places.isEmpty() -> item(key = "err") { ErrorRow(nearby.error, onRetry) }
                else -> items(nearby.places, key = { it.id }, contentType = { 0 }) { place ->
                    PlaceRow(place, categoryOf(place), visits(place.id), Modifier.animateItem()) { onPlaceClick(place) }
                }
            }
        }
    }
}

@Composable
private fun PinHereRow(busy: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().pressScale(src, 0.98f).clip(RoundedCornerShape(16.dp))
            .background(p.accent.copy(alpha = if (p.dark) 0.16f else 0.08f))
            .clickable(src, null) { view.tick(); onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = p.onAccent, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.AddLocationAlt, null, tint = p.onAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("就记在图钉这里", style = Type.bodyStrong, color = p.text)
            Text("找不到店？拖动地图对准位置", style = Type.caption, color = p.text2)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = p.text3)
    }
}

@Composable
private fun PlaceRow(place: Place, category: Category, visits: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val view = LocalView.current
    Row(
        modifier.fillMaxWidth().pressScale(src, 0.98f)
            .clickable(src, null) { view.tick(); onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = Type.bodyStrong, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(category.label, place.typeName).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                    style = Type.caption, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (visits > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "来过 $visits 次", style = Type.caption, color = p.accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.accent.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(formatDistance(place.distanceM), style = Type.caption.copy(fontFeatureSettings = "tnum"), color = p.text3)
    }
}

@Composable
private fun SkeletonRow(i: Int) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(850, delayMillis = i * 60, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a",
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).graphicsLayer { alpha = a }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(p.surface2))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.5f + (i % 3) * 0.12f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.3f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2))
        }
    }
}

@Composable
private fun ErrorRow(msg: String, onRetry: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.WifiOff, null, tint = p.text3, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        Text(msg, style = Type.body, color = p.text2)
        Spacer(Modifier.height(14.dp))
        Text(
            "重试", style = Type.bodyStrong, color = p.accent,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = 0.1f))
                .clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 9.dp),
        )
    }
}
