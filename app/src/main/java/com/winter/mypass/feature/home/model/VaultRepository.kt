package com.winter.mypass.feature.home.model

import android.content.Context
import com.winter.mypass.core.utils.RoomUtils

/**
 * 密码本会话以及记录和分类的事务操作。
 * @author Tingdong
 * @date 2026-11-19
 */
internal class VaultRepository(context: Context, private val defaultBookName: String) {
    private val storage = RoomUtils(context, VaultDatabase::class.java, DatabaseName, VaultMigration1To2, VaultMigration2To3)

    /**
     * 验证密钥，首次使用时初始化密码本和类型。
     * @param key 从应用锁解出的数据库密钥
     */
    suspend fun open(key: ByteArray) {
        storage.open(key)
        try {
            storage.transaction {
                val dao = vaultDao()
                if (dao.books().isEmpty()) {
                    dao.insertBook(VaultBook(name = defaultBookName, createdAt = System.currentTimeMillis()))
                    val existingTypes = dao.types()
                    for (name in listOf("APP", "Web")) {
                        if (existingTypes.none { it.name.equals(name, ignoreCase = true) }) {
                            dao.insertType(VaultType(name = name))
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            try {
                storage.close()
            } catch (closeFailure: Exception) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    /**
     * 关闭数据库并清理内部密钥。
     */
    suspend fun close() = storage.close()

    /**
     * 读取指定密码本的摘要，密码本已删除时选最早创建的本。
     * @param bookId 用户选择的密码本
     * @param preview 列表密码预览设置
     * @return 同一事务中的快照
     */
    suspend fun snapshot(bookId: Long?, preview: PasswordPreviewSettings): VaultSnapshot =
        storage.transaction { vaultDao().snapshot(bookId, preview) }

    /**
     * 在打开详情时读取一条完整记录。
     * @param id 记录标识
     * @return 当前记录及所属分类
     */
    suspend fun detail(id: Long): PasswordDraft = storage.transaction {
        val dao = vaultDao()
        val entry = dao.entry(id) ?: throw VaultException(VaultProblem.Missing)
        PasswordDraft(
            bookId = entry.bookId,
            id = entry.id,
            platform = entry.platform,
            password = entry.password,
            account = entry.account.orEmpty(),
            website = entry.website.orEmpty(),
            email = entry.email.orEmpty(),
            notes = entry.notes.orEmpty(),
            typeId = entry.typeId,
            tagIds = dao.tagIds(id).toSet(),
        )
    }

    /**
     * 检查重复并保存记录及标签关联。
     * @param draft 待保存草稿
     * @param currentBook 当前列表所属的密码本
     * @param allowDuplicate 用户已确认保留重复条目
     * @param preview 列表密码预览设置
     * @return 提交后的列表快照
     */
    suspend fun save(
        draft: PasswordDraft,
        currentBook: Long,
        allowDuplicate: Boolean,
        preview: PasswordPreviewSettings,
    ): VaultSnapshot =
        storage.transaction {
            val dao = vaultDao()
            val platform = draft.platform.trim()
            if (platform.isEmpty() || draft.password.isEmpty()) throw VaultException(VaultProblem.Required)
            val website = draft.website.takeIf(String::isNotBlank)?.let {
                browserWebsite(it) ?: throw VaultException(VaultProblem.InvalidWebsite)
            }
            val account = draft.account.trim().ifEmpty { null }
            val existing = draft.id?.let { dao.entry(it) ?: throw VaultException(VaultProblem.Missing) }
            val identityChanged = existing == null || existing.bookId != draft.bookId ||
                !existing.platform.equals(platform, ignoreCase = true) ||
                !existing.account.orEmpty().equals(account.orEmpty(), ignoreCase = true)
            if (!allowDuplicate && identityChanged && dao.summaries(draft.bookId, includePassword = false).any {
                    it.id != draft.id && it.platform.equals(platform, ignoreCase = true) &&
                        it.account.orEmpty().equals(account.orEmpty(), ignoreCase = true)
                }
            ) throw VaultException(VaultProblem.DuplicatePassword)

            val now = System.currentTimeMillis()
            val value = VaultPassword(
                id = draft.id ?: 0,
                bookId = draft.bookId,
                platform = platform,
                password = draft.password,
                account = account,
                website = website,
                email = draft.email.trim().ifEmpty { null },
                notes = draft.notes.ifEmpty { null },
                typeId = draft.typeId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            val id = if (existing == null) dao.insertPassword(value) else {
                dao.updatePassword(value)
                dao.clearPasswordTags(value.id)
                value.id
            }
            if (draft.tagIds.isNotEmpty()) dao.insertPasswordTags(draft.tagIds.map { PasswordTag(id, it) })
            dao.snapshot(currentBook, preview)
        }

    /**
     * 删除指定记录并读取更新后的快照。
     * @param id 记录标识
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 当前列表快照
     */
    suspend fun deletePassword(id: Long, bookId: Long, preview: PasswordPreviewSettings): VaultSnapshot = storage.transaction {
        val dao = vaultDao()
        dao.deletePassword(id)
        dao.snapshot(bookId, preview)
    }

    /**
     * 新建或重命名分类，同类名称按忽略大小写校验。
     * @param kind 分类种类
     * @param id 已有分类标识，新建时为空
     * @param rawName 输入的名称
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 更新后的列表，新建密码本时选中新本
     */
    suspend fun saveCategory(
        kind: CategoryKind,
        id: Long?,
        rawName: String,
        bookId: Long,
        preview: PasswordPreviewSettings,
    ): VaultSnapshot =
        storage.transaction {
            val dao = vaultDao()
            val name = rawName.trim()
            if (name.isEmpty()) throw VaultException(VaultProblem.Required)
            if (dao.categories(kind).any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
                throw VaultException(VaultProblem.DuplicateCategory)
            }
            val nextBook = when (kind) {
                CategoryKind.Book -> if (id == null) {
                    dao.insertBook(VaultBook(name = name, createdAt = System.currentTimeMillis()))
                } else {
                    dao.renameBook(id, name)
                    bookId
                }
                CategoryKind.Tag -> {
                    if (id == null) dao.insertTag(VaultTag(name = name)) else dao.renameTag(id, name)
                    bookId
                }
                CategoryKind.Type -> {
                    if (id == null) dao.insertType(VaultType(name = name)) else dao.renameType(id, name)
                    bookId
                }
            }
            dao.snapshot(nextBook, preview)
        }

    /**
     * 删除分类，在同一事务内限制密码本的删除条件。
     * @param kind 分类种类
     * @param id 分类标识
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 更新后的快照
     */
    suspend fun deleteCategory(
        kind: CategoryKind,
        id: Long,
        bookId: Long,
        preview: PasswordPreviewSettings,
    ): VaultSnapshot = storage.transaction {
        val dao = vaultDao()
        when (kind) {
            CategoryKind.Book -> {
                val books = dao.books()
                val book = books.firstOrNull { it.id == id } ?: throw VaultException(VaultProblem.Missing)
                if (book.entryCount > 0) throw VaultException(VaultProblem.BookNotEmpty)
                if (books.size == 1) throw VaultException(VaultProblem.LastBook)
                dao.deleteBook(id)
            }
            CategoryKind.Tag -> dao.deleteTag(id)
            CategoryKind.Type -> dao.deleteType(id)
        }
        dao.snapshot(bookId, preview)
    }

    /**
     * 读取分类和当前本的摘要及标签。
     * @receiver 当前事务中的 DAO
     * @param bookId 用户选择的密码本
     * @param preview 列表密码预览设置
     * @return 当前列表快照
     */
    private suspend fun VaultDao.snapshot(bookId: Long?, preview: PasswordPreviewSettings): VaultSnapshot {
        val books = books()
        val tags = tags()
        val selected = books.firstOrNull { it.id == bookId } ?: books.first()
        val names = tags.associateBy(VaultCategory::id)
        val links = passwordTags(selected.id).groupBy(PasswordTag::passwordId) { names.getValue(it.tagId) }
        val hidden = preview.visibility == PasswordVisibility.Hidden
        val entries = summaries(selected.id, includePassword = !hidden, includePasswordLength = hidden)
        val previews = if (preview.visibility == PasswordVisibility.Full) entries else entries.map { entry ->
            entry.copy(
                passwordPreview = preview.display(entry.passwordPreview.orEmpty(), entry.passwordLength),
                passwordLength = null,
            )
        }
        return VaultSnapshot(books, tags, types(), selected.id, previews, links)
    }

    /**
     * 读取指定分类以校验重名。
     * @receiver 当前事务中的 DAO
     * @param kind 分类种类
     * @return 已存在的分类
     */
    private suspend fun VaultDao.categories(kind: CategoryKind): List<VaultCategory> = when (kind) {
        CategoryKind.Book -> books()
        CategoryKind.Tag -> tags()
        CategoryKind.Type -> types()
    }

    companion object {
        const val DatabaseName = "vault.db"
    }
}
