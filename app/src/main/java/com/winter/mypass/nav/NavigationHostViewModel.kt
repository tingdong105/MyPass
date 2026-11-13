package com.winter.mypass.nav

import androidx.lifecycle.ViewModel

/**
 * 在配置变化期间保留会话内结果。
 * @author Tingdong
 * @date 2026-11-13
 */
internal class NavigationHostViewModel : ViewModel() {
    val results = NavigationResultStore()

    /**
     * 宿主会话结束时关闭结果通道。
     */
    override fun onCleared() {
        results.clear()
    }
}
