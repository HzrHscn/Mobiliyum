package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.FragmentAdminStoreEditBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.random.Random

class AdminStoreEditFragment : Fragment() {

    private var _binding: FragmentAdminStoreEditBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var storeId: Int = 0
    private var currentStore: Store? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { storeId = it.getInt("storeId", 0) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminStoreEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (storeId != 0) {
            binding.tvTitle.text = "Mağazayı Düzenle"
            loadStoreData()
        } else {
            binding.tvTitle.text = "Yeni Mağaza Ekle"
            binding.switchStoreActive.isChecked = true
        }

        binding.btnSaveStore.setOnClickListener { saveStore() }
    }

    private fun loadStoreData() {
        db.collection("stores").document(storeId.toString()).get()
            .addOnSuccessListener { doc ->
                currentStore = doc.toObject(Store::class.java)
                if (currentStore != null) {
                    val s = currentStore!!
                    binding.etStoreName.setText(s.name)
                    binding.etStoreCategory.setText(s.category)
                    binding.etStoreLocation.setText(s.location)
                    binding.etStoreImage.setText(s.imageUrl)
                    binding.etStoreEtap.setText(s.etap)
                    binding.switchStoreActive.isChecked = s.isActive
                }
            }
    }

    private fun saveStore() {
        val name = binding.etStoreName.text.toString().trim()
        val category = binding.etStoreCategory.text.toString().trim()
        val location = binding.etStoreLocation.text.toString().trim()
        val imageUrl = binding.etStoreImage.text.toString().trim()
        val etap = binding.etStoreEtap.text.toString().trim()
        val isActive = binding.switchStoreActive.isChecked

        if (name.isEmpty()) {
            Toast.makeText(context, "Mağaza adı zorunludur", Toast.LENGTH_SHORT).show()
            return
        }

        val idToSave = if (storeId == 0) Random.nextInt(10000, 99999) else storeId

        val updatedStore = Store(
            id = idToSave,
            name = name,
            category = category,
            location = location,
            imageUrl = imageUrl,
            etap = etap,
            isActive = isActive,
            // Korunan veriler
            clickCount = currentStore?.clickCount ?: 0,
            clickHistory = currentStore?.clickHistory ?: hashMapOf(),
            featuredProductIds = currentStore?.featuredProductIds ?: listOf()
        )

        db.collection("stores").document(idToSave.toString())
            .set(updatedStore, SetOptions.merge())
            .addOnSuccessListener {
                DataManager.updateStoreInCache(updatedStore)
                DataManager.triggerServerVersionUpdate()
                Toast.makeText(context, "Mağaza kaydedildi.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}