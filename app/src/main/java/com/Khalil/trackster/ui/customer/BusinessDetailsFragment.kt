package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.LayoutInflater
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.Khalil.trackster.R
import com.Khalil.trackster.model.WeeklySchedule
import com.Khalil.trackster.model.DaySchedule
import com.Khalil.trackster.model.SpecialDateSchedules
import com.Khalil.trackster.ui.common.PhotoGalleryAdapter
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

class BusinessDetailsFragment : Fragment(R.layout.fragment_business_details) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var reviewListener: ListenerRegistration? = null
    private lateinit var business: Business
    private val reviewAdapter = ReviewAdapter()

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        business = readBusiness()
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.tv_details_name).text = business.name
        val displayServices = requireArguments().getStringArrayList(ARG_SERVICE_DISPLAY).orEmpty()
        view.findViewById<TextView>(R.id.tv_details_services).text = displayServices.ifEmpty { business.services }.joinToString("\n")
        view.findViewById<TextView>(R.id.tv_details_queue).text = getString(if (business.isQueueOpen) R.string.business_queue_open else R.string.business_queue_closed)
        view.findViewById<TextView>(R.id.tv_details_rating).text = if (business.reviewCount > 0) {
            getString(R.string.business_rating_format, business.averageRating, business.reviewCount)
        } else getString(R.string.business_no_reviews)

        view.findViewById<RecyclerView>(R.id.recycler_details_photos).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = PhotoGalleryAdapter().also { it.submitList(business.photoUrls) }
            visibility = if (business.photoUrls.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.tv_details_photos_empty).visibility = if (business.photoUrls.isEmpty()) View.VISIBLE else View.GONE
        view.findViewById<RecyclerView>(R.id.recycler_reviews).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reviewAdapter
            isNestedScrollingEnabled = false
        }
        val book = view.findViewById<MaterialButton>(R.id.btn_details_book)
        book.isEnabled = business.isQueueOpen
        book.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BookingFragment.newInstance(business.id, business.name, business.services))
                .addToBackStack(null).commit()
        }
        renderLogo(view, business.logoUrl)
        loadPublicProfile(view)
        startReviews(view)
    }

    // Purpose: Listens to the public business document and renders all customer-facing information.
    private fun loadPublicProfile(view: View) {
        firestore.collection("businesses").document(business.id).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            renderWorkingHours(view, WeeklySchedule.fromFirestore(doc.get("weeklySchedule")))
            renderSpecialDates(view, doc.get("specialDateSchedules"))
            renderLogo(view, doc.getString("logoUrl").orEmpty())
            renderLocationAndAccessibility(view, doc.data.orEmpty())
        }
    }

    // Purpose: Displays upcoming exceptions to the business's normal weekly hours.
    private fun renderSpecialDates(view: View, raw: Any?) {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = keyFormat.format(java.util.Date())
        val upcoming = SpecialDateSchedules.fromFirestore(raw).values.filter { it.dateKey >= today }.sortedBy { it.dateKey }.take(3)
        val section = view.findViewById<View>(R.id.details_special_dates_section)
        section.visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE
        if (upcoming.isEmpty()) return
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        view.findViewById<TextView>(R.id.tv_details_special_dates).text = upcoming.joinToString("\n") { item ->
            val date = runCatching { keyFormat.parse(item.dateKey)?.let(dateFormat::format) }.getOrNull() ?: item.dateKey
            getString(R.string.special_date_public_format, date, item.title, specialHours(item.schedule))
        }
    }

    // Purpose: Returns a readable opening-hours or closed label for a special date.
    private fun specialHours(day: DaySchedule): String = when {
        !day.open -> getString(R.string.special_date_closed_label)
        day.breakEnabled -> getString(R.string.special_date_break_format, day.openTime, day.closeTime, day.breakStart, day.breakEnd)
        else -> getString(R.string.special_date_hours_format, day.openTime, day.closeTime)
    }

    // Purpose: Displays the business address, parking, and accessibility information.
    private fun renderLocationAndAccessibility(view: View, data: Map<String, Any>) {
        val address = data["address"] as? String ?: ""
        val city = data["city"] as? String ?: ""
        val notes = data["locationNotes"] as? String ?: ""
        val fullAddress = listOf(address, city).filter { it.isNotBlank() }.joinToString(", ")
        val locationSection = view.findViewById<View>(R.id.details_location_section)
        locationSection.visibility = if (fullAddress.isBlank()) View.GONE else View.VISIBLE
        if (fullAddress.isNotBlank()) {
            view.findViewById<TextView>(R.id.tv_details_address).text = fullAddress
            view.findViewById<TextView>(R.id.tv_details_location_notes).apply {
                text = notes
                visibility = if (notes.isBlank()) View.GONE else View.VISIBLE
            }
            view.findViewById<MaterialButton>(R.id.btn_directions).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(fullAddress)}"))
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(requireContext(), R.string.error_directions_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val features = mutableListOf<String>()
        val hasParking = data["hasParking"] == true
        if (hasParking) features += getString(R.string.parking_available)
        else if (data.containsKey("hasParking")) features += getString(R.string.parking_not_available)
        if (data["hasAccessibleParking"] == true) features += getString(R.string.accessible_parking)
        if (data["accessibleEntrance"] == true) features += getString(R.string.accessible_entrance)
        if (data["accessibleRestroom"] == true) features += getString(R.string.accessible_restroom)
        if (data["elevatorAvailable"] == true) features += getString(R.string.elevator_available)
        val container = view.findViewById<LinearLayout>(R.id.details_accessibility_container)
        container.removeAllViews()
        if (features.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.accessibility_not_provided)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.trackster_text_secondary))
            })
        } else {
            features.forEach { feature ->
                container.addView(TextView(requireContext()).apply {
                    text = getString(R.string.feature_available_format, feature)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.trackster_navy))
                    textSize = 15f
                    setPadding(0, (5 * resources.displayMetrics.density).toInt(), 0, (5 * resources.displayMetrics.density).toInt())
                })
            }
        }
    }

    // Purpose: Displays the weekly schedule as one clearly separated row per day.
    private fun renderWorkingHours(view: View, schedule: Map<String, com.Khalil.trackster.model.DaySchedule>) {
        val container = view.findViewById<LinearLayout>(R.id.details_hours_container)
        val labels = listOf(R.string.day_sunday, R.string.day_monday, R.string.day_tuesday, R.string.day_wednesday, R.string.day_thursday, R.string.day_friday, R.string.day_saturday)
        container.removeAllViews()
        WeeklySchedule.dayKeys.forEachIndexed { index, key ->
            val day = schedule[key] ?: return@forEachIndexed
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_public_working_hours, container, false)
            row.findViewById<TextView>(R.id.tv_public_day).text = getString(labels[index])
            row.findViewById<TextView>(R.id.tv_public_hours).text = if (day.open) "${day.openTime}–${day.closeTime}" else getString(R.string.day_closed_short)
            row.findViewById<TextView>(R.id.tv_public_break).apply {
                visibility = if (day.open && day.breakEnabled) View.VISIBLE else View.GONE
                if (day.breakEnabled) text = getString(R.string.public_break_format, day.breakStart, day.breakEnd)
            }
            container.addView(row)
        }
    }

    // Purpose: Displays the remote logo or the empty-logo state when no URL exists.
    private fun renderLogo(view: View, url: String) {
        val image = view.findViewById<ImageView>(R.id.iv_details_logo)
        if (url.isNotBlank()) {
            ImageViewCompat.setImageTintList(image, null)
            image.setPadding(0, 0, 0, 0)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(image).load(url).centerCrop().into(image)
        } else {
            Glide.with(image).clear(image)
            val padding = (13 * resources.displayMetrics.density).toInt()
            image.setPadding(padding, padding, padding, padding)
            image.setImageResource(R.drawable.ic_business)
            ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.trackster_blue)))
        }
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() { reviewListener?.remove(); reviewListener = null; super.onDestroyView() }

    // Purpose: Listens to verified business reviews and updates the review list in real time.
    private fun startReviews(view: View) {
        reviewListener = firestore.collection("reviews").whereEqualTo("businessId", business.id)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null) { Toast.makeText(requireContext(), getString(R.string.error_review_generic), Toast.LENGTH_SHORT).show(); return@addSnapshotListener }
                val reviews = snapshot?.documents.orEmpty().map { doc -> Review(
                    appointmentId = doc.id,
                    customerName = doc.getString("customerName").orEmpty().ifBlank { getString(R.string.role_customer_title) },
                    rating = doc.getLong("rating")?.toInt() ?: 0,
                    comment = doc.getString("comment").orEmpty(),
                    createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                ) }.sortedByDescending { it.createdAtMillis }
                reviewAdapter.submitList(reviews)
                view.findViewById<TextView>(R.id.tv_reviews_empty).visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    // Purpose: Builds a complete Business model from the fragment's navigation arguments.
    private fun readBusiness() = Business(
        id = requireArguments().getString(ARG_ID).orEmpty(),
        name = requireArguments().getString(ARG_NAME).orEmpty(),
        services = requireArguments().getStringArrayList(ARG_SERVICES).orEmpty(),
        photoUrls = requireArguments().getStringArrayList(ARG_PHOTOS).orEmpty(),
        openingHours = requireArguments().getString(ARG_HOURS).orEmpty(),
        isQueueOpen = requireArguments().getBoolean(ARG_OPEN),
        averageRating = requireArguments().getDouble(ARG_RATING),
        reviewCount = requireArguments().getInt(ARG_REVIEW_COUNT),
        logoUrl = requireArguments().getString(ARG_LOGO).orEmpty()
    )

    companion object {
        private const val ARG_ID = "id"; private const val ARG_NAME = "name"; private const val ARG_SERVICES = "services"
        private const val ARG_PHOTOS = "photos"; private const val ARG_HOURS = "hours"; private const val ARG_OPEN = "open"
        private const val ARG_RATING = "rating"; private const val ARG_REVIEW_COUNT = "review_count"
        private const val ARG_SERVICE_DISPLAY = "service_display"
        private const val ARG_LOGO = "logo"
        // Purpose: Creates a fragment instance containing the navigation arguments it requires.
        fun newInstance(business: Business) = BusinessDetailsFragment().apply { arguments = Bundle().apply {
            putString(ARG_ID, business.id); putString(ARG_NAME, business.name); putStringArrayList(ARG_SERVICES, ArrayList(business.services))
            putStringArrayList(ARG_SERVICE_DISPLAY, ArrayList(business.serviceCatalog.map { it.bookingLabel() }))
            putStringArrayList(ARG_PHOTOS, ArrayList(business.photoUrls)); putString(ARG_HOURS, business.openingHours); putBoolean(ARG_OPEN, business.isQueueOpen)
            putDouble(ARG_RATING, business.averageRating); putInt(ARG_REVIEW_COUNT, business.reviewCount)
            putString(ARG_LOGO, business.logoUrl)
        } }
    }
}
