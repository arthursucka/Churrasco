package com.longynus.churrasco
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class EventTimeTest {
    @Test fun invalidDatesAndHoursFailClosed() {
        assertNull(ChurrascoDateUtils.eventDateTimeMillis("31/02/2026", "12:00"))
        assertNull(ChurrascoDateUtils.eventDateTimeMillis("29/02/2025", "12:00"))
        assertNull(ChurrascoDateUtils.eventDateTimeMillis("08/10/2026", "25:70"))
        assertNull(ChurrascoDateUtils.eventDateTimeMillis("8/10/2026", "12:00"))
        assertNotNull(ChurrascoDateUtils.eventDateTimeMillis("29/02/2024", "12:00"))
    }
    @Test fun deviceTimezoneDoesNotChangeEventInstant() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(1791471600000L, ChurrascoDateUtils.eventDateTimeMillis("08/10/2026", "12:00"))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(1791471600000L, ChurrascoDateUtils.eventDateTimeMillis("08/10/2026", "12:00"))
        } finally { TimeZone.setDefault(original) }
    }
}
