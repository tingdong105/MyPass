package com.winter.mypass.core.utils

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import com.winter.mypass.core.database.SqlCipherOpenHelperFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException

/**
 * Room 加密数据库会话，统一管理打开、访问与关闭。
 * 每个数据库文件由业务层持有一个实例，业务层定义带 @Database 的数据库及 DAO。
 * 使用示例：val store = RoomUtils(context, VaultDatabase::class.java, "vault.db")
 * @param context 应用或页面上下文，仅保留应用上下文
 * @param databaseClass 业务数据库类型
 * @param name 应用私有目录内的数据库文件名
 * @param migrations 完整的数据库迁移路径
 * @author Tingdong
 * @date 2026-11-13
 */
class RoomUtils<T : RoomDatabase>(
    context: Context,
    private val databaseClass: Class<T>,
    private val name: String,
    vararg migrations: Migration
) {
    private val applicationContext = context.applicationContext
    private val migrations = migrations.copyOf()
    private val mutex = Mutex()
    private var database: T? = null
    private var sessionKey: ByteArray? = null

    init {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*\\.db"))) {
            "数据库名须以字母或数字开头，以 .db 结尾，且不能包含路径"
        }
    }

    /**
     * 在输入密码期间预先加载数据库原生库。
     * 使用示例：store.prepare()
     * @throws UnsatisfiedLinkError 原生库缺失或不支持当前设备
     */
    suspend fun prepare() = withContext(Dispatchers.IO) { loadSqlCipher() }

    /**
     * 在 IO 线程打开数据库并验证密钥和迁移。
     * 使用示例：try { store.open(vaultKey) } finally { vaultKey.fill(0) }
     * @param key 随机生成或经安全派生的 32 字节密钥，调用期间不得修改，调用方负责清理原数组
     * @throws IllegalArgumentException 密钥长度不符
     * @throws IllegalStateException 数据库已打开或缺少迁移
     * @throws android.database.sqlite.SQLiteException 密钥错误或数据库无法读取
     */
    suspend fun open(key: ByteArray) {
        require(key.size == 32) { "数据库密钥必须为 32 字节，请勿直接使用主密码" }
        mutex.withLock {
            check(database == null) { "数据库已打开，请先关闭再解锁" }
            val ownedKey = rawKey(key)
            var candidate: T? = null
            try {
                withContext(Dispatchers.IO) {
                    loadSqlCipher()
                    try {
                        candidate = buildDatabase(ownedKey).also { validateDatabase(it) }
                    } catch (_: SQLiteNotADatabaseException) {
                        // 旧版将随机密钥当作口令，首次解锁时转换为直接密钥。
                        migrateKey(key, ownedKey)
                        candidate = buildDatabase(ownedKey).also { validateDatabase(it) }
                    }
                }
                currentCoroutineContext().ensureActive()
                database = checkNotNull(candidate)
                sessionKey = ownedKey
            } catch (failure: Throwable) {
                try {
                    withContext(NonCancellable + Dispatchers.IO) { candidate?.close() }
                } catch (closeFailure: Throwable) {
                    failure.addSuppressed(closeFailure)
                } finally {
                    ownedKey.fill(0)
                }
                throw failure
            }
        }
    }

    /**
     * 构造使用直接密钥的 Room 实例。
     * 使用示例：val opened = buildDatabase(encodedKey)
     * @param key SQLCipher 直接密钥格式的字节数组
     * @return 尚未打开的数据库
     */
    private fun buildDatabase(key: ByteArray): T =
        Room.databaseBuilder(applicationContext, databaseClass, name)
            .openHelperFactory(SqlCipherOpenHelperFactory(key))
            .addMigrations(*migrations)
            .build()

    /**
     * 立即验证惰性连接，失败时关闭实例并保留原始文件。
     * 使用示例：validateDatabase(opened)
     * @param opened 待打开的 Room 数据库
     * @throws android.database.sqlite.SQLiteException 密钥错误或数据库无法读取
     */
    private fun validateDatabase(opened: T) {
        try {
            opened.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use {
                check(it.moveToFirst()) { "数据库校验失败" }
            }
        } catch (failure: Throwable) {
            try {
                opened.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    /**
     * 用旧口令打开现有数据库并原子重加密为直接密钥。
     * 使用示例：migrateKey(legacyKey, encodedKey)
     * @param legacyKey 旧版使用的随机口令
     * @param key SQLCipher 直接密钥格式的字节数组
     * @throws android.database.sqlite.SQLiteException 旧密钥无效或转换失败
     */
    private fun migrateKey(legacyKey: ByteArray, key: ByteArray) {
        SQLiteDatabase.openDatabase(
            applicationContext.getDatabasePath(name).path,
            legacyKey,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            DatabaseErrorHandler { _, error -> throw error },
            null,
        ).use { legacy ->
            legacy.changePassword(key)
            legacy.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray()).use {
                check(it.moveToFirst()) { "数据库密钥转换失败" }
            }
        }
    }

    /**
     * 将随机密钥编码为 SQLCipher 的直接密钥，避免重复派生。
     * 使用示例：val encoded = rawKey(vaultKey)
     * @param key 32 字节随机密钥
     * @return 由会话负责清除的 ASCII 密钥数组
     */
    private fun rawKey(key: ByteArray): ByteArray {
        val digits = "0123456789abcdef"
        return ByteArray(3 + key.size * 2).apply {
            this[0] = 'x'.code.toByte()
            this[1] = '\''.code.toByte()
            key.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xff
                this[2 + index * 2] = digits[value ushr 4].code.toByte()
                this[3 + index * 2] = digits[value and 0xf].code.toByte()
            }
            this[lastIndex] = '\''.code.toByte()
        }
    }

    /**
     * 在 IO 线程执行有限时长的 DAO 操作，关闭会等待操作结束。
     * 使用示例：val entries = store.useDatabase { entryDao().getAll() }
     * 块内直接调用 DAO，不嵌套调用本工具，不向外返回数据库、DAO、Cursor 或惰性 Flow。
     * @param block 数据库读写操作
     * @return 已读取的数据或操作结果
     * @throws IllegalStateException 数据库尚未打开
     */
    suspend fun <R> useDatabase(block: suspend T.() -> R): R = mutex.withLock {
        val opened = requireDatabase()
        withContext(Dispatchers.IO) { opened.block() }
    }

    /**
     * 在事务中执行多个 DAO 操作，异常或取消时回滚。
     * 使用示例：store.transaction { entryDao().insert(entry); tagDao().insert(tag) }
     * 块内直接调用 DAO，不嵌套调用本工具，不向外返回数据库关联对象。
     * @param block 需要原子提交的操作，也可用于一致性备份读取
     * @return 事务结果
     * @throws IllegalStateException 数据库尚未打开
     */
    suspend fun <R> transaction(block: suspend T.() -> R): R = mutex.withLock {
        val opened = requireDatabase()
        withContext(Dispatchers.IO) { opened.withTransaction { opened.block() } }
    }

    /**
     * 等待当前操作结束后关闭连接并清理内部密钥，可重复调用。
     * 使用示例：store.close()
     * @throws RuntimeException 底层连接关闭失败
     */
    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            try {
                database?.close()
            } finally {
                database = null
                sessionKey?.fill(0)
                sessionKey = null
            }
        }
    }

    /**
     * 获取互斥锁保护下的数据库。
     * 使用示例：val opened = requireDatabase()
     * @return 已解锁数据库
     * @throws IllegalStateException 数据库尚未打开
     */
    private fun requireDatabase(): T = checkNotNull(database) { "请先解锁并打开数据库" }

    /**
     * 进程内的 SQLCipher 原生库加载入口。
     * @author Tingdong
     * @date 2026-11-13
     */
    private companion object {
        private val nativeLibrary by lazy { System.loadLibrary("sqlcipher") }

        /**
         * 线程安全地加载 SQLCipher 原生库。
         * 使用示例：loadSqlCipher()
         * @throws UnsatisfiedLinkError 原生库缺失或不支持当前设备
         */
        fun loadSqlCipher() {
            nativeLibrary
        }
    }
}
