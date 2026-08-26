package com.Khalil.trackster.ui.customer

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.Khalil.trackster.model.WeeklySchedule
import java.util.Calendar

class BusinessAdapter(
    private val onOpenClick: (Business) -> Unit,
    private val onFavoriteClick: (Business) -> Unit
) : ListAdapter<Business, BusinessAdapter.BusinessViewHolder>(BusinessDiffCallback) {

    class BusinessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_business_icon)
        val ivLogo: ImageView = itemView.findViewById(R.id.iv_business_logo)
        val tvName: TextView = itemView.findViewById(R.id.tv_business_name)
        val tvServiceType: TextView = itemView.findViewById(R.id.tv_service_type)
        val tvRating: TextView = itemView.findViewById(R.id.tv_business_rating)
        val tvHours: TextView = itemView.findViewById(R.id.tv_business_hours)
        val tvQueueState: TextView = itemView.findViewById(R.id.tv_queue_state)
        val btnFavorite: MaterialButton = itemView.findViewById(R.id.btn_favorite)
        val btnBook: MaterialButton = itemView.findViewById(R.id.btn_book)
    }

    // Purpose: Inflates a row layout and creates its RecyclerView view holder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusinessViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_business, parent, false)
        return BusinessViewHolder(view)
    }

    // Purpose: Binds the item data and actions to the RecyclerView row at the requested position.
    override fun onBindViewHolder(holder: BusinessViewHolder, position: Int) {
        val business = getItem(position)
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        if (business.photoUrls.isNotEmpty()) {
            ImageViewCompat.setImageTintList(holder.ivIcon, null)
            holder.ivIcon.setPadding(0, 0, 0, 0)
            holder.ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(holder.ivIcon).load(business.photoUrls.first()).centerCrop()
                .placeholder(business.iconRes).into(holder.ivIcon)
        } else {
            Glide.with(holder.ivIcon).clear(holder.ivIcon)
            ImageViewCompat.setImageTintList(holder.ivIcon, null)
            holder.ivIcon.setImageDrawable(null)
        }
        if (business.logoUrl.isNotBlank()) {
            ImageViewCompat.setImageTintList(holder.ivLogo, null)
            holder.ivLogo.setPadding(0, 0, 0, 0)
            holder.ivLogo.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(holder.ivLogo).load(business.logoUrl).centerCrop().into(holder.ivLogo)
        } else {
            Glide.with(holder.ivLogo).clear(holder.ivLogo)
            val logoPadding = (15 * density).toInt()
            holder.ivLogo.setPadding(logoPadding, logoPadding, logoPadding, logoPadding)
            holder.ivLogo.scaleType = ImageView.ScaleType.CENTER_INSIDE
            holder.ivLogo.setImageResource(business.iconRes)
            ImageViewCompat.setImageTintList(holder.ivLogo, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.trackster_blue)))
        }
        holder.tvName.text = business.name
        holder.tvServiceType.text = business.serviceCatalog.take(3).joinToString(" · ") { "${it.name} ${it.priceLabel()}" }
        holder.tvRating.text = if (business.reviewCount > 0) {
            context.getString(R.string.business_rating_format, business.averageRating, business.reviewCount)
        } else context.getString(R.string.business_no_reviews)
        val today = business.weeklySchedule[WeeklySchedule.keyFor(Calendar.getInstance())]
        holder.tvHours.text = when {
            today == null -> context.getString(R.string.business_hours_fallback)
            !today.open -> context.getString(R.string.today_closed)
            today.breakEnabled -> context.getString(R.string.today_hours_with_break, today.openTime, today.closeTime, today.breakStart, today.breakEnd)
            else -> context.getString(R.string.today_hours, today.openTime, today.closeTime)
        }
        holder.tvQueueState.text = context.getString(
            if (business.isQueueOpen) R.string.business_queue_open else R.string.business_queue_closed
        )
        holder.btnBook.isEnabled = business.isQueueOpen
        holder.btnFavorite.setIconResource(
            if (business.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
        )
        holder.btnFavorite.contentDescription = context.getString(
            if (business.isFavorite) R.string.favorite_remove_description else R.string.favorite_add_description,
            business.name
        )
        holder.btnFavorite.setOnClickListener { onFavoriteClick(business) }
        holder.btnBook.setOnClickListener { onOpenClick(business) }
        holder.itemView.setOnClickListener { onOpenClick(business) }
    }

    private object BusinessDiffCallback : DiffUtil.ItemCallback<Business>() {
        // Purpose: Compares stable identifiers to determine whether two list entries represent the same item.
        override fun areItemsTheSame(oldItem: Business, newItem: Business) = oldItem.id == newItem.id
        // Purpose: Compares complete item contents to avoid unnecessary RecyclerView updates.
        override fun areContentsTheSame(oldItem: Business, newItem: Business) = oldItem == newItem
    }
}
