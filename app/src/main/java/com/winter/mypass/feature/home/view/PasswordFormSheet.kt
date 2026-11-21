package com.winter.mypass.feature.home.view

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.viewmodel.EditorPhase
import com.winter.mypass.feature.home.viewmodel.VaultPage
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 挂载新增和编辑表单及其选择器和确认弹窗。
 * @param editor 原记录、当前草稿和显示阶段
 * @param snapshot 可用密码本和分类
 * @param busy 是否正在保存
 * @param problem 保存错误
 * @param passwordClipboard 密码输入框的敏感剪贴板
 * @param onDraft 更新草稿
 * @param onSave 保存草稿及是否允许重复
 * @param onDismiss 请求关闭表单
 * @param onDismissFinished 处理退场完成
 * @param onDiscard 确认放弃修改或继续编辑
 * @param onClearProblem 清除重复记录提示
 */
@Composable
internal fun PasswordFormSheet(
    editor: VaultPage.Editor,
    snapshot: VaultSnapshot,
    busy: Boolean,
    problem: VaultProblem?,
    passwordClipboard: Clipboard,
    onDraft: ((PasswordDraft) -> PasswordDraft) -> Unit,
    onSave: (Boolean) -> Unit,
    onDismiss: () -> Boolean,
    onDismissFinished: () -> Unit,
    onDiscard: (Boolean) -> Unit,
    onClearProblem: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf(CategoryKind.Tag) }
    var picking by remember { mutableStateOf(false) }
    val open = editor.phase == EditorPhase.Open
    val enabled = open && !busy
    val showPicker = open && picking
    val duplicate = enabled && problem == VaultProblem.DuplicatePassword
    val dismiss: () -> Unit = {
        focus.clearFocus()
        onDismiss()
    }

    LaunchedEffect(Unit) {
        // 重建页面后补上已中断的退场回调。
        if (editor.phase.isClosing) onDismissFinished()
    }
    LaunchedEffect(problem) {
        if (problem == VaultProblem.InvalidWebsite) expanded = true
    }

    OverlayBottomSheet(
        show = open,
        title = stringResource(if (editor.draft.id == null) R.string.vault_add else R.string.vault_edit),
        sheetMaxWidth = VaultLayout.PaneWidth,
        modifier = Modifier.pointerInput(Unit) {
            // 只拦截顶部拖拽把手，保留点击外部关闭。
            val handleHeight = 24.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.y > handleHeight) return@awaitEachGesture
                down.consume()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { if (it.id == down.id) it.consume() }
                } while (event.changes.any { it.id == down.id && it.pressed })
            }
        },
        onDismissRequest = dismiss,
        onDismissFinished = onDismissFinished,
        dragHandleColor = Color.Transparent,
        allowDismiss = true,
        enableNestedScroll = false,
    ) {
        BackHandler(enabled = enabled && !showPicker && !duplicate, onBack = dismiss)
        PasswordForm(
            draft = editor.draft,
            snapshot = snapshot,
            enabled = enabled,
            problem = problem,
            expanded = expanded,
            passwordClipboard = passwordClipboard,
            onDraft = onDraft,
            onToggleSupplemental = {
                if (expanded) focus.clearFocus()
                expanded = !expanded
            },
            onPickCategory = {
                focus.clearFocus()
                picker = it
                picking = true
            },
            onCancel = dismiss,
            onConfirm = {
                focus.clearFocus()
                onSave(false)
            },
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
            scroll = scroll,
        )
    }

    CategoryPicker(showPicker, picker, snapshot, editor.draft, onDraft, onDismiss = { picking = false })
    VaultConfirm(
        duplicate, stringResource(R.string.vault_duplicate_title), stringResource(R.string.vault_password_duplicate),
        R.string.vault_save_anyway, onDismiss = onClearProblem, onConfirm = { onSave(true) },
    )
    VaultConfirm(
        editor.phase == EditorPhase.ConfirmDiscard,
        stringResource(R.string.vault_discard_title), stringResource(R.string.vault_discard_message),
        R.string.vault_discard, onDismiss = { onDiscard(false) }, onConfirm = { onDiscard(true) },
        cancel = R.string.vault_continue_editing,
    )
}

/**
 * 在固定操作区上方显示可滚动的密码表单。
 * @param draft 当前输入
 * @param snapshot 可用分类
 * @param enabled 是否接受输入和提交
 * @param problem 保存错误
 * @param expanded 补充信息是否展开
 * @param passwordClipboard 密码输入框的敏感剪贴板
 * @param onDraft 更新草稿
 * @param onToggleSupplemental 切换补充信息
 * @param onPickCategory 打开分类选择器
 * @param onCancel 请求关闭
 * @param onConfirm 提交保存
 * @param modifier 表单边距和窗口约束
 * @param scroll 表单的滚动位置
 */
