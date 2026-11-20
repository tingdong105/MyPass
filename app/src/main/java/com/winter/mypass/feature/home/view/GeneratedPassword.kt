package com.winter.mypass.feature.home.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 根据每层实际绘制的透明度，选出当前最清晰的密码。
 * @author Tingdong
 * @date 2026-11-20
 */
internal class GeneratedPasswordDisplay {
    private val layers = mutableStateMapOf<String, State<Float>>()
    val visiblePassword: String? by derivedStateOf {
        layers.maxByOrNull { it.value.value }?.takeIf { it.value.value > 0f }?.key
    }

    /**
     * 在文字层处于组合中时跟踪它的绘制透明度。
     * @param password 本层显示的密码
     * @param opacity 本层的淡入淡出状态
     */
    @Composable
    fun TrackLayer(password: String, opacity: State<Float>) {
        DisposableEffect(password, opacity) {
            layers[password] = opacity
            onDispose {
                if (layers[password] === opacity) layers.remove(password)
            }
        }
    }
}

/**
 * 居中显示生成结果，同长度刷新时淡入滑动。
 * @param password 生成器给出的非空结果
 * @param color 密码前景色
 * @param display 用于协调显示和复制的文字层状态
 */
@Composable
internal fun GeneratedPassword(password: String, color: Color, display: GeneratedPasswordDisplay) {
    val style = MiuixTheme.textStyles.title3.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
        textDirection = TextDirection.Ltr,
    )
    val measurer = rememberTextMeasurer(cacheSize = 0)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        key(password.length) {
            AnimatedContent(
                targetState = password,
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    val enter = slideInVertically(tween(235, easing = FastOutSlowInEasing)) { it / 5 }
                    (enter togetherWith ExitTransition.None).using(null)
                },
                label = "Generated password",
            ) { value ->
                val opacity = transition.animateFloat(
                    transitionSpec = {
                        if (targetState == EnterExitState.Visible) tween(200, delayMillis = 45)
                        else tween(85)
                    },
                    label = "Password opacity",
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                display.TrackLayer(value, opacity)
                val displayed = remember(value, width, measurer, style) {
                    val singleLine = measurer.measure(value, style = style, softWrap = false)
                    wrapPassword(value, singleLine, width)
                }
                Text(
                    text = displayed,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = opacity.value }.clearAndSetSemantics {
                        if (value == display.visiblePassword) text = AnnotatedString(value)
                        else hideFromAccessibility()
                    },
                    color = color,
                    style = style,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * 在第一个放不下的字符前断行。
 * @param password 原始密码
 * @param layout 同样式下测量出的单行布局
 * @param width 实际用于排版的宽度，单位为像素
 * @return 仅用于显示的分行文本
 */
private fun wrapPassword(password: String, layout: TextLayoutResult, width: Int): String {
    if (layout.size.width <= width) return password
    return buildString {
        var lineStart = 0
        var lineLeft = layout.getBoundingBox(0).left
        for (index in password.indices) {
            val bounds = layout.getBoundingBox(index)
            if (index > lineStart && bounds.right - lineLeft > width) {
                append('\n')
                lineStart = index
                lineLeft = bounds.left
            }
            append(password[index])
        }
    }
}
