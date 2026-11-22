package com.rosan.installer.ui.theme

import androidx.compose.runtime.Composable
import com.winter.mypass.ui.theme.LocalDarkTheme

/**
 * 向原始 InstallerX 底栏提供 MyPass 当前主题。
 * @author Tingdong
 * @date 2026-11-22
 */
internal object InstallerTheme {
    val isDark: Boolean
        @Composable get() = LocalDarkTheme.current
}
