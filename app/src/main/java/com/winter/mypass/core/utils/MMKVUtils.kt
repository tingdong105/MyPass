package com.winter.mypass.core.utils

import android.app.Application
import android.os.Parcelable
import com.tencent.mmkv.MMKV
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * MMKV 键值存储工具，默认实例用于非敏感配置。
 * @author Tingdong
 * @date 2026-11-13
 */
object MMKVUtils {

    @Volatile
    private var rootDirectory: String? = null

    private val defaultMMKV: MMKV by lazy {
        checkInitialization()
        MMKV.defaultMMKV()
    }

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 初始化存储，重复调用返回同一目录。
     * 使用示例：MMKVUtils.init(application)
     * @param application 应用实例
     * @return MMKV 根目录
     */
    @Synchronized
    fun init(application: Application): String {
        return rootDirectory ?: MMKV.initialize(application).also { rootDirectory = it }
    }

    /**
     * 检查初始化状态。
     * 使用示例：checkInitialization()
     * @throws IllegalStateException 尚未初始化
     */
    private fun checkInitialization() {
        check(rootDirectory != null) { "请先在 Application 中调用 MMKVUtils.init()" }
    }

    /**
     * 校验键并获取默认存储。
     * 使用示例：store("theme")
     * @param key 非空白键
     * @return 默认存储实例
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    private fun store(key: String): MMKV {
        require(key.isNotBlank()) { "MMKV 键不能为空白" }
        return defaultMMKV
    }

    /**
     * 打开命名实例，同一文件须始终使用相同模式和密钥。
     * 使用示例：MMKVUtils.getInstance("settings")
     * @param name 非空白实例名
     * @param mode 单进程或多进程模式
     * @param cryptKey 加密密钥，null 表示不加密，Modified UTF-8 长度为 1 至 16 字节
     * @param rootDir 自定义目录，null 表示默认目录
     * @return MMKV 实例，生命周期及密钥变更由调用方管理
     * @throws IllegalArgumentException 参数无效
     * @throws IllegalStateException 尚未初始化
     * @throws RuntimeException 实例打开失败
     */
    fun getInstance(
        name: String,
        mode: Int = MMKV.SINGLE_PROCESS_MODE,
        cryptKey: String? = null,
        rootDir: String? = null
    ): MMKV {
        checkInitialization()
        require(name.isNotBlank()) { "MMKV 实例名不能为空白" }
        require(mode == MMKV.SINGLE_PROCESS_MODE || mode == MMKV.MULTI_PROCESS_MODE) {
            "MMKV 模式必须为单进程或多进程"
        }
        require(rootDir == null || rootDir.isNotBlank()) { "MMKV 目录不能为空白" }
        validateCryptKey(cryptKey)
        return MMKV.mmkvWithID(name, mode, cryptKey, rootDir)
    }

    /**
     * 按 JNI 的 Modified UTF-8 编码校验密钥长度。
     * 使用示例：validateCryptKey(cryptKey)
     * @param cryptKey 加密密钥，null 表示不加密
     * @throws IllegalArgumentException 密钥为空或编码后超过 16 字节
     */
    private fun validateCryptKey(cryptKey: String?) {
        if (cryptKey == null) return
        var byteCount = 0
        for (char in cryptKey) {
            byteCount += when (char.code) {
                in 1..0x7F -> 1
                in 0..0x7FF -> 2
                else -> 3
            }
            require(byteCount <= 16) { "MMKV 密钥的 Modified UTF-8 长度不能超过 16 字节" }
        }
        require(byteCount > 0) { "MMKV 密钥不能为空" }
    }

    /**
     * 打开多进程存储，各进程须使用一致的密钥。
     * 使用示例：MMKVUtils.getMultiProcessInstance("shared")
     * @param name 实例名
     * @param cryptKey 加密密钥，null 表示不加密
     * @return 多进程实例
     * @throws IllegalArgumentException 参数无效
     * @throws IllegalStateException 尚未初始化
     */
    fun getMultiProcessInstance(name: String, cryptKey: String? = null): MMKV {
        return getInstance(name, MMKV.MULTI_PROCESS_MODE, cryptKey)
    }

