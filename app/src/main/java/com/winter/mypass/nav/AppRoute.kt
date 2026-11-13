package com.winter.mypass.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 类型安全页面路由，参数仅承载标识和轻量数据。
 * @author Tingdong
 * @date 2026-11-13
 */
@Serializable
sealed interface AppRoute : NavKey {
    /**
     * 默认首页路由。
     * @param name 问候对象
     * @author Tingdong
     * @date 2026-11-13
     */
    @Serializable
    @SerialName("home")
    data class Home(val name: String = "Android") : AppRoute
}
