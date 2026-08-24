package com.Khalil.trackster.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.bumptech.glide.Glide

class PhotoGalleryAdapter(
    private val removable: Boolean = false,
    private val onRemove: ((String) -> Unit)? = null
) : ListAdapter<String, PhotoGalleryAdapter.PhotoViewHolder>(Diff) {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.iv_gallery_photo)
        val remove: ImageView = view.findViewById(R.id.iv_remove_photo)
    }

    // Purpose: Inflates a row layout and creates its RecyclerView view holder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_business_photo, parent, false))

    // Purpose: Binds the item data and actions to the RecyclerView row at the requested position.
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val url = getItem(position)
        Glide.with(holder.photo).load(url).centerCrop().placeholder(R.drawable.ic_business).into(holder.photo)
        holder.remove.visibility = if (removable) View.VISIBLE else View.GONE
        holder.remove.contentDescription = holder.itemView.context.getString(R.string.photo_remove_description)
        holder.remove.setOnClickListener { if (removable) onRemove?.invoke(url) }
    }

    private object Diff : DiffUtil.ItemCallback<String>() {
        // Purpose: Compares stable identifiers to determine whether two list entries represent the same item.
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        // Purpose: Compares complete item contents to avoid unnecessary RecyclerView updates.
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