    /**
     * 打开加密存储，密钥由调用方安全提供。
     * 使用示例：MMKVUtils.getEncryptedInstance("secure", cryptKey)
     * @param name 实例名
     * @param cryptKey Modified UTF-8 长度为 1 至 16 字节的密钥
     * @param multiProcess 是否允许多进程访问
     * @return 加密实例
     * @throws IllegalArgumentException 参数无效
     * @throws IllegalStateException 尚未初始化
     */
    fun getEncryptedInstance(name: String, cryptKey: String, multiProcess: Boolean = false): MMKV {
        val mode = if (multiProcess) MMKV.MULTI_PROCESS_MODE else MMKV.SINGLE_PROCESS_MODE
        return getInstance(name, mode, cryptKey)
    }

    /**
     * 保存 Boolean 值。
     * 使用示例：MMKVUtils.putBoolean("setting", true)
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putBoolean(key: String, value: Boolean): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Boolean 值。
     * 使用示例：MMKVUtils.getBoolean("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return store(key).decodeBool(key, defaultValue)
    }

    /**
     * 保存 Int 值。
     * 使用示例：MMKVUtils.putInt("setting", 3)
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putInt(key: String, value: Int): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Int 值。
     * 使用示例：MMKVUtils.getInt("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return store(key).decodeInt(key, defaultValue)
    }

    /**
     * 保存 Long 值。
     * 使用示例：MMKVUtils.putLong("setting", 123L)
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putLong(key: String, value: Long): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Long 值。
     * 使用示例：MMKVUtils.getLong("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return store(key).decodeLong(key, defaultValue)
    }

    /**
     * 保存 Float 值。
     * 使用示例：MMKVUtils.putFloat("setting", 1.5f)
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putFloat(key: String, value: Float): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Float 值。
     * 使用示例：MMKVUtils.getFloat("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return store(key).decodeFloat(key, defaultValue)
    }

    /**
     * 保存 Double 值。
     * 使用示例：MMKVUtils.putDouble("setting", 1.5)
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putDouble(key: String, value: Double): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Double 值。
     * 使用示例：MMKVUtils.getDouble("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return store(key).decodeDouble(key, defaultValue)
    }

    /**
     * 保存 String 值，null 表示删除。
     * 使用示例：MMKVUtils.putString("setting", "dark")
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putString(key: String, value: String?): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 String 值。
     * 使用示例：MMKVUtils.getString("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return store(key).decodeString(key, defaultValue) ?: defaultValue
    }

    /**
     * 保存 ByteArray 值，null 表示删除。
     * 使用示例：MMKVUtils.putBytes("setting", byteArrayOf(1, 2))
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putBytes(key: String, value: ByteArray?): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 ByteArray 值。
     * 使用示例：MMKVUtils.getBytes("setting")
     * @param key 非空白键
     * @return 保存的值或 null
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getBytes(key: String): ByteArray? {
        return store(key).decodeBytes(key)
    }

    /**
     * 保存 Set<String> 值，null 表示删除。
     * 使用示例：MMKVUtils.putStringSet("setting", setOf("work", "home"))
     * @param key 非空白键
     * @param value 待保存值
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun putStringSet(key: String, value: Set<String>?): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Set<String> 值。
     * 使用示例：MMKVUtils.getStringSet("setting")
     * @param key 非空白键
     * @param defaultValue 缺失或无法解码时的默认值
     * @return 保存的值或默认值
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> {
        return store(key).decodeStringSet(key, defaultValue)?.toSet() ?: defaultValue.toSet()
    }

    /**
     * 保存 Parcelable 对象，null 表示删除。
     * 使用示例：MMKVUtils.putParcelable("filter", filter)
     * @param key 非空白键
     * @param value 实现 Parcelable 的对象
     * @return 是否写入成功
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun <T : Parcelable> putParcelable(key: String, value: T?): Boolean {
        return store(key).encode(key, value)
    }

    /**
     * 读取 Parcelable 对象。
     * 使用示例：MMKVUtils.getParcelable("filter", Filter::class.java)
     * @param key 非空白键
     * @param clazz 对象类型
     * @return 保存的对象，缺失或解码失败时返回 null
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun <T : Parcelable> getParcelable(key: String, clazz: Class<T>): T? {
        return store(key).decodeParcelable(key, clazz)
    }

    /**
     * 将可序列化对象保存为 JSON。
     * 使用示例：MMKVUtils.putObject("preferences", preferences)
     * @param key 非空白键
     * @param value 支持 kotlinx.serialization 的对象
     * @return 是否写入成功
     * @throws SerializationException 对象无法序列化
     * @throws IllegalArgumentException 键或对象无效
     * @throws IllegalStateException 尚未初始化
     */
    inline fun <reified T> putObject(key: String, value: T): Boolean {
        return putString(key, json.encodeToString(value))
    }

