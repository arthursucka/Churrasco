package com.longynus.churrasco

import com.longynus.churrasco.data.model.Contribution
import com.longynus.churrasco.items.ItemCatalog
import com.longynus.churrasco.items.ItemField
import com.longynus.churrasco.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAndCatalogTest {
    private val hour = 3_600_000L

    @Test fun remindersAre24hAnd2hBeforeAndSkipThePast() {
        val start = 100 * hour
        assertEquals(listOf(24 * 60 to start - 24 * hour, 2 * 60 to start - 2 * hour), ReminderScheduler.triggerTimes(start, 0))
        // Faltando 3 h, só sobra o aviso de 2 h.
        assertEquals(listOf(2 * 60 to start - 2 * hour), ReminderScheduler.triggerTimes(start, start - 3 * hour))
        assertTrue(ReminderScheduler.triggerTimes(start, start - hour).isEmpty())
    }

    @Test fun catalogFindsV1NamesAndHasOther() {
        assertEquals("Carne bovina", ItemCatalog.find("Carne Bovina")?.name)
        assertEquals("Queijo", ItemCatalog.find("Queijo Coalho")?.name)
        assertNotNull(ItemCatalog.find(ItemCatalog.OTHER))
        assertEquals("🍽️", ItemCatalog.emoji("Algo que não existe"))
        val names = ItemCatalog.categories.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun summaryFollowsFieldOrderAndAddsTheNote() {
        val c = Contribution(category = "Carne bovina", details = mapOf("tempero" to "Sem tempero", "quantidade" to "1,5 kg", "corte" to "Picanha"), note = "Já porcionada")
        assertEquals("Picanha · 1,5 kg · Sem tempero · Já porcionada", ItemCatalog.summary(c))
        assertEquals("Farofa da vó", ItemCatalog.summary(Contribution(category = "outro", note = "Farofa da vó")))
    }

    @Test fun quantityUsesBrazilianDecimalAndPlural() {
        val kg = ItemCatalog.find("Carne bovina")!!.fields.filterIsInstance<ItemField.Quantity>().first()
        assertEquals("1,5 kg", kg.format(1.5))
        val gelo = ItemCatalog.find("Gelo")!!.fields.filterIsInstance<ItemField.Quantity>().first()
        assertEquals("1 saco de 5 kg", gelo.format(1.0))
        assertEquals("2 sacos de 5 kg", gelo.format(2.0))
    }
}
