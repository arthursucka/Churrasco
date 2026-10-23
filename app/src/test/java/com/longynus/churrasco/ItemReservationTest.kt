package com.longynus.churrasco

import com.longynus.churrasco.model.Churrasco
import com.longynus.churrasco.model.Guest
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemReservationTest {
    private val event = Churrasco("id", "20/12/2026", "12:00", "Teste", "organizador",
        fornecidosAgregados = listOf("Sal", "Carvão", "Gelo"),
        organizerItems = listOf("Sal"),
        guestsConfirmed = listOf(Guest("Arthur", listOf("Carvão")), Guest("Cassi", listOf("Gelo"))))

    @Test fun changingOwnItemsKeepsThemAvailableAndProtectsOtherOwners() {
        assertEquals(setOf("Sal", "Gelo"), event.itemsReservedByOthers("Arthur"))
        assertEquals(listOf("Carvão"), event.ownItems("Arthur"))
    }
    @Test fun organizerItemRemainsReservedEvenWhenGuestRecordOverlaps() {
        val overlap = event.copy(guestsConfirmed = listOf(Guest("Arthur", listOf("Sal"))))
        assertEquals(setOf("Sal"), overlap.itemsReservedByOthers("Arthur"))
    }
    @Test fun returningAfterDeclineProtectsAllCurrentContributions() {
        assertEquals(setOf("Sal", "Carvão", "Gelo"), event.itemsReservedByOthers("Gabriel"))
        assertEquals(emptyList<String>(), event.ownItems("Gabriel"))
    }
}
