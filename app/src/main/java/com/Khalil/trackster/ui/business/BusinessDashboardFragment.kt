package com.Khalil.trackster.ui.business

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R
import com.Khalil.trackster.model.RevenueEstimator
import com.Khalil.trackster.model.ServiceOffering
import com.Khalil.trackster.model.WeeklySchedule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale

/**
 * The business owner's dashboard. Shows three at-a-glance stats and a live queue of
 * appointments for this business.
 *
 * The queue and stats are driven by a Firestore snapshot listener, so they update in
 * real time (e.g. when a customer books, or another device changes a status) - matching
 * the "Auto refresh" indicator in the header.
 */
class BusinessDashboardFragment : Fragment(R.layout.fragment_business_dashboard) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val BUSINESSES_COLLECTION = "businesses"
        private const val APPOINTMENTS_COLLECTION = "appointments"
        private const val QUEUE_COUNTERS_COLLECTION = "queueCounters"
        private const val FIELD_BUSINESS_NAME = "businessName"
        private const val FIELD_BUSINESS_ID = "businessId"
        private const val FIELD_STATUS = "status"
        private const val FIELD_QUEUE_NUMBER = "queueNumber"
        private const val FIELD_APPOINTMENT_DATE = "appointmentDate"
        private const val FIELD_APPOINTMENT_TIME = "appointmentTime"

        private const val STATUS_WAITING = "waiting"
        private const val STATUS_IN_SERVICE = "in_service"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_CANCELLED = "cancelled"

    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // The live queue listener; removed in onDestroyView so it can't fire after the view is gone.
    private var queueListener: ListenerRegistration? = null

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<RecyclerView>(R.id.recycler_queue).layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<ImageView>(R.id.iv_sign_out).setOnClickListener {
            signOut()
        }

        loadBusinessProfileThenQueue(view)
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() {
        // Stop listening before the view is torn down, otherwise callbacks could touch dead views.
        queueListener?.remove()
        queueListener = null
        super.onDestroyView()
    }

    /** Reads this owner's business name from Firestore, shows the greeting, then starts the live queue. */
    // Purpose: Loads business identity and services before starting the queue listener.
    private fun loadBusinessProfileThenQueue(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tv_greeting)
        val uid = auth.currentUser?.uid

        if (uid == null) {
            tvGreeting.text = getString(R.string.business_dashboard_greeting_format, getString(R.string.customer_home_default_name))
            showQueue(view, emptyList())
            return
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnSuccessListener { document ->
                // Guard against the fragment being detached before this async result arrives
                // (e.g. the owner switched bottom-nav tabs); getString()/requireContext() would crash.
                if (!isAdded) return@addOnSuccessListener
                val name = document.getString(FIELD_BUSINESS_NAME)
                tvGreeting.text = getString(R.string.business_dashboard_greeting_format, name ?: getString(R.string.customer_home_default_name))
                ensurePublicBusinessProfile(uid, name)
                startQueueListener(view)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                tvGreeting.text = getString(R.string.business_dashboard_greeting_format, getString(R.string.customer_home_default_name))
                Toast.makeText(requireContext(), getString(R.string.error_business_dashboard_generic), Toast.LENGTH_LONG).show()
                showQueue(view, emptyList())
            }
    }

    /**
     * Attaches a real-time listener on this business's appointments. Every time the data
     * changes, this recomputes the stats and rebuilds the queue list.
     */
    // Purpose: Listens to the business queue in real time and refreshes rows and statistics.
    private fun startQueueListener(view: View) {
        val uid = auth.currentUser?.uid ?: return

        // Replace any previous listener before starting a new one.
        queueListener?.remove()
        queueListener = firestore.collection(APPOINTMENTS_COLLECTION)
            .whereEqualTo(FIELD_BUSINESS_ID, uid)
            .addSnapshotListener { snapshot, error ->
                // The listener can fire after the view is destroyed; skip if we're no longer attached.
                if (!isAdded) return@addSnapshotListener
                if (error != null) {
                    Toast.makeText(requireContext(), getString(R.string.error_business_dashboard_generic), Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents ?: emptyList()

                updateStats(view, documents)
                updateInsights(view, documents)

                // Build the queue and order it by queue number (who's first in line).
                val entries = documents
                    .map { document ->
                        QueueEntry(
                            id = document.id,
                            queueNumber = document.getLong(FIELD_QUEUE_NUMBER)?.toInt() ?: 0,
                            customerName = document.getString("customerName").orEmpty(),
                            serviceType = document.getString("serviceType").orEmpty(),
                            status = document.getString(FIELD_STATUS).orEmpty(),
                            appointmentDate = document.getString(FIELD_APPOINTMENT_DATE).orEmpty(),
                            appointmentTime = document.getString(FIELD_APPOINTMENT_TIME).orEmpty(),
                            priceDisplay = document.getString("priceDisplay").orEmpty()
                        )
                    }
                    .filter { it.status != STATUS_COMPLETED && it.status != STATUS_CANCELLED }
                    .sortedBy { it.queueNumber }
                showQueue(view, entries)
            }
    }

    /** Recomputes the three header stats from the current set of appointment documents. */
    // Purpose: Calculates and displays waiting counts, average wait time, and estimated revenue.
    private fun updateStats(view: View, documents: List<DocumentSnapshot>) {
        val waiting = documents.count { it.getString(FIELD_STATUS) == STATUS_WAITING }
        val inService = documents.count { it.getString(FIELD_STATUS) == STATUS_IN_SERVICE }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayCount = documents.count {
            it.getString("appointmentDateKey") == today && it.getString(FIELD_STATUS) != STATUS_CANCELLED
        }

        view.findViewById<TextView>(R.id.tv_stat_waiting).text = getString(R.string.number_format, waiting)
        view.findViewById<TextView>(R.id.tv_stat_today).text = getString(R.string.number_format, todayCount)
        view.findViewById<TextView>(R.id.tv_stat_in_service).text = getString(R.string.number_format, inService)
    }

    // Purpose: Calculates completion, cancellation, top-service, and rating insights for the business.
    private fun updateInsights(view: View, documents: List<DocumentSnapshot>) {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val monthly = documents.filter { it.getString("appointmentDateKey").orEmpty().startsWith(monthKey) }
        val completed = monthly.filter { it.getString(FIELD_STATUS) == STATUS_COMPLETED }
        val cancelled = monthly.filter { it.getString(FIELD_STATUS) == STATUS_CANCELLED }
        val terminalCount = completed.size + cancelled.size
        val completionRate = if (terminalCount == 0) 0 else completed.size * 100 / terminalCount
        val revenue = completed.sumOf { appointment ->
            RevenueEstimator.estimate(
                priceType = appointment.getString("servicePriceType"),
                minimumPrice = appointment.get("serviceMinimumPrice") as? Number,
                maximumPrice = appointment.get("serviceMaximumPrice") as? Number,
                priceDisplay = appointment.getString("priceDisplay")
            )
        }
        val active = monthly.filter { it.getString(FIELD_STATUS) != STATUS_CANCELLED }
        val topService = active.groupingBy { it.getString("serviceType").orEmpty() }.eachCount()
            .filterKeys { it.isNotBlank() }.maxByOrNull { it.value }?.key
        val busiest = active.groupingBy {
            val day = runCatching {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.getString("appointmentDateKey").orEmpty())
                if (date == null) "" else SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
            }.getOrDefault("")
            day to it.getString(FIELD_APPOINTMENT_TIME).orEmpty()
        }.eachCount().filterKeys { it.first.isNotBlank() && it.second.isNotBlank() }.maxByOrNull { it.value }?.key
        val noData = getString(R.string.business_insight_no_data)
        view.findViewById<TextView>(R.id.tv_insight_completed).text = getString(R.string.number_format, completed.size)
        view.findViewById<TextView>(R.id.tv_insight_cancelled).text = getString(R.string.number_format, cancelled.size)
        view.findViewById<TextView>(R.id.tv_insight_revenue).text = getString(R.string.business_insight_currency_format, DecimalFormat("#,##0.##").format(revenue))
        view.findViewById<TextView>(R.id.tv_insight_completion_rate).text = if (terminalCount == 0) noData else getString(R.string.business_insight_percent_format, completionRate)
        view.findViewById<TextView>(R.id.tv_insight_top_service).text = topService ?: noData
        view.findViewById<TextView>(R.id.tv_insight_busiest_time).text = busiest?.let { getString(R.string.business_insight_time_format, it.first, it.second) } ?: noData
    }

    /** Shows the queue when there are entries, or the empty-state message otherwise. */
    // Purpose: Maps appointment documents to sorted queue rows and displays them.
    private fun showQueue(view: View, entries: List<QueueEntry>) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_queue)
        val emptyState = view.findViewById<TextView>(R.id.tv_empty_state)

        if (entries.isEmpty()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            recycler.adapter = null
            return
        }

        recycler.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        // "Call Next" belongs to the waiting entry with the lowest queue number (next in line).
        val nextWaitingId = if (entries.any { it.status == STATUS_IN_SERVICE }) {
            null
        } else {
            entries
                .filter { it.status == STATUS_WAITING }
                .minByOrNull { it.queueNumber }
                ?.id
        }

        recycler.adapter = QueueEntryAdapter(
            entries = entries,
            nextWaitingId = nextWaitingId,
            onCallNext = { entry -> updateStatus(entry, STATUS_IN_SERVICE) },
            onComplete = { entry -> updateStatus(entry, STATUS_COMPLETED) }
        )
    }

    /**
     * Writes a status change for one appointment. We don't reload here - the snapshot listener
     * picks up the change and refreshes the stats and list automatically.
     */
    // Purpose: Updates an appointment status transactionally and advances the serving counter when required.
    private fun updateStatus(entry: QueueEntry, newStatus: String) {
        val appointmentRef = firestore.collection(APPOINTMENTS_COLLECTION).document(entry.id)
        val batch = firestore.batch()
        batch.update(
            appointmentRef,
            mapOf(
                FIELD_STATUS to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (newStatus == STATUS_IN_SERVICE) {
            val uid = auth.currentUser?.uid ?: return
            val counterRef = firestore.collection(QUEUE_COUNTERS_COLLECTION).document(uid)
            batch.set(
                counterRef,
                mapOf(
                    "businessId" to uid,
                    "currentServingNumber" to entry.queueNumber,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        batch.commit().addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), getString(R.string.error_update_status_generic), Toast.LENGTH_LONG).show()
            }
    }

    /** Migrates business accounts created by older app versions into the public directory. */
    // Purpose: Ensures the owner's public business document exists with safe default fields.
    private fun ensurePublicBusinessProfile(uid: String, businessName: String?) {
        val publicRef = firestore.collection(BUSINESSES_COLLECTION).document(uid)
        publicRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            val profile = mutableMapOf<String, Any>(
                "ownerId" to uid,
                "businessName" to (businessName ?: getString(R.string.customer_home_default_name)),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!snapshot.exists()) {
                val defaultService = ServiceOffering(name = getString(R.string.default_service_name))
                val defaultSchedule = WeeklySchedule.default()
                profile["services"] = listOf(defaultService.name)
                profile["serviceCatalog"] = listOf(defaultService.toMap())
                profile["isQueueOpen"] = true
                profile["weeklySchedule"] = WeeklySchedule.toFirestore(defaultSchedule)
                profile["openingHours"] = WeeklySchedule.summary(defaultSchedule)
                profile["photoUrls"] = emptyList<String>()
                profile["logoUrl"] = ""
                profile["createdAt"] = FieldValue.serverTimestamp()
            }
            publicRef.set(profile, SetOptions.merge())
        }
    }

    /** Delegates sign-out to MainActivity, which also hides the bottom nav and returns to Landing. */
    // Purpose: Signs out the business owner and returns to the landing screen.
    private fun signOut() {
        (requireActivity() as MainActivity).onSignOut()
    }
}
