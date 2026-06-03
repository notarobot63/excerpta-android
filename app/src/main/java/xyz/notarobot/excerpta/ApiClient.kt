package xyz.notarobot.excerpta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    data class Result(val success: Boolean, val message: String)

    data class TagInfo(val name: String, val count: Int)

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
                Result(true, "Connecté en tant que $name")
            } else {
                conn.errorStream?.use { it.readBytes() }  // drain pour libérer la socket keep-alive
                Result(false, "Erreur $code - vérifiez l'URL et la clé API")
            }
        } catch (e: Exception) {
            Result(false, "Impossible de joindre le serveur : ${e.message}")
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

    suspend fun patchLink(serverUrl: String, apiKey: String, linkId: Int, isPublic: Boolean): Result =
        withContext(Dispatchers.IO) {
            val conn = openRequest("$serverUrl/api/v1/links/$linkId", apiKey, "PATCH", withBody = true)
            try {
                val body = JSONObject().put("is_public", isPublic).toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                when (conn.responseCode) {
                    200 -> Result(true, if (isPublic) "Lien rendu public" else "Lien rendu privé")
                    404 -> Result(false, "Lien introuvable")
                    else -> Result(false, "Erreur serveur (${conn.responseCode})")
                }
            } catch (e: Exception) {
                Result(false, "Erreur réseau : ${e.message}")
            } finally {
                conn.disconnect()
            }
        }

    suspend fun deleteLink(serverUrl: String, apiKey: String, linkId: Int): Result =
        withContext(Dispatchers.IO) {
            val conn = openRequest("$serverUrl/api/v1/links/$linkId", apiKey, "DELETE")
            try {
                when (conn.responseCode) {
                    204 -> Result(true, "Lien supprimé")
                    404 -> Result(false, "Lien introuvable")
                    401 -> Result(false, "Clé API invalide")
                    else -> Result(false, "Erreur serveur (${conn.responseCode})")
                }
            } catch (e: Exception) {
                Result(false, "Erreur réseau : ${e.message}")
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
        note: String = "",
    ): Result = withContext(Dispatchers.IO) {
        val conn = openRequest("$serverUrl/api/v1/links", apiKey, "POST", withBody = true)
        try {
            val body = JSONObject().apply {
                put("url", url)
                put("title", title)
                put("note", note)
                put("tags", JSONArray(tags))
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            when (val code = conn.responseCode) {
                201 -> Result(true, "Lien sauvegardé")
                401 -> Result(false, "Clé API invalide")
                400 -> Result(false, "URL invalide ou rejetée")
                else -> Result(false, "Erreur serveur ($code)")
            }
        } catch (e: Exception) {
            Result(false, "Erreur réseau : ${e.message}")
        } finally {
            conn.disconnect()
        }
    }
}
