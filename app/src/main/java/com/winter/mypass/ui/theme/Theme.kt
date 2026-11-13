package com.winter.mypass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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
            isDark = darkTheme,
        )
    }

    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        MiuixTheme(
            controller = controller,
            content = content,
        )
    }
}
