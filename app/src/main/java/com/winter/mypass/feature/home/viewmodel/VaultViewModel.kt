package com.winter.mypass.feature.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winter.mypass.core.utils.MMKVUtils
import com.winter.mypass.feature.home.model.CategoryKind
import com.winter.mypass.feature.home.model.ClipboardClearTimeout
import com.winter.mypass.feature.home.model.GeneratedPasswordCopy
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.PasswordGenerator
import com.winter.mypass.feature.home.model.PasswordGroup
import com.winter.mypass.feature.home.model.PasswordPreviewSettings
import com.winter.mypass.feature.home.model.TrashEntry
import com.winter.mypass.feature.home.model.VaultClipboard
import com.winter.mypass.feature.home.model.VaultException
import com.winter.mypass.feature.home.model.VaultNavigationSettings
import com.winter.mypass.feature.home.model.VaultOrder
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultRepository
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.VaultSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

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
    data class Detail(
        val draft: PasswordDraft,
        val phase: DetailPhase = DetailPhase.Open,
        val deletedAt: Long? = null,
    ) : VaultPage

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

    /**
     * 暂存当前历史页已加载的复制记录。
     * @author Tingdong
     * @date 2026-11-20
     */
    data class GenerationHistory(val entries: List<GeneratedPasswordCopy>? = null) : VaultPage

    /**
     * 暂存当前回收站已加载的密码。
     * @author Tingdong
     * @date 2026-11-22
     */
    data class Trash(
        val entries: List<TrashEntry>? = null,
        val detail: Detail? = null,
        val pendingRestore: Set<Long>? = null,
    ) : VaultPage
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
    val failed: Boolean = false,
) {
    /**
     * 调试输出只包含生成设置。
     */
    override fun toString(): String = "GeneratorState(options=$options, failed=$failed)"
}

/**
 * 固定本次移动的来源、选择和待确认的目标密码本。
 * @author Tingdong
 * @date 2026-11-23
 */
internal data class PasswordMove(val bookId: Long, val ids: Set<Long>, val targetBookId: Long? = null)

/**
 * 解锁期间的密码本界面状态。
 * @author Tingdong
 * @date 2026-11-19
 */
