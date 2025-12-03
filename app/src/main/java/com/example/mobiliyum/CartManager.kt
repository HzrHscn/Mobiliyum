package com.example.mobiliyum

import com.google.firebase.firestore.FirebaseFirestore

object CartManager {

    private val cartItems = ArrayList<Product>()
    private val db = FirebaseFirestore.getInstance()

    // Sepete Ekle
    fun addToCart(product: Product) {
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
                break
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

    fun getCartItemCount(): Int {
        return cartItems.size
    }

    fun clearCart() {
        cartItems.clear()
    }
}