package com.Khalil.trackster.ui.customer

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.auth.LandingFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The customer's home screen after signing in or up. Shows a greeting, a
 * placeholder list of businesses to browse (real Firestore business data
 * comes in a later step), and entry points into booking and "My Appointments".
 */
class CustomerHomeFragment : Fragment(R.layout.fragment_customer_home) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val FIELD_DISPLAY_NAME = "displayName"
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadGreeting(view)
        setUpBusinessList(view)

        view.findViewById<ImageView>(R.id.iv_my_appointments).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MyAppointmentsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<ImageView>(R.id.iv_sign_out).setOnClickListener {
            signOut()
        }
    }

    /** Reads the signed-in user's display name from Firestore and shows "Hi, [name]!". */
    private fun loadGreeting(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tv_greeting)
        val uid = auth.currentUser?.uid

        if (uid == null) {
            tvGreeting.text = getString(R.string.customer_home_greeting_format, getString(R.string.customer_home_default_name))
            return
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnSuccessListener { document ->
                val name = document.getString(FIELD_DISPLAY_NAME)
                    ?: auth.currentUser?.email
                    ?: getString(R.string.customer_home_default_name)
                tvGreeting.text = getString(R.string.customer_home_greeting_format, name)
            }
            .addOnFailureListener {
                // Firestore lookup failed (e.g. offline) - fall back to whatever Auth knows.
                val name = auth.currentUser?.email ?: getString(R.string.customer_home_default_name)
                tvGreeting.text = getString(R.string.customer_home_greeting_format, name)
            }
    }

    /** Fills the list with hardcoded placeholder businesses - swapped for real Firestore data later. */
    private fun setUpBusinessList(view: View) {
        val dummyBusinesses = listOf(
            Business("City Clinic", "General Checkup"),
            Business("Sharp Cuts Barber", "Haircut"),
            Business("Auto Fix Garage", "Oil Change"),
            Business("Glow Beauty Salon", "Manicure")
        )

        val adapter = BusinessAdapter(dummyBusinesses) { business ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BookingConfirmFragment.newInstance(business.name, business.serviceType))
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<RecyclerView>(R.id.recycler_businesses).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    /** Signs the user out and clears the back stack so "back" can't return to a signed-in screen. */
    private fun signOut() {
        auth.signOut()
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LandingFragment())
            .commit()
    }
}
