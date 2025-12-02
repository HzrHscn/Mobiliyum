package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobiliyum.databinding.FragmentCartBinding // ViewBinding

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private var cartAdapter: CartAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Buton Dinleyicileri (findViewById yerine binding)
        binding.btnRemoveSelected.setOnClickListener {
            handleRemoveSelected()
        }

        binding.btnOpenSelected.setOnClickListener {
            handleOpenSelectedLinks()
        }

        loadCart()
    }

    override fun onResume() {
        super.onResume()
        loadCart()
    }

    private fun loadCart() {
        val items = CartManager.getCartItems()

        if (items.isEmpty()) {
            binding.layoutEmptyCart.visibility = View.VISIBLE
            binding.layoutCartContent.visibility = View.GONE
        } else {
            binding.layoutEmptyCart.visibility = View.GONE
            binding.layoutCartContent.visibility = View.VISIBLE

            binding.rvCartItems.layoutManager = LinearLayoutManager(context)
            cartAdapter = CartAdapter(items) { product ->
                // Tıklama ile detay dialogu açma
                handleProductClick(product)
            }
            binding.rvCartItems.adapter = cartAdapter

            // Toplam tutarı hesapla ve göster
            val totalAmount = CartManager.calculateTotalAmount()
            binding.tvCartTotal.text = PriceUtils.formatPriceStyled(totalAmount)
        }
    }

    private fun handleProductClick(product: Product) {
        ProductDetailDialogFragment.newInstance(product)
            .show(parentFragmentManager, "ProductDetailDialog")
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}