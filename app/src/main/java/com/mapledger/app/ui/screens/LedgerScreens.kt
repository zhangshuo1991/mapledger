package com.mapledger.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapledger.app.data.Category
import com.mapledger.app.data.Expense
import com.mapledger.app.data.PlaceSummary
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.LocalPalette
import com.mapledger.app.ui.theme.Type
import java.text.SimpleDateFormat
import java.util.*

private val dayFmt = ThreadLocal.withInitial { SimpleDateFormat("M月d日 EEEE", Locale.CHINA) }
private val timeFmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.CHINA) }
private val dateFmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }

private fun dayKey(t: Long): Long = Calendar.getInstance().run {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); timeInMillis
}

fun relativeDay(t: Long): String {
    val days = ((dayKey(System.currentTimeMillis()) - dayKey(t)) / 86_400_000L).toInt()
    return when (days) { 0 -> "今天"; 1 -> "昨天"; in 2..6 -> "${days}天前"; else -> dateFmt.get()!!.format(Date(t)) }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).pressScale(src, 0.88f).clip(CircleShape).background(p.surface).clickable(src, null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = p.text, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, style = Type.headline, color = p.text)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    expenses: List<Expense>,
    monthTotalCents: Long,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onDelete: (Expense) -> Unit,
    undoGeneration: Int = 0,
) {
    val p = LocalPalette.current
    val groups = remember(expenses) { expenses.groupBy { dayKey(it.createdAt) }.toList() }
    val monthByCat = remember(expenses) {
        val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.let { dayKey(it.timeInMillis) }
        expenses.filter { it.createdAt >= start }.groupBy { Category.of(it.category) }
            .mapValues { (_, v) -> v.sumOf { it.amountCents } }.toList().sortedByDescending { it.second }
    }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopBar("账本", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "summary") { SummaryCard(monthTotalCents, monthByCat) }
            if (expenses.isEmpty()) item(key = "empty") { EmptyLedger() }
            groups.forEach { (day, list) ->
                stickyHeader(key = "h$day") {
                    Row(
                        Modifier.fillMaxWidth().background(p.bg).padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(dayFmt.get()!!.format(Date(day)), style = Type.caption, color = p.text2, modifier = Modifier.weight(1f))
                        Text("¥" + formatMoney(list.sumOf { it.amountCents }), style = Type.caption.copy(fontFeatureSettings = "tnum"), color = p.text2)
                    }
                }
                item(key = "g$day") {
                    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(p.surface)) {
                        list.forEachIndexed { i, e ->
                            key(e.id) {
                                SwipeRow(e, showDivider = i > 0, undoGeneration = undoGeneration, onClick = { onOpenPlace(e.placeId) }, onDelete = { onDelete(e) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(total: Long, byCat: List<Pair<Category, Long>>) {
    val p = LocalPalette.current
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(20.dp)) {
        Text("本月支出", style = Type.caption, color = p.text2)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("¥", style = Type.title, color = p.text, modifier = Modifier.padding(bottom = 6.dp, end = 4.dp))
            RollingText(formatMoney(total), Type.hero.copy(fontSize = 38.sp, fontFeatureSettings = "tnum"), p.text)
        }
        if (byCat.isNotEmpty() && total > 0) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                byCat.forEach { (c, v) ->
                    val w by animateFloatAsState(v.toFloat() / total, spring(dampingRatio = 0.8f, stiffness = 200f), label = "w")
                    if (w > 0.001f) Box(Modifier.weight(w).fillMaxHeight().background(c.color))
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRowLegend(byCat, total)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLegend(byCat: List<Pair<Category, Long>>, total: Long) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        byCat.forEach { (c, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.color))
                Spacer(Modifier.width(5.dp))
                Text("${c.label} ${Math.round(v * 100.0 / total)}%", style = Type.caption, color = p.text2)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(e: Expense, showDivider: Boolean, undoGeneration: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val view = LocalView.current
    var fired by remember(e.id) { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) {
            // confirmValueChange can be called more than once per swipe; delete only once.
            if (!fired) { fired = true; view.confirm(); onDelete() }
            true
        } else false
    })
    // After 撤销 the same row (same id) comes back: slide it back in instead of leaving it dismissed.
    LaunchedEffect(undoGeneration) {
        if (undoGeneration > 0 && state.currentValue != SwipeToDismissBoxValue.Settled) {
            fired = false
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(p.danger).padding(end = 22.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.DeleteOutline, "删除", tint = Color.White)
            }
        },
    ) {
        Column(Modifier.background(p.surface)) {
            if (showDivider) Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(0.5.dp).background(p.hairline))
            ExpenseRow(e, onClick)
        }
    }
}

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit, showTime: Boolean = true) {
    val p = LocalPalette.current
    val c = Category.of(e.category)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(c, 40.dp, 19.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val time = timeFmt.get()!!.format(Date(e.createdAt))
            // In the ledger the row names the place; on a place's own page it names the visit.
            Text(
                if (showTime) e.placeName else "${relativeDay(e.createdAt)} $time",
                style = Type.bodyStrong, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(if (showTime) "${c.label} · $time" else c.label, style = Type.caption, color = p.text2)
        }
        Spacer(Modifier.width(12.dp))
        Text("-" + formatMoney(e.amountCents), style = Type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = p.text)
    }
}

@Composable
private fun EmptyLedger() {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Map, null, tint = p.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("还没有记录", style = Type.headline, color = p.text)
        Spacer(Modifier.height(6.dp))
        Text("回到地图，选一个地点记下第一笔", style = Type.caption, color = p.text2)
    }
}

@Composable
fun PlaceDetailScreen(
    summary: PlaceSummary?,
    visits: List<Expense>,
    onBack: () -> Unit,
    onRecord: () -> Unit,
    onShowOnMap: () -> Unit,
) {
    val p = LocalPalette.current
    if (summary == null) {
        Column(Modifier.fillMaxSize().background(p.bg)) { TopBar("", onBack) }
        return
    }
    val c = Category.of(summary.category)
    LazyColumn(Modifier.fillMaxSize().background(p.bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "top") { TopBar("", onBack) }
        item(key = "head") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
                CategoryBadge(c, 60.dp, 28.dp, filled = true)
                Spacer(Modifier.height(16.dp))
                Text(summary.placeName, style = Type.title.copy(fontSize = 26.sp), color = p.text)
                Spacer(Modifier.height(4.dp))
                Text(listOf(c.label, summary.placeType).filter { it.isNotBlank() }.distinct().joinToString(" · "), style = Type.body, color = p.text2)
            }
        }
        item(key = "stats") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("来过", "${summary.visits}", "次", Modifier.weight(1f))
                    Stat("累计", formatMoney(summary.totalCents, trimZeros = true), "元", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("平均每次", formatMoney(summary.totalCents / summary.visits.coerceAtLeast(1), trimZeros = true), "元", Modifier.weight(1f))
                    Stat("上次", relativeDay(summary.lastAt), "", Modifier.weight(1f))
                }
            }
        }
        item(key = "actions") {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("再记一笔", Icons.Rounded.Add, true, Modifier.weight(1f), onRecord)
                ActionButton("在地图上看", Icons.Rounded.Map, false, Modifier.weight(1f), onShowOnMap)
            }
        }
        item(key = "vh") {
            Text("消费记录", style = Type.caption, color = p.text2, modifier = Modifier.padding(start = 22.dp, top = 24.dp, bottom = 8.dp))
        }
        item(key = "visits") {
            Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(p.surface)) {
                visits.forEachIndexed { i, e ->
                    key(e.id) {
                        if (i > 0) Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(0.5.dp).background(p.hairline))
                        ExpenseRow(e, onClick = {}, showTime = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.surface).padding(16.dp)) {
        Text(label, style = Type.caption, color = p.text2)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Type.title.copy(fontFeatureSettings = "tnum"), color = p.text, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, style = Type.caption, color = p.text2, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val view = LocalView.current
    Row(
        modifier.height(50.dp).pressScale(src, 0.96f).clip(RoundedCornerShape(16.dp))
            .background(if (primary) p.accent else p.surface)
            .clickable(src, null) { view.tick(); onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) p.onAccent else p.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.bodyStrong, color = if (primary) p.onAccent else p.text)
    }
}
