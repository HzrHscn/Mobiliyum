package com.example.mobiliyum

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

object DataManager {
    // Bellekteki veriler
    var cachedProducts: ArrayList<Product>? = null
    var cachedStores: ArrayList<Store>? = null

    private val db = FirebaseFirestore.getInstance()

    // --- ULTRA OPTİMİZE ÜRÜN ÇEKME ---
    fun fetchProductsSmart(
        context: Context,
        onSuccess: (ArrayList<Product>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 1. Durum: Uygulama yeni açıldı, RAM boş.
        if (cachedProducts == null) {
            fetchAllProducts(context, onSuccess, onError)
            return
        }

        // 2. Durum: RAM dolu, sadece yeni değişiklikleri (Delta) soralım.
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("lastProductSyncTime", 0L)

        db.collection("products")
            .whereGreaterThan("lastUpdated", lastSyncTime)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val newOrUpdatedProducts = ArrayList<Product>()
                    for (doc in documents) {
                        val p = doc.toObject(Product::class.java)
                        newOrUpdatedProducts.add(p)
                    }

                    // RAM'deki listeyi güncelle (Merge)
                    mergeProductsIntoCache(newOrUpdatedProducts)

                    // Yeni senkronizasyon zamanını kaydet
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("lastProductSyncTime", now).apply()
                }

                // Her durumda güncel listeyi dön
                onSuccess(cachedProducts!!)
            }
            .addOnFailureListener {
                // Hata durumunda eldekini ver
                onSuccess(cachedProducts!!)
            }
    }

    private fun fetchAllProducts(
        context: Context,
        onSuccess: (ArrayList<Product>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("products").get()
            .addOnSuccessListener { documents ->
                val list = ArrayList<Product>()
                for (doc in documents) {
                    val p = doc.toObject(Product::class.java)
                    list.add(p)
                }
                cachedProducts = list

                val now = System.currentTimeMillis()
                context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    .edit().putLong("lastProductSyncTime", now).apply()

                onSuccess(cachedProducts!!)
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Hata") }
    }

    private fun mergeProductsIntoCache(updates: List<Product>) {
        if (cachedProducts == null) cachedProducts = ArrayList()

        for (updatedItem in updates) {
            val index = cachedProducts!!.indexOfFirst { it.id == updatedItem.id }
            if (index != -1) {
                cachedProducts!![index] = updatedItem
            } else {
                cachedProducts!!.add(updatedItem)
            }
        }
    }

    // Admin tarafında anlık güncelleme için
    fun updateProductInCache(product: Product) {
        mergeProductsIntoCache(listOf(product))
    }

    // Store için güncelleme
    fun updateStoreInCache(store: Store) {
        if (cachedStores == null) cachedStores = ArrayList()
        val index = cachedStores!!.indexOfFirst { it.id == store.id }
        if (index != -1) cachedStores!![index] = store else cachedStores!!.add(store)
    }

    // EKSİK OLAN FONKSİYON:
    fun triggerServerVersionUpdate() {
        val newVersion = System.currentTimeMillis()
        db.collection("system").document("metadata")
            .set(mapOf("productsVersion" to newVersion), com.google.firebase.firestore.SetOptions.merge())
    }
}