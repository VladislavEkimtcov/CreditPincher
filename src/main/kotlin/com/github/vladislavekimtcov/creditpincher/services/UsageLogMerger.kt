package com.github.vladislavekimtcov.creditpincher.services

import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import java.time.Instant

/**
 * Pure logic for merging two conflicting versions of `usage-log.csv`
 * (as produced by `git show :2:usage-log.csv` / `git show :3:usage-log.csv`)
 * into a single, deduplicated, chronologically sorted CSV.
 *
 * Extracted from [GitConflictResolverDialog] so it can be unit tested without
 * needing to spin up any UI or IntelliJ platform services.
 */
object UsageLogMerger {

    private const val HEADER = "timestamp,amount"

    /** Merges the "ours" and "theirs" CSV contents, returning the resolved CSV text. */
    fun merge(oursContent: String, theirsContent: String): String {
        val oursEntries = parseEntries(oursContent)
        val theirsEntries = parseEntries(theirsContent)

        val merged = (oursEntries + theirsEntries)
            .distinctBy { it.timestamp to it.amount }
            .sortedBy { it.timestamp }

        val sb = StringBuilder()
        sb.append(HEADER).append("\n")
        for (entry in merged) {
            sb.append("${entry.timestamp},${entry.amount}\n")
        }
        return sb.toString()
    }

    fun parseEntries(content: String): List<CreditUsageEntry> {
        return content.lineSequence()
            .drop(1) // Drop header
            .mapNotNull { line ->
                val parts = line.split(',', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val timestamp = runCatching { Instant.parse(parts[0].trim()) }.getOrNull() ?: return@mapNotNull null
                val amount = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                CreditUsageEntry(timestamp, amount)
            }
            .toList()
    }
}

