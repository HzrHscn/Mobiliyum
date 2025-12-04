package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.FragmentAdminStoreEditBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class AdminStoreEditFragment : Fragment() {

    private var _binding: FragmentAdminStoreEditBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var storeId: Int = 0

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
            binding.switchStoreActive.isChecked = true // Yeni mağaza varsayılan aktiftir
        }

        binding.btnSaveStore.setOnClickListener { saveStore() }
    }

    private fun loadStoreData() {
        db.collection("stores").document(storeId.toString()).get()
            .addOnSuccessListener { doc ->
                val store = doc.toObject(Store::class.java)
                if (store != null) {
                    binding.etStoreName.setText(store.name)
                    binding.etStoreCategory.setText(store.category)
                    binding.etStoreLocation.setText(store.location)
                    binding.etStoreImage.setText(store.imageUrl)
                    binding.etStoreEtap.setText(store.etap)
                    // Aktiflik durumunu yükle
                    binding.switchStoreActive.isChecked = store.isActive
                }
            }
    }

    private fun saveStore() {
        val name = binding.etStoreName.text.toString()
        if (name.isEmpty()) return

        // Yeni ekleme ise ID oluştur, değilse mevcut ID'yi kullan
        val idToSave = if (storeId == 0) Random.nextInt(10000, 99999) else storeId

        val storeData = mapOf(
            "id" to idToSave,
            "name" to name,
            "category" to binding.etStoreCategory.text.toString(),
            "location" to binding.etStoreLocation.text.toString(),
            "imageUrl" to binding.etStoreImage.text.toString(),
            "etap" to binding.etStoreEtap.text.toString(),
            // Switch durumunu kaydet
            "isActive" to binding.switchStoreActive.isChecked
        )

        // merge yerine set kullanıp ID'yi de garanti altına alalım
        db.collection("stores").document(idToSave.toString())
            .set(storeData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(context, "Mağaza başarıyla kaydedildi.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}