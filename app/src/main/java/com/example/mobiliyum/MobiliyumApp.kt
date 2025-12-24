package com.example.mobiliyum

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MobiliyumApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.e("APP_TEST", "MobiliyumApp ÇALIŞTI")

        FirebaseApp.initializeApp(this)

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(50L * 1024 * 1024)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}