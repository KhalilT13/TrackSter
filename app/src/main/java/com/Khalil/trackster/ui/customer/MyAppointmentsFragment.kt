package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Lists the signed-in customer's appointments, read from the Firestore
 * "appointments" collection filtered by customerId.
 */
class MyAppointmentsFragment : Fragment(R.layout.fragment_my_appointments) {

    companion object {
        private const val APPOINTMENTS_COLLECTION = "appointments"
        private const val FIELD_CUSTOMER_ID = "customerId"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.iv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<RecyclerView>(R.id.recycler_appointments).layoutManager =
            LinearLayoutManager(requireContext())

        loadAppointments(view)
    }

    private fun loadAppointments(view: View) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showAppointments(view, emptyList())
            return
        }

        firestore.collection(APPOINTMENTS_COLLECTION)
            .whereEqualTo(FIELD_CUSTOMER_ID, uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val appointments = snapshot.documents.map { document ->
                    Appointment(
                        businessName = document.getString("businessName").orEmpty(),
                        serviceType = document.getString("serviceType").orEmpty(),
                        status = document.getString("status").orEmpty()
                    )
                }
                showAppointments(view, appointments)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), getString(R.string.error_my_appointments_generic), Toast.LENGTH_LONG).show()
                showAppointments(view, emptyList())
            }
    }

    /** Shows the list when there are appointments, or the empty-state message otherwise. */
    private fun showAppointments(view: View, appointments: List<Appointment>) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_appointments)
        val emptyState = view.findViewById<TextView>(R.id.tv_empty_state)

        if (appointments.isEmpty()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            recycler.adapter = AppointmentAdapter(appointments)
        }
    }
}
