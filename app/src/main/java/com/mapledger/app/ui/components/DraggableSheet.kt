package com.mapledger.app.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Physical bottom sheet state. [offset] is the sheet's top edge in px from the container top.
 * Anchors are sorted ascending (expanded = smallest offset). Release projects the fling with
 * exponential decay and snaps to the nearest anchor with a velocity-preserving spring.
 */
@Stable
class SheetState(private val scope: CoroutineScope) {
    private val _offset = mutableFloatStateOf(Float.MAX_VALUE)
    /** Read inside layout/draw lambdas (graphicsLayer/offset{}) to avoid recomposition per frame. */
    val offset: Float get() = _offset.floatValue
    // Snapshot state so layout lambdas that read it re-run once anchors are known.
    var anchors: List<Float> by mutableStateOf(emptyList())
        private set
    private var anim: Job? = null

    fun updateAnchors(new: List<Float>, initialIndex: Int) {
        val wasEmpty = anchors.isEmpty()
        val oldIndex = currentIndex()
        anchors = new.sorted()
        anim?.cancel()
        _offset.floatValue = anchors[(if (wasEmpty) initialIndex else oldIndex).coerceIn(anchors.indices)]
    }

    fun currentIndex(): Int =
        if (anchors.isEmpty()) 0 else anchors.indices.minBy { abs(anchors[it] - offset) }

    val isExpanded get() = anchors.isNotEmpty() && offset <= anchors.first() + 0.5f

    fun dragBy(dy: Float): Float {
        if (anchors.isEmpty()) return 0f
        anim?.cancel()
        val cur = offset
        val min = anchors.first(); val max = anchors.last()
        // Rubber-band resistance past the extremes.
        val eff = if ((cur <= min && dy < 0) || (cur >= max && dy > 0)) dy * 0.25f else dy
        val target = (cur + eff).coerceIn(min - 60f, max + 60f)
        _offset.floatValue = target
        return target - cur
    }

    private suspend fun animateTo(target: Float, velocity: Float, damping: Float, stiffness: Float) {
        androidx.compose.animation.core.animate(
            initialValue = offset, targetValue = target, initialVelocity = velocity,
            animationSpec = spring(dampingRatio = damping, stiffness = stiffness),
        ) { v, _ -> _offset.floatValue = v }
    }

    suspend fun settle(velocity: Float) {
        if (anchors.isEmpty()) return
        val projected = exponentialDecay<Float>(frictionMultiplier = 2.2f).calculateTargetValue(offset, velocity)
        val target = anchors.minBy { abs(it - projected) }
        launchAnim { animateTo(target, velocity, 0.82f, 420f) }
    }

    suspend fun animateToIndex(i: Int) {
        if (anchors.isEmpty()) return
        launchAnim { animateTo(anchors[i.coerceIn(anchors.indices)], 0f, 0.85f, 400f) }
    }

    private suspend fun launchAnim(block: suspend () -> Unit) {
        anim?.cancel()
        val j = scope.launch { block() }
        anim = j
        j.join()
    }

    /** Lets an inner LazyColumn hand over scroll to the sheet (and back) naturally. */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Dragging up while sheet not fully expanded: move the sheet first.
            return if (available.y < 0 && !isExpanded && source == NestedScrollSource.UserInput)
                Offset(0f, dragBy(available.y)) else Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // List is at its top and user keeps pulling down: drag the sheet down.
            return if (available.y > 0 && source == NestedScrollSource.UserInput)
                Offset(0f, dragBy(available.y)) else Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val moving = anchors.isNotEmpty() && anchors.none { abs(it - offset) < 0.5f }
            return if (moving || (available.y < 0 && !isExpanded)) { settle(available.y); available } else Velocity.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (anchors.isNotEmpty() && anchors.none { abs(it - offset) < 0.5f }) settle(available.y)
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberSheetState(): SheetState {
    val scope = rememberCoroutineScope()
    return remember { SheetState(scope) }
}

/** Drag handle area modifier for the sheet header. */
@Composable
fun Modifier.sheetDrag(state: SheetState): Modifier {
    val scope = rememberCoroutineScope()
    return this.draggable(
        state = rememberDraggableState { state.dragBy(it) },
        orientation = Orientation.Vertical,
        onDragStopped = { v -> scope.launch { state.settle(v) } },
    )
}
