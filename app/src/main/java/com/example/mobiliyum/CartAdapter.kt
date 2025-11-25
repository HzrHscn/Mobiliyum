package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CartAdapter(
    private val cartItems: List<Product>,
    // DÜZELTİLDİ: Ürüne tıklanmada çalışacak fonksiyon geri ekleniyor.
    private val onItemClick: (Product) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val selectedItems = mutableSetOf<Product>()

    class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        val tvUrl: TextView = itemView.findViewById(R.id.tvProductUrl)
        val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelectProduct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_product, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val product = cartItems[position]

        // Verileri yükle
        holder.tvName.text = product.name
        holder.tvPrice.text = product.price
        holder.tvUrl.text = product.productUrl.replace("https://", "").replace("http://", "")

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .into(holder.imgProduct)

        holder.cbSelect.isChecked = selectedItems.contains(product)

        // 1. CheckBox Olayı: Sadece CheckBox'a tıklanınca seçimi değiştir.
        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedItems.add(product)
            } else {
                selectedItems.remove(product)
            }
        }

        // 2. TÜM ITEM TIKLAMA Olayı (CheckBox hariç alana tıklanınca): Detay pop-up'ını aç.
        // CheckBox'a tıklama event'i, genellikle parent'a yayılmaz. Bu yüzden bu güvenli bir ayrım.
        holder.itemView.setOnClickListener {
            // Eğer doğrudan CheckBox'a tıklanmadıysa (ki bu durumda CheckBox'ın kendi listener'ı devreye girerdi)
            // Ürünün detay pop-up'ını açmak için callback'i çağır.
            onItemClick(product)
        }
    }

    override fun getItemCount(): Int = cartItems.size

    fun getSelectedProducts(): Set<Product> {
        return selectedItems
    }
}