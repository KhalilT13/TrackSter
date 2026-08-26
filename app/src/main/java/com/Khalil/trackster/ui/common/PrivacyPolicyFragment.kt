package com.Khalil.trackster.ui.common

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.Khalil.trackster.R

class PrivacyPolicyFragment : Fragment(R.layout.fragment_privacy_policy) {
    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
    }
}
