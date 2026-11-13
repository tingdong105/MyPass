package com.winter.mypass.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation3.runtime.result.ResultEffect

/**
 * 当前页面实例的结果接收上下文。
 * @param entryId 当前页面实例
 * @param store 宿主的结果通道
 * @param isInBackStack 页面是否仍在返回栈中
 * @author Tingdong
 * @date 2026-11-13
 */
internal class NavigationResultContext(
    val entryId: String,
    val store: NavigationResultStore,
    val isInBackStack: () -> Boolean,
)

internal val LocalNavigationResultContext = staticCompositionLocalOf<NavigationResultContext?> { null }

/**
 * 页面活跃时一次性接收结果，同一页面的每个标识只注册一个接收器。
 * 使用示例：NavigationResultEffect(savedKey) { viewModel.onSaved(it) }。
 * @param key 与发送端共享的类型安全标识
 * @param onResult 同步处理结果的回调
 * @throws IllegalStateException 在导航页面之外使用时抛出
 */
@Composable
fun <T : Any> NavigationResultEffect(key: NavigationResultKey<T>, onResult: (T) -> Unit) {
    val context = checkNotNull(LocalNavigationResultContext.current) {
        "NavigationResultEffect 必须在 AppNavHost 注册的页面内使用"
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // 出栈动画中的页面不再订阅；未恢复到前台的页面保留通道内待接收结果。
    if (context.isInBackStack() && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        val channel = remember(context, key) { context.store.channel(context.entryId, key) }
        ResultEffect<T>(resultKey = channel, resultEventBus = context.store.eventBus) { value ->
            if (context.isInBackStack()) onResult(value)
        }
    }
}
