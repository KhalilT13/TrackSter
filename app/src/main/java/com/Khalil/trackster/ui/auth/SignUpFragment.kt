package com.Khalil.trackster.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.home.PlaceholderHomeFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Sign-up screen. The user arrives here after picking a role (Customer or
 * Business Owner) on [LandingFragment]. Business owners get an extra
 * "Business Name" field.
 *
 * On submit: validate the form, create a Firebase Auth account, then save a
 * matching profile document in the Firestore "users" collection. We use the
 * standard Task/addOnSuccessListener/addOnFailureListener pattern (rather
 * than coroutines) since it's the simplest to follow without extra setup.
 */
class SignUpFragment : Fragment(R.layout.fragment_signup) {

    companion object {
        private const val ARG_ROLE = "role"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_BUSINESS = "business"
        private const val USERS_COLLECTION = "users"

        /** Creates a SignUpFragment pre-loaded with which role the user picked on the landing screen. */
        fun newInstance(role: String): SignUpFragment {
            return SignUpFragment().apply {
                arguments = Bundle().apply { putString(ARG_ROLE, role) }
            }
        }
    }

    private var role: String = ROLE_CUSTOMER

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        role = arguments?.getString(ARG_ROLE) ?: ROLE_CUSTOMER
        val isBusiness = role == ROLE_BUSINESS

        // Show which role was selected on the landing screen.
        val roleLabel = getString(if (isBusiness) R.string.role_business_title else R.string.role_customer_title)
        view.findViewById<TextView>(R.id.tv_role_subtitle).text =
            getString(R.string.signup_role_subtitle_format, roleLabel)

        // Business owners get an extra field; customers don't.
        view.findViewById<TextInputLayout>(R.id.til_business_name).visibility =
            if (isBusiness) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.iv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<MaterialButton>(R.id.btn_create_account).setOnClickListener {
            onCreateAccountClicked(view, isBusiness)
        }
    }

    private fun onCreateAccountClicked(view: View, isBusiness: Boolean) {
        val tilFullName = view.findViewById<TextInputLayout>(R.id.til_full_name)
        val tilBusinessName = view.findViewById<TextInputLayout>(R.id.til_business_name)
        val tilEmail = view.findViewById<TextInputLayout>(R.id.til_email)
        val tilPassword = view.findViewById<TextInputLayout>(R.id.til_password)
        val tilConfirmPassword = view.findViewById<TextInputLayout>(R.id.til_confirm_password)

        val fullName = tilFullName.editText?.text?.toString()?.trim().orEmpty()
        val businessName = tilBusinessName.editText?.text?.toString()?.trim().orEmpty()
        val email = tilEmail.editText?.text?.toString()?.trim().orEmpty()
        val password = tilPassword.editText?.text?.toString().orEmpty()
        val confirmPassword = tilConfirmPassword.editText?.text?.toString().orEmpty()

        // Clear any errors left over from a previous submit attempt.
        tilFullName.error = null
        tilBusinessName.error = null
        tilEmail.error = null
        tilPassword.error = null
        tilConfirmPassword.error = null

        var isValid = true

        if (fullName.isEmpty()) {
            tilFullName.error = getString(R.string.error_field_required)
            isValid = false
        }

        if (isBusiness && businessName.isEmpty()) {
            tilBusinessName.error = getString(R.string.error_field_required)
            isValid = false
        }

        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.error_field_required)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.error_field_required)
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.error = getString(R.string.error_field_required)
            isValid = false
        } else if (password.isNotEmpty() && password != confirmPassword) {
            tilConfirmPassword.error = getString(R.string.error_passwords_mismatch)
            isValid = false
        }

        if (!isValid) return

        setLoading(view, isLoading = true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid == null) {
                    setLoading(view, isLoading = false)
                    Toast.makeText(requireContext(), getString(R.string.error_signup_generic), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                saveUserProfile(view, uid, fullName, email, isBusiness, businessName)
            }
            .addOnFailureListener { exception ->
                setLoading(view, isLoading = false)
                handleAuthError(exception, tilEmail, tilPassword)
            }
    }

    /** Writes the new user's profile to Firestore, then moves on to the placeholder home screen. */
    private fun saveUserProfile(
        view: View,
        uid: String,
        fullName: String,
        email: String,
        isBusiness: Boolean,
        businessName: String
    ) {
        val userProfile = hashMapOf(
            "uid" to uid,
            "email" to email,
            "role" to role,
            "displayName" to fullName,
            "businessName" to if (isBusiness) businessName else null,
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(USERS_COLLECTION).document(uid)
            .set(userProfile)
            .addOnSuccessListener {
                setLoading(view, isLoading = false)
                Toast.makeText(requireContext(), getString(R.string.success_account_created), Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, PlaceholderHomeFragment.newInstance(role))
                    .commit()
            }
            .addOnFailureListener { exception ->
                setLoading(view, isLoading = false)
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: getString(R.string.error_signup_generic),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /** Maps common Firebase Auth exceptions to a clear, field-attached error where possible. */
    private fun handleAuthError(exception: Exception, tilEmail: TextInputLayout, tilPassword: TextInputLayout) {
        when (exception) {
            is FirebaseAuthUserCollisionException -> tilEmail.error = getString(R.string.error_email_in_use)
            is FirebaseAuthWeakPasswordException -> tilPassword.error = getString(R.string.error_weak_password)
            is FirebaseAuthInvalidCredentialsException -> tilEmail.error = getString(R.string.error_invalid_email)
            is FirebaseNetworkException -> Toast.makeText(requireContext(), getString(R.string.error_network), Toast.LENGTH_LONG).show()
            else -> Toast.makeText(
                requireContext(),
                exception.localizedMessage ?: getString(R.string.error_signup_generic),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Disables the button and shows/hides its spinner while a Firebase call is in flight. */
    private fun setLoading(view: View, isLoading: Boolean) {
        view.findViewById<MaterialButton>(R.id.btn_create_account).isEnabled = !isLoading
        view.findViewById<ProgressBar>(R.id.progress_create_account).visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}
