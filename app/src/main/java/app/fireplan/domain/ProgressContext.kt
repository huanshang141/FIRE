package app.fireplan.domain

import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

fun buildProgressContext(input: FireInput?, projection: FireProjection?): String {
    if (input == null || projection == null) {
        return "当前进度：用户还没有填写目标。"
    }
    val savings = projection.savingsRate?.let { "储蓄率 ${percent(it)}" } ?: "储蓄率不显示（年收入为 0）"
    val fire = projection.fireNumber?.let { "FIRE 数字 ${yuan(it)}" } ?: "FIRE 数字无法计算"
    val progress = when (projection.outlook) {
        FireOutlook.REACHED -> "进度 100%，已经达成"
        else -> projection.progress?.let { "进度 ${percent(it.coerceAtLeast(0.0))}" } ?: "进度无法计算"
    }
    val date = when (projection.outlook) {
        FireOutlook.REACHED -> "预计日期就是今天（${projection.targetDate?.format(dateFormatter) ?: "今天"}）"
        FireOutlook.PROJECTED -> "预计日期 ${projection.targetDate?.format(dateFormatter) ?: "未知"}"
        FireOutlook.UNREACHABLE -> "按当前数字无法达成"
        FireOutlook.UNDEFINED -> "无法给出预计日期"
    }
    return buildString {
        append("当前进度：净资产 ${yuan(input.netWorth)}")
        append("，月收入 ${yuan(input.monthlyIncome)}")
        append("，月支出 ${yuan(input.monthlyExpenses)}")
        append("，年收入 ${yuan(projection.annualIncome)}")
        append("，年支出 ${yuan(projection.annualExpenses)}")
        append("，年结余 ${yuan(projection.annualSavings)}")
        append("，$savings")
        append("，$fire")
        append("，$progress")
        append("，$date")
        append("。安全提取率 ${percent(input.withdrawalRate)}")
        append("，预期年化实际收益率 ${percent(input.annualReturn)}")
        append("。只使用这些数字。")
    }
}

private fun yuan(value: Double): String = "${plain(value)} 元"

private fun percent(fraction: Double): String = "${plain(fraction * 100.0)}%"

private fun plain(value: Double): String {
    if (!value.isFinite()) return "0"
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
