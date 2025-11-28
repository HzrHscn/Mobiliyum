package com.example.mobiliyum

import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PriceUtils {

    fun formatPriceStyled(rawPrice: Any): SpannableString {
        var priceValue = 0.0

        try {
            if (rawPrice is Double) {
                priceValue = rawPrice
            } else if (rawPrice is String) {
                var clean = rawPrice.toString().replace("[^\\d.,]".toRegex(), "").trim()

                if (clean.isNotEmpty()) {
                    if (clean.contains(",")) {
                        clean = clean.replace(".", "") // Binlikleri sil
                        clean = clean.replace(",", ".") // Virgülü nokta yap
                    } else {
                        clean = clean.replace(".", "")
                    }
                    priceValue = clean.toDouble()
                }
            }
        } catch (e: Exception) {
            priceValue = 0.0
        }
        val symbols = DecimalFormatSymbols(Locale.getDefault())
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        val decimalFormat = DecimalFormat("#,##0.00", symbols) // Daima 2 hane kuruş

        val formattedText = "${decimalFormat.format(priceValue)} ₺"
        val spannable = SpannableString(formattedText)

        // Virgülden sonraki kısmı bul ve küçült
        val commaIndex = formattedText.indexOf(',')
        if (commaIndex != -1) {
            // Virgülden başlayıp " ₺" öncesine kadar olan kısmı küçült
            val endOfNumber = formattedText.indexOf(' ')
            if (endOfNumber > commaIndex) {
                spannable.setSpan(
                    RelativeSizeSpan(0.7f), // %70 boyut
                    commaIndex + 1, // Virgülden sonraki ilk rakam
                    endOfNumber,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

    fun parsePrice(priceStr: String): Double {
        try {
            // "19.210,00 ₺" -> "19210.00"
            var clean = priceStr.replace("[^\\d.,]".toRegex(), "").trim()
            if (clean.contains(",")) {
                clean = clean.replace(".", "").replace(",", ".")
            } else {
                clean = clean.replace(".", "")
            }
            return clean.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            return 0.0
        }
    }
}