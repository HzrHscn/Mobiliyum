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
                        role = UserRole.CUSTOMER,
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

    // --- GÜNCELLEME FONKSİYONLARI (HATA ALINAN YERLER) ---

    // İsim Güncelleme
    fun updateUserName(newName: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return
        val dbUser = currentUserData ?: return

        val updates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

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

    // Şifre Güncelleme (Re-Auth gerektirir)
    fun updateUserPassword(oldPass: String, newPass: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        val dbUser = currentUserData

        if (user == null || user.email == null || dbUser == null) {
            onComplete(false, "Kullanıcı bulunamadı")
            return
        }

        // 1. Önce eski şifre ile yeniden kimlik doğrula (Güvenlik için şart)
        val credential = EmailAuthProvider.getCredential(user.email!!, oldPass)

        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                // 2. Şifreyi güncelle
                user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        // 3. Firestore'a zaman damgası at
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

    // --- DİĞERLERİ ---
    fun fetchUserProfile(uid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentUserData = document.toObject(User::class.java)
                    onSuccess()
                } else {
                    onSuccess()
                }
            }
            .addOnFailureListener {
                onFailure("Profil yüklenirken hata: ${it.message}")
            }
    }

    private fun saveUserToFirestore(user: User, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(user.id).set(user)
            .addOnSuccessListener {
                currentUserData = user
                onSuccess()
            }
            .addOnFailureListener {
                onFailure("Veritabanı kaydı başarısız: ${it.message}")
            }
    }

    fun getCurrentUser(): User? = currentUserData
    fun isLoggedIn(): Boolean = auth.currentUser != null
    fun logout() {
        auth.signOut()
        currentUserData = null
    }
    fun getUserRole(): UserRole = currentUserData?.role ?: UserRole.CUSTOMER
    fun checkSession(onResult: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserProfile(currentUser.uid, { onResult(true) }, { onResult(false) })
        } else {
            onResult(false)
        }
    }
}