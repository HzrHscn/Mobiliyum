package com.example.mobiliyum

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentAdminProductListBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.random.Random

class AdminProductListFragment : Fragment() {

    private var _binding: FragmentAdminProductListBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var allProducts = ArrayList<Product>()
    private lateinit var adapter: AdminProductAdapter

    private val csvFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> readAndUploadCSV(uri) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminProductListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAdminProductList.layoutManager = LinearLayoutManager(context)
        adapter = AdminProductAdapter(arrayListOf()) { product ->
            val fragment = AdminProductEditFragment()
            val bundle = Bundle()
            bundle.putParcelable("product_data", product)
            fragment.arguments = bundle
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).addToBackStack(null).commit()
        }
        binding.rvAdminProductList.adapter = adapter

        loadProducts()

        binding.fabAddProduct.setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, AdminProductEditFragment()).addToBackStack(null).commit()
        }

        binding.btnUploadCsv.setOnClickListener { openFilePicker() }

        binding.searchViewAdminProduct.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean { filter(newText ?: ""); return true }
        })
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        csvFileLauncher.launch(intent)
    }

    private fun readAndUploadCSV(uri: Uri) {
        // (CSV kodları aynen kalıyor, hata yoktu)
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var count = 0
            // CSV FORMATI: Ürün Adı;Fiyat;Kategori;MağazaID;ResimURL;productURL;Açıklama
            // Örnek: Chester Koltuk;15.000 TL;Oturma Odası;101;http://...;http://...;Konforlu chester koltuk
            val batch = db.batch()

            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) continue
                val tokens = line!!.split(";")
                if (tokens.size >= 4) {
                    val name = tokens[0].trim()
                    val price = tokens[1].trim()
                    val category = tokens[2].trim()
                    val storeIdStr = tokens[3].trim()
                    val imageUrl = if (tokens.size > 4) tokens[4].trim() else ""
                    val productUrl = if (tokens.size > 5) tokens[5].trim() else ""
                    val description = if (tokens.size > 6) tokens[6].trim() else ""
                    val storeId = storeIdStr.toIntOrNull() ?: 0

                    if (name.isNotEmpty() && storeId != 0) {
                        val newId = Random.nextInt(100000, 999999)
                        val productRef = db.collection("products").document(newId.toString())
                        val productData = Product(
                            id = newId,
                            name = name,
                            price = price,
                            category = category,
                            storeId = storeId,
                            imageUrl = imageUrl,
                            productUrl = productUrl,
                            description = description,
                            isActive = true
                        )
                        batch.set(productRef, productData)
                        count++
                    }
                }
            }
            reader.close()
            if (count > 0) {
                batch.commit().addOnSuccessListener {
                    Toast.makeText(context, "$count ürün yüklendi!", Toast.LENGTH_LONG).show()
                    loadProducts()
                    DataManager.triggerServerVersionUpdate() // Versiyonu güncelle
                }
            } else { Toast.makeText(context, "Ürün bulunamadı.", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { Toast.makeText(context, "Hata: ${e.localizedMessage}", Toast.LENGTH_SHORT).show() }
    }

    private fun loadProducts() {
        // HATA GİDERİLDİ: DataManager metoduna parametreler doğru geçiriliyor
        DataManager.fetchProductsSmart(
            requireContext(),
            onSuccess = { products ->
                allProducts = ArrayList(products)
                adapter.updateList(allProducts)
            },
            onError = { Toast.makeText(context, "Hata: $it", Toast.LENGTH_SHORT).show() }
        )
    }

    private fun filter(text: String) {
        val filtered = allProducts.filter { it.name.lowercase().contains(text.lowercase()) }
        adapter.updateList(ArrayList(filtered))
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    class AdminProductAdapter(private var list: ArrayList<Product>, val onClick: (Product) -> Unit) : RecyclerView.Adapter<AdminProductAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(android.R.id.text1)
            val tvInfo: TextView = v.findViewById(android.R.id.text2)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = if (item.isActive) item.name else "${item.name} (PASİF)"
            holder.tvInfo.text = "${item.category} | ${item.price}"
            holder.itemView.alpha = if (item.isActive) 1.0f else 0.5f
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = list.size
        fun updateList(newList: ArrayList<Product>) { list = newList; notifyDataSetChanged() }
    }
}