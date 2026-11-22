package com.winter.mypass.feature.home.view

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.ClipboardClearTimeout
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.PasswordPreviewSettings
import com.winter.mypass.feature.home.model.PasswordSummary
import com.winter.mypass.feature.home.model.PasswordVisibility
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultNavigationSettings
import com.winter.mypass.feature.home.model.VaultOrder
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.VaultSort
import com.winter.mypass.feature.home.viewmodel.PasswordMove
import com.winter.mypass.feature.home.viewmodel.VaultState
import com.winter.mypass.feature.home.viewmodel.VaultTab
import com.winter.mypass.ui.theme.AppThemeMode
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 在筛选图标下方选择密码类型，并高亮当前筛选状态。
 * @param types 当前可用的类型
 * @param typeId 当前类型标识，空值表示全部类型
 * @param enabled 是否允许操作
 * @param onSelect 选择类型后更新列表
 * @param onOpen 展开菜单前的页面处理
 * @param onDismiss 菜单关闭后的清理
 */
@Composable
internal fun TypeFilterMenu(
    types: List<VaultCategory>,
    typeId: Long?,
    enabled: Boolean,
    onSelect: (Long?) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val close = {
        show = false
        onDismiss()
    }
    val allTypes = stringResource(R.string.vault_all_types)
    val currentType = types.firstOrNull { it.id == typeId }?.name ?: allTypes
    Box {
        IconButton(
            onClick = {
                if (show) close() else {
                    onOpen()
                    show = true
                }
            },
            enabled = enabled,
            minWidth = Theme.sizes.minimumTouchTarget,
            minHeight = Theme.sizes.minimumTouchTarget,
            modifier = Modifier.semantics {
                selected = typeId != null
                stateDescription = currentType
            },
        ) {
            Icon(
                MiuixIcons.Filter, stringResource(R.string.vault_filter_type),
                Modifier.size(VaultLayout.HomeHeaderIconSize),
                tint = when {
                    !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    typeId != null -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurface
                },
            )
        }
        OverlayListPopup(
            show = show,
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true,
            minWidth = 0.dp,
            onDismissRequest = close,
        ) {
            Column(
                Modifier.widthIn(min = 160.dp, max = 280.dp).width(IntrinsicSize.Max)
                    .verticalScroll(rememberScrollState()).selectableGroup()
                    .padding(vertical = Theme.spacing.small / 2),
            ) {
                VaultPopupChoice(
                    allTypes, !enabled,
                    Modifier.selectable(typeId == null, enabled = enabled, role = Role.RadioButton) {
                        close()
                        onSelect(null)
                    },
                    selected = typeId == null,
                )
                if (types.isNotEmpty()) VaultRowDivider()
                for (type in types) {
                    VaultPopupChoice(
                        type.name, !enabled,
                        Modifier.selectable(type.id == typeId, enabled = enabled, role = Role.RadioButton) {
                            close()
                            onSelect(type.id)
                        },
                        selected = type.id == typeId,
                    )
                }
            }
        }
    }
}

/**
 * 在排序图标下方展开菜单，选择排序方式后收起，切换逆序时保持展开。
 * @param sort 当前排序与方向
 * @param busy 是否正在保存
 * @param problem 本次排序保存失败的原因
 * @param onSort 选择排序或切换方向
 * @param onOpen 展开菜单前的页面处理
 * @param onDismiss 菜单关闭后的清理
 */
@Composable
internal fun SortMenu(
    sort: VaultSort,
    busy: Boolean,
    problem: VaultProblem?,
    onSort: (VaultSort) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val close = {
        show = false
        onDismiss()
    }
    Box {
        VaultIcon(
            MiuixIcons.Sort, R.string.vault_sort, enabled = !busy, iconSize = VaultLayout.HomeHeaderIconSize,
        ) {
            if (show) close() else {
                onOpen()
                show = true
            }
        }
        OverlayListPopup(
            show = show,
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true,
            minWidth = 0.dp,
            onDismissRequest = close,
        ) {
            Column(
                // 保持紧凑的最小宽度，较长文案可自然撑宽菜单。
                Modifier.widthIn(min = 160.dp).width(IntrinsicSize.Max).verticalScroll(rememberScrollState())
                    .padding(vertical = Theme.spacing.small / 2),
            ) {
                if (problem != null) Box(Modifier.widthIn(max = 192.dp).padding(Theme.spacing.medium)) {
                    VaultErrorText(problem)
                }
                for (order in VaultOrder.entries) {
                    val label = when (order) {
                        VaultOrder.Updated -> R.string.vault_sort_updated
                        VaultOrder.Created -> R.string.vault_sort_created
                        VaultOrder.Name -> R.string.vault_sort_name
                    }
                    val selected = sort.order == order
                    VaultPopupChoice(
                        stringResource(label), busy,
                        Modifier.selectable(selected, enabled = !busy, role = Role.RadioButton) {
                            close()
                            if (!selected) onSort(sort.copy(order = order))
                        },
                        selected = selected,
                    )
                }
                VaultRowDivider()
                VaultPopupChoice(
                    stringResource(R.string.vault_reverse), busy,
                    Modifier.toggleable(sort.reversed, enabled = !busy, role = Role.Checkbox) {
                        onSort(sort.copy(reversed = it))
                    },
                    selected = sort.reversed,
                )
            }
        }
    }
}

