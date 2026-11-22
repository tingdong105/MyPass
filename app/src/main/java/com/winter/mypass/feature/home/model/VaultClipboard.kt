package com.winter.mypass.feature.home.model

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.winter.mypass.R
import com.winter.mypass.core.utils.MMKVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 敏感剪贴板的自动清除时限，零表示不自动清除。
 * @author Tingdong
 * @date 2026-11-22
 */
internal enum class ClipboardClearTimeout(val milliseconds: Long) {
    Never(0L),
    Seconds30(30_000L),
    Minute1(60_000L),
    Minutes5(300_000L),
}

/**
 * 在主线程跟踪本应用复制的敏感剪贴板条目。
 * @author Tingdong
 * @date 2026-11-19
 */
internal class VaultClipboard(private val context: Context, private val scope: CoroutineScope) {
    private val clipboard = requireNotNull(context.getSystemService(ClipboardManager::class.java))
    private var focused = false
    private var pending: PendingClip? = null
    private var timer: Job? = null
    private var copyToast: Toast? = null
    private var clearTimeout = ClipboardClearTimeout.Seconds30

    /**
     * 恢复清除时限，缺失或无效的值回退到三十秒。
     * @return 已保存或默认的时限
     */
    suspend fun loadClearTimeout(): ClipboardClearTimeout {
        val stored = withContext(Dispatchers.IO) {
            MMKVUtils.getLong(TimeoutKey, ClipboardClearTimeout.Seconds30.milliseconds)
        }
        clearTimeout = ClipboardClearTimeout.entries.firstOrNull { it.milliseconds == stored }
            ?: ClipboardClearTimeout.Seconds30
        scheduleClear()
        return clearTimeout
    }

    /**
     * 保存清除时限，并按原复制时间重新计算待清除条目的期限。
     * @param timeout 用户选择的时限
     * @throws IllegalStateException 写入失败
     */
    suspend fun setClearTimeout(timeout: ClipboardClearTimeout) {
        withContext(Dispatchers.IO) {
            check(MMKVUtils.putLong(TimeoutKey, timeout.milliseconds))
        }
        clearTimeout = timeout
        scheduleClear()
    }

    // Compose 的粘贴菜单要求剪贴板实现 AndroidClipboard 才能查询内容类型。
    @SuppressLint("VisibleForTests")
    val passwordEditorClipboard: Clipboard = object : AndroidClipboard {
        override val clipboardManager: ClipboardManager get() = clipboard

        /**
         * 读取系统剪贴板，供密码框粘贴。
         * @return 当前剪贴板条目
         */
        override suspend fun getClipEntry(): ClipEntry? = clipboard.primaryClip?.let(::ClipEntry)

        /**
         * 将密码框复制或剪切产生的条目标记为敏感。
         * @param clipEntry 待写入条目，为空时清除剪贴板
         */
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            if (clipEntry == null) {
                clipboard.clearPrimaryClip()
                pending = null
                timer?.cancel()
                timer = null
            } else {
                copyClip(clipEntry.clipData, sensitive = true, showToast = false)
            }
        }
    }

    /**
     * 复制文本，敏感条目使用独立标识和过期计时。
     * @param text 待复制内容
     * @param sensitive 内容是否为密码
     * @param showToast 是否显示复制反馈
     */
    fun copy(text: String, sensitive: Boolean, showToast: Boolean = true) {
        copyClip(ClipData.newPlainText("", text), sensitive, showToast)
    }

    /**
     * 写入剪贴板并跟踪敏感条目的过期时间。
     * @param clip 待写入条目
     * @param sensitive 是否添加敏感标记
     * @param showToast 是否显示复制反馈
     */
    private fun copyClip(clip: ClipData, sensitive: Boolean, showToast: Boolean) {
        val stamp = if (sensitive) PendingClip(UUID.randomUUID().toString(), SystemClock.elapsedRealtime()) else null
        if (stamp != null) {
            clip.description.extras = PersistableBundle().apply {
                putString(ClipToken, stamp.id)
                putBoolean(SensitiveClipKey, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        pending = stamp
        scheduleClear()
        if (showToast) {
            copyToast?.cancel()
            copyToast = Toast.makeText(context, R.string.vault_copied, Toast.LENGTH_SHORT).also(Toast::show)
        }
    }

    /**
     * 根据当前时限重建清除任务，永不清除时取消计时。
     */
    private fun scheduleClear() {
        timer?.cancel()
        timer = null
        val clip = pending ?: return
        if (clearTimeout == ClipboardClearTimeout.Never) return
        timer = scope.launch {
            delay((clip.copiedAt + clearTimeout.milliseconds - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            clearExpired()
        }
    }

    /**
     * 恢复窗口焦点时检查在后台到期的条目。
     * @param hasFocus 当前窗口是否有焦点
     */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        focused = hasFocus
        if (hasFocus) clearExpired()
    }

    /**
     * 到期后仅清除仍带有本次复制标识的条目。
     */
    private fun clearExpired() {
        val clip = pending ?: return
        if (!focused || clearTimeout == ClipboardClearTimeout.Never ||
            SystemClock.elapsedRealtime() - clip.copiedAt < clearTimeout.milliseconds
        ) return
        if (clipboard.primaryClipDescription?.extras?.getString(ClipToken) == clip.id) {
            clipboard.clearPrimaryClip()
        }
        pending = null
    }

    /**
     * 只保存标识和期限。
     * @author Tingdong
     * @date 2026-11-19
     */
    private data class PendingClip(val id: String, val copiedAt: Long)

    private companion object {
        const val TimeoutKey = "vault.clipboard_clear_timeout"
        const val ClipToken = "com.winter.mypass.clip.token"
        const val SensitiveClipKey = "android.content.extra.IS_SENSITIVE"
    }
}
