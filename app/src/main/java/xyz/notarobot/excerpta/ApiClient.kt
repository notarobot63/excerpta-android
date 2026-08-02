package xyz.notarobot.excerpta

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ApiClient {
    /**
     * Résultat d'un appel réseau. Le message est désigné par une ressource et
     * non par du texte : la couche réseau n'a pas de Context et n'a pas à
     * connaître la langue de l'interface. C'est l'appelant qui résout, via
     * [text].
     */
    data class Result(
        val success: Boolean,
        @StringRes val messageRes: Int,
        val messageArg: String? = null,
        val isNetworkError: Boolean = false,
    )

    data class TagInfo(val name: String, val count: Int)

    data class MeInfo(val tagsEnabled: Boolean, val foldersEnabled: Boolean)

    data class MetaInfo(val title: String, val description: String)

    data class GroupItem(
        val id: Int,
        val name: String,
        val parentId: Int?,
        val count: Int,
        val depth: Int,
    )

    data class LinkItem(
        val id: Int,
        val url: String,
        val title: String,
        val description: String,
        val faviconUrl: String,
        val thumbnailUrl: String,
        val tags: List<String>,
        val createdAt: String,
        val isPublic: Boolean = false,
        val note: String = "",
        val archivedUrl: String? = null,
        val archiveStatus: String? = null,
        val isBroken: Boolean = false,
        val checkStatus: Int? = null,
        val hasReader: Boolean = false,
    )

    data class ReaderContent(
        val title: String,
        val html: String,
        val extractedAt: String?,
    )

    data class LinksPage(
        val links: List<LinkItem>,
        val total: Int,
        val page: Int,
        val totalPages: Int,
    )

    private fun openGet(url: String, apiKey: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-API-Key", apiKey)
            connectTimeout = 10_000
            readTimeout = 10_000
        }

    private fun openRequest(
        url: String,
        apiKey: String,
        method: String,
        withBody: Boolean = false,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("X-API-Key", apiKey)
        if (withBody) {
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        connectTimeout = 10_000
        readTimeout = 10_000
    }

    suspend fun ping(serverUrl: String, apiKey: String): Result = withContext(Dispatchers.IO) {
        val conn = openGet("$serverUrl/api/v1/me", apiKey)
        try {
            val code = conn.responseCode
            if (code == 200) {
                val name = JSONObject(conn.inputStream.use { it.bufferedReader().readText() }).optString("name", "")
                Result(true, R.string.connected_as, name)
            } else {
                conn.errorStream?.use { it.readBytes() }  // drain pour libérer la socket keep-alive
                Result(false, R.string.ping_failed, code.toString())
            }
        } catch (e: Exception) {
            Result(false, R.string.server_unreachable, e.message ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** Préférences d'organisation du compte (voir Paramètres → Organization côté web). */
    suspend fun fetchMe(serverUrl: String, apiKey: String): MeInfo? =
        withContext(Dispatchers.IO) {
            val conn = openGet("$serverUrl/api/v1/me", apiKey)
            try {
                if (conn.responseCode != 200) return@withContext null
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                MeInfo(
                    tagsEnabled = o.optBoolean("tags_enabled", true),
                    foldersEnabled = o.optBoolean("folders_enabled", true),
                )
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    suspend fun fetchTags(serverUrl: String, apiKey: String): List<TagInfo> =
        withContext(Dispatchers.IO) {
            val conn = openGet("$serverUrl/api/v1/tags", apiKey)
            try {
                if (conn.responseCode != 200) return@withContext emptyList()
                val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("tags")
                List(arr.length()) {
                    val o = arr.getJSONObject(it)
                    TagInfo(o.getString("name"), o.optInt("count", 0))
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                conn.disconnect()
            }
        }

    /** Préremplissage live titre + extrait depuis l'URL, au moment du partage (miroir du fetchMeta() web). */
    suspend fun fetchMeta(serverUrl: String, apiKey: String, url: String): MetaInfo? =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val conn = openGet("$serverUrl/api/v1/fetch-meta?url=$encoded", apiKey)
            try {
                if (conn.responseCode != 200) return@withContext null
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                MetaInfo(
                    title = o.optString("title", ""),
                    description = o.optString("description", ""),
                )
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    suspend fun fetchGroups(serverUrl: String, apiKey: String): List<GroupItem> =
        withContext(Dispatchers.IO) {
            val conn = openGet("$serverUrl/api/v1/folders", apiKey)
            try {
                if (conn.responseCode != 200) return@withContext emptyList()
                val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("folders")
                List(arr.length()) {
                    val o = arr.getJSONObject(it)
                    GroupItem(
                        id = o.getInt("id"),
                        name = o.getString("name"),
                        parentId = if (o.isNull("parent_id")) null else o.getInt("parent_id"),
                        count = o.optInt("count", 0),
                        depth = o.optInt("depth", 0),
                    )
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                conn.disconnect()
            }
        }

    suspend fun fetchLinks(
        serverUrl: String,
        apiKey: String,
        page: Int = 1,
        q: String = "",
        tag: String = "",
        groupId: Int? = null,
    ): LinksPage? = withContext(Dispatchers.IO) {
        val params = buildString {
            append("page=$page&per_page=30")
            if (q.isNotBlank()) append("&q=${java.net.URLEncoder.encode(q, "UTF-8")}")
            if (tag.isNotBlank()) append("&tag=${java.net.URLEncoder.encode(tag, "UTF-8")}")
            if (groupId != null) append("&group_id=$groupId")
        }
        val conn = openGet("$serverUrl/api/v1/links?$params", apiKey)
        try {
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val arr = json.getJSONArray("links")
            val items = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val tagsArr = o.getJSONArray("tags")
                LinkItem(
                    id = o.getInt("id"),
                    url = o.getString("url"),
                    title = o.optString("title", o.getString("url")),
                    description = o.optString("description", ""),
                    faviconUrl = o.optString("favicon_url", ""),
                    thumbnailUrl = o.optString("thumbnail_url", ""),
                    isPublic = o.optBoolean("is_public", false),
                    tags = List(tagsArr.length()) { tagsArr.getString(it) },
                    createdAt = o.optString("created_at", ""),
                    note = o.optString("note", ""),
                    archivedUrl = if (o.isNull("archived_url")) null else o.optString("archived_url", "").ifBlank { null },
                    archiveStatus = if (o.isNull("archive_status")) null else o.optString("archive_status", "").ifBlank { null },
                    isBroken = o.optBoolean("is_broken", false),
                    checkStatus = if (o.isNull("check_status")) null else o.optInt("check_status"),
                    hasReader = o.optBoolean("has_reader", false),
                )
            }
            LinksPage(
                links = items,
                total = json.getInt("total"),
                page = json.getInt("page"),
                totalPages = json.getInt("total_pages"),
            )
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchReader(serverUrl: String, apiKey: String, linkId: Int): ReaderContent? =
        withContext(Dispatchers.IO) {
            // L'extraction peut être faite à la volée côté serveur (fetch + readability),
            // donc readTimeout plus généreux que le défaut de 10 s.
            val conn = openGet("$serverUrl/api/v1/links/$linkId/reader", apiKey)
                .apply { readTimeout = 25_000 }
            try {
                if (conn.responseCode != 200) {
                    conn.errorStream?.use { it.readBytes() }
                    return@withContext null
                }
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                ReaderContent(
                    title = o.optString("reader_title", ""),
                    html = o.optString("reader_html", ""),
                    extractedAt = if (o.isNull("reader_extracted_at")) null else o.optString("reader_extracted_at", null),
                )
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    suspend fun patchLink(serverUrl: String, apiKey: String, linkId: Int, isPublic: Boolean): Result =
        withContext(Dispatchers.IO) {
            val conn = openRequest("$serverUrl/api/v1/links/$linkId", apiKey, "PATCH", withBody = true)
            try {
                val body = JSONObject().put("is_public", isPublic).toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                when (conn.responseCode) {
                    200 -> Result(true, if (isPublic) R.string.link_made_public else R.string.link_made_private)
                    404 -> Result(false, R.string.link_not_found)
                    else -> Result(false, R.string.server_error, conn.responseCode.toString())
                }
            } catch (e: Exception) {
                Result(false, R.string.network_error, e.message ?: "", isNetworkError = true)
            } finally {
                conn.disconnect()
            }
        }

    suspend fun deleteLink(serverUrl: String, apiKey: String, linkId: Int): Result =
        withContext(Dispatchers.IO) {
            val conn = openRequest("$serverUrl/api/v1/links/$linkId", apiKey, "DELETE")
            try {
                when (conn.responseCode) {
                    204 -> Result(true, R.string.link_deleted)
                    404 -> Result(false, R.string.link_not_found)
                    401 -> Result(false, R.string.invalid_api_key)
                    else -> Result(false, R.string.server_error, conn.responseCode.toString())
                }
            } catch (e: Exception) {
                Result(false, R.string.network_error, e.message ?: "", isNetworkError = true)
            } finally {
                conn.disconnect()
            }
        }

    suspend fun addLink(
        serverUrl: String,
        apiKey: String,
        url: String,
        title: String,
        tags: List<String>,
        description: String = "",
        note: String = "",
        folderId: Int? = null,
        isPublic: Boolean = false,
    ): Result = withContext(Dispatchers.IO) {
        val conn = openRequest("$serverUrl/api/v1/links", apiKey, "POST", withBody = true)
        try {
            val body = JSONObject().apply {
                put("url", url)
                put("title", title)
                put("description", description)
                put("note", note)
                put("tags", JSONArray(tags))
                put("is_public", isPublic)
                if (folderId != null) put("folder_id", folderId)
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            when (val code = conn.responseCode) {
                201 -> Result(true, R.string.link_saved)
                401 -> Result(false, R.string.invalid_api_key)
                400 -> Result(false, R.string.invalid_url_rejected)
                else -> Result(false, R.string.server_error, code.toString())
            }
        } catch (e: Exception) {
            Result(false, R.string.network_error, e.message ?: "")
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Texte affichable d'un [ApiClient.Result], résolu dans la langue courante.
 */
fun ApiClient.Result.text(ctx: Context): String =
    if (messageArg != null) ctx.getString(messageRes, messageArg) else ctx.getString(messageRes)
