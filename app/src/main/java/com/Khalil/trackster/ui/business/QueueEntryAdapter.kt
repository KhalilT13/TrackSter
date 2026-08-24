package com.Khalil.trackster.ui.business

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

private const val STATUS_WAITING = "waiting"
private const val STATUS_COMPLETED = "completed"
private const val STATUS_IN_SERVICE = "in_service"

/**
 * Renders the business owner's live queue.
 *
 * [nextWaitingId] is the document id of the waiting entry with the lowest queue number
 * (i.e. who's up next). Only that entry shows "Call Next" ([onCallNext]); in-service
 * entries instead show "Complete" ([onComplete]); everything else shows no action.
 */
class QueueEntryAdapter(
    private val entries: List<QueueEntry>,
    private val nextWaitingId: String?,
    private val onCallNext: (QueueEntry) -> Unit,
    private val onComplete: (QueueEntry) -> Unit
) : RecyclerView.Adapter<QueueEntryAdapter.QueueEntryViewHolder>() {

    class QueueEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQueueNumber: TextView = itemView.findViewById(R.id.tv_queue_number)
        val tvCustomerName: TextView = itemView.findViewById(R.id.tv_customer_name)
        val tvServiceType: TextView = itemView.findViewById(R.id.tv_service_type)
        val tvSchedule: TextView = itemView.findViewById(R.id.tv_schedule)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        val btnCallNext: MaterialButton = itemView.findViewById(R.id.btn_call_next)
        val btnComplete: MaterialButton = itemView.findViewById(R.id.btn_complete)
    }

    // Purpose: Inflates a row layout and creates its RecyclerView view holder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueEntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue_entry, parent, false)
        return QueueEntryViewHolder(view)
    }

    // Purpose: Binds the item data and actions to the RecyclerView row at the requested position.
    override fun onBindViewHolder(holder: QueueEntryViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context

        holder.tvQueueNumber.text = context.getString(R.string.business_queue_number_format, entry.queueNumber)
        holder.tvCustomerName.text = entry.customerName
        holder.tvServiceType.text = listOf(entry.serviceType, entry.priceDisplay).filter { it.isNotBlank() }.joinToString(" · ")

        // Date/time line, hidden when we have neither (e.g. legacy records).
        if (entry.appointmentDate.isNotBlank() || entry.appointmentTime.isNotBlank()) {
            holder.tvSchedule.visibility = View.VISIBLE
            holder.tvSchedule.text = context.getString(
                R.string.my_appointments_datetime_format,
                entry.appointmentDate,
                entry.appointmentTime
            )
        } else {
            holder.tvSchedule.visibility = View.GONE
        }

        bindStatus(holder.tvStatus, entry.status)
        bindActions(holder, entry)
    }

    /** "Call Next" only on the next-up waiting entry; "Complete" only on in-service entries. */
    // Purpose: Configures the queue-row action buttons according to the appointment status.
    private fun bindActions(holder: QueueEntryViewHolder, entry: QueueEntry) {
        val isNextWaiting = entry.status == STATUS_WAITING && entry.id == nextWaitingId
        val isInService = entry.status == STATUS_IN_SERVICE

        holder.btnCallNext.visibility = if (isNextWaiting) View.VISIBLE else View.GONE
        holder.btnComplete.visibility = if (isInService) View.VISIBLE else View.GONE

        holder.btnCallNext.setOnClickListener { onCallNext(entry) }
        holder.btnComplete.setOnClickListener { onComplete(entry) }
    }

    /**
     * Sets the status label and pill colors. "waiting" is blue, "in_service" amber,
     * "completed" green; anything unexpected falls back to the waiting look.
     */
    // Purpose: Maps an internal status to the row's localized text and visual styling.
    private fun bindStatus(tvStatus: TextView, status: String) {
        // "in_service" -> "In service" for display.
        tvStatus.text = status.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

        val (bgColor, textColor) = when (status) {
            STATUS_COMPLETED -> R.color.status_completed_bg to R.color.status_completed_text
            STATUS_IN_SERVICE -> R.color.status_in_service_bg to R.color.status_in_service_text
            else -> R.color.trackster_blue_light to R.color.trackster_blue
        }
        val context = tvStatus.context
        // Tint (rather than swap) the pill background so the view keeps its padding.
        tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgColor))
        tvStatus.setTextColor(ContextCompat.getColor(context, textColor))
    }

    // Purpose: Returns the number of rows currently displayed by the adapter.
    override fun getItemCount(): Int = entries.size
}
