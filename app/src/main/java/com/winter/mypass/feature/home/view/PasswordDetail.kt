package com.winter.mypass.feature.home.view

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.PasswordPreviewSettings
import com.winter.mypass.feature.home.model.PasswordVisibility
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.viewmodel.DetailPhase
import com.winter.mypass.feature.home.viewmodel.VaultPage
import com.winter.mypass.feature.home.viewmodel.VaultState
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 在密码本上方显示详情和复制、编辑及删除操作。
 * @param detail 当前记录及 Sheet 阶段
 * @param snapshot 当前可用分类
 * @param preview 当前密码显示模式与遮罩符号
 * @param busy 是否正在执行操作
 * @param problem 操作错误
 * @param onDismiss 开始收起详情
 * @param onDismissFinished 完成退场后移除详情
 * @param onEdit 进入编辑
 * @param onDelete 删除当前记录
 * @param onCopy 复制内容，按需通知成功
 * @param onRestore 恢复回收站中的记录
 */
@Composable
internal fun PasswordDetailSheet(
    detail: VaultPage.Detail,
    snapshot: VaultSnapshot,
    preview: PasswordPreviewSettings,
    busy: Boolean,
    problem: VaultProblem?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onCopy: (String, Boolean, (() -> Unit)?) -> Unit,
    onRestore: (() -> Unit)? = null,
) {
    val entry = detail.draft
    val open = detail.phase == DetailPhase.Open
    var deleting by remember(entry.id) { mutableStateOf(false) }
    var visibility by remember(entry.id, preview) { mutableStateOf(preview.visibility) }
    val enabled = open && !busy && !deleting
    val sheetTitle = stringResource(R.string.vault_record)

    LaunchedEffect(Unit) {
        // 页面重建时补上被中断的退场回调。
        if (!open) onDismissFinished()
    }

    OverlayBottomSheet(
        show = open,
        modifier = Modifier.semantics { paneTitle = sheetTitle },
        sheetMaxWidth = VaultLayout.PaneWidth,
        insideMargin = DpSize(Theme.spacing.medium, 0.dp),
        allowDismiss = enabled,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        PasswordDetailContent(
            entry = entry,
            snapshot = snapshot,
            preview = preview.copy(visibility = visibility),
            enabled = enabled,
            problem = problem,
            onDismiss = onDismiss,
            onEdit = onEdit,
            onRequestDelete = onDelete?.let { { deleting = true } },
            onCopy = onCopy,
            onTogglePassword = {
                visibility = if (visibility == PasswordVisibility.Full) PasswordVisibility.Hidden else PasswordVisibility.Full
            },
            deletedAt = detail.deletedAt,
            onRestore = onRestore,
        )
    }

    if (onDelete != null) {
        VaultConfirm(
            open && deleting, stringResource(R.string.vault_delete_record),
            stringResource(R.string.vault_confirm_record, entry.platform), R.string.vault_delete,
            onDismiss = { deleting = false }, onConfirm = {
                deleting = false
                onDelete()
            },
        )
    }
}

/**
 * 排列详情标题、密码操作和补充字段。
 * @param entry 当前记录
 * @param snapshot 当前可用分类
 * @param preview 当前密码显示模式与遮罩符号
 * @param enabled 是否接受操作
 * @param problem 操作错误
 * @param onDismiss 关闭详情
 * @param onEdit 编辑记录
 * @param onRequestDelete 请求删除确认
 * @param onCopy 复制内容，按需通知成功
 * @param onTogglePassword 临时切换密码显示与隐藏
 * @param deletedAt 回收站记录的删除时间
 * @param onRestore 恢复回收站中的记录
 */
