package com.example.mobiliyum

object CartManager {
    private val cartItems = ArrayList<Product>()

    fun addToCart(product: Product) = cartItems.add(product)
    fun removeFromCart(product: Product) = cartItems.remove(product)
    fun getCartItems(): List<Product> = cartItems
    fun clearCart() = cartItems.clear()

    // String döndüren eski fonksiyon yerine hesaplama yapıp Double döndüren fonksiyon
    fun calculateTotalAmount(): Double {
        var total = 0.0
        for (item in cartItems) {
            try {
                // PriceUtils içindeki temizleme mantığının aynısı
                var cleanPrice = item.price.replace("[^\\d.,]".toRegex(), "").trim()
                if (cleanPrice.isNotEmpty()) {
                    if (cleanPrice.contains(",")) {
                        cleanPrice = cleanPrice.replace(".", "").replace(",", ".")
                    } else {
                        cleanPrice = cleanPrice.replace(".", "")
                    }
                    total += cleanPrice.toDouble()
                }
            } catch (e: Exception) { }
        }
        return total
    }

    // Eski fonksiyonu uyumluluk için tutabiliriz ama artık PriceUtils kullanıyoruz
    fun getTotalPrice(): String = PriceUtils.formatPriceStyled(calculateTotalAmount()).toString()
}