/**
 * 紧凑的下拉菜单行，左侧显示文字，右侧显示选中状态。
 * @param label 行标题
 * @param busy 是否正在保存设置
 * @param interaction 行的点击行为及无障碍状态
 * @param selected 是否显示选中状态
 */
@Composable
private fun VaultPopupChoice(
    label: String,
    busy: Boolean,
    interaction: Modifier,
    selected: Boolean,
) {
    val colors = DropdownDefaults.dropdownColors()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Theme.sizes.minimumTouchTarget)
            .then(interaction).padding(horizontal = Theme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, modifier = Modifier.weight(1f), fontSize = MiuixTheme.textStyles.body1.fontSize,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = when {
                busy -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
                selected -> colors.selectedContentColor
                else -> colors.contentColor
            },
        )
        val check = Modifier.size(DropdownDefaults.CheckIconSize)
        if (selected) {
            Icon(
                MiuixIcons.Basic.Check, contentDescription = null, modifier = check,
                tint = if (busy) MiuixTheme.colorScheme.disabledOnSecondaryVariant else colors.selectedIndicatorColor,
            )
        } else Spacer(check)
    }
}

/**
 * 编辑页的密码本和类型单选，以及标签多选。
 * @param visible 分类选择器是否可见
 * @param kind 当前种类
 * @param snapshot 可选分类
 * @param draft 需要更新的当前草稿
 * @param onDraft 将选择应用到当前草稿
 * @param onDismiss 关闭选择器
 */
