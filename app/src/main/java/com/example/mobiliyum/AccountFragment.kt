package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class AccountFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account, container, false)

        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPass = view.findViewById<EditText>(R.id.etPassword)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUser.text.toString()
            val password = etPass.text.toString()

            // SİMÜLASYON: Gerçek backend olmadığı için yetkiyi burada taklit ediyoruz.
            // Bunu daha sonra veritabanına bağlayacağız.

            if (username == "admin" && password == "1234") {
                Toast.makeText(context, "Süper Admin Girişi Başarılı!", Toast.LENGTH_LONG).show()
                // Buradan Admin Paneline yönlendirme yapacağız
            } else if (username == "supervisor" && password == "1234") {
                Toast.makeText(context, "Mobiliyum Sahibi Girişi Başarılı", Toast.LENGTH_SHORT).show()
            } else if (username == "magaza" && password == "1234") {
                Toast.makeText(context, "Mağaza Yetkilisi Girişi Başarılı", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Hatalı Kullanıcı Adı veya Şifre", Toast.LENGTH_LONG).show()
            }
        }

        return view
    }
}