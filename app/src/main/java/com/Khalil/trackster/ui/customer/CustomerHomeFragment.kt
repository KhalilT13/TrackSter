package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R
import com.Khalil.trackster.model.ServiceOffering
import com.Khalil.trackster.model.WeeklySchedule
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CustomerHomeFragment : Fragment(R.layout.fragment_customer_home) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var businessesListener: ListenerRegistration? = null
    private var reviewsListener: ListenerRegistration? = null
    private var favoritesListener: ListenerRegistration? = null
    private lateinit var businessAdapter: BusinessAdapter
    private var baseBusinesses = emptyList<Business>()
    private var ratings = emptyMap<String, List<Int>>()
    private var favorites = emptySet<String>()
    private var query = ""
    private var filter = BusinessFilter.ALL
    private var currentView: View? = null

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentView = view
        loadGreeting(view)
        setUpList(view)
        setUpFilters(view)
        startListeners()
        view.findViewById<ImageView>(R.id.iv_sign_out).setOnClickListener {
            (requireActivity() as MainActivity).onSignOut()
        }
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() {
        businessesListener?.remove(); reviewsListener?.remove(); favoritesListener?.remove()
        businessesListener = null; reviewsListener = null; favoritesListener = null; currentView = null
        super.onDestroyView()
    }

    // Purpose: Loads the customer's name and displays a personalized greeting.
    private fun loadGreeting(view: View) {
        val greeting = view.findViewById<TextView>(R.id.tv_greeting)
        greeting.text = getString(R.string.customer_home_greeting_format, getString(R.string.customer_home_default_name))
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (!isAdded) return@addOnSuccessListener
            val name = document.getString("displayName") ?: auth.currentUser?.email ?: getString(R.string.customer_home_default_name)
            greeting.text = getString(R.string.customer_home_greeting_format, name)
        }
    }

    // Purpose: Configures the business list and its details, booking, and favorite actions.
    private fun setUpList(view: View) {
        businessAdapter = BusinessAdapter(
            onOpenClick = { business ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, BusinessDetailsFragment.newInstance(business))
                    .addToBackStack(null).commit()
            },
            onFavoriteClick =(::toggleFavorite)
        )
        view.findViewById<RecyclerView>(R.id.recycler_businesses).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = businessAdapter
        }
    }

    // Purpose: Connects search and filter controls to real-time list rendering.
    private fun setUpFilters(view: View) {
        view.findViewById<TextInputEditText>(R.id.et_business_search).addTextChangedListener(object : TextWatcher {
            // Purpose: Handles the pre-change text callback; no action is required at this stage.
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            // Purpose: Updates the search query and rerenders businesses while the customer types.
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); render() }
            // Purpose: Handles the post-change text callback; no additional action is required.
            override fun afterTextChanged(s: Editable?) = Unit
        })
        view.findViewById<ChipGroup>(R.id.business_filter_group).setOnCheckedStateChangeListener { group, _ ->
            filter = when (group.checkedChipId) {
                R.id.chip_open -> BusinessFilter.OPEN
                R.id.chip_top_rated -> BusinessFilter.TOP_RATED
                R.id.chip_favorites -> BusinessFilter.FAVORITES
                else -> BusinessFilter.ALL
            }
            render()
        }
    }

    // Purpose: Starts the Firestore listeners required by this screen and rerenders on changes.
    private fun startListeners() {
        businessesListener = firestore.collection("businesses").addSnapshotListener { snapshot, error ->
            if (!isAdded) return@addSnapshotListener
            if (error != null) { toast(R.string.error_businesses_generic); return@addSnapshotListener }
            baseBusinesses = snapshot?.documents.orEmpty().mapNotNull { document ->
                val name = document.getString("businessName")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                @Suppress("UNCHECKED_CAST") val services = (document.get("services") as? List<String>).orEmpty().filter { it.isNotBlank() }
                @Suppress("UNCHECKED_CAST") val photos = (document.get("photoUrls") as? List<String>).orEmpty().filter { it.isNotBlank() }
                val catalog = ServiceOffering.fromFirestore(document.get("serviceCatalog"), services).filter { it.active }
                val serviceNames = catalog.map { it.name }.ifEmpty { listOf(getString(R.string.default_service_name)) }
                Business(document.id, name, serviceNames, photos,
                    document.getString("openingHours").orEmpty(), document.getBoolean("isQueueOpen") ?: true,
                    iconRes = iconForServices(serviceNames), serviceCatalog = catalog,
                    logoUrl = document.getString("logoUrl").orEmpty(),
                    weeklySchedule = WeeklySchedule.fromFirestore(document.get("weeklySchedule")))
            }
            render()
        }
        reviewsListener = firestore.collection("reviews").addSnapshotListener { snapshot, _ ->
            if (!isAdded) return@addSnapshotListener
            ratings = snapshot?.documents.orEmpty().groupBy { it.getString("businessId").orEmpty() }
                .mapValues { (_, docs) -> docs.mapNotNull { it.getLong("rating")?.toInt() } }
            render()
        }
        val uid = auth.currentUser?.uid ?: return
        favoritesListener = firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            @Suppress("UNCHECKED_CAST")
            favorites = (snapshot?.get("favoriteBusinessIds") as? List<String>).orEmpty().toSet()
            render()
        }
    }

    // Purpose: Refreshes this screen so its views reflect the latest data and selections.
    private fun render() {
        val view = currentView ?: return
        val enriched = baseBusinesses.map { business ->
            val values = ratings[business.id].orEmpty()
            business.copy(
                averageRating = if (values.isEmpty()) 0.0 else values.average(),
                reviewCount = values.size,
                isFavorite = business.id in favorites
            )
        }
        val visible = BusinessFiltering.apply(enriched, query, filter)
        businessAdapter.submitList(visible)
        view.findViewById<RecyclerView>(R.id.recycler_businesses).visibility = if (visible.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.tv_businesses_empty).visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    // Purpose: Adds or removes a business from the customer's Firestore favorites.
    private fun toggleFavorite(business: Business) {
        val uid = auth.currentUser?.uid ?: return
        val operation = if (business.isFavorite) FieldValue.arrayRemove(business.id) else FieldValue.arrayUnion(business.id)
        firestore.collection("users").document(uid).update("favoriteBusinessIds", operation)
            .addOnFailureListener { if (isAdded) toast(R.string.error_favorite_generic) }
    }

    // Purpose: Selects a representative business icon from its published service types.
    private fun iconForServices(services: List<String>): Int {
        val label = services.joinToString(" ").lowercase()
        return when {
            listOf("hair", "beard", "barber").any(label::contains) -> R.drawable.ic_scissors
            listOf("clinic", "checkup", "medical").any(label::contains) -> R.drawable.ic_medical
            listOf("car", "garage", "tire").any(label::contains) -> R.drawable.ic_car
            listOf("beauty", "spa").any(label::contains) -> R.drawable.ic_spa
            else -> R.drawable.ic_business
        }
    }

    // Purpose: Shows a short localized feedback message to the user.
    private fun toast(message: Int) = Toast.makeText(requireContext(), getString(message), Toast.LENGTH_LONG).show()
}
