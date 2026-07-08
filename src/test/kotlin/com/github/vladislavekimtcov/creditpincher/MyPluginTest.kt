package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.github.vladislavekimtcov.creditpincher.services.CreditStatsCalculator
import com.github.vladislavekimtcov.creditpincher.services.CreditUsageStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MyPluginTest {
    @Test
    fun calculatesMonthToDateStats() {
        val zoneId = ZoneId.of("UTC")
        val entries = listOf(
            entry("2026-07-01T09:00:00Z", 12.0),
            entry("2026-07-02T10:00:00Z", 18.0),
            entry("2026-07-02T15:30:00Z", 7.0),
        )

        val stats = CreditStatsCalculator.calculate(
            entries = entries,
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 10),
            monthlyBudget = 310.0,
            zoneId = zoneId,
        )

        assertEquals(37.0, stats.totalCredits, 0.0001)
        assertEquals(10L, stats.daysInRange)
        assertEquals(3, stats.entryCount)
        assertEquals(2, stats.activeDays)
        assertEquals(3.7, stats.averageCreditsPerDay, 0.0001)
        assertEquals(18.5, stats.averageCreditsPerActiveDay, 0.0001)
        assertEquals(LocalDate.of(2026, 7, 2), stats.busiestDay)
        assertEquals(25.0, stats.busiestDayCredits!!, 0.0001)
        assertEquals(100.0, stats.proratedBudgetForRange!!, 0.0001)
        assertEquals(37.0, stats.budgetUsedPercent!!, 0.0001)
        assertNull(stats.projectedBudgetRunOutDay)
        assertEquals(114.7, stats.projectedMonthTotal!!, 0.0001)
        assertEquals(195.3, stats.projectedMonthRemaining!!, 0.0001)
    }

    @Test
    fun predictsBudgetRunOutDayForFastUsage() {
        val zoneId = ZoneId.of("UTC")
        val entries = (1L..5L).map { day ->
            entry("2026-07-0${day}T12:00:00Z", 20.0)
        }

        val stats = CreditStatsCalculator.calculate(
            entries = entries,
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 5),
            monthlyBudget = 310.0,
            zoneId = zoneId,
        )

        assertEquals(100.0, stats.totalCredits, 0.0001)
        assertEquals(620.0, stats.projectedMonthTotal!!, 0.0001)
        assertEquals(-310.0, stats.projectedMonthRemaining!!, 0.0001)
        assertEquals(16, stats.projectedBudgetRunOutDay)
    }

    @Test
    fun storesBudgetAndUsageEntriesInPlainFiles() {
        val tempDirectory = Files.createTempDirectory("credit-pincher-test")
        try {
            val clock = Clock.fixed(Instant.parse("2026-07-08T12:00:00Z"), ZoneId.of("UTC"))
            val storage = CreditUsageStorage(tempDirectory, clock)

            storage.saveMonthlyBudget(450.5)
            storage.addUsage(12.5)
            storage.addUsage(7.25)

            assertEquals(450.5, storage.loadMonthlyBudget()!!, 0.0001)
            assertEquals(2, storage.loadEntries().size)
            assertTrue(Files.exists(tempDirectory.resolve("monthly-budget.txt")))
            assertTrue(Files.exists(tempDirectory.resolve("usage-log.csv")))

            val logFile = Files.readString(tempDirectory.resolve("usage-log.csv"))
            assertTrue(logFile.contains("timestamp,amount"))
            assertTrue(logFile.contains("12.5"))
            assertTrue(logFile.contains("7.25"))
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    private fun entry(timestamp: String, amount: Double): CreditUsageEntry =
        CreditUsageEntry(Instant.parse(timestamp), amount)
}
