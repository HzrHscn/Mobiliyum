package com.example.mobiliyum

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

object UserManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Firestore'dan çektiğimiz detaylı kullanıcı verisi
    private var currentUserData: User? = null

    // --- GİRİŞ ---
    fun login(email: String, pass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid != null) {
                    fetchUserProfile(uid, onSuccess, onFailure)
                } else {
                    onFailure("Kullanıcı kimliği alınamadı.")
                }
            }
            .addOnFailureListener {
                onFailure(it.localizedMessage ?: "Giriş başarısız.")
            }
    }

    // --- KAYIT ---
    fun register(email: String, pass: String, fullName: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()
                    firebaseUser.updateProfile(profileUpdates)

                    val newUser = User(
                        id = firebaseUser.uid,
                        email = email,
                        fullName = fullName,
                        role = UserRole.CUSTOMER, // Varsayılan rol
                        username = email.substringBefore("@"),
                        lastProfileUpdate = 0,
                        lastPasswordUpdate = 0
                    )
                    saveUserToFirestore(newUser, onSuccess, onFailure)
                }
            }
            .addOnFailureListener {
                onFailure(it.localizedMessage ?: "Kayıt başarısız.")
            }
    }

    // --- GÜNCELLEME İŞLEMLERİ ---
    fun updateUserName(newName: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return
        val dbUser = currentUserData ?: return

        val updates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()

        user.updateProfile(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                dbUser.fullName = newName
                dbUser.lastProfileUpdate = System.currentTimeMillis()

                db.collection("users").document(user.uid).set(dbUser)
                    .addOnSuccessListener { onComplete(true) }
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

        val credential = EmailAuthProvider.getCredential(user.email!!, oldPass)

        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
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

    // --- VERİ YÖNETİMİ ---
    fun fetchUserProfile(uid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentUserData = document.toObject(User::class.java)
                    // Ban kontrolü
                    if (currentUserData?.isBanned == true) {
                        logout()
                        onFailure("Hesabınız askıya alınmıştır.")
                    } else {
                        onSuccess()
                    }
                } else {
                    onFailure("Kullanıcı profili bulunamadı.")
                }
            }
            .addOnFailureListener {
                onFailure("Hata: ${it.message}")
            }
    }

    private fun saveUserToFirestore(user: User, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(user.id).set(user)
            .addOnSuccessListener {
                currentUserData = user
                onSuccess()
            }
            .addOnFailureListener {
                onFailure("Veritabanı hatası: ${it.message}")
            }
    }

    // --- YETKİ VE ERİŞİM (HATA ALINAN KISIM DÜZELTİLDİ) ---

    // 1. Ürün Düzenleme Yetkisi (Hata veren fonksiyon buydu)
    fun canEditProduct(product: Product): Boolean {
        val role = getUserRole()

        // Admin her şeyi düzenleyebilir
        if (role == UserRole.ADMIN) return true

        // Mağaza Yöneticisi veya Editör ise, sadece kendi mağazasını düzenler
        val userStoreId = currentUserData?.storeId
        if ((role == UserRole.MANAGER || role == UserRole.EDITOR) && userStoreId != null) {
            return userStoreId == product.storeId
        }

        return false
    }

    // 2. Admin Panelini Görme Yetkisi
    fun canViewAdminPanel(): Boolean {
        val role = getUserRole()
        return role != UserRole.CUSTOMER
    }

    fun getCurrentUser(): User? = currentUserData

    fun getUserRole(): UserRole = currentUserData?.role ?: UserRole.CUSTOMER

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun logout() {
        auth.signOut()
        currentUserData = null
    }

    fun checkSession(onResult: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserProfile(currentUser.uid,
                { onResult(true) },
                { onResult(false) }
            )
        } else {
            onResult(false)
        }
    }
}