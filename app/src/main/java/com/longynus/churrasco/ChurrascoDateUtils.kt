package com.longynus.churrasco

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object ChurrascoDateUtils {
    fun formatDate(day: Int, monthZeroBased: Int, year: Int): String =
        String.format(Locale("pt", "BR"), "%02d/%02d/%04d", day, monthZeroBased + 1, year)

    fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale("pt", "BR"), "%02d:%02d", hour, minute)

    fun normalizeDate(date: String): String {
        val parts = date.trim().split("/")
        if (parts.size != 3) return date

        val day = parts[0].toIntOrNull() ?: return date
        val month = parts[1].toIntOrNull() ?: return date
        val year = parts[2].toIntOrNull() ?: return date

        return String.format(Locale("pt", "BR"), "%02d/%02d/%04d", day, month, year)
    }

    fun normalizeTime(time: String): String {
        val parts = time.trim().split(":")
        if (parts.size != 2) return time

        val hour = parts[0].toIntOrNull() ?: return time
        val minute = parts[1].toIntOrNull() ?: return time

        return formatTime(hour, minute)
    }

    fun eventDateTime(date: String, time: String): String =
        "${normalizeDate(date)} às ${normalizeTime(time)} (São Paulo)"

    fun eventDateTimeMillis(date: String, time: String): Long? {
        if (!Regex("""\d{2}/\d{2}/\d{4}""").matches(date) ||
            !Regex("""\d{2}:\d{2}""").matches(time)) return null
        val (day, month, year) = date.split("/").map { it.toInt() }
        val (hour, minute) = time.split(":").map { it.toInt() }
        if (year !in 2020..2100) return null
        return runCatching {
            Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo")).apply {
                clear()
                isLenient = false
                set(year, month - 1, day, hour, minute, 0)
            }.timeInMillis
        }.getOrNull()
    }

    fun selectedDateTimeIsFuture(
        year: Int?,
        monthZeroBased: Int?,
        day: Int?,
        hour: Int?,
        minute: Int?
    ): Boolean {
        if (year == null || monthZeroBased == null || day == null || hour == null || minute == null) {
            return false
        }

        val selected = eventDateTimeMillis(formatDate(day, monthZeroBased, year), formatTime(hour, minute))
            ?: return false
        return selected > System.currentTimeMillis()
    }

    fun startOfToday(): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}


