package com.Khalil.trackster.ui.customer

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Calendar

class AppointmentAdapter(
    private val appointments: List<Appointment>,
    private val onCancel: (Appointment) -> Unit,
    private val onReview: (Appointment) -> Unit,
    private val onReschedule: (Appointment) -> Unit,
    private val onCalendar: (Appointment) -> Unit
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {
    class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val queue: TextView = itemView.findViewById(R.id.tv_queue_number)
        val name: TextView = itemView.findViewById(R.id.tv_business_name)
        val service: TextView = itemView.findViewById(R.id.tv_service_type)
        val dateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        val status: TextView = itemView.findViewById(R.id.tv_status)
        val cancel: MaterialButton = itemView.findViewById(R.id.btn_cancel_appointment)
        val review: MaterialButton = itemView.findViewById(R.id.btn_review_appointment)
        val reschedule: MaterialButton = itemView.findViewById(R.id.btn_reschedule_appointment)
        val calendar: MaterialButton = itemView.findViewById(R.id.btn_add_calendar)
    }

    // Purpose: Inflates a row layout and creates its RecyclerView view holder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AppointmentViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
    )

    // Purpose: Binds the item data and actions to the RecyclerView row at the requested position.
    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val appointment = appointments[position]; val context = holder.itemView.context
        holder.name.text = appointment.businessName
        holder.service.text = listOf(appointment.serviceType, appointment.priceDisplay).filter { it.isNotBlank() }.joinToString(" · ")
        holder.queue.text = if (appointment.queueNumber > 0) context.getString(R.string.my_appointments_queue_format, appointment.queueNumber) else context.getString(R.string.profile_missing_value)
        holder.dateTime.text = context.getString(R.string.my_appointments_datetime_format, appointment.appointmentDate, appointment.appointmentTime)
        holder.dateTime.visibility = if (appointment.appointmentDate.isBlank() && appointment.appointmentTime.isBlank()) View.GONE else View.VISIBLE
        bindStatus(holder.status, appointment.status)
        val upcoming = isUpcoming(appointment)
        holder.cancel.visibility = if (appointment.status == "waiting") View.VISIBLE else View.GONE
        holder.reschedule.visibility = if (appointment.status == "waiting" && upcoming) View.VISIBLE else View.GONE
        holder.calendar.visibility = if (appointment.status in listOf("waiting", "in_service") && upcoming) View.VISIBLE else View.GONE
        holder.review.visibility = if (appointment.status == "completed") View.VISIBLE else View.GONE
        holder.review.isEnabled = !appointment.hasReview
        holder.review.text = context.getString(if (appointment.hasReview) R.string.review_submitted else R.string.action_leave_review)
        holder.cancel.setOnClickListener { onCancel(appointment) }
        holder.review.setOnClickListener { if (!appointment.hasReview) onReview(appointment) }
        holder.reschedule.setOnClickListener { onReschedule(appointment) }
        holder.calendar.setOnClickListener { onCalendar(appointment) }
    }

    // Purpose: Checks whether an appointment is still future-facing and eligible for upcoming actions.
    private fun isUpcoming(appointment: Appointment): Boolean {
        val key = appointment.appointmentDateKey
        if (key.isBlank()) return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        if (key > today) return true
        if (key < today) return false
        val now = Calendar.getInstance()
        return appointment.appointmentStartMinutes > now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    // Purpose: Maps an internal status to the row's localized text and visual styling.
    private fun bindStatus(view: TextView, status: String) {
        view.text = view.context.getString(when (status) {
            "completed" -> R.string.appointment_status_completed
            "in_service" -> R.string.appointment_status_in_service
            "cancelled" -> R.string.appointment_status_cancelled
            else -> R.string.appointment_status_waiting
        })
        val pair = when (status) {
            "completed" -> R.color.status_completed_bg to R.color.status_completed_text
            "in_service" -> R.color.status_in_service_bg to R.color.status_in_service_text
            "cancelled" -> R.color.status_cancelled_bg to R.color.status_cancelled_text
            else -> R.color.trackster_blue_light to R.color.trackster_blue
        }
        view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(view.context, pair.first))
        view.setTextColor(ContextCompat.getColor(view.context, pair.second))
    }

    // Purpose: Returns the number of rows currently displayed by the adapter.
    override fun getItemCount() = appointments.size
}
