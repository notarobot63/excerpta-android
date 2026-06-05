package xyz.notarobot.excerpta

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PendingQueue {
    private const val FILENAME = "pending_links.json"

    data class PendingLink(
        val url: String,
        val title: String,
        val tags: List<String>,
        val note: String,
        val folderId: Int?,
        val isPublic: Boolean,
    )

    private fun file(ctx: Context) = File(ctx.cacheDir, FILENAME)

    fun isEmpty(ctx: Context): Boolean = load(ctx).isEmpty()

    fun enqueue(ctx: Context, link: PendingLink) {
        val list = load(ctx).toMutableList()
        list.add(link)
        persist(ctx, list)
    }

    fun load(ctx: Context): List<PendingLink> {
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
