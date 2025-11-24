package com.example.mobiliyum

object CartManager {

    private val cartItems = ArrayList<Product>()

    fun addToCart(product: Product) {
        cartItems.add(product)
    }

    fun removeFromCart(product: Product) {
        cartItems.remove(product)
    }

    fun getCartItems(): List<Product> {
        return cartItems
    }

    fun clearCart() {
        cartItems.clear()
    }

    fun getTotalPrice(): Double{
        var total = 0.0
        for (item in cartItems) {
            val cleanPrice = item.price
                .replace(" ₺", "")
                .replace(".", "")
                .trim()
            if (cleanPrice.isNotEmpty()) {
                total += cleanPrice.toDoubleOrNull() ?: 0.0
            }
        }
        return total
    }
}