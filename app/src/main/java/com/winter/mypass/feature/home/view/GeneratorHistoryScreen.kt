package com.winter.mypass.feature.home.view

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.GeneratedPasswordCopy
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 显示已复制的生成密码，支持重新复制或长按多选删除。
 * @param entries 历史记录，空值表示等待加载
 * @param busy 是否有正在处理的操作
 * @param problem 最近一次操作失败
 * @param active 是否为前台页面
 * @param onBack 返回生成器
 * @param onRetry 重试加载
 * @param onCopy 重新复制一条历史密码
 * @param onDelete 删除选中的记录并在成功后回调
 */
@Composable
internal fun GeneratorHistoryScreen(
    entries: List<GeneratedPasswordCopy>?,
    busy: Boolean,
    problem: VaultProblem?,
    active: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (Long) -> Unit,
    onDelete: (Set<Long>, () -> Unit) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val enabled = active && !busy
    val view = LocalView.current
    val leave = {
        if (enabled) {
            when {
                confirmingDelete -> confirmingDelete = false
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
        title = if (selecting) pluralStringResource(R.plurals.vault_history_selected, selected.size, selected.size)
        else stringResource(R.string.vault_generator_history),
        navigationIcon = {
            VaultIcon(
                if (selecting) MiuixIcons.Close else MiuixIcons.Back,
                if (selecting) R.string.vault_history_clear_selection else R.string.lock_back,
                enabled = enabled, iconSize = VaultLayout.HeaderIconSize,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                leave()
            }
        },
        actions = {
            if (selecting) {
                VaultIcon(MiuixIcons.Delete, R.string.vault_delete, enabled = enabled, iconSize = VaultLayout.HeaderIconSize) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    confirmingDelete = true
                }
            }
        },
    ) { modifier ->
        Box(modifier, contentAlignment = Alignment.TopCenter) {
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
                    Text(stringResource(R.string.vault_history_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Theme.spacing.medium, vertical = Theme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                ) {
                    if (problem != null) item { VaultErrorText(problem) }
                    items(entries, key = { it.id }) { entry ->
                        GeneratorHistoryRow(
                            entry, selecting, entry.id in selected, enabled,
                            onClick = { if (selecting) toggle(entry.id) else onCopy(entry.id) },
                            onSelect = { toggle(entry.id) },
                        )
                    }
                }
            }
        }
        VaultConfirm(
            show = active && confirmingDelete,
            title = stringResource(R.string.vault_history_delete_title),
            summary = pluralStringResource(R.plurals.vault_history_delete_summary, selected.size, selected.size),
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
    }
}

/**
 * 展示密码、复制时间和当前选择状态。
 * @param entry 复制历史
 * @param selecting 是否处于多选模式
 * @param checked 当前记录是否选中
 * @param enabled 是否可交互
 * @param onClick 复制或切换选择
 * @param onSelect 长按选择
 */
@Composable
private fun GeneratorHistoryRow(
    entry: GeneratedPasswordCopy,
    selecting: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = MiuixTheme.colorScheme
    val toggleState = if (checked) ToggleableState.On else ToggleableState.Off
    val copiedAt = remember(entry.copiedAt, LocalConfiguration.current) {
        DateUtils.formatDateTime(context, entry.copiedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
    }
    VaultGroup {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Theme.sizes.minimumTouchTarget)
                .then(if (checked) Modifier.background(colors.primary.copy(alpha = 0.12f)) else Modifier)
                .combinedClickable(
                    enabled = enabled,
                    role = if (selecting) Role.Checkbox else Role.Button,
                    onClickLabel = if (selecting) null else stringResource(R.string.vault_copy_password),
                    onLongClickLabel = stringResource(R.string.vault_history_select_row),
                    hapticFeedbackEnabled = false,
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onSelect()
                    },
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    },
                )
                .semantics { if (selecting) toggleableState = toggleState }
                .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2)) {
                Text(entry.password, style = MiuixTheme.textStyles.body1, fontFamily = FontFamily.Monospace)
                Text(copiedAt, style = MiuixTheme.textStyles.body2, color = colors.onSurfaceVariantSummary)
            }
            if (selecting) {
                Checkbox(toggleState, onClick = null, enabled = enabled)
            } else {
                Icon(MiuixIcons.Copy, null, Modifier.size(22.dp), tint = colors.onSurfaceVariantSummary)
            }
        }
    }
}

/**
 * 预览历史记录在不同屏幕与主题下的显示。
 */
@VaultPreviews
@Composable
private fun GeneratorHistoryPreview() {
    MyPassTheme {
        GeneratorHistoryScreen(
            entries = listOf(
                GeneratedPasswordCopy(2, "tHk3qA7e9#nF!Y5c", 1_795_161_600_000),
                GeneratedPasswordCopy(1, "G6M5a!rq8pZf#d2z".repeat(4), 1_795_158_000_000),
            ),
            busy = false, problem = null, active = true,
            onBack = {}, onRetry = {}, onCopy = {}, onDelete = { _, _ -> },
        )
    }
}
