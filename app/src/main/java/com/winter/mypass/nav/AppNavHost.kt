package com.winter.mypass.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay

/**
 * 应用导航宿主，统一管理页面展示、状态和返回行为。
 * 使用示例：MyPassTheme { AppNavHost() }。
 * @param modifier 宿主布局修饰符
 * @param startRoute 首次创建页面栈时的默认入口
 */
@Composable
fun AppNavHost(modifier: Modifier = Modifier, startRoute: AppRoute = AppRoute.Home()) {
    // 显式使用生成的序列化器，保留重复路由各自的实例标识。
    val backStack = rememberSerializable(
        serializer = NavBackStackSerializer(NavigationEntry.serializer()),
    ) {
        NavBackStack(NavigationEntry(startRoute))
    }
    val hostState = viewModel { NavigationHostViewModel() }
    val routeProvider = remember { appEntryProvider() }
    val navigator = remember(backStack, hostState, routeProvider) {
        AppNavigator(backStack, hostState.results) { route -> routeProvider(route) }
    }

    DisposableEffect(navigator) {
        val hostToken = Any()
        hostState.results.retainEntries(backStack.mapTo(mutableSetOf()) { it.id })
        NavigationService.bind(hostToken, navigator)
        onDispose { NavigationService.unbind(hostToken) }
    }

    val entryProvider = remember(backStack, routeProvider, hostState) {
        { entry: NavigationEntry ->
            val destination = routeProvider(entry.route)
            val resultContext = NavigationResultContext(entry.id, hostState.results) {
                backStack.any { it.id == entry.id }
            }
            NavEntry(key = entry, contentKey = entry.id, metadata = destination.metadata) {
                CompositionLocalProvider(LocalNavigationResultContext provides resultContext) {
                    destination.Content()
                }
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = { navigator.navigateBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider,
    )
}
