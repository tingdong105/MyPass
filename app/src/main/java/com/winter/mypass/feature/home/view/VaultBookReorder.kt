package com.winter.mypass.feature.home.view

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.VaultCategory
import com.winter.mypass.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 保存拖动浮层和列表占位，隔离换位时的坐标变化。
 * @param list 密码本列表滚动状态
 * @param initial 初始密码本顺序
 * @author Tingdong
 * @date 2026-11-23
 */
private class BookReorderState(val list: LazyListState, initial: List<VaultCategory>) {
    var items by mutableStateOf(initial)
    var activeId by mutableStateOf<Long?>(null)
        private set
    var dragging by mutableStateOf(false)
        private set
    var top by mutableFloatStateOf(0f)
        private set
    val separation = Animatable(0f)
    var coordinates: LayoutCoordinates? = null
    val handles = mutableMapOf<Long, LayoutCoordinates>()
    private var pointerY = 0f
    private var height = 0
    private var moved = false
    private var original = initial

    /**
     * 仅命中三个点按钮的实际触控区域。
     * @param position 指针相对列表的位置
     * @return 命中的密码本标识，空值表示不接管手势
     */
    fun handleAt(position: Offset): Long? {
        val parent = coordinates?.takeIf { it.isAttached } ?: return null
        val visible = list.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        return handles.entries.firstOrNull { (id, handle) ->
            id in visible && handle.isAttached &&
                parent.localBoundingBoxOf(handle, clipBounds = false).contains(position)
        }?.key
    }

    /**
     * 在长按按钮所属的行上创建浮层。
     * @param id 按钮所属密码本标识
     * @param y 指针相对列表顶部的位置
     * @return 是否成功开始拖动
     */
    fun start(id: Long, y: Float): Boolean {
        if (activeId != null || items.size < 2) return false
        val start = list.layoutInfo.viewportStartOffset
        val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return false
        original = items
        top = (row.offset - start).toFloat()
        height = row.size
        pointerY = y
        moved = false
        activeId = row.key as Long
        dragging = true
        return true
    }

    /**
     * 按指针位移更新浮层。
     * @param delta 本次纵向位移
     */
    fun drag(delta: Float) {
        if (!dragging) return
        moved = moved || delta != 0f
        pointerY += delta
        top += delta
    }

    /**
     * 浮层中心越过相邻行中心时移动占位。
     * @return 是否发生换位
     */
    fun exchange(): Boolean {
        val index = items.indexOfFirst { it.id == activeId }
        if (index < 0) return false
        val center = top + height / 2f
        val start = list.layoutInfo.viewportStartOffset
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull { row ->
            val neighbor = items.indexOfFirst { it.id == row.key }
            row.index == neighbor && when (neighbor) {
                index - 1 -> center < row.offset - start + row.size / 2f
                index + 1 -> center > row.offset - start + row.size / 2f
                else -> false
            }
        } ?: return false
        move(index, target.index)
        return true
    }

