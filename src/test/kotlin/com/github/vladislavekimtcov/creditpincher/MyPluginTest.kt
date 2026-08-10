package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.github.vladislavekimtcov.creditpincher.toolWindow.UsageBarChart
import com.github.vladislavekimtcov.creditpincher.services.ConflictMarkerParser
import com.github.vladislavekimtcov.creditpincher.services.CreditStatsCalculator
import com.github.vladislavekimtcov.creditpincher.services.CreditUsageStorage
import com.github.vladislavekimtcov.creditpincher.services.GitConflictContentProvider
import com.github.vladislavekimtcov.creditpincher.services.UsageLogMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testUsageBarChartDataUpdate() {
        val chart = UsageBarChart()
        val data = listOf(
            UsageBarChart.DailyUsage(LocalDate.of(2026, 7, 1), 10.0),
            UsageBarChart.DailyUsage(LocalDate.of(2026, 7, 2), 20.0)
        )
        chart.updateData(data, showDollars = false)
        chart.setSize(400, 200)
        val image = java.awt.image.BufferedImage(400, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        chart.paint(g)
        g.dispose()
    }

    private fun entry(timestamp: String, amount: Double): CreditUsageEntry =
        CreditUsageEntry(Instant.parse(timestamp), amount)

    @Test
    fun mergesUsageLogsChronologicallyAndDeduplicates() {
        val ours = """
            timestamp,amount
            2026-07-01T09:00:00Z,12.0
            2026-07-03T09:00:00Z,5.0
        """.trimIndent()

        val theirs = """
            timestamp,amount
            2026-07-02T10:00:00Z,18.0
            2026-07-03T09:00:00Z,5.0
        """.trimIndent()

        val merged = UsageLogMerger.merge(ours, theirs)
        val lines = merged.trim().lines()

        assertEquals("timestamp,amount", lines[0])
        assertEquals(4, lines.size)
        assertEquals("2026-07-01T09:00:00Z,12.0", lines[1])
        assertEquals("2026-07-02T10:00:00Z,18.0", lines[2])
        assertEquals("2026-07-03T09:00:00Z,5.0", lines[3])
    }

    @Test
    fun mergesDisjointUsageLogsWithoutDuplication() {
        val ours = "timestamp,amount\n2026-07-05T00:00:00Z,1.0\n"
        val theirs = "timestamp,amount\n2026-07-04T00:00:00Z,2.0\n"

        val merged = UsageLogMerger.merge(ours, theirs)
        val entries = UsageLogMerger.parseEntries(merged)

        assertEquals(2, entries.size)
        assertEquals(Instant.parse("2026-07-04T00:00:00Z"), entries[0].timestamp)
        assertEquals(Instant.parse("2026-07-05T00:00:00Z"), entries[1].timestamp)
    }

    @Test
    fun mergeHandlesEmptyOrBlankInputsGracefully() {
        val merged = UsageLogMerger.merge("", "timestamp,amount\n2026-07-01T00:00:00Z,3.0\n")
        val entries = UsageLogMerger.parseEntries(merged)

        assertEquals(1, entries.size)
        assertEquals(3.0, entries[0].amount, 0.0001)
    }

    @Test
    fun detectsConflictMarkers() {
        val conflicted = "timestamp,amount\n<<<<<<< HEAD\nlocal\n=======\nremote\n>>>>>>> origin/main\n"

        assertTrue(ConflictMarkerParser.hasConflictMarkers(conflicted))
        assertFalse(ConflictMarkerParser.hasConflictMarkers("timestamp,amount\n2026-07-01T00:00:00Z,3.0\n"))
        assertFalse(ConflictMarkerParser.hasConflictMarkers("a line that just says ======= in the middle"))
    }

    @Test
    fun splitsConflictMarkersIntoBothSidesKeepingCommonLines() {
        val conflicted = """
            timestamp,amount
            <<<<<<< HEAD
            2026-07-03T09:00:00Z,5.0
            =======
            2026-07-04T09:00:00Z,6.0
            >>>>>>> origin/main
            2026-07-05T09:00:00Z,7.0
        """.trimIndent()

        val contents = ConflictMarkerParser.parse(conflicted)

        assertEquals("timestamp,amount\n2026-07-03T09:00:00Z,5.0\n2026-07-05T09:00:00Z,7.0", contents.ours)
        assertEquals("timestamp,amount\n2026-07-04T09:00:00Z,6.0\n2026-07-05T09:00:00Z,7.0", contents.theirs)
        // Default (non-diff3) markers carry no ancestor text, only the common lines.
        assertEquals("timestamp,amount\n2026-07-05T09:00:00Z,7.0", contents.base)
    }

    @Test
    fun capturesAncestorSectionOfDiff3StyleConflictMarkers() {
        val conflicted = """
            <<<<<<< HEAD
            local
            ||||||| merged common ancestors
            original
            =======
            remote
            >>>>>>> origin/main
        """.trimIndent()

        val contents = ConflictMarkerParser.parse(conflicted)

        assertEquals("local", contents.ours)
        assertEquals("remote", contents.theirs)
        assertEquals("original", contents.base)
    }

    @Test
    fun conflictContentProviderPrefersIndexStagesOverWorkingTree() {
        val tempDirectory = Files.createTempDirectory("credit-pincher-conflict-test")
        try {
            Files.writeString(tempDirectory.resolve("usage-log.csv"), "should be ignored")

            val provider = StubConflictContentProvider(
                workingDirectory = tempDirectory,
                stages = mapOf(1 to "base text", 2 to "local text", 3 to "remote text"),
            )

            val contents = provider.contentsFor("usage-log.csv")

            assertEquals("base text", contents.base)
            assertEquals("local text", contents.ours)
            assertEquals("remote text", contents.theirs)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun conflictContentProviderFallsBackToWorkingTreeMarkers() {
        val tempDirectory = Files.createTempDirectory("credit-pincher-conflict-test")
        try {
            Files.writeString(
                tempDirectory.resolve("usage-log.csv"),
                "timestamp,amount\n<<<<<<< HEAD\nlocal\n=======\nremote\n>>>>>>> origin/main\n",
            )

            val provider = StubConflictContentProvider(workingDirectory = tempDirectory, stages = emptyMap())
            val contents = provider.contentsFor("usage-log.csv")

            assertEquals("timestamp,amount\nlocal\n", contents.ours)
            assertEquals("timestamp,amount\nremote\n", contents.theirs)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun conflictContentProviderFallsBackToPlainWorkingCopyWithoutMarkers() {
        val tempDirectory = Files.createTempDirectory("credit-pincher-conflict-test")
        try {
            Files.writeString(tempDirectory.resolve("monthly-budget.txt"), "310.0")

            val provider = StubConflictContentProvider(workingDirectory = tempDirectory, stages = emptyMap())
            val contents = provider.contentsFor("monthly-budget.txt")

            assertEquals("310.0", contents.ours)
            assertEquals("310.0", contents.theirs)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    /** Feeds canned index stages to [GitConflictContentProvider] so no repository is needed. */
    private class StubConflictContentProvider(
        workingDirectory: java.nio.file.Path,
        private val stages: Map<Int, String>,
    ) : GitConflictContentProvider(workingDirectory) {
        override fun readStage(stage: Int, file: String): String = stages[stage] ?: ""
    }
}
