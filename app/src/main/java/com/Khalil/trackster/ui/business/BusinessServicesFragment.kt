package com.Khalil.trackster.ui.business

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BusinessServicesFragment : Fragment(R.layout.fragment_business_services) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        openOnClick(view, R.id.card_manage_availability, BusinessAvailabilityFragment())
        openOnClick(view, R.id.card_manage_location, BusinessLocationFragment())
        openOnClick(view, R.id.card_manage_photos, BusinessPhotosFragment())
        openOnClick(view, R.id.card_manage_services, BusinessServiceManagerFragment())
        openOnClick(view, R.id.card_manage_reviews, BusinessReviewsFragment())
        loadHeader(view)
    }

    // Purpose: Connects a business-management card to its destination screen.
    private fun openOnClick(view: View, id: Int, destination: Fragment) {
        view.findViewById<View>(id).setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.fragment_container, destination).addToBackStack(null).commit()
        }
    }

    // Purpose: Loads and displays the business name in the management header.
    private fun loadHeader(view: View) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).get().addOnSuccessListener { business ->
            if (!isAdded) return@addOnSuccessListener
            view.findViewById<TextView>(R.id.tv_business_hub_name).text = business.getString("businessName") ?: getString(R.string.business_profile_title)
            val logo = business.getString("logoUrl").orEmpty()
            val image = view.findViewById<ImageView>(R.id.iv_business_logo)
            if (logo.isNotBlank()) {
                ImageViewCompat.setImageTintList(image, null)
                image.setPadding(0, 0, 0, 0)
                Glide.with(image).load(logo).centerCrop().into(image)
            } else {
                image.setImageResource(R.drawable.ic_business)
                val padding = (14 * resources.displayMetrics.density).toInt()
                image.setPadding(padding, padding, padding, padding)
                ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)))
            }
        }
    }
}
