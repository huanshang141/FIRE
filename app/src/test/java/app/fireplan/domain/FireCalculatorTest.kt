package app.fireplan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ln

class FireCalculatorTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun reachableWithPositiveReturn() {
        val result = FireCalculator.calculate(
            FireInput(
                netWorth = 100_000.0,
                monthlyIncome = 20_000.0,
                monthlyExpenses = 10_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.05,
            ),
            today,
        )
        assertEquals(240_000.0, result.annualIncome, 0.001)
        assertEquals(120_000.0, result.annualExpenses, 0.001)
        assertEquals(120_000.0, result.annualSavings, 0.001)
        assertEquals(0.5, result.savingsRate!!, 1e-9)
        assertEquals(3_000_000.0, result.fireNumber!!, 0.001)
        assertEquals(100_000.0 / 3_000_000.0, result.progress!!, 1e-9)
        assertEquals(FireOutlook.PROJECTED, result.outlook)
        val expectedYears = ln((3_000_000.0 * 0.05 + 120_000.0) / (100_000.0 * 0.05 + 120_000.0)) / ln(1.05)
        assertEquals(expectedYears, result.years!!, 1e-9)
        assertEquals(LocalDate.of(2042, 7, 26), result.targetDate)
    }

    @Test
    fun alreadyFireCapsProgressAndUsesToday() {
        val result = FireCalculator.calculate(
            FireInput(
                netWorth = 5_000_000.0,
                monthlyIncome = 1_000.0,
                monthlyExpenses = 8_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.0,
            ),
            today,
        )
        assertEquals(2_400_000.0, result.fireNumber!!, 0.001)
        assertEquals(1.0, result.progress!!, 0.0)
        assertEquals(0.0, result.years!!, 0.0)
        assertEquals(today, result.targetDate)
        assertEquals(FireOutlook.REACHED, result.outlook)
    }

    @Test
    fun zeroIncomeHidesSavingsRateAndCannotProject() {
        val result = FireCalculator.calculate(
            FireInput(
                netWorth = 100_000.0,
                monthlyIncome = 0.0,
                monthlyExpenses = 8_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.05,
            ),
            today,
        )
        assertEquals(0.0, result.annualIncome, 0.0)
        assertNull(result.savingsRate)
        assertEquals(-96_000.0, result.annualSavings, 0.001)
        assertEquals(2_400_000.0, result.fireNumber!!, 0.001)
        assertNull(result.years)
        assertNull(result.targetDate)
        assertEquals(FireOutlook.UNREACHABLE, result.outlook)
        assertTrue(buildProgressContext(null, null).contains("没有填写"))
        val context = buildProgressContext(
            FireInput(100_000.0, 0.0, 8_000.0, 0.04, 0.05),
            result,
        )
        assertTrue(context.contains("储蓄率不显示"))
        assertTrue(context.contains("按当前数字无法达成"))
    }

    @Test
    fun zeroExpensesDoesNotInventAFireNumber() {
        val result = FireCalculator.calculate(
            FireInput(
                netWorth = 100_000.0,
                monthlyIncome = 10_000.0,
                monthlyExpenses = 0.0,
                withdrawalRate = 0.04,
                annualReturn = 0.05,
            ),
            today,
        )
        assertNull(result.fireNumber)
        assertNull(result.progress)
        assertNull(result.targetDate)
        assertEquals(1.0, result.savingsRate!!, 1e-9)
        assertEquals(FireOutlook.UNDEFINED, result.outlook)
    }

    @Test
    fun negativeSavings() {
        val stalled = FireCalculator.calculate(
            FireInput(
                netWorth = 50_000.0,
                monthlyIncome = 5_000.0,
                monthlyExpenses = 8_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.0,
            ),
            today,
        )
        assertEquals(-0.6, stalled.savingsRate!!, 1e-9)
        assertEquals(FireOutlook.UNREACHABLE, stalled.outlook)
        assertNull(stalled.years)
        assertNull(stalled.targetDate)

        val balanced = FireCalculator.calculate(
            FireInput(
                netWorth = 50_000.0,
                monthlyIncome = 8_000.0,
                monthlyExpenses = 8_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.0,
            ),
            today,
        )
        assertEquals(0.0, balanced.annualSavings, 0.001)
        assertEquals(FireOutlook.UNREACHABLE, balanced.outlook)

        val stillGrowing = FireCalculator.calculate(
            FireInput(
                netWorth = 2_000_000.0,
                monthlyIncome = 5_000.0,
                monthlyExpenses = 8_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.08,
            ),
            today,
        )
        assertTrue(stillGrowing.annualSavings < 0.0)
        assertEquals(FireOutlook.PROJECTED, stillGrowing.outlook)
        assertEquals(LocalDate.of(2029, 10, 7), stillGrowing.targetDate)
    }

    @Test
    fun zeroReturnUsesLinearGap() {
        val result = FireCalculator.calculate(
            FireInput(
                netWorth = 100_000.0,
                monthlyIncome = 20_000.0,
                monthlyExpenses = 10_000.0,
                withdrawalRate = 0.04,
                annualReturn = 0.0,
            ),
            today,
        )
        assertEquals(2_900_000.0 / 120_000.0, result.years!!, 1e-9)
        assertEquals(FireOutlook.PROJECTED, result.outlook)
        assertEquals(LocalDate.of(2050, 12, 13), result.targetDate)
    }

    @Test
    fun illegalWithdrawalRateGivesNoFireNumber() {
        listOf(-0.04, 0.0, 1.5, Double.NaN).forEach { rate ->
            val result = FireCalculator.calculate(
                FireInput(
                    netWorth = 100_000.0,
                    monthlyIncome = 20_000.0,
                    monthlyExpenses = 10_000.0,
                    withdrawalRate = rate,
                    annualReturn = 0.05,
                ),
                today,
            )
            assertNull(result.fireNumber)
            assertNull(result.progress)
            assertNull(result.targetDate)
            assertEquals(FireOutlook.UNDEFINED, result.outlook)
        }

        val fullWithdrawal = FireCalculator.calculate(
            FireInput(
                netWorth = 0.0,
                monthlyIncome = 10_000.0,
                monthlyExpenses = 10_000.0,
                withdrawalRate = 1.0,
                annualReturn = 0.0,
            ),
            today,
        )
        assertEquals(120_000.0, fullWithdrawal.fireNumber!!, 0.001)
        assertEquals(FireOutlook.UNREACHABLE, fullWithdrawal.outlook)
    }
}
