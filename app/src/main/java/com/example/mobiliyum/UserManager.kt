package com.example.mobiliyum

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

object UserManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Anlık rolü hafızada tutuyoruz (Veritabanı gelince burası oradan dolacak)
    private var currentUserRole: UserRole = UserRole.CUSTOMER

    /**
     * KULLANICI GİRİŞİ (LOGIN)
     * @param onSuccess: İşlem başarılı olursa çalışacak kod bloğu
     * @param onFailure: Hata olursa çalışacak ve hatayı verecek kod bloğu
     */
    fun login(email: String, pass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                // Giriş başarılı, şimdi rolü belirle
                refreshUserRole()
                onSuccess()
            }
            .addOnFailureListener { exception ->
                // Hata mesajını (örneğin: Şifre yanlış) geri döndür
                onFailure(exception.localizedMessage ?: "Giriş başarısız.")
            }
    }

    /**
     * YENİ KULLANICI KAYDI (REGISTER)
     * Hem kayıt olur hem de kullanıcının "Ad Soyad" bilgisini profiline işler.
     */
    fun register(email: String, pass: String, fullName: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                // 1. Kayıt başarılı, şimdi Ad-Soyad bilgisini Firebase User profiline ekleyelim
                val user = authResult.user
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                user?.updateProfile(profileUpdates)
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // 2. Profil de güncellendi, işlem tamam
                            refreshUserRole()
                            onSuccess()
                        } else {
                            // Kayıt oldu ama isim eklenemedi (Nadir durum)
                            onFailure("Kayıt oldu ancak profil güncellenemedi.")
                        }
                    }
            }
            .addOnFailureListener { exception ->
                onFailure(exception.localizedMessage ?: "Kayıt başarısız.")
            }
    }

    /**
     * ÇIKIŞ YAP (LOGOUT)
     */
    fun logout() {
        auth.signOut()
        currentUserRole = UserRole.CUSTOMER
    }

    /**
     * ŞU ANKİ KULLANICIYI GETİR
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * GİRİŞ YAPILI MI KONTROLÜ
     */
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * ROL BELİRLEME (GEÇİCİ MANTIK)
     * Veritabanı (Firestore) bağlandığında burası veritabanından okunacak.
     * Şimdilik e-posta adresine göre "Admin" veya "Müdür" veriyoruz ki sistemi test edebilesin.
     */
    fun refreshUserRole() {
        val email = auth.currentUser?.email ?: return

        currentUserRole = when {
            email == "hazarhascan@gmail.com" -> UserRole.ADMIN
            email == "srv@mobiliyum.com" -> UserRole.SRV
            email.contains("cilek") -> UserRole.MANAGER  // Örn: mudur@cilek.com
            email.contains("editor") -> UserRole.EDITOR
            else -> UserRole.CUSTOMER
        }
    }

    // --- YETKİ SORGULARI ---

    fun getUserRole(): UserRole {
        // Eğer uygulama yeni açıldıysa ve kullanıcı zaten giriş yapmışsa rolü tekrar hesapla
        if (currentUserRole == UserRole.CUSTOMER && isLoggedIn()) {
            refreshUserRole()
        }
        return currentUserRole
    }

    fun canEditProduct(product: Product): Boolean {
        // Admin her şeyi düzenler, Manager sadece kendi mağazasını (İleride storeId kontrolü eklenecek)
        val role = getUserRole()
        return role == UserRole.ADMIN || role == UserRole.MANAGER || role == UserRole.EDITOR
    }

    fun canViewAdminPanel(): Boolean {
        val role = getUserRole()
        return role != UserRole.CUSTOMER
    }
}