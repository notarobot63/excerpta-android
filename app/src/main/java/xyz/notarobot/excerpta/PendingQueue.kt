package xyz.notarobot.excerpta

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PendingQueue {
    private const val FILENAME = "pending_links.json"
    private const val MAX_QUEUE = 200  // plafond anti-accumulation hors-ligne prolongé

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
     * `filesDir` et non `cacheDir` : Android vide le cache sans prevenir sous
     * pression de stockage, ce qui faisait disparaitre en silence des liens
     * partages hors-ligne et jamais synchronises.
     */
    private fun file(ctx: Context) = File(ctx.filesDir, FILENAME)

    /** Reprend une file laissee dans l'ancien emplacement (cacheDir) par une version anterieure. */
    private fun migrateFromCacheIfNeeded(ctx: Context) {
        val legacy = File(ctx.cacheDir, FILENAME)
        if (!legacy.exists()) return
        val target = file(ctx)
        try {
            if (!target.exists()) legacy.copyTo(target, overwrite = false)
            legacy.delete()
        } catch (_: Exception) {
        }
    }

    fun isEmpty(ctx: Context): Boolean = load(ctx).isEmpty()

    /** Ajoute un lien à la file. Retourne false si la file est pleine (lien non ajouté). */
    fun enqueue(ctx: Context, link: PendingLink): Boolean {
        val list = load(ctx).toMutableList()
        if (list.size >= MAX_QUEUE) return false
        list.add(link)
        persist(ctx, list)
        return true
    }

    fun load(ctx: Context): List<PendingLink> {
        migrateFromCacheIfNeeded(ctx)
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
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
        } catch (_: Exception) { emptyList() }
    }

    fun clear(ctx: Context) { file(ctx).delete() }

    fun replace(ctx: Context, links: List<PendingLink>) {
        if (links.isEmpty()) clear(ctx) else persist(ctx, links)
    }

    private fun persist(ctx: Context, links: List<PendingLink>) {
        try {
            val arr = JSONArray()
            links.forEach { link ->
                arr.put(JSONObject().apply {
                    put("url", link.url)
                    put("title", link.title)
                    put("description", link.description)
                    put("note", link.note)
                    put("is_public", link.isPublic)
                    if (link.folderId != null) put("folder_id", link.folderId)
                    val tagsArr = JSONArray()
                    link.tags.forEach { tagsArr.put(it) }
                    put("tags", tagsArr)
                })
            }
            file(ctx).writeText(arr.toString())
        } catch (_: Exception) {}
    }
}
