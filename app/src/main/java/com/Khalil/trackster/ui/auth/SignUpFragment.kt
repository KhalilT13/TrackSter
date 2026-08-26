package com.Khalil.trackster.ui.auth

import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R
import com.Khalil.trackster.model.ServiceOffering
import com.Khalil.trackster.model.WeeklySchedule
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

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
        private const val BUSINESSES_COLLECTION = "businesses"

        /** Creates a SignUpFragment pre-loaded with which role the user picked on the landing screen. */
        // Purpose: Creates a fragment instance containing the navigation arguments it requires.
        fun newInstance(role: String): SignUpFragment {
            return SignUpFragment().apply {
                arguments = Bundle().apply { putString(ARG_ROLE, role) }
            }
        }
    }

    private var role: String = ROLE_CUSTOMER

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private var selectedLogoUri: Uri? = null
    private val logoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedLogoUri = uri
            view?.findViewById<ImageView>(R.id.iv_signup_logo)?.apply {
                imageTintList = null; setPadding(0, 0, 0, 0); setImageURI(uri)
            }
        }
    }

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
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
        view.findViewById<View>(R.id.signup_logo_section).visibility = if (isBusiness) View.VISIBLE else View.GONE
        view.findViewById<MaterialButton>(R.id.btn_choose_signup_logo).setOnClickListener { logoPicker.launch("image/*") }

        view.findViewById<View>(R.id.iv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<MaterialButton>(R.id.btn_create_account).setOnClickListener {
            onCreateAccountClicked(view, isBusiness)
        }
    }

    // Purpose: Validates registration fields and creates a Firebase Authentication account.
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
                // Bail out if the user navigated away during the network call - otherwise the
                // views/getString()/requireActivity() calls below would crash while detached.
                if (!isAdded) return@addOnSuccessListener
                val uid = authResult.user?.uid
                if (uid == null) {
                    setLoading(view, isLoading = false)
                    Toast.makeText(requireContext(), getString(R.string.error_signup_generic), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                saveUserProfile(view, uid, fullName, email, isBusiness, businessName)
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                setLoading(view, isLoading = false)
                handleAuthError(exception, tilEmail, tilPassword)
            }
    }

    /** Writes the private user profile and public business profile, then enters the main app. */
    // Purpose: Stores the private user document and, for businesses, the public business profile.
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
            "isQueueOpen" to isBusiness,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = firestore.batch()
        batch.set(firestore.collection(USERS_COLLECTION).document(uid), userProfile)
        batch.set(firestore.collection("publicProfiles").document(uid), mapOf(
            "uid" to uid,
            "displayName" to fullName,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        if (isBusiness) {
            val defaultService = ServiceOffering(name = getString(R.string.default_service_name))
            val defaultSchedule = WeeklySchedule.default()
            batch.set(
                firestore.collection(BUSINESSES_COLLECTION).document(uid),
                hashMapOf(
                    "ownerId" to uid,
                    "businessName" to businessName,
                    "services" to listOf(defaultService.name),
                    "serviceCatalog" to listOf(defaultService.toMap()),
                    "isQueueOpen" to true,
                    "weeklySchedule" to WeeklySchedule.toFirestore(defaultSchedule),
                    "openingHours" to WeeklySchedule.summary(defaultSchedule),
                    "photoUrls" to emptyList<String>(),
                    "logoUrl" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.commit()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                if (isBusiness && selectedLogoUri != null) uploadSignupLogo(view, uid) else finishSignup(view)
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                setLoading(view, isLoading = false)
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: getString(R.string.error_signup_generic),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // Purpose: Uploads an optional sign-up logo and saves its URL in the business profile.
    private fun uploadSignupLogo(view: View, uid: String) {
        val uri = selectedLogoUri ?: run { finishSignup(view); return }
        val ref = storage.reference.child("businesses/$uid/logo/${UUID.randomUUID()}")
        ref.putFile(uri).continueWithTask { task -> if (!task.isSuccessful) throw task.exception ?: IllegalStateException(); ref.downloadUrl }
            .continueWithTask { task ->
                val url = task.result.toString()
                firestore.collection(BUSINESSES_COLLECTION).document(uid).update("logoUrl", url)
            }.addOnCompleteListener { finishSignup(view) }
    }

    // Purpose: Completes registration after required profile data and any optional logo are saved.
    private fun finishSignup(view: View) {
        if (!isAdded) return
        setLoading(view, isLoading = false)
        Toast.makeText(requireContext(), getString(R.string.success_account_created), Toast.LENGTH_SHORT).show()
        navigateToHomeScreen(role)
    }

    /**
     * Hands off to MainActivity, which shows the bottom nav and the correct first tab for the
     * role (Home for customers, Dashboard for business owners) and clears the auth back stack.
     */
    // Purpose: Opens the main application interface for the authenticated user's role.
    private fun navigateToHomeScreen(role: String) {
        (requireActivity() as MainActivity).showMainApp(role)
    }

    /** Maps common Firebase Auth exceptions to a clear, field-attached error where possible. */
    // Purpose: Displays the Firebase registration error beside the input field that caused it.
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
    // Purpose: Switches the screen between loading and interactive states to prevent duplicate actions.
    private fun setLoading(view: View, isLoading: Boolean) {
        view.findViewById<MaterialButton>(R.id.btn_create_account).isEnabled = !isLoading
        view.findViewById<ProgressBar>(R.id.progress_create_account).visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }
}
