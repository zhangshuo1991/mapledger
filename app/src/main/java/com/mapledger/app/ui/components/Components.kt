package com.mapledger.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mapledger.app.data.Category
import java.text.DecimalFormat

fun View.tick() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
fun View.confirm() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
)
fun View.reject() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
)

/** Springy press feedback: scales down while pressed, overshoots back on release. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.94f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = if (isPressed) 1f else 0.45f, stiffness = if (isPressed) 1400f else 600f),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

private val moneyFmt = ThreadLocal.withInitial { DecimalFormat("#,##0.00") }
private val moneyFmtShort = ThreadLocal.withInitial { DecimalFormat("#,##0.##") }
fun formatMoney(cents: Long, trimZeros: Boolean = false): String =
    (if (trimZeros) moneyFmtShort else moneyFmt).get()!!.format(cents / 100.0)

/**
 * Odometer-style number: each glyph rolls vertically with a spring when it changes.
 * Glyph slots are keyed from the right so units stay put while totals grow.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        val n = text.length
        for (i in 0 until n) {
            key(n - i) {
                val c = text[i]
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        val up = (targetState.digitToIntOrNull() ?: 0) >= (initialState.digitToIntOrNull() ?: 0)
                        val spec = spring<IntOffset>(dampingRatio = 0.78f, stiffness = 520f)
                        (slideInVertically(spec) { h -> if (up) h else -h } + fadeIn(tween(140)))
                            .togetherWith(slideOutVertically(spec) { h -> if (up) -h else h } + fadeOut(tween(110)))
                            .using(SizeTransform(clip = true))
                    },
                    label = "glyph",
                ) { ch -> Text(ch.toString(), style = style, color = color) }
            }
        }
    }
}

/**
 * Typing display: new glyphs pop in from below with a spring, removed ones collapse.
 */
@Composable
fun TypingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { i, c ->
            key(i, c) {
                val anim = remember { Animatable(0f) }
                LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 700f)) }
                Text(
                    c.toString(), style = style, color = color,
                    modifier = Modifier.graphicsLayer {
                        val v = anim.value
                        translationY = (1f - v) * 22.dp.toPx()
                        alpha = v.coerceIn(0f, 1f)
                        scaleX = 0.7f + 0.3f * v; scaleY = scaleX
                    },
                )
            }
        }
    }
}

@Composable
fun CategoryBadge(category: Category, size: Dp = 40.dp, iconSize: Dp = 20.dp, filled: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (filled) category.color else category.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, null, tint = if (filled) Color.White else category.color, modifier = Modifier.size(iconSize))
    }
}
