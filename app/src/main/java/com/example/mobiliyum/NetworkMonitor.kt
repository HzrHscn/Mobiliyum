package com.example.mobiliyum

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

object NetworkMonitor {

    private var isOnline = true
    private val listeners = ArrayList<(Boolean) -> Unit>()

    fun initialize(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("NetworkMonitor", "🌐 İnternet bağlantısı VAR")
                updateStatus(true)
            }

            override fun onLost(network: Network) {
                Log.d("NetworkMonitor", "📡 İnternet bağlantısı YOK")
                updateStatus(false)
            }
        })
    }

    private fun updateStatus(online: Boolean) {
        if (isOnline != online) {
            isOnline = online
            listeners.forEach { it(online) }
        }
    }

    fun isOnline() = isOnline

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }
}