@Composable
private fun PasswordDetailContent(
    entry: PasswordDraft,
    snapshot: VaultSnapshot,
    preview: PasswordPreviewSettings,
    enabled: Boolean,
    problem: VaultProblem?,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onRequestDelete: (() -> Unit)?,
    onCopy: (String, Boolean, (() -> Unit)?) -> Unit,
    onTogglePassword: () -> Unit,
    deletedAt: Long? = null,
    onRestore: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = Theme.spacing.medium, end = Theme.spacing.small, bottom = Theme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.platform, Modifier.weight(1f).padding(end = Theme.spacing.small).semantics { heading() },
                style = MiuixTheme.textStyles.title3, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (onEdit != null) VaultIcon(MiuixIcons.Edit, R.string.vault_edit, enabled, onClick = onEdit)
            if (onRestore != null) {
                TrashActionIcon(
                    ImageVector.vectorResource(R.drawable.ic_vault_trash_restore),
                    R.string.vault_restore, enabled = enabled, onClick = onRestore,
                )
            }
            VaultIcon(MiuixIcons.Close, R.string.vault_close, enabled, onClick = onDismiss)
        }

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Theme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
        ) {
            VaultErrorText(problem)
            DetailPasswordCard(
                password = preview.display(entry.password),
                revealed = preview.visibility == PasswordVisibility.Full,
                enabled = enabled,
                onTogglePassword = onTogglePassword,
            ) { onCopied ->
                onCopy(entry.password, true, onCopied)
            }

            val bookName = snapshot.books.firstOrNull { it.id == entry.bookId }?.name
            val typeName = snapshot.typeName(entry.typeId)
            val tags = snapshot.tags.filter { it.id in entry.tagIds }
            val deletedTime = deletedAt?.let { trashDeletionTime(it) }
            val fields = listOf(
                Triple(R.string.vault_account, MiuixIcons.Contacts, entry.account),
                Triple(R.string.vault_website, MiuixIcons.Link, entry.website),
                Triple(R.string.vault_email, MiuixIcons.Email, entry.email),
                Triple(R.string.vault_notes, MiuixIcons.Notes, entry.notes),
                Triple(R.string.vault_book, MiuixIcons.Folder, bookName),
                Triple(R.string.vault_type, MiuixIcons.Layers, typeName),
                Triple(
                    R.string.vault_trash_deleted_at,
                    ImageVector.vectorResource(R.drawable.ic_vault_trash_deleted_at),
                    deletedTime,
                ),
            ).filter { !it.third.isNullOrBlank() }
            VaultGroup {
                fields.forEachIndexed { index, (label, icon, value) ->
                    if (index > 0) DetailFieldDivider()
                    key(label) {
                        val text = value.orEmpty()
                        DetailField(label, icon, text, enabled) { onCopy(text, false, null) }
                    }
                }
                if (tags.isNotEmpty()) {
                    if (fields.isNotEmpty()) DetailFieldDivider()
                    DetailTags(tags, enabled) {
                        onCopy(tags.joinToString("、") { it.name }, false, null)
                    }
                }
                if (onRequestDelete != null) {
                    if (fields.isNotEmpty() || tags.isNotEmpty()) DetailFieldDivider()
                    DetailListRow(
                        label = R.string.vault_delete_record, icon = MiuixIcons.Delete,
                        enabled = enabled, destructive = true, onClick = onRequestDelete,
                    ) {
                        Icon(
                            MiuixIcons.ChevronForward, null, Modifier.size(Theme.spacing.medium),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 在独立卡片显示密码及可见性和复制操作。
 * @param password 按当前模式处理的密码文本
 * @param revealed 是否完整显示密码
 * @param enabled 是否接受操作
 * @param onTogglePassword 临时切换密码显示与隐藏
 * @param onCopy 复制原密码并通知成功
 */
@Composable
private fun DetailPasswordCard(
    password: String,
    revealed: Boolean,
    enabled: Boolean,
    onTogglePassword: () -> Unit,
    onCopy: (() -> Unit) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(
            start = Theme.spacing.medium, end = Theme.spacing.small,
            top = Theme.spacing.medium, bottom = Theme.spacing.medium,
        ),
        colors = CardDefaults.defaultColors(colors.primary.copy(alpha = 0.10f), colors.onSurface),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                Text(
                    stringResource(R.string.vault_password),
                    color = colors.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2,
                )
                Text(password, style = MiuixTheme.textStyles.title2, fontFamily = FontFamily.Monospace)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = if (enabled) colors.onSurface else colors.onSurfaceVariantSummary
                VaultPasswordVisibilityButton(revealed, enabled, tint, onClick = onTogglePassword)
                VaultCopyButton(enabled, tint = tint, onCopy = onCopy)
            }
        }
    }
}

/**
 * 左右对齐表单字段，点击整行复制字段值。
 * @param label 本地化字段名
 * @param icon 字段图标
 * @param value 字段内容
 * @param enabled 是否接受点击
 * @param onCopy 复制字段值
 */
@Composable
private fun DetailField(
    @StringRes label: Int,
    icon: ImageVector,
    value: String,
    enabled: Boolean,
    onCopy: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    DetailListRow(
        label, icon, enabled, onClickLabel = stringResource(R.string.vault_copy_action),
        onClick = onCopy,
    ) {
        Text(
            value, Modifier.weight(2f), textAlign = TextAlign.End,
            color = colors.onSurface, style = MiuixTheme.textStyles.body1,
        )
    }
}

/**
 * 将标签靠右排列，空间不足时换行。
 * @param tags 当前记录的标签
 * @param enabled 是否允许复制
 * @param onCopy 复制整行标签
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTags(tags: List<VaultCategory>, enabled: Boolean, onCopy: () -> Unit) {
    DetailListRow(
        R.string.vault_tags, MiuixIcons.Pin, enabled,
        onClickLabel = stringResource(R.string.vault_copy_action),
        onClick = onCopy,
    ) {
        FlowRow(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2),
        ) {
            for (tag in tags) VaultTagChip(tag.name)
        }
    }
}

/**
 * 为详情的字段和操作提供统一的列表行。
 * @param label 字段或操作名称
 * @param icon 名称前的图标
 * @param enabled 是否可操作
 * @param destructive 是否使用删除操作的强调色
 * @param onClickLabel 无障碍点击提示
 * @param onClick 行的点击操作
 * @param trailing 行尾的值或箭头
 */
@Composable
private fun DetailListRow(
    @StringRes label: Int,
    icon: ImageVector,
    enabled: Boolean,
    destructive: Boolean = false,
    onClickLabel: String? = null,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val tint = if (destructive) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Theme.sizes.minimumTouchTarget)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.small + Theme.spacing.small / 2),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(
            stringResource(label), Modifier.weight(1f).padding(start = Theme.spacing.small / 2),
            color = tint, style = MiuixTheme.textStyles.body1,
        )
        trailing()
    }
}

