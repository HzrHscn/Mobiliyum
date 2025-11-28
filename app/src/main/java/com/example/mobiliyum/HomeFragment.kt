package com.example.mobiliyum

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class HomeFragment : Fragment() {
    private lateinit var webView: WebView
    private lateinit var fabLogin: ExtendedFloatingActionButton
    private val URL_HOME = "https://mobiliyum.com/"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        webView = view.findViewById(R.id.webViewHome)
        fabLogin = view.findViewById(R.id.fabLogin)

        setupWebView()

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