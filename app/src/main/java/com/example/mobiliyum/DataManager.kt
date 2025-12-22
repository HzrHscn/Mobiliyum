package com.example.mobiliyum

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

// --- GÜNCELLENMİŞ REKLAM MODELİ ---
data class AdConfig(
    val isActive: Boolean = false,
    val imageUrl: String = "",
    val type: String = "STORE", // "STORE" veya "PRODUCT"
    val orientation: String = "VERTICAL", // "VERTICAL" veya "HORIZONTAL" (YENİ)
    val targetStoreId: String = "",
    val targetProductId: String = "", // Yeni: Ürün ID'si
    val title: String = "",
    val endDate: Long = 0L // Bitiş zamanı (Timestamp)
)

object DataManager {
    // RAM Önbelleği
    var cachedProducts: ArrayList<Product>? = null
    var cachedStores: ArrayList<Store>? = null

    // Anlık Reklam Verisi
    var currentAdConfig: AdConfig? = null

    private val db = FirebaseFirestore.getInstance()
    private val gson = Gson()

    private const val FILE_PRODUCTS = "products_cache.json"
    private const val FILE_STORES = "stores_cache.json"

    // --- ANA SENKRONİZASYON (Uygulama Açılışında Çağrılır) ---
    fun syncDataSmart(context: Context, onComplete: (Boolean) -> Unit) {
        // 1. Önce diskteki (telefondaki) veriyi RAM'e yükle
        loadFromDisk(context)

        // 2. Metadata'yı kontrol et (Reklam ve Mağaza Versiyonu için)
        db.collection("system").document("metadata").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // --- A. REKLAM BİLGİSİNİ AL ---
                    val adData = document.get("popupAd") as? Map<String, Any>
                    if (adData != null) {
                        currentAdConfig = AdConfig(
                            isActive = adData["isActive"] as? Boolean ?: false,
                            imageUrl = adData["imageUrl"] as? String ?: "",
                            type = adData["type"] as? String ?: "STORE",
                            orientation = adData["orientation"] as? String ?: "VERTICAL", // YENİ
                            targetStoreId = adData["targetStoreId"] as? String ?: "",
                            targetProductId = adData["targetProductId"] as? String ?: "",
                            title = adData["title"] as? String ?: "",
                            endDate = (adData["endDate"] as? Number)?.toLong() ?: 0L
                        )
                    }
                    // --- B. MAĞAZA KONTROLÜ ---
                    val serverStoresVer = document.getLong("storesVersion") ?: 0L
                    val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    val localStoresVer = prefs.getLong("localStoresVersion", -1L)
                    // --- C. ÜRÜN KONTROLÜ (DELTA SYNC) ---
                    fetchProductsDelta(context) {
                        // Ürünler bitince mağazaları kontrol et
                        checkStores(context, serverStoresVer, localStoresVer, onComplete)
                    }
                } else {
                    // Metadata yoksa ilk kurulum (Full Sync)
                    fetchAllFirstTime(context, onComplete)
                }
            }
            .addOnFailureListener {
                // İnternet yoksa veya hata varsa eldeki verilerle devam et
                onComplete(true)
            }
    }

    // --- DELTA SYNC: SADECE DEĞİŞEN ÜRÜNLERİ ÇEK ---
    private fun fetchProductsDelta(context: Context, onDone: () -> Unit) {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("lastProductSyncTime", 0L)

        // "Bana son güncelleme tarihinden sonra değişenleri ver" sorgusu
        db.collection("products")
            .whereGreaterThan("lastUpdated", lastSyncTime)
            .get()
            .addOnSuccessListener { documents ->
                val newItems = documents.toObjects(Product::class.java)

                if (newItems.isNotEmpty()) {
                    Log.d("DataManager", "${newItems.size} yeni/güncel ürün bulundu. Birleştiriliyor...")

                    if (cachedProducts == null) cachedProducts = ArrayList()

                    // MERGE (BİRLEŞTİRME) ALGORİTMASI
                    for (newItem in newItems) {
                        // RAM'deki listede bu ürün var mı?
                        val index = cachedProducts!!.indexOfFirst { it.id == newItem.id }
                        if (index != -1) {
                            // Varsa güncelle
                            cachedProducts!![index] = newItem
                        } else {
                            // Yoksa yeni ekle
                            cachedProducts!!.add(newItem)
                        }
                    }

                    // Güncel listeyi diske kaydet
                    saveToDisk(context, FILE_PRODUCTS, cachedProducts!!)

                    // Son güncelleme zamanını kaydet
                    prefs.edit().putLong("lastProductSyncTime", System.currentTimeMillis()).apply()
                } else {
                    Log.d("DataManager", "Ürünlerde değişiklik yok.")
                }
                onDone()
            }
            .addOnFailureListener {
                Log.e("DataManager", "Delta Sync hatası: ${it.message}")
                onDone()
            }
    }

    // --- MAĞAZA KONTROLÜ (Versiyon Bazlı) ---
    private fun checkStores(context: Context, serverVer: Long, localVer: Long, onComplete: (Boolean) -> Unit) {
        if (serverVer > localVer || cachedStores.isNullOrEmpty()) {
            db.collection("stores").get().addOnSuccessListener { documents ->
                cachedStores = ArrayList(documents.toObjects(Store::class.java))
                saveToDisk(context, FILE_STORES, cachedStores!!)

                context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    .edit().putLong("localStoresVersion", serverVer).apply()

                onComplete(true)
            }
        } else {
            onComplete(true)
        }
    }

    // --- UI TARAFINDAN KULLANILAN YARDIMCILAR ---

    // Ürünleri UI'a güvenli şekilde verir
    fun fetchProductsSmart(context: Context, onSuccess: (ArrayList<Product>) -> Unit, onError: (String) -> Unit) {
        if (!cachedProducts.isNullOrEmpty()) {
            onSuccess(cachedProducts!!)
        } else {
            loadFromDisk(context)
            if (!cachedProducts.isNullOrEmpty()) {
                onSuccess(cachedProducts!!)
            } else {
                // Disk de boşsa (ilk kurulumda hata vb.) tekrar dene
                fetchProductsDelta(context) {
                    if (!cachedProducts.isNullOrEmpty()) onSuccess(cachedProducts!!) else onError("Ürün bulunamadı")
                }
            }
        }
    }

    // Mağazaları UI'a güvenli şekilde verir
    fun fetchStoresSmart(context: Context, onSuccess: (ArrayList<Store>) -> Unit, onError: (String) -> Unit) {
        if (!cachedStores.isNullOrEmpty()) {
            onSuccess(cachedStores!!)
        } else {
            loadFromDisk(context)
            if (!cachedStores.isNullOrEmpty()) {
                onSuccess(cachedStores!!)
            } else {
                db.collection("stores").get()
                    .addOnSuccessListener {
                        cachedStores = ArrayList(it.toObjects(Store::class.java))
                        saveToDisk(context, FILE_STORES, cachedStores!!)
                        onSuccess(cachedStores!!)
                    }
                    .addOnFailureListener { onError(it.message ?: "Hata") }
            }
        }
    }

    // Tek bir ürünü güncelle (Admin panelinden çağrılır)
    fun updateProductInCache(context: Context, product: Product) {
        if (cachedProducts == null) cachedProducts = ArrayList()

        val index = cachedProducts!!.indexOfFirst { it.id == product.id }
        if (index != -1) {
            cachedProducts!![index] = product
        } else {
            cachedProducts!!.add(product)
        }
        saveToDisk(context, FILE_PRODUCTS, cachedProducts!!)
    }

    // Tek bir mağazayı güncelle
    fun updateStoreInCache(context: Context, store: Store) {
        if (cachedStores == null) cachedStores = ArrayList()

        val index = cachedStores!!.indexOfFirst { it.id == store.id }
        if (index != -1) {
            cachedStores!![index] = store
        } else {
            cachedStores!!.add(store)
        }
        saveToDisk(context, FILE_STORES, cachedStores!!)
    }

    // Metadata versiyonunu artır
    fun triggerServerVersionUpdate() {
        // Mağazalar ve genel yapı için versiyonu artırıyoruz
        db.collection("system").document("metadata")
            .update("storesVersion", FieldValue.increment(1))
            .addOnFailureListener {
                // Belge yoksa oluştur
                val initialData = hashMapOf(
                    "storesVersion" to 1,
                    "productsVersion" to 1 // Legacy için tutuyoruz
                )
                db.collection("system").document("metadata").set(initialData)
            }
    }

    private fun fetchAllFirstTime(context: Context, onComplete: (Boolean) -> Unit) {
        fetchProductsDelta(context) {
            checkStores(context, 1, 0, onComplete)
        }
    }

    // --- DİSK OKUMA/YAZMA ---
    private fun saveToDisk(context: Context, fileName: String, data: Any) {
        try {
            val file = File(context.filesDir, fileName)
            val writer = FileWriter(file)
            gson.toJson(data, writer)
            writer.flush()
            writer.close()
        } catch (e: Exception) { Log.e("DataManager", "Save Error: $e") }
    }

    private fun loadFromDisk(context: Context) {
        try {
            val pFile = File(context.filesDir, FILE_PRODUCTS)
            if (pFile.exists()) {
                val type = object : TypeToken<ArrayList<Product>>() {}.type
                cachedProducts = gson.fromJson(FileReader(pFile), type) ?: ArrayList()
            }
            val sFile = File(context.filesDir, FILE_STORES)
            if (sFile.exists()) {
                val type = object : TypeToken<ArrayList<Store>>() {}.type
                cachedStores = gson.fromJson(FileReader(sFile), type) ?: ArrayList()
            }
        } catch (e: Exception) { Log.e("DataManager", "Load Error: $e") }
    }
}