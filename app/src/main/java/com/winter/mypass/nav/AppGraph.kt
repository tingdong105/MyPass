package com.winter.mypass.nav

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.winter.mypass.feature.applock.view.ChangeCredentialRoute
import com.winter.mypass.feature.applock.viewmodel.AppLockViewModel
import com.winter.mypass.feature.home.view.VaultScreen
import com.winter.mypass.feature.home.viewmodel.VaultViewModel

/**
 * 注册统一导航的页面入口。
 * @param vault 当前解锁会话的密码本状态
 * @param appLock 当前会话的应用锁状态
 * @return 类型安全的页面注册表
 */
internal fun appEntryProvider(vault: VaultViewModel, appLock: AppLockViewModel): (AppRoute) -> NavEntry<AppRoute> = entryProvider(
    fallback = { route -> error("未注册导航路由：${route::class.simpleName}") },
) {
    entry<AppRoute.Home> {
        val navigator = LocalAppNavigator.current
        VaultScreen(vault) { navigator.navigate(AppRoute.ChangeCredential) }
    }
    entry<AppRoute.ChangeCredential>(metadata = changeCredentialTransitions) {
        ChangeCredentialRoute(appLock, LocalAppNavigator.current::navigateBack)
    }
}

private val changeCredentialTransitions = NavDisplay.transitionSpec {
    val enter = slideIntoContainer(SlideDirection.Start, tween(250, easing = FastOutSlowInEasing)) { it / 7 }
    (enter + fadeIn(tween(180))) togetherWith fadeOut(tween(135))
} + NavDisplay.popTransitionSpec {
    val exit = slideOutOfContainer(SlideDirection.End, tween(225, easing = FastOutSlowInEasing)) { it / 7 }
    fadeIn(tween(165)) togetherWith (exit + fadeOut(tween(150)))
}
