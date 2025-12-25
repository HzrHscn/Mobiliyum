package com.example.mobiliyum

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object DataManager {

    private const val TAG = "DataManager"

    private const val FILE_PRODUCTS = "products_cache.json"
    private const val FILE_STORES = "stores_cache.json"
    private const val PRODUCT_RESET_THRESHOLD = 1000
    private const val STORE_RESET_THRESHOLD = 500

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    fun getDb(): FirebaseFirestore = firestore

    private val gson = Gson()

    var cachedProducts = arrayListOf<Product>()
        private set
    var cachedStores = arrayListOf<Store>()
        private set

    var currentAdConfig: AdConfig? = null
        private set

    // ===================== INIT =====================

    /*init {
        try {
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(50L * 1024 * 1024)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore init error", e)
        }
    }*/

    // ===================== SMART SYNC ENTRY =====================

    fun syncDataSmart(context: Context, onComplete: (Boolean) -> Unit) {
        loadFromDisk(context)

        firestore.collection("system").document("metadata")
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    firstInstall(context, onComplete)
                } else {
                    handleMetadata(context, doc, onComplete)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Metadata fetch failed", it)
                onComplete(false)
            }
    }

    // ===================== METADATA =====================

    private fun handleMetadata(
        context: Context,
        doc: DocumentSnapshot,
        onComplete: (Boolean) -> Unit
    ) {
        parseAdConfig(doc)

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val serverProductVer = doc.getLong("productsVersion") ?: 0L
        val serverStoreVer = doc.getLong("storesVersion") ?: 0L

        val localProductVer = prefs.getLong("productsVersion", -1L)
        val localStoreVer = prefs.getLong("storesVersion", -1L)

        val productIds = (doc.get("updatedProductIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val storeIds = (doc.get("updatedStoreIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        syncProducts(context, serverProductVer, localProductVer, productIds) {
            syncStores(context, serverStoreVer, localStoreVer, storeIds) {
                onComplete(true)
            }
        }
    }

    // ===================== PRODUCT SYNC =====================

    private fun syncProducts(
        context: Context,
        serverVer: Long,
        localVer: Long,
        ids: List<String>,
        onDone: () -> Unit
    ) {
        val chunks = ids.distinct().chunked(10)
        var finished = 0

        chunks.forEach { chunk ->

            val safeIds = chunk.mapNotNull { it.toIntOrNull() }

            // 🔴 KRİTİK SATIR
            if (safeIds.isEmpty()) {
                if (++finished == chunks.size) {
                    saveVersion(context, "productsVersion", serverVer)
                    clearUpdatedProductIds()
                    onDone()
                }
                return@forEach
            }

            firestore.collection("products")
                .whereIn("id", safeIds)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach {
                        it.toObject(Product::class.java)?.let { p ->
                            updateProductInCache(context, p)
                        }
                    }
                    if (++finished == chunks.size) {
                        saveVersion(context, "productsVersion", serverVer)
                        clearUpdatedProductIds()
                        onDone()
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Product delta failed", it)
                    if (++finished == chunks.size) onDone()
                }
        }
    }

    private fun fullProductSync(context: Context, version: Long, onDone: () -> Unit) {
        firestore.collection("products").get()
            .addOnSuccessListener {
                cachedProducts = ArrayList(it.toObjects(Product::class.java))
                saveToDisk(context, FILE_PRODUCTS, cachedProducts)
                saveVersion(context, "productsVersion", version)
                onDone()
            }
            .addOnFailureListener {
                Log.e(TAG, "Full product sync failed", it)
                onDone()
            }
    }

    // ===================== STORE SYNC =====================

    private fun syncStores(
        context: Context,
        serverVer: Long,
        localVer: Long,
        ids: List<String>,
        onDone: () -> Unit
    ) {
        val chunks = ids.distinct().chunked(10)
        var finished = 0

        chunks.forEach { chunk ->

            val safeIds = chunk.mapNotNull { it.toIntOrNull() }

            // 🔴 KRİTİK SATIR
            if (safeIds.isEmpty()) {
                if (++finished == chunks.size) {
                    saveVersion(context, "storesVersion", serverVer)
                    clearUpdatedStoreIds()
                    onDone()
                }
                return@forEach
            }

            firestore.collection("stores")
                .whereIn("id", safeIds)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach {
                        it.toObject(Store::class.java)?.let { s ->
                            updateStoreInCache(context, s)
                        }
                    }
                    if (++finished == chunks.size) {
                        saveVersion(context, "storesVersion", serverVer)
                        clearUpdatedStoreIds()
                        onDone()
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Store delta failed", it)
                    if (++finished == chunks.size) onDone()
                }
        }
    }

    private fun fullStoreSync(context: Context, version: Long, onDone: () -> Unit) {
        firestore.collection("stores").get()
            .addOnSuccessListener {
                cachedStores = ArrayList(it.toObjects(Store::class.java))
                saveToDisk(context, FILE_STORES, cachedStores)
                saveVersion(context, "storesVersion", version)
                onDone()
            }
            .addOnFailureListener {
                Log.e(TAG, "Full store sync failed", it)
                onDone()
            }
    }

    // ===================== ADMIN TRIGGER =====================

    fun triggerServerVersionUpdate(
        updatedProductId: String? = null,
        updatedStoreId: String? = null
    ) {
        val ref = firestore.collection("system").document("metadata")

        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val updates = mutableMapOf<String, Any>()

            updatedProductId?.let {
                val count = (snap.getLong("productUpdateCount") ?: 0L) + 1
                if (count >= PRODUCT_RESET_THRESHOLD) {
                    updates["productsVersion"] = FieldValue.increment(1)
                    updates["updatedProductIds"] = emptyList<String>()
                    updates["productUpdateCount"] = 0L
                } else {
                    updates["updatedProductIds"] = FieldValue.arrayUnion(it)
                    updates["productUpdateCount"] = count
                }
            }

            updatedStoreId?.let {
                val count = (snap.getLong("storeUpdateCount") ?: 0L) + 1
                if (count >= STORE_RESET_THRESHOLD) {
                    updates["storesVersion"] = FieldValue.increment(1)
                    updates["updatedStoreIds"] = emptyList<String>()
                    updates["storeUpdateCount"] = 0L
                } else {
                    updates["updatedStoreIds"] = FieldValue.arrayUnion(it)
                    updates["storeUpdateCount"] = count
                }
            }

            if (updates.isNotEmpty()) tx.update(ref, updates)
        }.addOnFailureListener {
            Log.e(TAG, "Metadata update failed", it)
        }
    }

    // ===================== CACHE =====================

    fun updateProductInCache(context: Context, p: Product) {
        val i = cachedProducts.indexOfFirst { it.id == p.id }
        if (i >= 0) cachedProducts[i] = p else cachedProducts.add(p)
        saveToDisk(context, FILE_PRODUCTS, cachedProducts)
    }

    fun updateStoreInCache(context: Context, s: Store) {
        val i = cachedStores.indexOfFirst { it.id == s.id }
        if (i >= 0) cachedStores[i] = s else cachedStores.add(s)
        saveToDisk(context, FILE_STORES, cachedStores)
    }

    // ===================== DISK =====================

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
            File(context.filesDir, FILE_PRODUCTS).takeIf { it.exists() }?.let {
                val t = object : TypeToken<ArrayList<Product>>() {}.type
                cachedProducts = gson.fromJson(FileReader(it), t) ?: arrayListOf()
            }
            File(context.filesDir, FILE_STORES).takeIf { it.exists() }?.let {
                val t = object : TypeToken<ArrayList<Store>>() {}.type
                cachedStores = gson.fromJson(FileReader(it), t) ?: arrayListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Disk load error", e)
        }
    }

    // ===================== ADS =====================

    private fun parseAdConfig(doc: DocumentSnapshot) {
        val popup = doc.get("popupAd") as? Map<*, *> ?: return
        currentAdConfig = AdConfig(
            isActive = popup["isActive"] as? Boolean ?: false,
            imageUrl = popup["imageUrl"] as? String ?: "",
            title = popup["title"] as? String ?: "",
            endDate = (popup["endDate"] as? Number)?.toLong() ?: 0L,
            orientation = popup["orientation"] as? String ?: "VERTICAL",
            type = popup["type"] as? String ?: "",
            targetProductId = popup["targetProductId"] as? String ?: "",
            targetStoreId = popup["targetStoreId"] as? String ?: ""
        )
    }

    // ===================== FIRST INSTALL =====================

    private fun firstInstall(context: Context, onComplete: (Boolean) -> Unit) {
        fullProductSync(context, 1L) {
            fullStoreSync(context, 1L) {
                onComplete(true)
            }
        }
    }

    // ===================== HELPERS =====================

    private fun saveVersion(context: Context, key: String, value: Long) {
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .edit().putLong(key, value).apply()
    }

    private fun clearUpdatedProductIds() {
        firestore.collection("system").document("metadata")
            .update("updatedProductIds", emptyList<String>())
    }

    private fun clearUpdatedStoreIds() {
        firestore.collection("system").document("metadata")
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

        firestore.collection("products")
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
        firestore.collection("products").get()
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
        firestore.collection("stores")
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
        firestore.collection("stores").get()
            .addOnSuccessListener { docs ->
                cachedStores = ArrayList(docs.toObjects(Store::class.java))
                saveToDisk(context, FILE_STORES, cachedStores)
                onSuccess(ArrayList(cachedStores))
            }
            .addOnFailureListener {
                onError(it.localizedMessage ?: "Mağaza alınamadı")
            }
    }
}
