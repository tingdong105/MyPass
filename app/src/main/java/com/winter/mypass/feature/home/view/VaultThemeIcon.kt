package com.winter.mypass.feature.home.view

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.util.lerp
import com.winter.mypass.R
import com.winter.mypass.ui.theme.LocalAppTheme
import com.winter.mypass.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.sin

private const val IconAnimationEnd = 0.86f
private val IconMorphEasing = CubicBezierEasing(0.25f, 0.08f, 0.2f, 1f)

/**
 * 插值日月轮廓并轻微拉伸回弹，两套主题读取同一进度。
 * @param modifier 保持按钮布局稳定的图标尺寸
 */
@Composable
internal fun VaultThemeIcon(modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val reveal = LocalAppTheme.current?.reveal
    val tint = MiuixTheme.colorScheme.onSurface
    if (reveal == null) {
        val icon = if (dark) R.drawable.ic_vault_theme_sun else R.drawable.ic_vault_theme_moon
        Icon(ImageVector.vectorResource(icon), null, modifier, tint = tint)
    } else {
        val sun = ImageVector.vectorResource(R.drawable.ic_vault_theme_sun)
        val moon = ImageVector.vectorResource(R.drawable.ic_vault_theme_moon)
        val sunBody = (sun.root[0] as VectorPath).pathData
        val moonBody = (moon.root[0] as VectorPath).pathData
        val painter = rememberVectorPainter(
            defaultWidth = sun.defaultWidth,
            defaultHeight = sun.defaultHeight,
            viewportWidth = sun.viewportWidth,
            viewportHeight = sun.viewportHeight,
            autoMirror = false,
        ) { _, _ ->
            val time = (reveal.progress.value / IconAnimationEnd).coerceAtMost(1f)
            val morph = IconMorphEasing.transform(time)
            val toMoon = if (reveal.fromDark) morph else 1f - morph
            val raysScale = lerp(1f, 0.72f, toMoon)
            val configs = remember(sunBody, moonBody, toMoon) {
                mapOf(
                    "body" to ThemeIconVectorConfig(VectorProperty.PathData to morphOutline(sunBody, moonBody, toMoon)),
                    "light" to ThemeIconVectorConfig(
                        VectorProperty.ScaleX to raysScale,
                        VectorProperty.ScaleY to raysScale,
                        VectorProperty.Rotation to (toMoon * 28f),
                    ),
                    "rays" to ThemeIconVectorConfig(VectorProperty.StrokeAlpha to (1f - toMoon / 0.72f).coerceAtLeast(0f)),
                )
            }
            RenderVectorGroup(sun.root, configs)
        }
        Icon(
            painter, null,
            modifier.graphicsLayer {
                val time = (reveal.progress.value / IconAnimationEnd).coerceAtMost(1f)
                val remaining = 1f - time
                val stretch = sin(3 * PI * time).toFloat() * time * remaining * remaining * 0.68f
                scaleX = 1f - stretch * 0.75f
                scaleY = 1f + stretch
            },
            tint = tint,
        )
    }
}

/**
 * 将动画值应用到资源中的命名矢量节点。
 * @author Tingdong
 * @date 2026-11-22
 */
private class ThemeIconVectorConfig(vararg overrides: Pair<VectorProperty<*>, Any>) : VectorConfig {
    private val values = overrides.toMap()

    /**
     * 读取属性的动画值，未覆盖时保留资源默认值。
     * @param property 当前矢量属性
     * @param defaultValue 资源中的原值
     * @return 当前帧的属性值
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
        (values[property] ?: defaultValue) as T
}

/**
 * 插值资源中按相同顺序排列的起点和三次贝塞尔曲线。
 * @param sun 太阳主体轮廓
 * @param moon 月亮主体轮廓
 * @param fraction 0 为太阳，1 为月亮
 * @return 当前帧的闭合路径
 */
private fun morphOutline(sun: List<PathNode>, moon: List<PathNode>, fraction: Float): List<PathNode> {
    if (fraction == 0f) return sun
    if (fraction == 1f) return moon
    return sun.zip(moon) { from, to ->
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
}
