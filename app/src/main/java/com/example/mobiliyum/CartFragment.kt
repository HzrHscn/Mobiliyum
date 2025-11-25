package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Düzeltildi: ProductDetailDialogFragment sınıfını artık doğru bir şekilde import ediyoruz.
import com.example.mobiliyum.ProductDetailDialogFragment

class CartFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var btnRemoveSelected: Button
    private lateinit var btnOpenSelected: Button
    private lateinit var emptyView: View
    private lateinit var contentView: View

    private var cartAdapter: CartAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cart, container, false)

        // Bileşenleri Bağla
        recyclerView = view.findViewById(R.id.rvCartItems)
        tvTotal = view.findViewById(R.id.tvCartTotal)
        emptyView = view.findViewById(R.id.layoutEmptyCart)
        contentView = view.findViewById(R.id.layoutCartContent)

        // Butonları Bağla
        btnRemoveSelected = view.findViewById(R.id.btnRemoveSelected)
        btnOpenSelected = view.findViewById(R.id.btnOpenSelected)

        // Listener'ları ayarla
        btnRemoveSelected.setOnClickListener {
            handleRemoveSelected()
        }

        btnOpenSelected.setOnClickListener {
            handleOpenSelectedLinks()
        }

        loadCart()

        return view
    }

    override fun onResume() {
        super.onResume()
        loadCart()
    }

    private fun loadCart() {
        val items = CartManager.getCartItems()

        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            contentView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            contentView.visibility = View.VISIBLE

            recyclerView.layoutManager = LinearLayoutManager(context)

            // DÜZELTİLDİ: Adapter'a tıklama listener'ı tekrar ekleniyor.
            cartAdapter = CartAdapter(items) { clickedProduct ->
                handleProductClick(clickedProduct)
            }
            recyclerView.adapter = cartAdapter

            // Toplam tutarı yazdır
            tvTotal.text = "Tahmini Toplam: ${CartManager.getTotalPrice()} ₺"
        }
    }

    /**
     * Tıklanan ürün için detay pop-up'ı açar.
     * Bu fonksiyon artık ProductDetailDialogFragment'ı çağırıyor.
     */
    private fun handleProductClick(product: Product) {
        ProductDetailDialogFragment.newInstance(product)
            .show(parentFragmentManager, "ProductDetailDialog")
    }

    /**
     * Seçili ürünleri sepetten kaldırır.
     */
    private fun handleRemoveSelected() {
        val selectedProducts = cartAdapter?.getSelectedProducts()

        if (selectedProducts.isNullOrEmpty()) {
            Toast.makeText(context, "Lütfen silmek istediğiniz ürünleri seçin.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("${selectedProducts.size} Ürünü Kaldır")
            .setMessage("Seçili ${selectedProducts.size} ürün sepetten kaldırılacaktır. Onaylıyor musunuz?")
            .setPositiveButton("Evet, Kaldır") { _, _ ->
                selectedProducts.forEach { product ->
                    CartManager.removeFromCart(product)
                }

                Toast.makeText(context, "${selectedProducts.size} ürün sepetten kaldırıldı.", Toast.LENGTH_SHORT).show()
                loadCart() // Arayüzü güncelle
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * Seçili ürünlerin linklerini toplu halde açar.
     */
    private fun handleOpenSelectedLinks() {
        val selectedProducts = cartAdapter?.getSelectedProducts()

        if (selectedProducts.isNullOrEmpty()) {
            Toast.makeText(context, "Lütfen açmak istediğiniz en az bir ürün seçin.", Toast.LENGTH_SHORT).show()
            return
        }

        val message = selectedProducts.joinToString(separator = "\n") {
            "- ${it.name} (${getDomain(it.productUrl)})"
        }

        AlertDialog.Builder(context)
            .setTitle("${selectedProducts.size} Ürünü Dış Siteye Yönlendiriliyor")
            .setMessage("Seçilen ürünler için aşağıdaki siteler yeni sekmelerde açılacaktır:\n\n$message")
            .setPositiveButton("Onayla ve Aç") { _, _ ->
                selectedProducts.forEach { product ->
                    openWebsite(product.productUrl)
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // Harici site açma ve domain çekme yardımcı fonksiyonları
    private fun openWebsite(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Link açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host?.replace("www.", "") ?: url
        } catch (e: Exception) {
            url
        }
    }
}