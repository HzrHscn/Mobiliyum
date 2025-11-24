package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StoresFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var storeAdapter: StoreAdapter
    private lateinit var storeList: ArrayList<Store>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stores, container, false)

        recyclerView = view.findViewById(R.id.rvStores)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)

        // Demo Verileri Oluşturuyoruz (Sitede veri olmadığı için elle giriyoruz)
        storeList = ArrayList()

        // Gerçek Mobiliyum mağazalarından rastgele örnekler ve rastgele logo URL'leri
        storeList.add(Store(1, "Akyol Life", "Mobilya & Yatak", "https://framerusercontent.com/images/mpwlimJbwQQvgSJsWNvwSP0GRs.png?width=368&height=126", "A Etap 2. Kat No: 23"))
        storeList.add(Store(2, "Çilek Odası", "Bebek & Genç Odası", "https://framerusercontent.com/images/xwYl5Ts0pDUGKM70kNdkhrMzEQI.png?width=300&height=300", "A Etap Giriş Kat No: 12"))
        storeList.add(Store(3, "Nill's Mobilya", "Modern Mobilya", "https://framerusercontent.com/images/sqb4SQuImDK9gHeogTU6kYwT74.png?scale-down-to=512&width=678&height=300", "A Etap Giriş Kat No: 09"))
        storeList.add(Store(4, "Saloni Mobilya", "Luxury Koltuk Takımları", "https://framerusercontent.com/images/mSKz3cK45L0YPhlz4Pr7fwgDw20.png?width=300&height=300", "A Etap Giriş Kat No: 39"))
        storeList.add(Store(5, "İnegöl Sofa", "Ekonomik Takımlar", "https://framerusercontent.com/images/i7r6UmpjFofwJuouqDKyUm4nVI.png?width=243&height=76", "B Etap 2. Kat No: 25"))
        storeList.add(Store(6, "Büro Life", "Büro ve Ofis Mobilyaları", "https://framerusercontent.com/images/iVCfRa5PUvCZckmfUza1B3RiTkU.jpeg?width=324&height=155", "A Etap 2. Kat No: 50"))
        // Daha fazla ekleyebilirsin... Not: Resim URL'leri örnek, gerçek URL koyarsan resimler görünür.

        storeAdapter = StoreAdapter(storeList) { selectedStore ->

            // 1. Detay Fragmentını Oluştur
            val detailFragment = StoreDetailFragment()

            // 2. Verileri Paketle (Bundle)
            val bundle = Bundle()
            bundle.putString("name", selectedStore.name)
            bundle.putString("image", selectedStore.imageUrl)
            bundle.putString("location", selectedStore.location)
            detailFragment.arguments = bundle

            // 3. Sayfa Değişimi (Transaction)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment) // ActivityMain'deki container ID'si
                .addToBackStack(null) // Geri tuşuna basınca listeye dönmesi için şart!
                .commit()
        }

        recyclerView.adapter = storeAdapter

        return view
    }
}