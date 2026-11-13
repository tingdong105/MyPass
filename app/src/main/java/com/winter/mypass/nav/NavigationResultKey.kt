package com.winter.mypass.nav

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * 将会话内结果标识与数据类型绑定。
 * @param T 非空结果类型
 * @param name 全应用唯一的结果名称
 * @param valueType 包含泛型参数的数据类型
 * @author Tingdong
 * @date 2026-11-13
 */
class NavigationResultKey<T : Any> @PublishedApi internal constructor(
    val name: String,
    internal val valueType: KType,
) {
    init {
        require(name.isNotBlank()) { "导航结果名称不能为空" }
    }
}

/**
 * 创建类型安全结果标识。示例：val savedKey = navigationResultKey<Boolean>("credential_saved")。
 * @param T 非空结果类型
 * @param name 全应用唯一的结果名称
 * @return 与数据类型绑定的结果标识
 * @throws IllegalArgumentException 名称为空时抛出
 */
inline fun <reified T : Any> navigationResultKey(name: String): NavigationResultKey<T> =
    NavigationResultKey(name, typeOf<T>())
