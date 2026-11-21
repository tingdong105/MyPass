package com.winter.mypass.feature.backup.viewmodel

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winter.mypass.R
import com.winter.mypass.feature.backup.model.BackupDestination
import com.winter.mypass.feature.backup.model.BackupFilenameError
import com.winter.mypass.feature.backup.model.BackupRepository
import com.winter.mypass.feature.backup.model.BackupTooLarge
import com.winter.mypass.feature.backup.model.CloudBackupConfig
import com.winter.mypass.feature.backup.model.CloudBucketMismatch
import com.winter.mypass.feature.backup.model.CloudHttpError
import com.winter.mypass.feature.backup.model.CloudTlsError
import com.winter.mypass.feature.backup.model.ConfigField
import com.winter.mypass.feature.backup.model.InvalidBackupFile
import com.winter.mypass.feature.backup.model.InvalidConfig
import com.winter.mypass.feature.backup.model.Provider
import com.winter.mypass.feature.backup.model.PublishResult
import com.winter.mypass.feature.backup.model.TransferProgress
import com.winter.mypass.feature.backup.model.UnsupportedBackupVersion
import com.winter.mypass.feature.home.model.VaultBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

internal enum class BackupPage { Overview, Configuration, History }

internal enum class BackupTask(@param:StringRes val label: Int) {
    Load(R.string.backup_loading), Verify(R.string.backup_verifying), Save(R.string.backup_saving),
    Clear(R.string.backup_clearing), SetDirectory(R.string.backup_saving), List(R.string.backup_loading),
    Export(R.string.backup_exporting), Upload(R.string.backup_uploading),
    Read(R.string.backup_reading), Download(R.string.backup_downloading), Restore(R.string.backup_restoring),
}

internal data class RestoreSummary(val time: Long, val books: Int, val passwords: Int)
internal data class BackupNotice(
    @param:StringRes val text: Int,
    val error: Boolean = false,
    val status: Int? = null,
    @param:StringRes val field: Int? = null,
    val host: String? = null,
)

internal data class BackupState(
    val page: BackupPage = BackupPage.Overview,
    val destination: BackupDestination? = null,
    val exportDirectory: String? = null,
    val draft: CloudBackupConfig? = null,
    val fieldError: ConfigField? = null,
    val tested: Boolean = false,
    val history: List<String>? = null,
    val preview: RestoreSummary? = null,
    val task: BackupTask? = null,
    val percent: Int? = null,
    val notice: BackupNotice? = null,
    val confirmHttp: Boolean = false,
    val confirmDiscard: Boolean = false,
    val requestPermission: Boolean = false,
) {
    val idle get() = task == null
}

/**
 * 在当前解锁会话内管理页面、配置草稿和待确认的还原。
 * @author Tingdong
 * @date 2026-11-21
 */
