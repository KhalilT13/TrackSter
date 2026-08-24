package com.Khalil.trackster.ui.customer

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

/**
 * Customer-facing live queue screen. It watches the customer's active appointment and
 * the selected business queue so position and estimated wait change as staff call people.
 */
class CustomerQueueFragment : Fragment(R.layout.fragment_customer_queue) {

    companion object {
        private const val APPOINTMENTS_COLLECTION = "appointments"
        private const val QUEUE_COUNTERS_COLLECTION = "queueCounters"
        private const val STATUS_WAITING = "waiting"
        private const val STATUS_IN_SERVICE = "in_service"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_CANCELLED = "cancelled"
        private const val MINUTES_PER_TURN = 15
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var customerListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null
    private var activeAppointmentId: String? = null

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startCustomerListener(view)
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() {
        customerListener?.remove()
        queueListener?.remove()
        customerListener = null
        queueListener = null
        super.onDestroyView()
    }

    // Purpose: Listens to the active appointment and queue counter for real-time customer updates.
    private fun startCustomerListener(view: View) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showEmpty(view)
            return
        }

        customerListener?.remove()
        customerListener = firestore.collection(APPOINTMENTS_COLLECTION)
            .whereEqualTo("customerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null) {
                    Toast.makeText(requireContext(), getString(R.string.error_queue_generic), Toast.LENGTH_LONG).show()
                    showEmpty(view)
                    return@addSnapshotListener
                }

                val active = snapshot?.documents.orEmpty()
                    .map { document ->
                        Appointment(
                            id = document.id,
                            businessId = document.getString("businessId").orEmpty(),
                            businessName = document.getString("businessName").orEmpty(),
                            serviceType = document.getString("serviceType").orEmpty(),
                            status = document.getString("status").orEmpty(),
                            queueNumber = document.getLong("queueNumber")?.toInt() ?: 0,
                            appointmentDate = document.getString("appointmentDate").orEmpty(),
                            appointmentTime = document.getString("appointmentTime").orEmpty(),
                            createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                            priceDisplay = document.getString("priceDisplay").orEmpty()
                        )
                    }
                    .filter { it.status != STATUS_COMPLETED && it.status != STATUS_CANCELLED }
                    .sortedWith(
                        compareByDescending<Appointment> { it.status == STATUS_IN_SERVICE }
                            .thenBy { it.createdAtMillis }
                    )
                    .firstOrNull()

                if (active == null) {
                    showEmpty(view)
                } else {
                    showActive(view, active)
                }
            }
    }

    // Purpose: Displays the customer's active appointment, business, status, and queue number.
    private fun showActive(view: View, appointment: Appointment) {
        view.findViewById<View>(R.id.queue_content).visibility = View.VISIBLE
        view.findViewById<View>(R.id.queue_empty_state).visibility = View.GONE

        view.findViewById<TextView>(R.id.tv_queue_business).text = appointment.businessName
        view.findViewById<TextView>(R.id.tv_queue_number).text =
            if (appointment.queueNumber > 0) getString(R.string.my_appointments_queue_format, appointment.queueNumber)
            else getString(R.string.profile_missing_value)
        view.findViewById<TextView>(R.id.tv_queue_service).text =
            listOf(appointment.serviceType, appointment.priceDisplay).filter { it.isNotBlank() }.joinToString(" · ")
        view.findViewById<TextView>(R.id.tv_queue_schedule).text =
            getString(R.string.my_appointments_datetime_format, appointment.appointmentDate, appointment.appointmentTime)
        bindStatus(view.findViewById(R.id.tv_queue_status), appointment.status)

        if (appointment.status == STATUS_IN_SERVICE) {
            renderPosition(view, peopleAhead = 0, isInService = true)
            queueListener?.remove()
            queueListener = null
            return
        }

        if (activeAppointmentId == appointment.id && queueListener != null) return
        activeAppointmentId = appointment.id
        queueListener?.remove()

        if (appointment.businessId.isBlank()) {
            renderPosition(view, (appointment.queueNumber - 1).coerceAtLeast(0), isInService = false)
            return
        }

        queueListener = firestore.collection(QUEUE_COUNTERS_COLLECTION)
            .document(appointment.businessId)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || error != null) return@addSnapshotListener
                val currentServing = snapshot?.getLong("currentServingNumber")?.toInt() ?: 0
                val peopleAhead = QueueMath.peopleAhead(appointment.queueNumber, currentServing)
                renderPosition(view, peopleAhead, isInService = false)
            }
    }

    // Purpose: Displays people ahead and the estimated queue waiting time.
    private fun renderPosition(view: View, peopleAhead: Int, isInService: Boolean) {
        view.findViewById<TextView>(R.id.tv_people_ahead).text = if (isInService) {
            getString(R.string.queue_your_turn)
        } else {
            resources.getQuantityString(R.plurals.queue_people_ahead, peopleAhead, peopleAhead)
        }
        view.findViewById<TextView>(R.id.tv_estimated_wait).text = if (isInService) {
            getString(R.string.queue_wait_now)
        } else {
            getString(R.string.queue_wait_minutes, QueueMath.estimatedMinutes(peopleAhead, MINUTES_PER_TURN))
        }
    }

    // Purpose: Displays the empty state when the customer has no active queue appointment.
    private fun showEmpty(view: View) {
        activeAppointmentId = null
        queueListener?.remove()
        queueListener = null
        view.findViewById<View>(R.id.queue_content).visibility = View.GONE
        view.findViewById<View>(R.id.queue_empty_state).visibility = View.VISIBLE
    }

    // Purpose: Maps an internal status to the row's localized text and visual styling.
    private fun bindStatus(statusView: TextView, status: String) {
        statusView.text = status.replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        val (background, foreground) = if (status == STATUS_IN_SERVICE) {
            R.color.status_in_service_bg to R.color.status_in_service_text
        } else {
            R.color.trackster_blue_light to R.color.trackster_blue
        }
        statusView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), background))
        statusView.setTextColor(ContextCompat.getColor(requireContext(), foreground))
    }
}