    /**
     * 读取 JSON 对象，解析失败向调用方抛出异常。
     * 使用示例：MMKVUtils.getObject<Preferences>("preferences")
     * @param key 非空白键
     * @return 保存的对象，键不存在时返回 null
     * @throws SerializationException 数据损坏或缺少序列化器
     * @throws IllegalArgumentException 键或数据不符合类型要求
     * @throws IllegalStateException 尚未初始化
     */
    inline fun <reified T> getObject(key: String): T? {
        val value = readObjectJson(key) ?: return null
        return json.decodeFromString<T>(value)
    }

    /**
     * 一次读取对象的 JSON 数据。
     * 使用示例：readObjectJson("preferences")
     * @param key 非空白键
     * @return JSON 字符串，键不存在时返回 null
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    @PublishedApi
    internal fun readObjectJson(key: String): String? {
        return store(key).decodeString(key, null)
    }

    /**
     * 判断指定键是否存在。
     * 使用示例：MMKVUtils.containsKey("theme")
     * @param key 非空白键
     * @return 是否存在
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun containsKey(key: String): Boolean {
        return store(key).containsKey(key)
    }

    /**
     * 删除指定键。
     * 使用示例：MMKVUtils.remove("theme")
     * @param key 非空白键
     * @throws IllegalArgumentException 键为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun remove(key: String) {
        store(key).removeValueForKey(key)
    }

    /**
     * 删除匹配前缀的键，匹配与删除不构成事务。
     * 使用示例：MMKVUtils.removeValuesForKeys("settings_")
     * @param keyPrefix 非空白键前缀
     * @throws IllegalArgumentException 前缀为空白
     * @throws IllegalStateException 尚未初始化
     */
    fun removeValuesForKeys(keyPrefix: String) {
        require(keyPrefix.isNotBlank()) { "删除前缀不能为空白，请使用 clearAll() 清空" }
        val keys = getAllKeys().filter { it.startsWith(keyPrefix) }.toTypedArray()
        if (keys.isNotEmpty()) defaultMMKV.removeValuesForKeys(keys)
    }

    /**
     * 清空默认实例的数据。
     * 使用示例：MMKVUtils.clearAll()
     * @throws IllegalStateException 尚未初始化
     */
    fun clearAll() {
        defaultMMKV.clearAll()
    }

    /**
     * 获取默认实例的所有键。
     * 使用示例：MMKVUtils.getAllKeys()
     * @return 键集合，无数据时为空集合
     * @throws IllegalStateException 尚未初始化
     */
    fun getAllKeys(): Set<String> {
        return defaultMMKV.allKeys()?.toSet() ?: emptySet()
    }

    /**
     * 获取默认实例的文件大小。
     * 使用示例：MMKVUtils.totalSize()
     * @return 文件大小，单位为字节
     * @throws IllegalStateException 尚未初始化
     */
    fun totalSize(): Long {
        return defaultMMKV.totalSize()
    }

    /**
     * 获取默认实例的条目数量。
     * 使用示例：MMKVUtils.count()
     * @return 条目数量
     * @throws IllegalStateException 尚未初始化
     */
    fun count(): Long {
        return defaultMMKV.count()
    }
}

