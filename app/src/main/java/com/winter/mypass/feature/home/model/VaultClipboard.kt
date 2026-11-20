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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

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
        val stamp = if (sensitive) PendingClip(UUID.randomUUID().toString(), SystemClock.elapsedRealtime() + Timeout) else null
        if (stamp != null) {
            clip.description.extras = PersistableBundle().apply {
                putString(ClipToken, stamp.id)
                putBoolean(SensitiveClipKey, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        pending = stamp
        timer?.cancel()
        timer = if (stamp == null) null else scope.launch {
            delay((stamp.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            clearExpired()
        }
        if (showToast) {
            copyToast?.cancel()
            copyToast = Toast.makeText(context, R.string.vault_copied, Toast.LENGTH_SHORT).also(Toast::show)
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
        if (!focused || SystemClock.elapsedRealtime() < clip.expiresAt) return
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
    private data class PendingClip(val id: String, val expiresAt: Long)

    private companion object {
        const val Timeout = 30_000L
        const val ClipToken = "com.winter.mypass.clip.token"
        const val SensitiveClipKey = "android.content.extra.IS_SENSITIVE"
    }
}
