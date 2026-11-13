package com.winter.mypass.core.database

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

/**
 * 保留损坏文件的 SQLCipher 与 Room 适配器。
 * @param key 数据库会话持有的密钥数组
 * @author Tingdong
 * @date 2026-11-13
 */
internal class SqlCipherOpenHelperFactory(private val key: ByteArray) : SupportSQLiteOpenHelper.Factory {

    /**
     * 创建加密连接并转发 Room 生命周期。
     * @param configuration Room 连接配置
     * @return 加密数据库连接助手
     */
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        return object : SQLiteOpenHelper(
            configuration.context,
            configuration.name,
            key,
            null,
            callback.version,
            0,
            DatabaseErrorHandler { _, error -> throw error },
            null,
            false
        ) {
            /**
             * 配置数据库连接。
             * @param db 当前数据库
             */
            override fun onConfigure(db: SQLiteDatabase) = callback.onConfigure(db)

            /**
             * 创建业务表结构。
             * @param db 当前数据库
             */
            override fun onCreate(db: SQLiteDatabase) = callback.onCreate(db)

            /**
             * 执行已注册的升级迁移。
             * @param db 当前数据库
             * @param oldVersion 原版本
             * @param newVersion 目标版本
             */
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                callback.onUpgrade(db, oldVersion, newVersion)

            /**
             * 执行已注册的降级迁移。
             * @param db 当前数据库
             * @param oldVersion 原版本
             * @param newVersion 目标版本
             */
            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                callback.onDowngrade(db, oldVersion, newVersion)

            /**
             * 完成 Room 连接初始化。
             * @param db 当前数据库
             */
            override fun onOpen(db: SQLiteDatabase) = callback.onOpen(db)
        }
    }
}