/**
 * 绘制与字段文字内边距对齐的虚线。
 */
@Composable
private fun DetailFieldDivider() {
    val color = MiuixTheme.colorScheme.dividerLine
    Spacer(
        Modifier.fillMaxWidth().padding(horizontal = Theme.spacing.medium).height(1.dp).drawBehind {
            drawLine(
                color, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        },
    )
}

/** 预览详情 Sheet 的展开布局。 */
@VaultPreviews
@Composable
internal fun PasswordDetailPreview() {
    val preview = PasswordPreviewSettings()
    val sample = previewSnapshot(preview)
    val tags = sample.tags + listOf(
        VaultCategory(2, "常用服务", 1),
        VaultCategory(3, "个人账号", 1),
        VaultCategory(4, "开发与部署", 1),
    )
    val detail = VaultPage.Detail(previewEntry().copy(tagIds = tags.map(VaultCategory::id).toSet()))
    val snapshot = sample.copy(tags = tags)
    MyPassTheme {
        val colors = MiuixTheme.colorScheme
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            VaultTabbedPreview(VaultState(snapshot = snapshot, page = detail, passwordPreview = preview))
            Box(Modifier.fillMaxSize().background(colors.windowDimming))
            Card(
                modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
                cornerRadius = BottomSheetDefaults.cornerRadius,
                insideMargin = PaddingValues(horizontal = Theme.spacing.medium),
                colors = CardDefaults.defaultColors(BottomSheetDefaults.backgroundColor(), colors.onSurface),
            ) {
                Spacer(
                    Modifier.align(Alignment.CenterHorizontally).padding(vertical = Theme.spacing.medium)
                        .width(32.dp).height(4.dp)
                        .background(BottomSheetDefaults.dragHandleColor(), RoundedCornerShape(percent = 50)),
                )
                PasswordDetailContent(
                    detail.draft, snapshot, preview, true, null, {}, {}, {}, { _, _, _ -> }, {},
                )
            }
        }
    }
}
