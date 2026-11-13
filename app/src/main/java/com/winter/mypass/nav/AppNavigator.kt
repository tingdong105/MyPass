package com.winter.mypass.nav

import androidx.annotation.MainThread
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack

/**
 * 统一执行导航与原子栈操作。
 * @param backStack 当前宿主的可恢复页面栈
 * @param results 会话内页面结果
 * @param validateRoute 验证目标路由已注册
 * @author Tingdong
 * @date 2026-11-13
 */
@MainThread
class AppNavigator internal constructor(
    private val backStack: NavBackStack<NavigationEntry>,
    private val results: NavigationResultStore,
    private val validateRoute: (AppRoute) -> Unit,
) {
    init {
        require(backStack.isNotEmpty()) { "导航栈不能为空" }
        require(backStack.map { it.id }.distinct().size == backStack.size) { "页面实例标识不能重复" }
    }

    /**
     * 跳转并按选项清理栈。示例：navigate(AppRoute.Home("听冬酱"))。
     * @param route 目标路由
     * @param options 导航选项
     * @return 页面栈是否发生变化
     * @throws IllegalStateException 在非主线程调用或路由未注册时抛出
     */
    fun navigate(route: AppRoute, options: NavigationOptions = NavigationOptions()): Boolean {
        checkNavigationMainThread()
        validateRoute(route)
        val entries = backStack.toMutableList()
        options.popUpTo?.let { target ->
            val index = entries.indexOfLast { it.route == target }
            if (index < 0) return false
            val keepCount = if (options.inclusive) index else index + 1
            entries.subList(keepCount, entries.size).clear()
        }
        if (!options.singleTop || entries.lastOrNull()?.route != route) {
            entries.add(NavigationEntry(route))
        }
        return commit(entries)
    }

    /**
     * 返回上一页。示例：navigateBack()。
     * @return 是否成功返回，根页面返回 false
     * @throws IllegalStateException 在非主线程调用时抛出
     */
    fun navigateBack(): Boolean {
        checkNavigationMainThread()
        return if (backStack.size > 1) commit(backStack.dropLast(1)) else false
    }

    /**
     * 返回最近匹配的路由。示例：navigateBackTo(AppRoute.Home())。
     * @param route 包含完整参数的目标路由
     * @param inclusive 是否同时移除目标页面
     * @return 是否改变页面栈，目标缺失或操作会清空栈时返回 false
     * @throws IllegalStateException 在非主线程调用时抛出
     */
    fun navigateBackTo(route: AppRoute, inclusive: Boolean = false): Boolean {
        checkNavigationMainThread()
        val index = backStack.indexOfLast { it.route == route }
        if (index < 0) return false
        val keepCount = if (inclusive) index else index + 1
        return if (keepCount > 0) commit(backStack.take(keepCount)) else false
    }

    /**
     * 替换当前页并保留更早的历史。示例：replaceCurrent(AppRoute.Home())。
     * @param route 新页面路由
     * @return 是否替换成功
     * @throws IllegalStateException 在非主线程调用或路由未注册时抛出
     */
    fun replaceCurrent(route: AppRoute): Boolean {
        checkNavigationMainThread()
        validateRoute(route)
        return commit(backStack.dropLast(1) + NavigationEntry(route))
    }

    /**
     * 将目标页设为新的栈底。示例：resetTo(AppRoute.Home())。
     * @param route 新的根路由
     * @return 是否重置成功
     * @throws IllegalStateException 在非主线程调用或路由未注册时抛出
     */
    fun resetTo(route: AppRoute): Boolean {
        checkNavigationMainThread()
        validateRoute(route)
        return commit(listOf(NavigationEntry(route)))
    }

    /**
     * 返回并向上一页面实例投递结果。示例：navigateBackWithResult(savedKey, true)。
     * @param key 结果类型标识
     * @param value 返回值
     * @return 是否投递并返回，根页面返回 false
     * @throws IllegalStateException 在非主线程调用或结果标识类型冲突时抛出
     */
    fun <T : Any> navigateBackWithResult(key: NavigationResultKey<T>, value: T): Boolean {
        checkNavigationMainThread()
        if (backStack.size <= 1) return false
        val receiver = backStack[backStack.lastIndex - 1]
        results.send(receiver.id, key, value)
        return commit(backStack.dropLast(1))
    }

    /**
     * 原子更新页面栈并释放已移除页面的结果。
     * @param entries 更新后的非空页面栈
     * @return 页面栈是否改变
     */
    private fun commit(entries: List<NavigationEntry>): Boolean {
        check(entries.isNotEmpty()) { "导航操作不能留下空栈" }
        if (backStack.toList() == entries) return false
        // 中间的清空操作只在本地快照内可见，宿主始终读取完整页面栈。
        Snapshot.withMutableSnapshot {
            backStack.clear()
            backStack.addAll(entries)
        }
        results.retainEntries(entries.mapTo(mutableSetOf()) { it.id })
        return true
    }
}
