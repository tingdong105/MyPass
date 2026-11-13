package com.winter.mypass.nav

import androidx.navigation3.runtime.result.ResultEventBus
import kotlin.reflect.KType

/**
 * 按页面实例隔离官方结果通道。
 * @author Tingdong
 * @date 2026-11-13
 */
internal class NavigationResultStore {
    val eventBus = ResultEventBus()
    private val channels = mutableMapOf<String, MutableSet<String>>()
    private val keyTypes = mutableMapOf<String, KType>()

    /**
     * 注册页面结果通道。示例：channel(entryId, savedKey)。
     * @param entryId 接收页面实例
     * @param key 结果标识
     * @return 当前实例专用的通道名
     * @throws IllegalStateException 同名标识绑定不同类型时抛出
     */
    fun <T : Any> channel(entryId: String, key: NavigationResultKey<T>): String {
        val previousType = keyTypes[key.name]
        check(previousType == null || previousType == key.valueType) {
            "导航结果标识类型冲突：${key.name}"
        }
        keyTypes[key.name] = key.valueType
        val channel = "$entryId:${key.name}"
        channels.getOrPut(entryId) { mutableSetOf() }.add(channel)
        return channel
    }

    /**
     * 投递结果到指定页面。示例：send(entryId, savedKey, true)。
     * @param entryId 接收页面实例
     * @param key 结果标识
     * @param value 返回值
     */
    fun <T : Any> send(entryId: String, key: NavigationResultKey<T>, value: T) {
        eventBus.sendResult(channel(entryId, key), value)
    }

    /**
     * 仅保留仍在栈内的页面通道。示例：retainEntries(activeIds)。
     * @param entryIds 有效页面实例集合
     */
    fun retainEntries(entryIds: Set<String>) {
        val removedIds = channels.keys.filter { it !in entryIds }
        removedIds.forEach { id ->
            channels.remove(id)?.forEach { eventBus.removeResult(it) }
        }
    }

    /**
     * 释放整个会话的结果。示例：clear()。
     */
    fun clear() {
        retainEntries(emptySet())
        keyTypes.clear()
    }
}
