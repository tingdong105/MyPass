package com.winter.mypass.feature.home.view

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.TrashEntry
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 显示已删除的密码，支持恢复或永久删除。
 * @param entries 回收站记录，空值表示等待加载
 * @param types 可识别的密码类型
 * @param busy 是否有正在处理的操作
 * @param problem 最近一次操作失败
 * @param active 是否为前台页面
 * @param confirmingRestore 是否正在确认重复记录
 * @param onBack 关闭详情或返回设置
 * @param onRetry 重试加载
 * @param onOpenDetail 打开单条记录的详情
 * @param onRestore 恢复选中的记录并在成功后回调
 * @param onConfirmRestore 确认恢复并在成功后回调
 * @param onDismissRestore 取消本次恢复
 * @param onDelete 永久删除选中的记录并在成功后回调
 * @param onEmpty 清空回收站
 * @param detailContent 需要在页面浮层宿主内显示的详情
 */
@Composable
internal fun TrashScreen(
    entries: List<TrashEntry>?,
    types: List<VaultCategory>,
    busy: Boolean,
    problem: VaultProblem?,
    active: Boolean,
    confirmingRestore: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onRestore: (Set<Long>, () -> Unit) -> Unit,
    onConfirmRestore: (() -> Unit) -> Unit,
    onDismissRestore: () -> Unit,
    onDelete: (Set<Long>, () -> Unit) -> Unit,
    onEmpty: () -> Unit,
    detailContent: (@Composable () -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingEmpty by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val detailOpen = detailContent != null
    val enabled = active && !busy && !detailOpen && !confirmingRestore
    val view = LocalView.current
    val leave = {
        if (active && !busy) {
            when {
                confirmingRestore -> onDismissRestore()
                detailOpen -> onBack()
                confirmingDelete -> confirmingDelete = false
                confirmingEmpty -> confirmingEmpty = false
                selecting -> selected = emptySet()
                else -> onBack()
            }
        }
    }
    val toggle: (Long) -> Unit = { id ->
        selected = if (id in selected) selected - id else selected + id
    }
    BackHandler(active, onBack = leave)
    VaultFrame(
        title = if (selecting) pluralStringResource(R.plurals.vault_trash_selected, selected.size, selected.size)
        else stringResource(R.string.vault_trash),
        navigationIcon = {
            VaultIcon(
                if (selecting) MiuixIcons.Close else MiuixIcons.Back,
                if (selecting) R.string.vault_trash_clear_selection else R.string.lock_back,
                enabled = enabled, iconSize = VaultLayout.HeaderIconSize,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                leave()
            }
        },
        actions = {
            TrashToolbarActions(
                selecting = selecting,
                hasEntries = !entries.isNullOrEmpty(),
                enabled = enabled,
                onRestore = { onRestore(selected) { selected = emptySet() } },
                onDelete = { confirmingDelete = true },
                onEmpty = { confirmingEmpty = true },
            )
        },
    ) { modifier ->
        val background = if (detailOpen || confirmingRestore) Modifier.blockVaultBackgroundFocus() else Modifier
        Box(modifier.then(background), contentAlignment = Alignment.TopCenter) {
            when {
                entries == null -> Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    if (problem == null) CircularProgressIndicator()
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VaultErrorText(problem)
                        TextButton(stringResource(R.string.lock_retry), onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onRetry()
                        }, enabled = enabled)
                    }
                }
                entries.isEmpty() -> Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.vault_trash_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Theme.spacing.medium, vertical = Theme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                ) {
                    if (problem != null && !detailOpen) item { VaultErrorText(problem) }
                    items(entries, key = { it.id }) { entry ->
                        TrashRow(
                            entry = entry,
                            type = types.firstOrNull { it.id == entry.typeId },
                            selecting = selecting,
                            checked = entry.id in selected,
                            enabled = enabled,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(120),
                                placementSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                fadeOutSpec = tween(130),
                            ),
                            onOpen = { onOpenDetail(entry.id) },
                            onRestore = { onRestore(setOf(entry.id)) {} },
                            onSelect = { toggle(entry.id) },
                        )
                    }
                }
            }
        }
        VaultConfirm(
            show = active && confirmingDelete,
            title = stringResource(R.string.vault_trash_delete_title),
            summary = pluralStringResource(R.plurals.vault_trash_delete_summary, selected.size, selected.size),
            confirm = R.string.vault_delete,
            onDismiss = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                confirmingDelete = false
            },
            onConfirm = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                confirmingDelete = false
                onDelete(selected) { selected = emptySet() }
            },
        )
        VaultConfirm(
            show = active && confirmingEmpty,
            title = stringResource(R.string.vault_trash_clear),
            summary = pluralStringResource(R.plurals.vault_trash_clear_summary, entries?.size ?: 0, entries?.size ?: 0),
            confirm = R.string.vault_delete,
            onDismiss = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                confirmingEmpty = false
            },
            onConfirm = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                confirmingEmpty = false
                onEmpty()
            },
        )
        detailContent?.invoke()
        VaultConfirm(
            show = active && confirmingRestore,
            title = stringResource(R.string.vault_duplicate_title),
            summary = stringResource(R.string.vault_trash_restore_duplicate),
            confirm = R.string.vault_restore,
            onDismiss = onDismissRestore,
            onConfirm = { onConfirmRestore { selected = emptySet() } },
        )
    }
}

