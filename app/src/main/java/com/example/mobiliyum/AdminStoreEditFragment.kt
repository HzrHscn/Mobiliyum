package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.FragmentAdminStoreEditBinding
import com.google.firebase.firestore.FirebaseFirestore

class AdminStoreEditFragment : Fragment() {

    private var _binding: FragmentAdminStoreEditBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var storeId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            storeId = it.getInt("storeId", 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminStoreEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (storeId != 0) {
            loadStoreData()
        }

        binding.btnSaveStore.setOnClickListener {
            saveStoreData()
        }
    }

    private fun loadStoreData() {
        db.collection("stores").document(storeId.toString()).get()
            .addOnSuccessListener { document ->
                val store = document.toObject(Store::class.java)
                if (store != null) {
                    binding.etStoreName.setText(store.name)
                    binding.etStoreLocation.setText(store.location)
                    binding.etStoreImage.setText(store.imageUrl)
                    binding.etStoreCategory.setText(store.category)
                    binding.etStoreEtap.setText(store.etap)
                }
            }
    }

    private fun saveStoreData() {
        val name = binding.etStoreName.text.toString().trim()
        val location = binding.etStoreLocation.text.toString().trim()
        val image = binding.etStoreImage.text.toString().trim()
        val category = binding.etStoreCategory.text.toString().trim()
        val etap = binding.etStoreEtap.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(context, "Mağaza adı boş olamaz.", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "name" to name,
            "location" to location,
            "imageUrl" to image,
            "category" to category,
            "etap" to etap
        )

        db.collection("stores").document(storeId.toString()).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Mağaza güncellendi!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata oluştu.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}