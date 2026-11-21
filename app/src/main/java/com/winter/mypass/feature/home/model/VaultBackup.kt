package com.winter.mypass.feature.home.model

import kotlinx.serialization.Serializable

/**
 * 跨设备的完整密码库快照。
 * @author Tingdong
 * @date 2026-11-21
 */
@Serializable
internal data class VaultBackup(
    val createdAt: Long,
    val books: List<VaultBook>,
    val tags: List<VaultTag>,
    val types: List<VaultType>,
    val passwords: List<VaultPassword>,
    val links: List<PasswordTag>,
    val history: List<GeneratedPasswordCopy>,
) {
    /** 确认主键、唯一约束与外键完整后才展示覆盖确认。 */
    fun validate() {
        fun ids(values: List<Long>): Set<Long> = values.toSet().also {
            require(it.size == values.size && it.all { id -> id > 0 && id < Long.MAX_VALUE })
        }
        require(createdAt >= 0 && books.isNotEmpty())
        val bookIds = ids(books.map { it.id })
        val tagIds = ids(tags.map { it.id })
        val typeIds = ids(types.map { it.id })
        val passwordIds = ids(passwords.map { it.id })
        ids(history.map { it.id })
        for (names in listOf(books.map { it.name }, types.map { it.name }, tags.map { it.name })) {
            require(names.all { it.isNotBlank() } && names.distinct().size == names.size)
        }
        val builtIns = types.mapNotNull { it.builtInKey }
        require(builtIns.distinct().size == builtIns.size)
        require(passwords.all { it.bookId in bookIds && (it.typeId == null || it.typeId in typeIds) })
        require(links.distinct().size == links.size && links.all { it.passwordId in passwordIds && it.tagId in tagIds })
    }
}
