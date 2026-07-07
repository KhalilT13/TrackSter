package com.Khalil.trackster.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.auth.SignUpFragment

/**
 * Temporary landing spot right after a successful sign up. Just confirms
 * which role the new account was created as - real customer/business
 * dashboards will replace this in a later step.
 */
class PlaceholderHomeFragment : Fragment(R.layout.fragment_placeholder_home) {

    companion object {
        private const val ARG_ROLE = "role"

        /** Creates a PlaceholderHomeFragment for the given role ("customer" or "business"). */
        fun newInstance(role: String): PlaceholderHomeFragment {
            return PlaceholderHomeFragment().apply {
                arguments = Bundle().apply { putString(ARG_ROLE, role) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isBusiness = arguments?.getString(ARG_ROLE) == SignUpFragment.ROLE_BUSINESS
        val roleLabel = getString(if (isBusiness) R.string.role_business_title else R.string.role_customer_title)
        view.findViewById<TextView>(R.id.tv_welcome).text = getString(R.string.placeholder_welcome_format, roleLabel)
    }
}
