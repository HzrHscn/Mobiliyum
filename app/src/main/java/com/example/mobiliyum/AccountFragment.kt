package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AccountFragment : Fragment() {

    // --- XML Bileşenleri ---

    // Konteynerler
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutRegister: LinearLayout
    private lateinit var layoutProfile: LinearLayout

    // Login Ekranı
    private lateinit var etLoginEmail: TextInputEditText
    private lateinit var etLoginPass: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvGoToRegister: TextView

    // Register Ekranı
    private lateinit var etRegName: TextInputEditText
    private lateinit var etRegEmail: TextInputEditText
    private lateinit var etRegPass: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvGoToLogin: TextView

    // Profil Ekranı
    private lateinit var tvName: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnAdminPanel: MaterialButton
    private lateinit var btnLogout: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account, container, false)
        initViews(view)
        setupListeners()
        updateUIState()
        return view
    }

    private fun initViews(view: View) {
        layoutLogin = view.findViewById(R.id.layoutLoginContainer)
        layoutRegister = view.findViewById(R.id.layoutRegisterContainer)
        layoutProfile = view.findViewById(R.id.layoutProfileContainer)

        etLoginEmail = view.findViewById(R.id.etLoginEmail)
        etLoginPass = view.findViewById(R.id.etLoginPassword)
        btnLogin = view.findViewById(R.id.btnLogin)
        tvGoToRegister = view.findViewById(R.id.tvGoToRegister)

        etRegName = view.findViewById(R.id.etRegName)
        etRegEmail = view.findViewById(R.id.etRegEmail)
        etRegPass = view.findViewById(R.id.etRegPassword)
        btnRegister = view.findViewById(R.id.btnRegister)
        tvGoToLogin = view.findViewById(R.id.tvGoToLogin)

        tvName = view.findViewById(R.id.tvProfileName)
        tvRole = view.findViewById(R.id.tvProfileRole)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        btnAdminPanel = view.findViewById(R.id.btnAdminPanel)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun setupListeners() {
        // --- EKRAN GEÇİŞLERİ ---
        tvGoToRegister.setOnClickListener {
            layoutLogin.visibility = View.GONE
            layoutRegister.visibility = View.VISIBLE
        }

        tvGoToLogin.setOnClickListener {
            layoutRegister.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
        }

        // --- GİRİŞ YAP ---
        btnLogin.setOnClickListener {
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, "Lütfen alanları doldurun", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // UserManager'a isteği gönder ve sonucu bekle (Callback)
            UserManager.login(email, pass,
                onSuccess = {
                    Toast.makeText(context, "Giriş Başarılı!", Toast.LENGTH_SHORT).show()
                    updateUIState()
                },
                onFailure = { errorMessage ->
                    Toast.makeText(context, "Hata: $errorMessage", Toast.LENGTH_LONG).show()
                }
            )
        }

        // --- KAYIT OL ---
        btnRegister.setOnClickListener {
            val name = etRegName.text.toString().trim()
            val email = etRegEmail.text.toString().trim()
            val pass = etRegPass.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass.length < 6) {
                Toast.makeText(context, "Şifre en az 6 karakter olmalı", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Kayıt isteği gönder
            UserManager.register(email, pass, name,
                onSuccess = {
                    Toast.makeText(context, "Kayıt Başarılı! Hoşgeldiniz.", Toast.LENGTH_SHORT).show()
                    updateUIState()
                },
                onFailure = { errorMessage ->
                    Toast.makeText(context, "Kayıt Hatası: $errorMessage", Toast.LENGTH_LONG).show()
                }
            )
        }

        // --- ÇIKIŞ YAP ---
        btnLogout.setOnClickListener {
            UserManager.logout()
            Toast.makeText(context, "Çıkış Yapıldı", Toast.LENGTH_SHORT).show()
            updateUIState()

            // Inputları temizle
            etLoginEmail.text?.clear()
            etLoginPass.text?.clear()
        }

        // --- YÖNETİM PANELİ ---
        btnAdminPanel.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ManagementFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun updateUIState() {
        if (UserManager.isLoggedIn()) {
            // Giriş Yapılmış
            layoutLogin.visibility = View.GONE
            layoutRegister.visibility = View.GONE
            layoutProfile.visibility = View.VISIBLE

            val user = UserManager.getCurrentUser()
            // Firebase'den isim ve maili al
            tvName.text = user?.displayName ?: "Kullanıcı"
            tvEmail.text = user?.email ?: ""

            val role = UserManager.getUserRole()
            tvRole.text = "Yetki: ${role.name}"

            if (UserManager.canViewAdminPanel()) {
                btnAdminPanel.visibility = View.VISIBLE
            } else {
                btnAdminPanel.visibility = View.GONE
            }

        } else {
            // Giriş Yapılmamış -> Varsayılan Login ekranı
            layoutLogin.visibility = View.VISIBLE
            layoutRegister.visibility = View.GONE
            layoutProfile.visibility = View.GONE
        }
    }
}