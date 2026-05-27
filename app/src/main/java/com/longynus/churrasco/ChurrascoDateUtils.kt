package com.longynus.churrasco

import java.util.Calendar
import java.util.Locale

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
        "${normalizeDate(date)} às ${normalizeTime(time)}"

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

        val selected = Calendar.getInstance().apply {
            set(year, monthZeroBased, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return selected.after(Calendar.getInstance())
    }

    fun startOfToday(): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