internal class CloudBackupViewModel(
    private val repository: BackupRepository,
    private val onRestored: () -> Unit,
) : ViewModel() {
    private val mutable = MutableStateFlow(BackupState())
    val state = mutable.asStateFlow()
    private val current get() = mutable.value
    private var pendingRestore: VaultBackup? = null
    private var originalDraft: CloudBackupConfig? = null
    private var permission: CompletableDeferred<Boolean>? = null
    private var running: Job? = null
    private val progress: TransferProgress = { done, total ->
        val value = if (total > 0) (done * 100 / total).coerceIn(0, 100).toInt() else null
        mutable.update { if (it.percent == value) it else it.copy(percent = value) }
    }

    init {
        perform(BackupTask.Load, {
            val directory = repository.exportDirectory()
            mutable.update { it.copy(exportDirectory = directory) }
            repository.destination()
        }) { saved ->
            mutable.update { it.copy(destination = saved) }
        }
    }

    /** 首次打开空白表单，修改时带入已保存的连接。 */
    fun editDestination() {
        if (!current.idle) return
        val saved = current.destination?.config?.takeIf { it.provider in Provider.available }
        val draft = when {
            saved == null -> CloudBackupConfig()
            saved.endpoint.isBlank() -> saved.copy(endpoint = saved.address().toString())
            else -> saved
        }
        originalDraft = draft
        mutable.update { it.copy(page = BackupPage.Configuration, draft = draft,
            fieldError = null, tested = false, notice = null) }
    }

    /** 切换服务时清空连接字段，避免带入另一个服务的凭据。 */
    fun select(provider: Provider) {
        val draft = current.draft ?: return
        if (provider !in Provider.available || provider == draft.provider) return
        change(CloudBackupConfig(provider = provider, directory = draft.directory, retain = draft.retain))
    }

    fun change(draft: CloudBackupConfig) {
        if (current.idle && current.page == BackupPage.Configuration && draft != current.draft) {
            mutable.update { it.copy(draft = draft, fieldError = null, tested = false, notice = null) }
        }
    }

    /** 测试当前草稿，保留表单和已保存的目的地。 */
    fun testConnection(lanGranted: Boolean, allowHttp: Boolean = false) {
        val draft = current.draft ?: return
        if (!current.idle) return
        val address = try {
            if (draft.endpoint.isBlank()) throw InvalidConfig(ConfigField.Endpoint)
            draft.segments()
            draft.root()
        } catch (failure: InvalidConfig) {
            invalid(failure.field, draft.provider)
            return
        } catch (_: IllegalArgumentException) {
            invalid(ConfigField.Endpoint, draft.provider)
            return
        }
        if (!address.isHttps && !allowHttp && current.destination?.config?.endpoint != draft.endpoint) {
            mutable.update { it.copy(confirmHttp = true) }
            return
        }
        mutable.update { it.copy(confirmHttp = false, tested = false) }
        perform(BackupTask.Verify, {
            authorize(draft, lanGranted)
            repository.verify(draft)
        }) {
            mutable.update { it.copy(tested = true, notice = BackupNotice(R.string.backup_test_success)) }
        }
    }

    /** 保存刚刚测试通过的表单，无需再次连接。 */
    fun save() {
        val draft = current.draft ?: return
        if (!current.tested || current.page != BackupPage.Configuration) return
        perform(BackupTask.Save, { repository.save(draft) }) { saved ->
            originalDraft = null
            mutable.update { it.copy(page = BackupPage.Overview, destination = saved, draft = null,
                history = null, tested = false, notice = BackupNotice(R.string.backup_saved)) }
        }
    }

    /** 清空云配置后回到概览，保留本机文件和远端备份。 */
    fun clearDestination() {
        if (current.page != BackupPage.Configuration) return
        perform(BackupTask.Clear, repository::clearDestination) {
            originalDraft = null
            mutable.update { it.copy(page = BackupPage.Overview, destination = null, draft = null,
                fieldError = null, history = null, tested = false, confirmHttp = false,
                notice = BackupNotice(R.string.backup_config_cleared)) }
        }
    }

    private fun invalid(field: ConfigField, provider: Provider) {
        val label = when (field) {
            ConfigField.Endpoint -> R.string.backup_endpoint
            ConfigField.Region -> R.string.backup_region
            ConfigField.Bucket -> if (provider == Provider.COS) R.string.backup_cos_bucket else R.string.backup_bucket
            ConfigField.Directory -> R.string.backup_directory
            ConfigField.Account -> if (provider == Provider.WebDAV) R.string.backup_user else R.string.backup_access
            ConfigField.Secret -> if (provider == Provider.WebDAV) R.string.backup_password else R.string.backup_secret
            ConfigField.Retention -> R.string.backup_retain
        }
        mutable.update { it.copy(fieldError = field, tested = false,
            notice = BackupNotice(R.string.backup_field_error, error = true, field = label)) }
    }

    /** 界面展示 Toast 后消费反馈，避免旋转重播。 */
    fun consumeNotice(notice: BackupNotice) {
        mutable.update { if (it.notice === notice) it.copy(notice = null) else it }
    }

    fun dismissHttp() = mutable.update { it.copy(confirmHttp = false) }

    /** 在独立历史页重新获取当前目录的还原点。 */
    fun history(lanGranted: Boolean) {
        val config = current.destination?.config ?: return
        if (!current.idle) return
        mutable.update { it.copy(page = BackupPage.History) }
        perform(BackupTask.List, {
            authorize(config, lanGranted)
            repository.history(config)
        }) { names ->
            val notice = if (names.isEmpty()) BackupNotice(R.string.backup_empty) else null
            mutable.update { it.copy(history = names, notice = notice) }
        }
    }

    fun upload(lanGranted: Boolean) {
        val saved = current.destination ?: return
        perform(BackupTask.Upload, {
            authorize(saved.config, lanGranted)
            repository.upload(saved, progress)
        }) { result ->
            val notice = when (result) {
                PublishResult.Complete -> R.string.backup_uploaded
                PublishResult.CleanupFailed -> R.string.backup_cleanup_failed
            }
            mutable.update { it.copy(history = null, notice = BackupNotice(notice)) }
        }
    }

    fun download(name: String, lanGranted: Boolean) {
        val config = current.destination?.config ?: return
        perform(BackupTask.Download, {
            authorize(config, lanGranted)
            repository.download(config, name, progress)
        }, ::preview)
    }

    /** 文档选择器可跨应用锁返回，文件读写始终属于新解锁会话。 */
    fun document(uri: Uri) = perform(BackupTask.Read, { repository.read(uri, progress) }, ::preview)

    /** 保存目录授权，或在用户恢复默认时释放自定义目录。 */
    fun setExportDirectory(uri: Uri?) = perform(BackupTask.SetDirectory, { repository.setExportDirectory(uri) }) { label ->
        mutable.update { it.copy(exportDirectory = label, notice = BackupNotice(R.string.backup_location_saved)) }
    }

    /** 使用当前导出目录生成 .mp 文件。 */
    fun export() = perform(BackupTask.Export, { repository.export(progress) }) {
        mutable.update { it.copy(notice = BackupNotice(R.string.backup_exported)) }
    }

    fun storagePermissionDenied() {
        mutable.update { it.copy(notice = BackupNotice(R.string.backup_storage_denied, error = true)) }
    }

    private fun preview(snapshot: VaultBackup) {
        pendingRestore = snapshot
        mutable.update { it.copy(preview = RestoreSummary(snapshot.createdAt, snapshot.books.size, snapshot.passwords.size)) }
    }

    fun dismissPreview() {
        pendingRestore = null
        mutable.update { it.copy(preview = null) }
    }

    fun restore() {
        val snapshot = pendingRestore ?: return
        if (!current.idle) return
        dismissPreview()
        perform(BackupTask.Restore, { repository.restore(snapshot) }) {
            onRestored()
            mutable.update { it.copy(page = BackupPage.Overview, notice = BackupNotice(R.string.backup_restored)) }
        }
    }

    /** 返回上一级并在离开表单时释放未保存的凭据。 */
    fun back(): Boolean {
        if (!current.idle) return true
        if (current.preview != null) {
            dismissPreview()
            return true
        }
        if (current.confirmHttp) {
            dismissHttp()
            return true
        }
        when (current.page) {
            BackupPage.Overview -> {
                mutable.update { it.copy(notice = null) }
                return false
            }
            BackupPage.Configuration -> {
                if (current.confirmDiscard) dismissDiscard()
                else if (current.draft != originalDraft) mutable.update { it.copy(confirmDiscard = true) }
                else discardDraft()
            }
            BackupPage.History -> mutable.update {
                it.copy(page = BackupPage.Overview, draft = null, fieldError = null, notice = null)
            }
        }
        return true
    }

    /** 关闭退出确认，继续编辑当前草稿。 */
    fun dismissDiscard() = mutable.update { it.copy(confirmDiscard = false) }

    /** 丢弃草稿并返回备份概览。 */
    fun discardDraft() {
        if (!current.idle || current.page != BackupPage.Configuration) return
        originalDraft = null
        mutable.update { it.copy(page = BackupPage.Overview, draft = null, fieldError = null, tested = false,
            confirmDiscard = false, notice = null) }
    }

    fun cancelRead() {
        when (current.task) {
            BackupTask.Read, BackupTask.Download -> running?.cancel()
            else -> Unit
        }
    }

    fun permissionResult(granted: Boolean) { permission?.complete(granted) }

    /** 仅访问局域网时挂起当前命令，权限回调继续同一次操作。 */
    private suspend fun authorize(config: CloudBackupConfig, granted: Boolean) {
        if (granted) return
        val address = config.root()
        val host = address.host
        val local = !address.isHttps || host.endsWith(".local") || '.' !in host ||
            InetAddress.getAllByName(host).any { CloudBackupConfig.isPrivateLiteral(it.hostAddress.orEmpty()) }
        if (!local) return
        withContext(Dispatchers.Main.immediate) {
            val result = CompletableDeferred<Boolean>()
            permission = result
            mutable.update { it.copy(requestPermission = true) }
            try {
                if (!result.await()) throw LanPermissionDenied()
            } finally {
                permission = null
                mutable.update { it.copy(requestPermission = false) }
            }
        }
    }

    /** 工作在线程池执行，完成、取消和错误统一在主线程发布。 */
    private fun <T> perform(task: BackupTask, work: suspend () -> T, success: (T) -> Unit) {
        if (!current.idle) return
        mutable.update { it.copy(task = task, percent = null, notice = null) }
        running = viewModelScope.launch {
            try {
                success(withContext(Dispatchers.IO) { work() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(notice = failure.notice()) }
            } finally {
                mutable.update { it.copy(task = null, percent = null) }
            }
        }
    }

    private fun Exception.notice(): BackupNotice {
        val message = when (this) {
            is CloudHttpError -> when (status) {
                401, 403 -> R.string.backup_auth_failed
                404 -> R.string.backup_not_found
                else -> R.string.backup_http_error
            }
            is CloudBucketMismatch -> R.string.backup_cos_bucket_mismatch
            is LanPermissionDenied -> R.string.backup_lan_denied
            is UnknownHostException -> R.string.backup_host_failed
            is CloudTlsError -> if (error is SSLPeerUnverifiedException) R.string.backup_tls_hostname else R.string.backup_tls_host
            is SSLException -> R.string.backup_tls_failed
            is SocketTimeoutException -> R.string.backup_timeout
            is UnsupportedBackupVersion -> R.string.backup_new_version
            is BackupTooLarge -> R.string.backup_size_limit
            is InvalidBackupFile -> R.string.backup_invalid_file
            is BackupFilenameError -> R.string.backup_invalid_filename
            else -> R.string.backup_io_error
        }
        return BackupNotice(message, error = true, status = (this as? CloudHttpError)?.status, host = (this as? CloudTlsError)?.host)
    }

    override fun onCleared() { pendingRestore = null }

    private class LanPermissionDenied : Exception()
}
