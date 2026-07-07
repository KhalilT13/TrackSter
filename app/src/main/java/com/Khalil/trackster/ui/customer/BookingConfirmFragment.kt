package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Confirmation screen shown after the customer taps "Book / Join Queue" on a
 * business. Confirming writes a new document to the Firestore "appointments"
 * collection; canceling (or the back arrow) just returns to the home screen.
 */
class BookingConfirmFragment : Fragment(R.layout.fragment_booking_confirm) {

    companion object {
        private const val ARG_BUSINESS_NAME = "business_name"
        private const val ARG_SERVICE_TYPE = "service_type"
        private const val USERS_COLLECTION = "users"
        private const val APPOINTMENTS_COLLECTION = "appointments"
        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val STATUS_WAITING = "waiting"

        /** Creates a BookingConfirmFragment for the business the user tapped "Book / Join Queue" on. */
        fun newInstance(businessName: String, serviceType: String): BookingConfirmFragment {
            return BookingConfirmFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BUSINESS_NAME, businessName)
                    putString(ARG_SERVICE_TYPE, serviceType)
                }
            }
        }
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var businessName: String = ""
    private var serviceType: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        businessName = arguments?.getString(ARG_BUSINESS_NAME).orEmpty()
        serviceType = arguments?.getString(ARG_SERVICE_TYPE).orEmpty()

        view.findViewById<TextView>(R.id.tv_business_name).text = businessName
        view.findViewById<TextView>(R.id.tv_service_type).text = serviceType

        view.findViewById<View>(R.id.iv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<MaterialButton>(R.id.btn_confirm).setOnClickListener {
            onConfirmClicked(view)
        }
    }

    private fun onConfirmClicked(view: View) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), getString(R.string.error_booking_generic), Toast.LENGTH_LONG).show()
            return
        }

        setLoading(view, isLoading = true)

        // Look up the customer's display name the same way CustomerHomeFragment
        // does, so the appointment record has a readable name, not just a UID.
        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnSuccessListener { document ->
                val customerName = document.getString(FIELD_DISPLAY_NAME) ?: auth.currentUser?.email ?: uid
                createAppointment(view, uid, customerName)
            }
            .addOnFailureListener {
                val customerName = auth.currentUser?.email ?: uid
                createAppointment(view, uid, customerName)
            }
    }

    /** Writes the appointment document, then hands off to the "My Appointments" list. */
    private fun createAppointment(view: View, uid: String, customerName: String) {
        val appointment = hashMapOf(
            "customerId" to uid,
            "customerName" to customerName,
            "businessName" to businessName,
            "serviceType" to serviceType,
            "status" to STATUS_WAITING,
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(APPOINTMENTS_COLLECTION)
            .add(appointment)
            .addOnSuccessListener {
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), getString(R.string.success_booking_created), Toast.LENGTH_SHORT).show()
                // addToBackStack keeps this a normal forward hop (Home -> Confirm -> My
                // Appointments), consuming the Home -> Confirm entry properly instead of
                // orphaning it - an orphaned entry is what causes screens to overlap on Back.
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MyAppointmentsFragment())
                    .addToBackStack(null)
                    .commit()
            }
            .addOnFailureListener {
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), getString(R.string.error_booking_generic), Toast.LENGTH_LONG).show()
            }
    }

    /** Disables both buttons and shows/hides the spinner while the Firestore calls are in flight. */
    private fun setLoading(view: View, isLoading: Boolean) {
        view.findViewById<MaterialButton>(R.id.btn_confirm).isEnabled = !isLoading
        view.findViewById<MaterialButton>(R.id.btn_cancel).isEnabled = !isLoading
        view.findViewById<ProgressBar>(R.id.progress_confirm).visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}
