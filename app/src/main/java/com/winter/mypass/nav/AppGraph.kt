package com.winter.mypass.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import com.winter.mypass.feature.home.view.HomeScreen

/**
 * 注册路由与页面入口。示例：entry<AppRoute.Home> { HomeScreen(it.name) }。
 * @return 类型安全的页面注册表
 */
internal fun appEntryProvider(): (AppRoute) -> NavEntry<AppRoute> = entryProvider(
    fallback = { route -> error("未注册导航路由：${route::class.simpleName}") },
) {
    entry<AppRoute.Home> { route ->
        HomeScreen(name = route.name)
    }
}
