package com.winter.mypass.feature.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winter.mypass.core.utils.MMKVUtils
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.PasswordGenerator
import com.winter.mypass.feature.home.model.PasswordGroup
import com.winter.mypass.feature.home.model.PasswordPreviewSettings
import com.winter.mypass.feature.home.model.VaultClipboard
import com.winter.mypass.feature.home.model.VaultException
import com.winter.mypass.feature.home.model.VaultOrder
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultRepository
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.VaultSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 解锁后可从底栏选择的页面。
 * @author Tingdong
 * @date 2026-11-19
 */
internal enum class VaultTab { Home, Generate, Settings }

/**
 * 从顶层页面打开的会话内页面。
 * @author Tingdong
 * @date 2026-11-19
 */
internal sealed interface VaultPage {
    /**
     * 保留详情内容直到 Sheet 退场。
     * @author Tingdong
     * @date 2026-11-20
     */
    data class Detail(val draft: PasswordDraft, val phase: DetailPhase = DetailPhase.Open) : VaultPage

    /**
     * 保留原记录和草稿，直到表单完成退场。
     * @author Tingdong
     * @date 2026-11-20
     */
    data class Editor(
        val original: PasswordDraft,
        val draft: PasswordDraft,
        val phase: EditorPhase = EditorPhase.Open,
    ) : VaultPage

    data class Categories(val kind: CategoryKind) : VaultPage
}

/**
 * 详情 Sheet 的可见状态和退场目标。
 * @author Tingdong
 * @date 2026-11-20
 */
internal enum class DetailPhase { Open, Closing, ClosingForEdit }

/**
 * 密码表单的可见状态和退场后的操作。
 * @author Tingdong
 * @date 2026-11-20
 */
internal enum class EditorPhase {
    Open,
    Closing,
    ClosingForDiscardConfirmation,
    ConfirmDiscard;

    val isClosing: Boolean get() = this != Open && this != ConfirmDiscard
}

/**
 * 密码长度、字符类型和字母的大写选项。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class GeneratorOptions(
    val length: Int = PasswordGenerator.DefaultLength,
    val letters: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = false,
) {
    /** 返回当前启用的字符组，大写仅随字母选项生效。 */
    fun groups(): Set<PasswordGroup> = buildSet {
        if (letters) {
            add(PasswordGroup.Lowercase)
            if (uppercase) add(PasswordGroup.Uppercase)
        }
        if (digits) add(PasswordGroup.Digits)
        if (symbols) add(PasswordGroup.Symbols)
    }
}

/**
 * 生成器在解锁会话中使用的参数和结果。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class GeneratorState(
    val options: GeneratorOptions = GeneratorOptions(),
    val password: String = "",
    val generating: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * 调试输出只包含生成设置。
     */
    override fun toString(): String = "GeneratorState(options=$options, generating=$generating)"
}

/**
 * 解锁期间的密码本界面状态。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class VaultState(
    val snapshot: VaultSnapshot? = null,
    val sort: VaultSort = VaultSort(),
    val passwordPreview: PasswordPreviewSettings = PasswordPreviewSettings(),
    val query: String = "",
    val tab: VaultTab = VaultTab.Home,
    val page: VaultPage? = null,
    val generator: GeneratorState = GeneratorState(),
    val busy: Boolean = false,
    val problem: VaultProblem? = null,
)

/**
 * 管理密码本界面、串行操作和解锁期间的临时内容。
 * @author Tingdong
 * @date 2026-11-19
 */
