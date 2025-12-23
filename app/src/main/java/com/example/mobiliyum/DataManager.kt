package com.example.mobiliyum

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object DataManager {

    private const val TAG = "DataManager"
    private const val FILE_PRODUCTS = "products_cache.json"
    private const val FILE_STORES = "stores_cache.json"

    private val db = FirebaseFirestore.getInstance()
    private val gson = Gson()

    var cachedProducts: ArrayList<Product> = arrayListOf()
        private set

    var cachedStores: ArrayList<Store> = arrayListOf()
        private set

    var currentAdConfig: AdConfig? = null
        private set

    // Firebase Offline Persistence (İLK AÇILIŞTA AKTİF ET)
    init {
        try {
            db.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true) // Offline cache
                .setCacheSizeBytes(50 * 1024 * 1024L) // 50MB cache
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore settings error", e)
        }
    }

    // === SMART SYNC (OPTİMİZE) ===

    fun syncDataSmart(context: Context, onComplete: (Boolean) -> Unit) {
        // 1. Disk cache'i yükle
        loadFromDisk(context)

        // 2. Metadata'yı cache'den kontrol et
        db.collection("system").document("metadata")
            .get(Source.CACHE) // Önce cache'e bak
            .addOnSuccessListener { cachedDoc ->
                if (cachedDoc.exists()) {
                    // Cache'de var, versiyon kontrolü yap
                    checkAndSync(context, cachedDoc, onComplete)
                } else {
                    // Cache boş, server'dan çek
                    fetchFromServer(context, onComplete)
                }
            }
            .addOnFailureListener {
                // Cache hatası, server'dan çek
                fetchFromServer(context, onComplete)
            }
    }

    private fun fetchFromServer(context: Context, onComplete: (Boolean) -> Unit) {
        db.collection("system").document("metadata").get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    firstInstall(context, onComplete)
                } else {
                    checkAndSync(context, doc, onComplete)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Server fetch error", it)
                onComplete(false)
            }
    }

    private fun checkAndSync(
        context: Context,
        doc: com.google.firebase.firestore.DocumentSnapshot,
        onComplete: (Boolean) -> Unit
    ) {
        // Reklam config'i parse et
        parseAdConfig(doc)

        val serverProductVer = doc.getLong("productsVersion") ?: 0L
        val serverStoreVer = doc.getLong("storesVersion") ?: 0L

        val updatedProductIds = (doc.get("updatedProductIds") as? List<*>)?.mapNotNull {
            (it as? String)?.takeIf { id -> id.isNotBlank() }
        } ?: emptyList()

        val updatedStoreIds = (doc.get("updatedStoreIds") as? List<*>)?.mapNotNull {
            (it as? String)?.takeIf { id -> id.isNotBlank() }
        } ?: emptyList()

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val localProductVer = prefs.getLong("productsVersion", -1)
        val localStoreVer = prefs.getLong("storesVersion", -1)

        // Delta sync başlat
        syncProductsDelta(context, serverProductVer, localProductVer, updatedProductIds) {
            syncStoresDelta(context, serverStoreVer, localStoreVer, updatedStoreIds) {
                onComplete(true)
            }
        }
    }

    // === DELTA SYNC (OPTİMİZE - BATCH KULLANIMI) ===

    private fun syncProductsDelta(
        context: Context,
        serverVer: Long,
        localVer: Long,
        ids: List<String>,
        onDone: () -> Unit
    ) {
        if (serverVer == localVer || ids.isEmpty()) {
            onDone()
            return
        }

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // Firestore'da whereIn limiti 10, chunk'lara böl
        val chunks = ids.distinct().chunked(10)
        var completedChunks = 0

        if (chunks.isEmpty()) {
            prefs.edit().putLong("productsVersion", serverVer).apply()
            onDone()
            return
        }

        chunks.forEach { chunk ->
            // BATCH READ: Tek sorguda 10 ürün
            db.collection("products")
                .whereIn("id", chunk.mapNotNull { it.toIntOrNull() })
                .get()
                .addOnSuccessListener { docs ->
                    for (doc in docs) {
                        doc.toObject(Product::class.java)?.let { product ->
                            updateProductInCache(context, product)
                        }
                    }
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        prefs.edit().putLong("productsVersion", serverVer).apply()

                        // Metadata'daki güncellenmiş ID listesini temizle
                        clearUpdatedProductIds()

                        onDone()
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Product delta sync failed", it)
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        onDone()
                    }
                }
        }
    }

    private fun syncStoresDelta(
        context: Context,
        serverVer: Long,
        localVer: Long,
        ids: List<String>,
        onDone: () -> Unit
    ) {
        if (serverVer == localVer || ids.isEmpty()) {
            onDone()
            return
        }

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val chunks = ids.distinct().chunked(10)
        var completedChunks = 0

        if (chunks.isEmpty()) {
            prefs.edit().putLong("storesVersion", serverVer).apply()
            onDone()
            return
        }

        chunks.forEach { chunk ->
            db.collection("stores")
                .whereIn("id", chunk.mapNotNull { it.toIntOrNull() })
                .get()
                .addOnSuccessListener { docs ->
                    for (doc in docs) {
                        doc.toObject(Store::class.java)?.let { store ->
                            updateStoreInCache(context, store)
                        }
                    }
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        prefs.edit().putLong("storesVersion", serverVer).apply()
                        clearUpdatedStoreIds()
                        onDone()
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Store delta sync failed", it)
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        onDone()
                    }
                }
        }
    }

    // Metadata'daki güncellenmiş ID listelerini temizle (spam önleme)
    private fun clearUpdatedProductIds() {
        db.collection("system").document("metadata")
            .update("updatedProductIds", emptyList<String>())
    }

    private fun clearUpdatedStoreIds() {
        db.collection("system").document("metadata")
            .update("updatedStoreIds", emptyList<String>())
    }

    // === FETCH API (CACHE ÖNCE) ===

    fun fetchProductsSmart(
        context: Context,
        onSuccess: (ArrayList<Product>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (cachedProducts.isNotEmpty()) {
            onSuccess(ArrayList(cachedProducts))
            return
        }

        db.collection("products")
            .get(Source.CACHE) // Önce cache
            .addOnSuccessListener { cached ->
                if (!cached.isEmpty) {
                    cachedProducts = ArrayList(cached.toObjects(Product::class.java))
                    onSuccess(ArrayList(cachedProducts))
                } else {
                    // Cache boş, server'dan çek
                    fetchProductsFromServer(context, onSuccess, onError)
                }
            }
            .addOnFailureListener {
                fetchProductsFromServer(context, onSuccess, onError)
            }
    }

    private fun fetchProductsFromServer(
        context: Context,
        onSuccess: (ArrayList<Product>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("products").get()
            .addOnSuccessListener { docs ->
                cachedProducts = ArrayList(docs.toObjects(Product::class.java))
                saveToDisk(context, FILE_PRODUCTS, cachedProducts)
                onSuccess(ArrayList(cachedProducts))
            }
            .addOnFailureListener {
                onError(it.localizedMessage ?: "Ürün alınamadı")
            }
    }

    fun fetchStoresSmart(
        context: Context,
        onSuccess: (ArrayList<Store>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (cachedStores.isNotEmpty()) {
            onSuccess(ArrayList(cachedStores))
            return
        }

        db.collection("stores")
            .get(Source.CACHE)
            .addOnSuccessListener { cached ->
                if (!cached.isEmpty) {
                    cachedStores = ArrayList(cached.toObjects(Store::class.java))
                    onSuccess(ArrayList(cachedStores))
                } else {
                    fetchStoresFromServer(context, onSuccess, onError)
                }
            }
            .addOnFailureListener {
                fetchStoresFromServer(context, onSuccess, onError)
            }
    }

    private fun fetchStoresFromServer(
        context: Context,
        onSuccess: (ArrayList<Store>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("stores").get()
            .addOnSuccessListener { docs ->
                cachedStores = ArrayList(docs.toObjects(Store::class.java))
                saveToDisk(context, FILE_STORES, cachedStores)
                onSuccess(ArrayList(cachedStores))
            }
            .addOnFailureListener {
                onError(it.localizedMessage ?: "Mağaza alınamadı")
            }
    }

    // === CACHE UPDATE ===

    fun updateProductInCache(context: Context, product: Product) {
        val index = cachedProducts.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            cachedProducts[index] = product
        } else {
            cachedProducts.add(product)
        }
        saveToDisk(context, FILE_PRODUCTS, cachedProducts)
    }

    fun updateStoreInCache(context: Context, store: Store) {
        val index = cachedStores.indexOfFirst { it.id == store.id }
        if (index >= 0) {
            cachedStores[index] = store
        } else {
            cachedStores.add(store)
        }
        saveToDisk(context, FILE_STORES, cachedStores)
    }

    // === POPUP AD PARSE ===

    private fun parseAdConfig(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val popup = doc.get("popupAd") as? Map<*, *> ?: return

        currentAdConfig = AdConfig(
            isActive = popup["isActive"] as? Boolean ?: false,
            imageUrl = popup["imageUrl"] as? String ?: "",
            title = popup["title"] as? String ?: "",
            endDate = popup["endDate"] as? Long ?: 0L,
            orientation = popup["orientation"] as? String ?: "VERTICAL",
            type = popup["type"] as? String ?: "",
            targetProductId = popup["targetProductId"] as? String ?: "",
            targetStoreId = popup["targetStoreId"] as? String ?: ""
        )
    }

    // === FIRST INSTALL ===

    private fun firstInstall(context: Context, onComplete: (Boolean) -> Unit) {
        db.collection("products").get().addOnSuccessListener { productDocs ->
            cachedProducts = ArrayList(productDocs.toObjects(Product::class.java))
            saveToDisk(context, FILE_PRODUCTS, cachedProducts)

            db.collection("stores").get().addOnSuccessListener { storeDocs ->
                cachedStores = ArrayList(storeDocs.toObjects(Store::class.java))
                saveToDisk(context, FILE_STORES, cachedStores)

                context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("productsVersion", 1)
                    .putLong("storesVersion", 1)
                    .apply()

                onComplete(true)
            }
        }
    }

    // === DISK CACHE ===

    private fun saveToDisk(context: Context, file: String, data: Any) {
        try {
            FileWriter(File(context.filesDir, file)).use {
                gson.toJson(data, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Disk save error", e)
        }
    }

    private fun loadFromDisk(context: Context) {
        try {
            val p = File(context.filesDir, FILE_PRODUCTS)
            if (p.exists()) {
                val type = object : TypeToken<ArrayList<Product>>() {}.type
                cachedProducts = gson.fromJson(FileReader(p), type) ?: arrayListOf()
            }

            val s = File(context.filesDir, FILE_STORES)
            if (s.exists()) {
                val type = object : TypeToken<ArrayList<Store>>() {}.type
                cachedStores = gson.fromJson(FileReader(s), type) ?: arrayListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Disk load error", e)
            cachedProducts = arrayListOf()
            cachedStores = arrayListOf()
        }
    }

    // === VERSION UPDATE TRIGGER (ADMIN/EDITOR) ===

    fun triggerServerVersionUpdate(
        updatedProductId: String? = null,
        updatedStoreId: String? = null
    ) {
        val updates = hashMapOf<String, Any>()

        if (!updatedProductId.isNullOrBlank()) {
            updates["productsVersion"] = FieldValue.increment(1)
            updates["updatedProductIds"] = FieldValue.arrayUnion(updatedProductId)
        }

        if (!updatedStoreId.isNullOrBlank()) {
            updates["storesVersion"] = FieldValue.increment(1)
            updates["updatedStoreIds"] = FieldValue.arrayUnion(updatedStoreId)
        }

        if (updates.isEmpty()) return

        db.collection("system").document("metadata")
            .update(updates)
            .addOnFailureListener {
                Log.e(TAG, "Version update failed", it)
            }
    }
}