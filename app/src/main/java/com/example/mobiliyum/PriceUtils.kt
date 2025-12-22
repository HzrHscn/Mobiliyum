package com.example.mobiliyum

import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PriceUtils {

    // Ekranda güzel göstermek için (1250.5 -> "1.250,50 ₺")
    fun formatPriceStyled(rawPrice: Any): SpannableString {
        var priceValue = 0.0
        try {
            priceValue = when (rawPrice) {
                is Double -> rawPrice
                is Int -> rawPrice.toDouble()
                is String -> parsePrice(rawPrice)
                else -> 0.0
            }
        } catch (e: Exception) { priceValue = 0.0 }

        val symbols = DecimalFormatSymbols(Locale.getDefault())
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        val decimalFormat = DecimalFormat("#,##0.00", symbols)

        val formattedText = "${decimalFormat.format(priceValue)} ₺"
        val spannable = SpannableString(formattedText)

        val commaIndex = formattedText.indexOf(',')
        if (commaIndex != -1) {
            val endOfNumber = formattedText.indexOf(" ₺")
            val endIndex = if (endOfNumber != -1) endOfNumber else formattedText.length
            if (endIndex > commaIndex) {
                spannable.setSpan(RelativeSizeSpan(0.7f), commaIndex + 1, endIndex, 0)
            }
        }
        return spannable
    }

    // Fiyat kıyaslaması için String'i Double'a çevirir ("1.250,00 ₺" -> 1250.0)
    fun parsePrice(priceStr: String?): Double {
        if (priceStr.isNullOrEmpty()) return 0.0
        return try {
            // Sadece rakam, nokta ve virgülü bırak
            var clean = priceStr.replace("[^\\d.,]".toRegex(), "").trim()

            // TR formatı (1.000,50) ise
            if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                clean = clean.replace(".", "").replace(",", ".")
            } else {
                // US formatı (1,000.50) ise
                clean = clean.replace(",", "")
            }
            clean.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }
}