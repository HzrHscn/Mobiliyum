package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CartFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var emptyView: View
    private lateinit var contentView: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cart, container, false)
        // fragment_cart.xml içinde bir RecyclerView olduğunu varsayarak düzenliyoruz.
        // Eğer XML'i henüz güncellemediysen, aşağıda XML kodunu da vereceğim.

        recyclerView = view.findViewById(R.id.rvCartItems) // XML'de bu ID olmalı
        tvTotal = view.findViewById(R.id.tvCartTotal)      // XML'de bu ID olmalı
        emptyView = view.findViewById(R.id.layoutEmptyCart) // Boş sepet tasarımı
        contentView = view.findViewById(R.id.layoutCartContent) // Dolu sepet tasarımı

        loadCart()

        return view
    }

    // Sepet her görüntülendiğinde güncel veriyi çekmesi için onResume kullanıyoruz
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

            // Ürünleri listele
            recyclerView.layoutManager = LinearLayoutManager(context)
            // ProductAdapter'ı burada da kullanıyoruz. Tıklanınca bir şey yapmasına gerek yok şimdilik.
            recyclerView.adapter = ProductAdapter(items) {
                // Sepetteki ürüne tıklayınca detayına gidebiliriz istersek
            }

            // Toplam tutarı yazdır
            tvTotal.text = "Toplam: ${CartManager.getTotalPrice()} ₺"
        }
    }
}