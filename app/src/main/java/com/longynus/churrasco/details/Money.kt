package com.longynus.churrasco.details

import java.text.NumberFormat
import java.util.Locale

object Money {
    private val format = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun format(cents: Long): String = format.format(cents / 100.0).replace(' ', ' ')

    /** "12,50", "12.5", "R$ 1.200,00" -> centavos; null se não for um valor válido. */
    fun parse(input: String): Long? {
        val clean = input.replace("R$", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null
        val normalized = when {
            clean.contains(',') -> clean.replace(".", "").replace(',', '.')
            clean.count { it == '.' } > 1 -> clean.replace(".", "")
            else -> clean
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.scale() > 2 && value.stripTrailingZeros().scale() > 2) return null
        return value.movePointRight(2).toLong().takeIf { it in 1..10_000_000 }
    }
}
