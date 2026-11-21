package com.winter.mypass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme

internal val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * 通用界面间距与尺寸。
 * @author Tingdong
 * @date 2026-11-18
 */
internal object Theme {
    val spacing = Spacing
    val sizes = Sizes

    object Spacing {
        val small = 8.dp
        val medium = 16.dp
        val large = 24.dp
    }

    object Sizes {
        val minimumTouchTarget = 48.dp
    }
}

/**
 * 在 Miuix 暗色主题上定义深灰表面层级与对应的前景色。
 * @return MyPass 的默认暗色配色
 */
private fun myPassDarkColorScheme(): Colors {
    val page = Color(0xFF1B1C21)
    val card = Color(0xFF25272E)
    val text = Color(0xFFE5E7ED)
    val secondaryText = Color(0xFFADB3C2)
    val onAccent = Color(0xFF19212F)
    return darkColorScheme(
        primary = Color(0xFF83ABFA),
        onPrimary = onAccent,
        error = Color(0xFFF28B84),
        onError = onAccent,
        background = page,
        onBackground = text,
        onBackgroundVariant = secondaryText,
        surface = page,
        onSurface = text,
        surfaceVariant = card,
        onSurfaceSecondary = Color(0xFFC3C7D2),
        onSurfaceVariantSummary = secondaryText,
        onSurfaceVariantActions = secondaryText,
        surfaceContainer = card,
        onSurfaceContainer = text,
        onSurfaceContainerVariant = secondaryText,
        surfaceContainerHigh = Color(0xFF2D3038),
        onSurfaceContainerHigh = secondaryText,
        surfaceContainerHighest = Color(0xFF333640),
        onSurfaceContainerHighest = text,
        outline = Color(0xFF535866),
        dividerLine = Color(0xFF383B44),
    )
}

/**
 * Miuix 应用主题与固定字体缩放。
 * @param darkTheme 是否启用深色主题，默认跟随系统
 * @param dynamicColor 是否启用 Android 12 及以上的系统动态配色
 * @param content 主题内页面内容
 */
@Composable
fun MyPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val controller = remember(darkTheme, useDynamicColor) {
        ThemeController(
            colorSchemeMode = if (useDynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System,
            darkColors = myPassDarkColorScheme(),
            isDark = darkTheme,
        )
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
        LocalDarkTheme provides darkTheme,
    ) {
        MiuixTheme(
            controller = controller,
            content = content,
        )
    }
}
