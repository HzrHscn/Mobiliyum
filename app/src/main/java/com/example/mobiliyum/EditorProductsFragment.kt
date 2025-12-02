package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class EditorProductsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // XML yerine Programatik Layout kullanıyoruz (Hata riskini sıfırlar)
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(context).apply {
            text = "Ürün Yönetimi"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setPadding(32, 32, 32, 16)
        }
        layout.addView(title)

        val subTitle = TextView(context).apply {
            text = "Düzenlemek istediğiniz ürüne tıklayınız."
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(32, 0, 32, 32)
        }
        layout.addView(subTitle)

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        layout.addView(recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        loadStoreProducts()

        return layout
    }

    private fun loadStoreProducts() {
        val user = UserManager.getCurrentUser() ?: return
        if (user.storeId == null) return

        db.collection("products")
            .whereEqualTo("storeId", user.storeId)
            .get()
            .addOnSuccessListener { docs ->
                val products = docs.toObjects(Product::class.java)
                recyclerView.adapter = EditorProductAdapter(products)
            }
    }

    inner class EditorProductAdapter(private val items: List<Product>) : RecyclerView.Adapter<EditorProductAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgProduct)
            val name: TextView = v.findViewById(R.id.tvProductName)
            val price: TextView = v.findViewById(R.id.tvProductPrice)
            // item_cart_product.xml içindeki silme butonunu "Düzenle" butonu gibi kullanacağız
            // ID'sini kontrol et: genellikle btnRemoveFromCart olabilir, yoksa checkbox'tır.
            // Güvenlik için findViewById sonucunu nullable yapıp kontrol ediyoruz.
            val btnAction: View? = v.findViewById(R.id.cbSelectProduct)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // item_cart_product.xml kullandığını varsayıyoruz
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_product, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.price.text = PriceUtils.formatPriceStyled(item.price)
            Glide.with(holder.itemView).load(item.imageUrl).into(holder.img)

            // Çöp kutusu ikonunu 'Edit' kalemi ile değiştirelim
            (holder.btnAction as? ImageView)?.setImageResource(android.R.drawable.ic_menu_edit)

            // Tıklama olayları
            holder.itemView.setOnClickListener { openEdit(item) }
            holder.btnAction?.setOnClickListener { openEdit(item) }
        }

        override fun getItemCount() = items.size

        private fun openEdit(product: Product) {
            val fragment = ProductDetailFragment()
            val args = Bundle()
            args.putSerializable("product_data", product)
            fragment.arguments = args

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
    }
}