    /**
     * 移动一行并保留当前视口位置。
     * @param from 原顺序
     * @param to 目标顺序
     */
    fun move(from: Int, to: Int) {
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * 根据指针与边缘距离计算自动滚动速度。
     * @param edge 边缘触发区域的像素高度
     * @return 归一化滚动方向和速度
     */
    fun edgeSpeed(edge: Float): Float {
        if (!moved) return 0f
        val viewport = list.layoutInfo
        val end = viewport.viewportSize.height
        return when {
            pointerY < edge -> -((edge - pointerY) / edge).coerceIn(0f, 1f)
            pointerY > end - edge -> ((pointerY - end + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /**
     * 同时收拢浮层并移回占位，取消时恢复原顺序。
     * @param commit 是否提交本次顺序
     * @param onCommit 顺序确实改变后的持久化回调
     */
    suspend fun finish(commit: Boolean, onCommit: (List<Long>) -> Unit) {
        if (!dragging) return
        dragging = false
        try {
            separation.stop()
            coroutineScope {
                // 松手即开始融合，与落位同步收尾。
                launch { separation.animateTo(0f, tween(200)) }
                if (!commit) {
                    list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
                    items = original
                }
                // 等待换位布局完成，落点使用新占位坐标。
                withFrameNanos { }
                withFrameNanos { }
                var target = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeId }
                if (target == null) {
                    list.scrollToItem(items.indexOfFirst { it.id == activeId }.coerceAtLeast(0))
                    target = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeId }
                }
                val destination = target?.let { (it.offset - list.layoutInfo.viewportStartOffset).toFloat() } ?: top
                Animatable(top).animateTo(destination, tween(200)) {
                    top = value
                }
            }
            val ids = items.map { it.id }
            if (commit && ids != original.map { it.id }) onCommit(ids)
        } finally {
            activeId = null
        }
    }
}

/**
 * 密码本长按排序列表及其跟手浮层。
 * @param categories 已保存的密码本顺序
 * @param busy 是否正在保存
 * @param enabled 是否允许开始拖动或打开菜单
 * @param modifier 列表区域
 * @param onInteractionChanged 拖动与落位期间的交互状态
 * @param onReorder 提交完整顺序
 * @param onRename 编辑行名称
 * @param onDelete 删除行
 */
@Composable
internal fun ReorderableBookList(
    categories: List<VaultCategory>,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onInteractionChanged: (Boolean) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onRename: (VaultCategory) -> Unit,
    onDelete: (VaultCategory) -> Unit,
) {
    val list = rememberLazyListState()
    val state = remember(list) { BookReorderState(list, categories) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val commit by rememberUpdatedState(onReorder)
    val interactionChanged by rememberUpdatedState(onInteractionChanged)
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 720.dp.toPx() }
    val elevation = with(density) { Theme.spacing.small.toPx() }
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val floating = state.activeId != null
    val finish: (Boolean) -> Unit = { save -> scope.launch { state.finish(save, commit) } }

    LaunchedEffect(categories, busy, floating) {
        if (!floating && !busy) state.items = categories
    }
    DisposableEffect(floating) {
        interactionChanged(floating)
        onDispose { if (floating) interactionChanged(false) }
    }
    LaunchedEffect(enabled) {
        if (!enabled && state.dragging) state.finish(false, commit)
    }
    LaunchedEffect(state.dragging) {
        if (!state.dragging) return@LaunchedEffect
        launch { state.separation.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 450f)) }
        var previous = withFrameNanos { it }
        while (state.dragging) {
            val now = withFrameNanos { it }
            if (!state.dragging) break
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.032f)
            previous = now
            list.scrollBy(state.edgeSpeed(edge) * speed * seconds)
            if (state.exchange()) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    BackHandler(enabled && floating) { finish(false) }

    val moveUp = stringResource(R.string.vault_book_move_up)
    val moveDown = stringResource(R.string.vault_book_move_down)
    Box(modifier.drawWithContent {
        // 仅裁切上下视口，横向阴影可延伸到窗口边缘。
        clipRect(left = -windowWidth, right = size.width + windowWidth, bottom = size.height) {
            this@drawWithContent.drawContent()
        }
    }) {
        LazyColumn(
            state = list,
            userScrollEnabled = !floating,
            contentPadding = PaddingValues(
                start = Theme.spacing.small,
                end = Theme.spacing.small,
                top = Theme.spacing.medium,
                bottom = FloatingActionButtonDefaults.MinHeight + Theme.spacing.large,
            ),
            modifier = Modifier.fillMaxSize().onGloballyPositioned { state.coordinates = it }
                .pointerInput(enabled, state) {
                    if (enabled) awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val id = state.handleAt(down.position) ?: return@awaitEachGesture
                            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            if (!state.start(id, press.position.y)) return@awaitEachGesture
                            press.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val released = drag(press.id) { change ->
                                state.drag(change.positionChange().y)
                                change.consume()
                            }
                            if (released) currentEvent.changes.forEach { if (it.changedToUp()) it.consume() }
                            finish(released)
                        } catch (cancelled: CancellationException) {
                            finish(false)
                            throw cancelled
                        }
                    }
                },
        ) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                DisposableEffect(item.id) {
                    onDispose { state.handles.remove(item.id) }
                }
                val hidden = state.activeId == item.id
                val topRadius by animateDpAsState(
                    if (index == 0) CardDefaults.CornerRadius else 0.dp,
                    tween(200), label = "Book top corners",
                )
                val bottomRadius by animateDpAsState(
                    if (index == state.items.lastIndex) CardDefaults.CornerRadius else 0.dp,
                    tween(200), label = "Book bottom corners",
                )
                val shape = RoundedCornerShape(
                    topStart = topRadius, topEnd = topRadius,
                    bottomStart = bottomRadius, bottomEnd = bottomRadius,
                )
                Column(
                    Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = if (hidden) null else spring(dampingRatio = 0.85f, stiffness = 400f),
                        fadeOutSpec = null,
                    ).graphicsLayer { alpha = if (hidden) 0f else 1f }
                        .background(MiuixTheme.colorScheme.surfaceContainer, shape)
                        .semantics(mergeDescendants = true) {
                            customActions = if (!enabled || floating) emptyList() else buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) {
                                    state.move(index, index - 1)
                                    commit(state.items.map { it.id })
                                    true
                                })
                                if (index < state.items.lastIndex) add(CustomAccessibilityAction(moveDown) {
                                    state.move(index, index + 1)
                                    commit(state.items.map { it.id })
                                    true
                                })
                            }
                        },
                ) {
                    CategoryRow(
                        CategoryKind.Book, item, enabled && !floating, { onRename(item) }, { onDelete(item) },
                        actionModifier = Modifier.onGloballyPositioned { state.handles[item.id] = it },
                    )
                    // 分隔线占用固定高度，换位时保持行高一致。
                    Box(Modifier.graphicsLayer { alpha = if (index == state.items.lastIndex) 0f else 1f }) {
                        VaultRowDivider()
                    }
                }
            }
        }
        state.items.firstOrNull { it.id == state.activeId }?.let { item ->
            val index = state.items.indexOf(item)
            val progress = state.separation.value.coerceIn(0f, 1f)
            val topRadius = CardDefaults.CornerRadius * if (index == 0) 1f else progress
            val bottomRadius = CardDefaults.CornerRadius * if (index == state.items.lastIndex) 1f else progress
            val floatingShape = RoundedCornerShape(
                topStart = topRadius, topEnd = topRadius,
                bottomStart = bottomRadius, bottomEnd = bottomRadius,
            )
            Column(
                Modifier.padding(horizontal = Theme.spacing.small).fillMaxWidth().graphicsLayer {
                    translationY = state.top
                    scaleX = 1f + 0.025f * progress
                    scaleY = scaleX
                    shadowElevation = elevation * progress
                    shape = floatingShape
                    clip = true
                }.background(MiuixTheme.colorScheme.surfaceContainer).clearAndSetSemantics { },
            ) {
                CategoryRow(CategoryKind.Book, item, false, {}, {}, dragging = true)
                Box(Modifier.graphicsLayer {
                    alpha = if (index == state.items.lastIndex) 0f else 1f - progress
                }) { VaultRowDivider() }
            }
        }
    }
}
