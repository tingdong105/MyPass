package com.winter.mypass.feature.backup.model

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

/**
 * 通过 WebDAV 枚举文件、创建备份目录并发布完整的临时文件。
 * @author Tingdong
 * @date 2026-11-21
 */
internal class WebDavCloudStorage(config: CloudBackupConfig) : CloudStorage(config) {
    private val authorization = Credentials.basic(config.account, config.secret, Charsets.UTF_8)

    private fun builder(url: HttpUrl) = Request.Builder().url(url).header("Authorization", authorization)

    override fun request(method: String, url: HttpUrl, bytes: ByteArray?, progress: TransferProgress): Request =
        builder(url).method(method, bytes?.let { body(it, progress) }).build()

    /** 仅配置或上传需要建目录，浏览历史执行只读请求。 */
    override suspend fun verify() {
        try {
            list()
        } catch (failure: CloudHttpError) {
            if (failure.status != 404 || parts.isEmpty()) throw failure
            createFolder()
            list()
        }
    }

    private suspend fun createFolder() {
        var parent = root
        for (part in parts) {
            parent = parent.newBuilder().addPathSegment(part).addPathSegment("").build()
            try {
                send(builder(parent).method("MKCOL", null).build()) {}
            } catch (failure: CloudHttpError) {
                if (failure.status != 405) throw failure
            }
        }
    }

    override suspend fun list(): List<String> {
        val properties = """<D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/></D:prop></D:propfind>"""
        val request = builder(folder).header("Depth", "1")
            .method("PROPFIND", properties.toRequestBody("application/xml; charset=utf-8".toMediaType())).build()
        return send(request) { response ->
            buildSet {
                xml(response, "multistatus") { node ->
                    if (node.depth == 2 && node.namespace == "DAV:" && node.name == "response") {
                        readFile(node)?.let(::add)
                    }
                }
            }.sortedDescending()
        }
    }

    /** 只接收当前目录中的普通文件，跳过目录与不可访问的资源。 */
    private fun readFile(node: XmlPullParser): String? {
        val depth = node.depth
        var url: HttpUrl? = null
        var collection = false
        var available = true
        while (true) {
            when (node.next()) {
                XmlPullParser.END_TAG -> if (node.depth == depth) break
                XmlPullParser.END_DOCUMENT -> throw java.io.IOException("Incomplete DAV response")
                XmlPullParser.START_TAG -> if (node.namespace == "DAV:") {
                    when {
                        node.name == "href" && node.depth == depth + 1 -> url = folder.resolve(node.nextText())
                        node.name == "collection" -> collection = true
                        node.name == "status" && node.depth == depth + 1 ->
                            available = node.nextText().substringAfter(' ').startsWith('2')
                    }
                }
            }
        }
        val resource = url ?: return null
        val name = resource.pathSegments.last()
        if (collection || !available || !safeName(name)) return null
        val expected = fileUrl(name)
        return name.takeIf { resource.scheme == expected.scheme && resource.host == expected.host &&
            resource.port == expected.port && resource.pathSegments == expected.pathSegments }
    }

    override suspend fun upload(name: String, bytes: ByteArray, progress: TransferProgress) {
        val target = fileUrl(name)
        val temp = folder.newBuilder().addPathSegment("$name.part").build()
        val put = request("PUT", temp, bytes, progress)
        try {
            try {
                send(put) {}
            } catch (failure: CloudHttpError) {
                if (parts.isEmpty() || failure.status !in setOf(404, 409)) throw failure
                createFolder()
                send(put) {}
            }
            val move = builder(temp).header("Destination", target.toString()).header("Overwrite", "F")
                .method("MOVE", null).build()
            send(move) {}
        } catch (failure: Exception) {
            // 取消时 MOVE 可能已经完成，清理仅针对本次上传的临时文件。
            withContext(NonCancellable) {
                try {
                    withTimeout(4_000) { send(builder(temp).delete().build()) {} }
                } catch (cleanup: Exception) {
                    if (cleanup !is CloudHttpError || cleanup.status != 404) failure.addSuppressed(cleanup)
                }
            }
            throw failure
        }
    }
}
