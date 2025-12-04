package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.FragmentAccountBinding // ViewBinding

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        updateUIState()
    }

    private fun setupListeners() {
        // --- Giriş / Kayıt Geçişleri ---
        binding.tvGoToRegister.setOnClickListener {
            binding.layoutLoginContainer.visibility = View.GONE
            binding.layoutRegisterContainer.visibility = View.VISIBLE
        }

        binding.tvGoToLogin.setOnClickListener {
            binding.layoutRegisterContainer.visibility = View.GONE
            binding.layoutLoginContainer.visibility = View.VISIBLE
        }

        // --- Giriş İşlemi ---
        binding.btnLogin.setOnClickListener {
            val email = binding.etLoginEmail.text.toString().trim()
            val pass = binding.etLoginPassword.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) return@setOnClickListener

            UserManager.login(email, pass,
                onSuccess = {
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

        // --- Kayıt İşlemi ---
        binding.btnRegister.setOnClickListener {
            val name = binding.etRegName.text.toString().trim()
            val email = binding.etRegEmail.text.toString().trim()
            val pass = binding.etRegPassword.text.toString().trim()
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) return@setOnClickListener

            UserManager.register(email, pass, name,
                onSuccess = {
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

        // --- Çıkış ---
        binding.btnLogout.setOnClickListener {
            UserManager.logout()
            (activity as? MainActivity)?.hideBottomNav()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, WelcomeFragment())
                .commit()
        }

        // --- Profil Butonları ---
        binding.btnAdminPanel.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ManagementFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnMyFavorites.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FavoritesFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnMyReviews.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MyReviewsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnWebsite.setOnClickListener {
            // Web Fragment'ı (Eski HomeFragment) aç
            (activity as? MainActivity)?.loadFragment((activity as MainActivity).webFragment)
        }

        binding.btnNotifications.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }

        // --- İsim Güncelleme ---
        binding.btnChangeName.setOnClickListener {
            val user = UserManager.getCurrentUser() ?: return@setOnClickListener
            val thirtyDaysMs = 2592000000L

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

        // --- Şifre Güncelleme ---
        binding.btnChangePassword.setOnClickListener {
            val user = UserManager.getCurrentUser() ?: return@setOnClickListener
            val oneDayMs = 86400000L

            if (System.currentTimeMillis() - user.lastPasswordUpdate < oneDayMs) {
                Toast.makeText(context, "Şifrenizi günde sadece 1 kez değiştirebilirsiniz.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showChangePasswordDialog()
        }
    }

    private fun showChangePasswordDialog() {
        // Not: Dialog layout'ları için ViewBinding yapmak zorunlu değil ama
        // fragment içindeki view'ler için binding kullanıyoruz.
        // Dialog pencereleri basit olduğu için findViewById ile bırakabiliriz
        // ya da dialog layoutları için ayrı binding oluşturabiliriz.
        // Hızlı çözüm için dialog içeriğini standart bırakıyorum.

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

    private fun showConfirmationDialog(title: String, message: String, warning: String, onConfirm: () -> Unit) {
        val htmlMessage = """
            $message<br><br>
            <small><font color='#D32F2F'>$warning</font></small>
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Evet, Onaylıyorum") { _, _ -> onConfirm() }
            .setNegativeButton("Hayır", null)
            .show()
    }

    private fun updateUIState() {
        if (UserManager.isLoggedIn()) {
            binding.layoutLoginContainer.visibility = View.GONE
            binding.layoutRegisterContainer.visibility = View.GONE
            binding.layoutProfileContainer.visibility = View.VISIBLE

            val user = UserManager.getCurrentUser()
            binding.tvProfileName.text = user?.fullName ?: "Kullanıcı"
            binding.tvProfileEmail.text = user?.email ?: ""

            val role = user?.role ?: UserRole.CUSTOMER
            binding.tvProfileRole.text = "Yetki: ${role.name}"

            if (role != UserRole.CUSTOMER) {
                binding.btnAdminPanel.visibility = View.VISIBLE
                if (role == UserRole.ADMIN || role == UserRole.SRV) {
                    binding.btnAdminPanel.text = "Admin Yetkileri"
                } else {
                    binding.btnAdminPanel.text = "Mağaza Yönetim Paneli"
                }
            } else {
                binding.btnAdminPanel.visibility = View.GONE
            }

        } else {
            binding.layoutLoginContainer.visibility = View.VISIBLE
            binding.layoutRegisterContainer.visibility = View.GONE
            binding.layoutProfileContainer.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}