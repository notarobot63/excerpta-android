package xyz.notarobot.excerpta

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownServiceException

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

    /**
     * Echec de transport (pas de reseau, serveur injoignable, delai depasse) :
     * le seul cas ou un ajout merite d'etre remis en file pour plus tard.
     * Une URL mal formee ou un HTTP en clair refuse par la politique reseau
     * sont aussi des IOException, mais retenter n'y changera rien.
     */
    fun isNetworkFailure(e: Exception): Boolean =
        e is IOException && e !is MalformedURLException && e !is UnknownServiceException

    /**
     * Ouvre la connexion, envoie [body] s'il y en a un, passe la reponse a
     * [onResponse] et ferme. Toute exception, y compris a l'ouverture (URL
     * serveur mal saisie, schema non HTTP), est rendue a [onFailure] : un
     * appel reseau ne doit jamais faire planter l'activite qui l'a lance.
     *
     * Redirections jamais suivies : HttpURLConnection retransmet les en-tetes
     * personnalises, dont X-API-Key, vers la cible d'un 3xx, y compris sur un
     * autre hote. Un serveur mal configure ou detourne livrerait ainsi la cle.
     * L'URL du serveur est saisie par l'utilisateur et pointe directement sur
     * son instance : une redirection y est anormale, elle remonte donc comme
     * une erreur (code 3xx) plutot que d'etre suivie en silence.
     */
    private suspend fun <T> call(
        url: String,
        apiKey: String,
        method: String = "GET",
        body: String? = null,
        readTimeoutMs: Int = 10_000,
        onFailure: (Exception) -> T,
        onResponse: (HttpURLConnection) -> T,
    ): T = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as? HttpURLConnection
                ?: throw MalformedURLException("not an HTTP URL: $url")
            conn = c
            c.requestMethod = method
            c.setRequestProperty("X-API-Key", apiKey)
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = readTimeoutMs
            if (body != null) {
                c.setRequestProperty("Content-Type", "application/json")
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            onResponse(c)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun HttpURLConnection.readBody(): String = inputStream.use { it.bufferedReader().readText() }

    /** Vide le corps d'erreur pour liberer la socket keep-alive. */
    private fun HttpURLConnection.drainError() {
        errorStream?.use { it.readBytes() }
    }

    private fun failure(e: Exception): Result =
        Result(false, R.string.network_error, e.message ?: "", isNetworkError = isNetworkFailure(e))

    suspend fun ping(serverUrl: String, apiKey: String): Result = call(
        "$serverUrl/api/v1/me", apiKey,
        onFailure = { Result(false, R.string.server_unreachable, it.message ?: "") },
    ) { conn ->
        val code = conn.responseCode
        if (code == 200) {
            Result(true, R.string.connected_as, JSONObject(conn.readBody()).optString("name", ""))
        } else {
            conn.drainError()
            Result(false, R.string.ping_failed, code.toString())
        }
    }

    /** Préférences d'organisation du compte (voir Paramètres → Organization côté web). */
    suspend fun fetchMe(serverUrl: String, apiKey: String): MeInfo? = call(
        "$serverUrl/api/v1/me", apiKey, onFailure = { null },
    ) { conn ->
        if (conn.responseCode != 200) return@call null
        val o = JSONObject(conn.readBody())
        MeInfo(
            tagsEnabled = o.optBoolean("tags_enabled", true),
            foldersEnabled = o.optBoolean("folders_enabled", true),
        )
    }

    suspend fun fetchTags(serverUrl: String, apiKey: String): List<TagInfo> = call(
        "$serverUrl/api/v1/tags", apiKey, onFailure = { emptyList() },
    ) { conn ->
        if (conn.responseCode != 200) return@call emptyList()
        val arr = JSONObject(conn.readBody()).getJSONArray("tags")
        List(arr.length()) {
            val o = arr.getJSONObject(it)
            TagInfo(o.getString("name"), o.optInt("count", 0))
        }
    }

    /** Préremplissage live titre + extrait depuis l'URL, au moment du partage (miroir du fetchMeta() web). */
    suspend fun fetchMeta(serverUrl: String, apiKey: String, url: String): MetaInfo? = call(
        "$serverUrl/api/v1/fetch-meta?url=${URLEncoder.encode(url, "UTF-8")}", apiKey,
        onFailure = { null },
    ) { conn ->
        if (conn.responseCode != 200) return@call null
        val o = JSONObject(conn.readBody())
        MetaInfo(
            title = o.optString("title", ""),
            description = o.optString("description", ""),
        )
    }

    suspend fun fetchGroups(serverUrl: String, apiKey: String): List<GroupItem> = call(
        "$serverUrl/api/v1/folders", apiKey, onFailure = { emptyList() },
    ) { conn ->
        if (conn.responseCode != 200) return@call emptyList()
        val arr = JSONObject(conn.readBody()).getJSONArray("folders")
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
    }

    suspend fun fetchLinks(
        serverUrl: String,
        apiKey: String,
        page: Int = 1,
        q: String = "",
        tag: String = "",
        groupId: Int? = null,
    ): LinksPage? {
        val params = buildString {
            append("page=$page&per_page=30")
            if (q.isNotBlank()) append("&q=${URLEncoder.encode(q, "UTF-8")}")
            if (tag.isNotBlank()) append("&tag=${URLEncoder.encode(tag, "UTF-8")}")
            if (groupId != null) append("&group_id=$groupId")
        }
        return call("$serverUrl/api/v1/links?$params", apiKey, onFailure = { null }) { conn ->
            if (conn.responseCode != 200) return@call null
            val json = JSONObject(conn.readBody())
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
        }
    }

    // L'extraction peut être faite à la volée côté serveur (fetch + readability),
    // donc readTimeout plus généreux que le défaut de 10 s.
    suspend fun fetchReader(serverUrl: String, apiKey: String, linkId: Int): ReaderContent? = call(
        "$serverUrl/api/v1/links/$linkId/reader", apiKey, readTimeoutMs = 25_000, onFailure = { null },
    ) { conn ->
        if (conn.responseCode != 200) {
            conn.drainError()
            return@call null
        }
        val o = JSONObject(conn.readBody())
        ReaderContent(
            title = o.optString("reader_title", ""),
            html = o.optString("reader_html", ""),
            extractedAt = if (o.isNull("reader_extracted_at")) null else o.optString("reader_extracted_at", null),
        )
    }

    suspend fun patchLink(serverUrl: String, apiKey: String, linkId: Int, isPublic: Boolean): Result = call(
        "$serverUrl/api/v1/links/$linkId", apiKey, "PATCH",
        body = JSONObject().put("is_public", isPublic).toString(),
        onFailure = ::failure,
    ) { conn ->
        when (val code = conn.responseCode) {
            200 -> Result(true, if (isPublic) R.string.link_made_public else R.string.link_made_private)
            404 -> Result(false, R.string.link_not_found)
            else -> Result(false, R.string.server_error, code.toString())
        }
    }

    suspend fun deleteLink(serverUrl: String, apiKey: String, linkId: Int): Result = call(
        "$serverUrl/api/v1/links/$linkId", apiKey, "DELETE", onFailure = ::failure,
    ) { conn ->
        when (val code = conn.responseCode) {
            204 -> Result(true, R.string.link_deleted)
            404 -> Result(false, R.string.link_not_found)
            401 -> Result(false, R.string.invalid_api_key)
            else -> Result(false, R.string.server_error, code.toString())
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
    ): Result {
        val body = JSONObject().apply {
            put("url", url)
            put("title", title)
            put("description", description)
            put("note", note)
            put("tags", JSONArray(tags))
            put("is_public", isPublic)
            if (folderId != null) put("folder_id", folderId)
        }.toString()
        return call("$serverUrl/api/v1/links", apiKey, "POST", body, onFailure = ::failure) { conn ->
            when (val code = conn.responseCode) {
                201 -> Result(true, R.string.link_saved)
                401 -> Result(false, R.string.invalid_api_key)
                400 -> Result(false, R.string.invalid_url_rejected)
                else -> Result(false, R.string.server_error, code.toString())
            }
        }
    }
}

/**
 * Texte affichable d'un [ApiClient.Result], résolu dans la langue courante.
 */
fun ApiClient.Result.text(ctx: Context): String =
    if (messageArg != null) ctx.getString(messageRes, messageArg) else ctx.getString(messageRes)
