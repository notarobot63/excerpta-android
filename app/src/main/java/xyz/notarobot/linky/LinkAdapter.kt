package xyz.notarobot.linky

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import java.text.SimpleDateFormat
import java.util.Locale

class LinkAdapter : ListAdapter<ApiClient.LinkItem, LinkAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val favicon: ImageView = view.findViewById(R.id.imgFavicon)
        val domain: TextView = view.findViewById(R.id.tvDomain)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val description: TextView = view.findViewById(R.id.tvDescription)
        val date: TextView = view.findViewById(R.id.tvDate)
        val thumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.domain.text = Uri.parse(item.url).host?.removePrefix("www.") ?: item.url
        holder.title.text = item.title.ifBlank { item.url }

        if (item.description.isNotBlank()) {
            holder.description.visibility = View.VISIBLE
            holder.description.text = item.description
        } else {
            holder.description.visibility = View.GONE
        }

        holder.date.text = formatDate(item.createdAt)

        // Favicon
        if (item.faviconUrl.isNotBlank()) {
            holder.favicon.load(item.faviconUrl) {
                crossfade(true)
                error(R.drawable.ic_bookmark)
            }
        } else {
            holder.favicon.load(R.drawable.ic_bookmark)
        }

        // Thumbnail à droite
        if (item.thumbnailUrl.isNotBlank()) {
            holder.thumbnail.visibility = View.VISIBLE
            holder.thumbnail.load(item.thumbnailUrl) {
                crossfade(true)
                transformations(RoundedCornersTransformation(8f))
                error(android.R.drawable.ic_menu_gallery)
                listener(onError = { _, _ -> holder.thumbnail.visibility = View.GONE })
            }
        } else {
            holder.thumbnail.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
        }
    }

    private fun formatDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = fmt.parse(isoDate) ?: return ""
            val diff = System.currentTimeMillis() - date.time
            val min = diff / 60_000
            val h = diff / 3_600_000
            val d = diff / 86_400_000
            when {
                min < 2 -> "à l'instant"
                min < 60 -> "il y a $min min"
                h < 24 -> "il y a ${h}h"
                d == 1L -> "hier"
                d < 7 -> "il y a $d jours"
                else -> SimpleDateFormat("d MMM yyyy", Locale.FRENCH).format(date)
            }
        } catch (_: Exception) { "" }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ApiClient.LinkItem>() {
            override fun areItemsTheSame(a: ApiClient.LinkItem, b: ApiClient.LinkItem) = a.id == b.id
            override fun areContentsTheSame(a: ApiClient.LinkItem, b: ApiClient.LinkItem) = a == b
        }
    }
}
