package com.mapledger.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mapledger.app.ui.components.pressScale
import com.mapledger.app.ui.theme.LocalPalette
import com.mapledger.app.ui.theme.Type

@Composable
fun PrivacyScreen(onAgree: () -> Unit, onDecline: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f)) }

    Column(
        Modifier.fillMaxSize().background(p.bg).systemBarsPadding().padding(horizontal = 28.dp)
            .graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 40.dp.toPx() },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(p.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Place, null, tint = p.onAccent, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(28.dp))
            Text("欢迎使用地图记账", style = Type.title.copy(fontSize = Type.title.fontSize * 1.2f), color = p.text)
            Spacer(Modifier.height(10.dp))
            Text("在地图上点一下，就能记下一笔。开始之前，请了解我们如何使用你的信息。", style = Type.body, color = p.text2)
            Spacer(Modifier.height(32.dp))
            Point(Icons.Rounded.MyLocation, "位置信息", "用于定位你所在的位置并查找附近的店铺。仅在使用应用时获取。")
            Point(Icons.Rounded.PhoneAndroid, "本地存储", "你的每一笔账只保存在这台手机上，我们不会上传你的账本。")
            Point(Icons.Rounded.Map, "高德地图 SDK", "地图、定位和地点搜索由高德开放平台提供，SDK 会按其隐私政策收集必要的设备与位置信息。")
            Spacer(Modifier.height(8.dp))
            Text(
                "阅读《高德地图开放平台隐私权政策》",
                style = Type.caption, color = p.accent,
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lbs.amap.com/pages/privacy/"))) }
                }.padding(vertical = 8.dp),
            )
        }
        val src = remember { MutableInteractionSource() }
        Box(
            Modifier.fillMaxWidth().height(54.dp).pressScale(src, 0.97f).clip(RoundedCornerShape(16.dp))
                .background(p.accent).clickable(src, null, onClick = onAgree),
            contentAlignment = Alignment.Center,
        ) { Text("同意并继续", style = Type.headline, color = p.onAccent) }
        Text(
            "不同意", style = Type.body, color = p.text2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDecline).padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Row(Modifier.padding(bottom = 22.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Type.bodyStrong, color = p.text)
            Spacer(Modifier.height(3.dp))
            Text(body, style = Type.caption.copy(lineHeight = Type.caption.fontSize * 1.5f), color = p.text2)
        }
    }
}
