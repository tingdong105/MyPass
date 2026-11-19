package com.winter.mypass.feature.home.model

import android.icu.text.IDNA
import java.net.URI
import java.net.URISyntaxException
import java.text.Collator
import java.util.Locale

/**
 * 分类名称及关联的记录数。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class VaultCategory(val id: Long, val name: String, val entryCount: Int)

/**
 * 支持管理的分类。
 * @author Tingdong
 * @date 2026-11-19
 */
internal enum class CategoryKind { Book, Tag, Type }

/**
 * 列表展示和搜索使用的无密码摘要。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class PasswordSummary(
    val id: Long,
    val bookId: Long,
    val platform: String,
    val account: String?,
    val website: String?,
    val typeId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 只在当前解锁会话中使用的编辑草稿。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class PasswordDraft(
    val bookId: Long,
    val id: Long? = null,
    val platform: String = "",
    val password: String = "",
    val account: String = "",
    val website: String = "",
    val notes: String = "",
    val typeId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
) {
    /**
     * 调试输出只包含标识。
     */
    override fun toString(): String = "PasswordDraft(id=$id)"
}

/**
 * 同一次读取获得的分类和当前密码本摘要。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class VaultSnapshot(
    val books: List<VaultCategory>,
    val tags: List<VaultCategory>,
    val types: List<VaultCategory>,
    val bookId: Long,
    val passwords: List<PasswordSummary>,
    val tagsByPassword: Map<Long, List<VaultCategory>>,
) {
    private val typesById = types.associateBy(VaultCategory::id)

    /**
     * 返回指定记录的类型。
     * @param typeId 类型标识
     * @return 对应的类型名称
     */
    fun typeName(typeId: Long?): String? = typesById[typeId]?.name

    /**
     * 读取指定类别用于管理的列表。
     * @param kind 分类类型
     * @return 对应的分类列表
     */
    fun categories(kind: CategoryKind): List<VaultCategory> = when (kind) {
        CategoryKind.Book -> books
        CategoryKind.Tag -> tags
        CategoryKind.Type -> types
    }

    /**
     * 按当前本的关键词和排序偏好生成列表。
     * @param query 搜索词
     * @param sort 排序偏好
     * @param locale 名称排序的当前语言
     * @return 过滤及排序后的摘要
     */
    fun visiblePasswords(query: String, sort: VaultSort, locale: Locale): List<PasswordSummary> {
        val terms = query.trim().split(SearchSeparator).filter(String::isNotEmpty)
        val filtered = if (terms.isEmpty()) passwords else passwords.filter { entry ->
            val fields = listOfNotNull(entry.platform, entry.account, entry.website, typeName(entry.typeId)) +
                tagsByPassword[entry.id].orEmpty().map(VaultCategory::name)
            terms.all { term -> fields.any { field -> field.contains(term, ignoreCase = true) } }
        }
        val comparator = when (sort.order) {
            VaultOrder.Created -> compareBy(PasswordSummary::createdAt)
            VaultOrder.Updated -> compareBy(PasswordSummary::updatedAt)
            VaultOrder.Name -> {
                val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
                Comparator { first, second -> collator.compare(first.platform, second.platform) }
            }
        }.thenBy(PasswordSummary::id)
        return filtered.sortedWith(if (sort.reversed) comparator.reversed() else comparator)
    }

    private companion object {
        val SearchSeparator = Regex("\\s+")
    }
}

/**
 * 密码记录的排序维度。
 * @author Tingdong
 * @date 2026-11-19
 */
internal enum class VaultOrder { Updated, Created, Name }

/**
 * 全局排序方式及独立的逆序偏好。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class VaultSort(val order: VaultOrder = VaultOrder.Updated, val reversed: Boolean = true)

/**
 * 可在界面展示的业务错误。
 * @author Tingdong
 * @date 2026-11-19
 */
internal enum class VaultProblem {
    Storage, Required, InvalidWebsite, DuplicateCategory, DuplicatePassword, Missing, LastBook, BookNotEmpty,
}

/**
 * 将明确的业务错误传给 ViewModel。
 * @author Tingdong
 * @date 2026-11-19
 */
internal class VaultException(val problem: VaultProblem) : IllegalStateException()

private val WebsiteScheme = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://")
private val WebsiteIdna by lazy {
    IDNA.getUTS46Instance(
        IDNA.USE_STD3_RULES or IDNA.CHECK_BIDI or IDNA.CHECK_CONTEXTJ or IDNA.NONTRANSITIONAL_TO_ASCII,
    )
}

/**
 * 为省略协议的网址补充 HTTPS，并将国际化域名转换为浏览器使用的 ASCII 格式。
 * 使用示例：browserWebsite("例子.中国") 返回 "https://xn--fsqu00a.xn--fiqs8s"。
 * @param value 输入的网址
 * @return 规范化网址，空白或无效时返回空
 */
internal fun browserWebsite(value: String): String? {
    val address = value.trim()
    if (address.isEmpty() || address.any(Char::isWhitespace)) return null

    val prefix = WebsiteScheme.matchAt(address, 0)?.value.orEmpty()
    val scheme = prefix.substringBefore(':').lowercase(Locale.ROOT).ifEmpty { "https" }
    if (scheme != "http" && scheme != "https") return null

    val withScheme = "$scheme://${address.substring(prefix.length)}"
    return try {
        val uri = URI(withScheme)
        val authority = uri.rawAuthority ?: return null
        val userInfoEnd = authority.lastIndexOf('@')
        // 避免将 mailto: 等协议解析为 HTTPS 用户凭据。
        if (prefix.isEmpty() && ':' in authority.substringBeforeLast('@', "")) return null

        val browserUri = if (uri.host != null) {
            uri
        } else {
            val hostStart = userInfoEnd + 1
            val host = authority.substring(hostStart).substringBefore(':')
            if (host.all { it.code < 128 }) return null
            val info = IDNA.Info()
            val asciiHost = WebsiteIdna.nameToASCII(host, StringBuilder(), info).toString()
            if (info.hasErrors()) return null
            val offset = scheme.length + 3 + hostStart
            URI(withScheme.replaceRange(offset, offset + host.length, asciiHost))
        }
        browserUri.takeIf { it.host != null && it.port <= 65535 }?.toString()
    } catch (_: URISyntaxException) {
        null
    }
}
