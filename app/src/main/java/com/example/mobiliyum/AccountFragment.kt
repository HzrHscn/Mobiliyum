package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AccountFragment : Fragment() {

    // --- XML Bileşenleri ---
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutRegister: LinearLayout
    private lateinit var layoutProfile: LinearLayout

    // Login
    private lateinit var etLoginEmail: TextInputEditText
    private lateinit var etLoginPass: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvGoToRegister: TextView

    // Register
    private lateinit var etRegName: TextInputEditText
    private lateinit var etRegEmail: TextInputEditText
    private lateinit var etRegPass: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvGoToLogin: TextView

    // Profil
    private lateinit var tvName: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnAdminPanel: MaterialButton
    private lateinit var btnMyFavorites: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnChangeName: MaterialButton
    private lateinit var btnChangePassword: MaterialButton

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
        btnMyFavorites = view.findViewById(R.id.btnMyFavorites)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnChangeName = view.findViewById(R.id.btnChangeName)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
    }

    private fun setupListeners() {
        tvGoToRegister.setOnClickListener {
            layoutLogin.visibility = View.GONE
            layoutRegister.visibility = View.VISIBLE
        }

        tvGoToLogin.setOnClickListener {
            layoutRegister.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
        }

        btnLogin.setOnClickListener {
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPass.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) return@setOnClickListener

            UserManager.login(email, pass,
                onSuccess = {
                    // --- DÜZELTME: Giriş başarılı olunca favorileri yükle ---
                    FavoritesManager.loadUserFavorites {
                        Toast.makeText(context, "Giriş Başarılı!", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.showBottomNav()
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, HomeFragment())
                            .commit()
                    }
                },
                onFailure = { errorMessage ->
                    Toast.makeText(context, "Hata: $errorMessage", Toast.LENGTH_LONG).show()
                }
            )
        }

        btnRegister.setOnClickListener {
            val name = etRegName.text.toString().trim()
            val email = etRegEmail.text.toString().trim()
            val pass = etRegPass.text.toString().trim()
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) return@setOnClickListener

            UserManager.register(email, pass, name,
                onSuccess = {
                    // --- DÜZELTME: Kayıt başarılı olunca favorileri (boş da olsa) yükle ---
                    FavoritesManager.loadUserFavorites {
                        Toast.makeText(context, "Kayıt Başarılı! Hoşgeldiniz.", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.showBottomNav()
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, HomeFragment())
                            .commit()
                    }
                },
                onFailure = { errorMessage ->
                    Toast.makeText(context, "Kayıt Hatası: $errorMessage", Toast.LENGTH_LONG).show()
                }
            )
        }

        btnLogout.setOnClickListener {
            UserManager.logout()
            (activity as? MainActivity)?.hideBottomNav()
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, WelcomeFragment()).commit()
        }

        btnAdminPanel.setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, ManagementFragment()).addToBackStack(null).commit()
        }

        btnMyFavorites.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FavoritesFragment())
                .addToBackStack(null)
                .commit()
        }

        // --- İSİM GÜNCELLEME ---
        btnChangeName.setOnClickListener {
            val user = UserManager.getCurrentUser() ?: return@setOnClickListener
            val thirtyDaysMs = 2592000000L

            // 1. Süre Kontrolü (Baştan uyaralım)
            if (System.currentTimeMillis() - user.lastProfileUpdate < thirtyDaysMs) {
                val remainingDays = 30 - ((System.currentTimeMillis() - user.lastProfileUpdate) / 86400000L)
                Toast.makeText(context, "İsminizi değiştirmek için $remainingDays gün beklemelisiniz.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val input = EditText(context)
            input.hint = "Yeni İsim Soyisim"
            input.setPadding(50, 50, 50, 50)

            AlertDialog.Builder(context)
                .setTitle("İsim Güncelle")
                .setView(input)
                .setPositiveButton("Devam Et") { _, _ ->
                    val newName = input.text.toString()
                    if (newName.length > 3) {
                        // 2. ONAY DIALOGU (Kural hatırlatması)
                        showConfirmationDialog(
                            title = "İsim Değişikliği Onayı",
                            message = "İsminizi <b>$newName</b> olarak değiştirmek üzeresiniz.",
                            warning = "Güvenlik gereği isminizi <b>30 günde sadece 1 kez</b> değiştirebilirsiniz. Onaylıyor musunuz?",
                            onConfirm = {
                                UserManager.updateUserName(newName) { success ->
                                    if(success) {
                                        Toast.makeText(context, "İsim güncellendi!", Toast.LENGTH_SHORT).show()
                                        updateUIState()
                                    } else {
                                        Toast.makeText(context, "Hata oluştu.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        // --- ŞİFRE GÜNCELLEME ---
        btnChangePassword.setOnClickListener {
            val user = UserManager.getCurrentUser() ?: return@setOnClickListener
            val oneDayMs = 86400000L

            // 1. Süre Kontrolü
            if (System.currentTimeMillis() - user.lastPasswordUpdate < oneDayMs) {
                Toast.makeText(context, "Şifrenizi günde sadece 1 kez değiştirebilirsiniz.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            showChangePasswordDialog()
        }
    }

    // Şifre Dialogu ve Onay Mekanizması
    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null)
        val etOld = dialogView.findViewById<EditText>(R.id.etOldPassword)
        val etNew = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etNewConfirm = dialogView.findViewById<EditText>(R.id.etNewPasswordConfirm)

        AlertDialog.Builder(context)
            .setTitle("Şifre Değiştir")
            .setView(dialogView)
            .setPositiveButton("Güncelle") { _, _ ->
                val oldPass = etOld.text.toString()
                val newPass = etNew.text.toString()
                val confirmPass = etNewConfirm.text.toString()

                if (oldPass.isEmpty() || newPass.isEmpty()) return@setPositiveButton
                if (newPass != confirmPass) {
                    Toast.makeText(context, "Yeni şifreler uyuşmuyor.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass.length < 6) {
                    Toast.makeText(context, "Şifre en az 6 karakter olmalı.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // ONAY DIALOGU ÇAĞRISI
                showConfirmationDialog(
                    title = "Şifre Değişikliği Onayı",
                    message = "Şifrenizi güncellemek üzeresiniz. Bu işlemden sonra tüm cihazlardan çıkış yapılabilir.",
                    warning = "Güvenlik gereği şifrenizi <b>günde sadece 1 kez</b> değiştirebilirsiniz. Emin misiniz?",
                    onConfirm = {
                        UserManager.updateUserPassword(oldPass, newPass) { success, error ->
                            if (success) {
                                Toast.makeText(context, "Şifreniz başarıyla güncellendi.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Hata: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // --- GENEL ONAY PENCERESİ (YENİ) ---
    private fun showConfirmationDialog(title: String, message: String, warning: String, onConfirm: () -> Unit) {
        // HTML formatında metin oluştur (Uyarı kısmını küçültmek için)
        val htmlMessage = """
            $message<br><br>
            <small><font color='#D32F2F'>$warning</font></small>
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Evet, Onaylıyorum") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Hayır", null)
            .show()
    }

    private fun updateUIState() {
        if (UserManager.isLoggedIn()) {
            layoutLogin.visibility = View.GONE
            layoutRegister.visibility = View.GONE
            layoutProfile.visibility = View.VISIBLE

            val user = UserManager.getCurrentUser()
            tvName.text = user?.fullName ?: "Kullanıcı"
            tvEmail.text = user?.email ?: ""

            val role = user?.role ?: UserRole.CUSTOMER
            tvRole.text = "Yetki: ${role.name}"

            // Yetkili Panel Kontrolü ve İsimlendirme
            if (role != UserRole.CUSTOMER) {
                btnAdminPanel.visibility = View.VISIBLE

                // Admin veya SRV ise metni değiştir
                if (role == UserRole.ADMIN || role == UserRole.SRV) {
                    btnAdminPanel.text = "Admin Yetkileri"
                } else {
                    btnAdminPanel.text = "Mağaza Yönetim Paneli"
                }
            } else {
                btnAdminPanel.visibility = View.GONE
            }

        } else {
            layoutLogin.visibility = View.VISIBLE
            layoutRegister.visibility = View.GONE
            layoutProfile.visibility = View.GONE
        }
    }
}