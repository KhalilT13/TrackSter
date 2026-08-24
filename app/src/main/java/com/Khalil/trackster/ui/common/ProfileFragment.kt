package com.Khalil.trackster.ui.common

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.Khalil.trackster.MainActivity
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.auth.SignUpFragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private var currentLogoUrl = ""
    private val logoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) uploadLogo(uri) }

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val email = auth.currentUser?.email.orEmpty()
        view.findViewById<TextView>(R.id.tv_profile_email).text = email.ifBlank { getString(R.string.profile_missing_value) }
        view.findViewById<TextView>(R.id.tv_profile_name).text = email.ifBlank { getString(R.string.profile_missing_value) }
        view.findViewById<MaterialButton>(R.id.btn_sign_out).setOnClickListener { (requireActivity() as MainActivity).onSignOut() }
        view.findViewById<MaterialButton>(R.id.btn_change_logo).setOnClickListener { logoPicker.launch("image/*") }
        view.findViewById<MaterialButton>(R.id.btn_theme_setting).setOnClickListener { showThemeDialog() }
        view.findViewById<MaterialButton>(R.id.btn_language_setting).setOnClickListener { showLanguageDialog() }
        view.findViewById<MaterialButton>(R.id.btn_privacy_policy).setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.fragment_container, PrivacyPolicyFragment()).addToBackStack(null).commit()
        }
        updateSettingsLabels(view)
        loadProfile(view)
    }

    // Purpose: Displays the currently selected theme and language in the profile screen.
    private fun updateSettingsLabels(view: View) {
        view.findViewById<TextView>(R.id.tv_theme_value).text = getString(when (AppSettings.theme(requireContext())) {
            AppSettings.THEME_LIGHT -> R.string.theme_light
            AppSettings.THEME_DARK -> R.string.theme_dark
            else -> R.string.theme_system
        })
        view.findViewById<TextView>(R.id.tv_language_value).text = getString(when (AppSettings.languageTag()) {
            "en" -> R.string.language_english
            "he" -> R.string.language_hebrew
            else -> R.string.language_system
        })
    }

    // Purpose: Opens the System, Light, and Dark theme selection dialog.
    private fun showThemeDialog() {
        val values = arrayOf(AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK)
        val labels = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val selected = values.indexOf(AppSettings.theme(requireContext())).coerceAtLeast(0)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.settings_appearance)
            .setSingleChoiceItems(labels, selected, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemClickListener { _, _, position, _ -> dialog.dismiss(); AppSettings.setTheme(requireContext(), values[position]) }
        }
        dialog.show()
    }

    // Purpose: Opens the English and Hebrew language selection dialog.
    private fun showLanguageDialog() {
        val values = arrayOf("", "en", "he")
        val labels = arrayOf(getString(R.string.language_system), getString(R.string.language_english), getString(R.string.language_hebrew))
        val selected = values.indexOf(AppSettings.languageTag()).coerceAtLeast(0)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.settings_language)
            .setSingleChoiceItems(labels, selected, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemClickListener { _, _, position, _ -> dialog.dismiss(); AppSettings.setLanguage(values[position]) }
        }
        dialog.show()
    }

    // Purpose: Loads and displays the user's name, email address, and role.
    private fun loadProfile(view: View) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { user ->
            if (!isAdded) return@addOnSuccessListener
            view.findViewById<TextView>(R.id.tv_profile_name).text = user.getString("displayName")
                ?: user.getString("businessName") ?: auth.currentUser?.email ?: getString(R.string.profile_missing_value)
            val isBusiness = user.getString("role") == SignUpFragment.ROLE_BUSINESS
            view.findViewById<View>(R.id.business_logo_section).visibility = if (isBusiness) View.VISIBLE else View.GONE
            if (isBusiness) loadBusinessLogo(view, uid)
        }
    }

    // Purpose: Loads the business logo URL from Firestore for the profile screen.
    private fun loadBusinessLogo(view: View, uid: String) {
        firestore.collection("businesses").document(uid).get().addOnSuccessListener { business ->
            if (!isAdded) return@addOnSuccessListener
            currentLogoUrl = business.getString("logoUrl").orEmpty()
            renderLogo(view, currentLogoUrl)
        }
    }

    // Purpose: Displays the remote logo or the empty-logo state when no URL exists.
    private fun renderLogo(view: View, url: String) {
        val image = view.findViewById<ImageView>(R.id.iv_profile_avatar)
        val button = view.findViewById<MaterialButton>(R.id.btn_change_logo)
        view.findViewById<TextView>(R.id.tv_logo_status).text = if (url.isBlank()) getString(R.string.logo_not_uploaded) else getString(R.string.business_logo_signup_help)
        button.setText(if (url.isBlank()) R.string.action_choose_logo else R.string.action_change_logo)
        if (url.isNotBlank()) {
            image.setPadding(0, 0, 0, 0); ImageViewCompat.setImageTintList(image, null)
            Glide.with(image).load(url).centerCrop().into(image)
        } else {
            Glide.with(image).clear(image)
            val padding = (21 * resources.displayMetrics.density).toInt()
            image.setPadding(padding, padding, padding, padding); image.setImageResource(R.drawable.ic_business)
            ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)))
        }
    }

    // Purpose: Uploads a new logo to Cloud Storage and updates the business profile URL.
    private fun uploadLogo(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val view = view ?: return
        val button = view.findViewById<MaterialButton>(R.id.btn_change_logo)
        button.isEnabled = false
        val oldUrl = currentLogoUrl
        val ref = storage.reference.child("businesses/$uid/logo/${UUID.randomUUID()}")
        ref.putFile(uri).continueWithTask { task -> if (!task.isSuccessful) throw task.exception ?: IllegalStateException(); ref.downloadUrl }
            .addOnSuccessListener { url ->
                firestore.collection("businesses").document(uid).update("logoUrl", url.toString()).addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    currentLogoUrl = url.toString(); renderLogo(view, currentLogoUrl); button.isEnabled = true
                    Toast.makeText(requireContext(), R.string.success_logo_updated, Toast.LENGTH_SHORT).show()
                    if (oldUrl.isNotBlank()) runCatching { storage.getReferenceFromUrl(oldUrl).delete() }
                }.addOnFailureListener { if (isAdded) { button.isEnabled = true; Toast.makeText(requireContext(), R.string.error_logo_upload, Toast.LENGTH_SHORT).show() } }
            }.addOnFailureListener { if (isAdded) { button.isEnabled = true; Toast.makeText(requireContext(), R.string.error_logo_upload, Toast.LENGTH_SHORT).show() } }
    }

}
