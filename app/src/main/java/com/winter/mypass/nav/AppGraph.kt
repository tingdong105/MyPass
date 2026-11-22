package com.winter.mypass.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.winter.mypass.feature.about.view.AboutScreen
import com.winter.mypass.feature.about.view.OpenSourceLicensesRoute
import com.winter.mypass.feature.applock.view.ChangeCredentialRoute
import com.winter.mypass.feature.applock.viewmodel.AppLockViewModel
import com.winter.mypass.feature.backup.view.CloudBackupScreen
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
        VaultScreen(
            vault,
            onBackup = { navigator.navigate(AppRoute.Backup) },
            onChangeCredential = { navigator.navigate(AppRoute.ChangeCredential) },
            onAbout = { navigator.navigate(AppRoute.About) },
        )
    }
    entry<AppRoute.Backup>(metadata = pageTransitions) {
        CloudBackupScreen(appLock.backupModel, LocalAppNavigator.current::navigateBack)
    }
    entry<AppRoute.ChangeCredential>(metadata = pageTransitions) {
        ChangeCredentialRoute(appLock, LocalAppNavigator.current::navigateBack)
    }
    entry<AppRoute.About>(metadata = pageTransitions) {
        val navigator = LocalAppNavigator.current
        AboutScreen(navigator::navigateBack) { navigator.navigate(AppRoute.OpenSourceLicenses()) }
    }
    entry<AppRoute.OpenSourceLicenses>(metadata = pageTransitions) { route ->
        val navigator = LocalAppNavigator.current
        OpenSourceLicensesRoute(route.libraryId, navigator::navigateBack) {
            navigator.navigate(AppRoute.OpenSourceLicenses(it))
        }
    }
}

private val pageTransitions = NavDisplay.transitionSpec {
    val enter = slideIntoContainer(SlideDirection.Start, tween(250, easing = FastOutSlowInEasing)) { it / 7 }
    (enter + fadeIn(tween(180))) togetherWith fadeOut(tween(135))
} + NavDisplay.popTransitionSpec {
    pagePopTransition()
} + NavDisplay.predictivePopTransitionSpec {
    pagePopTransition()
}

/**
 * 为按钮和系统手势提供一致的返回动画。
 * @receiver 当前页面转场范围
 * @return 向右滑出并交叉淡入淡出的转场
 */
private fun AnimatedContentTransitionScope<Scene<*>>.pagePopTransition(): ContentTransform {
    val exit = slideOutOfContainer(SlideDirection.End, tween(225, easing = FastOutSlowInEasing)) { it / 7 }
    return fadeIn(tween(165)) togetherWith (exit + fadeOut(tween(150)))
}
