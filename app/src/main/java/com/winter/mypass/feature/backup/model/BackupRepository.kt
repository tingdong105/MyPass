package com.winter.mypass.feature.backup.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.OpenableColumns
import com.winter.mypass.feature.home.model.VaultBackup
import com.winter.mypass.feature.home.model.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 连接加密库、备份文件和用户选择的存储位置。
 * @author Tingdong
 * @date 2026-11-21
 */
internal class BackupRepository(context: Context, private val vault: VaultRepository) {
    private val configs = BackupConfigStore(context.applicationContext)
    private val resolver = context.contentResolver
    private val local = LocalBackupStorage(context)

    /** 读取本机上次验证通过的云配置。 */
    suspend fun destination(): BackupDestination? = configLock.withLock { configs.load() }

    /** 测试云目录访问。配置写入由保存操作负责。 */
    suspend fun verify(config: CloudBackupConfig) = CloudStorage.open(config).verify()

    /** 保存已通过连接测试的配置。 */
    suspend fun save(config: CloudBackupConfig): BackupDestination = configLock.withLock { configs.save(config) }

    /** 持久化清除云端连接，不发起远端请求。 */
    suspend fun clearDestination() = configLock.withLock { configs.clear() }

    /** 获取当前本机导出目录的标签。 */
    suspend fun exportDirectory(): String? = configLock.withLock {
        local.location()?.let { uri -> runCatching { local.directoryName(uri) }.getOrElse { uri.lastPathSegment.orEmpty() } }
    }

    /** 更新导出目录，传空时恢复系统下载目录。 */
    suspend fun setExportDirectory(uri: Uri?): String? = configLock.withLock { local.setLocation(uri) }

    /** 返回可在当前目的地还原的文件。 */
    suspend fun history(config: CloudBackupConfig): List<String> = CloudStorage.open(config).list()

    /** 上传完整快照后执行当前安装的历史保留策略。 */
    suspend fun upload(destination: BackupDestination, progress: TransferProgress): PublishResult =
        uploadLock.withLock {
            val snapshot = vault.backup()
            CloudStorage.open(destination.config).publish(
                destination.installation, snapshot.createdAt, BackupFile.encode(snapshot), progress,
            )
        }

    /** 下载文件并验证全部内容，返回待确认的快照。 */
    suspend fun download(config: CloudBackupConfig, name: String, progress: TransferProgress): VaultBackup =
        decode(CloudStorage.open(config).download(name, progress))

    /** 将快照导出到本机设置的目录。 */
    suspend fun export(progress: TransferProgress) {
        val bytes = BackupFile.encode(vault.backup())
        configLock.withLock { local.export(bytes, progress) }
    }

    /** 读取本地备份，先完成认证和关系校验，再交由用户确认。 */
    suspend fun read(uri: Uri, progress: TransferProgress): VaultBackup = decode(readDocument(uri, progress))

    /** 取消时立即释放调用方，并中止文档服务的查询、打开和流读取。 */
    private suspend fun readDocument(uri: Uri, progress: TransferProgress): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val opened = AtomicReference<InputStream?>()
            continuation.invokeOnCancellation {
                Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                    runCatching { opened.getAndSet(null)?.close() }
                    runCatching { signal.cancel() }
                }
            }
            // 文档提供方可能忽略取消，阻塞的调用不能拖住界面协程。
            Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                if (!continuation.isActive) return@dispatch
                val bytes = runCatching {
                    val size = documentSize(uri, signal)
                    if (size > BackupFile.Limit) throw BackupTooLarge()
                    if (!continuation.isActive) throw CancellationException()
                    val descriptor = resolver.openAssetFileDescriptor(uri, "r", signal) ?: throw IOException()
                    descriptor.use { file ->
                        file.createInputStream().use { stream ->
                            opened.set(stream)
                            try {
                                if (!continuation.isActive) throw CancellationException()
                                BackupFile.read(stream) { count ->
                                    if (!continuation.isActive) throw CancellationException()
                                    progress(count.toLong(), size)
                                }
                            } finally {
                                opened.compareAndSet(stream, null)
                            }
                        }
                    }
                }
                continuation.resumeWith(bytes)
            }
        }

    /** 提交已确认的还原，写入失败时由同一 Room 事务回滚。 */
    suspend fun restore(snapshot: VaultBackup) = vault.restore(snapshot)

    private fun decode(bytes: ByteArray): VaultBackup = try {
        BackupFile.decode(bytes)
    } catch (failure: BackupTooLarge) {
        throw failure
    } catch (failure: UnsupportedBackupVersion) {
        throw failure
    } catch (failure: Exception) {
        throw InvalidBackupFile(failure)
    }

    private fun documentSize(uri: Uri, signal: CancellationSignal): Long {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        return resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null, signal)?.use { cursor ->
            val size = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && size >= 0 && !cursor.isNull(size)) cursor.getLong(size) else -1L
        } ?: -1L
    }

    private companion object {
        // 同进程的多个窗口共享安装标识和保留份数。
        val configLock = Mutex()
        val uploadLock = Mutex()
    }
}

internal class InvalidBackupFile(cause: Exception) : IOException(cause)
