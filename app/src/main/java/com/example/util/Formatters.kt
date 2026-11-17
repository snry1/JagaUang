package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val indonesiaLocale = Locale("id", "ID")

private val idrFormatSymbols = DecimalFormatSymbols(indonesiaLocale).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

private val idrDecimalFormat = DecimalFormat("#,###", idrFormatSymbols)

fun formatRupiah(amount: Long, showPlusSign: Boolean = false): String {
    val absFormatted = idrDecimalFormat.format(abs(amount))
    return when {
        amount < 0 -> "-Rp $absFormatted"
        amount > 0 && showPlusSign -> "+Rp $absFormatted"
        else -> "Rp $absFormatted"
    }
}

fun formatRupiahCompact(amount: Long): String {
    val absAmount = abs(amount)
    val prefix = if (amount < 0) "-Rp " else "Rp "
    return when {
        absAmount >= 1_000_000_000L -> {
            val valM = absAmount / 1_000_000_000.0
            "$prefix${String.format(indonesiaLocale, "%.1f", valM).trimEnd('0').trimEnd(',')} M"
        }
        absAmount >= 1_000_000L -> {
            val valJt = absAmount / 1_000_000.0
            "$prefix${String.format(indonesiaLocale, "%.1f", valJt).trimEnd('0').trimEnd(',')} jt"
        }
        absAmount >= 10_000L -> {
            val valRb = absAmount / 1_000L
            "$prefix${valRb} rb"
        }
        else -> formatRupiah(amount)
    }
}

fun formatGroupedDigits(rawDigits: String): String {
    val clean = rawDigits.filter { it.isDigit() }.toLongOrNull() ?: return ""
    return idrDecimalFormat.format(clean)
}

fun currentMonthKey(epochMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    return String.format(Locale.US, "%04d-%02d", year, month)
}

fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, deltaMonths)
    }
    return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
}

fun formatMonthLabel(monthKey: String): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val sdf = SimpleDateFormat("MMMM yyyy", indonesiaLocale)
    return sdf.format(cal.time).replaceFirstChar { it.titlecase(indonesiaLocale) }
}

fun formatDateHeader(epochMillis: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(target, today) -> "Hari Ini"
        isSameDay(target, yesterday) -> "Kemarin"
        else -> SimpleDateFormat("EEE, d MMM yyyy", indonesiaLocale).format(Date(epochMillis))
    }
}

fun formatShortDate(epochMillis: Long): String {
    return SimpleDateFormat("d MMM yyyy", indonesiaLocale).format(Date(epochMillis))
}

fun formatDayOfWeekShort(epochMillis: Long): String {
    return SimpleDateFormat("EEE", indonesiaLocale).format(Date(epochMillis)).take(3)
}

fun dayStartMillis(epochMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun monthStartMillis(monthKey: String): Long {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