internal data class VaultState(
    val snapshot: VaultSnapshot? = null,
    val sort: VaultSort = VaultSort(),
    val passwordPreview: PasswordPreviewSettings = PasswordPreviewSettings(),
    val navigation: VaultNavigationSettings = VaultNavigationSettings(),
    val clipboardClearTimeout: ClipboardClearTimeout = ClipboardClearTimeout.Seconds30,
    val query: String = "",
    val typeFilterId: Long? = null,
    val selectedPasswordIds: Set<Long> = emptySet(),
    val passwordMove: PasswordMove? = null,
    val passwordMoveSucceeded: Boolean = false,
    val tab: VaultTab = VaultTab.Home,
    val page: VaultPage? = null,
    val generator: GeneratorState = GeneratorState(),
    val busy: Boolean = false,
    val problem: VaultProblem? = null,
) {
    /** 当前详情可能位于密码本或回收站。 */
    val detail: VaultPage.Detail?
        get() = when (val visiblePage = page) {
            is VaultPage.Detail -> visiblePage
            is VaultPage.Trash -> visiblePage.detail
            else -> null
        }
}

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
    private var pendingGeneratedAddition: String? = null
    private val navigationSaveMutex = Mutex()

    /**
     * 打开密码本前从本地读取的界面偏好。
     * @author Tingdong
     * @date 2026-11-22
     */
    private data class StoredPreferences(
        val bookId: Long?,
        val sort: VaultSort,
        val preview: PasswordPreviewSettings,
        val navigation: VaultNavigationSettings,
    )

    init {
        startGeneration(current.generator.options)
        load()
    }

    /**
     * 加载上次选择的密码本，恢复界面偏好和剪贴板清除时限。
     */
    fun load() = perform {
        val clipboardClearTimeout = clipboard.loadClearTimeout()
        val preferences = withContext(Dispatchers.IO) {
            val navigation = try {
                MMKVUtils.getObject<VaultNavigationSettings>(NavigationSettingsKey)
            } catch (_: SerializationException) {
                null
            }
            StoredPreferences(
                bookId = MMKVUtils.getLong(BookKey).takeIf { it > 0 },
                sort = readSort(),
                preview = PasswordPreviewSettings.fromStorage(MMKVUtils.getString(PasswordVisibilityKey)),
                navigation = navigation ?: VaultNavigationSettings(),
            )
        }
        val snapshot = repository.snapshot(preferences.bookId, preferences.preview)
        rememberBook(snapshot.bookId)
        mutableState.value = current.copy(
            snapshot = snapshot,
            sort = preferences.sort,
            passwordPreview = preferences.preview,
            navigation = preferences.navigation,
            clipboardClearTimeout = clipboardClearTimeout,
        )
    }

    /**
     * 打开底栏页面。
     * @param tab 目标页面
     */
    fun selectTab(tab: VaultTab) {
        if (current.tab != tab && !current.busy && current.page == null && current.passwordMove == null) {
            mutableState.value = current.copy(tab = tab, selectedPasswordIds = emptySet(), problem = null)
        }
    }

    /**
     * 切换当前密码本并保留搜索词。
     * @param id 密码本标识
     */
    fun selectBook(id: Long) = performFromTabs {
        val snapshot = repository.snapshot(id, current.passwordPreview)
        rememberBook(snapshot.bookId)
        mutableState.value = current.copy(snapshot = snapshot, selectedPasswordIds = emptySet())
    }

    /**
     * 更新只在当前本中生效的搜索词。
     * @param query 查询内容
     */
    fun search(query: String) {
        if (current.busy || current.page != null || current.passwordMove != null || query == current.query) return
        mutableState.value = current.copy(query = query, selectedPasswordIds = emptySet())
    }

    /**
     * 切换当前本的类型筛选并退出多选。
     * @param typeId 类型标识，空值表示全部类型
     */
    fun filterType(typeId: Long?) {
        if (current.busy || current.page != null || current.passwordMove != null ||
            current.tab != VaultTab.Home || current.typeFilterId == typeId
        ) return
        if (typeId != null && current.snapshot?.types?.none { it.id == typeId } != false) return
        mutableState.value = current.copy(typeFilterId = typeId, selectedPasswordIds = emptySet(), problem = null)
    }

    /**
     * 长按进入多选，或切换当前本内一条密码的选择状态。
     * @param id 密码记录标识
     */
    fun togglePasswordSelection(id: Long) {
        if (current.busy || current.page != null || current.passwordMove != null || current.tab != VaultTab.Home) return
        if (current.snapshot?.passwords?.none { it.id == id } != false) return
        val selected = current.selectedPasswordIds
        mutableState.value = current.copy(
            selectedPasswordIds = if (id in selected) selected - id else selected + id,
            problem = null,
        )
    }

    /**
     * 退出首页多选并清理操作错误。
     */
    fun clearPasswordSelection() {
        if (!current.busy && current.passwordMove == null) {
            mutableState.value = current.copy(selectedPasswordIds = emptySet(), problem = null)
        }
    }

    /**
     * 删除已确认的所选密码，成功后退出多选，失败时保留选择。
     */
    fun deleteSelectedPasswords() {
        val snapshot = current.snapshot ?: return
        if (current.tab != VaultTab.Home || current.page != null) return
        val ids = current.selectedPasswordIds.intersect(snapshot.passwords.map { it.id }.toSet())
        if (ids.isEmpty()) return
        performFromTabs {
            val updated = repository.deletePasswords(ids, snapshot.bookId, current.passwordPreview)
            mutableState.value = current.copy(snapshot = updated, selectedPasswordIds = emptySet())
        }
    }

    /**
     * 保存当前选择并打开目标密码本选择框。
     */
    fun openPasswordMove() {
        val snapshot = current.snapshot ?: return
        if (current.busy || current.page != null || current.passwordMove != null ||
            current.tab != VaultTab.Home || snapshot.books.size < 2
        ) return
        val ids = current.selectedPasswordIds.intersect(snapshot.passwords.map { it.id }.toSet())
        if (ids.isEmpty()) return
        mutableState.value = current.copy(passwordMove = PasswordMove(snapshot.bookId, ids), problem = null)
    }

    /**
     * 取消移动并保留原来的多选状态。
     */
    fun dismissPasswordMove() {
        if (!current.busy) mutableState.value = current.copy(passwordMove = null, problem = null)
    }

    /**
     * 消费移动成功提示，避免页面重建时重复展示。
     */
    fun consumePasswordMoveSuccess() {
        if (current.passwordMoveSucceeded) mutableState.value = current.copy(passwordMoveSucceeded = false)
    }

    /**
     * 移动到所选密码本，遇到重复项时等待用户确认。
     * @param targetBookId 目标密码本标识
     */
    fun moveSelectedPasswords(targetBookId: Long) = movePasswords(targetBookId, allowDuplicate = false)

    /**
     * 按已固定的目标和选择确认移动重复记录。
     */
    fun confirmPasswordMove() {
        val target = current.passwordMove?.targetBookId ?: return
        movePasswords(target, allowDuplicate = true)
    }

    /**
     * 串行提交移动，成功后刷新列表与计数并退出多选。
     * @param targetBookId 目标密码本标识
     * @param allowDuplicate 是否已确认保留重复记录
     */
    private fun movePasswords(targetBookId: Long, allowDuplicate: Boolean) {
        val move = current.passwordMove ?: return
        val snapshot = current.snapshot ?: return
        if (current.tab != VaultTab.Home || current.page != null || snapshot.bookId != move.bookId ||
            targetBookId == move.bookId || snapshot.books.none { it.id == targetBookId } ||
            (move.targetBookId != null && (!allowDuplicate || move.targetBookId != targetBookId))
        ) return
        perform {
            val updated = try {
                repository.movePasswords(move.ids, move.bookId, targetBookId, current.passwordPreview, allowDuplicate)
            } catch (failure: VaultException) {
                if (allowDuplicate || failure.problem != VaultProblem.DuplicatePassword) throw failure
                mutableState.value = current.copy(passwordMove = move.copy(targetBookId = targetBookId))
                return@perform
            }
            mutableState.value = current.copy(
                snapshot = updated, selectedPasswordIds = emptySet(), passwordMove = null, passwordMoveSucceeded = true,
            )
        }
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
     * 保存敏感剪贴板的自动清除时限。
     * @param timeout 用户选择的时限
     */
    fun setClipboardClearTimeout(timeout: ClipboardClearTimeout) {
        if (current.tab != VaultTab.Settings || current.clipboardClearTimeout == timeout) return
        performFromTabs {
            clipboard.setClearTimeout(timeout)
            mutableState.value = current.copy(clipboardClearTimeout = timeout)
        }
    }

    /**
     * 在串行锁内合并最新偏好，成功后只更新底栏状态。
     * @param update 对单个开关的修改
     */
    fun setNavigationSettings(update: (VaultNavigationSettings) -> VaultNavigationSettings) {
        if (current.tab != VaultTab.Settings || current.page != null || current.busy) return
        viewModelScope.launch {
            navigationSaveMutex.withLock {
                try {
                    val navigation = update(current.navigation)
                    if (navigation == current.navigation) return@withLock
                    withContext(Dispatchers.IO) {
                        check(MMKVUtils.putObject(NavigationSettingsKey, navigation))
                    }
                    mutableState.value = current.copy(navigation = navigation, problem = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = current.copy(problem = VaultProblem.Storage)
                }
            }
        }
    }

    /**
     * 打开指定条目的详情。
     * @param id 记录标识
     */
    fun openDetail(id: Long) = performFromTabs {
        val detail = repository.detail(id)
        mutableState.value = current.copy(
            tab = VaultTab.Home, page = VaultPage.Detail(detail), selectedPasswordIds = emptySet(),
        )
    }

    /**
     * 新建当前本的记录，生成页确认可等待当前写入结束。
     * @param password 需要回填的生成结果
     */
    fun newPassword(password: String = "") {
        val book = current.snapshot?.bookId ?: return
        if (current.page != null || current.passwordMove != null) return
        if (current.busy) {
            if (current.tab == VaultTab.Generate && password.isNotEmpty()) pendingGeneratedAddition = password
            return
        }
        val draft = PasswordDraft(bookId = book).withDefaultType()
        mutableState.value = current.copy(
            tab = VaultTab.Home, page = VaultPage.Editor(draft, draft.copy(password = password)),
            selectedPasswordIds = emptySet(), problem = null,
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
        val detail = current.detail ?: return
        val trash = current.page as? VaultPage.Trash
        if (!current.busy && trash?.pendingRestore == null && detail.phase == DetailPhase.Open) {
            val closing = detail.copy(phase = DetailPhase.Closing)
            mutableState.value = current.copy(page = trash?.copy(detail = closing) ?: closing, problem = null)
        }
    }

    /**
     * 详情 Sheet 退场后切换到编辑表单或首页。
     */
    fun finishPasswordDetailDismissal() {
        val detail = current.detail ?: return
        val next = when (detail.phase) {
            DetailPhase.Open -> return
            DetailPhase.Closing -> (current.page as? VaultPage.Trash)?.copy(detail = null)
            DetailPhase.ClosingForEdit -> {
                val draft = detail.draft.withDefaultType()
                VaultPage.Editor(draft, draft)
            }
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
        if (draft.typeId != editor.draft.typeId) {
            draft.typeId?.let { typeId ->
                viewModelScope.launch(Dispatchers.IO) { MMKVUtils.putLong(TypeKey, typeId) }
            }
        }
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
            editor.draft.typeId?.let { typeId ->
                withContext(Dispatchers.IO) { MMKVUtils.putLong(TypeKey, typeId) }
            }
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
     * 从二级页面返回对应底栏页面。
     */
    fun closePage() {
        if (current.busy || current.detail != null ||
            (current.page !is VaultPage.Categories && current.page !is VaultPage.GenerationHistory &&
                current.page !is VaultPage.Trash)
        ) return
        mutableState.value = current.copy(page = null, problem = null)
    }

    /**
     * 打开生成历史并读取已复制的密码。
     */
    fun openGenerationHistory() {
        if (current.busy || current.tab != VaultTab.Generate || current.page != null) return
        mutableState.value = current.copy(page = VaultPage.GenerationHistory(), problem = null)
        loadGenerationHistory()
    }

    /**
     * 加载或重试读取生成历史。
     */
    fun loadGenerationHistory() {
        if (current.page !is VaultPage.GenerationHistory) return
        perform {
            val entries = repository.generatedPasswordCopies()
            mutableState.value = current.copy(page = VaultPage.GenerationHistory(entries))
        }
    }

    /**
     * 复制历史中的一条密码。
     * @param id 历史记录标识
     */
    fun copyFromGenerationHistory(id: Long) {
        val history = current.page as? VaultPage.GenerationHistory ?: return
        val entry = history.entries?.firstOrNull { it.id == id } ?: return
        perform { clipboard.copy(entry.password, sensitive = true) }
    }

    /**
     * 删除选中的生成历史并刷新历史页。
     * @param ids 选中的历史记录标识
     * @param onDeleted 删除提交后的回调
     */
    fun deleteFromGenerationHistory(ids: Set<Long>, onDeleted: () -> Unit) {
        if (current.page !is VaultPage.GenerationHistory || ids.isEmpty()) return
        perform {
            val remaining = repository.deleteGeneratedPasswordCopies(ids)
            mutableState.value = current.copy(page = VaultPage.GenerationHistory(remaining))
            onDeleted()
        }
    }

    /**
     * 打开回收站并读取已删除的密码。
     */
    fun openTrash() {
        if (current.busy || current.tab != VaultTab.Settings || current.page != null) return
        mutableState.value = current.copy(page = VaultPage.Trash(), problem = null)
        loadTrash()
    }

    /**
     * 加载或重试读取回收站。
     */
    fun loadTrash() {
        val page = current.page as? VaultPage.Trash ?: return
        if (page.detail != null || page.pendingRestore != null) return
        val book = current.snapshot?.bookId ?: return
        perform {
            val trash = repository.trash(book, current.passwordPreview)
            mutableState.value = current.copy(snapshot = trash.snapshot, page = VaultPage.Trash(trash.entries))
        }
    }

    /**
     * 按需读取已删除记录的完整详情。
     * @param id 回收站记录标识
     */
    fun openTrashDetail(id: Long) {
        val page = current.page as? VaultPage.Trash ?: return
        if (page.detail != null || page.pendingRestore != null) return
        val entry = page.entries?.firstOrNull { it.id == id } ?: return
        perform {
            val draft = repository.detail(id, trashed = true)
            val detail = VaultPage.Detail(draft, deletedAt = entry.deletedAt)
            mutableState.value = current.copy(page = page.copy(detail = detail))
        }
    }

    /**
     * 恢复选中的回收站记录并刷新列表。
     * @param ids 选中的记录标识
     * @param onRestored 恢复提交后的回调
     */
    fun restoreFromTrash(ids: Set<Long>, onRestored: () -> Unit) {
        val page = current.page as? VaultPage.Trash ?: return
        if (page.pendingRestore != null) return
        restoreTrash(page, ids, allowDuplicate = false, onRestored = onRestored)
    }

    /**
     * 按确认时保存的选择恢复记录。
     * @param onRestored 恢复提交后的回调
     */
    fun confirmTrashRestore(onRestored: () -> Unit) {
        val page = current.page as? VaultPage.Trash ?: return
        val ids = page.pendingRestore ?: return
        restoreTrash(page, ids, allowDuplicate = true, onRestored = onRestored)
    }

    /**
     * 关闭重复记录确认并保留当前选择和详情。
     */
    fun dismissTrashRestore() {
        val page = current.page as? VaultPage.Trash ?: return
        if (current.busy || page.pendingRestore == null) return
        mutableState.value = current.copy(page = page.copy(pendingRestore = null))
    }

    /**
     * 在恢复冲突时保存待确认的选择，成功后更新列表。
     * @param page 当前回收站页面
     * @param ids 待恢复的记录标识
     * @param allowDuplicate 是否已确认保留重复记录
     * @param onRestored 恢复提交后的回调
     */
    private fun restoreTrash(
        page: VaultPage.Trash,
        ids: Set<Long>,
        allowDuplicate: Boolean,
        onRestored: () -> Unit,
    ) {
        val detail = page.detail
        if (ids.isEmpty() || (detail != null &&
                (detail.phase != DetailPhase.Open || ids.singleOrNull() != detail.draft.id))
        ) return
        val book = current.snapshot?.bookId ?: return
        perform {
            if (allowDuplicate) mutableState.value = current.copy(page = page.copy(pendingRestore = null))
            val trash = try {
                repository.restorePasswords(ids, book, current.passwordPreview, allowDuplicate)
            } catch (failure: VaultException) {
                if (allowDuplicate || failure.problem != VaultProblem.DuplicatePassword) throw failure
                mutableState.value = current.copy(page = page.copy(pendingRestore = ids.toSet()))
                return@perform
            }
            mutableState.value = current.copy(
                snapshot = trash.snapshot,
                page = VaultPage.Trash(trash.entries, detail?.copy(phase = DetailPhase.Closing)),
            )
            onRestored()
        }
    }

    /**
     * 永久删除选中的回收站记录并刷新列表。
     * @param ids 选中的记录标识
     * @param onDeleted 删除提交后的回调
     */
    fun deleteFromTrash(ids: Set<Long>, onDeleted: () -> Unit) {
        val page = current.page as? VaultPage.Trash ?: return
        if (page.detail != null || page.pendingRestore != null || ids.isEmpty()) return
        val book = current.snapshot?.bookId ?: return
        perform {
            val trash = repository.purgePasswords(ids, book, current.passwordPreview)
            mutableState.value = current.copy(snapshot = trash.snapshot, page = VaultPage.Trash(trash.entries))
            onDeleted()
        }
    }

    /**
     * 清空回收站并刷新列表。
     */
    fun emptyTrash() {
        val page = current.page as? VaultPage.Trash ?: return
        if (page.detail != null || page.pendingRestore != null) return
        val book = current.snapshot?.bookId ?: return
        perform {
            val trash = repository.clearTrash(book, current.passwordPreview)
            mutableState.value = current.copy(snapshot = trash.snapshot, page = VaultPage.Trash(trash.entries))
        }
    }

    /**
     * 打开指定分类的管理页。
     * @param kind 分类种类
     */
    fun manage(kind: CategoryKind) {
        if (!current.busy && current.page == null && current.passwordMove == null) {
            mutableState.value = current.copy(
                page = VaultPage.Categories(kind), selectedPasswordIds = emptySet(), problem = null,
            )
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
            mutableState.value = current.copy(
                snapshot = snapshot,
                typeFilterId = current.typeFilterId.takeIf { selected -> snapshot.types.any { it.id == selected } },
            )
        }
    }

    /**
     * 复制详情中的密码或补充字段。
     * @param value 待复制内容
     * @param sensitive 是否为密码
     * @param onCopied 剪贴板写入成功后的可选回调
     */
    fun copyText(value: String, sensitive: Boolean, onCopied: (() -> Unit)?) {
        if (current.detail?.phase != DetailPhase.Open) return
        if ((current.page as? VaultPage.Trash)?.pendingRestore != null) return
        perform {
            clipboard.copy(value, sensitive)
            onCopied?.invoke()
        }
    }

    /**
     * 复制点击时显示的密码，并在剪贴板写入成功后记录历史。
     * @param value 用户点击时显示的密码
     * @param onCopied 剪贴板写入成功后的回调
     */
    fun copyGenerated(value: String, onCopied: () -> Unit) {
        if (current.tab != VaultTab.Generate || current.page != null || value.isEmpty()) return
        perform {
            clipboard.copy(value, sensitive = true, showToast = false)
            onCopied()
            repository.recordGeneratedPasswordCopy(value)
        }
    }

    /**
     * 更新参数并在后台生成，仅应用最近一次请求的结果。
     * @param options 用户选中的生成参数
     */
    fun generatorOptions(options: GeneratorOptions) {
        val currentGenerator = current.generator
        if (current.busy || current.tab != VaultTab.Generate || current.page != null ||
            (options == currentGenerator.options && !currentGenerator.failed)
        ) return
        startGeneration(options)
    }

    /**
     * 用当前参数手动生成一组新密码。
     */
    fun refreshGeneratedPassword() {
        if (!current.busy && current.tab == VaultTab.Generate && current.page == null) {
            startGeneration(current.generator.options)
        }
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
            generator = current.generator.copy(options = options, failed = false),
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
        pendingGeneratedAddition = null
        mutableState.value = VaultState()
    }

    /**
     * 仅在底栏页面处于前台时执行其操作。
     * @param block 当前待执行操作
     */
    private fun performFromTabs(block: suspend () -> Unit) {
        if (current.page == null && current.passwordMove == null) perform(block)
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
                pendingGeneratedAddition = null
                throw cancelled
            } catch (failure: VaultException) {
                mutableState.value = current.copy(problem = failure.problem)
            } catch (_: Exception) {
                mutableState.value = current.copy(problem = VaultProblem.Storage)
            } finally {
                mutableState.value = current.copy(busy = false)
            }
            val addition = pendingGeneratedAddition
            pendingGeneratedAddition = null
            if (addition != null && currentCoroutineContext().isActive && current.tab == VaultTab.Generate) {
                newPassword(addition)
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
     * 为缺少类型的草稿补上上次选择的类型，首次使用第一个类型。
     * @return 带有默认类型的草稿
     */
    private fun PasswordDraft.withDefaultType(): PasswordDraft {
        if (typeId != null) return this
        val types = current.snapshot?.types.orEmpty()
        if (types.isEmpty()) return this
        val remembered = MMKVUtils.getLong(TypeKey)
        return copy(typeId = types.firstOrNull { it.id == remembered }?.id ?: types.first().id)
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
        const val NavigationSettingsKey = "vault.navigation_settings"
        const val TypeKey = "vault.last_type"
    }
}
