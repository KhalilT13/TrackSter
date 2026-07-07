package com.Khalil.trackster.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.google.android.material.button.MaterialButton

/**
 * Renders the list of businesses on the customer home screen. [onBookClick]
 * lets the screen decide what happens when "Book / Join Queue" is tapped -
 * for now that's just a Toast, since real booking comes in a later step.
 */
class BusinessAdapter(
    private val businesses: List<Business>,
    private val onBookClick: (Business) -> Unit
) : RecyclerView.Adapter<BusinessAdapter.BusinessViewHolder>() {

    class BusinessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_business_name)
        val tvServiceType: TextView = itemView.findViewById(R.id.tv_service_type)
        val btnBook: MaterialButton = itemView.findViewById(R.id.btn_book)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusinessViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_business, parent, false)
        return BusinessViewHolder(view)
    }

    override fun onBindViewHolder(holder: BusinessViewHolder, position: Int) {
        val business = businesses[position]
        holder.tvName.text = business.name
        holder.tvServiceType.text = business.serviceType
        holder.btnBook.setOnClickListener { onBookClick(business) }
    }

    override fun getItemCount(): Int = businesses.size
}
