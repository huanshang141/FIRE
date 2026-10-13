package app.fireplan.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

fun formatYuan(value: Double): String {
    if (!value.isFinite()) return "— 元"
    val format = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
        isGroupingUsed = true
    }
    return "${format.format(value)} 元"
}

fun formatPercent(fraction: Double): String {
    if (!fraction.isFinite()) return "—"
    return "${BigDecimal.valueOf(fraction * 100.0).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"
}

fun formatDate(date: LocalDate): String = date.format(dateFormatter)

fun formatPlain(value: Double): String {
    if (!value.isFinite()) return ""
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

fun formatPercentInput(fraction: Double): String {
    if (!fraction.isFinite()) return ""
    return BigDecimal.valueOf(fraction).movePointRight(2).stripTrailingZeros().toPlainString()
}

fun parseNumber(text: String): Double? {
    val cleaned = text.trim().replace(",", "").replace("，", "").replace(" ", "")
    if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == "-.") return null
    return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
}
