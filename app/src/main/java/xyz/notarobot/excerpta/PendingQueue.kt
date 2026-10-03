package xyz.notarobot.excerpta

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PendingLink(
    val url: String,
    val title: String,
    val tags: List<String>,
    val description: String = "",
    val note: String,
    val folderId: Int?,
    val isPublic: Boolean,
)

/**
 * Liens partagés hors-ligne, en attente d'envoi. Sans dépendance Android pour
 * rester testable en JVM ; [PendingQueue] en fournit l'instance unique.
 */
class PendingStore(private val file: File, private val maxSize: Int = 200) {

    enum class EnqueueResult { QUEUED, FULL, WRITE_FAILED }

    data class FlushReport(val sent: Int, val rejected: Int)

    private val flushing = Mutex()

    @Synchronized
    fun load(): List<PendingLink> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val tagsArr = o.getJSONArray("tags")
                PendingLink(
                    url = o.getString("url"),
                    title = o.optString("title", ""),
                    tags = List(tagsArr.length()) { tagsArr.getString(it) },
                    description = o.optString("description", ""),
                    note = o.optString("note", ""),
                    folderId = if (o.isNull("folder_id")) null else o.getInt("folder_id"),
                    isPublic = o.optBoolean("is_public", false),
                )
            }
        } catch (_: Exception) {
            // Mis de côté plutôt qu'ignoré : le prochain ajout réécrirait le
            // fichier par-dessus et ferait disparaître les liens qu'il contient.
            file.renameTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
            emptyList()
        }
    }

    fun isEmpty(): Boolean = load().isEmpty()

    @Synchronized
    fun enqueue(link: PendingLink): EnqueueResult {
        val list = load()
        if (list.size >= maxSize) return EnqueueResult.FULL
        return if (persist(list + link)) EnqueueResult.QUEUED else EnqueueResult.WRITE_FAILED
    }

    /** Retrait à la demande de l'utilisateur (appui long sur un lien en attente). */
    fun discard(link: PendingLink) = remove(listOf(link))

    /**
     * Retire les liens traités en relisant le fichier : un lien ajouté pendant
     * l'envoi (partage depuis une autre appli) est conservé.
     */
    @Synchronized
    private fun remove(processed: List<PendingLink>) {
        if (processed.isEmpty()) return
        val remaining = load().toMutableList()
        processed.forEach { remaining.remove(it) }
        persist(remaining)
    }

    /**
     * Envoie la file dans l'ordre. Les refus du serveur sont retirés (et
     * comptés, pour être signalés) ; au premier échec réseau on s'arrête :
     * le reste échouerait pareil, chacun après un délai de connexion complet.
     * Renvoie null si un envoi est déjà en cours, pour ne rien envoyer deux fois.
     */
    suspend fun flush(send: suspend (PendingLink) -> ApiClient.Result): FlushReport? {
        if (!flushing.tryLock()) return null
        try {
            val done = mutableListOf<PendingLink>()
            var sent = 0
            var rejected = 0
            for (link in load()) {
                val result = send(link)
                when {
                    result.success -> sent++
                    result.isNetworkError -> break
                    else -> rejected++
                }
                done += link
            }
            remove(done)
            return FlushReport(sent, rejected)
        } finally {
            flushing.unlock()
        }
    }

    /** Écriture atomique : fichier temporaire puis renommage, jamais de JSON tronqué. */
    private fun persist(links: List<PendingLink>): Boolean = try {
        if (links.isEmpty()) {
            !file.exists() || file.delete()
        } else {
            val arr = JSONArray()
            links.forEach { link ->
                arr.put(JSONObject().apply {
                    put("url", link.url)
                    put("title", link.title)
                    put("description", link.description)
                    put("note", link.note)
                    put("is_public", link.isPublic)
                    if (link.folderId != null) put("folder_id", link.folderId)
                    put("tags", JSONArray(link.tags))
                })
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    } catch (_: Exception) {
        false
    }
}

object PendingQueue {
    private const val FILENAME = "pending_links.json"

    @Volatile private var store: PendingStore? = null

    /**
     * `filesDir` et non `cacheDir` : Android vide le cache sans prevenir sous
     * pression de stockage, ce qui faisait disparaitre en silence des liens
     * partages hors-ligne et jamais synchronises.
     */
    fun get(ctx: Context): PendingStore = store ?: synchronized(this) {
        store ?: run {
            migrateFromCacheIfNeeded(ctx)
            PendingStore(File(ctx.filesDir, FILENAME)).also { store = it }
        }
    }

    /** Reprend une file laissee dans l'ancien emplacement (cacheDir) par une version anterieure. */
    private fun migrateFromCacheIfNeeded(ctx: Context) {
        val legacy = File(ctx.cacheDir, FILENAME)
        if (!legacy.exists()) return
        val target = File(ctx.filesDir, FILENAME)
        try {
            if (!target.exists()) legacy.copyTo(target, overwrite = false)
            legacy.delete()
        } catch (_: Exception) {
        }
    }
}
