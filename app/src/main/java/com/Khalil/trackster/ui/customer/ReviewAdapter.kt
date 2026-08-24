package com.Khalil.trackster.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R

class ReviewAdapter : ListAdapter<Review, ReviewAdapter.ReviewViewHolder>(Diff) {
    class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_review_customer)
        val rating: RatingBar = view.findViewById(R.id.rating_review)
        val comment: TextView = view.findViewById(R.id.tv_review_comment)
    }

    // Purpose: Inflates a row layout and creates its RecyclerView view holder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ReviewViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
    )

    // Purpose: Binds the item data and actions to the RecyclerView row at the requested position.
    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = getItem(position)
        holder.name.text = review.customerName
        holder.rating.rating = review.rating.toFloat()
        holder.comment.text = review.comment
        holder.comment.visibility = if (review.comment.isBlank()) View.GONE else View.VISIBLE
    }

    private object Diff : DiffUtil.ItemCallback<Review>() {
        // Purpose: Compares stable identifiers to determine whether two list entries represent the same item.
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.appointmentId == newItem.appointmentId
        // Purpose: Compares complete item contents to avoid unnecessary RecyclerView updates.
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }
}
