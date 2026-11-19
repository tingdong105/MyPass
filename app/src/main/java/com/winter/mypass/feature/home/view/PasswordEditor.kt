package com.winter.mypass.feature.home.view

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.browserWebsite
import com.winter.mypass.feature.home.viewmodel.GeneratorOptions
import com.winter.mypass.feature.home.viewmodel.GeneratorState
import com.winter.mypass.feature.home.viewmodel.VaultPage
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 显示单条记录，集中放置查看和复制密码的操作。
 * @param entry 当前记录
 * @param snapshot 当前可用分类
 * @param busy 是否正在执行操作
 * @param problem 操作错误
 * @param active 页面是否拥有返回事件
 * @param onBack 回到列表
 * @param onEdit 进入编辑
 * @param onDelete 删除当前记录
 * @param onCopy 复制内容及其敏感标记
 */
@Composable
internal fun PasswordDetail(
    entry: PasswordDraft,
    snapshot: VaultSnapshot,
    busy: Boolean,
    problem: VaultProblem?,
    active: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, Boolean) -> Unit,
) {
    var revealed by remember(entry.id) { mutableStateOf(false) }
    var deleting by remember(entry.id) { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    BackHandler(enabled = active, onBack = onBack)
    VaultFrame(
        title = stringResource(R.string.vault_record),
        navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, iconSize = VaultLayout.HeaderIconSize, onClick = onBack) },
        actions = { VaultIcon(MiuixIcons.Edit, R.string.vault_edit, enabled = !busy, iconSize = VaultLayout.HeaderIconSize, onClick = onEdit) },
    ) { modifier ->
        VaultColumn(modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
            ) {
                VaultGlyph(entry.platform, large = true)
                Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    Text(entry.platform, style = MiuixTheme.textStyles.title1)
                    if (entry.account.isNotBlank()) Text(entry.account, color = colors.onSurfaceVariantSummary)
                }
            }

            VaultErrorText(problem)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(Theme.spacing.medium),
                colors = CardDefaults.defaultColors(colors.primary.copy(alpha = 0.10f), colors.onSurface),
            ) {
                Text(
                    stringResource(R.string.vault_password),
                    color = colors.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (revealed) entry.password else "••••••••",
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        style = MiuixTheme.textStyles.title3,
                    )
                    PasswordEye(revealed, !busy) { revealed = !revealed }
                    VaultIcon(MiuixIcons.Copy, R.string.vault_copy_password, enabled = !busy) {
                        onCopy(entry.password, true)
                    }
                }
            }

            if (entry.website.isNotBlank() || entry.notes.isNotBlank()) VaultGroup {
                if (entry.website.isNotBlank()) WebsiteRow(entry.website, !busy) { onCopy(entry.website, false) }
                if (entry.notes.isNotBlank()) {
                    if (entry.website.isNotBlank()) VaultRowDivider()
                    BasicComponent(
                        title = stringResource(R.string.vault_notes),
                        summary = entry.notes,
                    )
                }
            }

            val bookName = snapshot.books.firstOrNull { it.id == entry.bookId }?.name
            val typeName = snapshot.typeName(entry.typeId)
            val tags = snapshot.tags.filter { it.id in entry.tagIds }.joinToString("、") { it.name }
            if (bookName != null || typeName != null || tags.isNotEmpty()) VaultGroup {
                if (bookName != null) BasicComponent(title = stringResource(R.string.vault_book), summary = bookName)
                if (typeName != null) {
                    if (bookName != null) VaultRowDivider()
                    BasicComponent(title = stringResource(R.string.vault_type), summary = typeName)
                }
                if (tags.isNotEmpty()) {
                    if (bookName != null || typeName != null) VaultRowDivider()
                    BasicComponent(title = stringResource(R.string.vault_tags), summary = tags)
                }
            }
            TextButton(
                stringResource(R.string.vault_delete_record),
                onClick = { deleting = true }, enabled = !busy, modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(color = colors.errorContainer, textColor = colors.error),
            )
        }
        VaultConfirm(
            active && deleting, stringResource(R.string.vault_delete_record),
            stringResource(R.string.vault_confirm_record, entry.platform), R.string.vault_delete,
            onDismiss = { deleting = false }, onConfirm = {
                deleting = false
                onDelete()
            },
        )
    }
}

