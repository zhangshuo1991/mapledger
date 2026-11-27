package com.mapledger.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapledger.app.data.Category
import com.mapledger.app.place.Place
import com.mapledger.app.place.formatDistance
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.LocalPalette
import com.mapledger.app.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Amount input rules: up to 7 integer digits and 2 decimals. Returns null when the key is rejected. */
internal fun applyKey(cur: String, key: String): String? = when (key) {
    "del" -> if (cur.isEmpty()) null else cur.dropLast(1)
    "." -> when {
        cur.contains('.') -> null
        cur.isEmpty() -> "0."
        else -> "$cur."
    }
    else -> {
        val dot = cur.indexOf('.')
        when {
            dot >= 0 && cur.length - dot > 2 -> null
            dot < 0 && cur.length >= 7 -> null
            cur == "0" -> key
            else -> cur + key
        }
    }
}

internal fun toCents(s: String): Long = s.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L

@Composable
fun AmountSheet(
    place: Place?,
    initialCategory: Category,
    autoCategory: Category,
    remembered: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Category) -> Unit,
) {
    val p = LocalPalette.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Keep showing the last place while animating out.
    var shown by remember { mutableStateOf(place) }
    if (place != null) shown = place
    val visible = place != null

    val progress = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) { drag.snapTo(0f); progress.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 380f)) }
        else progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
    }
    if (!visible && progress.value == 0f && !progress.isRunning) return
    val target = shown ?: return

    var amount by remember(target.id) { mutableStateOf("") }
    var category by remember(target.id, initialCategory) { mutableStateOf(initialCategory) }
    var saving by remember(target.id) { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val cents = toCents(amount)

    fun reject() {
        view.reject()
        scope.launch { shake.snapTo(0f); shake.animateTo(1f, tween(380, easing = LinearEasing)) }
    }
    fun press(k: String) {
        if (saving) return
        val next = applyKey(amount, k)
        if (next == null) reject() else { view.tick(); amount = next }
    }
    fun done() {
        if (saving) return
        if (cents <= 0) { reject(); return }
        saving = true
        view.confirm()
        scope.launch { delay(420); onSave(cents, category) }
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(p.scrim)
                .clickable(remember { MutableInteractionSource() }, null) { if (!saving) onDismiss() },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - progress.value) * size.height + drag.value
                }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(p.surface)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding(),
        ) {
            // Header (drag down to dismiss)
            Column(
                Modifier.fillMaxWidth().draggable(
                    rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(-20f)) } },
                    Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drag.value > with(density) { 120.dp.toPx() } || v > 1800f) onDismiss()
                        else drag.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f), initialVelocity = v)
                    },
                ).padding(horizontal = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(p.text3.copy(alpha = 0.45f)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(category, transitionSpec = {
                        (scaleIn(spring(0.5f, 600f), 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                    }, label = "badge") { CategoryBadge(it, 44.dp, 22.dp, filled = true) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(target.name, style = Type.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOf(target.typeName, if (target.distanceM > 0) formatDistance(target.distanceM) else "")
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = Type.caption, color = p.text2, maxLines = 1)
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(p.surface2).clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "关闭", tint = p.text2, modifier = Modifier.size(18.dp)) }
                }
            }

            // Amount display
            Row(
                Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val t = shake.value
                        translationX = if (t in 0.001f..0.999f) sin(t * Math.PI * 6).toFloat() * (1 - t) * 14.dp.toPx() else 0f
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("¥", style = Type.title.copy(fontSize = 26.sp), color = if (amount.isEmpty()) p.text3 else p.text,
                    modifier = Modifier.padding(end = 6.dp, top = 6.dp))
                if (amount.isEmpty()) Text("0", style = Type.hero.copy(fontFeatureSettings = "tnum"), color = p.text3)
                else TypingText(amount, Type.hero.copy(fontFeatureSettings = "tnum"), p.text)
            }

            // Categories
            val rowState = rememberLazyListState()
            LaunchedEffect(target.id) {
                val idx = Category.entries.indexOf(initialCategory)
                // Only scroll when the chip would start off-screen; keep the row's natural start otherwise.
                rowState.scrollToItem(if (idx >= 3) idx - 1 else 0)
            }
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Category.entries, key = { it.name }) { c ->
                    CategoryChip(c, c == category) { view.tick(); category = c }
                }
            }
            val hint = when {
                category != initialCategory -> "已改为「${category.label}」，下次在这里会自动使用"
                remembered -> "按你上次在这里的选择：${category.label}"
                target.isCustom -> "自选位置，选一个分类吧"
                else -> "根据「${target.typeName.ifBlank { autoCategory.label }}」自动分类"
            }
            AnimatedContent(hint, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "hint") {
                Text(it, style = Type.caption, color = p.text3, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp))
            }

            Keypad(
                canSave = cents > 0, saving = saving,
                onKey = ::press, onDone = ::done,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CategoryChip(c: Category, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) c.color else p.surface2, spring(stiffness = 700f), label = "bg")
    val fg by animateColorAsState(if (selected) Color.White else p.text2, spring(stiffness = 700f), label = "fg")
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.pressScale(src, 0.92f).clip(CircleShape).background(bg).clickable(src, null, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(c.icon, null, tint = if (selected) Color.White else c.color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(c.label, style = Type.bodyStrong.copy(fontSize = 14.sp), color = fg)
    }
}

@Composable
private fun Keypad(canSave: Boolean, saving: Boolean, onKey: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "del"))
    val gap = 8.dp
    Row(modifier.fillMaxWidth().height(58.dp * 4 + gap * 3), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    r.forEach { k -> Key(k, Modifier.weight(1f).fillMaxHeight()) { onKey(k) } }
                }
            }
        }
        DoneKey(canSave, saving, Modifier.weight(1f).fillMaxHeight(), onDone)
    }
}

@Composable
private fun Key(k: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) p.text3.copy(alpha = 0.35f) else p.surface2, tween(if (pressed) 40 else 220), label = "kbg")
    Box(
        modifier.pressScale(src, 0.92f).clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (k == "del") Icon(Icons.AutoMirrored.Rounded.Backspace, "删除", tint = p.text, modifier = Modifier.size(22.dp))
        else Text(k, style = Type.key.copy(fontFeatureSettings = "tnum"), color = p.text)
    }
}

@Composable
private fun DoneKey(enabled: Boolean, saving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (enabled || saving) p.accent else p.accent.copy(alpha = 0.35f), spring(stiffness = 500f), label = "dbg")
    Box(
        modifier.pressScale(src, 0.94f).clip(RoundedCornerShape(16.dp)).background(bg).clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(saving, transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.3f) + fadeIn(tween(120)))
                .togetherWith(scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(100)))
        }, label = "done") { s ->
            if (s) Icon(Icons.Rounded.Check, "已完成", tint = p.onAccent, modifier = Modifier.size(32.dp))
            else Text("完成", style = Type.headline, color = p.onAccent)
        }
    }
}

