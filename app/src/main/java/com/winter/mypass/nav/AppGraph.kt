package com.winter.mypass.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import com.winter.mypass.feature.home.view.VaultScreen
import com.winter.mypass.feature.home.viewmodel.VaultViewModel

/**
 * 注册统一导航的页面入口。
 * @param vault 当前解锁会话的密码本状态
 * @return 类型安全的页面注册表
 */
internal fun appEntryProvider(vault: VaultViewModel): (AppRoute) -> NavEntry<AppRoute> = entryProvider(
    fallback = { route -> error("未注册导航路由：${route::class.simpleName}") },
) {
    entry<AppRoute.Home> {
        VaultScreen(vault)
    }
}