@Composable
internal fun CategoryPicker(
    visible: Boolean,
    kind: CategoryKind,
    snapshot: VaultSnapshot,
    draft: PasswordDraft,
    onDraft: ((PasswordDraft) -> PasswordDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxContent = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    OverlayBottomSheet(
        visible, title = stringResource(categoryTitle(kind)), onDismissRequest = onDismiss,
        endAction = if (kind == CategoryKind.Tag) {
            { TextButton(stringResource(R.string.vault_done), onClick = onDismiss) }
        } else null,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxContent).verticalScroll(rememberScrollState())
                .padding(bottom = Theme.spacing.medium),
        ) {
            for (item in snapshot.categories(kind)) {
                when (kind) {
                    CategoryKind.Book -> VaultChoice(item.name, item.id == draft.bookId) {
                        onDraft { it.copy(bookId = item.id) }
                        onDismiss()
                    }
                    CategoryKind.Type -> VaultChoice(
                        item.name, item.id == draft.typeId,
                        leading = { VaultGlyph(item.name, item) },
                    ) {
                        onDraft { it.copy(typeId = item.id) }
                        onDismiss()
                    }
                    CategoryKind.Tag -> VaultToggleRow(item.name, item.id in draft.tagIds) { checked ->
                        onDraft {
                            val tags = if (checked) it.tagIds + item.id else it.tagIds - item.id
                            it.copy(tagIds = tags)
                        }
                    }
                }
            }
            if (kind == CategoryKind.Tag && snapshot.tags.isEmpty()) {
                Text(
                    stringResource(R.string.vault_categories_empty),
                    Modifier.padding(Theme.spacing.medium), color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * 保留移动弹窗的展示内容，直到退场动画完成。
 * @author Tingdong
 * @date 2026-11-23
 */
private data class PasswordMoveDialogContent(
    val move: PasswordMove,
    val books: List<VaultCategory>,
    val busy: Boolean,
    val problem: VaultProblem?,
)

/**
 * 选择移动目标，并在发现重复项时确认保留全部记录。
 * @param show 是否显示弹窗
 * @param move 本次移动的固定选择与目标
 * @param books 现有密码本
 * @param busy 是否正在移动
 * @param problem 移动失败原因
 * @param onDismiss 取消移动
 * @param onSelect 选择目标密码本并移动
 * @param onConfirm 确认移动重复记录
 */
@Composable
internal fun PasswordMoveDialog(
    show: Boolean,
    move: PasswordMove?,
    books: List<VaultCategory>,
    busy: Boolean,
    problem: VaultProblem?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onConfirm: () -> Unit,
) {
    var retainedContent by remember { mutableStateOf<PasswordMoveDialogContent?>(null) }
    if (show && move != null) retainedContent = PasswordMoveDialogContent(move, books, busy, problem)
    val content = retainedContent ?: return
    val duplicate = content.move.targetBookId != null
    val dismiss = { if (show && !content.busy) onDismiss() }
    val maxContent = LocalConfiguration.current.screenHeightDp.dp * 0.5f
    OverlayDialog(
        show,
        title = stringResource(if (duplicate) R.string.vault_duplicate_title else R.string.vault_move_passwords),
        summary = if (duplicate) stringResource(R.string.vault_move_passwords_duplicate) else null,
        onDismissRequest = dismiss,
        onDismissFinished = { retainedContent = null },
    ) {
        // 移动及退场期间拦截返回手势。
        BackHandler(enabled = !show || content.busy) { }
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
            if (!duplicate) Column(
                Modifier.fillMaxWidth().heightIn(max = maxContent).verticalScroll(rememberScrollState()),
            ) {
                for (book in content.books.filter { it.id != content.move.bookId }) BasicComponent(
                    title = book.name,
                    enabled = !content.busy,
                    onClick = { if (show && !content.busy) onSelect(book.id) },
                    startAction = { Icon(MiuixIcons.Folder, null, Modifier.size(VaultLayout.HomeHeaderIconSize)) },
                )
            }
            VaultErrorText(content.problem)
            if (content.busy) Box(
                Modifier.fillMaxWidth().padding(Theme.spacing.small), contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                TextButton(
                    stringResource(R.string.vault_cancel), onClick = dismiss,
                    enabled = !content.busy, modifier = Modifier.weight(1f),
                )
                if (duplicate) TextButton(
                    stringResource(R.string.vault_move), onClick = { if (show && !content.busy) onConfirm() },
                    enabled = !content.busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/**
 * 显示类型、标签或密码本管理。
 * @param kind 要管理的类别
 * @param categories 现有项和关联数量
 * @param busy 是否有正在提交的操作
 * @param problem 失败详情
 * @param active 当前页面是否可消费返回事件
 * @param onBack 返回到原入口
 * @param onClearProblem 清理历史错误
 * @param onSave 新建或重命名
 * @param onDelete 删除指定项
 * @param onReorder 保存密码本顺序
 */
@Composable
internal fun CategoryScreen(
    kind: CategoryKind,
    categories: List<VaultCategory>,
    busy: Boolean,
    problem: VaultProblem?,
    active: Boolean,
    onBack: () -> Unit,
    onClearProblem: () -> Unit,
    onSave: (Long?, String, () -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    var draft by remember(kind) { mutableStateOf(CategoryDraft()) }
    var editing by remember(kind) { mutableStateOf(false) }
    var target by remember(kind) { mutableStateOf<VaultCategory?>(null) }
    var deleting by remember(kind) { mutableStateOf(false) }
    var reordering by remember(kind) { mutableStateOf(false) }
    val rename: (VaultCategory) -> Unit = { item ->
        draft = CategoryDraft(item.id, item.name)
        onClearProblem()
        editing = true
    }
    val delete: (VaultCategory) -> Unit = { item ->
        target = item
        onClearProblem()
        deleting = true
    }
    val closeEdit = {
        if (!busy) {
            editing = false
            onClearProblem()
        }
    }
    BackHandler(active) { if (!busy && !reordering) onBack() }
    VaultFrame(
        title = stringResource(categoryTitle(kind)),
        navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, enabled = !busy && !reordering, iconSize = VaultLayout.HeaderIconSize, onClick = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!busy && !reordering) {
                        draft = CategoryDraft()
                        onClearProblem()
                        editing = true
                    }
                },
                modifier = Modifier.padding(horizontal = Theme.spacing.small),
            ) {
                Icon(
                    MiuixIcons.Add, stringResource(R.string.vault_new_category),
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { modifier ->
        if (kind == CategoryKind.Book) {
            Box(modifier, contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxSize()
                        .padding(horizontal = Theme.spacing.small),
                ) {
                    if (!editing && problem != null) Box(
                        Modifier.padding(top = Theme.spacing.medium).padding(horizontal = Theme.spacing.small),
                    ) {
                        VaultErrorText(problem)
                    }
                    ReorderableBookList(
                        categories = categories,
                        busy = busy,
                        enabled = active && !busy && !editing && !deleting,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onInteractionChanged = { reordering = it },
                        onReorder = onReorder,
                        onRename = rename,
                        onDelete = delete,
                    )
                }
            }
        } else VaultColumn(modifier, horizontalPadding = Theme.spacing.medium) {
            if (!editing) VaultErrorText(problem)
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.vault_categories_empty), Modifier.padding(top = Theme.spacing.large),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else VaultGroup {
                categories.forEachIndexed { index, item ->
                    if (index > 0) VaultRowDivider()
                    CategoryRow(
                        kind, item, !busy,
                        onRename = { rename(item) },
                        onDelete = { delete(item) },
                    )
                }
            }
            if (categories.isNotEmpty()) Spacer(
                Modifier.height(FloatingActionButtonDefaults.MinHeight + Theme.spacing.medium),
            )
        }
        OverlayDialog(
            active && editing,
            title = stringResource(if (draft.id == null) R.string.vault_new_category else R.string.vault_rename),
            onDismissRequest = closeEdit,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium)) {
                VaultErrorText(problem)
                TextField(
                    draft.name, {
                        draft = draft.copy(name = it)
                        onClearProblem()
                    },
                    Modifier.fillMaxWidth(), label = stringResource(R.string.vault_category_name),
                    enabled = !busy, singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    TextButton(stringResource(R.string.vault_cancel), onClick = closeEdit, enabled = !busy, modifier = Modifier.weight(1f))
                    TextButton(
                        stringResource(R.string.vault_save), modifier = Modifier.weight(1f),
                        enabled = !busy && draft.name.isNotBlank(),
                        onClick = { onSave(draft.id, draft.name) { editing = false } },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }

        target?.let { item ->
            val blocked = when {
                kind != CategoryKind.Book -> null
                item.entryCount > 0 -> R.string.vault_book_not_empty
                categories.size <= 1 -> R.string.vault_last_book
                else -> null
            }
            if (blocked != null) {
                OverlayDialog(
                    active && deleting, title = stringResource(R.string.vault_delete),
                    summary = stringResource(blocked), onDismissRequest = { deleting = false },
                ) {
                    TextButton(
                        stringResource(R.string.vault_done), onClick = { deleting = false },
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            } else {
                val message = if (item.entryCount == 0) {
                    stringResource(R.string.vault_confirm_category, item.name)
                } else stringResource(R.string.vault_confirm_used_category, item.name, item.entryCount)
                VaultConfirm(
                    active && deleting, stringResource(R.string.vault_delete), message, R.string.vault_delete,
                    onDismiss = { deleting = false }, onConfirm = {
                        deleting = false
                        onDelete(item.id)
                    },
                )
            }
        }
    }
}

/**
 * 管理行的低频操作放在行级菜单中。
 * @param kind 当前分类
 * @param item 行标题与关联数量
 * @param enabled 是否接受点击
 * @param onRename 更改名称
 * @param onDelete 打开删除确认
 * @param actionModifier 更多按钮的定位与手势修饰符
 * @param dragging 是否为拖动浮层中的按钮
 */
@Composable
internal fun CategoryRow(
    kind: CategoryKind,
    item: VaultCategory,
    enabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    actionModifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    var menu by remember(item.id) { mutableStateOf(false) }
    var pendingAction by remember(item.id) { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            menu = false
            pendingAction = null
        }
    }
    val icon = when (kind) {
        CategoryKind.Book -> MiuixIcons.Folder
        CategoryKind.Tag -> MiuixIcons.Pin
        CategoryKind.Type -> MiuixIcons.Layers
    }
    BasicComponent(
        title = item.name,
        summary = pluralStringResource(R.plurals.vault_record_count, item.entryCount, item.entryCount),
        startAction = {
            if (kind == CategoryKind.Type) VaultGlyph(item.name, item)
            else Icon(icon, null, Modifier.size(22.dp))
        },
        endActions = {
            if (!item.builtIn) Box(actionModifier) {
                IconButton(
                    onClick = { menu = true }, enabled = enabled, holdDownState = menu,
                    minWidth = Theme.sizes.minimumTouchTarget, minHeight = Theme.sizes.minimumTouchTarget,
                ) {
                    Icon(
                        MiuixIcons.More, stringResource(R.string.vault_category_actions), Modifier.size(22.dp),
                        tint = if (enabled || dragging) MiuixTheme.colorScheme.onSurface
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                OverlayListPopup(
                    show = menu && enabled,
                    alignment = PopupPositionProvider.Align.End,
                    minWidth = 0.dp,
                    onDismissRequest = { menu = false },
                    onDismissFinished = {
                        val action = pendingAction
                        pendingAction = null
                        if (enabled) action?.invoke()
                    },
                ) {
                    Column(
                        Modifier.widthIn(min = 168.dp, max = 240.dp).width(IntrinsicSize.Max)
                            .verticalScroll(rememberScrollState()).padding(vertical = Theme.spacing.small / 2),
                    ) {
                        CategoryMenuAction(stringResource(R.string.vault_rename), enabled && menu) {
                            pendingAction = onRename
                            menu = false
                        }
                        CategoryMenuAction(stringResource(R.string.vault_delete), enabled && menu) {
                            pendingAction = onDelete
                            menu = false
                        }
                    }
                }
            }
        },
    )
}

/**
 * 使用下拉菜单字号和触控高度显示分类操作。
 * @param label 操作名称
 * @param enabled 是否可执行操作
 * @param onClick 点击后关闭菜单并执行操作
 */
@Composable
private fun CategoryMenuAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Theme.sizes.minimumTouchTarget + Theme.spacing.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontSize = MiuixTheme.textStyles.body1.fontSize, fontWeight = FontWeight.Medium,
            color = DropdownDefaults.dropdownColors().contentColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 提供启动密码、界面样式和分类管理入口。
 * @param preview 当前显示模式与遮罩符号
 * @param navigation 当前底栏样式偏好
 * @param clipboardClearTimeout 当前剪贴板清除时限
 * @param busy 是否正在保存设置
 * @param onPasswordPreview 保存预览设置
 * @param onNavigationSettings 保存底栏样式偏好
 * @param onClipboardClearTimeout 保存剪贴板清除时限
 * @param onChangeCredential 打开启动密码修改页
 * @param onBackup 打开备份页
 * @param onTrash 打开回收站
 * @param onManage 打开对应分类
 * @param dark 当前是否使用暗色主题
 * @param themeMode 当前选择的主题模式
 * @param onTheme 选择主题模式，null 表示暂不可切换
 * @param onPopupVisibility 通知顶层是否正在显示设置弹层
 * @param onAbout 打开关于软件页
 */
@Composable
internal fun VaultSettings(
    preview: PasswordPreviewSettings,
    navigation: VaultNavigationSettings,
    clipboardClearTimeout: ClipboardClearTimeout,
    busy: Boolean,
    onPasswordPreview: (PasswordPreviewSettings) -> Unit,
    onNavigationSettings: ((VaultNavigationSettings) -> VaultNavigationSettings) -> Unit,
    onClipboardClearTimeout: (ClipboardClearTimeout) -> Unit,
    onChangeCredential: () -> Unit,
    onBackup: () -> Unit,
    onTrash: () -> Unit,
    onManage: (CategoryKind) -> Unit,
    dark: Boolean,
    themeMode: AppThemeMode,
    onTheme: ((AppThemeMode) -> Unit)?,
    onPopupVisibility: (Boolean) -> Unit,
    onAbout: () -> Unit,
) {
    var choosingTheme by remember { mutableStateOf(false) }
    var choosingSymbol by remember { mutableStateOf(false) }
    var choosingVisibility by remember { mutableStateOf(false) }
    var choosingClipboardTimeout by remember { mutableStateOf(false) }
    var customSymbol by remember { mutableStateOf<String?>(null) }
    val symbolTitle = stringResource(R.string.vault_mask_symbol)
    val popupVisibility by rememberUpdatedState(onPopupVisibility)
    val popupOpen = choosingSymbol || choosingVisibility || choosingTheme || choosingClipboardTimeout
    DisposableEffect(popupOpen) {
        popupVisibility(popupOpen)
        onDispose { if (popupOpen) popupVisibility(false) }
    }
    SettingsSection(R.string.vault_security_settings) {
        VaultLink(
            stringResource(R.string.backup_title),
            enabled = !busy,
            icon = MiuixIcons.UploadCloud,
            onClick = onBackup,
        )
        VaultRowDivider()
        VaultLink(
            stringResource(R.string.vault_trash),
            enabled = !busy,
            icon = MiuixIcons.Delete,
            onClick = onTrash,
        )
        VaultRowDivider()
        VaultLink(
            stringResource(R.string.lock_change_title),
            enabled = !busy,
            icon = MiuixIcons.Lock,
            onClick = onChangeCredential,
        )
        VaultRowDivider()
        ClipboardClearTimeoutMenu(
            clipboardClearTimeout, !busy, choosingClipboardTimeout, { choosingClipboardTimeout = it },
            onClipboardClearTimeout,
        )
    }
    SettingsSection(R.string.vault_style_settings) {
        ThemeMenu(themeMode, dark, !busy && onTheme != null, choosingTheme, { choosingTheme = it }) {
            onTheme?.invoke(it)
        }
        VaultRowDivider()
        PasswordVisibilityMenu(
            preview.visibility, busy, choosingVisibility, { choosingVisibility = it },
        ) { onPasswordPreview(preview.copy(visibility = it)) }
        VaultRowDivider()
        VaultLink(symbolTitle, enabled = !busy, icon = MiuixIcons.Lock, value = preview.symbol) {
            customSymbol = preview.symbol.takeUnless {
                it == PasswordPreviewSettings.Asterisk || it == PasswordPreviewSettings.Dot
            }
            choosingSymbol = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VaultRowDivider()
            VaultSwitch(
                R.string.vault_floating_navigation, navigation.floating, enabled = !busy,
                icon = ImageVector.vectorResource(R.drawable.ic_vault_floating_bar),
            ) { checked ->
                onNavigationSettings { latest -> latest.copy(floating = checked) }
            }
            AnimatedVisibility(
                visible = navigation.floating,
                enter = expandVertically(tween(220), expandFrom = Alignment.Top) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
            ) {
                Column {
                    VaultRowDivider()
                    VaultSwitch(
                        R.string.vault_liquid_glass, navigation.liquidGlass, enabled = !busy && navigation.floating,
                        icon = ImageVector.vectorResource(R.drawable.ic_vault_liquid_glass),
                    ) { checked ->
                        onNavigationSettings { latest -> latest.copy(liquidGlass = checked) }
                    }
                }
            }
        }
    }
    SettingsSection(R.string.vault_category_settings) {
        VaultLink(stringResource(R.string.vault_manage_tags), enabled = !busy, icon = MiuixIcons.Pin) {
            onManage(CategoryKind.Tag)
        }
        VaultRowDivider()
        VaultLink(stringResource(R.string.vault_manage_types), enabled = !busy, icon = MiuixIcons.Layers) {
            onManage(CategoryKind.Type)
        }
    }
    VaultGroup {
        VaultLink(stringResource(R.string.about_title), icon = MiuixIcons.Info, onClick = onAbout)
    }
    OverlayDialog(choosingSymbol, title = symbolTitle, onDismissRequest = { choosingSymbol = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.selectableGroup()) {
                val presets = listOf(
                    R.string.vault_mask_asterisk to PasswordPreviewSettings.Asterisk,
                    R.string.vault_mask_dot to PasswordPreviewSettings.Dot,
                )
                for ((label, symbol) in presets) {
                    VaultChoice(
                        stringResource(label, symbol), selected = customSymbol == null && preview.symbol == symbol,
                        enabled = !busy,
                    ) {
                        choosingSymbol = false
                        onPasswordPreview(preview.copy(symbol = symbol))
                    }
                }
                VaultChoice(
                    stringResource(R.string.vault_mask_custom), selected = customSymbol != null, enabled = !busy,
                ) {
                    if (customSymbol == null) customSymbol = ""
                }
            }
            customSymbol?.let { symbol ->
                val valid = PasswordPreviewSettings.isValidSymbol(symbol)
                Column(
                    modifier = Modifier.padding(top = Theme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                ) {
                    TextField(
                        symbol, { customSymbol = it },
                        Modifier.fillMaxWidth(), label = stringResource(R.string.vault_mask_symbol_hint),
                        enabled = !busy, singleLine = true,
                    )
                    if (symbol.isNotEmpty() && !valid) Text(
                        stringResource(R.string.vault_mask_symbol_invalid),
                        color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.body2,
                    )
                    TextButton(
                        stringResource(R.string.vault_save), enabled = valid && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            choosingSymbol = false
                            onPasswordPreview(preview.copy(symbol = symbol))
                        },
                    )
                }
            }
        }
    }
}

/**
 * 用标题和卡片组织一组相关设置。
 * @param title 分组名称
 * @param content 组内选项
 */
@Composable
internal fun SettingsSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        Text(
            stringResource(title),
            Modifier.padding(horizontal = Theme.spacing.medium).semantics { heading() },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        VaultGroup(content = content)
    }
}

/**
 * 在主题设置行展开 Miuix 单选下拉菜单。
 * @param mode 当前主题模式
 * @param dark 当前界面是否为暗色
 * @param enabled 是否可以选择
 * @param show 是否展开菜单
 * @param onShow 修改菜单展开状态
 * @param onSelect 应用所选主题模式
 */
@Composable
private fun ThemeMenu(
    mode: AppThemeMode,
    dark: Boolean,
    enabled: Boolean,
    show: Boolean,
    onShow: (Boolean) -> Unit,
    onSelect: (AppThemeMode) -> Unit,
) {
    Box {
        VaultLink(
            stringResource(R.string.appearance_theme), enabled = enabled,
            icon = ImageVector.vectorResource(if (dark) R.drawable.ic_vault_theme_moon else R.drawable.ic_vault_theme_sun),
            value = stringResource(mode.label), onClick = { onShow(true) },
        )
        OverlayListPopup(
            show = show, alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true, onDismissRequest = { onShow(false) },
        ) {
            ListPopupColumn {
                AppThemeMode.entries.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = stringResource(option.label), optionSize = AppThemeMode.entries.size,
                        isSelected = mode == option, index = index, enabled = enabled,
                        onSelectedIndexChange = {
                            onShow(false)
                            if (mode != option) onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** 主题模式对应的本地化名称。 */
@get:StringRes
private val AppThemeMode.label: Int
    get() = when (this) {
        AppThemeMode.System -> R.string.appearance_follow_system
        AppThemeMode.Light -> R.string.appearance_light
        AppThemeMode.Dark -> R.string.appearance_dark
    }

/**
 * 在密码显示设置行的右侧展开单选菜单。
 * @param visibility 当前显示模式
 * @param busy 是否正在保存
 * @param show 是否展开菜单
 * @param onShow 修改菜单展开状态
 * @param onSelect 应用菜单选项
 */
@Composable
private fun PasswordVisibilityMenu(
    visibility: PasswordVisibility,
    busy: Boolean,
    show: Boolean,
    onShow: (Boolean) -> Unit,
    onSelect: (PasswordVisibility) -> Unit,
) {
    Box {
        VaultLink(
            stringResource(R.string.vault_password_visibility), enabled = !busy,
            icon = MiuixIcons.Show, value = stringResource(visibility.label),
        ) { onShow(true) }
        OverlayListPopup(
            show = show, alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true, onDismissRequest = { onShow(false) },
        ) {
            ListPopupColumn {
                PasswordVisibility.entries.forEachIndexed { index, mode ->
                    DropdownImpl(
                        text = stringResource(mode.label),
                        optionSize = PasswordVisibility.entries.size,
                        isSelected = mode == visibility, index = index, enabled = !busy,
                        onSelectedIndexChange = {
                            onShow(false)
                            if (mode != visibility) onSelect(mode)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 在剪贴板设置行展开 Miuix 单选下拉菜单。
 * @param timeout 当前清除时限
 * @param enabled 是否可以选择
 * @param show 是否展开菜单
 * @param onShow 修改菜单展开状态
 * @param onSelect 保存所选时限
 */
@Composable
private fun ClipboardClearTimeoutMenu(
    timeout: ClipboardClearTimeout,
    enabled: Boolean,
    show: Boolean,
    onShow: (Boolean) -> Unit,
    onSelect: (ClipboardClearTimeout) -> Unit,
) {
    Box {
        VaultLink(
            stringResource(R.string.vault_clipboard_clear_timeout), enabled = enabled,
            icon = MiuixIcons.Copy, value = stringResource(timeout.label), onClick = { onShow(true) },
        )
        OverlayListPopup(
            show = show, alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true, onDismissRequest = { onShow(false) },
        ) {
            ListPopupColumn {
                ClipboardClearTimeout.entries.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = stringResource(option.label), optionSize = ClipboardClearTimeout.entries.size,
                        isSelected = timeout == option, index = index, enabled = enabled,
                        onSelectedIndexChange = {
                            onShow(false)
                            if (timeout != option) onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** 剪贴板清除时限对应的本地化名称。 */
@get:StringRes
private val ClipboardClearTimeout.label: Int
    get() = when (this) {
        ClipboardClearTimeout.Never -> R.string.vault_clipboard_clear_never
        ClipboardClearTimeout.Seconds30 -> R.string.vault_clipboard_clear_30_seconds
        ClipboardClearTimeout.Minute1 -> R.string.vault_clipboard_clear_1_minute
        ClipboardClearTimeout.Minutes5 -> R.string.vault_clipboard_clear_5_minutes
    }

/** 密码显示模式对应的本地化名称。 */
@get:StringRes
private val PasswordVisibility.label: Int
    get() = when (this) {
        PasswordVisibility.Full -> R.string.vault_password_visibility_full
        PasswordVisibility.Partial -> R.string.vault_password_visibility_partial
        PasswordVisibility.Hidden -> R.string.vault_password_visibility_hidden
    }

/**
 * 作为一个可点击复选项显示行与勾选控件。
 * @param label 选项文本
 * @param checked 当前是否启用
 * @param enabled 是否可切换
 * @param onChange 切换回调
 */
@Composable
private fun VaultToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    BasicComponent(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        title = label, enabled = enabled,
        endActions = {
            Checkbox(if (checked) ToggleableState.On else ToggleableState.Off, onClick = null, enabled = enabled)
        },
    )
}

/**
 * 将分隔线和邻近行的文本对齐。
 */
@Composable
internal fun VaultRowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Theme.spacing.medium))
}

/**
 * 分类弹窗中的名称草稿。
 * @author Tingdong
 * @date 2026-11-19
 */
private data class CategoryDraft(val id: Long? = null, val name: String = "")

/**
 * 取得分类对应的标题。
 * @param kind 分类类型
 * @return 标题字符串
 */
@StringRes
private fun categoryTitle(kind: CategoryKind): Int = when (kind) {
    CategoryKind.Book -> R.string.vault_books
    CategoryKind.Tag -> R.string.vault_tags
    CategoryKind.Type -> R.string.vault_types
}

/**
 * 提供预览使用的示例记录。
 * @return 预览数据
 */
internal fun previewEntry(): PasswordDraft = PasswordDraft(
    bookId = 1, id = 1, platform = "GitHub", account = "winter@example.com", password = "preview-secret",
    website = "https://github.com", email = "mail@example.com",
    notes = "个人代码仓库", typeId = 2, tagIds = setOf(1L),
)

/**
 * 提供多本、多类型的预览数据。
 * @param preview 预览中使用的密码显示与遮罩设置
 * @return 密码本预览快照
 */
internal fun previewSnapshot(preview: PasswordPreviewSettings = PasswordPreviewSettings()): VaultSnapshot {
    val work = VaultCategory(1, "工作", 2)
    return VaultSnapshot(
        books = listOf(VaultCategory(1, "个人密码本", 5), VaultCategory(2, "工作密码本", 0)),
        tags = listOf(work),
        types = listOf(
            VaultCategory(1, "软件", 2, "software"), VaultCategory(2, "网站", 1, "website"),
            VaultCategory(3, "小程序", 1, "miniapp"), VaultCategory(4, "银行卡", 1),
        ),
        bookId = 1,
        passwords = listOf(
            PasswordSummary(1, 1, "GitHub", "winter@example.com", "https://github.com", 2, 0, 5, preview.display("preview-secret")),
            PasswordSummary(2, 1, "微信", "tingdong", null, 1, 0, 4, preview.display("W8-68!")),
            PasswordSummary(3, 1, "记账小程序", "winter@example.com", null, 3, 0, 3, preview.display("notes#636")),
            PasswordSummary(4, 1, "哔哩哔哩", "听冬酱", null, 1, 0, 2, preview.display("bili-2468")),
            PasswordSummary(5, 1, "储蓄卡", null, null, 4, 0, 1, preview.display("bd")),
        ),
        tagsByPassword = mapOf(1L to listOf(work), 3L to listOf(work)),
    )
}

/** 预览分类管理页。 */
@VaultPreviews
@Composable
internal fun CategoryPreview() {
    MyPassTheme {
        CategoryScreen(CategoryKind.Type, previewSnapshot().types, false, null, true, {}, {}, { _, _, _ -> }, {}, {})
    }
}

/** 预览密码本排序管理页。 */
@VaultPreviews
@Composable
internal fun BookCategoryPreview() {
    MyPassTheme {
        CategoryScreen(CategoryKind.Book, previewSnapshot().books, false, null, true, {}, {}, { _, _, _ -> }, {}, {})
    }
}

/**
 * 预览底部导航下的设置页。
 */
@VaultPreviews
@Composable
internal fun SettingsPreview() {
    VaultTabbedPreview(VaultState(tab = VaultTab.Settings))
}

/**
 * 预览悬浮导航开启时的两个样式开关。
 */
@VaultPreviews
@Composable
internal fun FloatingNavigationSettingsPreview() {
    VaultTabbedPreview(
        VaultState(tab = VaultTab.Settings, navigation = VaultNavigationSettings(floating = true, liquidGlass = true)),
    )
}
