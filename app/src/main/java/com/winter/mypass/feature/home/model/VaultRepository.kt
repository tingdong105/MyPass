package com.winter.mypass.feature.home.model

import android.content.Context
import androidx.annotation.StringRes
import com.winter.mypass.R
import com.winter.mypass.core.utils.RoomUtils

/**
 * 密码本会话以及记录和分类的事务操作。
 * @author Tingdong
 * @date 2026-11-19
 */
internal class VaultRepository(context: Context, private val defaultBookName: String) {
    private val context = context.applicationContext

    private val storage = RoomUtils(
        context, VaultDatabase::class.java, DatabaseName,
        VaultMigration1To2, VaultMigration2To3, VaultMigration3To4, VaultMigration4To5, VaultMigration5To6,
        VaultMigration6To7,
    )

    /**
     * 预先加载数据库引擎，供输入密码期间并行准备。
     * @throws UnsatisfiedLinkError 数据库原生库不可用
     */
    suspend fun prepare() = storage.prepare()

    /**
     * 验证密钥，首次使用时初始化密码本，并确保内置类型存在。
     * @param key 从应用锁解出的数据库密钥
     */
    suspend fun open(key: ByteArray) {
        storage.open(key)
        try {
            storage.transaction {
                val dao = vaultDao()
                if (dao.books().isEmpty()) {
                    dao.insertBook(VaultBook(name = defaultBookName, createdAt = System.currentTimeMillis()))
                }
                dao.ensureBuiltInTypes()
                dao.purgeExpiredTrash()
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
     * 确保三个内置类型存在，并把旧版默认类型沿用为内置类型。
     * @receiver 当前事务中的 DAO
     */
    private suspend fun VaultDao.ensureBuiltInTypes() {
        val existing = types().toMutableList()
        for (builtIn in BuiltInTypes) {
            if (existing.any { it.builtInKey == builtIn.key }) continue
            val name = context.getString(builtIn.name)
            val sameName = existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (sameName != null) {
                // 用户可能早已创建同名类型，直接复用，避免触发名称唯一约束。
                if (sameName.builtInKey == null) {
                    setTypeBuiltIn(sameName.id, builtIn.key, name)
                    existing[existing.indexOf(sameName)] = sameName.copy(name = name, builtInKey = builtIn.key)
                }
                continue
            }
            val legacy = builtIn.legacy?.let { old ->
                existing.firstOrNull { it.builtInKey == null && it.name.equals(old, ignoreCase = true) }
            }
            if (legacy != null) {
                setTypeBuiltIn(legacy.id, builtIn.key, name)
                existing[existing.indexOf(legacy)] = legacy.copy(name = name, builtInKey = builtIn.key)
            } else {
                val id = insertType(VaultType(name = name, builtInKey = builtIn.key))
                existing += VaultCategory(id, name, 0, builtIn.key)
            }
        }
    }

    /**
     * 关闭数据库并清理内部密钥。
     */
    suspend fun close() = storage.close()

    /**
     * 读取已复制的生成密码。
     * @return 按最近复制排序的历史记录
     */
    suspend fun generatedPasswordCopies(): List<GeneratedPasswordCopy> =
        storage.useDatabase { vaultDao().generatedPasswordCopies() }

    /**
     * 保存一次成功复制生成密码的记录。
     * @param password 已写入剪贴板的生成密码
     */
    suspend fun recordGeneratedPasswordCopy(password: String) = storage.useDatabase {
        vaultDao().insertGeneratedPasswordCopy(GeneratedPasswordCopy(password = password, copiedAt = System.currentTimeMillis()))
    }

    /**
     * 在同一事务中删除选中的历史记录并读取最新列表。
     * @param ids 选中的历史记录标识
     * @return 剩余的历史记录
     */
    suspend fun deleteGeneratedPasswordCopies(ids: Set<Long>): List<GeneratedPasswordCopy> = storage.transaction {
        val dao = vaultDao()
        for (batch in ids.chunked(500)) dao.deleteGeneratedPasswordCopies(batch)
        dao.generatedPasswordCopies()
    }

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
     * @param trashed 是否读取回收站中的记录
     * @return 当前记录及所属分类
     */
    suspend fun detail(id: Long, trashed: Boolean = false): PasswordDraft = storage.transaction {
        val dao = vaultDao()
        val entry = dao.entry(id)?.takeIf { (it.deletedAt != null) == trashed }
            ?: throw VaultException(VaultProblem.Missing)
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
            if (platform.isEmpty() || draft.password.isEmpty() || draft.typeId == null) {
                throw VaultException(VaultProblem.Required)
            }
            val website = draft.website.takeIf(String::isNotBlank)?.let {
                browserWebsite(it) ?: throw VaultException(VaultProblem.InvalidWebsite)
            }
            val account = draft.account.trim().ifEmpty { null }
            val existing = draft.id?.let { dao.entry(it) ?: throw VaultException(VaultProblem.Missing) }
            val identity = PasswordIdentity(draft.bookId, platform, account)
            val previous = existing?.let { PasswordIdentity(it.bookId, it.platform, it.account) }
            if (!allowDuplicate && previous?.compareTo(identity) != 0 &&
                dao.passwordIdentities(draft.bookId, draft.id).any { it.compareTo(identity) == 0 }
            ) {
                throw VaultException(VaultProblem.DuplicatePassword)
            }

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
                deletedAt = existing?.deletedAt,
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
     * 将指定记录移入回收站并读取更新后的快照。
     * @param id 记录标识
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 当前列表快照
     */
    suspend fun deletePassword(id: Long, bookId: Long, preview: PasswordPreviewSettings): VaultSnapshot = storage.transaction {
        val dao = vaultDao()
        dao.deletePassword(id, System.currentTimeMillis())
        dao.snapshot(bookId, preview)
    }

    /**
     * 在同一事务中批量将当前本内的密码移入回收站并刷新计数。
     * @param ids 待删除的记录标识
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 删除后的列表快照
     */
    suspend fun deletePasswords(ids: Set<Long>, bookId: Long, preview: PasswordPreviewSettings): VaultSnapshot =
        storage.transaction {
            val dao = vaultDao()
            val deletedAt = System.currentTimeMillis()
            for (batch in ids.chunked(500)) dao.deletePasswords(bookId, batch, deletedAt)
            dao.snapshot(bookId, preview)
        }

    /**
     * 检查目标密码本与重复项，在同一事务中移动全部所选密码。
     * @param ids 待移动的记录标识
     * @param bookId 来源密码本
     * @param targetBookId 目标密码本
     * @param preview 列表密码预览设置
     * @param allowDuplicate 是否已确认保留重复记录
     * @return 来源密码本的最新列表快照
     * @throws VaultException 密码本或记录失效，或移动会产生未经确认的重复记录
     */
    suspend fun movePasswords(
        ids: Set<Long>,
        bookId: Long,
        targetBookId: Long,
        preview: PasswordPreviewSettings,
        allowDuplicate: Boolean,
    ): VaultSnapshot = storage.transaction {
        val dao = vaultDao()
        val books = dao.books()
        if (ids.isEmpty() || bookId == targetBookId ||
            books.none { it.id == bookId } || books.none { it.id == targetBookId }
        ) throw VaultException(VaultProblem.Missing)
        val batches = ids.chunked(500)
        val moving = batches.flatMap { dao.movingIdentities(bookId, it) }
        if (moving.size != ids.size) throw VaultException(VaultProblem.Missing)
        if (!allowDuplicate) {
            val identities = dao.passwordIdentities(targetBookId).toCollection(sortedSetOf())
            if (moving.any { !identities.add(PasswordIdentity(targetBookId, it.platform, it.account)) }) {
                throw VaultException(VaultProblem.DuplicatePassword)
            }
        }
        val updatedAt = System.currentTimeMillis()
        for (batch in batches) {
            if (dao.movePasswords(bookId, batch, targetBookId, updatedAt) != batch.size) {
                throw VaultException(VaultProblem.Missing)
            }
        }
        dao.snapshot(bookId, preview)
    }

    /**
     * 读取全部密码本的回收站记录。
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 回收站内容与最新列表快照
     */
    suspend fun trash(bookId: Long, preview: PasswordPreviewSettings): VaultTrash = storage.transaction {
        val dao = vaultDao()
        dao.purgeExpiredTrash()
        VaultTrash(dao.snapshot(bookId, preview), dao.trashed())
    }

    /**
     * 在同一事务中恢复选中的回收站记录。
     * @param ids 待恢复的记录标识
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @param allowDuplicate 用户已确认保留重复条目
     * @return 回收站内容与最新列表快照
     * @throws VaultException 恢复会产生未经确认的重复记录
     */
    suspend fun restorePasswords(
        ids: Set<Long>,
        bookId: Long,
        preview: PasswordPreviewSettings,
        allowDuplicate: Boolean,
    ): VaultTrash = storage.transaction {
        val dao = vaultDao()
        dao.purgeExpiredTrash()
        val batches = ids.chunked(500)
        if (!allowDuplicate) {
            val byBook = batches.flatMap { dao.trashedIdentities(it) }.groupBy(PasswordIdentity::bookId)
            for ((sourceBook, restoring) in byBook) {
                val identities = dao.passwordIdentities(sourceBook).toCollection(sortedSetOf())
                if (restoring.any { !identities.add(it) }) throw VaultException(VaultProblem.DuplicatePassword)
            }
        }
        for (batch in batches) dao.restorePasswords(batch)
        VaultTrash(dao.snapshot(bookId, preview), dao.trashed())
    }

    /**
     * 在当前事务内清理超过保留期的记录及其标签关联。
     * @receiver 当前事务中的 DAO
     */
    private suspend fun VaultDao.purgeExpiredTrash() {
        purgeTrash(System.currentTimeMillis() - TrashRetentionMillis)
    }

    /**
     * 在同一事务中永久删除选中的回收站记录。
     * @param ids 待删除的记录标识
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 回收站内容与最新列表快照
     */
    suspend fun purgePasswords(
        ids: Set<Long>,
        bookId: Long,
        preview: PasswordPreviewSettings,
    ): VaultTrash = storage.transaction {
        val dao = vaultDao()
        for (batch in ids.chunked(500)) dao.purgePasswords(batch)
        VaultTrash(dao.snapshot(bookId, preview), dao.trashed())
    }

    /**
     * 清空回收站并刷新列表快照。
     * @param bookId 当前密码本
     * @param preview 列表密码预览设置
     * @return 回收站内容与最新列表快照
     */
    suspend fun clearTrash(bookId: Long, preview: PasswordPreviewSettings): VaultTrash = storage.transaction {
        val dao = vaultDao()
        dao.clearTrash()
        VaultTrash(dao.snapshot(bookId, preview), dao.trashed())
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
            val categories = dao.categories(kind)
            if (categories.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
                throw VaultException(VaultProblem.DuplicateCategory)
            }
            if (kind == CategoryKind.Type && id != null && categories.firstOrNull { it.id == id }?.builtIn == true) {
                throw VaultException(VaultProblem.BuiltIn)
            }
            val nextBook = when (kind) {
                CategoryKind.Book -> if (id == null) {
                    dao.insertBook(VaultBook(
                        name = name, createdAt = System.currentTimeMillis(), sortOrder = dao.nextBookOrder(),
                    ))
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
     * 在同一事务内保存完整的密码本顺序。
     * @param ids 按显示位置排列的全部密码本标识
     * @param bookId 当前选中的密码本
     * @param preview 列表密码预览设置
     * @return 保留当前选择的最新快照
     * @throws VaultException 标识重复或密码本已变动
     */
    suspend fun reorderBooks(ids: List<Long>, bookId: Long, preview: PasswordPreviewSettings): VaultSnapshot =
        storage.transaction {
            val dao = vaultDao()
            val books = dao.books()
            if (ids.size != books.size || ids.toSet() != books.map { it.id }.toSet()) {
                throw VaultException(VaultProblem.Missing)
            }
            ids.forEachIndexed { index, id -> dao.updateBookOrder(id, index.toLong()) }
            dao.snapshot(bookId, preview)
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
                if (dao.trashedCount(id) > 0) throw VaultException(VaultProblem.BookHasTrash)
                if (books.size == 1) throw VaultException(VaultProblem.LastBook)
                dao.deleteBook(id)
            }
            CategoryKind.Tag -> dao.deleteTag(id)
            CategoryKind.Type -> {
                val type = dao.types().firstOrNull { it.id == id } ?: throw VaultException(VaultProblem.Missing)
                if (type.builtIn) throw VaultException(VaultProblem.BuiltIn)
                dao.deleteType(id)
            }
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

    /** 在单个读事务中获取完整密码库。 */
    suspend fun backup(): VaultBackup = storage.transaction {
        with(vaultDao()) {
            VaultBackup(System.currentTimeMillis(), backupBooks(), backupTags(), backupTypes(),
                backupPasswords(), backupLinks(), generatedPasswordCopies())
        }
    }

    /** 在同一事务中替换所有数据，失败时由 Room 回滚。 */
    suspend fun restore(data: VaultBackup) = storage.transaction {
        with(vaultDao()) {
            clearBooks()
            clearTags()
            clearTypes()
            clearGeneratedCopies()
            data.books.forEach { insertBook(it) }
            data.types.forEach { insertType(it) }
            data.tags.forEach { insertTag(it) }
            data.passwords.forEach { insertPassword(it) }
            insertPasswordTags(data.links)
            purgeExpiredTrash()
            data.history.forEach { insertGeneratedPasswordCopy(it) }
            ensureBuiltInTypes()
        }
    }

    companion object {
        const val DatabaseName = "vault.db"

        /** 回收站保留时长：30 天。 */
        private const val TrashRetentionMillis = 30L * 24 * 60 * 60 * 1000

        private val BuiltInTypes = listOf(
            BuiltInType("software", R.string.vault_builtin_software, legacy = "APP"),
            BuiltInType("website", R.string.vault_builtin_website, legacy = "Web"),
            BuiltInType("miniapp", R.string.vault_builtin_miniapp),
        )
    }
}

/**
 * 内置类型的稳定标识、显示名称和需要沿用的旧版名称。
 * @param key 跨语言保持不变的内置标识
 * @param name 显示名称资源
 * @param legacy 旧版本自动创建的默认类型名称
 */
private data class BuiltInType(
    val key: String,
    @StringRes val name: Int,
    val legacy: String? = null,
)