/** 当前工具栏显示的回收站操作。 */
private enum class TrashActions { None, Clear, Selection }

/**
 * 随回收站模式切换工具栏按钮。
 * @param selecting 是否处于多选模式
 * @param hasEntries 是否有可清空的记录
 * @param enabled 是否允许操作
 * @param onRestore 恢复所选记录
 * @param onDelete 确认永久删除所选记录
 * @param onEmpty 确认清空回收站
 */
@Composable
private fun TrashToolbarActions(
    selecting: Boolean,
    hasEntries: Boolean,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onEmpty: () -> Unit,
) {
    val target = when {
        selecting -> TrashActions.Selection
        hasEntries -> TrashActions.Clear
        else -> TrashActions.None
    }
    AnimatedContent(
        targetState = target,
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            (slideInVertically(tween(160)) { it / 6 } + fadeIn(tween(140)))
                .togetherWith(slideOutVertically(tween(110)) { -it / 6 } + fadeOut(tween(100)))
                .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.9f, stiffness = 450f) })
        },
        label = "Trash toolbar actions",
    ) { actions ->
        val current = actions == target
        Row(Modifier.semantics { if (!current) hideFromAccessibility() }) {
            when (actions) {
                TrashActions.None -> Unit
                TrashActions.Clear -> TrashActionIcon(
                    ImageVector.vectorResource(R.drawable.ic_vault_trash_broom),
                    R.string.vault_trash_clear, enabled && current, onClick = onEmpty,
                )
                TrashActions.Selection -> {
                    TrashActionIcon(
                        ImageVector.vectorResource(R.drawable.ic_vault_trash_restore),
                        R.string.vault_restore, enabled && current, onClick = onRestore,
                    )
                    TrashActionIcon(
                        MiuixIcons.Delete, R.string.vault_trash_delete_title,
                        enabled && current, destructive = true, onClick = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * 以固定尺寸和配色展示操作图标。
 * @param image 按钮图案
 * @param label 无障碍名称
 * @param enabled 是否允许点击
 * @param destructive 是否强调永久删除
 * @param onClick 执行用户选择的操作
 */
@Composable
internal fun TrashActionIcon(
    image: ImageVector,
    @StringRes label: Int,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val view = LocalView.current
    Box(
        modifier = Modifier.size(Theme.sizes.minimumTouchTarget).clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = null,
            indication = null,
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            image, stringResource(label), Modifier.size(VaultLayout.HomeHeaderIconSize),
            tint = if (destructive) colors.error else colors.primary,
        )
    }
}

/**
 * 展示回收站条目的名称和删除时间。
 * @param entry 回收站记录
 * @param type 记录关联的密码类型
 * @param selecting 是否处于多选模式
 * @param checked 当前记录是否选中
 * @param enabled 是否可交互
 * @param modifier 列表项的增删和位置动画
 * @param onOpen 打开详情
 * @param onRestore 恢复当前记录
 * @param onSelect 长按选择
 */
@Composable
private fun TrashRow(
    entry: TrashEntry,
    type: VaultCategory?,
    selecting: Boolean,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val deleted = stringResource(R.string.vault_trash_deleted_info, trashDeletionTime(entry.deletedAt))
    VaultSelectableCard(
        enabled = enabled,
        selecting = selecting,
        checked = checked,
        modifier = modifier,
        onOpen = onOpen,
        onSelect = onSelect,
        scaleTrailing = false,
        trailing = {
            TrashActionIcon(
                ImageVector.vectorResource(R.drawable.ic_vault_trash_restore),
                R.string.vault_restore, enabled && !selecting, onClick = onRestore,
            )
        },
    ) {
        VaultGlyph(entry.platform, type)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2),
        ) {
            Text(entry.platform, style = MiuixTheme.textStyles.title3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(deleted, style = MiuixTheme.textStyles.body2, color = colors.onSurfaceVariantSummary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 按系统区域设置显示回收站记录的删除时间。
 * @param deletedAt 毫秒时间戳
 * @return 本地化日期和时间
 */
@Composable
internal fun trashDeletionTime(deletedAt: Long): String {
    val context = LocalContext.current
    return remember(deletedAt, LocalConfiguration.current) {
        DateUtils.formatDateTime(context, deletedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
    }
}

/**
 * 预览回收站在不同屏幕与主题下的显示。
 */
@VaultPreviews
@Composable
private fun TrashPreview() {
    MyPassTheme {
        TrashScreen(
            entries = listOf(
                TrashEntry(2, "微信", null, 1_795_161_600_000),
                TrashEntry(1, "GitHub", null, 1_795_158_000_000),
            ),
            types = emptyList(), busy = false, problem = null, active = true, confirmingRestore = false,
            onBack = {}, onRetry = {}, onOpenDetail = {}, onRestore = { _, _ -> },
            onConfirmRestore = {}, onDismissRestore = {}, onDelete = { _, _ -> }, onEmpty = {},
        )
    }
}
