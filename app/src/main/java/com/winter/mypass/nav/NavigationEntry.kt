package com.winter.mypass.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 独立页面实例，保存路由和稳定的状态标识。
 * @param route 页面路由
 * @param id 页面实例标识
 * @author Tingdong
 * @date 2026-11-13
 */
@Serializable
internal data class NavigationEntry(
    val route: AppRoute,
    val id: String = UUID.randomUUID().toString(),
) : NavKey
