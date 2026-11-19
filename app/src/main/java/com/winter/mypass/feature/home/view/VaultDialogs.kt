package com.winter.mypass.feature.home.view

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.PasswordSummary
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultOrder
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.VaultSort
import com.winter.mypass.feature.home.viewmodel.VaultState
import com.winter.mypass.feature.home.viewmodel.VaultTab
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 在排序图标下方展开菜单，逆序独立于当前维度。
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
            enableWindowDim = false,
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
                    SortPopupRow(
                        stringResource(label), busy,
                        Modifier.selectable(selected, enabled = !busy, role = Role.RadioButton) {
                            if (!selected) onSort(sort.copy(order = order))
                        },
                        selected = selected,
                    )
                }
                VaultRowDivider()
                SortPopupRow(
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
 * 紧凑的排序菜单行，左侧显示文字，右侧显示选中状态。
 * @param label 行标题
 * @param busy 是否正在保存排序设置
 * @param interaction 行的点击行为及无障碍状态
 * @param selected 是否显示选中状态
 */
@Composable
private fun SortPopupRow(
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
            if (kind == CategoryKind.Type) {
                VaultChoice(stringResource(R.string.vault_none), draft.typeId == null) {
                    onDraft { it.copy(typeId = null) }
                    onDismiss()
                }
            }
            for (item in snapshot.categories(kind)) {
                when (kind) {
                    CategoryKind.Book -> VaultChoice(item.name, item.id == draft.bookId) {
                        onDraft { it.copy(bookId = item.id) }
                        onDismiss()
                    }
                    CategoryKind.Type -> VaultChoice(item.name, item.id == draft.typeId) {
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
) {
    var draft by remember(kind) { mutableStateOf(CategoryDraft()) }
    var editing by remember(kind) { mutableStateOf(false) }
    var target by remember(kind) { mutableStateOf<VaultCategory?>(null) }
    var deleting by remember(kind) { mutableStateOf(false) }
    val closeEdit = {
        if (!busy) {
            editing = false
            onClearProblem()
        }
    }
    BackHandler(active, onBack = onBack)
    VaultFrame(
        title = stringResource(categoryTitle(kind)),
        navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, enabled = !busy, iconSize = VaultLayout.HeaderIconSize, onClick = onBack) },
        actions = {
            VaultIcon(MiuixIcons.Add, R.string.vault_new_category, enabled = !busy, iconSize = VaultLayout.HeaderIconSize) {
                draft = CategoryDraft()
                onClearProblem()
                editing = true
            }
        },
    ) { modifier ->
        VaultColumn(modifier) {
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
                        onRename = {
                            draft = CategoryDraft(item.id, item.name)
                            onClearProblem()
                            editing = true
                        },
                        onDelete = {
                            target = item
                            onClearProblem()
                            deleting = true
                        },
                    )
                }
            }
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
 */
@Composable
private fun CategoryRow(
    kind: CategoryKind,
    item: VaultCategory,
    enabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember(item.id) { mutableStateOf(false) }
    val icon = when (kind) {
        CategoryKind.Book -> MiuixIcons.Folder
        CategoryKind.Tag -> MiuixIcons.Pin
        CategoryKind.Type -> MiuixIcons.Layers
    }
    BasicComponent(
        title = item.name,
        summary = pluralStringResource(R.plurals.vault_record_count, item.entryCount, item.entryCount),
        startAction = { Icon(icon, null, Modifier.size(22.dp)) },
        endActions = {
            Box {
                VaultIcon(MiuixIcons.More, R.string.vault_category_actions, enabled) { menu = true }
                OverlayListPopup(show = menu, onDismissRequest = { menu = false }) {
                    ListPopupColumn {
                        BasicComponent(title = stringResource(R.string.vault_rename), onClick = {
                            menu = false
                            onRename()
                        })
                        BasicComponent(title = stringResource(R.string.vault_delete), onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
        },
    )
}

/**
 * 设置页只放当前提供的分类入口。
 * @param onManage 打开对应分类
 */
@Composable
internal fun VaultSettings(onManage: (CategoryKind) -> Unit) {
    VaultGroup {
        VaultLink(stringResource(R.string.vault_manage_tags), icon = MiuixIcons.Pin) { onManage(CategoryKind.Tag) }
        VaultRowDivider()
        VaultLink(stringResource(R.string.vault_manage_types), icon = MiuixIcons.Layers) { onManage(CategoryKind.Type) }
    }
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
    website = "https://github.com", notes = "个人代码仓库", typeId = 2, tagIds = setOf(1L),
)

/**
 * 提供多本、多类型的预览数据。
 * @return 密码本预览快照
 */
internal fun previewSnapshot(): VaultSnapshot {
    val work = VaultCategory(1, "工作", 2)
    return VaultSnapshot(
        books = listOf(VaultCategory(1, "个人密码本", 5), VaultCategory(2, "工作密码本", 0)),
        tags = listOf(work),
        types = listOf(VaultCategory(1, "APP", 2), VaultCategory(2, "Web", 3)),
        bookId = 1,
        passwords = listOf(
            PasswordSummary(1, 1, "GitHub", "winter@example.com", "https://github.com", 2, 0, 5),
            PasswordSummary(2, 1, "微信", "tingdong", null, 1, 0, 4),
            PasswordSummary(3, 1, "Notion", "winter@example.com", "https://notion.com", 2, 0, 3),
            PasswordSummary(4, 1, "哔哩哔哩", "听冬酱", null, 1, 0, 2),
            PasswordSummary(5, 1, "豆瓣", "winter@example.com", "https://douban.com", 2, 0, 1),
        ),
        tagsByPassword = mapOf(1L to listOf(work), 3L to listOf(work)),
    )
}

/** 预览分类管理页。 */
@VaultPreviews
@Composable
internal fun CategoryPreview() {
    MyPassTheme {
        CategoryScreen(CategoryKind.Type, previewSnapshot().types, false, null, true, {}, {}, { _, _, _ -> }, {})
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
