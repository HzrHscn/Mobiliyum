package com.example.mobiliyum

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
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

    // 🔓 PUBLIC – NULL YOK
    var cachedProducts: ArrayList<Product> = arrayListOf()
        private set

    var cachedStores: ArrayList<Store> = arrayListOf()
        private set

    // 🔥 REKLAM CONFIG
    var currentAdConfig: AdConfig? = null
        private set

    // =====================================================
    // 🔹 APP START SYNC
    // =====================================================
    fun syncDataSmart(context: Context, onComplete: (Boolean) -> Unit) {
        loadFromDisk(context)

        db.collection("system").document("metadata").get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    firstInstall(context, onComplete)
                    return@addOnSuccessListener
                }

                // ---- POPUP AD ----
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

                syncProductsDelta(context, serverProductVer, localProductVer, updatedProductIds) {
                    syncStoresDelta(context, serverStoreVer, localStoreVer, updatedStoreIds) {
                        onComplete(true)
                    }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Metadata error", it)
                onComplete(true)
            }
    }

    // =====================================================
    // 🔹 FETCH API
    // =====================================================
    fun fetchProductsSmart(
        context: Context,
        onSuccess: (ArrayList<Product>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (cachedProducts.isNotEmpty()) {
            onSuccess(ArrayList(cachedProducts))
            return
        }

        db.collection("products").get()
            .addOnSuccessListener {
                cachedProducts = ArrayList(it.toObjects(Product::class.java))
                saveToDisk(context, FILE_PRODUCTS, cachedProducts)
                onSuccess(ArrayList(cachedProducts))
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Ürün alınamadı") }
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

        db.collection("stores").get()
            .addOnSuccessListener {
                cachedStores = ArrayList(it.toObjects(Store::class.java))
                saveToDisk(context, FILE_STORES, cachedStores)
                onSuccess(ArrayList(cachedStores))
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Mağaza alınamadı") }
    }

    // =====================================================
    // 🔹 CACHE UPDATE (ADMIN / EDITOR)
    // =====================================================
    fun updateProductInCache(context: Context, product: Product) {
        val index = cachedProducts.indexOfFirst { it.id == product.id }
        if (index >= 0) cachedProducts[index] = product else cachedProducts.add(product)
        saveToDisk(context, FILE_PRODUCTS, cachedProducts)
    }

    fun updateStoreInCache(context: Context, store: Store) {
        val index = cachedStores.indexOfFirst { it.id == store.id }
        if (index >= 0) cachedStores[index] = store else cachedStores.add(store)
        saveToDisk(context, FILE_STORES, cachedStores)
    }

    // =====================================================
    // 🔹 DELTA SYNC
    // =====================================================
    private fun syncProductsDelta(
        context: Context,
        serverVer: Long,
        localVer: Long,
        ids: List<String>,
        onDone: () -> Unit
    ) {
        if (serverVer == localVer || ids.isEmpty()) {
            onDone(); return
        }

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var remaining = ids.size

        ids.distinct().forEach { id ->
            db.collection("products").document(id).get()
                .addOnSuccessListener {
                    it.toObject(Product::class.java)?.let { p ->
                        updateProductInCache(context, p)
                    }
                }
                .addOnCompleteListener {
                    remaining--
                    if (remaining == 0) {
                        prefs.edit().putLong("productsVersion", serverVer).apply()
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
            onDone(); return
        }

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var remaining = ids.size

        ids.distinct().forEach { id ->
            db.collection("stores").document(id).get()
                .addOnSuccessListener {
                    it.toObject(Store::class.java)?.let { s ->
                        updateStoreInCache(context, s)
                    }
                }
                .addOnCompleteListener {
                    remaining--
                    if (remaining == 0) {
                        prefs.edit().putLong("storesVersion", serverVer).apply()
                        onDone()
                    }
                }
        }
    }

    // =====================================================
    // 🔹 POPUP AD PARSE
    // =====================================================
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

    // =====================================================
    // 🔹 FIRST INSTALL
    // =====================================================
    private fun firstInstall(context: Context, onComplete: (Boolean) -> Unit) {
        db.collection("products").get().addOnSuccessListener {
            cachedProducts = ArrayList(it.toObjects(Product::class.java))
            saveToDisk(context, FILE_PRODUCTS, cachedProducts)

            db.collection("stores").get().addOnSuccessListener { s ->
                cachedStores = ArrayList(s.toObjects(Store::class.java))
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

    // =====================================================
    // 🔹 DISK CACHE
    // =====================================================
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
                cachedProducts = gson.fromJson(FileReader(p), type)
            }

            val s = File(context.filesDir, FILE_STORES)
            if (s.exists()) {
                val type = object : TypeToken<ArrayList<Store>>() {}.type
                cachedStores = gson.fromJson(FileReader(s), type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Disk load error", e)
        }
    }
    // =====================================================
// 🔹 ADMIN VERSION & DELTA TRIGGER
// =====================================================
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

        FirebaseFirestore.getInstance()
            .collection("system")
            .document("metadata")
            .update(updates)
    }
}
