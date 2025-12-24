package com.example.mobiliyum

object CartManager {

    private val cartItems = ArrayList<Product>()

    // Sepete Ekle
    fun addToCart(product: Product) {
        val existingItem = cartItems.find { it.id == product.id }
        if (existingItem != null) {
            if (existingItem.quantity < 99) {
                existingItem.quantity += 1
            }
        } else {
            // Sepete eklerken de kopya oluşturuyoruz ki orijinal ürün etkilenmesin
            val newProduct = product.copy(quantity = 1)
            cartItems.add(newProduct)
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

    // Adet Artır (+)
    fun increaseQuantity(product: Product) {
        val item = cartItems.find { it.id == product.id }
        item?.let {
            if (it.quantity < 99) {
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

    // Sepeti Getir (Deep Copy)
    // Bu sayede UI tarafında yapılan değişiklikler veya DiffUtil kontrolleri
    // bellekteki asıl listeyi bozmaz ve her seferinde "yeni" veri gibi algılanır.
    fun getCartItems(): List<Product> {
        return cartItems.map { it.copy() }
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

    // Tek bir ürünü tamamen silmek için (Çöp kutusu butonu)
    fun clearProduct(product: Product) {
        removeFromCart(product)
    }
}