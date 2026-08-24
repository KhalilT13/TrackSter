package com.Khalil.trackster.ui.business

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Khalil.trackster.R
import com.Khalil.trackster.ui.common.PhotoGalleryAdapter
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class BusinessPhotosFragment : Fragment(R.layout.fragment_business_photos) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private var listener: ListenerRegistration? = null
    private var photos = emptyList<String>()
    private lateinit var adapter: PhotoGalleryAdapter
    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { if (it.isNotEmpty()) upload(it) }

    // Purpose: Initializes screen views, click handlers, and data loading after the layout is created.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { parentFragmentManager.popBackStack() }
        adapter = PhotoGalleryAdapter(removable = true, onRemove = ::remove)
        view.findViewById<RecyclerView>(R.id.recycler_business_photos).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false); adapter = this@BusinessPhotosFragment.adapter
        }
        view.findViewById<MaterialButton>(R.id.btn_upload_photos).setOnClickListener {
            if (photos.size >= MAX_PHOTOS) toast(R.string.error_photo_limit) else picker.launch("image/*")
        }
        val uid = auth.currentUser?.uid ?: return
        listener = firestore.collection("businesses").document(uid).addSnapshotListener { doc, _ ->
            @Suppress("UNCHECKED_CAST")
            val loadedPhotos = (doc?.get("photoUrls") as? List<String>).orEmpty().take(MAX_PHOTOS)
            photos = loadedPhotos
            adapter.submitList(photos)
            view.findViewById<TextView>(R.id.tv_photos_empty).visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // Purpose: Removes view-scoped listeners and references when the fragment view is destroyed.
    override fun onDestroyView() { listener?.remove(); listener = null; super.onDestroyView() }

    // Purpose: Uploads selected images to Cloud Storage and appends their URLs to the business gallery.
    private fun upload(selected: List<Uri>) {
        val uid = auth.currentUser?.uid ?: return
        val allowed = selected.take((MAX_PHOTOS - photos.size).coerceAtLeast(0))
        if (allowed.size < selected.size) toast(R.string.error_photo_limit)
        allowed.forEach { uri ->
            val ref = storage.reference.child("businesses/$uid/gallery/${UUID.randomUUID()}")
            ref.putFile(uri).continueWithTask { task -> if (!task.isSuccessful) throw task.exception ?: IllegalStateException(); ref.downloadUrl }
                .addOnSuccessListener { firestore.collection("businesses").document(uid).update("photoUrls", FieldValue.arrayUnion(it.toString())) }
                .addOnFailureListener { if (isAdded) toast(R.string.error_photo_upload) }
        }
    }

    // Purpose: Removes the selected item from stored data and refreshes the screen.
    private fun remove(url: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("businesses").document(uid).update("photoUrls", FieldValue.arrayRemove(url))
            .addOnSuccessListener { storage.getReferenceFromUrl(url).delete() }
            .addOnFailureListener { if (isAdded) toast(R.string.error_business_profile_generic) }
    }

    // Purpose: Shows a short localized feedback message to the user.
    private fun toast(id: Int) = Toast.makeText(requireContext(), id, Toast.LENGTH_SHORT).show()
    companion object { private const val MAX_PHOTOS = 6 }
}
