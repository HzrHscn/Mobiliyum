package com.example.mobiliyum

object StoreSortHelper {

    // Konum metninden "Kat Sırası Puanı" üretir.
    // Düşük puan en üstte (Giriş) çıkar.
    fun calculateLocationWeight(location: String?): Int {
        if (location.isNullOrEmpty()) return 999999 // Bilgi yoksa en alta at

        val locLower = location.lowercase()
        var weight = 0

        // 1. KAT PUANI BELİRLEME
        if (locLower.contains("giriş") || locLower.contains("zemin") || locLower.contains("bodrum")) {
            weight = 0 // En öncelikli (0 - 99 arası)
        } else if (locLower.contains("1. kat") || locLower.contains("1.kat")) {
            weight = 1000 // 1. Katlar (1000 - 1999 arası)
        } else if (locLower.contains("2. kat") || locLower.contains("2.kat")) {
            weight = 2000 // 2. Katlar (2000 - 2999 arası)
        } else if (locLower.contains("3. kat") || locLower.contains("3.kat")) {
            weight = 3000
        } else {
            weight = 5000 // Kat bilgisi anlaşılmayanlar en sona
        }

        // 2. KAPI NUMARASI PUANI EKLEME (Aynı kattakileri sıraya dizmek için)
        // Metnin içindeki sayıları bulur (Örn: "No: 12" -> 12 puan ekle)
        try {
            // Regex ile metindeki tüm rakamları bul, "1. kat"taki 1 ile karışmaması için
            // Genelde "no:" veya "no" dan sonra gelen sayıyı arayabiliriz veya basitçe
            // string içindeki son sayıyı alabiliriz.
            // Burada basitlik adına metindeki sayıları extract edip mantıklı bir ekleme yapıyoruz.

            val numbers = Regex("[0-9]+").findAll(location)
                .map { it.value.toInt() }
                .toList()

            // Eğer metinde sayı varsa, son bulunan sayı genelde kapı numarasıdır.
            // (Çünkü kat numarası baştadır)
            if (numbers.isNotEmpty()) {
                // Son sayı kapı numarasıysa onu ekle.
                // Örn: "1. Kat No: 25" -> weight(1000) + 25 = 1025 puan
                weight += numbers.last()
            }
        } catch (e: Exception) {
            // Sayı parse edilemezse puan ekleme, olduğu yerde kalsın
        }

        return weight
    }
}