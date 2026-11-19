package com.winter.mypass.feature.applock.viewmodel

import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.winter.mypass.feature.applock.model.CredentialStore
import com.winter.mypass.feature.applock.model.CredentialType
import com.winter.mypass.feature.home.model.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用密码页面及当前输入的显示状态。
 * @author Tingdong
 * @date 2026-11-18
 */
internal data class AppLockState(
    val screen: LockScreen = LockScreen.Loading,
    val busy: Boolean = false,
    val error: LockError? = null,
    val inputRevision: Int = 0,
)

/**
 * 应用锁的入口阶段，不包含凭据或密钥。
 * @author Tingdong
 * @date 2026-11-18
 */
internal sealed interface LockScreen {
    data object Loading : LockScreen
    data object Choose : LockScreen
    data class Setup(val type: CredentialType, val confirm: Boolean = false) : LockScreen
    data class Locked(val type: CredentialType, val retryAt: Long) : LockScreen
    data object Unlocked : LockScreen
    data class Unavailable(val reason: LockStorageError) : LockScreen
}

/**
 * 与当前凭据输入相关的错误。
 * @author Tingdong
 * @date 2026-11-18
 */
internal enum class LockError { Invalid, Mismatch, Incorrect }

/**
 * 决定凭据错误提示及是否可重试的原因。
 * @author Tingdong
 * @date 2026-11-18
 */
internal enum class LockStorageError { InvalidRecord, MissingRecord, MissingKey, Retryable, VaultUnavailable }

/**
 * 管理首次设置、解锁操作和活动会话。
 * @author Tingdong
 * @date 2026-11-18
 */
