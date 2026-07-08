package com.github.vladislavekimtcov.creditpincher.services

import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.github.vladislavekimtcov.creditpincher.model.UsageStats
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object CreditStatsCalculator {
    fun calculate(
        entries: List<CreditUsageEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        monthlyBudget: Double?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): UsageStats {
        val normalizedStart = minOf(startDate, endDate)
        val normalizedEnd = maxOf(startDate, endDate)
        val filteredEntries = entries.filter { entry ->
            val entryDate = entry.localDate(zoneId)
            !entryDate.isBefore(normalizedStart) && !entryDate.isAfter(normalizedEnd)
        }
        val creditsByDay = filteredEntries
            .groupBy { it.localDate(zoneId) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf(CreditUsageEntry::amount) }

        val totalCredits = filteredEntries.sumOf(CreditUsageEntry::amount)
        val daysInRange = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1
        val activeDays = creditsByDay.size
        val averageCreditsPerDay = if (daysInRange > 0) totalCredits / daysInRange else 0.0
        val averageCreditsPerActiveDay = if (activeDays > 0) totalCredits / activeDays else 0.0
        val busiestDay = creditsByDay.maxByOrNull { it.value }
        val proratedBudget = monthlyBudget?.let { calculateProratedBudget(normalizedStart, normalizedEnd, it) }
        val budgetUsedPercent = if (proratedBudget != null && proratedBudget > 0.0) {
            (totalCredits / proratedBudget) * 100.0
        } else {
            null
        }
        val singleMonthProjection = monthlyBudget
            ?.takeIf { it > 0.0 && YearMonth.from(normalizedStart) == YearMonth.from(normalizedEnd) }
            ?.let { budget ->
                val month = YearMonth.from(normalizedStart)
                val projectedMonthTotal = averageCreditsPerDay * month.lengthOfMonth()
                val projectedMonthRemaining = budget - projectedMonthTotal
                val projectedBudgetRunOutDay = if (averageCreditsPerDay > 0.0 && projectedMonthTotal > budget) {
                    ceil(budget / averageCreditsPerDay).toInt().coerceIn(1, month.lengthOfMonth())
                } else {
                    null
                }

                Triple(projectedMonthTotal, projectedMonthRemaining, projectedBudgetRunOutDay) to month
            }

        return UsageStats(
            startDate = normalizedStart,
            endDate = normalizedEnd,
            daysInRange = daysInRange,
            entryCount = filteredEntries.size,
            activeDays = activeDays,
            totalCredits = totalCredits,
            averageCreditsPerDay = averageCreditsPerDay,
            averageCreditsPerActiveDay = averageCreditsPerActiveDay,
            busiestDay = busiestDay?.key,
            busiestDayCredits = busiestDay?.value,
            highestSingleEntry = filteredEntries.maxOfOrNull(CreditUsageEntry::amount),
            latestEntryDate = filteredEntries.maxOfOrNull { it.localDate(zoneId) },
            monthlyBudget = monthlyBudget,
            proratedBudgetForRange = proratedBudget,
            remainingBudgetForRange = proratedBudget?.minus(totalCredits),
            budgetUsedPercent = budgetUsedPercent,
            projectedMonthTotal = singleMonthProjection?.first?.first,
            projectedMonthRemaining = singleMonthProjection?.first?.second,
            projectedBudgetRunOutDay = singleMonthProjection?.first?.third,
            projectionMonth = singleMonthProjection?.second,
        )
    }

    private fun calculateProratedBudget(
        startDate: LocalDate,
        endDate: LocalDate,
        monthlyBudget: Double,
    ): Double {
        var currentDate = startDate
        var totalBudget = 0.0

        while (!currentDate.isAfter(endDate)) {
            totalBudget += monthlyBudget / YearMonth.from(currentDate).lengthOfMonth()
            currentDate = currentDate.plusDays(1)
        }

        return totalBudget
    }
}
