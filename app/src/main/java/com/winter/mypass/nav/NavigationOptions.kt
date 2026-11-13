package com.winter.mypass.nav

/**
 * 页面跳转与栈清理选项。
 * @param singleTop 是否复用参数完全相同的栈顶页面
 * @param popUpTo 入栈前清理到最近匹配的路由，缺失时取消整个操作
 * @param inclusive 清理时是否包含目标页面
 * @author Tingdong
 * @date 2026-11-13
 */
data class NavigationOptions(
    val singleTop: Boolean = true,
    val popUpTo: AppRoute? = null,
    val inclusive: Boolean = false,
)
