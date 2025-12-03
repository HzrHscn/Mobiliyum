package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobiliyum.databinding.FragmentCartBinding
import com.google.firebase.firestore.FirebaseFirestore

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private var cartAdapter: CartAdapter? = null
    private var suggestionAdapter: ProductAdapter? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Buton Dinleyicileri
        binding.btnClearCart.setOnClickListener { confirmClearCart() }

        binding.btnStartPurchase.setOnClickListener { showPurchaseSelectionDialog() }

        binding.btnExploreStores.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StoresFragment())
                .commit()
        }

        loadCart()
    }

    override fun onResume() {
        super.onResume()
        loadCart()
    }

    private fun loadCart() {
        val items = CartManager.getCartItems()
        val isCartEmpty = items.isEmpty()

        binding.layoutEmptyCart.isVisible = isCartEmpty
        binding.layoutCartContent.isVisible = !isCartEmpty
        binding.footerLayout.isVisible = !isCartEmpty

        // Sepet boşsa "Sepeti Temizle" yazısını da gizle
        binding.btnClearCart.isVisible = !isCartEmpty

        if (isCartEmpty) {
            binding.rvSuggestions.isVisible = true
            loadSuggestions()
        } else {
            binding.rvSuggestions.isVisible = false

            binding.rvCartItems.layoutManager = LinearLayoutManager(context)

            // Adapter Kurulumu (Artık 3 parametre alıyor)
            if (cartAdapter == null) {
                cartAdapter = CartAdapter(
                    onItemClick = { product -> handleProductClick(product) },
                    onDeleteClick = { product -> confirmDeleteItem(product) },
                    onQuantityChange = { product, change -> handleQuantityChange(product, change) }
                )
                binding.rvCartItems.adapter = cartAdapter
            }

            // Yeni listeyi oluşturup gönder (DiffUtil tetiklensin)
            cartAdapter?.submitList(ArrayList(items.map { it.copy() }))
            // Not: copy() kullanarak deep copy yapıyoruz ki DiffUtil içerik değişimini (adet) algılayabilsin.

            updateTotal()
        }

        // Badge güncelle
        (activity as? MainActivity)?.updateCartBadge()
    }

    private fun updateTotal() {
        val totalAmount = CartManager.calculateTotalAmount()
        binding.tvCartTotal.text = PriceUtils.formatPriceStyled(totalAmount)
    }

    private fun handleQuantityChange(product: Product, change: Int) {
        if (change > 0) {
            CartManager.increaseQuantity(product)
        } else {
            CartManager.decreaseQuantity(product)
        }
        // Listeyi yenile
        loadCart()
    }

    private fun confirmDeleteItem(product: Product) {
        AlertDialog.Builder(context)
            .setTitle("Ürünü Sil")
            .setMessage("${product.name} sepetten çıkarılsın mı?")
            .setPositiveButton("Evet") { _, _ ->
                CartManager.removeFromCart(product)
                loadCart()
                Toast.makeText(context, "Ürün silindi.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun confirmClearCart() {
        AlertDialog.Builder(context)
            .setTitle("Sepeti Temizle")
            .setMessage("Tüm ürünler silinecek. Onaylıyor musunuz?")
            .setPositiveButton("Evet, Temizle") { _, _ ->
                CartManager.clearCart()
                loadCart()
                Toast.makeText(context, "Sepet temizlendi.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // --- SATIN ALMA DİYALOĞU ---
    private fun showPurchaseSelectionDialog() {
        val items = CartManager.getCartItems()
        if (items.isEmpty()) return

        // Listede gösterilecek isimler
        val itemNames = items.map { "${it.name} (${it.quantity} Adet)" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Hangi Ürünü Satın Alacaksınız?")
            .setItems(itemNames) { _, which ->
                val selectedProduct = items[which]
                showRedirectConfirmation(selectedProduct)
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showRedirectConfirmation(product: Product) {
        AlertDialog.Builder(context)
            .setTitle("Mağazaya Yönlendiriliyorsunuz")
            .setMessage("${product.name} ürününü satın almak için satıcı firmanın web sitesine yönlendirileceksiniz. Onaylıyor musunuz?")
            .setPositiveButton("Evet, Git") { _, _ ->
                openWebsite(product.productUrl)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun loadSuggestions() {
        binding.rvSuggestions.layoutManager = GridLayoutManager(context, 2)

        suggestionAdapter = ProductAdapter { product ->
            val fragment = ProductDetailFragment()
            val bundle = Bundle()
            bundle.putParcelable("product_data", product)
            fragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
        binding.rvSuggestions.adapter = suggestionAdapter

        db.collection("app_settings").document("cart_suggestions").get()
            .addOnSuccessListener { doc ->
                val ids = doc.get("productIds") as? List<Long>
                if (!ids.isNullOrEmpty()) {
                    val intIds = ids.map { it.toInt() }
                    db.collection("products").whereIn("id", intIds).get()
                        .addOnSuccessListener { querySnapshot ->
                            val products = querySnapshot.toObjects(Product::class.java)
                            suggestionAdapter?.submitList(products)
                        }
                }
            }
    }

    private fun handleProductClick(product: Product) {
        ProductDetailDialogFragment.newInstance(product)
            .show(parentFragmentManager, "ProductDetailDialog")
    }

    private fun openWebsite(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Link açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}