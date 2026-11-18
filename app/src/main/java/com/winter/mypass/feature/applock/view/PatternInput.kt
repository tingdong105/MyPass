package com.winter.mypass.feature.applock.view

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.applock.model.addPatternPoint
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 九宫格连线输入，也支持通过无障碍操作逐点选择。
 * @param enabled 是否可接受输入
 * @param maxSide 当前视窗下的网格边长上限
 * @param onSubmit 提交按绘制顺序编码的点
 */
@Composable
internal fun PatternInput(enabled: Boolean, maxSide: Dp, onSubmit: (CharArray) -> Unit) {
    var dragPath by remember { mutableStateOf(emptyList<Int>()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var selectedPoints by remember { mutableStateOf(emptyList<Int>()) }
    val submit by rememberUpdatedState(onSubmit)
    val view = LocalView.current
    val tick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    val path = dragPath.ifEmpty { selectedPoints }
    val emphasis = List(9) { index ->
        val active = index in path
        animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = if (active) keyframes {
                durationMillis = 220
                1.18f at 85 using LinearOutSlowInEasing
            } else tween(110),
            label = "Pattern node",
        )
    }
    val accent = MiuixTheme.colorScheme.primary
    val nodeColor = if (enabled) MiuixTheme.colorScheme.onBackgroundVariant
    else MiuixTheme.colorScheme.disabledOnSurface

    BoxWithConstraints(
        modifier = Modifier.widthIn(max = maxSide).fillMaxWidth().aspectRatio(1f).pointerInput(enabled, view) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val start = awaitFirstDown()
                val centers = patternCenters(size.width / 3f)
                val radius = size.width / 12f
                val points = mutableListOf<Int>()
                var previous = start.position
                appendHits(points, centers, radius, previous, previous)
                if (points.isEmpty()) return@awaitEachGesture
                start.consume()
                selectedPoints = emptyList()
                try {
                    view.performHapticFeedback(tick)
                    dragPath = points.toList()
                    cursor = previous
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == start.id } ?: break
                        if (change.isConsumed) break
                        val previousCount = points.size
                        for (sample in change.historical) {
                            appendHits(points, centers, radius, previous, sample.position)
                            previous = sample.position
                        }
                        appendHits(points, centers, radius, previous, change.position)
                        previous = change.position
                        change.consume()
                        if (points.size > previousCount) view.performHapticFeedback(tick)
                        if (!change.pressed) {
                            if (points.isNotEmpty()) submit(points.encodePattern())
                            break
                        }
                        dragPath = points.toList()
                        cursor = previous
                    }
                } finally {
                    dragPath = emptyList()
                    cursor = null
                }
            }
        },
    ) {
        val cell = maxWidth / 3
        val target = minOf(cell, Theme.sizes.minimumTouchTarget)
        Canvas(Modifier.fillMaxSize()) {
            val centers = patternCenters(size.width / 3f)
            val endpoint = cursor
            val strokeWidth = 3.dp.toPx()
            path.zipWithNext().forEach { (from, to) ->
                drawLine(accent, centers[from], centers[to], strokeWidth, StrokeCap.Round)
            }
            if (endpoint != null && path.isNotEmpty()) {
                val last = centers[path.last()]
                if (last != endpoint) {
                    drawLine(
                        brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.25f)), last, endpoint),
                        start = last,
                        end = endpoint,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            centers.forEachIndexed { index, center ->
                val pulse = emphasis[index].value
                val selected = pulse.coerceIn(0f, 1f)
                if (pulse > 0f) {
                    drawCircle(accent.copy(alpha = 0.18f * selected), 16.dp.toPx() * pulse, center)
                }
                drawCircle(lerp(nodeColor, accent, selected), 5.dp.toPx() + 2.dp.toPx() * pulse, center)
            }
        }
        if (enabled) {
            repeat(9) { point ->
                val label = stringResource(R.string.lock_pattern_point, point + 1)
                Box(
                    Modifier
                        .absoluteOffset(
                            x = cell * (point % 3 + 0.5f) - target / 2,
                            y = cell * (point / 3 + 0.5f) - target / 2,
                        )
                        .size(target)
                        .semantics {
                            contentDescription = label
                            selected = point in selectedPoints
                            onClick {
                                val points = selectedPoints.toMutableList()
                                if (points.addPatternPoint(point) == 0) {
                                    false
                                } else {
                                    selectedPoints = points
                                    view.performHapticFeedback(tick)
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
    if (selectedPoints.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
            TextButton(
                text = stringResource(R.string.lock_clear_pattern),
                onClick = { selectedPoints = emptyList() },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.lock_submit),
                onClick = {
                    val submission = selectedPoints.encodePattern()
                    selectedPoints = emptyList()
                    submit(submission)
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * 按行计算九宫格点的像素坐标。示例：patternCenters(64f)。
 * @param cell 单个格子的像素宽度
 * @return 依次排列的点中心
 */
private fun patternCenters(cell: Float): List<Offset> =
    List(9) { point -> Offset(cell * (point % 3 + 0.5f), cell * (point / 3 + 0.5f)) }

/**
 * 按移动方向补齐触摸采样之间命中的点。示例：appendHits(path, centers, radius, from, to)。
 * @param points 已选点
 * @param centers 九宫格点中心
 * @param radius 单个点的命中半径
 * @param from 上一个触摸坐标
 * @param to 当前触摸坐标
 */
private fun appendHits(
    points: MutableList<Int>,
    centers: List<Offset>,
    radius: Float,
    from: Offset,
    to: Offset,
) {
    val movement = to - from
    val lengthSquared = movement.getDistanceSquared()
    val hits = centers.mapIndexedNotNull { index, point ->
        if (index in points) return@mapIndexedNotNull null
        val relative = point - from
        val fraction = if (lengthSquared == 0f) 0f else
            ((relative.x * movement.x + relative.y * movement.y) / lengthSquared).coerceIn(0f, 1f)
        val distance = point - (from + movement * fraction)
        if (distance.getDistanceSquared() <= radius * radius) index to fraction else null
    }
    hits.sortedBy { it.second }.forEach { (index, _) -> points.addPatternPoint(index) }
}

/**
 * 将点编号转换为凭据。示例：listOf(0, 1, 2, 5).encodePattern()。
 * @return 使用 '1' 至 '9' 编码的点序列
 */
private fun List<Int>.encodePattern(): CharArray = CharArray(size) { '1' + this[it] }
