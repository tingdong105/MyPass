package com.winter.mypass.feature.home.view

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.winter.mypass.R
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import kotlin.math.PI
import kotlin.math.sin

/**
 * 用路径开合和弹性拉伸切换密码可见性图标。
 * @param revealed 是否完整显示密码
 * @param enabled 是否允许切换
 * @param tint 与复制按钮共用的线条颜色
 * @param onClick 临时切换密码可见性
 */
@Composable
internal fun VaultPasswordVisibilityButton(revealed: Boolean, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val open = ImageVector.vectorResource(R.drawable.ic_vault_eye_open)
    val closed = ImageVector.vectorResource(R.drawable.ic_vault_eye_closed)
    val progress = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "Password eye morph",
    )
    val opacity = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "Password eye opacity",
    )
    val painter = rememberVectorPainter(
        defaultWidth = open.defaultWidth,
        defaultHeight = open.defaultHeight,
        viewportWidth = open.viewportWidth,
        viewportHeight = open.viewportHeight,
        autoMirror = false,
    ) { _, _ ->
        val fraction = progress.value
        val visibility = opacity.value
        val configs = remember(open, closed, fraction, visibility) {
            mapOf(
                "eyelid" to PasswordEyeVectorConfig(
                    VectorProperty.PathData to morphEyePath(
                        (open.root[0] as VectorPath).pathData,
                        (closed.root[0] as VectorPath).pathData, fraction,
                    ),
                ),
                "pupil" to PasswordEyeVectorConfig(
                    VectorProperty.PathData to morphEyePath(
                        (open.root[1] as VectorPath).pathData,
                        (closed.root[1] as VectorPath).pathData, fraction,
                    ),
                    VectorProperty.StrokeAlpha to (1f - visibility),
                ),
                "lashes" to PasswordEyeVectorConfig(
                    VectorProperty.StrokeAlpha to visibility,
                ),
            )
        }
        RenderVectorGroup(open.root, configs)
    }
    IconButton(
        onClick = onClick, enabled = enabled,
        minWidth = Theme.sizes.minimumTouchTarget, minHeight = Theme.sizes.minimumTouchTarget,
    ) {
        Icon(
            painter = painter,
            contentDescription = stringResource(if (revealed) R.string.vault_hide else R.string.vault_show),
            modifier = Modifier.size(22.dp).graphicsLayer {
                val squash = sin(PI * progress.value).toFloat()
                scaleX = 1f + squash * 0.12f
                scaleY = 1f - squash * 0.16f
            },
            tint = tint,
        )
    }
}

/**
 * 为眼睛资源的命名路径提供动画属性。
 * @author Tingdong
 * @date 2026-11-23
 */
private class PasswordEyeVectorConfig(vararg overrides: Pair<VectorProperty<*>, Any>) : VectorConfig {
    private val values = overrides.toMap()

    /**
     * 读取当前帧的路径或透明度，其他属性保留资源值。
     * @param property 当前矢量属性
     * @param defaultValue 资源默认值
     * @return 当前帧的属性值
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
        (values[property] ?: defaultValue) as T
}

/**
 * 插值同构的睁眼和闭眼轮廓，允许弹簧轻微越过终点。
 * 使用示例：morphEyePath(openPath, closedPath, 0.5f)
 * @param open 睁眼路径
 * @param closed 闭眼路径
 * @param fraction 开合进度，0 为睁眼，1 为闭眼
 * @return 当前帧的路径
 */
private fun morphEyePath(open: List<PathNode>, closed: List<PathNode>, fraction: Float): List<PathNode> =
    open.zip(closed) { from, to ->
        when (from) {
            is PathNode.MoveTo -> {
                val end = to as PathNode.MoveTo
                PathNode.MoveTo(lerp(from.x, end.x, fraction), lerp(from.y, end.y, fraction))
            }
            is PathNode.CurveTo -> {
                val end = to as PathNode.CurveTo
                PathNode.CurveTo(
                    lerp(from.x1, end.x1, fraction), lerp(from.y1, end.y1, fraction),
                    lerp(from.x2, end.x2, fraction), lerp(from.y2, end.y2, fraction),
                    lerp(from.x3, end.x3, fraction), lerp(from.y3, end.y3, fraction),
                )
            }
            else -> from
        }
    }
