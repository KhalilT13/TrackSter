package com.Khalil.trackster.ui.business

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.customer.Review
import com.Khalil.trackster.ui.customer.ReviewAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BusinessReviewsFragment : Fragment(R.layout.fragment_business_reviews) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null
    private val adapter = ReviewAdapter()

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<RecyclerView>(R.id.recycler_business_reviews).apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@BusinessReviewsFragment.adapter }
        val uid = auth.currentUser?.uid ?: return
        listener = firestore.collection("reviews").whereEqualTo("businessId", uid).addSnapshotListener { snapshot, error ->
            if (!isAdded) return@addSnapshotListener
            if (error != null) { Toast.makeText(requireContext(), R.string.error_review_generic, Toast.LENGTH_SHORT).show(); return@addSnapshotListener }
            val reviews = snapshot?.documents.orEmpty().map { doc -> Review(
                doc.id, doc.getString("customerName").orEmpty().ifBlank { getString(R.string.role_customer_title) },
                doc.getLong("rating")?.toInt() ?: 0, doc.getString("comment").orEmpty(), doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            ) }.sortedByDescending { it.createdAtMillis }
            adapter.submitList(reviews)
            view.findViewById<TextView>(R.id.tv_business_reviews_empty).visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
            view.findViewById<TextView>(R.id.tv_owner_review_summary).text = if (reviews.isEmpty()) getString(R.string.business_no_reviews)
                else getString(R.string.business_rating_format, reviews.map { it.rating }.average(), reviews.size)
        }
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() { listener?.remove(); listener = null; super.onDestroyView() }
}
