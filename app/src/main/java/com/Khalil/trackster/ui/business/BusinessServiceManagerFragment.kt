package com.Khalil.trackster.ui.business

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.Khalil.trackster.model.ServiceOffering
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class BusinessServiceManagerFragment : Fragment(R.layout.fragment_business_service_manager) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null
    private var services = emptyList<ServiceOffering>()

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<MaterialButton>(R.id.btn_add_service).setOnClickListener { showEditor() }
        val uid = auth.currentUser?.uid ?: return
        listener = firestore.collection("businesses").document(uid).addSnapshotListener { doc, error ->
            if (!isAdded) return@addSnapshotListener
            if (error != null) { toast(R.string.error_services_generic); return@addSnapshotListener }
            @Suppress("UNCHECKED_CAST") val legacy = (doc?.get("services") as? List<String>).orEmpty()
            services = ServiceOffering.fromFirestore(doc?.get("serviceCatalog"), legacy).distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
            render(view)
        }
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() { listener?.remove(); listener = null; super.onDestroyView() }

    // Purpose: Refreshes this screen so its views reflect the latest data and selections.
    private fun render(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.services_container)
        container.removeAllViews()
        services.forEach { service ->
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_manage_service, container, false)
            item.findViewById<TextView>(R.id.tv_service_name).text = service.name
            item.findViewById<TextView>(R.id.tv_service_price).text = service.priceLabel()
            item.findViewById<TextView>(R.id.tv_service_duration).text = getString(R.string.service_duration_format, service.durationMinutes)
            item.findViewById<MaterialButton>(R.id.btn_edit_service).setOnClickListener { showEditor(service) }
            item.findViewById<MaterialButton>(R.id.btn_delete_service).setOnClickListener { remove(service) }
            container.addView(item)
        }
        view.findViewById<TextView>(R.id.tv_services_empty).visibility = if (services.isEmpty()) View.VISIBLE else View.GONE
    }

    // Purpose: Opens the editor dialog and preloads values when an existing item is being edited.
    private fun showEditor(existing: ServiceOffering? = null) {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_service_editor, null)
        val name = content.findViewById<TextInputLayout>(R.id.til_service_name)
        val minimum = content.findViewById<TextInputLayout>(R.id.til_minimum_price)
        val maximum = content.findViewById<TextInputLayout>(R.id.til_maximum_price)
        val duration = content.findViewById<TextInputLayout>(R.id.til_service_duration)
        val pricing = content.findViewById<RadioGroup>(R.id.radio_price_type)
        name.editText?.setText(existing?.name.orEmpty())
        minimum.editText?.setText(existing?.minimumPrice?.let(::plain).orEmpty())
        maximum.editText?.setText(existing?.maximumPrice?.let(::plain).orEmpty())
        duration.editText?.setText((existing?.durationMinutes ?: 30).toString())
        pricing.check(when (existing?.priceType) {
            ServiceOffering.PRICE_FIXED -> R.id.radio_fixed_price
            ServiceOffering.PRICE_RANGE -> R.id.radio_price_range
            else -> R.id.radio_contact_price
        })
        // Purpose: Shows and enables the price fields required by the selected pricing type.
        fun updateFields() {
            val type = type(pricing)
            content.findViewById<View>(R.id.price_fields).visibility = if (type == ServiceOffering.PRICE_CONTACT) View.GONE else View.VISIBLE
            maximum.visibility = if (type == ServiceOffering.PRICE_RANGE) View.VISIBLE else View.GONE
            minimum.hint = getString(if (type == ServiceOffering.PRICE_RANGE) R.string.label_minimum_price else R.string.label_fixed_price)
        }
        pricing.setOnCheckedChangeListener { _, _ -> updateFields() }; updateFields()
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(if (existing == null) R.string.add_service_title else R.string.edit_service_title)
            .setView(content).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                name.error = null; minimum.error = null; maximum.error = null; duration.error = null
                val serviceName = name.editText?.text?.toString()?.trim().orEmpty()
                val priceType = type(pricing)
                val min = minimum.editText?.text?.toString()?.toDoubleOrNull()
                val max = maximum.editText?.text?.toString()?.toDoubleOrNull()
                val minutes = duration.editText?.text?.toString()?.toIntOrNull()
                when {
                    serviceName.isBlank() -> name.error = getString(R.string.error_field_required)
                    serviceName.length > 40 -> name.error = getString(R.string.error_service_too_long)
                    services.any { it.id != existing?.id && it.name.equals(serviceName, true) } -> name.error = getString(R.string.error_service_duplicate)
                    priceType != ServiceOffering.PRICE_CONTACT && (min == null || min < 0) -> minimum.error = getString(R.string.error_invalid_price)
                    priceType == ServiceOffering.PRICE_RANGE && (max == null || max < (min ?: 0.0)) -> maximum.error = getString(R.string.error_invalid_price_range)
                    minutes == null || minutes !in 5..480 -> duration.error = getString(R.string.error_invalid_duration)
                    else -> {
                        val updated = ServiceOffering(existing?.id ?: UUID.randomUUID().toString(), serviceName, priceType,
                            if (priceType == ServiceOffering.PRICE_CONTACT) null else min,
                            if (priceType == ServiceOffering.PRICE_RANGE) max else null, minutes, existing?.active ?: true)
                        save(if (existing == null) services + updated else services.map { if (it.id == existing.id) updated else it }) { dialog.dismiss() }
                    }
                }
            }
        }
        dialog.show()
    }

    // Purpose: Removes the selected item from stored data and refreshes the screen.
    private fun remove(service: ServiceOffering) {
        if (services.size <= 1) { toast(R.string.error_service_last); return }
        MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.delete_service_title, service.name))
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.action_delete_service) { _, _ -> save(services.filterNot { it.id == service.id }) }.show()
    }

    // Purpose: Validates the current values and stores the changes in the business document.
    private fun save(updated: List<ServiceOffering>, success: (() -> Unit)? = null) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).set(mapOf(
            "ownerId" to uid, "services" to updated.filter { it.active }.map { it.name },
            "serviceCatalog" to updated.map { it.toMap() }, "updatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge()).addOnSuccessListener { if (isAdded) success?.invoke() }
            .addOnFailureListener { if (isAdded) toast(R.string.error_services_generic) }
    }

    // Purpose: Returns the selected pricing type: fixed, range, or starting/contact price.
    private fun type(group: RadioGroup) = when (group.checkedRadioButtonId) {
        R.id.radio_fixed_price -> ServiceOffering.PRICE_FIXED
        R.id.radio_price_range -> ServiceOffering.PRICE_RANGE
        else -> ServiceOffering.PRICE_CONTACT
    }
    // Purpose: Formats a numeric price without unnecessary decimal digits.
    private fun plain(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    // Purpose: Shows a short localized feedback message to the user.
    private fun toast(id: Int) = Toast.makeText(requireContext(), id, Toast.LENGTH_SHORT).show()
}
