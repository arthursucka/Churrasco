package com.longynus.churrasco

import com.longynus.churrasco.details.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesBrazilianAndPlainInputs() {
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(120000L, Money.parse("R$ 1.200,00"))
        assertEquals(100000L, Money.parse("1000"))
        assertEquals(5L, Money.parse("0,05"))
    }

    @Test fun rejectsInvalidValues() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-3"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("1,234"))
        assertNull(Money.parse("200000,00"))
    }

    @Test fun formatsAsReais() {
        assertEquals("R$ 12,50", Money.format(1250))
        assertEquals("R$ 1.200,00", Money.format(120000))
    }
}
