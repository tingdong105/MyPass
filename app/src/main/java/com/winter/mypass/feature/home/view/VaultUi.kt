package com.winter.mypass.feature.home.view

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.ui.theme.LocalDarkTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 阻止键盘和无障碍焦点进入浮层背后的内容。
 * @return 带焦点组边界的底层页面修饰符
 */
internal fun Modifier.blockVaultBackgroundFocus(): Modifier = this
    .focusProperties { onEnter = { cancelFocusChange() } }
    .focusGroup()
    .semantics { hideFromAccessibility() }

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
 * @param floatingActionButton 右下角浮动操作按钮
 * @param content 页面内容及其窗口边距
 */
@Composable
internal fun VaultFrame(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
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
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { insets ->
        content(Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize())
    }
}

/**
 * 以统一的阅读宽度容纳滚动表单或详情。
 * @param modifier 已处理窗口边距的区域
 * @param horizontalPadding 内容相对页面两侧的边距
 * @param bottomClearance 滚动到底时需要避开的悬浮底栏高度
 * @param content 页面分组
 */
@Composable
internal fun VaultColumn(
    modifier: Modifier,
    horizontalPadding: Dp = Theme.spacing.large,
    bottomClearance: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(top = Theme.spacing.medium, bottom = Theme.spacing.large + bottomClearance),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.large),
            content = content,
        )
    }
}

/**
 * 显示带触感反馈的整行开关。
 * @param label 开关名称
 * @param checked 是否开启
 * @param enabled 是否可以切换
 * @param icon 可选的行首图标
 * @param onChange 保存新的开关状态
 */
@Composable
internal fun VaultSwitch(
    @StringRes label: Int,
    checked: Boolean,
    enabled: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    val view = LocalView.current
    BasicComponent(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onChange(it)
        },
        title = stringResource(label),
        startAction = icon?.let { { Icon(it, null, Modifier.size(22.dp)) } },
        endActions = { Switch(checked, onCheckedChange = null, enabled = enabled) },
    )
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
 * 在记录卡片上统一长按反馈和多选过渡。
 * @param enabled 是否可交互
 * @param selecting 是否处于多选模式
 * @param checked 当前卡片是否已选中
 * @param modifier 列表项修饰符
 * @param onOpen 打开卡片详情
 * @param onSelect 切换卡片选择
 * @param scaleTrailing 普通模式的尾部内容是否随多选过渡缩放
 * @param trailing 普通模式的尾部内容
 * @param content 记录主体
 */
@Composable
internal fun VaultSelectableCard(
    enabled: Boolean,
    selecting: Boolean,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    scaleTrailing: Boolean = true,
    trailing: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val view = LocalView.current
    val toggleState = if (checked) ToggleableState.On else ToggleableState.Off
    val background by key(LocalDarkTheme.current) {
        animateColorAsState(
            if (checked) colors.primary.copy(alpha = 0.12f).compositeOver(colors.surfaceContainer) else colors.surfaceContainer,
            spring(dampingRatio = 0.9f, stiffness = 450f), label = "Card selection color",
        )
    }
    val scale by animateFloatAsState(
        if (checked) 0.98f else 1f,
        spring(dampingRatio = 0.65f, stiffness = 450f), label = "Card selection scale",
    )
    Card(
        modifier = modifier.fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 450f))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics(mergeDescendants = true) {
                role = if (selecting) Role.Checkbox else Role.Button
                if (selecting) toggleableState = toggleState
                if (!enabled) disabled()
            },
        insideMargin = PaddingValues(Theme.spacing.medium),
        colors = CardDefaults.defaultColors(background, colors.onSurfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (!enabled) null else if (selecting) {
            {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSelect()
            }
        } else onOpen,
        onLongPress = onSelect.takeIf { enabled },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            AnimatedContent(
                targetState = selecting,
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = {
                    val appear = fadeIn(tween(100))
                    val disappear = fadeOut(tween(90))
                    val enter = if (scaleTrailing || targetState) {
                        scaleIn(spring(dampingRatio = 0.65f, stiffness = 500f), initialScale = 0.65f) + appear
                    } else appear
                    val exit = if (scaleTrailing || initialState) scaleOut(tween(90), targetScale = 0.65f) + disappear
                    else disappear
                    enter.togetherWith(exit)
                        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.85f, stiffness = 450f) })
                },
                label = "Card selection indicator",
            ) { selectionMode ->
                if (selectionMode) {
                    key(LocalDarkTheme.current) {
                        Box(Modifier.clearAndSetSemantics { }) {
                            Checkbox(toggleState, onClick = null, enabled = enabled)
                        }
                    }
                } else {
                    Box(Modifier.semantics { if (selecting) hideFromAccessibility() }) { trailing() }
                }
            }
        }
    }
}

