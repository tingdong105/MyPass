package com.winter.mypass.feature.backup.model

import okhttp3.HttpUrl
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** S3 通用桶的 Signature V4 与分页对象列表。 */
internal class S3CloudStorage(config: CloudBackupConfig) : CloudStorage(config) {
    private val signingRegion = config.signingRegion(root)
    private val expectedBucket = config.expectedCustomCosBucket(root)
    private val listingUrl = root.newBuilder().addQueryParameter("delimiter", "/")
        .addQueryParameter("prefix", parts.joinToString("/", postfix = if (parts.isEmpty()) "" else "/"))
        .build()

    override suspend fun verify() {
        if (expectedBucket == null) super.verify() else verifyObjectTarget()
    }

    /** 自定义域名可能重新绑定，写入或读取备份前再核对一次。 */
    override suspend fun verifyObjectTarget() {
        if (expectedBucket == null) return
        val probe = listingUrl.newBuilder().addQueryParameter("max-keys", "1").build()
        readListing(probe) {}
    }

    override suspend fun list(): List<String> {
        val prefix = listingUrl.queryParameter("prefix").orEmpty()
        val names = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var marker: String? = null
        do {
            val url = marker?.let { listingUrl.newBuilder().addQueryParameter("marker", it).build() } ?: listingUrl
            var truncated = false
            var next: String? = null
            var last: String? = null
            readListing(url) { node ->
                when {
                    node.depth == 2 && node.name == "IsTruncated" -> truncated = node.nextText().toBooleanStrict()
                    node.depth == 2 && node.name == "NextMarker" -> next = node.nextText()
                    node.depth == 3 && node.name == "Key" -> {
                        val key = node.nextText()
                        last = key
                        val name = key.removePrefix(prefix)
                        if (key.startsWith(prefix) && safeName(name)) names += name
                    }
                }
            }
            marker = if (truncated) requireNotNull(next ?: last) else null
            require(marker == null || visited.add(marker))
        } while (marker != null)
        return names.distinct().sortedDescending()
    }

    /** 校验列表响应里的真实桶名，包含空列表和分页响应。 */
    private suspend fun readListing(url: HttpUrl, onNode: (XmlPullParser) -> Unit) {
        send(request("GET", url, null, { _, _ -> })) { response ->
            var actualBucket: String? = null
            xml(response, "ListBucketResult") { node ->
                if (node.depth == 2 && node.name == "Name") actualBucket = node.nextText()
                else onNode(node)
            }
            if (expectedBucket != null && actualBucket != expectedBucket) throw CloudBucketMismatch()
        }
    }

    override fun request(method: String, url: HttpUrl, bytes: ByteArray?, progress: TransferProgress): Request {
        val stamp = clock.format(Instant.now())
        val day = stamp.take(8)
        val scope = "$day/$signingRegion/s3/aws4_request"
        val payload = sha256(bytes ?: byteArrayOf())
        val hostname = if (':' in url.host) "[${url.host}]" else url.host
        val host = if (url.port == HttpUrl.defaultPort(url.scheme)) hostname else "$hostname:${url.port}"
        val headers = "host:$host\nx-amz-content-sha256:$payload\nx-amz-date:$stamp\n"
        val signed = "host;x-amz-content-sha256;x-amz-date"
        val path = url.pathSegments.joinToString("/", prefix = "/") { encode(it) }
        val query = (0 until url.querySize).map { i ->
            encode(url.queryParameterName(i)) + "=" + encode(url.queryParameterValue(i).orEmpty())
        }.sorted().joinToString("&")
        val canonical = "$method\n$path\n$query\n$headers\n$signed\n$payload"
        val stringToSign = "AWS4-HMAC-SHA256\n$stamp\n$scope\n${sha256(canonical.toByteArray())}"
        var key = ("AWS4" + config.secret).toByteArray()
        for (part in listOf(day, signingRegion, "s3", "aws4_request")) {
            val next = hmac(key, part)
            key.fill(0)
            key = next
        }
        val signature = try { hex(hmac(key, stringToSign)) } finally { key.fill(0) }
        val wireUrl = url.newBuilder().encodedPath(path).encodedQuery(query.ifEmpty { null }).build()
        return Request.Builder().url(wireUrl).header("x-amz-date", stamp).header("x-amz-content-sha256", payload)
            .header("Authorization", "AWS4-HMAC-SHA256 Credential=${config.account}/$scope, SignedHeaders=$signed, Signature=$signature")
            .method(method, bytes?.let { body(it, progress) }).build()
    }

    companion object {
        private val clock = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).withZone(ZoneOffset.UTC)
        private val digits = "0123456789abcdef"
        private fun hex(data: ByteArray) = buildString(data.size * 2) {
            for (byte in data) {
                val value = byte.toInt() and 255
                append(digits[value ushr 4]); append(digits[value and 15])
            }
        }
        private fun sha256(data: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(data))
        private fun hmac(key: ByteArray, text: String) = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(text.toByteArray())
        }
        /** SigV4 的路径和查询均按 UTF-8 字节进行 RFC 3986 编码。 */
        private fun encode(value: String) = buildString {
            for (byte in value.toByteArray()) {
                val n = byte.toInt() and 255
                val c = n.toChar()
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.~") append(c)
                else { append('%'); append(digits[n ushr 4].uppercaseChar()); append(digits[n and 15].uppercaseChar()) }
            }
        }
    }
}
