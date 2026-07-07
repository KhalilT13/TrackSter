package com.Khalil.trackster.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * The very first screen the user sees: app branding, a choice between the
 * "Customer" and "Business Owner" roles, and Sign In / Sign Up actions.
 *
 * The role cards are just visual selection - the chosen role is passed
 * along when navigating to either Sign In or Sign Up.
 */
class LandingFragment : Fragment(R.layout.fragment_landing) {

    /** The two roles a user can pick on this screen. */
    private enum class Role {
        CUSTOMER,
        BUSINESS
    }

    // Which role is currently selected. Customer is selected by default.
    private var selectedRole: Role = Role.CUSTOMER

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cardCustomer = view.findViewById<MaterialCardView>(R.id.card_customer)
        val cardBusiness = view.findViewById<MaterialCardView>(R.id.card_business)

        // Draw the correct highlight for whichever role is selected.
        refreshCardHighlights(cardCustomer, cardBusiness)

        cardCustomer.setOnClickListener {
            selectedRole = Role.CUSTOMER
            refreshCardHighlights(cardCustomer, cardBusiness)
        }

        cardBusiness.setOnClickListener {
            selectedRole = Role.BUSINESS
            refreshCardHighlights(cardCustomer, cardBusiness)
        }

        view.findViewById<MaterialButton>(R.id.btn_sign_in).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SignInFragment.newInstance(selectedRoleArg()))
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<MaterialButton>(R.id.btn_sign_up).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SignUpFragment.newInstance(selectedRoleArg()))
                .addToBackStack(null)
                .commit()
        }
    }

    /** Converts the selected role card into the plain "customer"/"business" string the auth screens expect. */
    private fun selectedRoleArg(): String {
        return if (selectedRole == Role.BUSINESS) SignUpFragment.ROLE_BUSINESS else SignUpFragment.ROLE_CUSTOMER
    }

    /** Re-applies the selected/unselected stroke style to both role cards. */
    private fun refreshCardHighlights(cardCustomer: MaterialCardView, cardBusiness: MaterialCardView) {
        applyCardSelectionStyle(cardCustomer, isSelected = selectedRole == Role.CUSTOMER)
        applyCardSelectionStyle(cardBusiness, isSelected = selectedRole == Role.BUSINESS)
    }

    /**
     * Selected cards get a thicker blue stroke and a light blue fill;
     * unselected cards get back the thin gray stroke and white fill they
     * start with in fragment_landing.xml.
     */
    private fun applyCardSelectionStyle(card: MaterialCardView, isSelected: Boolean) {
        val density = card.context.resources.displayMetrics.density
        if (isSelected) {
            card.strokeColor = ContextCompat.getColor(card.context, R.color.trackster_blue)
            card.strokeWidth = (2 * density).toInt()
            card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.trackster_blue_light))
        } else {
            card.strokeColor = ContextCompat.getColor(card.context, R.color.light_gray)
            card.strokeWidth = (1 * density).toInt()
            card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.white))
        }
    }
}
