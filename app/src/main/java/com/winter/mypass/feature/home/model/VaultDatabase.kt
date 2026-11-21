package com.winter.mypass.feature.home.model

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

/**
 * 密码本及其创建顺序。
 */
@Entity(tableName = "vault_books", indices = [Index(value = ["name"], unique = true)])
internal data class VaultBook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val createdAt: Long,
)

/**
 * 可跨密码本使用的标签。
 */
@Entity(tableName = "vault_tags", indices = [Index(value = ["name"], unique = true)])
internal data class VaultTag(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

/**
 * 可跨密码本使用的类型。
 */
@Entity(tableName = "vault_types", indices = [Index(value = ["name"], unique = true)])
internal data class VaultType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val builtInKey: String? = null,
)

/**
 * 加密库中的完整密码记录。
 */
@Entity(
    tableName = "vault_passwords",
    foreignKeys = [
        ForeignKey(VaultBook::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VaultType::class, ["id"], ["typeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("bookId"), Index("typeId")],
)
internal class VaultPassword(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val platform: String,
    val password: String,
    val typeId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val account: String?,
    val website: String?,
    val notes: String?,
    val email: String?,
)

/**
 * 加密库中的一次生成密码复制记录。
 * @author Tingdong
 * @date 2026-11-20
 */
@Entity(tableName = "vault_generated_password_copies")
internal class GeneratedPasswordCopy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val password: String,
    val copiedAt: Long,
)

/**
 * 密码和标签的多对多关联。
 */
@Entity(
    tableName = "vault_password_tags",
    primaryKeys = ["passwordId", "tagId"],
    foreignKeys = [
        ForeignKey(VaultPassword::class, ["id"], ["passwordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VaultTag::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
internal data class PasswordTag(val passwordId: Long, val tagId: Long)

/**
 * 密码本查询及事务内写入接口。
 */
@Dao
internal interface VaultDao {
    /**
     * 按复制顺序倒序读取生成历史。
     * @return 当前生成历史
     */
    @Query("SELECT * FROM vault_generated_password_copies ORDER BY id DESC")
    suspend fun generatedPasswordCopies(): List<GeneratedPasswordCopy>

    /**
     * 写入一条已成功复制的生成结果。
     * @param entry 本次复制记录
     */
    @Insert
    suspend fun insertGeneratedPasswordCopy(entry: GeneratedPasswordCopy)

    /**
     * 删除一批生成历史。
     * @param ids 待删除的记录标识
     */
    @Query("DELETE FROM vault_generated_password_copies WHERE id IN (:ids)")
    suspend fun deleteGeneratedPasswordCopies(ids: List<Long>)

    /**
     * 读取密码本和各本条目数。
     */
    @Query("""
        SELECT b.id, b.name, COUNT(p.id) AS entryCount, NULL AS builtInKey FROM vault_books b
        LEFT JOIN vault_passwords p ON p.bookId = b.id GROUP BY b.id ORDER BY b.id
    """)
    suspend fun books(): List<VaultCategory>

    /**
     * 读取标签和关联条目数。
     */
    @Query("""
        SELECT t.id, t.name, COUNT(p.passwordId) AS entryCount, NULL AS builtInKey FROM vault_tags t
        LEFT JOIN vault_password_tags p ON p.tagId = t.id GROUP BY t.id ORDER BY t.id
    """)
    suspend fun tags(): List<VaultCategory>

    /**
     * 读取类型和关联条目数，内置类型排在最前。
     */
    @Query("""
        SELECT t.id, t.name, COUNT(p.id) AS entryCount, t.builtInKey FROM vault_types t
        LEFT JOIN vault_passwords p ON p.typeId = t.id GROUP BY t.id
        ORDER BY t.builtInKey IS NULL, t.id
    """)
    suspend fun types(): List<VaultCategory>

    /**
     * 读取可搜索的摘要，以及预览所需的密码或字符数。
     * @param bookId 当前密码本
     * @param includePassword 是否随摘要读取原密码
     * @param includePasswordLength 是否读取全隐藏所需的字符数
     * @return 该密码本的记录摘要
     */
    @Query("""
        SELECT id, bookId, platform, account, website, typeId, createdAt, updatedAt,
               CASE WHEN :includePassword OR (:includePasswordLength AND instr(password, char(0)) > 0)
                    THEN password END AS passwordPreview,
               CASE WHEN :includePasswordLength AND instr(password, char(0)) = 0
                    THEN length(password) END AS passwordLength
        FROM vault_passwords WHERE bookId = :bookId
    """)
    suspend fun summaries(
        bookId: Long,
        includePassword: Boolean,
        includePasswordLength: Boolean = false,
    ): List<PasswordSummary>

    /**
     * 读取指定密码本的记录与标签关联。
     */
    @Query("""
        SELECT t.passwordId, t.tagId FROM vault_password_tags t
        JOIN vault_passwords p ON t.passwordId = p.id WHERE p.bookId = :bookId
    """)
    suspend fun passwordTags(bookId: Long): List<PasswordTag>

    /**
     * 读取单条完整记录。
     */
    @Query("SELECT * FROM vault_passwords WHERE id = :id")
    suspend fun entry(id: Long): VaultPassword?

    /**
     * 读取单条记录的标签标识。
     */
    @Query("SELECT tagId FROM vault_password_tags WHERE passwordId = :id ORDER BY tagId")
    suspend fun tagIds(id: Long): List<Long>

    /**
     * 插入密码本。
     */
    @Insert suspend fun insertBook(book: VaultBook): Long

    /**
     * 插入标签。
     */
    @Insert suspend fun insertTag(tag: VaultTag): Long

    /**
     * 插入类型。
     */
    @Insert suspend fun insertType(type: VaultType): Long

    /**
     * 新增密码。
     */
    @Insert suspend fun insertPassword(password: VaultPassword): Long

    /**
     * 更新密码及所属密码本。
     */
    @Update suspend fun updatePassword(password: VaultPassword)

    /**
     * 保存密码与标签的关联。
     */
    @Insert suspend fun insertPasswordTags(links: List<PasswordTag>)

    /**
     * 移除指定记录的标签关联。
     */
    @Query("DELETE FROM vault_password_tags WHERE passwordId = :id")
    suspend fun clearPasswordTags(id: Long)

    /**
     * 重命名密码本。
     */
    @Query("UPDATE vault_books SET name = :name WHERE id = :id")
    suspend fun renameBook(id: Long, name: String)

    /**
     * 重命名标签。
     */
    @Query("UPDATE vault_tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    /**
     * 重命名类型。
     */
    @Query("UPDATE vault_types SET name = :name WHERE id = :id")
    suspend fun renameType(id: Long, name: String)

    /**
     * 将旧版默认类型沿用为内置类型。
     */
    @Query("UPDATE vault_types SET name = :name, builtInKey = :key WHERE id = :id")
    suspend fun setTypeBuiltIn(id: Long, key: String, name: String)

    /**
     * 删除已通过空本检查的密码本。
     */
    @Query("DELETE FROM vault_books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    /**
     * 删除标签及关联。
     */
    @Query("DELETE FROM vault_tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    /**
     * 删除类型并清空相关记录的类型字段。
     */
    @Query("DELETE FROM vault_types WHERE id = :id")
    suspend fun deleteType(id: Long)

    /**
     * 删除密码及标签关联。
     */
    @Query("DELETE FROM vault_passwords WHERE id = :id")
    suspend fun deletePassword(id: Long)
}

/**
 * 密码本 Room 数据库。
 * @author Tingdong
 * @date 2026-11-19
 */
@Database(
    entities = [
        VaultBook::class, VaultTag::class, VaultType::class, VaultPassword::class,
        PasswordTag::class, GeneratedPasswordCopy::class,
    ],
    version = 5,
)
internal abstract class VaultDatabase : RoomDatabase() {
    /**
     * 返回密码本 DAO。
     */
    abstract fun vaultDao(): VaultDao
}
