package com.winter.mypass.feature.home.view

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.PasswordSummary
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.VaultSort
import com.winter.mypass.feature.home.viewmodel.GeneratorOptions
import com.winter.mypass.feature.home.viewmodel.VaultPage
import com.winter.mypass.feature.home.viewmodel.VaultState
import com.winter.mypass.feature.home.viewmodel.VaultTab
import com.winter.mypass.feature.home.viewmodel.VaultViewModel
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 密码本导航使用独立的页面标识，草稿和搜索变化不触发转场。
 * @author Tingdong
 * @date 2026-11-19
 */
private enum class Destination(val depth: Int) { Loading(0), Tabs(0), Catalog(1), Detail(1) }

/**
 * 根据当前状态确定转场的目的地。
 * @receiver 当前会话状态
 */
private val VaultState.destination: Destination
    get() = if (snapshot == null) Destination.Loading else when (val currentPage = page) {
        is VaultPage.Categories -> Destination.Catalog
        is VaultPage.Detail -> Destination.Detail
        is VaultPage.Editor -> if (currentPage.original.id == null) Destination.Tabs else Destination.Detail
        null -> Destination.Tabs
    }

/**
 * 连接解锁后各屏的状态、返回和页面转场。
 * @param model 当前解锁会话的密码本
 */
@Composable
internal fun VaultScreen(model: VaultViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val list = remember { LazyListState() }
    val focus = LocalFocusManager.current
    val editorOpen = state.page is VaultPage.Editor
    LaunchedEffect(editorOpen) {
        if (editorOpen) focus.clearFocus(force = true)
    }
    // 退场期间保留当前返回目标。
    BackHandler(enabled = (state.page as? VaultPage.Editor)?.phase?.isClosing == true) { }
    LaunchedEffect(state.snapshot?.bookId, state.query, state.sort) {
        if (state.snapshot != null) list.scrollToItem(0)
    }
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        targetState = state,
        contentKey = { it.destination },
        transitionSpec = {
            val source = initialState.destination
            val target = targetState.destination
            if (source.depth == target.depth) {
                fadeIn(tween(150, delayMillis = 45)) togetherWith fadeOut(tween(80))
            } else {
                val direction = if (target.depth > source.depth) forward else -forward
                (fadeIn(tween(170)) + slideInHorizontally(tween(210)) { direction * it / 10 }) togetherWith
                    (fadeOut(tween(130)) + slideOutHorizontally(tween(210)) { -direction * it / 18 })
            }
        },
        label = "Vault destination",
    ) { screen ->
        val active = screen.destination == state.destination
        val snapshot = screen.snapshot
        val page = screen.page
        if (snapshot == null) {
            VaultLoading(screen, model::load)
        } else {
            val editor = page as? VaultPage.Editor
            val detail = when (page) {
                is VaultPage.Detail -> page.draft
                is VaultPage.Editor -> page.original.takeIf { it.id != null }
                else -> null
            }
            val overlays: @Composable () -> Unit = {
                if (active && editor != null) {
                    PasswordFormSheet(
                        editor = editor,
                        snapshot = snapshot,
                        busy = screen.busy,
                        problem = screen.problem,
                        passwordClipboard = model.passwordEditorClipboard,
                        onDraft = model::editDraft,
                        onSave = model::savePassword,
                        onDismiss = model::dismissPasswordEditor,
                        onDismissFinished = model::finishPasswordEditorDismissal,
                        onDiscard = model::confirmPasswordDiscard,
                        onClearProblem = model::clearProblem,
                    )
                }
            }
            when {
                detail != null -> PasswordDetail(
                    detail, snapshot, screen.busy, screen.problem.takeIf { editor == null },
                    active && editor == null, model::closePage, model::editPassword, model::deletePassword,
                    model::copyText, overlays,
                )
                page is VaultPage.Categories -> CategoryScreen(
                    page.kind, snapshot.categories(page.kind), screen.busy, screen.problem, active,
                    model::closePage, model::clearProblem, model::saveCategory, model::deleteCategory,
                )
                else -> VaultTopLevel(
                    screen, snapshot, list, active && page == null, model::selectTab, model::selectBook, model::manage, model::search,
                    model::sort, model::clearProblem, model::openDetail, model::copyPassword, model::newPassword,
                    model::generatorOptions, model::refreshGeneratedPassword, model::copyGenerated, overlays,
                )
            }
        }
    }
}

