package com.longynus.churrasco

import com.longynus.churrasco.pix.PixPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixPayloadTest {
    @Test fun matchesTheCentralBankManualExample() {
        // Exemplo do Manual do BR Code (chave aleatória, sem valor).
        val payload = PixPayload.build("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", city = "BRASILIA")
        assertEquals(
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D",
            payload,
        )
    }

    @Test fun includesAmountAndRemovesAccents() {
        val payload = PixPayload.build("paulo@pix.com", "João Ação", amountCents = 12345)
        assertTrue(payload.contains("5406123.45"))
        assertTrue(payload.contains("5909Joao Acao"))
        assertEquals(PixPayload.crc16(payload.dropLast(4)), payload.takeLast(4))
    }

    @Test fun truncatesLongNames() {
        val payload = PixPayload.build("k", "A".repeat(40))
        assertTrue(payload.contains("5925" + "A".repeat(25)))
    }
}
