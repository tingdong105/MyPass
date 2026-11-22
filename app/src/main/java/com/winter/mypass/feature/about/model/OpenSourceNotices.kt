package com.winter.mypass.feature.about.model

import android.content.res.Resources
import com.winter.mypass.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 构建依赖对应的开源声明。
 * @author Tingdong
 * @date 2026-11-22
 */
internal data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val website: String,
    val licenses: List<OpenSourceLicense>,
)

/**
 * 原始许可名称、链接和离线全文。
 * @author Tingdong
 * @date 2026-11-22
 */
internal data class OpenSourceLicense(val name: String, val url: String, val content: String)

/**
 * 在后台读取构建时生成的依赖与许可。
 * @param resources 应用资源
 * @return 按名称排序的开源依赖
 * @throws org.json.JSONException 生成资源结构不完整
 * @throws java.io.IOException 资源读取失败
 */
internal suspend fun loadOpenSourceNotices(resources: Resources): List<OpenSourceLibrary> = withContext(Dispatchers.IO) {
    val root = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { JSONObject(it.readText()) }
    val licenses = root.getJSONObject("licenses")
    val libraries = root.getJSONArray("libraries")
    List(libraries.length()) { index ->
        val library = libraries.getJSONObject(index)
        val id = library.getString("uniqueId")
        val licenseIds = library.optJSONArray("licenses")
        val developers = library.optJSONArray("developers")
        val authors = if (developers == null) emptyList() else List(developers.length()) {
            developers.getJSONObject(it).text("name")
        }.filter(String::isNotBlank)
        OpenSourceLibrary(
            id = id,
            name = library.text("name").ifBlank { id },
            version = library.text("artifactVersion"),
            author = authors.joinToString().ifBlank { library.optJSONObject("organization")?.text("name").orEmpty() },
            website = library.text("website").ifBlank { library.optJSONObject("scm")?.text("url").orEmpty() },
            licenses = if (licenseIds == null) emptyList() else List(licenseIds.length()) {
                val license = licenses.getJSONObject(licenseIds.getString(it))
                OpenSourceLicense(license.getString("name"), license.text("url"), license.text("content"))
            },
        )
    }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
}

/**
 * 将 JSON 的空值转换为空字符串。
 * @param key 字段名
 * @return 非空字符串
 */
private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
