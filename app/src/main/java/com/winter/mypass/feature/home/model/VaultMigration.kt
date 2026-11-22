package com.winter.mypass.feature.home.model

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 迁移两个曾使用相同版本号的密码本结构。 */
internal val VaultMigration1To2 = object : Migration(1, 2) {
    /**
     * 按旧表名识别来源，保留记录及分类标识和已有时间戳。
     * @param db 当前加密数据库
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        val layout = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
            buildSet { while (rows.moveToNext()) add(rows.getString(0)) }
        }
        when {
            "vault_passwords" in layout && "entries" !in layout -> {
                db.execSQL("ALTER TABLE vault_books ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_passwords ADD COLUMN account TEXT")
                db.execSQL("ALTER TABLE vault_passwords ADD COLUMN website TEXT")
                db.execSQL("ALTER TABLE vault_passwords ADD COLUMN notes TEXT")
            }
            "entries" in layout && "vault_passwords" !in layout -> db.copyAlternateVault()
            else -> error("未识别的密码本 v1 结构")
        }
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "密码本关联检查失败" } }
    }
}

/** 为现有密码记录添加可选邮箱字段。 */
internal val VaultMigration2To3 = object : Migration(2, 3) {
    /**
     * 新增可空的邮箱列，保留原有记录和关联。
     * @param db 当前加密数据库
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_passwords ADD COLUMN email TEXT")
    }
}

/** 在加密库中新增生成密码的复制历史。 */
internal val VaultMigration3To4 = object : Migration(3, 4) {
    /**
     * 创建独立历史表，保留原有密码本的数据。
     * @param db 当前加密数据库
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE vault_generated_password_copies (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                password TEXT NOT NULL,
                copiedAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

/** 为类型增加内置标识，用于保护内置类型。 */
internal val VaultMigration4To5 = object : Migration(4, 5) {
    /**
     * 新增可空的内置标识列，已有类型默认仍然可编辑。
     * @param db 当前加密数据库
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_types ADD COLUMN builtInKey TEXT")
    }
}

/** 为密码记录增加回收站时间，未删除时为空。 */
internal val VaultMigration5To6 = object : Migration(5, 6) {
    /**
     * 新增可空的删除时间列，已有记录保持正常可见。
     * @param db 当前加密数据库
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_passwords ADD COLUMN deletedAt INTEGER")
    }
}

/** 为密码本增加可持久化的显示顺序。 */
internal val VaultMigration6To7 = object : Migration(6, 7) {
    /**
     * 新增顺序列，按旧版主键顺序初始化。
     * @param db 当前加密数据库
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_books ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE vault_books SET sortOrder = id")
    }
}

/**
 * 将旧版 entries 结构复制到 v2 表，先移除旧关联表再移除其父表。
 * @receiver 迁移事务中的数据库
 */
private fun SupportSQLiteDatabase.copyAlternateVault() {
    execSQL("CREATE TABLE vault_books (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL DEFAULT 0)")
    execSQL("CREATE UNIQUE INDEX index_vault_books_name ON vault_books (name)")
    execSQL("CREATE TABLE vault_tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
    execSQL("CREATE UNIQUE INDEX index_vault_tags_name ON vault_tags (name)")
    execSQL("CREATE TABLE vault_types (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
    execSQL("CREATE UNIQUE INDEX index_vault_types_name ON vault_types (name)")
    execSQL("""
        CREATE TABLE vault_passwords (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId INTEGER NOT NULL, platform TEXT NOT NULL,
            password TEXT NOT NULL, typeId INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            account TEXT, website TEXT, notes TEXT,
            FOREIGN KEY(bookId) REFERENCES vault_books(id) ON DELETE CASCADE,
            FOREIGN KEY(typeId) REFERENCES vault_types(id) ON DELETE SET NULL
        )
    """.trimIndent())
    execSQL("CREATE INDEX index_vault_passwords_bookId ON vault_passwords (bookId)")
    execSQL("CREATE INDEX index_vault_passwords_typeId ON vault_passwords (typeId)")
    execSQL("""
        CREATE TABLE vault_password_tags (
            passwordId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(passwordId, tagId),
            FOREIGN KEY(passwordId) REFERENCES vault_passwords(id) ON DELETE CASCADE,
            FOREIGN KEY(tagId) REFERENCES vault_tags(id) ON DELETE CASCADE
        )
    """.trimIndent())
    execSQL("CREATE INDEX index_vault_password_tags_tagId ON vault_password_tags (tagId)")

    execSQL("INSERT INTO vault_books (id, name, createdAt) SELECT id, name, createdAt FROM vaults")
    execSQL("INSERT INTO vault_tags (id, name) SELECT id, name FROM tags")
    execSQL("INSERT INTO vault_types (id, name) SELECT id, name FROM types")
    execSQL("""
        INSERT INTO vault_passwords (id, bookId, platform, password, typeId, createdAt, updatedAt)
        SELECT id, vaultId, platform, password, typeId, createdAt, updatedAt FROM entries
    """.trimIndent())
    execSQL("INSERT INTO vault_password_tags (passwordId, tagId) SELECT entryId, tagId FROM entry_tags")
    for ((old, current) in listOf("vaults" to "vault_books", "tags" to "vault_tags", "types" to "vault_types", "entries" to "vault_passwords")) {
        execSQL("""
            INSERT INTO sqlite_sequence (name, seq)
            SELECT '$current', seq FROM sqlite_sequence WHERE name = '$old'
            AND NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = '$current')
        """.trimIndent())
        execSQL("""
            UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM sqlite_sequence WHERE name = '$old'), 0))
            WHERE name = '$current'
        """.trimIndent())
    }
    for (table in listOf("entry_tags", "entries", "tags", "types", "vaults")) execSQL("DROP TABLE $table")
}
