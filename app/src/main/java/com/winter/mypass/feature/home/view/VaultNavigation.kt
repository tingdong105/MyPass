package com.winter.mypass.feature.home.view

import android.os.Build
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosan.installer.ui.library.FloatingBottomBar
import com.rosan.installer.ui.library.FloatingBottomBarMode
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.VaultNavigationSettings
import com.winter.mypass.feature.home.viewmodel.VaultTab
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

private val VaultNavigationItems = listOf(
    Triple(VaultTab.Home, R.drawable.ic_vault_home, R.string.vault_home),
    Triple(VaultTab.Generate, R.drawable.ic_vault_generate, R.string.vault_generate),
    Triple(VaultTab.Settings, R.drawable.ic_vault_settings, R.string.vault_settings),
)

/**
 * 为原始玻璃底栏创建与参考项目一致的背景图层。
 * @return Android 13 以上的背景采样对象
 */
@Composable
internal fun rememberVaultNavigationBackdrop(): LayerBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || LocalInspectionMode.current) return null
    return rememberLayerBackdrop()
}

/**
 * 接入 InstallerX 26.09 原始底栏及其点击、拖动和渲染实现。
 * @param tab 已确认的标签
 * @param settings 两个底栏设置
 * @param backdrop 当前页面背景图层
 * @param selectedIndex 原始分页控制器选中的索引
 * @param enabled 当前是否允许切页
 * @param onTab 切换 MyPass 标签
 * @param modifier 页面布局和焦点属性
 */
@Composable
internal fun VaultNavigation(
    tab: VaultTab,
    settings: VaultNavigationSettings,
    backdrop: LayerBackdrop?,
    selectedIndex: () -> Int,
    enabled: Boolean,
    onTab: (VaultTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !settings.floating) {
        NavigationBar(modifier = modifier) {
            for ((destination, icon, label) in VaultNavigationItems) {
                NavigationBarItem(
                    tab == destination, { onTab(destination) },
                    ImageVector.vectorResource(icon), stringResource(label), enabled = enabled,
                )
            }
        }
        return
    }
    if (backdrop == null) {
        FloatingNavigationBar(modifier = modifier) {
            for ((destination, icon, label) in VaultNavigationItems) {
                FloatingNavigationBarItem(
                    tab == destination, { onTab(destination) },
                    ImageVector.vectorResource(icon), stringResource(label), enabled = enabled,
                )
            }
        }
        return
    }
    val acceptsInput = rememberUpdatedState(enabled)
    Box(
        modifier.fillMaxWidth()
            .onPreviewKeyEvent { !acceptsInput.value }
            .focusProperties {
                onEnter = { if (!acceptsInput.value) cancelFocusChange() }
            }
            .focusGroup()
            .pointerInput(Unit) {
                // 禁用途中取消已开始的拖动，让原始组件回到确认的标签。
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (!acceptsInput.value) event.changes.forEach { it.consume() }
                    }
                }
            }
            .then(if (enabled) Modifier else Modifier.clearAndSetSemantics { disabled() })
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        FloatingBottomBar(
            items = VaultNavigationItems,
            modifier = Modifier.align(Alignment.BottomCenter).width(312.dp).padding(
                bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            selectedIndex = selectedIndex,
            onSelected = { index -> if (enabled) onTab(VaultNavigationItems[index].first) },
            backdrop = backdrop,
            mode = if (settings.liquidGlass) FloatingBottomBarMode.LiquidGlass else FloatingBottomBarMode.None,
            iconContent = { item, _ ->
                Icon(ImageVector.vectorResource(item.second), contentDescription = null)
            },
            labelContent = { item, _ ->
                Text(
                    stringResource(item.third), fontSize = 11.sp, lineHeight = 14.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                )
            },
        )
        if (!enabled) {
            // 覆盖命中区域，阻止新的触摸进入原始组件。
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            )
        }
    }
}
