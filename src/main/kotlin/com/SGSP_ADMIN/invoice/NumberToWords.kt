package com.SGSP_ADMIN.invoice

object NumberToWords {
    private val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun twoDigit(n: Int): String = when {
        n < 20 -> units[n]
        else -> {
            val t = n / 10
            val u = n % 10
            tens[t] + if (u > 0) " ${units[u]}" else ""
        }
    }

    private fun threeDigit(n: Int): String = when {
        n < 100 -> twoDigit(n)
        else -> {
            val h = n / 100
            val rem = n % 100
            units[h] + " Hundred" + if (rem > 0) " and ${twoDigit(rem)}" else ""
        }
    }

    fun convert(amount: Double): String {
        val rupees = amount.toLong()
        val paise = ((amount - rupees) * 100).toInt()
        if (rupees == 0L) return "Zero Rupees" + if (paise > 0) " and $paise Paise" else ""
        var n = rupees
        val parts = mutableListOf<String>()
        val crore = (n / 10000000L).toInt(); if (crore > 0) { parts.add("${threeDigit(crore)} Crore"); n %= 10000000L }
        val lakh = (n / 100000L).toInt(); if (lakh > 0) { parts.add("${threeDigit(lakh)} Lakh"); n %= 100000L }
        val thousand = (n / 1000L).toInt(); if (thousand > 0) { parts.add("${threeDigit(thousand)} Thousand"); n %= 1000L }
        val rem = n.toInt(); if (rem > 0) parts.add(threeDigit(rem))
        val rupeeWords = parts.joinToString(" ")
        return rupeeWords + " Rupees" + if (paise > 0) " and $paise Paise" else ""
    }
}