/**
 * 保持初次加载和失败重试在同一位置。
 * @param state 当前加载或错误状态
 * @param retry 重新读取数据
 */
@Composable
private fun VaultLoading(state: VaultState, retry: () -> Unit) {
    VaultFrame(stringResource(R.string.app_name)) { modifier ->
        Box(modifier, contentAlignment = Alignment.Center) {
            if (state.problem == null) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VaultErrorText(state.problem)
                TextButton(stringResource(R.string.lock_retry), onClick = retry)
            }
        }
    }
}

/**
 * 组织首页、生成及设置的统一导航，并处理搜索键盘与侧栏。
 * @param state 当前页面及操作状态
 * @param snapshot 当前本和分类数据
 * @param list 详情返回时恢复的列表位置
 * @param active 当前页面是否在前台
 * @param onTab 切换底部入口
 * @param onBook 切换当前密码本
 * @param onManage 打开分类管理
 * @param onQuery 修改搜索词
 * @param onSort 修改排序
 * @param onClear 清除错误反馈
 * @param onOpen 查看一条记录
 * @param onCopy 复制一条密码
 * @param onAdd 新建记录
 * @param onGeneratorOptions 修改生成参数
 * @param onRefreshGenerated 使用当前参数生成新密码
 * @param onCopyGenerated 复制生成结果并在成功后确认
 * @param overlays 挂载到顶层 Scaffold 的浮层
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultTopLevel(
    state: VaultState,
    snapshot: VaultSnapshot,
    list: LazyListState,
    active: Boolean,
    onTab: (VaultTab) -> Unit,
    onBook: (Long) -> Unit,
    onManage: (CategoryKind) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (VaultSort) -> Unit,
    onClear: () -> Unit,
    onOpen: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    onAdd: (String) -> Unit,
    onGeneratorOptions: (GeneratorOptions) -> Unit,
    onRefreshGenerated: () -> Unit,
    onCopyGenerated: (String, () -> Unit) -> Unit,
    overlays: @Composable () -> Unit = {},
) {
    var drawer by remember { mutableStateOf(false) }
    val keyboard = WindowInsets.isImeVisible
    val tabSlideDistance = with(LocalDensity.current) { 64.dp.roundToPx() }
    val editorOpen = state.page is VaultPage.Editor
    val background = if (editorOpen) Modifier.blockVaultBackgroundFocus() else Modifier
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val docked = maxWidth >= VaultLayout.DockedSidebarBreakpoint
        val home = state.tab == VaultTab.Home
        val drawerVisible = drawer && home && !docked
        BackHandler(active && !keyboard && (drawerVisible || !home)) {
            if (drawerVisible) drawer = false else onTab(VaultTab.Home)
        }
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .then(if (drawerVisible) Modifier.semantics { hideFromAccessibility() } else Modifier),
            bottomBar = { if (!keyboard) VaultNavigation(state.tab, onTab, background) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { navigationInsets ->
            AnimatedContent(
                targetState = state.tab,
                modifier = Modifier.padding(navigationInsets).consumeWindowInsets(navigationInsets)
                    .fillMaxSize().clipToBounds().then(background),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) {
                        SlideDirection.Start
                    } else SlideDirection.End
                    val durationMillis = 220
                    val enter = slideIntoContainer(direction, tween(durationMillis, easing = FastOutSlowInEasing)) {
                        it.coerceIn(-tabSlideDistance, tabSlideDistance)
                    } + fadeIn(tween(durationMillis, easing = FastOutSlowInEasing))
                    val exit = slideOutOfContainer(direction, tween(durationMillis, easing = FastOutSlowInEasing)) {
                        it.coerceIn(-tabSlideDistance, tabSlideDistance)
                    } + fadeOut(tween(durationMillis, easing = FastOutSlowInEasing))
                    (enter togetherWith exit) using null
                },
                label = "Vault tabs",
            ) { tab ->
                val homePage = tab == VaultTab.Home
                val generatorScroll = if (tab == VaultTab.Generate) MiuixScrollBehavior() else null
                val generatorSnackbar = remember { SnackbarHostState() }
                val focus = LocalFocusManager.current
                val bookTitle = snapshot.books.first { it.id == snapshot.bookId }.name
                val heading = when (tab) {
                    VaultTab.Home -> bookTitle
                    VaultTab.Generate -> stringResource(R.string.vault_generator_title)
                    VaultTab.Settings -> stringResource(R.string.vault_settings)
                }
                val sortAction: @Composable () -> Unit = {
                    if (homePage) SortMenu(
                        state.sort, state.busy, state.problem, onSort,
                        onOpen = {
                            focus.clearFocus()
                            onClear()
                        },
                        onDismiss = onClear,
                    )
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                        .then(if (generatorScroll == null) Modifier else Modifier.nestedScroll(generatorScroll.nestedScrollConnection))
                        .then(if (tab != state.tab) Modifier.semantics { hideFromAccessibility() } else Modifier),
                    topBar = {
                        if (!homePage || !docked) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                val header = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth()
                                if (generatorScroll != null) {
                                    TopAppBar(title = heading, modifier = header, scrollBehavior = generatorScroll)
                                } else {
                                    VaultTabHeader(
                                        title = heading,
                                        modifier = header,
                                        onOpenBooks = if (homePage) {
                                            {
                                                focus.clearFocus()
                                                drawer = true
                                            }
                                        } else null,
                                        actions = sortAction,
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = { if (tab == VaultTab.Generate) GeneratorSnackbarHost(generatorSnackbar) },
                    floatingActionButton = {
                        if (homePage && !keyboard && !drawerVisible) FloatingActionButton(
                            onClick = { if (!state.busy) onAdd("") },
                            modifier = Modifier.padding(horizontal = Theme.spacing.small),
                        ) {
                            Icon(MiuixIcons.Add, stringResource(R.string.vault_add), tint = MiuixTheme.colorScheme.onPrimary)
                        }
                    },
                    contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
                ) { insets ->
                    val body = Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize()
                    when (tab) {
                        VaultTab.Home -> Row(body) {
                            if (docked) {
                                BookSidebar(
                                    snapshot, state.busy, Modifier.width(VaultLayout.DockedSidebarWidth).fillMaxHeight(),
                                    onBook, { onManage(CategoryKind.Book) },
                                )
                                VerticalDivider()
                            }
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (docked) VaultTabHeader(
                                    title = heading, includeWindowInsets = false,
                                    modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
                                    actions = sortAction,
                                )
                                PasswordList(
                                    state, snapshot, list, Modifier.fillMaxWidth().weight(1f), onQuery, onOpen, onCopy,
                                )
                            }
                        }
                        VaultTab.Generate -> VaultColumn(body, horizontalPadding = Theme.spacing.medium) {
                            VaultErrorText(state.problem)
                            GeneratorPage(
                                state.generator, state.busy, onGeneratorOptions, onRefreshGenerated,
                                onCopyGenerated, onAdd, generatorSnackbar,
                            )
                        }
                        VaultTab.Settings -> VaultColumn(body, horizontalPadding = Theme.spacing.medium) {
                            VaultSettings(onManage)
                        }
                    }
                }
            }
            overlays()
        }
        if (!docked && home && !editorOpen) BookDrawer(
            drawerVisible, snapshot, state.busy, onDismiss = { drawer = false },
            onSelect = {
                drawer = false
                onBook(it)
            },
            onManage = {
                drawer = false
                onManage(CategoryKind.Book)
            },
        )
    }
}

/**
 * 固定高度的起始对齐顶栏。
 * @param title 当前页面标题
 * @param modifier 顶栏的宽度约束
 * @param includeWindowInsets 是否为顶栏处理系统栏边距
 * @param onOpenBooks 手机上的侧栏入口
 * @param actions 顶栏末端的操作
 */
