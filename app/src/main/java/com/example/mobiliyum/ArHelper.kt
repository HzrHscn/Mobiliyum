package com.example.mobiliyum

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object ArHelper {

    private const val TAG = "ArHelper"

    /**
     * ARCore kurulu mu kontrol et
     */
    fun isArCoreInstalled(context: Context): Boolean {
        try {
            val packageManager = context.packageManager
            val arCorePackage = "com.google.ar.core"

            // ARCore uygulaması kurulu mu?
            packageManager.getPackageInfo(arCorePackage, 0)
            Log.d(TAG, "✅ ARCore kurulu")
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "❌ ARCore kurulu değil")
            return false
        }
    }

    /**
     * Kamera izni var mı kontrol et
     */
    fun hasCameraPermission(context: Context): Boolean {
        val permission = android.Manifest.permission.CAMERA
        val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            Log.d(TAG, "✅ Kamera izni var")
        } else {
            Log.e(TAG, "❌ Kamera izni yok")
        }

        return granted
    }
}