/**
 * 网址行的主操作是打开浏览器，次操作是复制。
 * @param address 保存的网址
 * @param enabled 是否接受点击
 * @param onCopy 复制网址
 */
@Composable
private fun WebsiteRow(address: String, enabled: Boolean, onCopy: () -> Unit) {
    val context = LocalContext.current
    val website = remember(address) { browserWebsite(address) }
    BasicComponent(
        title = stringResource(R.string.vault_website), summary = address,
        enabled = enabled,
        onClickLabel = stringResource(R.string.vault_open_url),
        onClick = website?.let { url ->
            {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.vault_no_browser, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.vault_no_browser, Toast.LENGTH_SHORT).show()
                }
            }
        },
        startAction = { Icon(MiuixIcons.Link, null, Modifier.size(22.dp)) },
        endActions = { VaultIcon(MiuixIcons.Copy, R.string.vault_copy_url, enabled, onClick = onCopy) },
    )
}

/**
 * 编辑记录及其分类，可在同一表单生成并填入密码。
 * @param editor 原始记录与当前草稿
 * @param snapshot 可用分类
 * @param generator 生成器状态
 * @param busy 是否正在保存或生成
 * @param problem 当前错误
 * @param active 页面是否拥有返回事件
 * @param onDraft 更新草稿
 * @param onSave 保存草稿及是否允许重复
 * @param onBack 离开编辑
 * @param onClearProblem 清除错误
 * @param onGeneratorOptions 更新生成选项
 * @param onRefreshGenerated 刷新当前密码
 * @param passwordClipboard 密码输入框专用的敏感剪贴板
 */
