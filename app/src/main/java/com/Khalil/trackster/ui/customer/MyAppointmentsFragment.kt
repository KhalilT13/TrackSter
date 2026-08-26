package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.Khalil.trackster.model.AppointmentReferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.google.android.gms.tasks.Tasks
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MyAppointmentsFragment : Fragment(R.layout.fragment_my_appointments) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var appointmentsListener: ListenerRegistration? = null
    private var reviewsListener: ListenerRegistration? = null
    private var appointments = emptyList<Appointment>()
    private var reviewedIds = emptySet<String>()
    private var currentView: View? = null

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState); currentView = view
        view.findViewById<RecyclerView>(R.id.recycler_appointments).layoutManager = LinearLayoutManager(requireContext())
        startListeners()
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() {
        appointmentsListener?.remove(); reviewsListener?.remove(); appointmentsListener = null; reviewsListener = null; currentView = null
        super.onDestroyView()
    }

    // Purpose: Starts the Firestore listeners required by this screen and rerenders on changes.
    private fun startListeners() {
        val uid = auth.currentUser?.uid ?: return
        appointmentsListener = firestore.collection("appointments").whereEqualTo("customerId", uid).addSnapshotListener { snapshot, error ->
            if (!isAdded) return@addSnapshotListener
            if (error != null) { toast(R.string.error_my_appointments_generic); return@addSnapshotListener }
            resolveAppointments(snapshot?.documents.orEmpty())
        }
        reviewsListener = firestore.collection("reviews").whereEqualTo("customerId", uid).addSnapshotListener { snapshot, _ ->
            reviewedIds = snapshot?.documents.orEmpty().map { it.id }.toSet(); render()
        }
    }

    /** Joins appointment IDs to the latest business, service, and customer names. */
    private fun resolveAppointments(documents: List<DocumentSnapshot>) {
        if (documents.isEmpty()) { appointments = emptyList(); render(); return }
        val businessRefs = documents.mapNotNull { it.getString("businessId")?.takeIf(String::isNotBlank) }
            .distinct().map { firestore.collection("businesses").document(it) }
        Tasks.whenAllSuccess<DocumentSnapshot>(businessRefs.map { it.get() }).addOnSuccessListener { businesses ->
            val byId = businesses.associateBy { it.id }
            val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
            firestore.collection("publicProfiles").document(uid).get().addOnCompleteListener { profileTask ->
                if (!isAdded) return@addOnCompleteListener
                val customerName = AppointmentReferences.customerName(profileTask.result?.data)
                appointments = documents.map { document ->
                    val businessId = document.getString("businessId").orEmpty()
                    val business = byId[businessId]
                    @Suppress("UNCHECKED_CAST")
                    val legacy = (business?.get("services") as? List<String>).orEmpty()
                    Appointment(
                id = document.id, businessId = document.getString("businessId").orEmpty(), businessName = document.getString("businessName").orEmpty(),
                serviceType = AppointmentReferences.serviceName(document.getString("serviceId").orEmpty(), business?.get("serviceCatalog"), legacy)
                    .ifBlank { document.getString("serviceType").orEmpty() }, status = document.getString("status").orEmpty(),
                queueNumber = document.getLong("queueNumber")?.toInt() ?: 0, appointmentDate = document.getString("appointmentDate").orEmpty(),
                appointmentTime = document.getString("appointmentTime").orEmpty(), createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                customerName = customerName, priceDisplay = document.getString("priceDisplay").orEmpty(),
                bookingSlotIds = (document.get("bookingSlotIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                appointmentDateKey = document.getString("appointmentDateKey").orEmpty(),
                appointmentStartMinutes = document.getLong("appointmentStartMinutes")?.toInt() ?: 0,
                appointmentEndMinutes = document.getLong("appointmentEndMinutes")?.toInt() ?: 0,
                serviceId = document.getString("serviceId").orEmpty(),
                serviceDurationMinutes = document.getLong("serviceDurationMinutes")?.toInt() ?: 30
            ).copy(businessName = business?.getString("businessName").orEmpty().ifBlank { document.getString("businessName").orEmpty() })
                }.sortedByDescending { it.createdAtMillis }
                render()
            }
        }
    }

    // Purpose: Refreshes this screen so its views reflect the latest data and selections.
    private fun render() {
        val view = currentView ?: return
        val list = appointments.map { it.copy(hasReview = it.id in reviewedIds) }
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_appointments)
        view.findViewById<TextView>(R.id.tv_empty_state).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        recycler.adapter = AppointmentAdapter(list, ::confirmCancellation, ::showReviewDialog, ::openReschedule, ::addToCalendar)
    }

    // Purpose: Opens booking in reschedule mode for the selected appointment.
    private fun openReschedule(appointment: Appointment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BookingFragment.newInstanceForReschedule(appointment))
            .addToBackStack(null).commit()
    }

    // Purpose: Loads the business address before preparing the appointment calendar event.
    private fun addToCalendar(appointment: Appointment) {
        firestore.collection("businesses").document(appointment.businessId).get()
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                val location = listOf(task.result?.getString("address").orEmpty(), task.result?.getString("city").orEmpty())
                    .filter { it.isNotBlank() }.joinToString(", ")
                launchCalendar(appointment, location)
            }
    }

    // Purpose: Opens the device calendar with the appointment's title, location, and times.
    private fun launchCalendar(appointment: Appointment, location: String) {
        val start = appointmentMillis(appointment) ?: return toast(R.string.error_calendar_unavailable)
        val duration = (appointment.appointmentEndMinutes - appointment.appointmentStartMinutes)
            .takeIf { it > 0 } ?: appointment.serviceDurationMinutes
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + duration * 60_000L)
            .putExtra(CalendarContract.Events.TITLE, getString(R.string.calendar_event_title, appointment.serviceType, appointment.businessName))
            .putExtra(CalendarContract.Events.DESCRIPTION, getString(R.string.calendar_event_description, appointment.serviceType, appointment.priceDisplay))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { toast(R.string.error_calendar_unavailable) }
    }

    // Purpose: Converts an appointment date and start time into epoch milliseconds.
    private fun appointmentMillis(appointment: Appointment): Long? = runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(appointment.appointmentDateKey) ?: return@runCatching null
        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, appointment.appointmentStartMinutes / 60)
            set(Calendar.MINUTE, appointment.appointmentStartMinutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.getOrNull()

    // Purpose: Requests confirmation before cancelling a future appointment.
    private fun confirmCancellation(appointment: Appointment) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.cancel_title).setMessage(R.string.cancel_message)
            .setNegativeButton(R.string.action_keep_appointment, null)
            .setPositiveButton(R.string.action_cancel_appointment) { _, _ -> cancelAppointment(appointment) }.show()
    }

    // Purpose: Marks the appointment cancelled and removes its local reminders.
    private fun cancelAppointment(appointment: Appointment) {
        val batch = firestore.batch()
        batch.update(firestore.collection("appointments").document(appointment.id), mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp()))
        appointment.bookingSlotIds.forEach { slotId -> batch.delete(firestore.collection("bookingSlots").document(slotId)) }
        batch.commit()
            .addOnSuccessListener { if (isAdded) toast(R.string.success_appointment_cancelled) }
            .addOnFailureListener { if (isAdded) toast(R.string.error_cancel_appointment) }
    }

    // Purpose: Opens the rating and comment dialog for a completed appointment.
    private fun showReviewDialog(appointment: Appointment) {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_review, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.review_title, appointment.businessName))
            .setView(content).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.action_submit_review, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rating = content.findViewById<RatingBar>(R.id.rating_input).rating.toInt()
                if (rating == 0) { toast(R.string.review_rating_required); return@setOnClickListener }
                val comment = content.findViewById<TextInputLayout>(R.id.til_review_comment).editText?.text?.toString()?.trim().orEmpty()
                submitReview(appointment, rating, comment) { dialog.dismiss() }
            }
        }
        dialog.show()
    }

    // Purpose: Stores one verified review linked to the completed appointment and business.
    private fun submitReview(appointment: Appointment, rating: Int, comment: String, success: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val customerName = appointment.customerName.ifBlank { auth.currentUser?.email.orEmpty() }
        val review = mapOf("appointmentId" to appointment.id, "customerId" to uid, "customerName" to customerName,
            "businessId" to appointment.businessId, "businessName" to appointment.businessName,
            "rating" to rating, "comment" to comment.take(500), "createdAt" to FieldValue.serverTimestamp())
        firestore.collection("reviews").document(appointment.id).set(review)
            .addOnSuccessListener { if (isAdded) { toast(R.string.success_review_submitted); success() } }
            .addOnFailureListener { if (isAdded) toast(R.string.error_review_generic) }
    }

    // Purpose: Shows a short localized feedback message to the user.
    private fun toast(id: Int) = Toast.makeText(requireContext(), getString(id), Toast.LENGTH_SHORT).show()
}
