package com.Khalil.trackster.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import java.util.Locale

/** Renders the list of the signed-in customer's appointments on [MyAppointmentsFragment]. */
class AppointmentAdapter(
    private val appointments: List<Appointment>
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

    class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_business_name)
        val tvServiceType: TextView = itemView.findViewById(R.id.tv_service_type)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val appointment = appointments[position]
        holder.tvName.text = appointment.businessName
        holder.tvServiceType.text = appointment.serviceType
        // Firestore stores status as plain lowercase ("waiting"); capitalize it for display.
        holder.tvStatus.text = appointment.status.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    override fun getItemCount(): Int = appointments.size
}
