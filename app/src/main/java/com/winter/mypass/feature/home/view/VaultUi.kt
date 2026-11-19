package com.winter.mypass.feature.home.view

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 窄屏、平板和明暗主题的界面检查。
 * @author Tingdong
 * @date 2026-11-19
 */
@Preview(name = "手机浅色", widthDp = 360, heightDp = 800, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "手机深色", widthDp = 360, heightDp = 800, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "平板浅色", widthDp = 1100, heightDp = 820, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "平板深色", widthDp = 1100, heightDp = 820, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class VaultPreviews

/**
 * 密码本页面的行宽与平板侧栏尺寸。
 * @author Tingdong
 * @date 2026-11-19
 */
internal object VaultLayout {
    val PaneWidth = 632.dp
    val DockedSidebarBreakpoint = 840.dp
    val DockedSidebarWidth = 248.dp
    val DrawerWidth = 264.dp
    val HomeHeaderIconSize = 24.dp
    val HeaderIconSize = 28.dp
}

/**
 * 为二级页面提供原生顶栏和窗口边距。
 * @param title 顶栏标题
 * @param navigationIcon 返回或关闭入口
 * @param actions 顶栏操作
 * @param content 页面内容及其窗口边距
 */
@Composable
internal fun VaultFrame(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                SmallTopAppBar(
                    title, modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
                    navigationIcon = navigationIcon, actions = actions,
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { insets ->
        content(Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize())
    }
}

/**
 * 以统一的阅读宽度容纳滚动表单或详情。
 * @param modifier 已处理窗口边距的区域
 * @param horizontalPadding 内容相对页面两侧的边距
 * @param content 页面分组
 */
@Composable
internal fun VaultColumn(
    modifier: Modifier,
    horizontalPadding: Dp = Theme.spacing.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(top = Theme.spacing.medium, bottom = Theme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.large),
            content = content,
        )
    }
}

/**
 * 用一张表面容纳相关操作行，行自行控制内边距。
 * @param modifier 分组位置
 * @param content 组内的项目
 */
@Composable
internal fun VaultGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp), content = content)
}

/**
 * 约束图标的光学尺寸和点击目标。
 * @param image 使用的图标
 * @param label 无障碍名称
 * @param enabled 是否可点击
 * @param iconSize 图案的显示尺寸
 * @param onClick 点击回调
 */
@Composable
internal fun VaultIcon(
    image: ImageVector,
    @StringRes label: Int,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick, enabled = enabled,
        minWidth = Theme.sizes.minimumTouchTarget, minHeight = Theme.sizes.minimumTouchTarget,
    ) {
        Icon(
            image, stringResource(label), Modifier.size(iconSize),
            tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 只在确实会打开下一层时使用箭头行。
 * @param title 行标题
 * @param summary 当前值
 * @param enabled 是否可打开
 * @param icon 可识别分类的图标
 * @param onClick 打开下一层
 */
@Composable
internal fun VaultLink(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title, summary = summary, enabled = enabled, onClick = onClick,
        startAction = icon?.let { { Icon(it, null, Modifier.size(22.dp)) } },
        endActions = {
            Icon(MiuixIcons.ChevronForward, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        },
    )
}

/**
 * 记录图形在列表与详情中使用同一视觉规则。
 * @param platform 平台名称，用于显示首个字形
 * @param large 是否用于详情首屏
 */
@Composable
internal fun VaultGlyph(platform: String, large: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val initial = platform.trim().takeIf(String::isNotEmpty)?.let {
        val point = it.codePointAt(0)
        String(Character.toChars(point)).uppercase()
    }
    Box(
        Modifier.size(if (large) 62.dp else 38.dp)
            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(if (large) 21.dp else 13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (initial == null) Icon(MiuixIcons.Lock, null, Modifier.size(20.dp), tint = colors.onSurface)
        else Text(
            initial, color = colors.onSurface, maxLines = 1,
            style = if (large) MiuixTheme.textStyles.title2 else MiuixTheme.textStyles.title4,
        )
    }
}

/**
 * 绘制底部面板中的单选行。
 * @param label 选项名称
 * @param selected 当前是否选中
 * @param enabled 是否可选择
 * @param onClick 选择回调
 */
@Composable
internal fun VaultChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Theme.sizes.minimumTouchTarget)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Theme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 使用 Miuix 确认弹窗处理明确的破坏性或需要再次确认的操作。
 * @param show 可见状态
 * @param title 弹窗标题
 * @param summary 影响说明
 * @param confirm 确认操作文案
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调
 */
@Composable
internal fun VaultConfirm(
    show: Boolean,
    title: String,
    summary: String,
    @StringRes confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(show, title = title, summary = summary, onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
            TextButton(stringResource(R.string.vault_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton(
                stringResource(confirm), onClick = onConfirm, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * 行内显示操作错误并向辅助功能发出提示。
 * @param problem 当前可能存在的问题
 */
@Composable
internal fun VaultErrorText(problem: VaultProblem?) {
    val label = when (problem) {
        null -> return
        VaultProblem.Storage -> R.string.vault_action_error
        VaultProblem.Required -> R.string.vault_required
        VaultProblem.InvalidWebsite -> R.string.vault_invalid_url
        VaultProblem.DuplicateCategory -> R.string.vault_name_duplicate
        VaultProblem.DuplicatePassword -> R.string.vault_password_duplicate
        VaultProblem.Missing -> R.string.vault_missing
        VaultProblem.LastBook -> R.string.vault_last_book
        VaultProblem.BookNotEmpty -> R.string.vault_book_not_empty
    }
    Text(
        stringResource(label), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error,
    )
}