/**
 * 显示密码记录的圆角标签。
 * @param name 标签名称
 */
@Composable
internal fun VaultTagChip(name: String) {
    val colors = MiuixTheme.colorScheme
    Text(
        name,
        Modifier.background(colors.tertiaryContainer, RoundedCornerShape(percent = 50))
            .padding(horizontal = Theme.spacing.small, vertical = Theme.spacing.small / 2),
        color = colors.onSurface, style = MiuixTheme.textStyles.body2,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
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
 * @param summary 标题下方的补充内容
 * @param enabled 是否可打开
 * @param icon 可识别分类的图标
 * @param value 尾部显示的当前值
 * @param onClick 打开下一层
 */
@Composable
internal fun VaultLink(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    BasicComponent(
        title = title, summary = summary, enabled = enabled, onClick = onClick,
        startAction = icon?.let { { Icon(it, null, Modifier.size(22.dp)) } },
        endActions = {
            if (value != null) Text(
                value, Modifier.weight(1f, fill = false).padding(end = Theme.spacing.small), color = secondary,
                style = MiuixTheme.textStyles.body2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(MiuixIcons.ChevronForward, null, Modifier.size(16.dp), tint = secondary)
        },
    )
}

/**
 * 内置类型显示透明底图标，其余首字搭配稳定的浅色背景。
 * @param name 平台或类型名称，用于显示首字
 * @param type 密码类型，按稳定的内置标识匹配插画
 * @param modifier 图标的位置与尺寸
 */
@Composable
internal fun VaultGlyph(name: String, type: VaultCategory? = null, modifier: Modifier = Modifier) {
    val illustration = when (type?.builtInKey) {
        "software" -> R.drawable.ic_vault_type_software
        "website" -> R.drawable.ic_vault_type_website
        "miniapp" -> R.drawable.ic_vault_type_miniapp
        else -> null
    }
    if (illustration != null) {
        Image(painterResource(illustration), contentDescription = null, modifier = modifier.size(38.dp))
        return
    }
    val colors = MiuixTheme.colorScheme
    val normalizedName = name.trim()
    val initial = normalizedName.takeIf(String::isNotEmpty)?.let {
        val point = it.codePointAt(0)
        String(Character.toChars(point)).uppercase()
    }
    // 名称决定色相，重组和重启时保持配色。
    val hue = Math.floorMod(normalizedName.hashCode(), 360).toFloat()
    val background = Color.hsv(hue, saturation = 0.20f, value = 0.97f)
        .copy(alpha = if (colors.surface.luminance() < 0.5f) 0.22f else 1f)
    Box(
        modifier.size(38.dp).background(background, RoundedCornerShape(percent = 35)),
        contentAlignment = Alignment.Center,
    ) {
        if (initial == null) Icon(MiuixIcons.Lock, null, Modifier.size(20.dp), tint = colors.onSurface)
        else Text(initial, color = colors.onSurface, maxLines = 1, style = MiuixTheme.textStyles.title4)
    }
}

/**
 * 绘制底部面板中的单选行。
 * @param label 选项名称
 * @param selected 当前是否选中
 * @param enabled 是否可选择
 * @param leading 选项名称前的图标
 * @param onClick 选择回调
 */
@Composable
internal fun VaultChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Theme.sizes.minimumTouchTarget)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Theme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        leading?.invoke()
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
 * @param cancel 取消操作文案
 * @param textAlign 标题和说明的对齐方式
 */
@Composable
internal fun VaultConfirm(
    show: Boolean,
    title: String,
    summary: String,
    @StringRes confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    @StringRes cancel: Int = R.string.vault_cancel,
    textAlign: TextAlign = TextAlign.Center,
) {
    val useDefaultAlignment = textAlign == TextAlign.Center
    OverlayDialog(
        show, title = title.takeIf { useDefaultAlignment }, summary = summary.takeIf { useDefaultAlignment },
        onDismissRequest = onDismiss,
    ) {
        if (!useDefaultAlignment) {
            Text(title, Modifier.fillMaxWidth().padding(bottom = Theme.spacing.small),
                style = MiuixTheme.textStyles.title4, textAlign = textAlign)
            Text(summary, Modifier.fillMaxWidth().padding(bottom = Theme.spacing.medium),
                style = MiuixTheme.textStyles.body1, textAlign = textAlign,
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
            TextButton(stringResource(cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
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
        VaultProblem.BookHasTrash -> R.string.vault_book_has_trash
        VaultProblem.BuiltIn -> R.string.vault_built_in
    }
    Text(
        stringResource(label), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error,
    )
}
