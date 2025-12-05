package com.example.mobiliyum

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

object UserManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private var currentUserData: User? = null

    // --- GİRİŞ (SPAM KORUMALI) ---
    fun login(email: String, pass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val user = authResult.user

                if (user != null) {
                    // Önce profili çekelim ki "son mail zamanını" görebilelim
                    fetchUserProfile(user.uid,
                        onSuccess = {
                            // Profil yüklendi, şimdi kontrol edelim
                            if (user.isEmailVerified) {
                                onSuccess() // Giriş başarılı
                            } else {
                                // Doğrulanmamış -> Spam kontrolü yaparak mail at
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

    // YARDIMCI FONKSİYON: Spam Kontrolü ve Mail Gönderimi
    private fun checkAndResendVerification(user: FirebaseUser, onFailure: (String) -> Unit) {
        val dbUser = currentUserData ?: return
        val now = System.currentTimeMillis()
        val oneHourMs = 3600 * 1000L // 1 Saat (Milisaniye)

        val lastSent = dbUser.lastVerificationMailSent
        val timeDiff = now - lastSent

        if (timeDiff > oneHourMs) {
            // 1 saat geçmiş, yeni mail gönderebiliriz
            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    auth.signOut() // Oturumu kapat (Güvenlik)

                    if (task.isSuccessful) {
                        // Zaman damgasını güncelle
                        db.collection("users").document(user.uid)
                            .update("lastVerificationMailSent", now)

                        onFailure("Hesabınız doğrulanmamış. Yeni bir doğrulama maili gönderildi! Lütfen spam kutunuzu kontrol edin.")
                    } else {
                        onFailure("Mail gönderilemedi. Lütfen daha sonra tekrar deneyin.")
                    }
                }
        } else {
            // 1 saat dolmamış, kullanıcıyı uyar
            auth.signOut()
            val remainingMinutes = (oneHourMs - timeDiff) / 60000
            onFailure("Hesabınız doğrulanmamış. Çok sık işlem yaptınız. Yeni mail için $remainingMinutes dakika beklemelisiniz.")
        }
    }

    // --- KAYIT (Zaman Damgası Ekli) ---
    fun register(email: String, pass: String, fullName: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()
                    firebaseUser.updateProfile(profileUpdates)

                    // Maili hemen gönderelim
                    firebaseUser.sendEmailVerification()
                        .addOnFailureListener {
                            android.util.Log.e("Auth", "Mail hatası: ${it.message}")
                        }

                    // Kullanıcıyı kaydederken ŞU ANKİ ZAMANI da ekliyoruz
                    val now = System.currentTimeMillis()

                    val newUser = User(
                        id = firebaseUser.uid,
                        email = email,
                        fullName = fullName,
                        role = UserRole.CUSTOMER,
                        username = email.substringBefore("@"),
                        lastProfileUpdate = 0,
                        lastPasswordUpdate = 0,
                        // İlk mail şimdi atıldığı için zamanı kaydediyoruz
                        lastVerificationMailSent = now
                    )

                    saveUserToFirestore(newUser,
                        onSuccess = {
                            auth.signOut()
                            onSuccess()
                        },
                        onFailure = onFailure
                    )
                }
            }
            .addOnFailureListener {
                onFailure(it.localizedMessage ?: "Kayıt başarısız.")
            }
    }

    // --- DİĞER FONKSİYONLAR (AYNI KALACAK) ---

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
            } else { onComplete(false) }
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
                    } else { onComplete(false, updateTask.exception?.localizedMessage) }
                }
            } else { onComplete(false, "Mevcut şifre hatalı.") }
        }
    }

    fun fetchUserProfile(uid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentUserData = document.toObject(User::class.java)
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
            .addOnFailureListener { onFailure("Hata: ${it.message}") }
    }

    private fun saveUserToFirestore(user: User, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(user.id).set(user)
            .addOnSuccessListener {
                currentUserData = user
                onSuccess()
            }
            .addOnFailureListener { onFailure("Veritabanı hatası: ${it.message}") }
    }

    fun canEditProduct(product: Product): Boolean {
        val role = getUserRole()
        if (role == UserRole.ADMIN) return true
        val userStoreId = currentUserData?.storeId
        if ((role == UserRole.MANAGER || role == UserRole.EDITOR) && userStoreId != null) {
            return userStoreId == product.storeId
        }
        return false
    }

    fun canViewAdminPanel(): Boolean {
        return getUserRole() != UserRole.CUSTOMER
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
            fetchUserProfile(currentUser.uid, { onResult(true) }, { onResult(false) })
        } else {
            onResult(false)
        }
    }
}