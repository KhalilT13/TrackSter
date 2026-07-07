package com.Khalil.trackster.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.customer.CustomerHomeFragment
import com.Khalil.trackster.ui.home.PlaceholderHomeFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Sign-in screen. Validates the form, signs the user in with Firebase Auth,
 * then looks up their role in Firestore's "users" collection so we know
 * which "Welcome" screen to send them to. Uses the same
 * Task/addOnSuccessListener/addOnFailureListener pattern as SignUpFragment.
 */
class SignInFragment : Fragment(R.layout.fragment_signin) {

    companion object {
        private const val ARG_ROLE = "role"
        private const val USERS_COLLECTION = "users"
        private const val FIELD_ROLE = "role"

        /**
         * Creates a SignInFragment. [role] isn't used for signing in itself -
         * it's only carried along so that if the user taps the "Sign Up" link
         * on this screen, they land back on sign up with the same role they
         * picked on the landing screen.
         */
        fun newInstance(role: String): SignInFragment {
            return SignInFragment().apply {
                arguments = Bundle().apply { putString(ARG_ROLE, role) }
            }
        }
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val roleForSignUpLink = arguments?.getString(ARG_ROLE) ?: SignUpFragment.ROLE_CUSTOMER

        view.findViewById<View>(R.id.iv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<TextView>(R.id.tv_go_signup).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SignUpFragment.newInstance(roleForSignUpLink))
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<MaterialButton>(R.id.btn_sign_in).setOnClickListener {
            onSignInClicked(view)
        }
    }

    private fun onSignInClicked(view: View) {
        val tilEmail = view.findViewById<TextInputLayout>(R.id.til_email)
        val tilPassword = view.findViewById<TextInputLayout>(R.id.til_password)

        val email = tilEmail.editText?.text?.toString()?.trim().orEmpty()
        val password = tilPassword.editText?.text?.toString().orEmpty()

        // Clear any errors left over from a previous submit attempt.
        tilEmail.error = null
        tilPassword.error = null

        var isValid = true
        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.error_field_required)
            isValid = false
        }
        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_field_required)
            isValid = false
        }
        if (!isValid) return

        setLoading(view, isLoading = true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid == null) {
                    setLoading(view, isLoading = false)
                    Toast.makeText(requireContext(), getString(R.string.error_signin_generic), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                fetchRoleAndContinue(view, uid)
            }
            .addOnFailureListener { exception ->
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), signInErrorMessage(exception), Toast.LENGTH_LONG).show()
            }
    }

    /** Looks up the signed-in user's role in Firestore, then moves on to the placeholder home screen. */
    private fun fetchRoleAndContinue(view: View, uid: String) {
        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnSuccessListener { document ->
                setLoading(view, isLoading = false)
                val role = document.getString(FIELD_ROLE) ?: SignUpFragment.ROLE_CUSTOMER
                navigateToHomeScreen(role)
            }
            .addOnFailureListener {
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), getString(R.string.error_signin_generic), Toast.LENGTH_LONG).show()
            }
    }

    /** Customers go to their real home screen; business owners still get the placeholder for now. */
    private fun navigateToHomeScreen(role: String) {
        val destination = if (role == SignUpFragment.ROLE_CUSTOMER) {
            CustomerHomeFragment()
        } else {
            PlaceholderHomeFragment.newInstance(role)
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, destination)
            .commit()
    }

    /**
     * Maps Firebase Auth sign-in failures to a single clear message. We
     * deliberately never show the raw exception text to the user.
     */
    private fun signInErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidUserException,
            is FirebaseAuthInvalidCredentialsException -> getString(R.string.error_invalid_credentials)
            is FirebaseNetworkException -> getString(R.string.error_network)
            else -> getString(R.string.error_signin_generic)
        }
    }

    /** Disables the button and shows/hides its spinner while a Firebase call is in flight. */
    private fun setLoading(view: View, isLoading: Boolean) {
        view.findViewById<MaterialButton>(R.id.btn_sign_in).isEnabled = !isLoading
        view.findViewById<ProgressBar>(R.id.progress_sign_in).visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}
