package com.Khalil.trackster

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.Khalil.trackster.ui.auth.LandingFragment
import com.Khalil.trackster.notifications.QueueReminderManager
import com.Khalil.trackster.ui.auth.SignUpFragment
import com.Khalil.trackster.ui.business.BusinessDashboardFragment
import com.Khalil.trackster.ui.business.BusinessServicesFragment
import com.Khalil.trackster.ui.common.ProfileFragment
import com.Khalil.trackster.ui.common.AppSettings
import com.Khalil.trackster.ui.customer.CustomerQueueFragment
import com.Khalil.trackster.ui.customer.CustomerHomeFragment
import com.Khalil.trackster.ui.customer.MyAppointmentsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

/**
 * Hosts the whole app in a single fragment container, plus a bottom navigation
 * bar that is only visible once the user is logged in.
 *
 * There are two navigation "modes":
 *  - Auth flow (Landing / Sign In / Sign Up): bottom nav hidden. Those fragments
 *    still navigate themselves via fragment transactions + the back stack.
 *  - Main app (after login): bottom nav visible. Tapping a tab asks this Activity
 *    to swap the container to that tab's fragment. Tab switches do NOT go on the
 *    back stack, so Back doesn't cycle through tab history.
 *
 * The auth fragments call [showMainApp] on success and [onSignOut] to log out,
 * so this Activity is the single place that shows/hides the bottom nav.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var queueReminderManager: QueueReminderManager
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // The role whose tabs are currently shown, or null while logged out (auth flow).
    private var currentRole: String? = null

    // The menu id of the tab currently displayed. Used to avoid re-placing the same
    // tab fragment (e.g. when the bottom nav restores its selection after rotation).
    private var currentTabId: Int = 0

    // Purpose: Initializes the activity, restores saved state, and configures the initial navigation.
    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)
        queueReminderManager = QueueReminderManager(this)

        // Pad the whole screen by the system bars so content stays in the safe area and the
        // bottom nav rides just above the system navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        currentRole = savedInstanceState?.getString(KEY_ROLE)
        currentTabId = savedInstanceState?.getInt(KEY_TAB_ID) ?: 0

        if (savedInstanceState == null) {
            val signedInUser = FirebaseAuth.getInstance().currentUser
            if (signedInUser == null) {
                showLanding()
            } else {
                restoreSignedInSession(signedInUser.uid)
            }
        } else {
            // Recreated (e.g. rotation): the FragmentManager has already restored whichever
            // fragment was showing, so we only rebuild the bottom nav chrome. Selecting the
            // saved tab id restores the highlight; showTab's guard stops it from re-placing
            // the restored fragment.
            currentRole?.let { role ->
                setUpBottomNavForRole(role)
                bottomNav.isVisible = true
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (role == SignUpFragment.ROLE_CUSTOMER && uid != null) {
                    requestNotificationPermission()
                    queueReminderManager.start(uid)
                }
                if (currentTabId != 0) {
                    bottomNav.selectedItemId = currentTabId
                }
            }

            // If rotation happened during the short session-restore request, there may be no
            // fragment and no saved role yet. Restart that request instead of leaving a blank UI.
            if (currentRole == null &&
                supportFragmentManager.findFragmentById(R.id.fragment_container) == null
            ) {
                val signedInUser = FirebaseAuth.getInstance().currentUser
                if (signedInUser == null) showLanding() else restoreSignedInSession(signedInUser.uid)
            }
        }
    }

    // Purpose: Saves the selected role and active tab so they survive activity recreation.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ROLE, currentRole)
        outState.putInt(KEY_TAB_ID, currentTabId)
    }

    /**
     * Called by the auth fragments after a successful sign in / sign up. Leaves the auth
     * flow and enters the tabbed main app for [role].
     */
    // Purpose: Displays the role-appropriate app shell and initializes its bottom navigation.
    fun showMainApp(role: String) {
        findViewById<ProgressBar>(R.id.progress_startup).visibility = View.GONE
        currentRole = role
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) syncPublicProfile(uid)
        if (role == SignUpFragment.ROLE_CUSTOMER && uid != null) {
            requestNotificationPermission()
            queueReminderManager.start(uid)
        } else {
            queueReminderManager.stop()
        }
        currentTabId = 0
        // Drop the auth screens off the back stack so Back can't return to them.
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        setUpBottomNavForRole(role)
        bottomNav.isVisible = true

        val firstTab = firstTabIdForRole(role)
        // Set the highlight, then place the first tab explicitly. showTab is guarded so this
        // results in exactly one fragment placement regardless of whether selecting the item
        // also fired the listener.
        bottomNav.selectedItemId = firstTab
        showTab(firstTab)
    }

    /** Keeps the minimal public name document current for appointment reference resolution. */
    private fun syncPublicProfile(uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get().addOnSuccessListener { user ->
            val displayName = user.getString("displayName").orEmpty().trim()
            if (displayName.isNotBlank()) {
                db.collection("publicProfiles").document(uid).set(mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
        }
    }

    /**
     * Called by the Profile tab and the home screens' sign-out buttons. Logs the user out,
     * hides the bottom nav, and returns to the auth flow.
     */
    // Purpose: Signs out the current user, stops active listeners, and returns to role selection.
    fun onSignOut() {
        queueReminderManager.stop()
        FirebaseAuth.getInstance().signOut()
        currentRole = null
        currentTabId = 0
        bottomNav.isVisible = false
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        showLanding()
    }

    /** Moves a newly booked customer straight to the live queue tab. */
    // Purpose: Navigates the customer directly to the live queue tab.
    fun openCustomerQueue() {
        val queueTab = R.id.nav_customer_queue
        bottomNav.selectedItemId = queueTab
        showTab(queueTab)
    }

    // Purpose: Loads the signed-in user's role from Firestore and restores the correct interface.
    private fun restoreSignedInSession(uid: String) {
        findViewById<ProgressBar>(R.id.progress_startup).visibility = View.VISIBLE
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { profile ->
                val role = profile.getString("role")
                if (role == SignUpFragment.ROLE_CUSTOMER || role == SignUpFragment.ROLE_BUSINESS) {
                    showMainApp(role)
                } else {
                    FirebaseAuth.getInstance().signOut()
                    showLanding()
                }
            }
            .addOnFailureListener {
                // A stale or unreachable session should never trap the user on a spinner.
                FirebaseAuth.getInstance().signOut()
                showLanding()
            }
    }

    // Purpose: Displays the landing screen and hides navigation intended for signed-in users.
    private fun showLanding() {
        queueReminderManager.stop()
        currentRole = null
        currentTabId = 0
        bottomNav.isVisible = false
        findViewById<ProgressBar>(R.id.progress_startup).visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LandingFragment())
            .commit()
    }

    /** Swaps the bottom nav's menu to the customer or business set. */
    // Purpose: Configures bottom-navigation tabs and labels for the customer or business role.
    private fun setUpBottomNavForRole(role: String) {
        bottomNav.menu.clear()
        val menuRes = if (role == SignUpFragment.ROLE_BUSINESS) {
            R.menu.bottom_nav_business
        } else {
            R.menu.bottom_nav_customer
        }
        bottomNav.inflateMenu(menuRes)
    }

    // Purpose: Returns the default bottom-navigation tab for the supplied user role.
    private fun firstTabIdForRole(role: String): Int {
        return if (role == SignUpFragment.ROLE_BUSINESS) R.id.nav_business_dashboard else R.id.nav_customer_home
    }

    /**
     * Shows the fragment for the tapped tab. Tab switches don't go on the back stack, and any
     * in-tab sub-navigation (e.g. an in-progress booking flow) is cleared first so we always
     * land cleanly on the tab's root screen.
     *
     * Guarded so that re-selecting the current tab, or the bottom nav restoring its selection
     * after a rotation, doesn't replace an already-correct fragment.
     */
    // Purpose: Replaces the main content with the screen associated with the selected navigation item.
    private fun showTab(itemId: Int) {
        if (itemId == currentTabId && supportFragmentManager.findFragmentById(R.id.fragment_container) != null) {
            return
        }

        val fragment: Fragment = when (itemId) {
            R.id.nav_customer_home -> CustomerHomeFragment()
            R.id.nav_customer_appointments -> MyAppointmentsFragment()
            R.id.nav_customer_queue -> CustomerQueueFragment()
            R.id.nav_customer_profile -> ProfileFragment()
            R.id.nav_business_dashboard -> BusinessDashboardFragment()
            R.id.nav_business_services -> BusinessServicesFragment()
            R.id.nav_business_profile -> ProfileFragment()
            else -> return
        }

        currentTabId = itemId
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    companion object {
        private const val KEY_ROLE = "current_role"
        private const val KEY_TAB_ID = "current_tab_id"
    }

    // Purpose: Releases activity-level listeners and resources when the activity is destroyed.
    override fun onDestroy() {
        queueReminderManager.stop()
        super.onDestroy()
    }

    // Purpose: Requests notification permission on Android versions that require explicit consent.
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("app_permissions", MODE_PRIVATE)
            if (!prefs.getBoolean("notification_requested", false)) {
                prefs.edit().putBoolean("notification_requested", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
