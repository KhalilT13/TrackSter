package com.Khalil.trackster.ui.business

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class BusinessLocationFragment : Fragment(R.layout.fragment_business_location) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        val parking = view.findViewById<MaterialSwitch>(R.id.switch_parking)
        val accessibleParking = view.findViewById<MaterialSwitch>(R.id.switch_accessible_parking)
        parking.setOnCheckedChangeListener { _, checked ->
            accessibleParking.isEnabled = checked
            if (!checked) accessibleParking.isChecked = false
        }
        view.findViewById<MaterialButton>(R.id.btn_save_location).setOnClickListener { save(view) }
        load(view)
    }

    // Purpose: Loads the saved Firestore data required by this screen for display and editing.
    private fun load(view: View) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            input(view, R.id.til_location_address).editText?.setText(doc.getString("address").orEmpty())
            input(view, R.id.til_location_city).editText?.setText(doc.getString("city").orEmpty())
            input(view, R.id.til_location_notes).editText?.setText(doc.getString("locationNotes").orEmpty())
            view.findViewById<MaterialSwitch>(R.id.switch_parking).isChecked = doc.getBoolean("hasParking") == true
            view.findViewById<MaterialSwitch>(R.id.switch_accessible_parking).isChecked = doc.getBoolean("hasAccessibleParking") == true
            view.findViewById<MaterialSwitch>(R.id.switch_accessible_entrance).isChecked = doc.getBoolean("accessibleEntrance") == true
            view.findViewById<MaterialSwitch>(R.id.switch_accessible_restroom).isChecked = doc.getBoolean("accessibleRestroom") == true
            view.findViewById<MaterialSwitch>(R.id.switch_elevator).isChecked = doc.getBoolean("elevatorAvailable") == true
        }
    }

    // Purpose: Validates the current values and stores the changes in the business document.
    private fun save(view: View) {
        val uid = auth.currentUser?.uid ?: return
        val addressLayout = input(view, R.id.til_location_address)
        val cityLayout = input(view, R.id.til_location_city)
        val address = addressLayout.editText?.text?.toString()?.trim().orEmpty()
        val city = cityLayout.editText?.text?.toString()?.trim().orEmpty()
        addressLayout.error = null
        cityLayout.error = null
        if (address.isBlank() != city.isBlank()) {
            if (address.isBlank()) addressLayout.error = getString(R.string.error_location_incomplete)
            if (city.isBlank()) cityLayout.error = getString(R.string.error_location_incomplete)
            return
        }
        val button = view.findViewById<MaterialButton>(R.id.btn_save_location)
        button.isEnabled = false
        val parking = view.findViewById<MaterialSwitch>(R.id.switch_parking).isChecked
        val values = mapOf(
            "ownerId" to uid,
            "address" to address,
            "city" to city,
            "locationNotes" to input(view, R.id.til_location_notes).editText?.text?.toString()?.trim().orEmpty(),
            "hasParking" to parking,
            "hasAccessibleParking" to (parking && view.findViewById<MaterialSwitch>(R.id.switch_accessible_parking).isChecked),
            "accessibleEntrance" to view.findViewById<MaterialSwitch>(R.id.switch_accessible_entrance).isChecked,
            "accessibleRestroom" to view.findViewById<MaterialSwitch>(R.id.switch_accessible_restroom).isChecked,
            "elevatorAvailable" to view.findViewById<MaterialSwitch>(R.id.switch_elevator).isChecked,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("businesses").document(uid).set(values, SetOptions.merge())
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                button.isEnabled = true
                Toast.makeText(requireContext(), R.string.success_location_saved, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                button.isEnabled = true
                Toast.makeText(requireContext(), R.string.error_location_save, Toast.LENGTH_SHORT).show()
            }
    }

    // Purpose: Returns the TextInputLayout identified by the supplied view ID.
    private fun input(view: View, id: Int) = view.findViewById<TextInputLayout>(id)
}
