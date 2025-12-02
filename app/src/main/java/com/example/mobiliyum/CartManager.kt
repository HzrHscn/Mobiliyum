package com.example.mobiliyum

import com.google.firebase.firestore.FirebaseFirestore

object CartManager {

    private val cartItems = ArrayList<Product>()
    private val db = FirebaseFirestore.getInstance()

    // Sepete Ekle
    fun addToCart(product: Product) {
        // Aynı ürün tekrar eklenirse adet artırmak yerine şimdilik listeye ekliyoruz.
        // İsterseniz burada "contains" kontrolü yapabilirsiniz.
        if (!cartItems.any { it.id == product.id }) {
            cartItems.add(product)
        }
    }

    // Sepetten Çıkar
    fun removeFromCart(product: Product) {
        val iterator = cartItems.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.id == product.id) {
                iterator.remove()
                break // Sadece bir tane sil
            }
        }
    }

    // Sepeti Getir
    fun getCartItems(): List<Product> {
        return cartItems
    }

    // Toplam Tutarı Hesapla
    fun calculateTotalAmount(): Double {
        var total = 0.0
        for (item in cartItems) {
            total += PriceUtils.parsePrice(item.price)
        }
        return total
    }

    // EKSİK OLAN FONKSİYON (HATA BURADAYDI)
    fun getCartItemCount(): Int {
        return cartItems.size
    }

    // Sepeti Temizle
    fun clearCart() {
        cartItems.clear()
    }
}