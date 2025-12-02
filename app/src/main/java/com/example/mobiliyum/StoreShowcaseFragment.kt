package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemCartProductBinding // Adapter Binding
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class StoreShowcaseFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSubmit: MaterialButton
    private val db = FirebaseFirestore.getInstance()
    private val selectedIds = ArrayList<Int>()
    private var adapter: ShowcaseSelectionAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Programatik Layout (XML kullanmıyor, bu yüzden Binding yok)
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val header = TextView(context).apply {
            text = "Vitrin Yönetimi (Mağazanın Seçimi)"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setPadding(32, 32, 32, 16)
        }
        layout.addView(header)

        val subHeader = TextView(context).apply {
            text = "Mağaza sayfanızda 'Mağazanın Seçimi' alanında görünecek 2 ürünü seçiniz."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#757575"))
            setPadding(32, 0, 32, 32)
        }
        layout.addView(subHeader)

        recyclerView = RecyclerView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        layout.addView(recyclerView)

        btnSubmit = MaterialButton(context).apply {
            text = "İşlemi Tamamla"
            setBackgroundColor(android.graphics.Color.parseColor("#FF6F00"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 16, 32, 32) }
        }
        layout.addView(btnSubmit)

        val user = UserManager.getCurrentUser()
        if (user?.role == UserRole.EDITOR) {
            btnSubmit.text = "Seçimi Onaya Gönder"
        } else {
            btnSubmit.text = "Vitrini Güncelle"
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        loadMyProducts()

        btnSubmit.setOnClickListener {
            if (selectedIds.size != 2) {
                Toast.makeText(context, "Lütfen tam olarak 2 ürün seçiniz.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (user?.role == UserRole.EDITOR) {
                EditorManager.submitShowcaseRequest(selectedIds) {
                    Toast.makeText(context, "Talep Müdüre iletildi.", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
            } else {
                updateShowcaseDirectly()
            }
        }
        return layout
    }

    private fun loadMyProducts() {
        val user = UserManager.getCurrentUser() ?: return
        val storeId = user.storeId ?: return
        db.collection("stores").document(storeId.toString()).get()
            .addOnSuccessListener { storeDoc ->
                val currentFeatured = storeDoc.toObject(Store::class.java)?.featuredProductIds ?: emptyList()
                selectedIds.clear()
                selectedIds.addAll(currentFeatured)
                db.collection("products").whereEqualTo("storeId", storeId).get()
                    .addOnSuccessListener { docs ->
                        val list = docs.toObjects(Product::class.java)
                        adapter = ShowcaseSelectionAdapter(list)
                        recyclerView.adapter = adapter
                    }
            }
    }

    private fun updateShowcaseDirectly() {
        val user = UserManager.getCurrentUser() ?: return
        val storeId = user.storeId ?: return
        db.collection("stores").document(storeId.toString())
            .update("featuredProductIds", selectedIds)
            .addOnSuccessListener {
                Toast.makeText(context, "Vitrin başarıyla güncellendi!", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ADAPTER (ViewBinding ile güncellendi)
    inner class ShowcaseSelectionAdapter(private val items: List<Product>) : RecyclerView.Adapter<ShowcaseSelectionAdapter.VH>() {

        inner class VH(val binding: ItemCartProductBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemCartProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.tvProductName.text = item.name
            holder.binding.tvProductPrice.text = PriceUtils.formatPriceStyled(item.price)
            Glide.with(holder.itemView).load(item.imageUrl).into(holder.binding.imgProduct)

            holder.binding.cbSelectProduct.setOnCheckedChangeListener(null)
            holder.binding.cbSelectProduct.isChecked = selectedIds.contains(item.id)

            holder.binding.cbSelectProduct.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selectedIds.size >= 2) {
                        holder.binding.cbSelectProduct.isChecked = false
                        Toast.makeText(context, "En fazla 2 ürün seçebilirsiniz.", Toast.LENGTH_SHORT).show()
                    } else { selectedIds.add(item.id) }
                } else { selectedIds.remove(item.id) }
            }
            holder.itemView.setOnClickListener { holder.binding.cbSelectProduct.toggle() }
        }
        override fun getItemCount() = items.size
    }
}