@Composable
private fun PasswordForm(
    draft: PasswordDraft,
    snapshot: VaultSnapshot,
    enabled: Boolean,
    problem: VaultProblem?,
    expanded: Boolean,
    passwordClipboard: Clipboard,
    onDraft: ((PasswordDraft) -> PasswordDraft) -> Unit,
    onToggleSupplemental: () -> Unit,
    onPickCategory: (CategoryKind) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
) {
    val fieldColors = TextFieldDefaults.textFieldColors(labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    val next = KeyboardOptions(imeAction = ImeAction.Next)
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            PasswordFormField(R.string.vault_platform, required = true) { field ->
                DraftTextField(
                    draft.platform, { value -> onDraft { it.copy(platform = value) } }, field,
                    colors = fieldColors, hint = R.string.vault_platform_hint, enabled = enabled, keyboardOptions = next,
                )
            }
            PasswordFormField(R.string.vault_account) { field ->
                DraftTextField(
                    draft.account, { value -> onDraft { it.copy(account = value) } }, field,
                    colors = fieldColors, hint = R.string.vault_account_hint, enabled = enabled, keyboardOptions = next,
                )
            }
            PasswordFormField(R.string.vault_password, required = true) { field ->
                PasswordFormInput(
                    draft.password, enabled, passwordClipboard, fieldColors, modifier = field,
                    imeAction = if (expanded) ImeAction.Next else ImeAction.Done,
                    onValueChange = { value -> onDraft { it.copy(password = value) } },
                )
            }
            PasswordFormField(R.string.vault_type, required = true) { field ->
                TypeField(
                    snapshot.type(draft.typeId), enabled, fieldColors, modifier = field,
                ) { onPickCategory(CategoryKind.Type) }
            }
            SupplementalToggle(expanded, enabled, onToggleSupplemental)
            AnimatedVisibility(visible = expanded) {
                SupplementalFields(draft, snapshot, enabled, fieldColors, onDraft, onPickCategory)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = Theme.spacing.small, bottom = Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            VaultErrorText(problem?.takeUnless { it == VaultProblem.DuplicatePassword })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                TextButton(
                    stringResource(R.string.vault_cancel), onClick = onCancel, enabled = enabled,
                    modifier = Modifier.weight(1f), minHeight = Theme.sizes.minimumTouchTarget,
                )
                TextButton(
                    stringResource(R.string.vault_confirm_add), onClick = onConfirm,
                    enabled = enabled && draft.platform.isNotBlank() && draft.password.isNotEmpty() &&
                        draft.typeId != null,
                    modifier = Modifier.weight(1f), minHeight = Theme.sizes.minimumTouchTarget,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/**
 * 切换补充信息并提示展开状态。
 * @param expanded 是否已展开
 * @param enabled 是否可点击
 * @param onToggle 切换展开状态
 */
@Composable
private fun SupplementalToggle(expanded: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) -90f else 90f, label = "Supplemental chevron")
    val status = stringResource(if (expanded) R.string.vault_expanded else R.string.vault_collapsed)
    val colors = MiuixTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TextFieldDefaults.CornerRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
            .heightIn(min = Theme.sizes.minimumTouchTarget).padding(end = Theme.spacing.medium)
            .semantics { stateDescription = status },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.vault_supplemental_information), Modifier.weight(1f),
            color = colors.onSurfaceSecondary, style = MiuixTheme.textStyles.body1,
        )
        Icon(
            MiuixIcons.ChevronForward, contentDescription = null, Modifier.size(Theme.spacing.medium).rotate(rotation),
            tint = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * 显示可选的补充字段和分类。
 * @param draft 当前输入
 * @param snapshot 可选择的分类
 * @param enabled 是否可输入
 * @param fieldColors 输入框配色
 * @param onDraft 更新草稿
 * @param onPickCategory 打开分类选择器
 */
@Composable
private fun SupplementalFields(
    draft: PasswordDraft,
    snapshot: VaultSnapshot,
    enabled: Boolean,
    fieldColors: TextFieldColors,
    onDraft: ((PasswordDraft) -> PasswordDraft) -> Unit,
    onPickCategory: (CategoryKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        PasswordFormField(R.string.vault_website) { field ->
            DraftTextField(
                draft.website, { value -> onDraft { it.copy(website = value) } }, field,
                colors = fieldColors, hint = R.string.vault_website_hint, enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri, autoCorrectEnabled = false,
                ),
            )
        }
        PasswordFormField(R.string.vault_tags) { field ->
            TagField(snapshot.tags.filter { it.id in draft.tagIds }, enabled, fieldColors, modifier = field) {
                onPickCategory(CategoryKind.Tag)
            }
        }
        PasswordFormField(R.string.vault_email) { field ->
            DraftTextField(
                draft.email, { value -> onDraft { it.copy(email = value) } }, field,
                colors = fieldColors, hint = R.string.vault_email_hint, enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next, keyboardType = KeyboardType.Email, autoCorrectEnabled = false,
                ),
            )
        }
        PasswordFormField(R.string.vault_notes) { field ->
            DraftTextField(
                draft.notes, { value -> onDraft { it.copy(notes = value) } }, field,
                colors = fieldColors, hint = R.string.vault_notes_hint, enabled = enabled, minLines = 3, maxLines = 6,
            )
        }
        if (draft.id != null) VaultGroup {
            VaultLink(
                stringResource(R.string.vault_book), snapshot.books.firstOrNull { it.id == draft.bookId }?.name,
                enabled = enabled, icon = MiuixIcons.Folder,
            ) { onPickCategory(CategoryKind.Book) }
        }
    }
}

