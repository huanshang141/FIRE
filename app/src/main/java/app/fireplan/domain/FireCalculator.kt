package app.fireplan.domain

import java.time.DateTimeException
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.roundToLong

data class FireInput(
    val netWorth: Double,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val withdrawalRate: Double,
    val annualReturn: Double,
)

enum class FireOutlook {
    REACHED,
    PROJECTED,
    UNREACHABLE,
    UNDEFINED,
}

data class FireProjection(
    val annualIncome: Double,
    val annualExpenses: Double,
    val annualSavings: Double,
    val savingsRate: Double?,
    val fireNumber: Double?,
    val progress: Double?,
    val outlook: FireOutlook,
    val years: Double?,
    val targetDate: LocalDate?,
)

object FireCalculator {
    const val DAYS_PER_YEAR = 365.2425

    fun calculate(input: FireInput, today: LocalDate = LocalDate.now()): FireProjection {
        val annualIncome = input.monthlyIncome * 12.0
        val annualExpenses = input.monthlyExpenses * 12.0
        val annualSavings = (input.monthlyIncome - input.monthlyExpenses) * 12.0
        val savingsRate = if (annualIncome == 0.0) null else annualSavings / annualIncome
        val fireNumber = fireNumber(annualExpenses, input.withdrawalRate)

        if (fireNumber == null) {
            return FireProjection(
                annualIncome = annualIncome,
                annualExpenses = annualExpenses,
                annualSavings = annualSavings,
                savingsRate = savingsRate,
                fireNumber = null,
                progress = null,
                outlook = FireOutlook.UNDEFINED,
                years = null,
                targetDate = null,
            )
        }

        if (input.netWorth.isFinite() && input.netWorth >= fireNumber) {
            return FireProjection(
                annualIncome = annualIncome,
                annualExpenses = annualExpenses,
                annualSavings = annualSavings,
                savingsRate = savingsRate,
                fireNumber = fireNumber,
                progress = 1.0,
                outlook = FireOutlook.REACHED,
                years = 0.0,
                targetDate = today,
            )
        }

        val progress = if (input.netWorth.isFinite()) input.netWorth / fireNumber else null
        val years = yearsUntil(input.netWorth, annualSavings, fireNumber, input.annualReturn)
        val targetDate = years?.let { dateAfterYears(today, it) }
        if (years == null || targetDate == null) {
            return FireProjection(
                annualIncome = annualIncome,
                annualExpenses = annualExpenses,
                annualSavings = annualSavings,
                savingsRate = savingsRate,
                fireNumber = fireNumber,
                progress = progress,
                outlook = FireOutlook.UNREACHABLE,
                years = null,
                targetDate = null,
            )
        }

        return FireProjection(
            annualIncome = annualIncome,
            annualExpenses = annualExpenses,
            annualSavings = annualSavings,
            savingsRate = savingsRate,
            fireNumber = fireNumber,
            progress = progress,
            outlook = FireOutlook.PROJECTED,
            years = years,
            targetDate = targetDate,
        )
    }

    private fun fireNumber(annualExpenses: Double, withdrawalRate: Double): Double? {
        if (!annualExpenses.isFinite() || annualExpenses <= 0.0) return null
        if (!withdrawalRate.isFinite() || withdrawalRate <= 0.0 || withdrawalRate > 1.0) return null
        val number = annualExpenses / withdrawalRate
        return number.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun yearsUntil(principal: Double, annualSavings: Double, fireNumber: Double, annualReturn: Double): Double? {
        if (!principal.isFinite() || !annualSavings.isFinite() || !annualReturn.isFinite()) return null
        if (annualReturn == 0.0) {
            if (annualSavings <= 0.0) return null
            val gap = fireNumber - principal
            if (gap <= 0.0) return 0.0
            return gap / annualSavings
        }
        if (annualReturn <= -1.0) return null
        val denominator = principal * annualReturn + annualSavings
        if (denominator <= 0.0) return null
        val numerator = fireNumber * annualReturn + annualSavings
        if (numerator <= 0.0) return null
        val ratio = numerator / denominator
        if (ratio <= 0.0) return null
        val years = ln(ratio) / ln(1.0 + annualReturn)
        if (!years.isFinite() || years < 0.0) return null
        return years
    }

    private fun dateAfterYears(today: LocalDate, years: Double): LocalDate? {
        val days = (years * DAYS_PER_YEAR).roundToLong()
        return try {
            today.plusDays(days)
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }
}
