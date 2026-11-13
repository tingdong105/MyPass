package com.winter.mypass.nav

import android.os.Looper
import androidx.annotation.MainThread

/**
 * 当前导航宿主的轻量绑定，不持有 Activity。
 * @author Tingdong
 * @date 2026-11-13
 */
internal object NavigationService {
    private var hostToken: Any? = null
    private var navigator: AppNavigator? = null

    /**
     * 绑定宿主身份与导航器。
     * @param token 当前宿主的唯一身份
     * @param appNavigator 当前导航器
     */
    @MainThread
    fun bind(token: Any, appNavigator: AppNavigator) {
        checkNavigationMainThread()
        hostToken = token
        navigator = appNavigator
    }

    /**
     * 按身份解绑，避免旧宿主清除新宿主。
     * @param token 离开组合的宿主身份
     */
    @MainThread
    fun unbind(token: Any) {
        checkNavigationMainThread()
        if (hostToken === token) {
            navigator = null
            hostToken = null
        }
    }

    /**
     * 将操作转交有效宿主。
     * @param action 导航操作
     * @return 是否改变页面栈，未绑定时返回 false
     */
    @MainThread
    fun dispatch(action: AppNavigator.() -> Boolean): Boolean {
        checkNavigationMainThread()
        return navigator?.action() ?: false
    }
}

/**
 * 跳转到指定页面。示例：navigate(AppRoute.Home("听冬酱"))。
 * @param route 类型安全路由
 * @param options 跳转及栈清理选项
 * @return 是否改变页面栈，未绑定宿主时返回 false
 * @throws IllegalStateException 在非主线程调用或路由未注册时抛出
 */
@MainThread
fun navigate(route: AppRoute, options: NavigationOptions = NavigationOptions()): Boolean =
    NavigationService.dispatch { navigate(route, options) }

/**
 * 返回上一页。示例：navigateBack()。
 * @return 是否返回成功，根页面或未绑定宿主时返回 false
 * @throws IllegalStateException 在非主线程调用时抛出
 */
@MainThread
fun navigateBack(): Boolean = NavigationService.dispatch { navigateBack() }

/**
 * 返回最近匹配的页面。示例：navigateBackTo(AppRoute.Home())。
 * @param route 包含完整参数的目标路由
 * @param inclusive 是否包含目标页面
 * @return 是否改变页面栈，目标缺失、会清空栈或未绑定时返回 false
 * @throws IllegalStateException 在非主线程调用时抛出
 */
@MainThread
fun navigateBackTo(route: AppRoute, inclusive: Boolean = false): Boolean =
    NavigationService.dispatch { navigateBackTo(route, inclusive) }

/**
 * 替换当前页面。示例：replaceCurrent(AppRoute.Home())。
 * @param route 新页面路由
 * @return 是否替换成功，未绑定宿主时返回 false
 * @throws IllegalStateException 在非主线程调用或路由未注册时抛出
 */
@MainThread
fun replaceCurrent(route: AppRoute): Boolean = NavigationService.dispatch { replaceCurrent(route) }

/**
 * 重置页面栈。示例：resetTo(AppRoute.Home())。
 * @param route 新的根路由
 * @return 是否重置成功，未绑定宿主时返回 false
 * @throws IllegalStateException 在非主线程调用或路由未注册时抛出
 */
@MainThread
fun resetTo(route: AppRoute): Boolean = NavigationService.dispatch { resetTo(route) }

/**
 * 返回并向上一页面实例回传结果。示例：navigateBackWithResult(savedKey, true)。
 * @param key 结果类型标识
 * @param value 返回值
 * @return 是否投递并返回，根页面或未绑定宿主时返回 false
 * @throws IllegalStateException 在非主线程调用或结果标识类型冲突时抛出
 */
@MainThread
fun <T : Any> navigateBackWithResult(key: NavigationResultKey<T>, value: T): Boolean =
    NavigationService.dispatch { navigateBackWithResult(key, value) }

/**
 * 校验导航调用线程。示例：checkNavigationMainThread()。
 * @throws IllegalStateException 在非主线程调用时抛出
 */
internal fun checkNavigationMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) { "导航操作必须在主线程调用" }
}
