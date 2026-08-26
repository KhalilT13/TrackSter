package com.Khalil.trackster.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R
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
        // Purpose: Creates a fragment instance containing the navigation arguments it requires.
        fun newInstance(role: String): SignInFragment {
            return SignInFragment().apply {
                arguments = Bundle().apply { putString(ARG_ROLE, role) }
            }
        }
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
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

    // Purpose: Validates credentials and signs in with Firebase Email/Password Authentication.
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
                // The user may have navigated away (e.g. pressed Back) during the network call;
                // touching views/getString()/requireActivity() while detached would crash.
                if (!isAdded) return@addOnSuccessListener
                val uid = authResult.user?.uid
                if (uid == null) {
                    setLoading(view, isLoading = false)
                    Toast.makeText(requireContext(), getString(R.string.error_signin_generic), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                fetchRoleAndContinue(view, uid)
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), signInErrorMessage(exception), Toast.LENGTH_LONG).show()
            }
    }

    /** Looks up the signed-in user's role in Firestore, then enters the matching main app. */
    // Purpose: Loads the user's role from Firestore and continues only when it matches the selected role.
    private fun fetchRoleAndContinue(view: View, uid: String) {
        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                setLoading(view, isLoading = false)
                val role = document.getString(FIELD_ROLE) ?: SignUpFragment.ROLE_CUSTOMER
                navigateToHomeScreen(role)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), getString(R.string.error_signin_generic), Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Hands off to MainActivity, which shows the bottom nav and the correct first tab for the
     * role (Home for customers, Dashboard for business owners) and clears the auth back stack.
     */
    // Purpose: Opens the main application interface for the authenticated user's role.
    private fun navigateToHomeScreen(role: String) {
        (requireActivity() as MainActivity).showMainApp(role)
    }

    /**
     * Maps Firebase Auth sign-in failures to a single clear message. We
     * deliberately never show the raw exception text to the user.
     */
    // Purpose: Maps a Firebase authentication exception to a clear localized error message.
    private fun signInErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidUserException,
            is FirebaseAuthInvalidCredentialsException -> getString(R.string.error_invalid_credentials)
            is FirebaseNetworkException -> getString(R.string.error_network)
            else -> getString(R.string.error_signin_generic)
        }
    }

    /** Disables the button and shows/hides its spinner while a Firebase call is in flight. */
    // Purpose: Switches the screen between loading and interactive states to prevent duplicate actions.
    private fun setLoading(view: View, isLoading: Boolean) {
        view.findViewById<MaterialButton>(R.id.btn_sign_in).isEnabled = !isLoading
        view.findViewById<ProgressBar>(R.id.progress_sign_in).visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}