/**
 * 在可点击的输入框内显示选中的标签。
 * @param tags 与草稿关联的现有标签
 * @param enabled 是否允许打开选择器
 * @param fieldColors 与文本输入框一致的配色
 * @param modifier 输入框宽度和无障碍标题
 * @param onClick 打开标签多选
 */
@Composable
private fun TagField(
    tags: List<VaultCategory>,
    enabled: Boolean,
    fieldColors: TextFieldColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val inset = TextFieldDefaults.InsideMargin
    Row(
        modifier.squircleSurface(fieldColors.backgroundColor, TextFieldDefaults.CornerRadius)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = inset.width),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // 用普通输入框的单行字号和内边距确定高度。
            Text(
                " ", Modifier.padding(vertical = inset.height).alpha(0f).clearAndSetSemantics { },
                style = MiuixTheme.textStyles.main,
            )
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.vault_tags_hint), color = fieldColors.labelColor,
                    style = MiuixTheme.textStyles.main, fontWeight = FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2),
                    ) {
                        for (tag in tags) {
                            VaultTagChip(tag.name)
                        }
                    }
                }
            }
        }
        Icon(
            MiuixIcons.ChevronForward, contentDescription = null, Modifier.size(Theme.spacing.medium),
            tint = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * 以输入框外观显示当前选中的类型。
 * @param type 当前类型及内置标识
 * @param enabled 是否允许打开选择器
 * @param fieldColors 与输入框一致的配色
 * @param modifier 输入框宽度和无障碍标题
 * @param onClick 打开类型选择器
 */
@Composable
private fun TypeField(
    type: VaultCategory?,
    enabled: Boolean,
    fieldColors: TextFieldColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val inset = TextFieldDefaults.InsideMargin
    Row(
        modifier.squircleSurface(fieldColors.backgroundColor, TextFieldDefaults.CornerRadius)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = inset.width),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        if (type != null) VaultGlyph(type.name, type, Modifier.size(Theme.spacing.large))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // 用普通输入框的单行字号和内边距确定高度。
            Text(
                " ", Modifier.padding(vertical = inset.height).alpha(0f).clearAndSetSemantics { },
                style = MiuixTheme.textStyles.main,
            )
            Text(
                type?.name ?: stringResource(R.string.vault_type_hint),
                color = if (type == null) fieldColors.labelColor else colors.onSurface,
                style = MiuixTheme.textStyles.main, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            MiuixIcons.ChevronForward, contentDescription = null, Modifier.size(Theme.spacing.medium),
            tint = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * 输入密码并处理可见性与敏感剪贴板。
 * @param value 当前密码
 * @param enabled 是否可输入
 * @param clipboard 处理敏感内容的剪贴板
 * @param colors 输入框配色
 * @param modifier 输入框宽度和无障碍标题
 * @param imeAction 键盘的下一步操作
 * @param onValueChange 更新密码
 */
@Composable
private fun PasswordFormInput(
    value: String,
    enabled: Boolean,
    clipboard: Clipboard,
    colors: TextFieldColors,
    modifier: Modifier,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalClipboard provides clipboard) {
        DraftTextField(
            value, onValueChange, modifier, colors = colors, hint = R.string.vault_password_hint, enabled = enabled,
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
            trailingIcon = {
                VaultIcon(
                    if (revealed) MiuixIcons.Hide else MiuixIcons.Show,
                    if (revealed) R.string.vault_hide else R.string.vault_show,
                    enabled,
                ) { revealed = !revealed }
            },
        )
    }
}

/**
 * 同步保存输入、光标和输入法组合区，并将文本变化回写草稿。
 * @param initialText 输入框进入组合时的草稿文本
 * @param onTextChange 回写修改后的文本
 * @param modifier 输入框宽度和无障碍标题
 * @param colors 输入框配色
 * @param hint 空框提示
 * @param enabled 是否可输入
 * @param keyboardOptions 键盘类型和操作
 * @param minLines 最少显示行数
 * @param maxLines 最多显示行数
 * @param visualTransformation 文本的显示变换
 * @param trailingIcon 输入框末端操作
 */
@Composable
private fun DraftTextField(
    initialText: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier,
    colors: TextFieldColors,
    @StringRes hint: Int,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var input by remember { mutableStateOf(TextFieldValue(initialText)) }
    Box {
        TextField(
            value = input,
            onValueChange = { next ->
                val textChanged = input.text != next.text
                input = next
                if (textChanged) onTextChange(next.text)
            },
            modifier = modifier,
            colors = colors,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            singleLine = maxLines == 1,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
        )
        if (input.text.isEmpty()) {
            val inset = TextFieldDefaults.InsideMargin
            val singleLine = maxLines == 1
            Text(
                stringResource(hint),
                Modifier.align(if (singleLine) Alignment.CenterStart else Alignment.TopStart).padding(
                    start = inset.width,
                    top = if (singleLine) 0.dp else inset.height,
                    end = if (trailingIcon == null) inset.width else Theme.sizes.minimumTouchTarget,
                ),
                color = colors.labelColor, style = MiuixTheme.textStyles.main, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 在输入框上方显示常驻标题及必填标记。
 * @param title 字段标题
 * @param required 是否必填
 * @param content 接收无障碍描述的输入框
 */
@Composable
private fun PasswordFormField(
    @StringRes title: Int,
    required: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val label = stringResource(title)
    val description = if (required) stringResource(R.string.vault_required_field, label) else label
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small / 2)) {
        Text(
            buildAnnotatedString {
                append(label)
                if (required) withStyle(SpanStyle(color = colors.error)) { append(" *") }
            },
            Modifier.clearAndSetSemantics { },
            color = colors.onSurfaceSecondary,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
        )
        content(Modifier.fillMaxWidth().semantics { contentDescription = description })
    }
}

/** 预览首次打开的新增表单。 */
@VaultPreviews
@Composable
internal fun AddPasswordFormPreview() {
    val snapshot = previewSnapshot()
    PasswordFormPreview(PasswordDraft(bookId = snapshot.bookId), snapshot, expanded = false)
}

/** 预览带多个标签的已展开编辑表单。 */
@VaultPreviews
@Composable
internal fun EditPasswordFormPreview() {
    val snapshot = previewSnapshot()
    val tags = snapshot.tags + listOf(
        VaultCategory(2, "项目维护", 1), VaultCategory(3, "常用服务", 1),
        VaultCategory(4, "个人账号", 1), VaultCategory(5, "开发与部署", 1),
    )
    val draft = previewEntry().copy(tagIds = tags.map { it.id }.toSet())
    PasswordFormPreview(draft, snapshot.copy(tags = tags), expanded = true)
}

/**
 * 使用手机和平板预览常用表单状态。
 * @param draft 示例草稿
 * @param snapshot 示例分类
 * @param expanded 是否展示补充信息
 */
@Composable
private fun PasswordFormPreview(draft: PasswordDraft, snapshot: VaultSnapshot, expanded: Boolean) {
    MyPassTheme {
        VaultFrame(stringResource(if (draft.id == null) R.string.vault_add else R.string.vault_edit)) { modifier ->
            Box(modifier, contentAlignment = Alignment.TopCenter) {
                PasswordForm(
                    draft = draft,
                    snapshot = snapshot,
                    enabled = true,
                    problem = null,
                    expanded = expanded,
                    passwordClipboard = LocalClipboard.current,
                    onDraft = {},
                    onToggleSupplemental = {},
                    onPickCategory = {},
                    onCancel = {},
                    onConfirm = {},
                    modifier = Modifier.widthIn(max = VaultLayout.PaneWidth)
                        .padding(horizontal = Theme.spacing.large, vertical = Theme.spacing.medium),
                )
            }
        }
    }
}
