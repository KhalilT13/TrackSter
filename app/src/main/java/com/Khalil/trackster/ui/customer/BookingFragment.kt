package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R
import com.Khalil.trackster.model.ServiceOffering
import com.Khalil.trackster.model.SpecialDateSchedule
import com.Khalil.trackster.model.SpecialDateSchedules
import com.Khalil.trackster.model.WeeklySchedule
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BookingFragment : Fragment(R.layout.fragment_booking) {
    companion object {
        private const val ARG_BUSINESS_NAME = "business_name"
        private const val ARG_BUSINESS_ID = "business_id"
        private const val ARG_RESCHEDULE_ID = "reschedule_id"
        private const val ARG_RESCHEDULE_SERVICE_ID = "reschedule_service_id"
        private const val ARG_RESCHEDULE_SERVICE_NAME = "reschedule_service_name"
        private const val USERS_COLLECTION = "users"
        private const val APPOINTMENTS_COLLECTION = "appointments"
        private const val QUEUE_COUNTERS_COLLECTION = "queueCounters"
        private const val BOOKING_SLOTS_COLLECTION = "bookingSlots"
        private const val STATUS_WAITING = "waiting"

        // Purpose: Creates a fragment instance containing the navigation arguments it requires.
        fun newInstance(businessId: String, businessName: String, @Suppress("UNUSED_PARAMETER") services: List<String> = emptyList()) =
            BookingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BUSINESS_ID, businessId)
                    putString(ARG_BUSINESS_NAME, businessName)
                }
            }

        // Purpose: Creates a booking fragment configured to reschedule an existing appointment.
        fun newInstanceForReschedule(appointment: Appointment) = BookingFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_BUSINESS_ID, appointment.businessId)
                putString(ARG_BUSINESS_NAME, appointment.businessName)
                putString(ARG_RESCHEDULE_ID, appointment.id)
                putString(ARG_RESCHEDULE_SERVICE_ID, appointment.serviceId)
                putString(ARG_RESCHEDULE_SERVICE_NAME, appointment.serviceType)
            }
        }
    }

    private data class DateOption(val label: String, val key: String, val calendar: Calendar)

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var businessId = ""
    private var businessName = ""
    private var availableServices = emptyList<ServiceOffering>()
    private var schedule = WeeklySchedule.default()
    private var specialDates = linkedMapOf<String, SpecialDateSchedule>()
    private var rescheduleId = ""
    private var rescheduleServiceId = ""
    private var rescheduleServiceName = ""
    private var selectedService: ServiceOffering? = null
    private var selectedDate: DateOption? = null
    private var selectedTime: String? = null
    private val serviceByChip = mutableMapOf<Int, ServiceOffering>()
    private val dateByChip = mutableMapOf<Int, DateOption>()

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        businessId = arguments?.getString(ARG_BUSINESS_ID).orEmpty()
        businessName = arguments?.getString(ARG_BUSINESS_NAME).orEmpty()
        rescheduleId = arguments?.getString(ARG_RESCHEDULE_ID).orEmpty()
        rescheduleServiceId = arguments?.getString(ARG_RESCHEDULE_SERVICE_ID).orEmpty()
        rescheduleServiceName = arguments?.getString(ARG_RESCHEDULE_SERVICE_NAME).orEmpty()
        if (rescheduleId.isNotBlank()) {
            view.findViewById<TextView>(R.id.tv_booking_title).setText(R.string.reschedule_title)
            view.findViewById<MaterialButton>(R.id.btn_book_appointment).setText(R.string.action_confirm_reschedule)
        }
        view.findViewById<TextView>(R.id.tv_business_name).text = businessName
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<MaterialButton>(R.id.btn_book_appointment).setOnClickListener { onBookClicked(view) }
        setUpSelectionListeners(view)
        updateSummary(view)
        loadCurrentBusiness(view)
    }

    // Purpose: Connects service, date, and time selections and refreshes dependent booking state.
    private fun setUpSelectionListeners(view: View) {
        view.findViewById<ChipGroup>(R.id.service_chip_group).setOnCheckedStateChangeListener { group, _ ->
            selectedService = serviceByChip[group.checkedChipId]
            selectedTime = null
            view.findViewById<ChipGroup>(R.id.time_chip_group).clearCheck()
            renderTimes(view)
            updateSummary(view)
        }
        view.findViewById<ChipGroup>(R.id.date_chip_group).setOnCheckedStateChangeListener { group, _ ->
            selectedDate = dateByChip[group.checkedChipId]
            selectedTime = null
            view.findViewById<ChipGroup>(R.id.time_chip_group).clearCheck()
            renderTimes(view)
            updateSummary(view)
        }
        view.findViewById<ChipGroup>(R.id.time_chip_group).setOnCheckedStateChangeListener { group, _ ->
            selectedTime = checkedText(group)
            updateSummary(view)
        }
    }

    // Purpose: Loads the business services, weekly hours, and special dates from Firestore.
    private fun loadCurrentBusiness(view: View) {
        setLoading(view, true)
        availabilityMessage(view, R.string.booking_loading_availability)
        firestore.collection("businesses").document(businessId).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                if (document.getBoolean("isQueueOpen") != true) {
                    setLoading(view, false)
                    availabilityMessage(view, R.string.error_queue_closed)
                    return@addOnSuccessListener
                }
                @Suppress("UNCHECKED_CAST")
                val legacy = (document.get("services") as? List<String>).orEmpty()
                availableServices = ServiceOffering.fromFirestore(document.get("serviceCatalog"), legacy).filter { it.active }
                schedule = WeeklySchedule.fromFirestore(document.get("weeklySchedule"))
                specialDates = SpecialDateSchedules.fromFirestore(document.get("specialDateSchedules"))
                if (rescheduleId.isNotBlank()) {
                    selectedService = availableServices.firstOrNull { it.id == rescheduleServiceId }
                        ?: availableServices.firstOrNull { it.name.equals(rescheduleServiceName, true) }
                }
                renderServices(view)
                renderDates(view)
                setLoading(view, false)
                availabilityMessage(view, if (dateByChip.isEmpty()) R.string.booking_no_open_dates else R.string.booking_choose_service_date)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                setLoading(view, false)
                availabilityMessage(view, R.string.error_booking_generic)
            }
    }

    // Purpose: Creates selectable chips for active services and their displayed prices.
    private fun renderServices(view: View) {
        val group = view.findViewById<ChipGroup>(R.id.service_chip_group)
        group.removeAllViews(); serviceByChip.clear()
        availableServices.forEach { service ->
            val chip = newChip(group, service.bookingLabel())
            serviceByChip[chip.id] = service
            if (rescheduleId.isNotBlank()) {
                chip.isChecked = service.id == selectedService?.id
                chip.isEnabled = false
            }
        }
    }

    // Purpose: Creates upcoming date choices and disables dates on which the business is closed.
    private fun renderDates(view: View) {
        val group = view.findViewById<ChipGroup>(R.id.date_chip_group)
        group.removeAllViews(); dateByChip.clear()
        val labelFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val options = (0 until 14).mapNotNull { offset ->
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            val key = keyFormat.format(calendar.time)
            val day = SpecialDateSchedules.effectiveDay(schedule, specialDates, calendar, key)
            if (!day.open) null else DateOption(labelFormat.format(calendar.time), key, calendar)
        }.take(7)
        options.forEach { option ->
            val chip = newChip(group, option.label)
            dateByChip[chip.id] = option
        }
    }

    // Purpose: Creates half-hour booking choices while excluding breaks, closures, and invalid starts.
    private fun renderTimes(view: View) {
        val group = view.findViewById<ChipGroup>(R.id.time_chip_group)
        group.removeAllViews()
        val service = selectedService
        val date = selectedDate
        if (service == null || date == null) {
            availabilityMessage(view, R.string.booking_choose_service_date)
            return
        }
        val day = SpecialDateSchedules.effectiveDay(schedule, specialDates, date.calendar, date.key)
        val now = Calendar.getInstance()
        val isToday = date.key == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val earliest = if (isToday) now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + 1 else null
        val slots = WeeklySchedule.slots(day, service.durationMinutes, earliest)
        slots.forEach { slot ->
            val chip = newChip(group, slot)
            markUnavailableIfReserved(chip, slot, date, service.durationMinutes)
        }
        availabilityMessage(view, if (slots.isEmpty()) R.string.booking_no_times else R.string.booking_choose_service_date, hideWhenAvailable = slots.isNotEmpty())
    }

    // Purpose: Creates and adds a labeled selectable Chip to the supplied group.
    private fun newChip(group: ChipGroup, label: String): Chip {
        val chip = LayoutInflater.from(requireContext()).inflate(R.layout.chip_choice, group, false) as Chip
        chip.id = View.generateViewId(); chip.text = label; group.addView(chip)
        return chip
    }

    // Purpose: Checks existing reservations and disables a time choice that overlaps a booking.
    private fun markUnavailableIfReserved(chip: Chip, time: String, date: DateOption, durationMinutes: Int) {
        val start = WeeklySchedule.minutes(time) ?: return
        val slotIds = mutableListOf<String>()
        var cursor = start
        while (cursor < start + durationMinutes) {
            slotIds += "${businessId}_${date.key}_$cursor"
            cursor += 30
        }
        val remaining = AtomicInteger(slotIds.size)
        val reserved = AtomicBoolean(false)
        slotIds.forEach { slotId ->
            firestore.collection(BOOKING_SLOTS_COLLECTION).document(slotId).get().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.exists() == true && task.result?.getString("appointmentId") != rescheduleId) reserved.set(true)
                if (remaining.decrementAndGet() == 0 && reserved.get() && isAdded) {
                    chip.isChecked = false
                    chip.isEnabled = false
                    chip.text = getString(R.string.booking_slot_unavailable_format, time)
                }
            }
        }
    }

    // Purpose: Returns the label of the currently selected Chip in a group.
    private fun checkedText(group: ChipGroup): String? =
        if (group.checkedChipId == View.NO_ID) null else group.findViewById<Chip>(group.checkedChipId)?.text?.toString()

    // Purpose: Refreshes the selected service, price, date, and time in the booking summary.
    private fun updateSummary(view: View) {
        val dash = getString(R.string.profile_missing_value)
        view.findViewById<TextView>(R.id.tv_summary_service).text = selectedService?.name ?: dash
        view.findViewById<TextView>(R.id.tv_summary_price).text = selectedService?.priceLabel() ?: dash
        view.findViewById<TextView>(R.id.tv_summary_date).text = selectedDate?.label ?: dash
        view.findViewById<TextView>(R.id.tv_summary_time).text = selectedTime ?: dash
    }

    // Purpose: Validates all booking choices and starts appointment creation or rescheduling.
    private fun onBookClicked(view: View) {
        val service = selectedService
        val date = selectedDate
        val time = selectedTime
        if (service == null || date == null || time == null) {
            Toast.makeText(requireContext(), R.string.booking_incomplete, Toast.LENGTH_SHORT).show()
            return
        }
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), R.string.error_booking_generic, Toast.LENGTH_LONG).show(); return
        }
        setLoading(view, true)
        if (rescheduleId.isNotBlank()) {
            rescheduleAppointmentTransaction(view, uid, service, date, time)
            return
        }
        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                val customerName = task.result?.getString("displayName") ?: auth.currentUser?.email ?: uid
                createAppointmentTransaction(view, uid, customerName, service, date, time)
            }
    }

    // Purpose: Atomically reserves the new slot, releases old slots, and updates the appointment.
    private fun rescheduleAppointmentTransaction(view: View, uid: String, selected: ServiceOffering, date: DateOption, time: String) {
        val businessRef = firestore.collection("businesses").document(businessId)
        val appointmentRef = firestore.collection(APPOINTMENTS_COLLECTION).document(rescheduleId)
        firestore.runTransaction { transaction ->
            val business = transaction.get(businessRef)
            val appointment = transaction.get(appointmentRef)
            if (!appointment.exists() || appointment.getString("customerId") != uid || appointment.getString("status") != STATUS_WAITING) {
                throw IllegalStateException("APPOINTMENT_CHANGED")
            }
            if (business.getBoolean("isQueueOpen") != true) throw IllegalStateException("QUEUE_CLOSED")
            @Suppress("UNCHECKED_CAST")
            val legacy = (business.get("services") as? List<String>).orEmpty()
            val currentService = ServiceOffering.fromFirestore(business.get("serviceCatalog"), legacy)
                .firstOrNull { it.active && (it.id == selected.id || it.name.equals(selected.name, true)) }
                ?: throw IllegalStateException("SERVICE_CHANGED")
            val currentSchedule = WeeklySchedule.fromFirestore(business.get("weeklySchedule"))
            val currentSpecialDates = SpecialDateSchedules.fromFirestore(business.get("specialDateSchedules"))
            val day = SpecialDateSchedules.effectiveDay(currentSchedule, currentSpecialDates, date.calendar, date.key)
            if (time !in WeeklySchedule.slots(day, currentService.durationMinutes)) throw IllegalStateException("SCHEDULE_CHANGED")
            val startMinutes = WeeklySchedule.minutes(time) ?: throw IllegalStateException("SCHEDULE_CHANGED")
            val oldSlotIds = (appointment.get("bookingSlotIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val newSlotIds = mutableListOf<String>()
            var cursor = startMinutes
            while (cursor < startMinutes + currentService.durationMinutes) {
                val slotId = "${businessId}_${date.key}_$cursor"
                val existing = transaction.get(firestore.collection(BOOKING_SLOTS_COLLECTION).document(slotId))
                if (existing.exists() && existing.getString("appointmentId") != rescheduleId) throw IllegalStateException("SLOT_TAKEN")
                newSlotIds += slotId
                cursor += 30
            }
            transaction.update(appointmentRef, mapOf(
                "appointmentDate" to date.label,
                "appointmentDateKey" to date.key,
                "appointmentTime" to time,
                "appointmentStartMinutes" to startMinutes,
                "appointmentEndMinutes" to startMinutes + currentService.durationMinutes,
                "bookingSlotIds" to newSlotIds,
                "updatedAt" to FieldValue.serverTimestamp(),
                "rescheduledAt" to FieldValue.serverTimestamp()
            ))
            oldSlotIds.filterNot { it in newSlotIds }.forEach { transaction.delete(firestore.collection(BOOKING_SLOTS_COLLECTION).document(it)) }
            newSlotIds.filterNot { it in oldSlotIds }.forEach { slotId ->
                transaction.set(firestore.collection(BOOKING_SLOTS_COLLECTION).document(slotId), mapOf(
                    "appointmentId" to rescheduleId,
                    "customerId" to uid,
                    "businessId" to businessId,
                    "appointmentDateKey" to date.key,
                    "startMinutes" to startMinutes,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }
        }.addOnSuccessListener {
            if (!isAdded) return@addOnSuccessListener
            setLoading(view, false)
            Toast.makeText(requireContext(), R.string.success_appointment_rescheduled, Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }.addOnFailureListener { error ->
            if (!isAdded) return@addOnFailureListener
            setLoading(view, false)
            val message = when {
                error.message?.contains("QUEUE_CLOSED") == true -> R.string.error_queue_closed
                error.message?.contains("SLOT_TAKEN") == true -> R.string.error_time_just_taken
                error.message?.contains("CHANGED") == true -> R.string.error_schedule_changed
                else -> R.string.error_reschedule_appointment
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            if (message != R.string.error_reschedule_appointment) loadCurrentBusiness(view)
        }
    }

    // Purpose: Atomically assigns a queue number, reserves slots, and creates the appointment.
    private fun createAppointmentTransaction(view: View, uid: String, customerName: String, selected: ServiceOffering, date: DateOption, time: String) {
        val businessRef = firestore.collection("businesses").document(businessId)
        val counterRef = firestore.collection(QUEUE_COUNTERS_COLLECTION).document(businessId)
        val appointmentRef = firestore.collection(APPOINTMENTS_COLLECTION).document()
        firestore.runTransaction { transaction ->
            val business = transaction.get(businessRef)
            if (business.getBoolean("isQueueOpen") != true) throw IllegalStateException("QUEUE_CLOSED")
            @Suppress("UNCHECKED_CAST")
            val legacy = (business.get("services") as? List<String>).orEmpty()
            val currentService = ServiceOffering.fromFirestore(business.get("serviceCatalog"), legacy)
                .firstOrNull { it.active && (it.id == selected.id || it.name.equals(selected.name, true)) }
                ?: throw IllegalStateException("SERVICE_CHANGED")
            val currentSchedule = WeeklySchedule.fromFirestore(business.get("weeklySchedule"))
            val currentSpecialDates = SpecialDateSchedules.fromFirestore(business.get("specialDateSchedules"))
            val day = SpecialDateSchedules.effectiveDay(currentSchedule, currentSpecialDates, date.calendar, date.key)
            if (time !in WeeklySchedule.slots(day, currentService.durationMinutes)) throw IllegalStateException("SCHEDULE_CHANGED")
            val startMinutes = WeeklySchedule.minutes(time) ?: throw IllegalStateException("SCHEDULE_CHANGED")
            val slotIds = mutableListOf<String>()
            var cursor = startMinutes
            while (cursor < startMinutes + currentService.durationMinutes) {
                val slotId = "${businessId}_${date.key}_$cursor"
                val slotRef = firestore.collection(BOOKING_SLOTS_COLLECTION).document(slotId)
                if (transaction.get(slotRef).exists()) throw IllegalStateException("SLOT_TAKEN")
                slotIds += slotId
                cursor += 30
            }
            val counter = transaction.get(counterRef)
            val queueNumber = (counter.getLong("lastNumber") ?: 0L) + 1L
            transaction.set(counterRef, mapOf("businessId" to businessId, "lastNumber" to queueNumber, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            val appointment = hashMapOf<String, Any>(
                "customerId" to uid, "customerName" to customerName, "businessId" to businessId, "businessName" to businessName,
                "serviceId" to currentService.id, "serviceType" to currentService.name, "servicePriceType" to currentService.priceType,
                "priceDisplay" to currentService.priceLabel(), "serviceDurationMinutes" to currentService.durationMinutes,
                "status" to STATUS_WAITING, "queueNumber" to queueNumber, "appointmentDate" to date.label,
                "appointmentDateKey" to date.key, "appointmentTime" to time, "appointmentStartMinutes" to startMinutes,
                "appointmentEndMinutes" to startMinutes + currentService.durationMinutes, "bookingSlotIds" to slotIds,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            )
            currentService.minimumPrice?.let { appointment["serviceMinimumPrice"] = it }
            currentService.maximumPrice?.let { appointment["serviceMaximumPrice"] = it }
            transaction.set(appointmentRef, appointment)
            slotIds.forEach { slotId ->
                transaction.set(firestore.collection(BOOKING_SLOTS_COLLECTION).document(slotId), mapOf(
                    "appointmentId" to appointmentRef.id, "customerId" to uid, "businessId" to businessId,
                    "appointmentDateKey" to date.key, "startMinutes" to startMinutes, "createdAt" to FieldValue.serverTimestamp()
                ))
            }
            queueNumber
        }.addOnSuccessListener {
            if (!isAdded) return@addOnSuccessListener
            setLoading(view, false)
            Toast.makeText(requireContext(), R.string.success_booking_created, Toast.LENGTH_SHORT).show()
            (requireActivity() as MainActivity).openCustomerQueue()
        }.addOnFailureListener { error ->
            if (!isAdded) return@addOnFailureListener
            setLoading(view, false)
            val message = when {
                error.message?.contains("QUEUE_CLOSED") == true -> R.string.error_queue_closed
                error.message?.contains("SLOT_TAKEN") == true -> R.string.error_time_just_taken
                error.message?.contains("CHANGED") == true -> R.string.error_schedule_changed
                else -> R.string.error_booking_generic
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            if (message != R.string.error_booking_generic) loadCurrentBusiness(view)
        }
    }

    // Purpose: Displays booking availability or loading feedback and hides it when appropriate.
    private fun availabilityMessage(view: View, message: Int, hideWhenAvailable: Boolean = false) {
        view.findViewById<TextView>(R.id.tv_booking_availability).apply {
            text = getString(message)
            visibility = if (hideWhenAvailable) View.GONE else View.VISIBLE
        }
    }

    // Purpose: Switches the screen between loading and interactive states to prevent duplicate actions.
    private fun setLoading(view: View, loading: Boolean) {
        view.findViewById<MaterialButton>(R.id.btn_book_appointment).isEnabled = !loading
        view.findViewById<ProgressBar>(R.id.progress_booking).visibility = if (loading) View.VISIBLE else View.GONE
    }
}
