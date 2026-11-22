package com.winter.mypass.feature.home.model

import android.icu.text.IDNA
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.text.Collator
import java.util.Locale

/**
 * 分类名称及关联的记录数。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class VaultCategory(
    val id: Long,
    val name: String,
    val entryCount: Int,
    val builtInKey: String? = null,
) {
    /** 内置分类不允许重命名或删除。 */
    val builtIn: Boolean get() = builtInKey != null
}

/**
 * 支持管理的分类。
 * @author Tingdong
 * @date 2026-11-19
 */
internal enum class CategoryKind { Book, Tag, Type }

/**
 * 保存悬浮导航及其液态玻璃样式偏好。
 * @author Tingdong
 * @date 2026-11-22
 */
@Serializable
internal data class VaultNavigationSettings(
    val floating: Boolean = false,
    val liquidGlass: Boolean = false,
)

/**
 * 列表展示和搜索使用的记录摘要。
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
    val passwordPreview: String? = null,
    val passwordLength: Int? = null,
) {
    /**
     * 调试输出只包含记录标识。
     * @return 不含密码和账户信息的摘要
     */
    override fun toString(): String = "PasswordSummary(id=$id)"
}

/**
 * 用于比较同一密码本内的记录身份。
 * @author Tingdong
 * @date 2026-11-22
 */
internal class PasswordIdentity(
    val bookId: Long,
    val platform: String,
    val account: String?,
) : Comparable<PasswordIdentity> {
    /**
     * 按密码本、平台和账号比较，平台和账号忽略大小写。
     * @param other 要比较的记录身份
     * @return 顺序值，相同身份返回零
     */
    override fun compareTo(other: PasswordIdentity): Int {
        val byBook = bookId.compareTo(other.bookId)
        if (byBook != 0) return byBook
        val byPlatform = platform.compareTo(other.platform, ignoreCase = true)
        return if (byPlatform != 0) byPlatform else account.orEmpty().compareTo(other.account.orEmpty(), ignoreCase = true)
    }
}

/**
 * 回收站中的一条密码摘要。
 * @author Tingdong
 * @date 2026-11-22
 */
internal data class TrashEntry(
    val id: Long,
    val platform: String,
    val typeId: Long?,
    val deletedAt: Long,
) {
    /**
     * 调试输出只包含记录标识。
     * @return 不含密码和账户信息的摘要
     */
    override fun toString(): String = "TrashEntry(id=$id)"
}

/**
 * 首页卡片的密码显示方式。
 * @author Tingdong
 * @date 2026-11-20
 */
internal enum class PasswordVisibility(val storageValue: String) {
    Full("full"),
    Partial("partial"),
    Hidden("hidden"),
}

/**
 * 首页卡片的密码显示和遮罩符号偏好。
 * @author Tingdong
 * @date 2026-11-20
 */
internal data class PasswordPreviewSettings(
    val visibility: PasswordVisibility = PasswordVisibility.Partial,
    val symbol: String = Asterisk,
) {
    /**
     * 按当前模式生成密码显示文本。
     * @param password 原密码
     * @param length 全隐藏查询返回的密码字符数
     * @return 原文或遮罩后的文本
     */
    fun display(password: String, length: Int? = null): String {
        if (visibility == PasswordVisibility.Full) return password
        val count = length ?: password.codePointCount(0, password.length)
        if (visibility == PasswordVisibility.Hidden || count <= 2) return symbol.repeat(count)
        val edge = if (count < 6) 1 else 2
        val start = password.offsetByCodePoints(0, edge)
        val end = password.offsetByCodePoints(password.length, -edge)
        return password.substring(0, start) + symbol.repeat(count - 2 * edge) + password.substring(end)
    }

    companion object {
        const val Asterisk = "*"
        const val Dot = "•"

        /**
         * 验证自定义遮罩是一个可显示的 Unicode 字符。
         * @param symbol 输入的遮罩符号
         * @return 符号是否可用
         */
        fun isValidSymbol(symbol: String): Boolean {
            if (symbol.codePointCount(0, symbol.length) != 1) return false
            val point = symbol.codePointAt(0)
            return !Character.isWhitespace(point) && !Character.isSpaceChar(point) &&
                when (Character.getType(point)) {
                    Character.UNASSIGNED.toInt(), Character.CONTROL.toInt(), Character.FORMAT.toInt(),
                    Character.SURROGATE.toInt(), Character.NON_SPACING_MARK.toInt(),
                    Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> false
                    else -> true
                }
        }

        /**
         * 读取显示模式与符号，兼容只保存显示模式的旧值。
         * @param value MMKV 中保存的值
         * @return 可用的密码预览设置
         */
        fun fromStorage(value: String): PasswordPreviewSettings {
            val visibility = PasswordVisibility.entries.firstOrNull { it.storageValue == value.substringBefore(':') }
                ?: PasswordVisibility.Partial
            val symbol = value.substringAfter(':', "").takeIf(::isValidSymbol) ?: Asterisk
            return PasswordPreviewSettings(visibility, symbol)
        }
    }
}

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
    val email: String = "",
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
     * 返回记录关联的类型及内置标识。
     * @param typeId 类型标识
     * @return 对应类型，缺失时为空
     */
    fun type(typeId: Long?): VaultCategory? = typesById[typeId]

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
     * 按当前本的类型、关键词和排序偏好生成列表。
     * @param query 搜索词
     * @param sort 排序偏好
     * @param locale 名称排序的当前语言
     * @param typeId 筛选的类型标识，空值表示全部类型
     * @return 过滤及排序后的摘要
     */
    fun visiblePasswords(query: String, sort: VaultSort, locale: Locale, typeId: Long? = null): List<PasswordSummary> {
        val terms = query.trim().split(SearchSeparator).filter(String::isNotEmpty)
        val typed = if (typeId == null) passwords else passwords.filter { it.typeId == typeId }
        val filtered = if (terms.isEmpty()) typed else typed.filter { entry ->
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
 * 回收站操作后的列表快照和最新回收站内容。
 * @author Tingdong
 * @date 2026-11-22
 */
internal data class VaultTrash(
    val snapshot: VaultSnapshot,
    val entries: List<TrashEntry>,
)

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
    Storage, Required, InvalidWebsite, DuplicateCategory, DuplicatePassword, Missing, LastBook, BookNotEmpty, BookHasTrash, BuiltIn,
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
