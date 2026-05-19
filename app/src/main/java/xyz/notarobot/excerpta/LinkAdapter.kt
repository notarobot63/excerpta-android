package xyz.notarobot.excerpta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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

    var onLongClick: ((ApiClient.LinkItem) -> Unit)? = null

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
            holder.thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.thumbnail.load(item.thumbnailUrl) {
                crossfade(true)
                transformations(RoundedCornersTransformation(8f))
                listener(onError = { _, _ ->
                    holder.thumbnail.scaleType = ImageView.ScaleType.FIT_XY
                    holder.thumbnail.setImageDrawable(makePlaceholder(holder.thumbnail, item.url))
                })
            }
        } else {
            holder.thumbnail.visibility = View.VISIBLE
            holder.thumbnail.scaleType = ImageView.ScaleType.FIT_XY
            holder.thumbnail.setImageDrawable(makePlaceholder(holder.thumbnail, item.url))
        }

        holder.itemView.setOnClickListener {
            it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
        }

        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
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

        private val COLORS = intArrayOf(
            0xFF2e86ab.toInt(), 0xFFa23b72.toInt(), 0xFFf18f01.toInt(),
            0xFFc73e1d.toInt(), 0xFF3b1f2b.toInt(), 0xFF44bba4.toInt(),
            0xFFe94f37.toInt(), 0xFF393e41.toInt(), 0xFF6b4226.toInt(),
            0xFF7b2d8b.toInt(), 0xFF2d6a4f.toInt(), 0xFFe76f51.toInt(),
            0xFF457b9d.toInt(), 0xFF6a0572.toInt(), 0xFF0077b6.toInt(),
        )

        fun makePlaceholder(view: View, url: String): BitmapDrawable {
            val host = Uri.parse(url).host?.removePrefix("www.") ?: url
            val initial = host.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val idx = host.sumOf { it.code } % COLORS.size
            val size = 240
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = COLORS[idx]
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = size * 0.42f
            paint.isFakeBoldText = true
            val cy = size / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(initial, size / 2f, cy, paint)
            return BitmapDrawable(view.context.resources, bitmap)
        }
    }
}
