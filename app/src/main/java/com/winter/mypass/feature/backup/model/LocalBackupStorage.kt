package com.winter.mypass.feature.backup.model

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * 管理备份目录授权，按应用生成的 .mp 文件名写入。
 * @author Tingdong
 * @date 2026-11-22
 */
internal class LocalBackupStorage(context: Context) {
    private val resolver = context.contentResolver
    private val preference = AtomicFile(File(context.noBackupFilesDir, "backup-export-location"))

    /** 加载本机选择的目录，空值表示使用系统下载目录。 */
    fun location(): Uri? = try {
        Uri.parse(preference.readFully().decodeToString())
    } catch (failure: FileNotFoundException) {
        if (preference.baseFile.exists()) throw failure
        null
    }

    /** 先取得新目录的持久权限，成功保存后释放旧目录的授权。 */
    fun setLocation(uri: Uri?): String? {
        val previous = location()
        val label = uri?.let(::directoryName)
        if (uri == null) {
            preference.delete()
            check(!preference.baseFile.exists())
        } else {
            try {
                resolver.takePersistableUriPermission(uri, DirectoryGrant)
                if (uri == previous) return label
                val output = preference.startWrite()
                try {
                    output.write(uri.toString().toByteArray())
                    preference.finishWrite(output)
                } catch (failure: Exception) {
                    preference.failWrite(output)
                    throw failure
                }
            } catch (failure: Exception) {
                if (uri != previous) release(uri)
                throw failure
            }
        }
        previous?.let(::release)
        return label
    }

    /** 显示目录路径或文档服务提供的目录名称。 */
    fun directoryName(tree: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val name = displayName(uri) ?: throw IOException("Unavailable backup directory")
        return if (tree.authority == "com.android.externalstorage.documents") {
            id.substringAfter(':').ifEmpty { name }
        } else name
    }

    /** 写入已配置目录，失败时回收本次创建的文件。 */
    suspend fun export(bytes: ByteArray, progress: TransferProgress) {
        val time = LocalDateTime.now().format(FileTime)
        val name = "MyPass-$time-${UUID.randomUUID().toString().take(8)}.mp"
        val tree = location()
        if (tree != null) {
            exportToTree(tree, name, bytes, progress)
        } else if (Build.VERSION.SDK_INT >= 29) {
            exportToDownloads(name, bytes, progress)
        } else {
            exportToLegacyDownloads(name, bytes, progress)
        }
    }

    @RequiresApi(29)
    private suspend fun exportToDownloads(name: String, bytes: ByteArray, progress: TransferProgress) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MpMimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, DownloadDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Cannot create download")
        try {
            if (!hasMpName(uri)) {
                val corrected = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                resolver.update(uri, corrected, null, null)
            }
            requireMpFile(uri)
            writeUri(uri, bytes, progress)
            val completed = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, completed, null, null) != 1) throw IOException("Cannot publish download")
        } catch (failure: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }

    private suspend fun exportToTree(tree: Uri, name: String, bytes: ByteArray, progress: TransferProgress) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var uri = DocumentsContract.createDocument(resolver, parent, MpMimeType, name)
            ?: throw IOException("Cannot create backup document")
        try {
            if (!hasMpName(uri)) {
                uri = try {
                    DocumentsContract.renameDocument(resolver, uri, name) ?: throw BackupFilenameError()
                } catch (failure: Exception) {
                    throw BackupFilenameError(failure)
                }
            }
            requireMpFile(uri)
            writeUri(uri, bytes, progress)
        } catch (failure: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun exportToLegacyDownloads(name: String, bytes: ByteArray, progress: TransferProgress) {
        val parent = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPass")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create Downloads/MyPass")
        val temporary = File.createTempFile(".mypass-", ".part", parent)
        try {
            temporary.outputStream().use { write(it, bytes, progress) }
            Files.move(temporary.toPath(), File(parent, name).toPath())
        } finally {
            temporary.delete()
        }
    }

    private suspend fun writeUri(uri: Uri, bytes: ByteArray, progress: TransferProgress) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write backup")
        output.use { write(it, bytes, progress) }
    }

    private suspend fun write(output: OutputStream, bytes: ByteArray, progress: TransferProgress) {
        val context = currentCoroutineContext()
        var offset = 0
        while (offset < bytes.size) {
            context.ensureActive()
            val count = minOf(32 * 1024, bytes.size - offset)
            output.write(bytes, offset, count)
            offset += count
            progress(offset.toLong(), bytes.size.toLong())
        }
        context.ensureActive()
    }

    private fun requireMpFile(uri: Uri) {
        if (!hasMpName(uri)) throw BackupFilenameError()
    }

    private fun hasMpName(uri: Uri): Boolean = displayName(uri)?.endsWith(".mp", ignoreCase = true) == true

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun release(uri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(uri, DirectoryGrant) }
    }

    companion object {
        val DownloadDirectory = "${Environment.DIRECTORY_DOWNLOADS}/MyPass/"
        private val MpMimeType get() = MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp") ?: "application/octet-stream"
        private val FileTime = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.US)
        private const val DirectoryGrant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

internal class BackupFilenameError(cause: Exception? = null) : IOException(cause)
