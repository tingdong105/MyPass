package com.winter.mypass.feature.backup.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resumeWithException

internal enum class PublishResult { Complete, CleanupFailed }

internal typealias TransferProgress = (Long, Long) -> Unit
internal class CloudHttpError(val status: Int) : IOException()
internal class CloudBucketMismatch : IOException()
internal class CloudTlsError(val host: String, val error: SSLException) : IOException(error)

/**
 * 云端文件操作与可取消的 HTTP 传输。
 * @author Tingdong
 * @date 2026-11-21
 */
internal abstract class CloudStorage(protected val config: CloudBackupConfig) {
    protected val root = config.root()
    protected val parts = config.segments()
    protected val folder = root.newBuilder().apply { parts.forEach { addPathSegment(it) }; addPathSegment("") }.build()

    abstract suspend fun list(): List<String>

    /** 确认目的地可以列举；WebDAV 可在首次配置时创建目录。 */
    open suspend fun verify() { list() }

    protected abstract fun request(method: String, url: HttpUrl, bytes: ByteArray?, progress: TransferProgress): Request

    /** 检查不能单凭请求域名识别的目标。 */
    protected open suspend fun verifyObjectTarget() = Unit

    /** 完成上传后才允许调用方执行保留策略。 */
    open suspend fun upload(name: String, bytes: ByteArray, progress: TransferProgress) {
        send(request("PUT", fileUrl(name), bytes, progress)) {}
    }

    /** 上传成功后清理过期版本，分别报告两个阶段的结果。 */
    suspend fun publish(installation: String, time: Long, bytes: ByteArray, progress: TransferProgress): PublishResult {
        val name = BackupNames.create(installation, time)
        verifyObjectTarget()
        upload(name, bytes, progress)
        return try {
            val names = awaitPublished(name) ?: return PublishResult.CleanupFailed
            val expired = BackupNames.expired(names, installation, name, config.retain)
            expired.forEach { delete(it) }
            PublishResult.Complete
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PublishResult.CleanupFailed
        }
    }

    /** 只在新备份已出现在同一目标的列表中后才允许删除旧文件。 */
    private suspend fun awaitPublished(name: String): List<String>? {
        val initial = list()
        if (name in initial) return initial
        return withTimeoutOrNull(8_000) {
            for (waitMillis in listOf(300L, 900L, 1_800L)) {
                delay(waitMillis)
                val refreshed = list()
                if (name in refreshed) return@withTimeoutOrNull refreshed
            }
            null
        }
    }

    suspend fun download(name: String, progress: TransferProgress): ByteArray {
        verifyObjectTarget()
        return send(request("GET", fileUrl(name), null, progress)) { response ->
            val body = requireNotNull(response.body)
            val total = body.contentLength()
            if (total > BackupFile.Limit) throw BackupTooLarge()
            BackupFile.read(body.byteStream()) { progress(it.toLong(), total) }
        }
    }

    private suspend fun delete(name: String) {
        send(request("DELETE", fileUrl(name), null, { _, _ -> })) {}
    }

    protected fun fileUrl(name: String): HttpUrl {
        require(safeName(name))
        return folder.newBuilder().addPathSegment(name).build()
    }

    protected fun safeName(name: String) = name.endsWith(".mp", ignoreCase = true) &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }

    protected fun body(bytes: ByteArray, progress: TransferProgress) = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            var sent = 0
            while (sent < bytes.size) {
                val count = minOf(32 * 1024, bytes.size - sent)
                sink.write(bytes, sent, count)
                sent += count
                progress(sent.toLong(), contentLength())
            }
        }
    }

    /** HTTP 回调持有响应体直到解析完成，取消协程时立即断开请求。 */
    protected suspend fun <T> send(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { cont ->
        val call = (if (request.url.isHttps) client else localClient).newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val failure = if (e is SSLException) CloudTlsError(request.url.host, e) else e
                cont.resumeWithException(failure)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw CloudHttpError(it.code)
                        read(it)
                    }
                }
                cont.resumeWith(result)
            }
        })
    }

    /** 逐节点读取有限大小的目录，拒绝文档类型声明。 */
    protected fun xml(response: Response, rootName: String, start: (XmlPullParser) -> Unit) {
        val bytes = BackupFile.read(requireNotNull(response.body).byteStream(), 4 * 1024 * 1024)
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        }
        parser.setInput(bytes.inputStream(), null)
        var foundRoot = false
        while (true) when (parser.nextToken()) {
            XmlPullParser.END_DOCUMENT -> { require(foundRoot); return }
            XmlPullParser.DOCDECL -> throw IOException("DTD")
            XmlPullParser.START_TAG -> {
                if (parser.depth == 1) { require(parser.name == rootName); foundRoot = true }
                start(parser)
            }
        }
    }

    companion object {
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(120, TimeUnit.SECONDS).build()
        private val localClient = client.newBuilder().proxy(Proxy.NO_PROXY).dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !CloudBackupConfig.isPrivateLiteral(it.hostAddress.orEmpty()) }) {
                    throw UnknownHostException("WebDAV HTTP requires a local address")
                }
                return addresses
            }
        }).build()

        fun open(config: CloudBackupConfig): CloudStorage =
            if (config.provider == Provider.WebDAV) WebDavCloudStorage(config) else S3CloudStorage(config)
    }
}
