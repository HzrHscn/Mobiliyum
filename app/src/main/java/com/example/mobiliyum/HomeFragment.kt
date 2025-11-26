package com.example.mobiliyum

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HomeFragment : Fragment() {
    private lateinit var webView: WebView
    private lateinit var fabLogin: ExtendedFloatingActionButton

    // Duyuru Bileşenleri
    private lateinit var cardAnnounce: CardView
    private lateinit var tvAnnounceTitle: TextView
    private lateinit var tvAnnounceMsg: TextView
    private lateinit var btnCloseAnnounce: ImageView

    private val URL_HOME = "https://mobiliyum.com/"
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        webView = view.findViewById(R.id.webViewHome)
        fabLogin = view.findViewById(R.id.fabLogin)

        // Duyuru View'ları
        cardAnnounce = view.findViewById(R.id.cardAnnouncement)
        tvAnnounceTitle = view.findViewById(R.id.tvAnnounceTitle)
        tvAnnounceMsg = view.findViewById(R.id.tvAnnounceMessage)
        btnCloseAnnounce = view.findViewById(R.id.btnCloseAnnounce)

        setupWebView()
        checkForAnnouncements() // Duyuru kontrolü

        // Kapatma butonu
        btnCloseAnnounce.setOnClickListener {
            cardAnnounce.visibility = View.GONE
        }

        if (!UserManager.isLoggedIn()) {
            fabLogin.visibility = View.VISIBLE
            fabLogin.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, WelcomeFragment())
                    .commit()
            }
        } else {
            fabLogin.visibility = View.GONE
        }

        if (webView.url == null) {
            webView.loadUrl(URL_HOME)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        return view
    }

    private fun checkForAnnouncements() {
        // En son eklenen duyuruyu çek
        db.collection("announcements")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    val title = doc.getString("title")
                    val message = doc.getString("message")

                    if (!title.isNullOrEmpty() && !message.isNullOrEmpty()) {
                        tvAnnounceTitle.text = title
                        tvAnnounceMsg.text = message
                        cardAnnounce.visibility = View.VISIBLE
                    }
                }
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webView.webViewClient = object : WebViewClient() {}
    }
}