internal class VaultViewModel(
    private val repository: VaultRepository,
    private val clipboard: VaultClipboard,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VaultState())
    val state = mutableState.asStateFlow()
    val passwordEditorClipboard get() = clipboard.passwordEditorClipboard

    private val current: VaultState get() = mutableState.value
    private var generation: Job? = null

    init {
        startGeneration(current.generator.options)
        load()
    }

    /**
     * 加载上次选择的密码本，恢复排序与密码显示偏好。
     */
    fun load() = perform {
        val (selected, sort, preview) = withContext(Dispatchers.IO) {
            Triple(
                MMKVUtils.getLong(BookKey).takeIf { it > 0 },
                readSort(),
                PasswordPreviewSettings.fromStorage(MMKVUtils.getString(PasswordVisibilityKey)),
            )
        }
        val snapshot = repository.snapshot(selected, preview)
        rememberBook(snapshot.bookId)
        mutableState.value = current.copy(snapshot = snapshot, sort = sort, passwordPreview = preview)
    }

    /**
     * 打开底栏页面。
     * @param tab 目标页面
     */
    fun selectTab(tab: VaultTab) {
        if (!current.busy && current.page == null) mutableState.value = current.copy(tab = tab, problem = null)
    }

    /**
     * 切换当前密码本并保留搜索词。
     * @param id 密码本标识
     */
    fun selectBook(id: Long) = performFromTabs {
        val snapshot = repository.snapshot(id, current.passwordPreview)
        rememberBook(snapshot.bookId)
        mutableState.value = current.copy(snapshot = snapshot)
    }

    /**
     * 更新只在当前本中生效的搜索词。
     * @param query 查询内容
     */
    fun search(query: String) {
        if (current.page != null) return
        mutableState.value = current.copy(query = query)
    }

    /**
     * 保存排序方式及逆序状态。
     * @param sort 新的排序设置
     */
    fun sort(sort: VaultSort) = performFromTabs {
        withContext(Dispatchers.IO) {
            check(MMKVUtils.putString(SortKey, "${sort.order.name}:${sort.reversed}"))
        }
        mutableState.value = current.copy(sort = sort)
    }

    /**
     * 保存显示模式和遮罩符号，并刷新当前密码本的预览值。
     * @param preview 用户选择的密码预览设置
     */
    fun setPasswordPreview(preview: PasswordPreviewSettings) {
        val bookId = current.snapshot?.bookId ?: return
        if (current.tab != VaultTab.Settings || current.passwordPreview == preview ||
            !PasswordPreviewSettings.isValidSymbol(preview.symbol)
        ) return
        performFromTabs {
            val snapshot = repository.snapshot(bookId, preview)
            withContext(Dispatchers.IO) {
                check(MMKVUtils.putString(PasswordVisibilityKey, "${preview.visibility.storageValue}:${preview.symbol}"))
            }
            mutableState.value = current.copy(snapshot = snapshot, passwordPreview = preview)
        }
    }

    /**
     * 打开指定条目的详情。
     * @param id 记录标识
     */
    fun openDetail(id: Long) = performFromTabs {
        val detail = repository.detail(id)
        mutableState.value = current.copy(tab = VaultTab.Home, page = VaultPage.Detail(detail))
    }

    /**
     * 新建当前本的记录，可使用生成的密码。
     * @param password 需要回填的生成结果
     */
    fun newPassword(password: String = "") {
        val book = current.snapshot?.bookId ?: return
        if (current.busy || current.page != null) return
        val draft = PasswordDraft(bookId = book)
        mutableState.value = current.copy(
            tab = VaultTab.Home, page = VaultPage.Editor(draft, draft.copy(password = password)), problem = null,
        )
    }

    /**
     * 关闭详情后打开编辑表单。
     */
    fun editPassword() {
        val detail = current.page as? VaultPage.Detail ?: return
        if (!current.busy && detail.phase == DetailPhase.Open) {
            mutableState.value = current.copy(page = detail.copy(phase = DetailPhase.ClosingForEdit), problem = null)
        }
    }

    /**
     * 开始关闭当前详情 Sheet。
     */
    fun dismissPasswordDetail() {
        val detail = current.page as? VaultPage.Detail ?: return
        if (!current.busy && detail.phase == DetailPhase.Open) {
            mutableState.value = current.copy(page = detail.copy(phase = DetailPhase.Closing), problem = null)
        }
    }

    /**
     * 详情 Sheet 退场后切换到编辑表单或首页。
     */
    fun finishPasswordDetailDismissal() {
        val detail = current.page as? VaultPage.Detail ?: return
        val next = when (detail.phase) {
            DetailPhase.Open -> return
            DetailPhase.Closing -> null
            DetailPhase.ClosingForEdit -> VaultPage.Editor(detail.draft, detail.draft)
        }
        mutableState.value = current.copy(page = next, problem = null)
    }

    /**
     * 更新当前表单。
     * @param update 应用到当前草稿的字段变更
     */
    fun editDraft(update: (PasswordDraft) -> PasswordDraft) {
        val editor = current.page as? VaultPage.Editor ?: return
        if (current.busy || editor.phase != EditorPhase.Open) return
        val draft = update(editor.draft)
        mutableState.value = current.copy(page = editor.copy(draft = draft), problem = null)
    }

    /**
     * 保存当前表单后返回密码本列表。
     * @param allowDuplicate 用户确认允许重复
     */
    fun savePassword(allowDuplicate: Boolean = false) {
        val editor = current.page as? VaultPage.Editor ?: return
        if (editor.phase != EditorPhase.Open) return
        val book = current.snapshot?.bookId ?: return
        perform {
            val snapshot = repository.save(editor.draft, book, allowDuplicate, current.passwordPreview)
            mutableState.value = current.copy(snapshot = snapshot, page = editor.copy(phase = EditorPhase.Closing))
        }
    }

    /**
     * 从底部按钮或系统返回开始关闭密码表单。
     * @return 是否已处理关闭请求，保存中返回 false
     */
    fun dismissPasswordEditor(): Boolean {
        val editor = current.page as? VaultPage.Editor ?: return true
        if (editor.phase != EditorPhase.Open) return true
        if (current.busy) return false
        val phase = if (editor.draft != editor.original) {
            EditorPhase.ClosingForDiscardConfirmation
        } else {
            EditorPhase.Closing
        }
        mutableState.value = current.copy(page = editor.copy(phase = phase))
        return true
    }

    /**
     * 接收 Sheet 退场完成通知并处理下一步。
     */
    fun finishPasswordEditorDismissal() {
        val editor = current.page as? VaultPage.Editor ?: return
        val next = when (editor.phase) {
            EditorPhase.Closing -> null
            EditorPhase.ClosingForDiscardConfirmation -> editor.copy(phase = EditorPhase.ConfirmDiscard)
            EditorPhase.Open, EditorPhase.ConfirmDiscard -> return
        }
        mutableState.value = current.copy(page = next, problem = current.problem.takeIf { next is VaultPage.Editor })
    }

    /**
     * 放弃修改或重新打开原草稿。
     * @param discard 用户是否确认放弃修改
     */
    fun confirmPasswordDiscard(discard: Boolean) {
        val editor = current.page as? VaultPage.Editor ?: return
        if (editor.phase != EditorPhase.ConfirmDiscard) return
        if (discard) {
            mutableState.value = current.copy(page = null, problem = null)
        } else {
            mutableState.value = current.copy(page = editor.copy(phase = EditorPhase.Open))
        }
    }

    /**
     * 删除详情中的记录。
     */
    fun deletePassword() {
        val detail = current.page as? VaultPage.Detail ?: return
        if (detail.phase != DetailPhase.Open) return
        val id = detail.draft.id ?: return
        val book = current.snapshot?.bookId ?: return
        perform {
            val snapshot = repository.deletePassword(id, book, current.passwordPreview)
            mutableState.value = current.copy(snapshot = snapshot, page = detail.copy(phase = DetailPhase.Closing))
        }
    }

    /**
     * 从分类页面返回列表。
     */
    fun closePage() {
        if (current.busy || current.page !is VaultPage.Categories) return
        mutableState.value = current.copy(page = null, problem = null)
    }

    /**
     * 打开指定分类的管理页。
     * @param kind 分类种类
     */
    fun manage(kind: CategoryKind) {
        if (!current.busy && current.page == null) {
            mutableState.value = current.copy(page = VaultPage.Categories(kind), problem = null)
        }
    }

    /**
     * 新建或重命名当前分类。
     * @param id 分类标识，新建时为空
     * @param name 分类名称
     * @param onSaved 保存后的界面回调
     */
    fun saveCategory(id: Long?, name: String, onSaved: () -> Unit) {
        val kind = (current.page as? VaultPage.Categories)?.kind ?: return
        val book = current.snapshot?.bookId ?: return
        perform {
            val snapshot = repository.saveCategory(kind, id, name, book, current.passwordPreview)
            if (book != snapshot.bookId) rememberBook(snapshot.bookId)
            mutableState.value = current.copy(snapshot = snapshot)
            onSaved()
        }
    }

    /**
     * 删除当前分类下指定的条目。
     * @param id 分类标识
     */
    fun deleteCategory(id: Long) {
        val kind = (current.page as? VaultPage.Categories)?.kind ?: return
        val book = current.snapshot?.bookId ?: return
        perform {
            val snapshot = repository.deleteCategory(kind, id, book, current.passwordPreview)
            if (book != snapshot.bookId) rememberBook(snapshot.bookId)
            mutableState.value = current.copy(snapshot = snapshot)
        }
    }

    /**
     * 复制详情中的密码或补充字段。
     * @param value 待复制内容
     * @param sensitive 是否为密码
     * @param onCopied 剪贴板写入成功后的可选回调
     */
    fun copyText(value: String, sensitive: Boolean, onCopied: (() -> Unit)?) {
        if ((current.page as? VaultPage.Detail)?.phase != DetailPhase.Open) return
        perform {
            clipboard.copy(value, sensitive)
            onCopied?.invoke()
        }
    }

    /**
     * 复制当前生成的密码，剪贴板写入成功后通知界面。
     * @param value 用户点击时显示的密码
     * @param onCopied 剪贴板写入成功后的回调
     */
    fun copyGenerated(value: String, onCopied: () -> Unit) {
        val generator = current.generator
        if (current.tab != VaultTab.Generate || current.page != null || generator.generating ||
            value.isEmpty() || value != generator.password
        ) return
        perform {
            clipboard.copy(value, sensitive = true, showToast = false)
            onCopied()
        }
    }

    /**
     * 更新参数并在后台生成，仅应用最近一次请求的结果。
     * @param options 用户选中的生成参数
     */
    fun generatorOptions(options: GeneratorOptions) {
        val currentGenerator = current.generator
        if (current.busy || (options == currentGenerator.options && !currentGenerator.failed)) return
        startGeneration(options)
    }

    /**
     * 用当前参数手动生成一组新密码。
     */
    fun refreshGeneratedPassword() {
        val generator = current.generator
        if (!current.busy && !generator.generating) startGeneration(generator.options)
    }

    /**
     * 创建初始结果或刷新结果，保持密码本加载独立。
     * @param options 要应用的生成参数
     */
    private fun startGeneration(options: GeneratorOptions) {
        val groups = options.groups()
        if (options.length !in PasswordGenerator.LengthRange || groups.isEmpty()) return

        generation?.cancel()
        mutableState.value = current.copy(
            generator = current.generator.copy(options = options, generating = true, failed = false),
        )
        generation = viewModelScope.launch {
            try {
                val password = withContext(Dispatchers.Default) {
                    PasswordGenerator.generate(options.length, groups)
                }
                mutableState.value = current.copy(generator = GeneratorState(options, password))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = current.copy(generator = GeneratorState(options, failed = true))
            }
        }
    }

    /**
     * 清除已关闭的错误提示。
     */
    fun clearProblem() {
        if (!current.busy) mutableState.value = current.copy(problem = null)
    }

    /**
     * 会话结束时同步清除密码、草稿和生成结果。
     */
    override fun onCleared() {
        generation?.cancel()
        mutableState.value = VaultState()
    }

    /**
     * 仅在底栏页面处于前台时执行其操作。
     * @param block 当前待执行操作
     */
    private fun performFromTabs(block: suspend () -> Unit) {
        if (current.page == null) perform(block)
    }

    /**
     * 串行执行后台操作并将失败转换为界面状态。
     * @param block 当前待执行操作
     */
    private fun perform(block: suspend () -> Unit) {
        if (current.busy) return
        mutableState.value = current.copy(busy = true, problem = null)
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VaultException) {
                mutableState.value = current.copy(problem = failure.problem)
            } catch (_: Exception) {
                mutableState.value = current.copy(problem = VaultProblem.Storage)
            } finally {
                mutableState.value = current.copy(busy = false)
            }
        }
    }

    /**
     * 在 IO 线程保存本次选中的密码本。
     * @param id 密码本标识
     */
    private suspend fun rememberBook(id: Long) {
        withContext(Dispatchers.IO) { MMKVUtils.putLong(BookKey, id) }
    }

    /**
     * 兼容读取两个旧实现的排序偏好。
     * @return 已保存或默认的排序方式与方向
     */
    private fun readSort(): VaultSort {
        val value = MMKVUtils.getString(SortKey)
        val order = VaultOrder.entries.firstOrNull { it.name == value.substringBefore(':') }
            ?: VaultOrder.entries.firstOrNull { it.name == MMKVUtils.getString("vault.sort.mode") }
            ?: VaultOrder.Updated
        val reversed = value.substringAfter(':', "").toBooleanStrictOrNull()
            ?: MMKVUtils.getBoolean("vault.sort.descending", true)
        return VaultSort(order, reversed)
    }

    private companion object {
        const val SortKey = "vault.sort"
        const val BookKey = "vault.current_book"
        const val PasswordVisibilityKey = "vault.password_visibility"
    }
}
