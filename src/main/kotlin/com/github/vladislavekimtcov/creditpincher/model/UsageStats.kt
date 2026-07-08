package com.github.vladislavekimtcov.creditpincher.model

import java.time.LocalDate
import java.time.YearMonth

data class UsageStats(
	val startDate: LocalDate,
	val endDate: LocalDate,
	val daysInRange: Long,
	val entryCount: Int,
	val activeDays: Int,
	val totalCredits: Double,
	val averageCreditsPerDay: Double,
	val averageCreditsPerActiveDay: Double,
	val busiestDay: LocalDate?,
	val busiestDayCredits: Double?,
	val highestSingleEntry: Double?,
	val latestEntryDate: LocalDate?,
	val monthlyBudget: Double?,
	val proratedBudgetForRange: Double?,
	val remainingBudgetForRange: Double?,
	val budgetUsedPercent: Double?,
	val projectedMonthTotal: Double?,
	val projectedMonthRemaining: Double?,
	val projectedBudgetRunOutDay: Int?,
	val projectionMonth: YearMonth?,
)