@Composable
private fun VaultTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    includeWindowInsets: Boolean = true,
    onOpenBooks: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val insets = if (includeWindowInsets) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    } else Modifier
    Row(
        modifier.then(insets).heightIn(min = Theme.sizes.minimumTouchTarget + Theme.spacing.small)
            .padding(start = if (onOpenBooks == null) Theme.spacing.medium else Theme.spacing.small, end = Theme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenBooks != null) {
            VaultIcon(
                ImageVector.vectorResource(R.drawable.ic_vault_menu), R.string.vault_books,
                onClick = onOpenBooks, iconSize = VaultLayout.HomeHeaderIconSize,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MiuixTheme.textStyles.title3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/**
 * 底部导航的三个顶层目的地。
 * @param tab 当前标签
 * @param onTab 切换当前标签
 * @param modifier 底栏的位置和无障碍属性
 */
@Composable
private fun VaultNavigation(tab: VaultTab, onTab: (VaultTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            tab == VaultTab.Home, { onTab(VaultTab.Home) },
            ImageVector.vectorResource(R.drawable.ic_vault_home), stringResource(R.string.vault_home),
        )
        NavigationBarItem(
            tab == VaultTab.Generate, { onTab(VaultTab.Generate) },
            ImageVector.vectorResource(R.drawable.ic_vault_generate), stringResource(R.string.vault_generate),
        )
        NavigationBarItem(
            tab == VaultTab.Settings, { onTab(VaultTab.Settings) },
            ImageVector.vectorResource(R.drawable.ic_vault_settings), stringResource(R.string.vault_settings),
        )
    }
}

/**
 * 展示可搜索的记录列表，并限制长行的阅读宽度。
 * @param state 搜索、排序与操作状态
 * @param snapshot 当前本的记录摘要
 * @param list 当前列表位置
 * @param modifier 列表区域
 * @param onQuery 修改当前搜索
 * @param onOpen 打开详情
 * @param onCopy 单条复制
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordList(
    state: VaultState,
    snapshot: VaultSnapshot,
    list: LazyListState,
    modifier: Modifier,
    onQuery: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onCopy: (Long) -> Unit,
) {
    val focus = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0]
    val keyboard = WindowInsets.isImeVisible
    val entries = remember(snapshot, state.query, state.sort, locale) {
        snapshot.visiblePasswords(state.query, state.sort, locale)
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxSize().padding(horizontal = Theme.spacing.medium),
        ) {
            InputField(
                query = state.query, onQueryChange = onQuery, onSearch = { focus.clearFocus() },
                expanded = false, onExpandedChange = {}, modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.vault_search),
                leadingIcon = {
                    Icon(MiuixIcons.Search, null, Modifier.padding(horizontal = Theme.spacing.medium).size(20.dp))
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) VaultIcon(MiuixIcons.Close, R.string.vault_clear_search) { onQuery("") }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = list,
                contentPadding = PaddingValues(bottom = when {
                    entries.isEmpty() -> 0.dp
                    keyboard -> Theme.spacing.medium
                    else -> 88.dp
                }),
            ) {
                if (state.problem != null) item(key = "error") {
                    Box(Modifier.padding(top = Theme.spacing.medium)) { VaultErrorText(state.problem) }
                }
                if (entries.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            if (state.query.isBlank()) {
                                VaultEmptyState()
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
                                ) {
                                    Icon(
                                        MiuixIcons.Search, null, Modifier.size(32.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    Text(stringResource(R.string.vault_no_results), style = MiuixTheme.textStyles.title4)
                                }
                            }
                        }
                    }
                } else {
                    item(key = "count") {
                        Text(
                            pluralStringResource(R.plurals.vault_record_count, entries.size, entries.size),
                            Modifier.padding(top = Theme.spacing.large, bottom = Theme.spacing.small),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2,
                        )
                    }
                    itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 54.dp))
                        PasswordListRow(
                            entry, snapshot, !state.busy && state.page == null,
                            { onOpen(entry.id) }, { onCopy(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 记录行保留整个标题区域的查看操作与独立复制操作。
 * @param entry 当前记录摘要
 * @param snapshot 用于显示分类名称
 * @param enabled 当前是否可以操作
 * @param onOpen 打开详情
 * @param onCopy 复制密码
 */
@Composable
private fun PasswordListRow(
    entry: PasswordSummary,
    snapshot: VaultSnapshot,
    enabled: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val labels = listOfNotNull(snapshot.typeName(entry.typeId)) +
        snapshot.tagsByPassword[entry.id].orEmpty().map(VaultCategory::name)
    BasicComponent(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)),
        enabled = enabled, onClick = onOpen,
        insideMargin = PaddingValues(top = Theme.spacing.small, bottom = Theme.spacing.small),
        startAction = { VaultGlyph(entry.platform) },
        endActions = { VaultIcon(MiuixIcons.Copy, R.string.vault_copy_password, enabled, onClick = onCopy) },
    ) {
        Text(entry.platform, style = MiuixTheme.textStyles.title4, maxLines = 1, overflow = TextOverflow.Ellipsis)
        entry.account?.let { account ->
            Text(account, style = MiuixTheme.textStyles.body2, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (labels.isNotEmpty()) Text(
            labels.joinToString("  "), style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 手机侧栏从窗口起始边缘进出，遮罩跟随可见状态。
 * @param show 当前是否展开
 * @param snapshot 可切换的密码本
 * @param busy 是否正在读取
 * @param onDismiss 收起侧栏
 * @param onSelect 切换密码本
 * @param onManage 打开管理
 */
@Composable
private fun BookDrawer(
    show: Boolean,
    snapshot: VaultSnapshot,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1 else 1
    val scrim = Color.Black.copy(alpha = 0.40f)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(show, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(scrim)
                    .clickable(onClickLabel = stringResource(R.string.vault_close), onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            show, modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(240)) { direction * it },
            exit = slideOutHorizontally(tween(200)) { direction * it },
        ) {
            BookSidebar(
                snapshot, busy,
                Modifier.width(VaultLayout.DrawerWidth).fillMaxHeight().background(MiuixTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                onSelect, onManage, onDismiss,
            )
        }
    }
}

/**
 * 侧栏内容在手机抽屉与平板固定边栏共用。
 * @param snapshot 当前密码本列表
 * @param busy 是否正在切换
 * @param modifier 侧栏位置
 * @param onSelect 切换密码本
 * @param onManage 进入管理
 * @param onDismiss 手机侧栏的关闭动作
 */
@Composable
private fun BookSidebar(
    snapshot: VaultSnapshot,
    busy: Boolean,
    modifier: Modifier,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier.padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.vault_books), Modifier.weight(1f).padding(start = Theme.spacing.small),
                style = MiuixTheme.textStyles.title3,
            )
            if (onDismiss != null) VaultIcon(MiuixIcons.Close, R.string.vault_close, onClick = onDismiss)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
            itemsIndexed(snapshot.books, key = { _, book -> book.id }) { _, book ->
                val selected = book.id == snapshot.bookId
                val tint = colors.onSurface
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.15f) else colors.surface)
                        .selectable(selected, enabled = !busy, role = Role.RadioButton) { onSelect(book.id) }
                        .padding(Theme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
                ) {
                    Icon(if (selected) MiuixIcons.FolderFill else MiuixIcons.Folder, null, Modifier.size(20.dp), tint = tint)
                    Text(book.name, Modifier.weight(1f), tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.entryCount.toString(), color = tint, style = MiuixTheme.textStyles.body2.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
        HorizontalDivider()
        VaultLink(stringResource(R.string.vault_manage_books), enabled = !busy, icon = MiuixIcons.Folder, onClick = onManage)
    }
}

/**
 * 使用和应用一致的顶层容器渲染预览。
 * @param state 预览页面及样例数据
 */
@Composable
internal fun VaultTabbedPreview(state: VaultState) {
    val snapshot = state.snapshot ?: previewSnapshot()
    MyPassTheme {
        VaultTopLevel(
            state.copy(snapshot = snapshot), snapshot, remember { LazyListState() }, true,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> },
        )
    }
}

/**
 * 预览密码本列表。
 */
@VaultPreviews
@Composable
internal fun VaultHomePreview() {
    VaultTabbedPreview(VaultState())
}

/**
 * 预览首次打开空密码本时的创建入口。
 */
@VaultPreviews
@Composable
internal fun VaultEmptyPreview() {
    val snapshot = previewSnapshot()
    VaultTabbedPreview(VaultState(snapshot = snapshot.copy(bookId = 2, passwords = emptyList(), tagsByPassword = emptyMap())))
}
