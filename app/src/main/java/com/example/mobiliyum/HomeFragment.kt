package com.example.mobiliyum

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.FragmentHomeBinding // ViewBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val URL_HOME = "https://mobiliyum.com/"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWebView()

        if (!UserManager.isLoggedIn()) {
            binding.fabLogin.visibility = View.VISIBLE
            binding.fabLogin.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, WelcomeFragment())
                    .commit()
            }
        } else {
            binding.fabLogin.visibility = View.GONE
        }

        if (binding.webViewHome.url == null) {
            binding.webViewHome.loadUrl(URL_HOME)
        }

        // Geri tuşu kontrolü (WebView içinde geri gitme)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webViewHome.canGoBack()) {
                    binding.webViewHome.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = binding.webViewHome.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        binding.webViewHome.webViewClient = object : WebViewClient() {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}