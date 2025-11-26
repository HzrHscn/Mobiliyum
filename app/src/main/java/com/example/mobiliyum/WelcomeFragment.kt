package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class WelcomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_welcome, container, false)

        val btnLoginReg = view.findViewById<MaterialButton>(R.id.btnLoginRegister)
        val btnGuest = view.findViewById<MaterialButton>(R.id.btnGuest)

        btnLoginReg.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AccountFragment())
                .addToBackStack(null)
                .commit()
        }

        btnGuest.setOnClickListener {
            // DÜZELTME: Misafir modunda menüyü GİZLE
            (activity as? MainActivity)?.hideBottomNav()

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }

        return view
    }
}