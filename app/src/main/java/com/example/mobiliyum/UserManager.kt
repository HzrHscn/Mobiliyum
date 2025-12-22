package com.example.mobiliyum

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

object UserManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Uygulama boyunca kullanılacak oturum bilgisi
    private var currentUserData: User? = null

    // --- OTURUM KONTROLÜ (GİRİŞTE ÇAĞRILIR) ---
    fun checkSession(onResult: (Boolean) -> Unit) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            // Kullanıcı verisini çek ve hafızaya al
            fetchUserProfile(firebaseUser.uid,
                onSuccess = { onResult(true) },
                onFailure = { onResult(false) }
            )
        } else {
            onResult(false)
        }
    }

    // --- GİRİŞ (SPAM KORUMALI) ---
    fun login(email: String, pass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val user = authResult.user

                if (user != null) {
                    fetchUserProfile(user.uid,
                        onSuccess = {
                            if (user.isEmailVerified) {
                                onSuccess() // Giriş başarılı
                            } else {
                                checkAndResendVerification(user, onFailure)
                            }
                        },
                        onFailure = { err ->
                            auth.signOut()
                            onFailure("Kullanıcı verisi alınamadı: $err")
                        }
                    )
                } else {
                    onFailure("Kullanıcı bulunamadı.")
                }
            }
            .addOnFailureListener {
                onFailure("Giriş başarısız: ${it.localizedMessage}")
            }
    }

    // --- KAYIT (ZAMAN DAMGALI) ---
    fun register(email: String, pass: String, fullName: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    // Kullanıcı Adını (Display Name) Firebase Auth'a kaydet
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName).build()
                    firebaseUser.updateProfile(profileUpdates)

                    // Doğrulama maili gönder
                    firebaseUser.sendEmailVerification()

                    // Firestore'a kaydedilecek veriyi hazırla
                    val now = System.currentTimeMillis()
                    val newUser = User(
                        id = firebaseUser.uid,
                        email = email,
                        fullName = fullName,
                        role = UserRole.CUSTOMER, // Varsayılan Müşteri
                        username = email.substringBefore("@"),
                        lastVerificationMailSent = now,
                        lastProfileUpdate = now,
                        lastPasswordUpdate = now
                    )

                    saveUserToFirestore(newUser,
                        onSuccess = {
                            auth.signOut() // Kayıttan sonra çıkış yap (Mail onayı için)
                            onSuccess()
                        },
                        onFailure = onFailure
                    )
                }
            }
            .addOnFailureListener { onFailure(it.localizedMessage ?: "Kayıt başarısız.") }
    }

    // --- PROFİL ÇEKME ---
    fun fetchUserProfile(uid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        // Firebase'den gelen veriyi User objesine çevir
                        val roleStr = document.getString("role") ?: "CUSTOMER"
                        val roleEnum = try { UserRole.valueOf(roleStr) } catch(e:Exception){ UserRole.CUSTOMER }

                        currentUserData = User(
                            id = uid,
                            email = document.getString("email") ?: "",
                            fullName = document.getString("fullName") ?: "",
                            role = roleEnum,
                            storeId = document.getLong("storeId")?.toInt(),
                            username = document.getString("username") ?: "",
                            isBanned = document.getBoolean("isBanned") ?: false,
                            lastProfileUpdate = document.getLong("lastProfileUpdate") ?: 0L,
                            lastPasswordUpdate = document.getLong("lastPasswordUpdate") ?: 0L,
                            lastVerificationMailSent = document.getLong("lastVerificationMailSent") ?: 0L
                        )

                        if (currentUserData?.isBanned == true) {
                            logout()
                            onFailure("Hesabınız askıya alınmıştır.")
                        } else {
                            onSuccess()
                        }
                    } catch (e: Exception) {
                        onFailure("Veri hatası: ${e.message}")
                    }
                } else {
                    onFailure("Kullanıcı profili bulunamadı.")
                }
            }
            .addOnFailureListener { onFailure("Hata: ${it.message}") }
    }

    // --- YETKİ KONTROLÜ (MANAGER ve EDITOR DESTEKLİ) ---
    fun canEditProduct(product: Product): Boolean {
        val role = getUserRole()
        val myStoreId = currentUserData?.storeId

        // Admin her şeyi düzenler
        if (role == UserRole.ADMIN) return true

        // Manager ve Editör sadece kendi mağazasını
        if ((role == UserRole.MANAGER || role == UserRole.EDITOR) && myStoreId != null) {
            return myStoreId == product.storeId
        }
        return false
    }

    // Admin paneli menüsünü görebilir mi?
    fun canViewAdminPanel(): Boolean {
        // Müşteri veya Standart User DEĞİLSE görebilir
        return getUserRole() != UserRole.CUSTOMER
    }

    // --- YARDIMCILAR (SPAM VE KAYIT) ---
    private fun checkAndResendVerification(user: FirebaseUser, onFailure: (String) -> Unit) {
        val dbUser = currentUserData ?: return
        val now = System.currentTimeMillis()
        val oneHourMs = 3600 * 1000L

        val lastSent = dbUser.lastVerificationMailSent
        val timeDiff = now - lastSent

        if (timeDiff > oneHourMs) {
            user.sendEmailVerification().addOnCompleteListener { task ->
                auth.signOut()
                if (task.isSuccessful) {
                    db.collection("users").document(user.uid).update("lastVerificationMailSent", now)
                    onFailure("Hesabınız doğrulanmamış. Yeni bir mail gönderildi! Lütfen spam kutusunu kontrol edin.")
                } else {
                    onFailure("Mail gönderilemedi. Lütfen daha sonra tekrar deneyin.")
                }
            }
        } else {
            auth.signOut()
            val remaining = (oneHourMs - timeDiff) / 60000
            onFailure("Hesabınız doğrulanmamış. Çok sık işlem yaptınız. Yeni mail için $remaining dakika beklemelisiniz.")
        }
    }

    private fun saveUserToFirestore(user: User, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(user.id).set(user)
            .addOnSuccessListener {
                currentUserData = user
                onSuccess()
            }
            .addOnFailureListener { onFailure("Veritabanı hatası: ${it.message}") }
    }

    // --- PROFİL GÜNCELLEME FONKSİYONLARI ---

    fun updateUserName(newName: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return
        val dbUser = currentUserData ?: return

        val updates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()

        user.updateProfile(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                dbUser.fullName = newName
                dbUser.lastProfileUpdate = System.currentTimeMillis()

                db.collection("users").document(user.uid).set(dbUser)
                    .addOnSuccessListener {
                        currentUserData = dbUser // RAM'i de güncelle
                        onComplete(true)
                    }
                    .addOnFailureListener { onComplete(false) }
            } else {
                onComplete(false)
            }
        }
    }

    fun updateUserPassword(oldPass: String, newPass: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        val dbUser = currentUserData

        if (user == null || user.email == null || dbUser == null) {
            onComplete(false, "Kullanıcı bulunamadı")
            return
        }

        // Önce eski şifreyle tekrar giriş yap (Re-auth)
        val credential = EmailAuthProvider.getCredential(user.email!!, oldPass)

        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                // Şifre doğru, şimdi değiştir
                user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        dbUser.lastPasswordUpdate = System.currentTimeMillis()

                        db.collection("users").document(user.uid).set(dbUser)
                        onComplete(true, null)
                    } else {
                        onComplete(false, updateTask.exception?.localizedMessage)
                    }
                }
            } else {
                onComplete(false, "Mevcut şifre hatalı.")
            }
        }
    }

    // --- GETTER & SETTER ---
    fun getCurrentUser(): User? = currentUserData

    fun getUserRole(): UserRole = currentUserData?.role ?: UserRole.CUSTOMER

    fun isLoggedIn(): Boolean = auth.currentUser != null && currentUserData != null

    fun logout() {
        auth.signOut()
        currentUserData = null
    }
}