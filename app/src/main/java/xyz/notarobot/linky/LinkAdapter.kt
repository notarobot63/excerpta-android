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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class LinkAdapter : ListAdapter<ApiClient.LinkItem, LinkAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val favicon: ImageView = view.findViewById(R.id.imgFavicon)
        val domain: TextView = view.findViewById(R.id.tvDomain)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val description: TextView = view.findViewById(R.id.tvDescription)
        val chips: ChipGroup = view.findViewById(R.id.chipGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.domain.text = Uri.parse(item.url).host ?: item.url
        holder.title.text = item.title.ifBlank { item.url }

        if (item.description.isNotBlank()) {
            holder.description.visibility = View.VISIBLE
            holder.description.text = item.description
        } else {
            holder.description.visibility = View.GONE
        }

        holder.chips.removeAllViews()
        item.tags.forEach { tag ->
            val chip = Chip(holder.chips.context).apply {
                text = tag
                isClickable = false
                isCheckable = false
                textSize = 10f
            }
            holder.chips.addView(chip)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
            it.context.startActivity(intent)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ApiClient.LinkItem>() {
            override fun areItemsTheSame(a: ApiClient.LinkItem, b: ApiClient.LinkItem) = a.id == b.id
            override fun areContentsTheSame(a: ApiClient.LinkItem, b: ApiClient.LinkItem) = a == b
        }
    }
}
