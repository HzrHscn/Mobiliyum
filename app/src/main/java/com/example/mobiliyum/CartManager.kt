package com.example.mobiliyum

import com.google.firebase.firestore.FirebaseFirestore

object CartManager {

    private val cartItems = ArrayList<Product>()
    private val db = FirebaseFirestore.getInstance()

    // Sepete Ekle
    fun addToCart(product: Product) {
        val existingItem = cartItems.find { it.id == product.id }
        if (existingItem != null) {
            if (existingItem.quantity < 99) { // Ekleme yaparken de sınır kontrolü
                existingItem.quantity += 1
            }
        } else {
            product.quantity = 1
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

    // Adet Artır (+) -> SINIR EKLENDİ
    fun increaseQuantity(product: Product) {
        val item = cartItems.find { it.id == product.id }
        item?.let {
            if (it.quantity < 99) { // Maksimum 99 adet
                it.quantity += 1
            }
        }
    }

    // Adet Azalt (-)
    fun decreaseQuantity(product: Product) {
        val item = cartItems.find { it.id == product.id }
        item?.let {
            if (it.quantity > 1) {
                it.quantity -= 1
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
            total += PriceUtils.parsePrice(item.price) * item.quantity
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