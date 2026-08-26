package com.Khalil.trackster.ui.business

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.Khalil.trackster.model.DaySchedule
import com.Khalil.trackster.model.SpecialDateSchedule
import com.Khalil.trackster.model.SpecialDateSchedules
import com.Khalil.trackster.model.WeeklySchedule
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BusinessSpecialDatesFragment : Fragment(R.layout.fragment_business_special_dates) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var items = linkedMapOf<String, SpecialDateSchedule>()

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<MaterialButton>(R.id.btn_add_special_date).setOnClickListener { chooseDate(view) }
        load(view)
    }

    // Purpose: Loads the saved Firestore data required by this screen for display and editing.
    private fun load(view: View) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            items = SpecialDateSchedules.fromFirestore(doc.get("specialDateSchedules"))
            render(view)
        }.addOnFailureListener { if (isAdded) toast(R.string.error_special_dates_save) }
    }

    // Purpose: Refreshes this screen so its views reflect the latest data and selections.
    private fun render(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.special_dates_container)
        val today = dateKeyFormat.format(Calendar.getInstance().time)
        val visible = items.values.filter { it.dateKey >= today }.sortedBy { it.dateKey }
        container.removeAllViews()
        view.findViewById<View>(R.id.tv_special_dates_empty).visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        visible.forEach { item ->
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_special_date, container, false)
            row.findViewById<TextView>(R.id.tv_special_date_title).text = item.title.ifBlank {
                getString(if (item.schedule.open) R.string.special_date_default_custom else R.string.special_date_default_closed)
            }
            row.findViewById<TextView>(R.id.tv_special_date_date).text = displayDate(item.dateKey)
            row.findViewById<TextView>(R.id.tv_special_date_hours).text = hoursLabel(item.schedule)
            row.setOnClickListener { showEditor(view, item.dateKey, item) }
            row.findViewById<View>(R.id.iv_delete_special_date).setOnClickListener { confirmDelete(view, item) }
            container.addView(row)
        }
    }

    // Purpose: Opens a date picker for creating a special schedule override.
    private fun chooseDate(view: View) {
        val initial = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val chosen = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            val key = dateKeyFormat.format(chosen.time)
            showEditor(view, key, items[key])
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        }.show()
    }

    // Purpose: Opens the editor dialog and preloads values when an existing item is being edited.
    private fun showEditor(host: View, dateKey: String, existing: SpecialDateSchedule?) {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_special_date, null)
        val closed = content.findViewById<MaterialSwitch>(R.id.switch_special_closed)
        val custom = content.findViewById<View>(R.id.special_custom_hours_container)
        val hasBreak = content.findViewById<MaterialSwitch>(R.id.switch_special_break)
        val breakRow = content.findViewById<View>(R.id.special_break_hours_row)
        val open = content.findViewById<MaterialButton>(R.id.btn_special_open)
        val close = content.findViewById<MaterialButton>(R.id.btn_special_close)
        val breakStart = content.findViewById<MaterialButton>(R.id.btn_special_break_start)
        val breakEnd = content.findViewById<MaterialButton>(R.id.btn_special_break_end)
        content.findViewById<TextInputLayout>(R.id.til_special_date_title).editText?.setText(existing?.title.orEmpty())
        existing?.schedule?.let { day ->
            closed.isChecked = !day.open
            open.text = day.openTime; close.text = day.closeTime
            hasBreak.isChecked = day.breakEnabled; breakStart.text = day.breakStart; breakEnd.text = day.breakEnd
        }
        // Purpose: Synchronizes the special-date editor with open, closed, and optional-break settings.
        fun refresh() {
            custom.visibility = if (closed.isChecked) View.GONE else View.VISIBLE
            breakRow.visibility = if (!closed.isChecked && hasBreak.isChecked) View.VISIBLE else View.GONE
        }
        closed.setOnCheckedChangeListener { _, _ -> refresh() }
        hasBreak.setOnCheckedChangeListener { _, _ -> refresh() }
        listOf(open, close, breakStart, breakEnd).forEach { button -> button.setOnClickListener { pickTime(button) } }
        refresh()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.special_date_edit_title, displayDate(dateKey)))
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save_special_date, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val day = if (closed.isChecked) DaySchedule(false) else DaySchedule(
                    open = true, openTime = open.text.toString(), closeTime = close.text.toString(),
                    breakEnabled = hasBreak.isChecked, breakStart = breakStart.text.toString(), breakEnd = breakEnd.text.toString()
                )
                if (!WeeklySchedule.isValid(day)) { toast(R.string.error_special_date_hours); return@setOnClickListener }
                val title = content.findViewById<TextInputLayout>(R.id.til_special_date_title).editText?.text?.toString()?.trim().orEmpty()
                    .ifBlank { getString(if (day.open) R.string.special_date_default_custom else R.string.special_date_default_closed) }
                items[dateKey] = SpecialDateSchedule(dateKey, title, day)
                save(host) { dialog.dismiss() }
            }
        }
        dialog.show()
    }

    // Purpose: Requests confirmation before removing a special-date override.
    private fun confirmDelete(view: View, item: SpecialDateSchedule) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.special_date_delete_title)
            .setMessage(getString(R.string.special_date_delete_message, displayDate(item.dateKey), item.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete_special_date) { _, _ -> items.remove(item.dateKey); save(view) }
            .show()
    }

    // Purpose: Validates the current values and stores the changes in the business document.
    private fun save(view: View, success: (() -> Unit)? = null) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).set(mapOf(
            "ownerId" to uid,
            "specialDateSchedules" to SpecialDateSchedules.toFirestore(items),
            "updatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge()).addOnSuccessListener {
            if (!isAdded) return@addOnSuccessListener
            render(view); toast(R.string.success_special_dates_saved); success?.invoke()
        }.addOnFailureListener { if (isAdded) toast(R.string.error_special_dates_save) }
    }

    // Purpose: Opens a time picker and writes the selected time into the target button.
    private fun pickTime(button: MaterialButton) {
        val initial = WeeklySchedule.minutes(button.text.toString()) ?: 9 * 60
        TimePickerDialog(requireContext(), { _, hour, minute -> button.text = WeeklySchedule.timeLabel(hour * 60 + minute) }, initial / 60, initial % 60, true).show()
    }

    // Purpose: Formats an internal date key as a localized, readable date.
    private fun displayDate(key: String): String = runCatching {
        val date = dateKeyFormat.parse(key) ?: return@runCatching key
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
    }.getOrDefault(key)

    // Purpose: Returns a readable description of special opening hours and any break.
    private fun hoursLabel(day: DaySchedule): String = when {
        !day.open -> getString(R.string.special_date_closed_label)
        day.breakEnabled -> getString(R.string.special_date_break_format, day.openTime, day.closeTime, day.breakStart, day.breakEnd)
        else -> getString(R.string.special_date_hours_format, day.openTime, day.closeTime)
    }

    // Purpose: Shows a short localized feedback message to the user.
    private fun toast(id: Int) = Toast.makeText(requireContext(), id, Toast.LENGTH_SHORT).show()
}