internal class AppLockViewModel(
    private val store: CredentialStore,
    val vault: VaultRepository,
    private val cleanupScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppLockState())
    val state = mutableState.asStateFlow()

    private var firstInput: CharArray? = null
    val vaultOwner: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private var closing: Job? = null
    private var unlockedType: CredentialType? = null
    private var operation: Job? = null
    private var timeout: Job? = null
    private var backgroundDeadline: Long? = null

    /**
     * 回到前台时检查超时，按需加载凭据。
     */
    fun onForeground() {
        backgroundDeadline?.let { if (SystemClock.elapsedRealtime() >= it) lock() }
        backgroundDeadline = null
        timeout?.cancel()
        if (state.value.screen == LockScreen.Loading && operation?.isActive != true) retry()
    }

    /**
     * 离开前台后清除输入并开始计时。
     */
    fun onBackground() {
        clearFirstInput()
        val current = state.value
        if (current.busy) {
            operation?.cancel()
            updateScreen(LockScreen.Loading)
        } else if (current.screen is LockScreen.Setup) {
            updateScreen(current.screen.copy(confirm = false))
        } else if (current.screen == LockScreen.Unlocked) {
            val deadline = SystemClock.elapsedRealtime() + BackgroundTimeout
            backgroundDeadline = deadline
            timeout?.cancel()
            timeout = viewModelScope.launch {
                delay(BackgroundTimeout)
                if (backgroundDeadline == deadline) lock()
            }
        } else {
            updateScreen(current.screen)
        }
    }

    /**
     * 检查本机凭据记录，或重试先前的读写错误。
     */
    fun retry() {
        if (state.value.busy) return
        updateScreen(LockScreen.Loading)
        runOperation { store.load() }
    }

    /**
     * 进入指定类型的密码设置页。
     * @param type 用户选择的密码类型
     */
    fun choose(type: CredentialType) {
        if (state.value.screen == LockScreen.Choose) updateScreen(LockScreen.Setup(type))
    }

    /**
     * 清除暂存的首次输入并返回类型选择。
     */
    fun chooseAgain() {
        val current = state.value
        if (!current.busy && current.screen is LockScreen.Setup) {
            clearFirstInput()
            updateScreen(LockScreen.Choose)
        }
    }

    /**
     * 提交输入并接管临时数组，操作结束时清除。
     * @param input 用户刚输入的凭据
     */
    fun submit(input: CharArray) {
        val current = state.value
        if (current.busy) {
            input.fill('\u0000')
            return
        }
        when (val screen = current.screen) {
            is LockScreen.Setup -> submitSetup(screen, input)
            is LockScreen.Locked -> {
                if (screen.retryAt > SystemClock.elapsedRealtime()) {
                    input.fill('\u0000')
                } else if (screen.type.accepts(input)) {
                    runOperation(input, screen.type) { store.verify(screen.type, input) }
                } else {
                    input.fill('\u0000')
                    updateScreen(screen, LockError.Invalid)
                }
            }
            else -> input.fill('\u0000')
        }
    }

    /**
     * 校验首次输入或确认值，第二次匹配时保存凭据。
     * @param screen 当前设置阶段
     * @param input 当前提交的凭据
     */
    private fun submitSetup(screen: LockScreen.Setup, input: CharArray) {
        if (!screen.type.accepts(input)) {
            input.fill('\u0000')
            updateScreen(screen, LockError.Invalid)
        } else if (!screen.confirm) {
            firstInput = input
            updateScreen(screen.copy(confirm = true))
        } else if (firstInput?.contentEquals(input) != true) {
            input.fill('\u0000')
            updateScreen(screen, LockError.Mismatch)
        } else {
            clearFirstInput()
            runOperation(input, screen.type) { store.create(screen.type, input) }
        }
    }

    /**
     * 串行执行凭据 IO 并交接密钥，取消时清理临时数据。
     * @param input 操作结束时需清除的凭据
     * @param type 解锁使用的密码类型
     * @param action IO 协程中执行的操作
     */
    private fun runOperation(
        input: CharArray? = null,
        type: CredentialType? = null,
        action: suspend () -> CredentialStore.Result,
    ) {
        val current = state.value
        mutableState.value = current.copy(busy = true, error = null, inputRevision = current.inputRevision + 1)
        val previous = operation
        operation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var pendingKey: ByteArray? = null
            try {
                previous?.join()
                val result = withContext(Dispatchers.IO) {
                    action().also { pendingKey = (it as? CredentialStore.Result.Unlocked)?.vaultKey }
                }
                when (result) {
                    CredentialStore.Result.Missing -> updateScreen(LockScreen.Choose)
                    is CredentialStore.Result.Locked -> updateScreen(
                        LockScreen.Locked(result.type, result.retryAt),
                        LockError.Incorrect.takeIf { result.incorrect },
                    )
                    is CredentialStore.Result.Unlocked -> {
                        unlockVault(checkNotNull(type), result.vaultKey)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                val reason = when (failure) {
                    is CredentialStore.InvalidRecord -> LockStorageError.InvalidRecord
                    is CredentialStore.MissingRecord -> LockStorageError.MissingRecord
                    is CredentialStore.MissingKey, is KeyPermanentlyInvalidatedException -> LockStorageError.MissingKey
                    else -> LockStorageError.Retryable
                }
                Log.w("AppLock", "Credential operation failed: ${reason.name} (${failure.javaClass.simpleName})")
                updateScreen(LockScreen.Unavailable(reason))
            } finally {
                input?.fill('\u0000')
                pendingKey?.fill(0)
            }
        }
    }

    /**
     * 验证加密库后才进入解锁会话。
     * @param type 已验证的应用锁类型
     * @param key 此次认证产生的临时密钥
     */
    private suspend fun unlockVault(type: CredentialType, key: ByteArray) {
        closing?.join()
        try {
            vault.open(key)
            unlockedType = type
            updateScreen(LockScreen.Unlocked)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            reportVaultFailure(failure)
        } catch (failure: LinkageError) {
            currentCoroutineContext().ensureActive()
            reportVaultFailure(failure)
        }
    }

    /**
     * 展示加密库错误供用户重试。
     * @param failure 打开或迁移时遇到的异常
     */
    private fun reportVaultFailure(failure: Throwable) {
        Log.w("AppLock", "Vault unavailable (${failure.javaClass.simpleName})")
        updateScreen(LockScreen.Unavailable(LockStorageError.VaultUnavailable))
    }

    /** 显示应用锁并结束数据库会话。 */
    private fun lock() {
        val type = checkNotNull(unlockedType)
        unlockedType = null
        backgroundDeadline = null
        updateScreen(LockScreen.Locked(type, retryAt = 0))
        closeVault()
    }

    /**
     * 清除会话中的 ViewModel 并等待数据库关闭。
     * @param authentication 销毁时需要等待的认证任务
     */
    private fun closeVault(authentication: Job? = null) {
        vaultOwner.viewModelStore.clear()
        val previous = closing
        closing = cleanupScope.launch {
            authentication?.join()
            previous?.join()
            try {
                vault.close()
            } catch (failure: Exception) {
                Log.w("AppLock", "Vault close failed (${failure.javaClass.simpleName})")
            }
        }
    }

    /**
     * 发布页面并重置临时输入。
     * @param screen 目标界面
     * @param error 需要展示的错误
     */
    private fun updateScreen(screen: LockScreen, error: LockError? = null) {
        mutableState.value = AppLockState(screen, error = error, inputRevision = state.value.inputRevision + 1)
    }

    /**
     * 清除设置流程暂存的首次凭据。
     */
    private fun clearFirstInput() {
        firstInput?.fill('\u0000')
        firstInput = null
    }

    /**
     * 销毁活动会话时清除敏感内容。
     */
    override fun onCleared() {
        clearFirstInput()
        closeVault(operation)
    }

    private companion object {
        const val BackgroundTimeout = 30_000L
    }
}
