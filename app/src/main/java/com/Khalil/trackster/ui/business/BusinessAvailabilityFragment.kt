package com.Khalil.trackster.ui.business

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.Khalil.trackster.model.DaySchedule
import com.Khalil.trackster.model.SpecialDateSchedules
import com.Khalil.trackster.model.WeeklySchedule
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class BusinessAvailabilityFragment : Fragment(R.layout.fragment_business_availability) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var schedule = WeeklySchedule.default()

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<MaterialButton>(R.id.btn_save_availability).setOnClickListener { save(view) }
        view.findViewById<MaterialButton>(R.id.btn_manage_special_dates).setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.fragment_container, BusinessSpecialDatesFragment()).addToBackStack(null).commit()
        }
        load(view)
    }

    // Purpose: Loads the saved Firestore data required by this screen for display and editing.
    private fun load(view: View) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            schedule = WeeklySchedule.fromFirestore(doc.get("weeklySchedule"))
            view.findViewById<MaterialSwitch>(R.id.switch_queue_open).isChecked = doc.getBoolean("isQueueOpen") ?: true
            val special = SpecialDateSchedules.fromFirestore(doc.get("specialDateSchedules"))
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val upcoming = special.values.count { it.dateKey >= today }
            view.findViewById<android.widget.TextView>(R.id.tv_special_dates_preview).text = if (upcoming == 0) {
                getString(R.string.special_dates_empty)
            } else resources.getQuantityString(R.plurals.special_dates_upcoming_count, upcoming, upcoming)
            render(view)
        }.addOnFailureListener { if (isAdded) toast(R.string.error_business_profile_generic) }
    }

    // Purpose: Refreshes this screen so its views reflect the latest data and selections.
    private fun render(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.schedule_days_container)
        val labels = listOf(R.string.day_sunday, R.string.day_monday, R.string.day_tuesday, R.string.day_wednesday, R.string.day_thursday, R.string.day_friday, R.string.day_saturday)
        container.removeAllViews()
        WeeklySchedule.dayKeys.forEachIndexed { index, key ->
            val day = schedule[key] ?: DaySchedule(false)
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_business_day_schedule, container, false)
            row.tag = key
            val openSwitch = row.findViewById<MaterialSwitch>(R.id.switch_day_open)
            val breakSwitch = row.findViewById<MaterialSwitch>(R.id.switch_break)
            val open = row.findViewById<MaterialButton>(R.id.btn_open_time)
            val close = row.findViewById<MaterialButton>(R.id.btn_close_time)
            val breakStart = row.findViewById<MaterialButton>(R.id.btn_break_start)
            val breakEnd = row.findViewById<MaterialButton>(R.id.btn_break_end)
            val dayName = getString(labels[index])
            openSwitch.isChecked = day.open
            openSwitch.text = getString(if (day.open) R.string.day_open_format else R.string.day_closed_format, dayName)
            breakSwitch.isChecked = day.breakEnabled
            open.text = day.openTime; close.text = day.closeTime; breakStart.text = day.breakStart; breakEnd.text = day.breakEnd
            updateState(row)
            openSwitch.setOnCheckedChangeListener { _, checked ->
                openSwitch.text = getString(if (checked) R.string.day_open_format else R.string.day_closed_format, dayName)
                updateState(row)
            }
            breakSwitch.setOnCheckedChangeListener { _, _ -> updateState(row) }
            listOf(open, close, breakStart, breakEnd).forEach { button -> button.setOnClickListener { pickTime(button) } }
            container.addView(row)
        }
    }

    // Purpose: Enables or disables time and break controls according to the day's open state.
    private fun updateState(row: View) {
        val open = row.findViewById<MaterialSwitch>(R.id.switch_day_open).isChecked
        val hasBreak = row.findViewById<MaterialSwitch>(R.id.switch_break).isChecked
        listOf(R.id.tv_working_hours_label, R.id.day_hours_row, R.id.break_divider, R.id.switch_break, R.id.tv_break_help).forEach {
            row.findViewById<View>(it).visibility = if (open) View.VISIBLE else View.GONE
        }
        row.findViewById<View>(R.id.break_hours_row).visibility = if (open && hasBreak) View.VISIBLE else View.GONE
    }

    // Purpose: Opens a time picker and writes the selected time into the target button.
    private fun pickTime(button: MaterialButton) {
        val initial = WeeklySchedule.minutes(button.text.toString()) ?: 9 * 60
        TimePickerDialog(requireContext(), { _, hour, minute -> button.text = WeeklySchedule.timeLabel(hour * 60 + minute) }, initial / 60, initial % 60, true).show()
    }

    // Purpose: Reads the current input values and returns them as a structured model.
    private fun read(view: View): LinkedHashMap<String, DaySchedule> {
        val result = linkedMapOf<String, DaySchedule>()
        val container = view.findViewById<LinearLayout>(R.id.schedule_days_container)
        for (index in 0 until container.childCount) {
            val row = container.getChildAt(index)
            result[row.tag as String] = DaySchedule(
                row.findViewById<MaterialSwitch>(R.id.switch_day_open).isChecked,
                row.findViewById<MaterialButton>(R.id.btn_open_time).text.toString(),
                row.findViewById<MaterialButton>(R.id.btn_close_time).text.toString(),
                row.findViewById<MaterialSwitch>(R.id.switch_break).isChecked,
                row.findViewById<MaterialButton>(R.id.btn_break_start).text.toString(),
                row.findViewById<MaterialButton>(R.id.btn_break_end).text.toString()
            )
        }
        return result
    }

    // Purpose: Validates the current values and stores the changes in the business document.
    private fun save(view: View) {
        val uid = auth.currentUser?.uid ?: return
        val updated = read(view)
        if (updated.values.any { !WeeklySchedule.isValid(it) }) { toast(R.string.error_invalid_work_hours); return }
        firestore.collection("businesses").document(uid).set(mapOf(
            "ownerId" to uid, "weeklySchedule" to WeeklySchedule.toFirestore(updated),
            "openingHours" to WeeklySchedule.summary(updated),
            "isQueueOpen" to view.findViewById<MaterialSwitch>(R.id.switch_queue_open).isChecked,
            "updatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge()).addOnSuccessListener { if (isAdded) toast(R.string.success_business_profile_saved) }
            .addOnFailureListener { if (isAdded) toast(R.string.error_business_profile_generic) }
    }

    // Purpose: Shows a short localized feedback message to the user.
    private fun toast(id: Int) = Toast.makeText(requireContext(), id, Toast.LENGTH_SHORT).show()
}
