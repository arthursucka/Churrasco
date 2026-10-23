package com.longynus.churrasco.pix

import java.text.Normalizer
import java.util.Locale

/**
 * Código Pix "copia e cola" (BR Code estático, padrão EMV do Banco Central).
 * Qualquer app de banco lê o texto ou o QR gerado a partir dele.
 */
object PixPayload {

    fun build(key: String, receiverName: String, amountCents: Long? = null, city: String = "SAO PAULO", txid: String = "***"): String {
        val account = field("00", "br.gov.bcb.pix") + field("01", key.trim())
        val body = buildString {
            append(field("00", "01"))
            append(field("26", account))
            append(field("52", "0000"))
            append(field("53", "986"))
            if (amountCents != null && amountCents > 0) append(field("54", formatAmount(amountCents)))
            append(field("58", "BR"))
            append(field("59", clean(receiverName, 25).ifBlank { "CHURRASCO" }))
            append(field("60", clean(city, 15).ifBlank { "SAO PAULO" }))
            append(field("62", field("05", txid.take(25))))
            append("6304")
        }
        return body + crc16(body)
    }

    private fun field(id: String, value: String): String = id + value.length.toString().padStart(2, '0') + value

    private fun formatAmount(cents: Long): String = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)

    /** Sem acentos e só caracteres aceitos pelo padrão, para não quebrar leitores antigos. */
    private fun clean(value: String, max: Int): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Za-z0-9 .-]"), "")
            .trim()
            .take(max)

    /** CRC16-CCITT-FALSE (polinômio 0x1021, inicial 0xFFFF), em 4 dígitos hexadecimais. */
    fun crc16(text: String): String {
        var crc = 0xFFFF
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc.toString(16).uppercase(Locale.ROOT).padStart(4, '0')
    }
}