@Composable
internal fun PasswordEditor(
    editor: VaultPage.Editor,
    snapshot: VaultSnapshot,
    generator: GeneratorState,
    busy: Boolean,
    problem: VaultProblem?,
    active: Boolean,
    onDraft: (PasswordDraft) -> Unit,
    onSave: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClearProblem: () -> Unit,
    onGeneratorOptions: (GeneratorOptions) -> Unit,
    onRefreshGenerated: () -> Unit,
    passwordClipboard: Clipboard,
) {
    val draft = editor.draft
    val focus = LocalFocusManager.current
    var revealed by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(CategoryKind.Book) }
    var picking by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }

    val requestBack: () -> Unit = {
        if (!busy) {
            focus.clearFocus()
            if (editor.original == draft) onBack() else discarding = true
        }
    }

    BackHandler(enabled = active, onBack = requestBack)
    VaultFrame(
        title = stringResource(if (draft.id == null) R.string.vault_add else R.string.vault_edit),
        navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, enabled = !busy, iconSize = VaultLayout.HeaderIconSize, onClick = requestBack) },
        actions = {
            TextButton(
                stringResource(R.string.vault_save), onClick = {
                    focus.clearFocus()
                    onSave(false)
                },
                enabled = !busy && draft.platform.isNotBlank() && draft.password.isNotEmpty(),
                minHeight = Theme.sizes.minimumTouchTarget,
                insideMargin = PaddingValues(horizontal = Theme.spacing.medium, vertical = Theme.spacing.small),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    ) { modifier ->
        VaultColumn(modifier) {
            VaultErrorText(problem?.takeUnless { it == VaultProblem.DuplicatePassword })
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                TextField(
                    draft.platform, { onDraft(draft.copy(platform = it)) }, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.vault_platform), singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                TextField(
                    draft.account, { onDraft(draft.copy(account = it)) }, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.vault_account), singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                CompositionLocalProvider(LocalClipboard provides passwordClipboard) {
                    TextField(
                        draft.password, { onDraft(draft.copy(password = it)) }, Modifier.fillMaxWidth(),
                        label = stringResource(R.string.vault_password), singleLine = true, enabled = !busy,
                        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next,
                        ),
                        trailingIcon = { PasswordEye(revealed, !busy) { revealed = !revealed } },
                    )
                }
                VaultLink(stringResource(R.string.vault_generate_password), enabled = !busy, icon = MiuixIcons.Tune) {
                    focus.clearFocus()
                    onClearProblem()
                    generating = true
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                TextField(
                    draft.website, { onDraft(draft.copy(website = it)) }, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.vault_website), singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
                TextField(
                    draft.notes, { onDraft(draft.copy(notes = it)) }, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.vault_notes), enabled = !busy, minLines = 3, maxLines = 6,
                )
            }

            val showPicker: (CategoryKind) -> Unit = {
                focus.clearFocus()
                picker = it
                picking = true
            }
            val tags = snapshot.tags.filter { it.id in draft.tagIds }.joinToString("、") { it.name }
            VaultGroup {
                VaultLink(
                    stringResource(R.string.vault_book), snapshot.books.firstOrNull { it.id == draft.bookId }?.name,
                    enabled = !busy, icon = MiuixIcons.Folder,
                ) { showPicker(CategoryKind.Book) }
                VaultRowDivider()
                VaultLink(
                    stringResource(R.string.vault_type), snapshot.typeName(draft.typeId),
                    enabled = !busy, icon = MiuixIcons.Layers,
                ) { showPicker(CategoryKind.Type) }
                VaultRowDivider()
                VaultLink(
                    stringResource(R.string.vault_tags), tags.ifEmpty { null },
                    enabled = !busy, icon = MiuixIcons.Pin,
                ) { showPicker(CategoryKind.Tag) }
            }
        }

        CategoryPicker(
            active && picking, picker, snapshot, draft, onDraft,
            onDismiss = { picking = false },
        )

        OverlayBottomSheet(
            active && generating, title = stringResource(R.string.vault_generate_password),
            onDismissRequest = { generating = false }, allowDismiss = !busy,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Theme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
            ) {
                VaultErrorText(problem?.takeUnless { it == VaultProblem.DuplicatePassword })
                GeneratorPanel(generator, busy, onGeneratorOptions, onRefreshGenerated, onUse = {
                    onDraft(draft.copy(password = it))
                    generating = false
                })
            }
        }

        VaultConfirm(
            active && discarding, stringResource(R.string.vault_discard_title),
            stringResource(R.string.vault_discard_message), R.string.vault_discard,
            onDismiss = { discarding = false }, onConfirm = {
                discarding = false
                onBack()
            },
        )
        VaultConfirm(
            active && problem == VaultProblem.DuplicatePassword,
            stringResource(R.string.vault_duplicate_title), stringResource(R.string.vault_password_duplicate),
            R.string.vault_save_anyway, onDismiss = onClearProblem, onConfirm = { onSave(true) },
        )
    }
}

/**
 * 密码可见性按钮使用相同的点击和无障碍语义。
 * @param revealed 密码是否可见
 * @param enabled 是否可切换
 * @param onToggle 切换可见性
 */
@Composable
private fun PasswordEye(revealed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    VaultIcon(
        if (revealed) MiuixIcons.Hide else MiuixIcons.Show,
        if (revealed) R.string.vault_hide else R.string.vault_show,
        enabled, onClick = onToggle,
    )
}

/** 预览密码详情的分组和复制操作。 */
@VaultPreviews
@Composable
internal fun PasswordDetailPreview() {
    val entry = previewEntry()
    MyPassTheme {
        PasswordDetail(entry, previewSnapshot(), false, null, true, {}, {}, {}, { _, _ -> })
    }
}

/** 预览现有记录的编辑表单。 */
@VaultPreviews
@Composable
internal fun PasswordEditorPreview() {
    val entry = previewEntry()
    MyPassTheme {
        PasswordEditor(
            VaultPage.Editor(entry, entry), previewSnapshot(), GeneratorState(), false, null, true,
            {}, {}, {}, {}, {}, {}, LocalClipboard.current,
        )
    }
}
